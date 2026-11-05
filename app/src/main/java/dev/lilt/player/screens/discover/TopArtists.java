package dev.lilt.player.screens.discover;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Top Artists" derived from a trending chart: every chart entry credits its artist with
 * 1/sqrt(rank), so an artist with several charting songs outranks a one-off, and the best rank
 * breaks ties. Artists are keyed by Audius user id, or by folded name when the id is unknown.
 * Pure Java.
 */
public final class TopArtists {
    private TopArtists() {}

    public static final class Entry {
        public final String id, name;
        /** Index in the chart of this artist's best entry (use it for a representative track). */
        public final int bestIndex;
        public final int songs;
        public final double score;
        Entry(String id, String name, int bestIndex, int songs, double score) {
            this.id = id; this.name = name; this.bestIndex = bestIndex; this.songs = songs; this.score = score;
        }
    }

    /** {@code ids.get(i)} / {@code names.get(i)} describe the artist of chart entry {@code i} (rank i + 1). */
    public static List<Entry> fromChart(List<String> ids, List<String> names, int limit) {
        Map<String, double[]> acc = new LinkedHashMap<>(); // key → {score, bestIndex, songs}
        Map<String, String[]> who = new LinkedHashMap<>();
        int n = Math.min(ids.size(), names.size());
        for (int i = 0; i < n; i++) {
            String id = ids.get(i) == null ? "" : ids.get(i).trim();
            String name = names.get(i) == null ? "" : names.get(i).trim();
            if (name.isEmpty() || name.equals("Unknown artist")) continue;
            String key = id.isEmpty() ? "name:" + SearchText.fold(name) : "id:" + id;
            double[] a = acc.get(key);
            if (a == null) { a = new double[] {0, i, 0}; acc.put(key, a); who.put(key, new String[] {id, name}); }
            a[0] += 1.0 / Math.sqrt(i + 1);
            a[2] += 1;
        }
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            String[] w = who.get(e.getKey());
            double[] a = e.getValue();
            out.add(new Entry(w[0], w[1], (int) a[1], (int) a[2], a[0]));
        }
        out.sort((a, b) -> a.score != b.score ? Double.compare(b.score, a.score) : Integer.compare(a.bestIndex, b.bestIndex));
        return out.size() > limit ? new ArrayList<>(out.subList(0, Math.max(0, limit))) : out;
    }
}
