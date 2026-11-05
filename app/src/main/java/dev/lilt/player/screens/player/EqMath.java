package dev.lilt.player.screens.player;

import java.util.Locale;

/**
 * Equaliser graph arithmetic: millibel levels to graph positions and back, labels for band
 * frequencies and gains. Pure Java; tests/player/EqMathTest.
 */
public final class EqMath {
    private EqMath() {}

    /** Drag resolution: 0.5 dB. */
    public static final int STEP_MB = 50;
    /** Values this close to 0 dB snap to it. */
    public static final int ZERO_SNAP_MB = 40;

    /** Y (px) of {@code levelMb} in a graph whose top is max and bottom is min. */
    public static float y(int levelMb, int minMb, int maxMb, float top, float height) {
        if (maxMb <= minMb) return top + height / 2;
        float f = (maxMb - clamp(levelMb, minMb, maxMb)) / (float) (maxMb - minMb);
        return top + f * height;
    }

    /** Level (mB, stepped and snapped to 0) for a touch at {@code y}. */
    public static short levelAt(float y, int minMb, int maxMb, float top, float height) {
        if (maxMb <= minMb || height <= 0) return 0;
        float f = (y - top) / height;
        f = f < 0 ? 0 : Math.min(1, f);
        int raw = Math.round(maxMb - f * (maxMb - minMb));
        int stepped = Math.round(raw / (float) STEP_MB) * STEP_MB;
        if (Math.abs(stepped) <= ZERO_SNAP_MB) stepped = 0;
        return (short) clamp(stepped, minMb, maxMb);
    }

    /** "60", "230", "910", "3.6k", "14k" (band centres in Hz). */
    public static String frequency(int hz) {
        if (hz < 1000) return Integer.toString(Math.max(0, hz));
        float k = hz / 1000f;
        if (k >= 10 || Math.abs(k - Math.round(k)) < 0.05f) return Math.round(k) + "k";
        return String.format(Locale.ROOT, "%.1fk", k);
    }

    /** "+3 dB", "0 dB", "−4.5 dB" (U+2212 minus). */
    public static String gain(int levelMb) {
        float db = levelMb / 100f;
        String n = Math.abs(db - Math.round(db)) < 0.05f ? Integer.toString(Math.abs(Math.round(db)))
                : String.format(Locale.ROOT, "%.1f", Math.abs(db));
        if (Math.round(levelMb / 10f) == 0) return "0 dB";
        return (db > 0 ? "+" : "−") + n + " dB";
    }

    /** Index of the band nearest to {@code x} given band x positions (ascending). */
    public static int nearestBand(float x, float[] bandX) {
        if (bandX == null || bandX.length == 0) return -1;
        int best = 0;
        for (int i = 1; i < bandX.length; i++) if (Math.abs(bandX[i] - x) < Math.abs(bandX[best] - x)) best = i;
        return best;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
