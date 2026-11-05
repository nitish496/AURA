package dev.lilt.player.screens.player;

import dev.lilt.player.ui.NowPlayingLayout;

/**
 * Concrete element rectangles (dp) for Now Playing, built on the design system's adaptive
 * {@link NowPlayingLayout} (which decides the artwork size and how leftover height is shared
 * between the groups). This class places every view inside those groups: the drag handle,
 * artwork, title block, heart / more buttons, scrubber, the three transport buttons and the
 * bottom glass capsule, for the stacked (portrait) and two-pane (landscape / very short) cases.
 *
 * <p>Touch targets are at least 44dp; the visible glass buttons (36dp) are inset in them so
 * their visual edges line up with the side margin. Pure Java; tests/player/NowPlayingGeometryTest.
 */
public final class NowPlayingGeometry {
    /** Visible size of the heart / more glass buttons, and the touch target around them. */
    public static final int GLASS_BUTTON = 36, GLASS_TARGET = 44;
    /** Visual gap between the heart and more buttons. */
    public static final int BUTTON_GAP = 8;
    /** Transport touch targets: previous / next and play-pause. */
    public static final int SKIP_TARGET = 64, PLAY_TARGET = 76;
    /** Bottom capsule size (three 52 x 40 segments plus 8dp padding at each end). */
    public static final int CAPSULE_W = 192, CAPSULE_H = NowPlayingLayout.CAPSULE;
    /** Extra touch height added above and below the scrubber group. */
    public static final int SCRUB_TOUCH_PAD = 8;
    /** Visible drag handle. */
    public static final int HANDLE_W = 36, HANDLE_H = 5;
    /** Widest the controls column gets in the two-pane layout. */
    public static final int RIGHT_PANE_MAX = 480;
    /** Close chevron: visible size and touch target at the top-start corner. */
    public static final int CHEVRON = 20, CHEVRON_TARGET = 48;

    /** An axis-aligned rectangle in dp. */
    public static final class Box {
        public final int x, y, w, h;
        Box(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = Math.max(0, w); this.h = Math.max(0, h); }
        public int right() { return x + w; }
        public int bottom() { return y + h; }
        public int centerX() { return x + w / 2; }
        public boolean intersects(Box o) { return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom(); }
        @Override public String toString() { return "[" + x + "," + y + " " + w + "x" + h + "]"; }
    }

    public final NowPlayingLayout layout;
    public final boolean twoPane;
    /** Full-width drag strip at the top (the visible handle is centred in it). */
    public final Box handleArea, handle;
    public final Box art;
    /** Title + artist text block (left aligned). */
    public final Box titleBlock;
    public final Box heart, more;
    /** Scrubber view including its touch padding (the track is {@link #SCRUB_TOUCH_PAD} below its top). */
    public final Box scrubber;
    public final Box previous, playPause, next;
    public final Box capsule;

    private NowPlayingGeometry(NowPlayingLayout l) {
        layout = l;
        twoPane = l.twoPane;
        int m = NowPlayingLayout.MARGIN;
        int colX, colW;
        if (twoPane) {
            // Right pane: the half of the window right of centre, capped at 480dp and centred in it.
            int paneX = Math.max(l.artX + l.artSize + m, l.width / 2);
            int paneW = l.width - m - paneX;
            colW = Math.min(RIGHT_PANE_MAX, paneW);
            colX = paneX + (paneW - colW) / 2;
            art = new Box(l.artX, l.artY, l.artSize, l.artSize);
        } else {
            colX = m;
            colW = l.width - 2 * m;
            art = new Box(l.artX, l.artY, l.artSize, l.artSize);
        }
        handleArea = new Box(0, l.handleY, l.width, NowPlayingLayout.HANDLE);
        handle = new Box((l.width - HANDLE_W) / 2, l.handleY + (NowPlayingLayout.HANDLE - HANDLE_H) / 2, HANDLE_W, HANDLE_H);

        int inset = (GLASS_TARGET - GLASS_BUTTON) / 2;
        int buttonY = l.titleY + (NowPlayingLayout.TITLE_ROW - GLASS_TARGET) / 2;
        int moreX = colX + colW + inset - GLASS_TARGET;
        int heartX = moreX - BUTTON_GAP - GLASS_BUTTON - inset * 2;
        more = new Box(moreX, buttonY, GLASS_TARGET, GLASS_TARGET);
        heart = new Box(heartX, buttonY, GLASS_TARGET, GLASS_TARGET);
        titleBlock = new Box(colX, l.titleY, Math.max(0, heartX + inset - 12 - colX), NowPlayingLayout.TITLE_ROW);

        scrubber = new Box(colX, l.scrubberY - SCRUB_TOUCH_PAD, colW, NowPlayingLayout.SCRUBBER + 2 * SCRUB_TOUCH_PAD);

        // Three equal slots ("space-around"); buttons centred in their slot and in the row.
        int ty = l.transportY;
        int th = NowPlayingLayout.TRANSPORT;
        int slot = colW / 3;
        previous = centered(colX + slot / 2, ty + th / 2, Math.min(SKIP_TARGET, slot), Math.min(SKIP_TARGET, th));
        playPause = centered(colX + colW / 2, ty + th / 2, Math.min(PLAY_TARGET, slot), Math.min(PLAY_TARGET, th + 4));
        next = centered(colX + colW - slot / 2, ty + th / 2, Math.min(SKIP_TARGET, slot), Math.min(SKIP_TARGET, th));

        capsule = new Box(colX + (colW - CAPSULE_W) / 2, l.capsuleY, CAPSULE_W, CAPSULE_H);
    }

    private static Box centered(int cx, int cy, int w, int h) { return new Box(cx - w / 2, cy - h / 2, w, h); }

    /** Geometry for a window of {@code widthDp} x {@code heightDp} with the given system insets (dp). */
    public static NowPlayingGeometry compute(int widthDp, int heightDp, int topInsetDp, int bottomInsetDp) {
        return new NowPlayingGeometry(NowPlayingLayout.compute(widthDp, heightDp, Math.max(0, topInsetDp), Math.max(0, bottomInsetDp)));
    }

    /** Every element, in vertical order (for tests and debugging). */
    public Box[] all() { return new Box[] {handle, art, titleBlock, heart, more, scrubber, previous, playPause, next, capsule}; }
}
