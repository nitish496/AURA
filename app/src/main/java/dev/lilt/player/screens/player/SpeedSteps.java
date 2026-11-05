package dev.lilt.player.screens.player;

import java.util.Locale;

/**
 * Playback speed slider: 0.5x to 2.0x in 0.05 steps, snapping to the common speeds when the
 * thumb comes close. Pure Java; tests/player/SpeedStepsTest.
 */
public final class SpeedSteps {
    private SpeedSteps() {}

    public static final float MIN = 0.5f, MAX = 2.0f, STEP = 0.05f;
    /** Snap points (the haptic tick fires when the value lands on one). */
    public static final float[] SNAPS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    /** Distance within which the value snaps. */
    public static final float SNAP_RANGE = 0.04f;

    /** Slider fraction 0..1 → speed (stepped and snapped). */
    public static float fromFraction(float fraction) {
        float f = fraction < 0 ? 0 : Math.min(1, fraction);
        return snap(MIN + f * (MAX - MIN));
    }

    /** Speed → slider fraction 0..1. */
    public static float toFraction(float speed) {
        return (clamp(speed) - MIN) / (MAX - MIN);
    }

    /** Rounds to {@link #STEP} and pulls values near a snap point onto it. */
    public static float snap(float speed) {
        float s = clamp(speed);
        for (float p : SNAPS) if (Math.abs(s - p) <= SNAP_RANGE) return p;
        float stepped = Math.round(s / STEP) * STEP;
        return Math.round(clamp(stepped) * 100f) / 100f;
    }

    public static boolean isSnapPoint(float speed) {
        for (float p : SNAPS) if (Math.abs(speed - p) < 0.001f) return true;
        return false;
    }

    /** "1×", "1.25×", "0.8×". */
    public static String label(float speed) {
        float s = Math.round(clamp(speed) * 100f) / 100f;
        String text = String.format(Locale.ROOT, "%.2f", s);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text + "×";
    }

    private static float clamp(float s) {
        if (Float.isNaN(s)) return 1f;
        return Math.max(MIN, Math.min(MAX, s));
    }
}
