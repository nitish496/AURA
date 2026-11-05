package dev.lilt.player.screens.player;

/**
 * Synced-lyrics scrolling: where to scroll so the active line sits at about a third of the
 * viewport, when automatic scrolling resumes after the user scrolled by hand, and how bright /
 * blurred each line is. Pure Java; tests/player/LyricScrollTest.
 */
public final class LyricScroll {
    private LyricScroll() {}

    /** Active line's top edge sits at this share of the viewport height. */
    public static final float ANCHOR = 0.32f;
    /** After a manual scroll, follow the song again after this long without touching. */
    public static final long RESUME_AFTER_MS = 3500;
    /** Alpha of lines that are not active (future / past). */
    public static final float FUTURE_ALPHA = 0.38f, PAST_ALPHA = 0.30f;
    /** Light blur (dp) for lines near and far from the active one (API 31+). */
    public static final float BLUR_NEAR_DP = 0.8f, BLUR_FAR_DP = 1.6f;

    /**
     * Scroll offset that places a line whose top is at {@code lineTop} (content px) at
     * {@link #ANCHOR} of {@code viewport}, clamped to the scrollable range.
     */
    public static int target(int lineTop, int viewport, int contentHeight) {
        int max = Math.max(0, contentHeight - viewport);
        int t = Math.round(lineTop - viewport * ANCHOR);
        return Math.max(0, Math.min(max, t));
    }

    /** True when the song should drive the scroll position again. */
    public static boolean autoScroll(long nowMs, long lastManualMs, boolean touching) {
        if (touching) return false;
        return lastManualMs <= 0 || nowMs - lastManualMs >= RESUME_AFTER_MS;
    }

    /**
     * Alpha for line {@code index} when {@code active} is the current line (-1 before the first).
     * While the user browses by hand every line is readable.
     */
    public static float alpha(int index, int active, boolean manual) {
        if (manual) return index == active ? 1f : 0.72f;
        if (index == active) return 1f;
        return index < active ? PAST_ALPHA : FUTURE_ALPHA;
    }

    /** Blur radius (dp) for a line; 0 for the active line, any line while browsing, and gaps. */
    public static float blurDp(int index, int active, boolean manual) {
        if (manual || index == active) return 0f;
        int d = Math.abs(index - active);
        return d <= 1 ? BLUR_NEAR_DP : BLUR_FAR_DP;
    }

    /** Scroll animation length for a jump of {@code distancePx}: 350..700 ms. */
    public static long durationMs(int distancePx, float density) {
        float dp = Math.abs(distancePx) / (density > 0 ? density : 1f);
        return Math.max(350, Math.min(700, (long) (350 + dp * 0.6f)));
    }
}
