package dev.lilt.player.ui;

/**
 * The adaptive vertical layout of Now Playing (also used by Lyrics/Queue for the bottom capsule).
 *
 * <p>Top to bottom: drag handle, artwork, title row (title + artist, heart + more), scrubber
 * (track + elapsed/remaining), transport (previous / play-pause / next) and the glass control
 * capsule (lyrics, output, queue). There is no volume slider: hardware keys own volume.
 *
 * <p>The artwork is as large as possible: {@code min(width - 2 * MARGIN, height budget)}. Space
 * left over after the fixed group heights and the minimum gaps is shared between the six gaps
 * in fixed proportions ({@link #WEIGHTS}), so the screen never shows one big empty block, on
 * 16:9 through 21:9 phones, with gesture or three-button navigation.
 *
 * <p>All values are dp, measured from the top of the window (which is drawn edge to edge).
 * Pure Java; tests/ui/NowPlayingLayoutTest checks fit and proportions.
 */
public final class NowPlayingLayout {
    /** Side margin of every group. */
    public static final int MARGIN = 24;
    /** Group heights. */
    public static final int HANDLE = 20, TITLE_ROW = 52, SCRUBBER = 32, TRANSPORT = 72, CAPSULE = 52;
    /** Minimum gaps: handle→art, art→title, title→scrubber, scrubber→transport, transport→capsule, capsule→bottom inset. */
    public static final int[] MIN_GAPS = {8, 28, 16, 16, 20, 12};
    /** Share of the leftover height each gap receives (sums to 1). */
    public static final float[] WEIGHTS = {0.12f, 0.12f, 0.08f, 0.26f, 0.26f, 0.16f};
    /** Artwork bounds. */
    public static final int MIN_ART = 160, MAX_ART = 520;
    /** Artwork scale while paused (animated with {@link Motion#spring()}). */
    public static final float PAUSED_ART_SCALE = 0.86f;

    public final int width, height;
    public final int artSize, artX;
    public final int handleY, artY, titleY, scrubberY, transportY, capsuleY;
    /** The six gaps actually used, same order as {@link #MIN_GAPS}. */
    private final int[] gaps;
    /** True when the window is too short for the stacked layout (landscape): use two panes. */
    public final boolean twoPane;

    private NowPlayingLayout(int width, int height, int artSize, int artX, int[] y, int[] gaps, boolean twoPane) {
        this.width = width;
        this.height = height;
        this.artSize = artSize;
        this.artX = artX;
        this.handleY = y[0];
        this.artY = y[1];
        this.titleY = y[2];
        this.scrubberY = y[3];
        this.transportY = y[4];
        this.capsuleY = y[5];
        this.gaps = gaps;
        this.twoPane = twoPane;
    }

    public int gap(int index) { return gaps[index]; }

    /** Sum of fixed group heights (without artwork). */
    public static int fixedHeight() { return HANDLE + TITLE_ROW + SCRUBBER + TRANSPORT + CAPSULE; }

    /** Sum of {@link #MIN_GAPS}. */
    public static int minimumGaps() {
        int s = 0;
        for (int g : MIN_GAPS) s += g;
        return s;
    }

    /**
     * @param widthDp        window width
     * @param heightDp       window height (the activity is edge to edge)
     * @param topInsetDp     status bar / cutout inset
     * @param bottomInsetDp  navigation bar or gesture inset
     */
    public static NowPlayingLayout compute(int widthDp, int heightDp, int topInsetDp, int bottomInsetDp) {
        int avail = heightDp - topInsetDp - bottomInsetDp;
        int budget = avail - fixedHeight() - minimumGaps();
        boolean twoPane = widthDp > heightDp || budget < MIN_ART;
        if (twoPane) {
            // Landscape: artwork fills the left pane; the right pane stacks title → capsule
            // vertically centred, using the minimum gaps.
            int art = Math.max(120, Math.min(MAX_ART, Math.min(avail - 2 * MARGIN, widthDp / 2 - 2 * MARGIN)));
            int[] gaps = MIN_GAPS.clone();
            gaps[0] = 0;
            int stack = TITLE_ROW + SCRUBBER + TRANSPORT + CAPSULE + gaps[2] + gaps[3] + gaps[4];
            int top = topInsetDp + Math.max(0, (avail - stack) / 2);
            int[] y = {topInsetDp, topInsetDp + (avail - art) / 2, top, 0, 0, 0};
            y[3] = y[2] + TITLE_ROW + gaps[2];
            y[4] = y[3] + SCRUBBER + gaps[3];
            y[5] = y[4] + TRANSPORT + gaps[4];
            return new NowPlayingLayout(widthDp, heightDp, art, MARGIN, y, gaps, true);
        }
        int art = Math.max(MIN_ART, Math.min(MAX_ART, Math.min(widthDp - 2 * MARGIN, budget)));
        int extra = Math.max(0, budget - art);
        int[] gaps = new int[MIN_GAPS.length];
        int used = 0;
        for (int i = 0; i < gaps.length; i++) {
            int add = Math.round(extra * WEIGHTS[i]);
            gaps[i] = MIN_GAPS[i] + add;
            used += add;
        }
        gaps[gaps.length - 1] += extra - used; // absorb rounding so the bottom lands exactly on the inset
        int[] y = new int[6];
        y[0] = topInsetDp;
        y[1] = y[0] + HANDLE + gaps[0];
        y[2] = y[1] + art + gaps[1];
        y[3] = y[2] + TITLE_ROW + gaps[2];
        y[4] = y[3] + SCRUBBER + gaps[3];
        y[5] = y[4] + TRANSPORT + gaps[4];
        return new NowPlayingLayout(widthDp, heightDp, art, (widthDp - art) / 2, y, gaps, false);
    }

    /** Bottom of the capsule plus its gap; equals {@code height - bottomInset} in the stacked layout. */
    public int contentBottom() { return capsuleY + CAPSULE + gaps[5]; }
}
