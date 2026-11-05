package dev.lilt.player.stream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Blocking LRCLIB client core (pure Java + org.json; {@link LyricsClient} is the app facade).
 *
 * <p>Lookup: {@code GET /api/get} with the exact signature (title, artist, album, duration in
 * seconds) when the album is meaningful, then {@code GET /api/search} by title + artist (and a
 * cleaned title without "(feat. ...)"/"[...]"/" - ..." suffixes) choosing the closest duration.
 * Synced lines are kept only when the duration is within {@link #SYNC_TOLERANCE_S}; a looser match
 * (up to {@link #MATCH_TOLERANCE_S}) contributes plain lyrics only, so timings are never wrong.
 *
 * <p>Results, including "not found", are cached in memory and as one small JSON file per song
 * (found: 30 days, not found: 2 days). Expired lyrics are still served when the refresh fails.
 */
public final class LrclibApi {
    public static final String BASE_URL = "https://lrclib.net";
    public static final String USER_AGENT = "AURA/2.0 (https://github.com/nitish496/AURA)";
    static final long FOUND_TTL_MS = 30L * 24 * 60 * 60_000L, MISSING_TTL_MS = 2L * 24 * 60 * 60_000L;
    static final int SYNC_TOLERANCE_S = 3, MATCH_TOLERANCE_S = 10;
    private static final int MAX_CACHE_FILES = 2000, MAX_RECORD_BYTES = 1 << 20;
    private static final List<String> LOOSE_ALBUMS = Arrays.asList("", "other songs", "single", "audius", "unknown",
            "unknown album", "<unknown>", "singles & other songs", "download", "downloads");

    private final String baseUrl;
    private final Http http;
    private final File cacheDir;
    private final LongSupplier clock;
    private final TtlCache<String, Lyrics> memory;
    private int writesSincePrune;

    /** @param cacheDir directory for cached results, or null for memory-only caching. */
    public LrclibApi(String baseUrl, Http http, File cacheDir, LongSupplier clock) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.cacheDir = cacheDir;
        this.clock = clock;
        this.memory = new TtlCache<>(64, clock);
    }

    /** lrclib.net with connect 6 s / read 10 s timeouts and the AURA User-Agent. */
    public static LrclibApi production(File cacheDir) {
        return new LrclibApi(BASE_URL, new Http(USER_AGENT, 6_000, 10_000, 2 << 20), cacheDir, System::currentTimeMillis);
    }

    /**
     * Lyrics for a song; {@link Lyrics#NONE} when LRCLIB has none. Blocking: call off the main thread.
     * @throws IOException when LRCLIB cannot be reached and nothing usable is cached.
     */
    public Lyrics fetch(String title, String artist, String album, long durationMs) throws IOException {
        String name = tidy(title), by = tidy(artist);
        if (name.isEmpty() || name.equalsIgnoreCase("Untitled track")) return Lyrics.NONE;
        long seconds = durationMs <= 0 ? 0 : Math.round(durationMs / 1000.0);
        String key = key(name, by, usableAlbum(album) ? tidy(album) : "", seconds);
        Lyrics hit = memory.get(key);
        if (hit != null) return hit;
        Cached disk = readDisk(key);
        if (disk != null && !disk.expired) {
            memory.put(key, disk.lyrics, 60 * 60_000L);
            return disk.lyrics;
        }
        Lyrics found;
        try {
            found = lookup(name, by, album, seconds);
        } catch (IOException unavailable) {
            if (disk != null && !disk.lyrics.isEmpty()) return disk.lyrics; // stale but better than nothing
            throw unavailable;
        }
        memory.put(key, found, 60 * 60_000L);
        writeDisk(key, found);
        return found;
    }

    /** Cached result without network access, or null when unknown. Reads one small file. */
    public Lyrics cached(String title, String artist, String album, long durationMs) {
        String name = tidy(title), by = tidy(artist);
        if (name.isEmpty()) return null;
        long seconds = durationMs <= 0 ? 0 : Math.round(durationMs / 1000.0);
        String key = key(name, by, usableAlbum(album) ? tidy(album) : "", seconds);
        Lyrics hit = memory.get(key);
        if (hit != null) return hit;
        Cached disk = readDisk(key);
        return disk == null ? null : disk.lyrics;
    }

    private Lyrics lookup(String title, String artist, String album, long seconds) throws IOException {
        boolean artistKnown = !artist.isEmpty() && !artist.equalsIgnoreCase("Unknown artist");
        if (artistKnown && usableAlbum(album) && seconds > 0) {
            Http.Response exact = http.get(baseUrl + "/api/get?track_name=" + Http.encode(title) + "&artist_name=" + Http.encode(artist)
                    + "&album_name=" + Http.encode(tidy(album)) + "&duration=" + seconds, "application/json");
            if (exact.ok()) {
                try {
                    Lyrics lyrics = fromRecord(new JSONObject(exact.text()), true);
                    if (!lyrics.isEmpty() || lyrics.instrumental) return lyrics;
                } catch (JSONException malformed) { throw new IOException("Malformed LRCLIB response", malformed); }
            } else if (exact.code != 404 && exact.code != 400) {
                throw new Http.StatusException(exact.code, "/api/get");
            }
        }
        List<String> titles = new ArrayList<>();
        titles.add(title);
        String cleaned = cleanTitle(title);
        if (!cleaned.isEmpty() && !cleaned.equalsIgnoreCase(title)) titles.add(cleaned);
        for (String query : titles) {
            Http.Response search = http.get(baseUrl + "/api/search?track_name=" + Http.encode(query)
                    + (artistKnown ? "&artist_name=" + Http.encode(artist) : ""), "application/json");
            if (!search.ok()) {
                if (search.code == 404 || search.code == 400) continue;
                throw new Http.StatusException(search.code, "/api/search");
            }
            try {
                Lyrics match = best(new JSONArray(search.text()), seconds, artistKnown ? artist : null);
                if (match != null) return match;
            } catch (JSONException malformed) { throw new IOException("Malformed LRCLIB response", malformed); }
        }
        return Lyrics.NONE;
    }

    /**
     * Best search result: same artist when any result matches it, then the closest duration
     * (synced before plain on ties). Null when nothing is within {@link #MATCH_TOLERANCE_S}.
     */
    static Lyrics best(JSONArray results, long seconds, String artist) {
        List<JSONObject> usable = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null) continue;
            if (!AudiusParser.text(o, "syncedLyrics").isEmpty() || !AudiusParser.text(o, "plainLyrics").isEmpty()
                    || AudiusParser.flag(o, "instrumental", false)) usable.add(o);
        }
        if (artist != null) {
            String wanted = fold(artist);
            List<JSONObject> sameArtist = new ArrayList<>();
            for (JSONObject o : usable) {
                String other = fold(AudiusParser.text(o, "artistName"));
                if (!other.isEmpty() && (other.equals(wanted) || other.contains(wanted) || wanted.contains(other))) sameArtist.add(o);
            }
            if (!sameArtist.isEmpty()) usable = sameArtist;
        }
        JSONObject best = null;
        long bestDiff = Long.MAX_VALUE;
        int bestRank = Integer.MAX_VALUE;
        for (JSONObject o : usable) {
            long duration = Math.round(AudiusParser.decimal(o, "duration"));
            long diff = seconds > 0 && duration > 0 ? Math.abs(duration - seconds) : (seconds > 0 ? MATCH_TOLERANCE_S : 0);
            int rank = !AudiusParser.text(o, "syncedLyrics").isEmpty() ? 0 : !AudiusParser.text(o, "plainLyrics").isEmpty() ? 1 : 2;
            if (diff < bestDiff || (diff == bestDiff && rank < bestRank)) { best = o; bestDiff = diff; bestRank = rank; }
        }
        if (best == null || (seconds > 0 && bestDiff > MATCH_TOLERANCE_S)) return null;
        return fromRecord(best, seconds <= 0 || bestDiff <= SYNC_TOLERANCE_S);
    }

    /** One LRCLIB record ({@code syncedLyrics}, {@code plainLyrics}, {@code instrumental}). */
    static Lyrics fromRecord(JSONObject o, boolean keepSync) {
        String synced = AudiusParser.text(o, "syncedLyrics"), plain = AudiusParser.text(o, "plainLyrics");
        boolean instrumental = AudiusParser.flag(o, "instrumental", false);
        List<Lyrics.Line> lines = keepSync && !synced.isEmpty() ? LrcParser.parse(synced) : Collections.<Lyrics.Line>emptyList();
        String text = !plain.isEmpty() ? plain : LrcParser.plainText(synced);
        return new Lyrics(lines, text, instrumental, Lyrics.SOURCE_LRCLIB);
    }

    /** Title without featured-artist credits, bracketed notes and " - Remastered"-style suffixes. */
    static String cleanTitle(String title) {
        String cleaned = title.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", " ");
        cleaned = cleaned.replaceAll("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*$", "");
        cleaned = cleaned.replaceAll("\\s+[-–—]\\s+.*$", "");
        return cleaned.trim().replaceAll("\\s{2,}", " ");
    }

    /** False for placeholder albums ("Other songs", "Single", "Unknown album"...). */
    static boolean usableAlbum(String album) {
        return album != null && !LOOSE_ALBUMS.contains(fold(album));
    }

    static String key(String title, String artist, String album, long seconds) {
        return fold(title) + "\u0001" + fold(artist) + "\u0001" + fold(album) + "\u0001" + seconds;
    }

    private static String tidy(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s{2,}", " ");
    }

    private static String fold(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ---- Disk cache ---------------------------------------------------------------------------

    private static final class Cached {
        final Lyrics lyrics; final boolean expired;
        Cached(Lyrics lyrics, boolean expired) { this.lyrics = lyrics; this.expired = expired; }
    }

    private File file(String key) { return new File(cacheDir, sha1(key) + ".json"); }

    private Cached readDisk(String key) {
        if (cacheDir == null) return null;
        File file = file(key);
        try {
            String text = JsonFiles.read(file, MAX_RECORD_BYTES);
            if (text == null) return null;
            JSONObject o = new JSONObject(text);
            long at = AudiusParser.number(o, "at"), now = clock.getAsLong();
            boolean found = AudiusParser.flag(o, "found", false);
            boolean expired = at > now + 60_000L || now - at > (found ? FOUND_TTL_MS : MISSING_TTL_MS);
            if (!found) return expired ? null : new Cached(Lyrics.NONE, false);
            List<Lyrics.Line> lines = new ArrayList<>();
            JSONArray stored = o.optJSONArray("lines");
            if (stored != null) for (int i = 0; i < stored.length(); i++) {
                JSONArray pair = stored.optJSONArray(i);
                if (pair != null && pair.length() == 2) lines.add(new Lyrics.Line(pair.optLong(0), pair.optString(1, "")));
            }
            return new Cached(new Lyrics(lines, AudiusParser.text(o, "plain"), AudiusParser.flag(o, "instrumental", false),
                    Lyrics.SOURCE_LRCLIB), expired);
        } catch (IOException | JSONException | RuntimeException unreadable) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        }
    }

    private void writeDisk(String key, Lyrics lyrics) {
        if (cacheDir == null) return;
        try {
            JSONObject o = new JSONObject();
            boolean found = !lyrics.isEmpty() || lyrics.instrumental;
            o.put("v", 1).put("at", clock.getAsLong()).put("found", found);
            if (found) {
                JSONArray lines = new JSONArray();
                for (Lyrics.Line line : lyrics.lines) lines.put(new JSONArray().put(line.timeMs).put(line.text));
                o.put("lines", lines).put("plain", lyrics.plain).put("instrumental", lyrics.instrumental);
            }
            JsonFiles.writeAtomic(file(key), o.toString());
            if (++writesSincePrune >= 50 || writesSincePrune == 1) { writesSincePrune = 1; prune(); }
        } catch (IOException | JSONException ignored) {
            // The cache is an optimisation; lookups still work without it.
        }
    }

    private void prune() {
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length <= MAX_CACHE_FILES) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - MAX_CACHE_FILES * 3 / 4; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
