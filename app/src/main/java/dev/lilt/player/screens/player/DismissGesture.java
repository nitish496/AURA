package dev.lilt.player.screens.player;

/**
 * Drag-down-to-dismiss decisions for the full-screen player screens. The content follows the
 * finger (with resistance when pulled upwards); on release it dismisses when it travelled far
 * enough or was flung down, otherwise it springs back. Pure Java; tests/player/DismissGestureTest.
 */
public final class DismissGesture {
    private DismissGesture() {}

    /** Share of the height that dismisses without a fling. */
    public static final float DISTANCE_FRACTION = 0.25f;
    /** Downward fling (dp/s) that dismisses from any distance. */
    public static final float FLING_DP_S = 900f;
    /** Upward speed (dp/s) that always cancels. */
    public static final float CANCEL_DP_S = 300f;
    /** Minimum travel (dp) for a fling to count (prevents dismissing on a twitch). */
    public static final float MIN_FLING_TRAVEL_DP = 24f;

    /**
     * @param offsetPx       how far the content has moved down (px, may be negative)
     * @param velocityPxS    vertical velocity at release (px/s, positive = down)
     * @param heightPx       height of the screen
     * @param density        px per dp
     */
    public static boolean shouldDismiss(float offsetPx, float velocityPxS, float heightPx, float density) {
        if (heightPx <= 0) return false;
        float d = density > 0 ? density : 1f;
        float vDp = velocityPxS / d;
        if (vDp <= -CANCEL_DP_S) return false;
        if (vDp >= FLING_DP_S && offsetPx / d >= MIN_FLING_TRAVEL_DP) return true;
        return offsetPx >= heightPx * DISTANCE_FRACTION;
    }

    /** Content offset for a raw finger travel: 1:1 downwards, strong resistance upwards. */
    public static float follow(float rawDyPx, float density) {
        if (rawDyPx >= 0) return rawDyPx;
        float limit = 24f * (density > 0 ? density : 1f);
        return -limit * (1f - (float) Math.exp(rawDyPx / (limit * 3f)));
    }

    /** Progress 0..1 used for the corner radius and the dimming of what is underneath. */
    public static float progress(float offsetPx, float heightPx) {
        if (heightPx <= 0 || offsetPx <= 0) return 0;
        return Math.min(1f, offsetPx / heightPx);
    }

    /** Remaining animation time (ms) to cover {@code remainingPx} at the release speed, 150..350 ms. */
    public static long settleDurationMs(float remainingPx, float velocityPxS) {
        float v = Math.abs(velocityPxS);
        if (v < 1) return 300;
        long t = (long) (Math.abs(remainingPx) / v * 1000f * 1.6f);
        return Math.max(150, Math.min(350, t));
    }
}
