package dev.lilt.player.screens.discover;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accent- and case-insensitive text matching for search: folding, relevance scoring and the
 * character ranges to highlight. "Björk" matches "bjork", "drum and bass" matches "Drum & Bass".
 * Pure Java.
 */
public final class SearchText {
    private SearchText() {}

    /** Folded text plus, for every folded char, the index of the original char it came from. */
    static final class Folded {
        final String text;
        final int[] origin;
        Folded(String text, int[] origin) { this.text = text; this.origin = origin; }
    }

    /** Lower-case, accents removed, '&' read as "and", every run of other punctuation/space as one space. */
    public static String fold(String s) { return foldMapped(s).text; }

    static Folded foldMapped(String s) {
        if (s == null) return new Folded("", new int[0]);
        StringBuilder out = new StringBuilder(s.length());
        int[] origin = new int[s.length() * 3 + 8];
        boolean space = true; // suppress leading spaces
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String piece;
            if (c == '&') piece = " and ";
            else if (Character.isLetterOrDigit(c)) {
                piece = c < 128 ? String.valueOf(Character.toLowerCase(c))
                        : Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
                if (piece.equals("ß")) piece = "ss";
            } else if (Character.getType(c) == Character.NON_SPACING_MARK) piece = "";
            else if (c == '\'' || c == '’') piece = ""; // "don't" == "dont"
            else piece = " ";
            for (int k = 0; k < piece.length(); k++) {
                char p = piece.charAt(k);
                if (p == ' ') { if (space) continue; space = true; }
                else space = false;
                if (out.length() == origin.length) origin = java.util.Arrays.copyOf(origin, origin.length * 2);
                origin[out.length()] = i;
                out.append(p);
            }
        }
        int len = out.length();
        while (len > 0 && out.charAt(len - 1) == ' ') len--;
        return new Folded(out.substring(0, len), origin);
    }

    /** Folded tokens (words). */
    public static List<String> tokens(String s) {
        List<String> list = new ArrayList<>();
        String f = fold(s);
        if (f.isEmpty()) return list;
        for (String t : f.split(" ")) if (!t.isEmpty()) list.add(t);
        return list;
    }

    /** Relevance buckets returned by {@link #relevance}. */
    public static final double EXACT = 1.0, PREFIX = 0.9, WORDS_IN_ORDER = 0.8, WORDS = 0.72, CONTAINS = 0.5;

    /**
     * How well {@code text} answers {@code query}, 0..1: exact (1.0) &gt; text starts with the
     * query (0.9) &gt; every query word starts a word of the text in order (0.8) or in any order
     * (0.72) &gt; every query word occurs somewhere (0.5) &gt; partial (≤ 0.3) &gt; none (0).
     */
    public static double relevance(String query, String text) {
        String q = fold(query), t = fold(text);
        if (q.isEmpty() || t.isEmpty()) return 0;
        if (q.equals(t)) return EXACT;
        if (t.startsWith(q)) return PREFIX;
        String[] qt = q.split(" "), tt = t.split(" ");
        boolean all = true, inOrder = true;
        int cursor = 0;
        for (String w : qt) {
            int found = -1;
            for (int i = 0; i < tt.length; i++) if (tt[i].startsWith(w)) { found = i; if (i >= cursor) break; }
            if (found < 0) { all = false; break; }
            if (found < cursor) inOrder = false; else cursor = found + 1;
        }
        if (all) return inOrder ? WORDS_IN_ORDER : WORDS;
        int hits = 0;
        for (String w : qt) if (t.contains(w)) hits++;
        if (hits == qt.length) return CONTAINS;
        return 0.3 * hits / qt.length;
    }

    /**
     * Ranges of {@code text} (pairs of start inclusive, end exclusive, in original char indices,
     * sorted and merged) that match words of {@code query}: word prefixes first, else substrings.
     */
    public static int[] highlight(String text, String query) {
        Folded f = foldMapped(text);
        List<String> words = tokens(query);
        if (f.text.isEmpty() || words.isEmpty()) return new int[0];
        List<int[]> ranges = new ArrayList<>();
        for (String w : words) {
            int at = -1;
            for (int from = 0; from <= f.text.length() - w.length(); ) {
                int i = f.text.indexOf(w, from);
                if (i < 0) break;
                if (i == 0 || f.text.charAt(i - 1) == ' ') { at = i; break; }
                from = i + 1;
            }
            if (at < 0) at = f.text.indexOf(w);
            if (at < 0) continue;
            int start = f.origin[at];
            int end = f.origin[at + w.length() - 1] + 1;
            ranges.add(new int[] {start, Math.min(end, text.length())});
        }
        ranges.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] r : ranges) {
            if (!merged.isEmpty() && r[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], r[1]);
            } else merged.add(r);
        }
        int[] out = new int[merged.size() * 2];
        for (int i = 0; i < merged.size(); i++) { out[2 * i] = merged.get(i)[0]; out[2 * i + 1] = merged.get(i)[1]; }
        return out;
    }
}
