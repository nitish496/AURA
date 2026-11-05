package dev.lilt.player.screens.player;

/**
 * Sleep-timer presets and labels. Pure Java; tests/player/SleepTextTest.
 */
public final class SleepText {
    private SleepText() {}

    /** Preset durations in minutes. */
    public static final int[] PRESETS = {15, 30, 45, 60};
    /** Range of the custom timer. */
    public static final int CUSTOM_MIN = 1, CUSTOM_MAX = 120;

    /** "In 15 minutes", "In 1 hour", "In 1 hr 30 min", "In 1 minute". */
    public static String preset(int minutes) {
        if (minutes == 60) return "In 1 hour";
        if (minutes == 120) return "In 2 hours";
        if (minutes > 60) {
            int h = minutes / 60, m = minutes % 60;
            return "In " + h + " hr" + (m > 0 ? " " + m + " min" : "");
        }
        return "In " + minutes + (minutes == 1 ? " minute" : " minutes");
    }

    /** Countdown "23:41" / "1:02:03"; rounds up so it never shows 0:00 while running. */
    public static String countdown(long remainingMs) {
        long s = (Math.max(0, remainingMs) + 999) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + ":" + two(m) + ":" + two(sec);
        return m + ":" + two(sec);
    }

    /** Value shown on the Sleep timer row. */
    public static String summary(long remainingMs, boolean endOfTrack) {
        if (endOfTrack) return "End of song";
        if (remainingMs <= 0) return "Off";
        return countdown(remainingMs);
    }

    /** Clamps a custom value to {@link #CUSTOM_MIN}..{@link #CUSTOM_MAX}. */
    public static int clampCustom(int minutes) {
        return Math.max(CUSTOM_MIN, Math.min(CUSTOM_MAX, minutes));
    }

    /** The preset (minutes) a running timer most likely came from, or -1 (for the check mark). */
    public static int matchingPreset(long remainingMs, int lastSetMinutes) {
        if (remainingMs <= 0) return -1;
        for (int p : PRESETS) if (p == lastSetMinutes && remainingMs <= p * 60_000L) return p;
        return -1;
    }

    private static String two(long v) { return v < 10 ? "0" + v : Long.toString(v); }
}
