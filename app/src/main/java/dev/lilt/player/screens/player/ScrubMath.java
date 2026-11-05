package dev.lilt.player.screens.player;

/**
 * Scrubber arithmetic: touch position to playback position, relative dragging, haptic tick
 * spacing and how often the progress needs repainting. Pure Java; tests/player/ScrubMathTest.
 */
public final class ScrubMath {
    private ScrubMath() {}

    /** Haptic tick steps (ms) to choose from, smallest first. */
    private static final long[] STEPS = {5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 900_000, 1_800_000, 3_600_000};
    /** At most this many ticks across the whole track. */
    public static final int MAX_TICKS = 24;

    /** Fraction 0..1 of {@code x} along a track starting at {@code left} that is {@code width} wide. */
    public static float fraction(float x, float left, float width) {
        if (width <= 0) return 0;
        float f = (x - left) / width;
        return f < 0 ? 0 : Math.min(1, f);
    }

    /** Playback position for a fraction of {@code durationMs}. */
    public static long positionAt(float fraction, long durationMs) {
        if (durationMs <= 0) return 0;
        float f = fraction < 0 ? 0 : Math.min(1, fraction);
        return Math.round((double) f * durationMs);
    }

    /**
     * Relative drag: the position moves by the finger's horizontal travel ({@code dxPx}) as a
     * share of the track width, starting from {@code startMs}; clamped to the song.
     */
    public static long dragPosition(long startMs, float dxPx, float widthPx, long durationMs) {
        if (durationMs <= 0 || widthPx <= 0) return 0;
        long p = startMs + Math.round((double) dxPx / widthPx * durationMs);
        return Math.max(0, Math.min(durationMs, p));
    }

    /** Spacing of haptic ticks while dragging: a round step giving at most {@link #MAX_TICKS} ticks. */
    public static long tickStepMs(long durationMs) {
        if (durationMs <= 0) return 0;
        for (long s : STEPS) if (durationMs / s <= MAX_TICKS) return s;
        return STEPS[STEPS.length - 1] * Math.max(1, durationMs / (STEPS[STEPS.length - 1] * MAX_TICKS) + 1);
    }

    /** Index of the tick segment {@code positionMs} lies in; a change between two drags means "tick". */
    public static long tickIndex(long positionMs, long stepMs) {
        if (stepMs <= 0) return 0;
        return Math.max(0, positionMs) / stepMs;
    }

    /**
     * How often (ms) the progress has to repaint so the played part grows about one pixel per
     * frame at most: the time one pixel represents, between 16 ms and 1 s.
     */
    public static long refreshIntervalMs(long durationMs, int trackPx, float speed) {
        if (durationMs <= 0 || trackPx <= 0) return 1000;
        float s = speed > 0 ? speed : 1f;
        long perPixel = (long) (durationMs / (s * trackPx));
        return Math.max(16, Math.min(1000, perPixel));
    }

    /** The track's height (px) while idle → dragging, for a drag {@code progress} 0..1. */
    public static float trackHeight(float idlePx, float activePx, float progress) {
        float p = progress < 0 ? 0 : Math.min(1, progress);
        return idlePx + (activePx - idlePx) * p;
    }
}
