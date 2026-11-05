package dev.lilt.player.ui;

/**
 * Timing helpers for synced lyrics (used by the Lyrics screen). Lines are given as start
 * times in milliseconds, ascending. Pure Java.
 */
public final class LyricsTiming {
    private LyricsTiming() {}

    /**
     * Index of the line that is active at {@code positionMs}: the last line whose start time is
     * at or before the position, or -1 before the first line. Times must be ascending.
     */
    public static int indexAt(long[] times, long positionMs) {
        if (times == null || times.length == 0 || positionMs < times[0]) return -1;
        int lo = 0, hi = times.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= positionMs) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /**
     * Milliseconds until the next line starts (for scheduling the next highlight), or -1 when
     * the last line is active.
     */
    public static long untilNext(long[] times, long positionMs) {
        int i = indexAt(times, positionMs);
        if (times == null || i + 1 >= times.length) return -1;
        return Math.max(0, times[i + 1] - positionMs);
    }

    /**
     * Opacity for a line {@code distance} lines away from the active one (0 = active): the active
     * line is fully opaque, neighbours fade gently, everything else rests at {@code dimmed}.
     */
    public static float alphaFor(int distance, float dimmed) {
        int d = Math.abs(distance);
        if (d == 0) return 1f;
        if (d == 1) return Math.min(1f, dimmed + 0.08f);
        return dimmed;
    }
}
