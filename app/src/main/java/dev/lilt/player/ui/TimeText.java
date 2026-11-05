package dev.lilt.player.ui;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats times and counts the same way on every screen. Render results with tabular figures
 * ({@link Type#tabular}). Pure Java.
 */
public final class TimeText {
    private TimeText() {}

    /** U+2212 MINUS SIGN: the same width as digits in Inter's tabular figures, unlike '-'. */
    public static final char MINUS = '−';
    /** Separator used between metadata items ("Album · 2025"). */
    public static final String DOT = " · ";

    /** Playback clock: "0:07", "3:41", "1:02:03". Negative values clamp to 0. */
    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** Remaining time shown at the end of a scrubber: "−2:48". */
    public static String remaining(long positionMs, long durationMs) {
        long left = Math.max(0, durationMs - Math.max(0, positionMs));
        // Round up so the label reaches −0:00 only at the very end.
        return MINUS + clock(((left + 999) / 1000) * 1000);
    }

    /** Collection length: "48 min", "1 hr 54 min", "2 hr". Under a minute rounds up to "1 min". */
    public static String duration(long totalMs) {
        if (totalMs <= 0) return "0 min";
        long minutes = Math.max(1, (totalMs + 30_000) / 60_000);
        long h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " hr" : h + " hr " + m + " min";
    }

    /** "1 song", "1,248 songs" (grouping follows {@code locale}). */
    public static String songs(int count, Locale locale) {
        return NumberFormat.getIntegerInstance(locale).format(count) + (count == 1 ? " song" : " songs");
    }

    /** "32 songs · 1 hr 54 min". */
    public static String summary(int count, long totalMs, Locale locale) {
        return songs(count, locale) + DOT + duration(totalMs);
    }

    /** Whole minutes listened, e.g. "214 min" (Replay). */
    public static String minutes(long ms, Locale locale) {
        return NumberFormat.getIntegerInstance(locale).format(Math.max(0, ms) / 60_000) + " min";
    }

    /** Compact counts for followers / plays: 950, "1.2K", "48K", "1.3M". */
    public static String compact(long n) {
        if (n < 1000) return Long.toString(Math.max(0, n));
        if (n < 1_000_000) return trim(n / 1000.0, n < 10_000) + "K";
        if (n < 1_000_000_000) return trim(n / 1_000_000.0, n < 10_000_000) + "M";
        return trim(n / 1_000_000_000.0, n < 10_000_000_000L) + "B";
    }

    private static String trim(double v, boolean oneDecimal) {
        if (!oneDecimal) return Long.toString((long) Math.floor(v));
        double r = Math.floor(v * 10) / 10.0;
        return r == Math.floor(r) ? Long.toString((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }
}
