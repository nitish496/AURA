package dev.lilt.player.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure listening history behind {@link ListeningStats}: all-time totals per track id plus daily
 * buckets (UTC days) so "since" queries such as this week, this month or this year are possible.
 * Bounded: at most {@link #MAX_TRACKS} tracks and {@link #MAX_DAY_ROWS} day rows, oldest first out.
 * Thread-safe.
 */
public final class ListeningLedger {
    public static final long DAY_MS = 86_400_000L;
    public static final int MAX_TRACKS = 4000, MAX_DAY_ROWS = 20_000;
    private static final String VERSION = "L1";

    /** Per-track figures; for a "since" query plays and msListened cover that period only. */
    public static final class TrackStat {
        public final String id, title, artist, album;
        public final int plays;
        public final long lastPlayedAt, msListened;
        TrackStat(Entry e, int plays, long msListened) {
            id = e.id; title = e.title; artist = e.artist; album = e.album; lastPlayedAt = e.lastPlayedAt;
            this.plays = plays; this.msListened = msListened;
        }
    }

    public static final class ArtistStat {
        public final String name;
        public final int plays, tracks;
        public final long msListened;
        ArtistStat(String name, int plays, int tracks, long msListened) { this.name = name; this.plays = plays; this.tracks = tracks; this.msListened = msListened; }
    }

    private static final class Entry {
        final String id;
        String title = "", artist = "", album = "";
        int plays;
        long lastPlayedAt, lastTouched, msListened;
        Entry(String id) { this.id = id; }
    }

    private final LinkedHashMap<String, Entry> tracks = new LinkedHashMap<>();
    /** day -> track id -> {plays, ms} */
    private final TreeMap<Long, Map<String, long[]>> days = new TreeMap<>();
    private int dayRows;

    public synchronized void recordPlay(String id, String title, String artist, String album, long at) {
        Entry e = touch(id, title, artist, album, at);
        if (e == null) return;
        e.plays++;
        e.lastPlayedAt = Math.max(e.lastPlayedAt, at);
        bucket(e.id, at)[0]++;
        trim();
    }

    public synchronized void addListened(String id, String title, String artist, String album, long ms, long at) {
        if (ms <= 0) return;
        Entry e = touch(id, title, artist, album, at);
        if (e == null) return;
        e.msListened += ms;
        bucket(e.id, at)[1] += ms;
        trim();
    }

    private Entry touch(String id, String title, String artist, String album, long at) {
        if (id == null || id.isEmpty()) return null;
        Entry e = tracks.get(id);
        if (e == null) { e = new Entry(id); tracks.put(id, e); }
        if (title != null && !title.isEmpty()) e.title = title;
        if (artist != null && !artist.isEmpty()) e.artist = artist;
        if (album != null && !album.isEmpty()) e.album = album;
        e.lastTouched = Math.max(e.lastTouched, at);
        return e;
    }

    private long[] bucket(String id, long at) {
        Map<String, long[]> day = days.computeIfAbsent(Math.floorDiv(at, DAY_MS), d -> new HashMap<>());
        long[] row = day.get(id);
        if (row == null) { row = new long[2]; day.put(id, row); dayRows++; }
        return row;
    }

    private void trim() {
        while (dayRows > MAX_DAY_ROWS && !days.isEmpty()) dayRows -= days.pollFirstEntry().getValue().size();
        while (tracks.size() > MAX_TRACKS) {
            Entry oldest = null;
            for (Entry e : tracks.values()) if (oldest == null || e.lastTouched < oldest.lastTouched) oldest = e;
            tracks.remove(oldest.id);
            for (Map<String, long[]> day : days.values()) if (day.remove(oldest.id) != null) dayRows--;
        }
    }

    public synchronized TrackStat track(String id) {
        Entry e = id == null ? null : tracks.get(id);
        return e == null ? null : new TrackStat(e, e.plays, e.msListened);
    }

    /** Most played tracks; ties broken by listening time, then recency. sinceMs <= 0 means all time. */
    public synchronized List<TrackStat> topTracks(int n, long sinceMs) {
        List<TrackStat> result = new ArrayList<>();
        if (n <= 0) return result;
        for (Map.Entry<String, long[]> total : totals(sinceMs).entrySet()) {
            Entry e = tracks.get(total.getKey());
            long[] t = total.getValue();
            if (e != null && t[0] > 0) result.add(new TrackStat(e, (int) Math.min(Integer.MAX_VALUE, t[0]), t[1]));
        }
        result.sort(Comparator.comparingInt((TrackStat s) -> s.plays).reversed()
                .thenComparing(Comparator.comparingLong((TrackStat s) -> s.msListened).reversed())
                .thenComparing(Comparator.comparingLong((TrackStat s) -> s.lastPlayedAt).reversed()));
        return result.size() > n ? new ArrayList<>(result.subList(0, n)) : result;
    }

    /** Most played artists (case-insensitive, unknown artists excluded). */
    public synchronized List<ArtistStat> topArtists(int n, long sinceMs) {
        List<ArtistStat> result = new ArrayList<>();
        if (n <= 0) return result;
        Map<String, long[]> byArtist = new HashMap<>();      // key -> {plays, ms, tracks}
        Map<String, Entry> display = new HashMap<>();
        for (Map.Entry<String, long[]> total : totals(sinceMs).entrySet()) {
            Entry e = tracks.get(total.getKey());
            if (e == null) continue;
            String key = artistKey(e.artist);
            if (key.isEmpty()) continue;
            long[] a = byArtist.computeIfAbsent(key, k -> new long[3]);
            a[0] += total.getValue()[0]; a[1] += total.getValue()[1]; a[2]++;
            Entry shown = display.get(key);
            if (shown == null || e.lastTouched > shown.lastTouched) display.put(key, e);
        }
        for (Map.Entry<String, long[]> a : byArtist.entrySet()) {
            long[] v = a.getValue();
            if (v[0] > 0) result.add(new ArtistStat(display.get(a.getKey()).artist, (int) Math.min(Integer.MAX_VALUE, v[0]), (int) v[2], v[1]));
        }
        result.sort(Comparator.comparingInt((ArtistStat s) -> s.plays).reversed()
                .thenComparing(Comparator.comparingLong((ArtistStat s) -> s.msListened).reversed())
                .thenComparing(s -> s.name.toLowerCase(Locale.ROOT)));
        return result.size() > n ? new ArrayList<>(result.subList(0, n)) : result;
    }

    public synchronized long totalListenedMs(long sinceMs) {
        long sum = 0;
        for (long[] t : totals(sinceMs).values()) sum += t[1];
        return sum;
    }

    public synchronized long totalMinutes(long sinceMs) { return totalListenedMs(sinceMs) / 60_000L; }

    /** Most recently played tracks, newest first. */
    public synchronized List<TrackStat> recent(int n) {
        List<TrackStat> result = new ArrayList<>();
        for (Entry e : tracks.values()) if (e.plays > 0) result.add(new TrackStat(e, e.plays, e.msListened));
        result.sort(Comparator.comparingLong((TrackStat s) -> s.lastPlayedAt).reversed());
        return result.size() > n ? new ArrayList<>(result.subList(0, Math.max(0, n))) : result;
    }

    public synchronized boolean isEmpty() { return tracks.isEmpty(); }

    public synchronized void clear() { tracks.clear(); days.clear(); dayRows = 0; }

    private Map<String, long[]> totals(long sinceMs) {
        Map<String, long[]> result = new HashMap<>();
        if (sinceMs <= 0) {
            for (Entry e : tracks.values()) result.put(e.id, new long[]{e.plays, e.msListened});
            return result;
        }
        for (Map<String, long[]> day : days.tailMap(Math.floorDiv(sinceMs, DAY_MS), true).values()) {
            for (Map.Entry<String, long[]> row : day.entrySet()) {
                long[] sum = result.computeIfAbsent(row.getKey(), k -> new long[2]);
                sum[0] += row.getValue()[0]; sum[1] += row.getValue()[1];
            }
        }
        return result;
    }

    static String artistKey(String artist) {
        String key = artist == null ? "" : artist.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return key.equals("unknown artist") || key.equals("<unknown>") ? "" : key;
    }

    public synchronized String encode() {
        StringBuilder out = new StringBuilder(64 + tracks.size() * 96 + dayRows * 24);
        out.append(VERSION).append('\n');
        Map<String, Integer> ordinal = new HashMap<>();
        int next = 0;
        for (Entry e : tracks.values()) {
            ordinal.put(e.id, next++);
            out.append("T\t").append(escape(e.id)).append('\t').append(escape(e.title)).append('\t').append(escape(e.artist))
                    .append('\t').append(escape(e.album)).append('\t').append(e.plays).append('\t').append(e.lastPlayedAt)
                    .append('\t').append(e.msListened).append('\t').append(e.lastTouched).append('\n');
        }
        for (Map.Entry<Long, Map<String, long[]>> day : days.entrySet()) {
            for (Map.Entry<String, long[]> row : day.getValue().entrySet()) {
                Integer index = ordinal.get(row.getKey());
                if (index == null) continue;
                out.append("D\t").append(day.getKey()).append('\t').append(index).append('\t')
                        .append(row.getValue()[0]).append('\t').append(row.getValue()[1]).append('\n');
            }
        }
        return out.toString();
    }

    /** Decodes {@link #encode()} output; malformed lines are skipped, unknown versions give an empty ledger. */
    public static ListeningLedger decode(String text) {
        ListeningLedger ledger = new ListeningLedger();
        if (text == null || !text.startsWith(VERSION + "\n")) return ledger;
        List<String> ids = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            try {
                if (f[0].equals("T") && f.length >= 9) {
                    Entry e = new Entry(unescape(f[1]));
                    if (e.id.isEmpty() || ledger.tracks.containsKey(e.id)) { ids.add(""); continue; }
                    e.title = unescape(f[2]); e.artist = unescape(f[3]); e.album = unescape(f[4]);
                    e.plays = Math.max(0, Integer.parseInt(f[5])); e.lastPlayedAt = Long.parseLong(f[6]);
                    e.msListened = Math.max(0, Long.parseLong(f[7])); e.lastTouched = Long.parseLong(f[8]);
                    ledger.tracks.put(e.id, e);
                    ids.add(e.id);
                } else if (f[0].equals("D") && f.length >= 5) {
                    int index = Integer.parseInt(f[2]);
                    if (index < 0 || index >= ids.size() || ids.get(index).isEmpty()) continue;
                    Map<String, long[]> day = ledger.days.computeIfAbsent(Long.parseLong(f[1]), d -> new HashMap<>());
                    if (day.put(ids.get(index), new long[]{Math.max(0, Long.parseLong(f[3])), Math.max(0, Long.parseLong(f[4]))}) == null) ledger.dayRows++;
                }
            } catch (RuntimeException malformed) { /* skip the line */ }
        }
        ledger.trim();
        return ledger;
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '\t': out.append("\\t"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    private static String unescape(String value) {
        if (value.indexOf('\\') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char n = value.charAt(++i);
                out.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else out.append(c);
        }
        return out.toString();
    }
}
