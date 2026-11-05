package dev.lilt.player.stream;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Blocking Audius API v1 client core (pure Java + org.json, no Android types; {@link AudiusClient}
 * is the app-facing facade). Thread-safe.
 *
 * <p>Hosts: the host list from {@code GET https://api.audius.co} is probed in parallel and the
 * fastest healthy host is used until it fails (I/O error, timeout, 5xx, 408/429 or a malformed
 * body). A failed host cools down for five minutes and the request retries on another host, then
 * on the {@code https://api.audius.co} gateway. 4xx answers are returned to the caller without
 * failover. Every request carries {@code app_name=AURA}. Parsed results are cached in memory
 * (trending and catalogue pages 10 minutes, searches 2 minutes).
 *
 * <p>All methods except {@link #streamUrl(String)}, {@link #host()} and the static helpers block
 * on the network: call them from a worker thread.
 */
public final class AudiusApi {
    public static final String DISCOVERY_URL = "https://api.audius.co";
    public static final String GATEWAY = "https://api.audius.co";
    public static final String APP_NAME = "AURA";
    /** App-wide prefix of remote track ids ({@code "audius:" + rawId}). */
    public static final String ID_PREFIX = "audius:";

    static final long TRENDING_TTL_MS = 10 * 60_000L, SEARCH_TTL_MS = 2 * 60_000L, CATALOG_TTL_MS = 10 * 60_000L;
    static final int MAX_ATTEMPTS = 3, PROBE_FANOUT = 4, MAX_LIMIT = 100;
    static final String PROBE_PATH = "/v1/tracks/trending?limit=1";

    /** Curated Audius genre names (exact API spelling) for Browse; non-music categories left out. */
    private static final List<String> GENRES = Collections.unmodifiableList(Arrays.asList(
            "Electronic", "Hip-Hop/Rap", "Pop", "R&B/Soul", "Alternative", "Rock", "Lo-Fi", "House", "Techno",
            "Drum & Bass", "Dubstep", "Trap", "Ambient", "Downtempo", "Jazz", "Classical", "Soundtrack", "Latin",
            "Reggae", "Dancehall", "Afrobeats", "World", "Folk", "Acoustic", "Country", "Funk", "Blues", "Metal",
            "Punk", "Experimental", "Hyperpop", "Devotional"));

    /** Receives the newly selected host (for persistence across launches). */
    public interface HostListener { void onHostSelected(String host, List<String> candidates); }

    interface Parser<T> { T parse(String body) throws JSONException; }

    private final String discoveryUrl;
    private final Http http, probeHttp;
    private final long probeTimeoutMs;
    private final LongSupplier clock;
    private final Random random;
    private final boolean allowLocalHosts;
    private final HostPool pool;
    private final TtlCache<String, Object> cache;
    private final Object selection = new Object();
    private final ExecutorService probes;
    private final Map<String, CatalogPlaylist> knownPlaylists = Collections.synchronizedMap(
            new LinkedHashMap<String, CatalogPlaylist>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, CatalogPlaylist> eldest) { return size() > 256; }
            });
    private volatile HostListener hostListener;

    /**
     * @param allowLocalHosts accept {@code http://127.0.0.1} hosts (tests only); production accepts https origins only.
     */
    public AudiusApi(String discoveryUrl, String gateway, Http http, Http probeHttp, long probeTimeoutMs,
                     LongSupplier clock, Random random, boolean allowLocalHosts) {
        this.discoveryUrl = discoveryUrl;
        this.http = http;
        this.probeHttp = probeHttp;
        this.probeTimeoutMs = probeTimeoutMs;
        this.clock = clock;
        this.random = random;
        this.allowLocalHosts = allowLocalHosts;
        this.pool = new HostPool(stripSlash(gateway));
        this.cache = new TtlCache<>(64, clock);
        AtomicInteger count = new AtomicInteger();
        this.probes = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "aura-audius-probe-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Production endpoints: connect 6 s / read 10 s for API calls, 3 s / 4 s for probes. */
    public static AudiusApi production(String userAgent) {
        return new AudiusApi(DISCOVERY_URL, GATEWAY, new Http(userAgent, 6_000, 10_000, 8 << 20),
                new Http(userAgent, 3_000, 4_000, 1 << 20), 4_000, System::currentTimeMillis, new Random(), false);
    }

    public void setHostListener(HostListener listener) { hostListener = listener; }

    public static List<String> genres() { return GENRES; }

    // ---- Catalogue ---------------------------------------------------------------------------

    /** Trending tracks. {@code genre} null/"" = all genres; {@code time} "week" (default), "month", "year" or "allTime". */
    public List<AudiusTrack> trending(String genre, String time, int limit) throws IOException {
        StringBuilder path = new StringBuilder("/v1/tracks/trending?time=").append(normalizeTime(time)).append("&limit=").append(clamp(limit));
        if (genre != null && !genre.trim().isEmpty()) path.append("&genre=").append(Http.encode(genre.trim()));
        return first(get(path.toString(), TRENDING_TTL_MS, body -> AudiusParser.tracks(body, null)), limit);
    }

    public List<AudiusTrack> searchTracks(String query, int limit) throws IOException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return Collections.emptyList();
        return first(get("/v1/tracks/search?query=" + Http.encode(q) + "&limit=" + clamp(limit), SEARCH_TTL_MS,
                body -> AudiusParser.tracks(body, null)), limit);
    }

    public List<CatalogArtist> searchArtists(String query, int limit) throws IOException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return Collections.emptyList();
        return first(get("/v1/users/search?query=" + Http.encode(q) + "&limit=" + clamp(limit), SEARCH_TTL_MS, AudiusParser::users), limit);
    }

    public List<CatalogPlaylist> searchPlaylists(String query, int limit) throws IOException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return Collections.emptyList();
        return remember(first(get("/v1/playlists/search?query=" + Http.encode(q) + "&limit=" + clamp(limit), SEARCH_TTL_MS,
                AudiusParser::playlists), limit));
    }

    public List<CatalogPlaylist> trendingPlaylists(int limit) throws IOException {
        return remember(first(get("/v1/playlists/trending?time=week&limit=" + clamp(limit), TRENDING_TTL_MS, AudiusParser::playlists), limit));
    }

    /** Tracks of a playlist/album; album tracks are labelled with the album name when the album is known. */
    public List<AudiusTrack> playlistTracks(String playlistId) throws IOException {
        String raw = rawId(playlistId);
        CatalogPlaylist known = knownPlaylists.get(raw);
        return playlistTracks(raw, known != null && known.isAlbum ? known.name : null);
    }

    public List<AudiusTrack> playlistTracks(CatalogPlaylist playlist) throws IOException {
        knownPlaylists.put(playlist.id, playlist);
        return playlistTracks(playlist.id, playlist.isAlbum ? playlist.name : null);
    }

    private List<AudiusTrack> playlistTracks(String raw, String album) throws IOException {
        requireId(raw);
        String path = "/v1/playlists/" + Http.encodePath(raw) + "/tracks";
        String key = album == null ? path : path + "#album=" + album;
        return getKeyed(key, path, CATALOG_TTL_MS, body -> AudiusParser.tracks(body, album));
    }

    /** An artist's tracks, most played first. */
    public List<AudiusTrack> artistTracks(String userId, int limit) throws IOException {
        String raw = rawId(userId);
        requireId(raw);
        return first(get("/v1/users/" + Http.encodePath(raw) + "/tracks?sort=plays&limit=" + clamp(limit), CATALOG_TTL_MS,
                body -> AudiusParser.tracks(body, null)), limit);
    }

    /** One track, or null when it does not exist (404) or cannot be streamed. */
    public AudiusTrack track(String id) throws IOException {
        String raw = rawId(id);
        requireId(raw);
        try {
            List<AudiusTrack> one = get("/v1/tracks/" + Http.encodePath(raw), CATALOG_TTL_MS, body -> {
                AudiusTrack parsed = AudiusParser.singleTrack(body);
                return parsed == null ? Collections.<AudiusTrack>emptyList() : Collections.singletonList(parsed);
            });
            return one.isEmpty() ? null : one.get(0);
        } catch (Http.StatusException status) {
            if (status.code == 404) return null;
            throw status;
        }
    }

    /**
     * Tracks related to a seed: the seed artist's most played tracks interleaved with trending
     * tracks of the seed genre, excluding the seed. For a remote seed missing artist/genre the seed
     * is looked up first. A local seed (no remote id) matches the artist by exact name only.
     * Partial results are returned when one source fails; throws only when every source fails.
     */
    public List<AudiusTrack> related(String seedId, String artistId, String genre, String artistName, int limit) throws IOException {
        String raw = seedId != null && seedId.startsWith(ID_PREFIX) ? rawId(seedId) : null;
        String artist = artistId == null ? "" : rawId(artistId);
        String seedGenre = genre == null ? "" : genre.trim();
        IOException failure = null;
        if (raw != null && (artist.isEmpty() || seedGenre.isEmpty())) {
            try {
                AudiusTrack seed = track(raw);
                if (seed != null) {
                    if (artist.isEmpty()) artist = seed.artistId;
                    if (seedGenre.isEmpty()) seedGenre = seed.genre;
                }
            } catch (IOException e) { failure = e; }
        }
        int wanted = clamp(limit);
        List<AudiusTrack> byArtist = Collections.emptyList(), byGenre = Collections.emptyList();
        boolean any = false;
        if (!artist.isEmpty()) {
            try { byArtist = artistTracks(artist, wanted); any = true; } catch (IOException e) { failure = e; }
        } else if (raw == null && artistName != null && !artistName.trim().isEmpty() && !"Unknown artist".equals(artistName)) {
            try {
                List<AudiusTrack> found = new ArrayList<>();
                String wantedName = artistName.trim().toLowerCase(Locale.ROOT);
                for (AudiusTrack t : searchTracks(artistName, wanted)) if (t.artist.trim().toLowerCase(Locale.ROOT).equals(wantedName)) found.add(t);
                byArtist = found; any = true;
            } catch (IOException e) { failure = e; }
        }
        if (!seedGenre.isEmpty()) {
            try { byGenre = trending(seedGenre, "month", wanted); any = true; } catch (IOException e) { failure = e; }
        }
        if (!any && failure != null) throw failure;
        return mixRelated(byArtist, byGenre, raw, limit);
    }

    /** Interleaves artist and genre picks (artist first), skipping the seed and duplicates. Pure. */
    static List<AudiusTrack> mixRelated(List<AudiusTrack> byArtist, List<AudiusTrack> byGenre, String excludeRawId, int limit) {
        List<AudiusTrack> mixed = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (excludeRawId != null) seen.add(excludeRawId);
        int a = 0, g = 0, max = Math.max(0, limit);
        while (mixed.size() < max && (a < byArtist.size() || g < byGenre.size())) {
            if (a < byArtist.size()) { AudiusTrack t = byArtist.get(a++); if (seen.add(t.id)) mixed.add(t); }
            if (mixed.size() >= max) break;
            if (g < byGenre.size()) { AudiusTrack t = byGenre.get(g++); if (seen.add(t.id)) mixed.add(t); }
        }
        return mixed;
    }

    // ---- Streams and hosts -------------------------------------------------------------------

    /**
     * Stream URL on the current healthy host (gateway when none is known yet). Non-blocking. The
     * endpoint 302-redirects to the audio file.
     */
    public String streamUrl(String trackId) {
        String host = pool.last();
        return streamUrlOn(host == null ? pool.gateway() : host, trackId);
    }

    /**
     * Stream URL to use for a stored track: the current healthy host when one is known; otherwise
     * the stored URL while its host is not known to be failing; otherwise the gateway. Non-blocking.
     */
    public String freshStreamUrl(String trackId, String storedUrl) {
        String host = pool.last();
        if (host == null) {
            String stored = hostOf(storedUrl);
            String storedId = trackIdFromStreamUrl(storedUrl);
            if (stored != null && storedId != null && rawId(storedId).equals(rawId(trackId)) && !pool.cooling(stored, clock.getAsLong())) return storedUrl;
            host = pool.gateway();
        }
        return streamUrlOn(host, trackId);
    }

    static String streamUrlOn(String host, String trackId) {
        return host + "/v1/tracks/" + Http.encodePath(rawId(trackId)) + "/stream?app_name=" + APP_NAME;
    }

    /** Marks the host of a failed stream URL as unhealthy so the next URL uses another host. Non-blocking. */
    public void hostFailed(String url) {
        String origin = hostOf(url);
        if (origin != null) pool.markFailed(origin, clock.getAsLong());
    }

    /** Current host (null before the first selection). */
    public String host() { return pool.last(); }

    /** Discovered candidates (for persistence). */
    public List<String> candidates() { return pool.candidates(); }

    /** Restores a host remembered from an earlier launch (ignored when older than a day). */
    public void restore(String host, List<String> candidates, long savedAt) {
        long now = clock.getAsLong();
        if (savedAt > now || now - savedAt > 24 * 60 * 60_000L) return;
        List<String> valid = new ArrayList<>();
        if (candidates != null) for (String c : candidates) { String o = AudiusParser.origin(c, allowLocalHosts); if (o != null) valid.add(o); }
        if (!valid.isEmpty() && pool.candidates().isEmpty()) pool.setCandidates(valid, savedAt);
        String origin = AudiusParser.origin(host, allowLocalHosts);
        if (origin != null && pool.last() == null && !origin.equals(pool.gateway())) pool.use(origin, now);
    }

    /** Selects a host now (probing if needed). Blocking; useful to warm up at app start. */
    public String selectHost() { return chooseHost(now()); }

    public void clearCache() { cache.clear(); }

    /** {@code "audius:abc"} or {@code "abc"} → {@code "abc"}. */
    public static String rawId(String id) {
        if (id == null) return "";
        String trimmed = id.trim();
        return trimmed.startsWith(ID_PREFIX) ? trimmed.substring(ID_PREFIX.length()) : trimmed;
    }

    /** {@code "audius:<id>"} for an Audius stream URL ({@code /v1/tracks/<id>/stream}), else null. */
    public static String trackIdFromStreamUrl(String url) {
        if (url == null) return null;
        try {
            String path = URI.create(url).getRawPath();
            if (path == null) return null;
            String[] parts = path.split("/");
            // ["", "v1", "tracks", "<id>", "stream"]
            if (parts.length == 5 && parts[1].equals("v1") && parts[2].equals("tracks") && parts[4].equals("stream") && AudiusParser.isId(parts[3])) {
                return ID_PREFIX + parts[3];
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    static String hostOf(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            return (uri.getScheme() + "://" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "")).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException malformed) { return null; }
    }

    static String normalizeTime(String time) {
        if (time == null) return "week";
        switch (time.trim()) {
            case "month": return "month";
            case "year": return "year";
            case "allTime": case "alltime": case "all": return "allTime";
            default: return "week";
        }
    }

    // ---- Transport ----------------------------------------------------------------------------

    private <T> T get(String pathAndQuery, long ttlMs, Parser<T> parser) throws IOException {
        return getKeyed(pathAndQuery, pathAndQuery, ttlMs, parser);
    }

    private <T> T getKeyed(String cacheKey, String pathAndQuery, long ttlMs, Parser<T> parser) throws IOException {
        @SuppressWarnings("unchecked") T hit = (T) cache.get(cacheKey);
        if (hit != null) return hit;
        String what = pathAndQuery.indexOf('?') >= 0 ? pathAndQuery.substring(0, pathAndQuery.indexOf('?')) : pathAndQuery;
        IOException failure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String host = chooseHost(now());
            String url = host + pathAndQuery + (pathAndQuery.indexOf('?') >= 0 ? '&' : '?') + "app_name=" + APP_NAME;
            Http.Response response;
            try {
                response = http.get(url, "application/json");
            } catch (IOException transport) {
                pool.markFailed(host, now());
                failure = transport;
                continue;
            }
            if (response.ok()) {
                T value;
                try {
                    value = parser.parse(response.text());
                } catch (JSONException | RuntimeException malformed) {
                    pool.markFailed(host, now());
                    failure = new IOException("Malformed Audius response for " + what, malformed);
                    continue;
                }
                if (value instanceof List) value = immutable(value);
                cache.put(cacheKey, value, ttlMs);
                return value;
            }
            if (response.code >= 500 || response.code == 429 || response.code == 408) {
                pool.markFailed(host, now());
                failure = new Http.StatusException(response.code, what);
                continue;
            }
            throw new Http.StatusException(response.code, what);
        }
        throw failure == null ? new IOException("Audius is unavailable") : failure;
    }

    @SuppressWarnings("unchecked")
    private static <T> T immutable(T list) { return (T) Collections.unmodifiableList((List<Object>) list); }

    private String chooseHost(long now) {
        String current = pool.current(now);
        if (current != null) return current;
        synchronized (selection) {
            now = now();
            current = pool.current(now);
            if (current != null) return current;
            if (pool.discoveryDue(now)) discover(now);
            List<String> round = pool.probeOrder(now, PROBE_FANOUT, random);
            String winner = HostPool.firstHealthy(round, this::probe, probes, probeTimeoutMs);
            now = now();
            if (winner != null) {
                pool.use(winner, now);
                HostListener listener = hostListener;
                if (listener != null) {
                    try { listener.onHostSelected(winner, pool.candidates()); } catch (RuntimeException ignored) { }
                }
                return winner;
            }
            for (String loser : round) pool.markFailed(loser, now);
            pool.use(pool.gateway(), now);
            return pool.gateway();
        }
    }

    private void discover(long now) {
        pool.discoveryAttempted(now);
        try {
            Http.Response response = probeHttp.get(discoveryUrl, "application/json");
            if (!response.ok()) return;
            List<String> hosts = AudiusParser.hosts(response.text(), allowLocalHosts);
            if (!hosts.isEmpty()) pool.setCandidates(hosts, now);
        } catch (IOException | JSONException | RuntimeException unavailable) {
            // Discovery is optional: the gateway still serves the API.
        }
    }

    private boolean probe(String host) throws IOException {
        Http.Response response = probeHttp.get(host + PROBE_PATH + "&app_name=" + APP_NAME, "application/json");
        if (!response.ok()) return false;
        try { return new JSONObject(response.text()).optJSONArray("data") != null; }
        catch (JSONException malformed) { return false; }
    }

    private List<CatalogPlaylist> remember(List<CatalogPlaylist> playlists) {
        for (CatalogPlaylist p : playlists) knownPlaylists.put(p.id, p);
        return playlists;
    }

    private long now() { return clock.getAsLong(); }

    private static <T> List<T> first(List<T> list, int limit) {
        int max = clamp(limit);
        return list.size() <= max ? list : Collections.unmodifiableList(new ArrayList<>(list.subList(0, max)));
    }

    private static int clamp(int limit) { return Math.max(1, Math.min(MAX_LIMIT, limit)); }

    private static void requireId(String raw) throws IOException {
        if (!AudiusParser.isId(raw)) throw new IOException("Invalid Audius id");
    }

    private static String stripSlash(String url) {
        String value = url;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
