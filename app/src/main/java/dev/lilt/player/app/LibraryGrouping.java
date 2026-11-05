package dev.lilt.player.app;

import dev.lilt.player.Track;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Groups library tracks into albums and artists (no I/O; runs on the scan thread).
 *
 * <p>Albums use {@link Track#albumKey()} (MusicRules collection keys, so "Other songs" collects
 * loose files and remote singles never form albums). The album artist is the most common album
 * artist of its tracks; mixed albums with no majority show "Various Artists". Tracks are ordered
 * by track number, then title. Artists group case-insensitively by track artist; their albums are
 * the albums containing their songs. Both lists sort with {@link SectionIndex#ORDER}.
 */
public final class LibraryGrouping {
    private LibraryGrouping() {}

    public static final String VARIOUS = "Various Artists";

    public static List<AlbumGroup> albums(List<Track> tracks) {
        Map<String, List<Track>> byKey = new LinkedHashMap<>();
        for (Track t : tracks) {
            if (t == null) continue;
            String key = t.albumKey();
            if (key.startsWith("remote-single:")) continue;
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<AlbumGroup> out = new ArrayList<>(byKey.size());
        for (Map.Entry<String, List<Track>> e : byKey.entrySet()) {
            List<Track> list = e.getValue();
            list.sort((a, b) -> {
                int na = a.trackNumber % 1000, nb = b.trackNumber % 1000; // MediaStore encodes disc*1000+track
                int da = a.trackNumber / 1000, db = b.trackNumber / 1000;
                if (da != db) return Integer.compare(da, db);
                if (na != nb) return Integer.compare(na <= 0 ? Integer.MAX_VALUE : na, nb <= 0 ? Integer.MAX_VALUE : nb);
                return SectionIndex.ORDER.compare(a.title, b.title);
            });
            out.add(new AlbumGroup(e.getKey(), list.get(0).album, albumArtist(list), 0, list));
        }
        SectionIndex.sort(out, a -> a.title);
        return out;
    }

    static String albumArtist(List<Track> list) {
        Map<String, Integer> counts = new HashMap<>();
        String best = list.get(0).albumArtist;
        int bestCount = 0;
        for (Track t : list) {
            int c = counts.merge(t.albumArtist, 1, Integer::sum);
            if (c > bestCount) { bestCount = c; best = t.albumArtist; }
        }
        return counts.size() > 1 && bestCount * 2 <= list.size() ? VARIOUS : best;
    }

    public static List<ArtistGroup> artists(List<Track> tracks, List<AlbumGroup> albums) {
        Map<String, List<Track>> byName = new LinkedHashMap<>();
        Map<String, String> display = new HashMap<>();
        for (Track t : tracks) {
            if (t == null) continue;
            String k = key(t.artist);
            byName.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
            display.putIfAbsent(k, t.artist);
        }
        Map<String, List<AlbumGroup>> albumsOf = new HashMap<>();
        for (AlbumGroup a : albums) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Track t : a.tracks) if (seen.add(key(t.artist))) albumsOf.computeIfAbsent(key(t.artist), x -> new ArrayList<>()).add(a);
        }
        List<ArtistGroup> out = new ArrayList<>(byName.size());
        for (Map.Entry<String, List<Track>> e : byName.entrySet()) {
            List<Track> list = e.getValue();
            SectionIndex.sort(list, t -> t.title);
            List<AlbumGroup> own = albumsOf.get(e.getKey());
            out.add(new ArtistGroup(display.get(e.getKey()), list, own == null ? new ArrayList<>() : own));
        }
        SectionIndex.sort(out, a -> a.name);
        return out;
    }

    /** Case-insensitive artist key. */
    public static String key(String artist) { return artist == null ? "" : artist.trim().toLowerCase(Locale.ROOT); }
}
