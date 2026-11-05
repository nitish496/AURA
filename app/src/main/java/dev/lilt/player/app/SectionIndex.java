package dev.lilt.player.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Alphabetical sections for long song lists (the A–Z fast scroller): titles are filed under their
 * first letter with accents folded ("Élan" → E); anything else (digits, symbols, other scripts)
 * goes under "#", which sorts last, as in Apple Music. Pure Java.
 */
public final class SectionIndex {
    /** Section labels in display order. */
    public static final String[] LETTERS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"};
    public static final int OTHER = 26;

    private final int[] sectionOfPosition;
    private final int[] firstPosition; // per section; -1 when empty

    /** {@code titles} must already be in {@link #ORDER} order. */
    public SectionIndex(List<String> titles) {
        sectionOfPosition = new int[titles.size()];
        firstPosition = new int[LETTERS.length];
        java.util.Arrays.fill(firstPosition, -1);
        for (int i = 0; i < titles.size(); i++) {
            int s = section(titles.get(i));
            sectionOfPosition[i] = s;
            if (firstPosition[s] < 0) firstPosition[s] = i;
        }
    }

    /** Section (0..26) of a title. */
    public static int section(String title) {
        char c = initial(title);
        return c >= 'A' && c <= 'Z' ? c - 'A' : OTHER;
    }

    /** First letter, upper-case ASCII with accents removed, or '#' . */
    public static char initial(String title) {
        if (title == null) return '#';
        for (int i = 0; i < title.length(); i++) {
            char raw = title.charAt(i);
            if (Character.isWhitespace(raw) || raw == '"' || raw == '\'' || raw == '(' || raw == '[' || raw == '¡' || raw == '¿') continue;
            String folded = Normalizer.normalize(String.valueOf(raw), Normalizer.Form.NFD);
            char c = Character.toUpperCase(folded.charAt(0));
            return c >= 'A' && c <= 'Z' ? c : '#';
        }
        return '#';
    }

    /** Sort key: letter sections first (accent- and case-insensitive), "#" titles last. */
    public static String sortKey(String title) {
        String t = title == null ? "" : title.trim();
        String folded = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return (section(t) == OTHER ? "\u0001" : "\u0000") + folded;
    }

    /** Comparator for display titles. */
    public static final Comparator<String> ORDER = new Comparator<String>() {
        @Override public int compare(String a, String b) { return sortKey(a).compareTo(sortKey(b)); }
    };

    /** Sorts {@code items} by title using {@link #ORDER} (stable). */
    public static <T> void sort(List<T> items, java.util.function.Function<T, String> title) {
        List<String> keys = new ArrayList<>(items.size());
        for (T item : items) keys.add(sortKey(title.apply(item)));
        Integer[] order = new Integer[items.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (x, y) -> keys.get(x).compareTo(keys.get(y)));
        List<T> sorted = new ArrayList<>(items.size());
        for (Integer i : order) sorted.add(items.get(i));
        items.clear();
        items.addAll(sorted);
    }

    public int size() { return sectionOfPosition.length; }

    public int sectionForPosition(int position) {
        if (sectionOfPosition.length == 0) return 0;
        return sectionOfPosition[Math.max(0, Math.min(sectionOfPosition.length - 1, position))];
    }

    /** First position of {@code section}, or of the nearest following non-empty section (then preceding). */
    public int positionForSection(int section) {
        int s = Math.max(0, Math.min(LETTERS.length - 1, section));
        for (int i = s; i < LETTERS.length; i++) if (firstPosition[i] >= 0) return firstPosition[i];
        for (int i = s - 1; i >= 0; i--) if (firstPosition[i] >= 0) return firstPosition[i];
        return 0;
    }

    public boolean hasSection(int section) { return firstPosition[section] >= 0; }

    /** Labels of the sections that have items (for a compact index). */
    public List<String> present() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < LETTERS.length; i++) if (firstPosition[i] >= 0) out.add(LETTERS[i]);
        return Collections.unmodifiableList(out);
    }
}
