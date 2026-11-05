package dev.lilt.player.ui;

/**
 * Scroll-driven chrome behaviour, kept free of Android types so it can be tested.
 *
 * <p>Minimising the tab bar: scrolling down by at least {@code threshold} px (once past the
 * {@code topZone}) minimises it; scrolling up by half the threshold, or returning to the top
 * zone, restores it. The hysteresis stops the bar flickering on small reversals.
 *
 * <p>Large-title collapse: {@link #collapseProgress} maps the scroll position to 0..1 for the
 * cross-fade between the large title in the content and the small title in the glass top bar.
 */
public final class ScrollBehavior {
    private final int threshold, topZone;
    private boolean minimized;
    private int travel; // signed distance moved in the current direction

    /** @param thresholdPx distance (px) that must be scrolled down before minimising (24dp).
     *  @param topZonePx   near the top the bar is always expanded (e.g. the large-title height). */
    public ScrollBehavior(int thresholdPx, int topZonePx) {
        this.threshold = Math.max(1, thresholdPx);
        this.topZone = Math.max(0, topZonePx);
    }

    /**
     * Feed every scroll change. {@code scrollY} is the new offset from the top, {@code dy} the
     * change since the last call (positive = content moved up / user scrolled down).
     * @return the (possibly new) minimised state.
     */
    public boolean onScroll(int scrollY, int dy) {
        if (scrollY <= topZone) {
            travel = 0;
            minimized = false;
            return false;
        }
        if (dy == 0) return minimized;
        if ((dy > 0) != (travel > 0)) travel = 0; // direction changed
        travel += dy;
        if (!minimized && travel >= threshold) { minimized = true; travel = 0; }
        else if (minimized && -travel >= threshold / 2) { minimized = false; travel = 0; }
        return minimized;
    }

    public boolean minimized() { return minimized; }

    /** Forces a state, e.g. expand when the user taps the minimised tab or a screen changes. */
    public void set(boolean minimizedNow) { minimized = minimizedNow; travel = 0; }

    /**
     * 0 while the large title's baseline is below the top bar; rises to 1 over {@code rangePx}
     * as it scrolls beneath the bar.
     * @param scrollY         content scroll offset
     * @param titleBottomPx   bottom of the large title, in content coordinates (scroll 0)
     * @param barBottomPx     bottom of the glass top bar, in window coordinates
     * @param rangePx         distance over which the cross-fade happens (about 16dp)
     */
    public static float collapseProgress(int scrollY, int titleBottomPx, int barBottomPx, int rangePx) {
        float start = titleBottomPx - barBottomPx;
        float p = (scrollY - start) / Math.max(1f, rangePx);
        return p < 0 ? 0 : Math.min(1, p);
    }

    /** Linear interpolation helper for animating chrome geometry by a 0..1 progress. */
    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }
}
