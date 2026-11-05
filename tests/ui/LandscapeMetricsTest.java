package dev.lilt.player.ui;

import dev.lilt.player.screens.player.NowPlayingGeometry;
import dev.lilt.player.screens.player.NowPlayingGeometry.Box;

/** Sizing math for landscape / short windows: chrome mode, panel caps and the two-pane Now Playing. */
public final class LandscapeMetricsTest {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    public static void main(String[] args) {
        check(ShelfMetrics.compactChrome(360) && ShelfMetrics.compactChrome(479), "short windows are compact");
        check(!ShelfMetrics.compactChrome(480) && !ShelfMetrics.compactChrome(800) && !ShelfMetrics.compactChrome(0), "tall/unknown windows are not");
        check(ShelfMetrics.maxPanelHeightPx(720, 48, 24, 32, 100) == 616, "window - insets - margin");
        check(ShelfMetrics.maxPanelHeightPx(100, 48, 24, 32, 100) == 100, "never below the minimum");
        check(ShelfMetrics.panelWidthPx(2000, 16, 960) == 960 && ShelfMetrics.panelWidthPx(400, 16, 960) == 368, "sheet width cap");
        check(ShelfMetrics.panelWidthPx(10, 16, 960) >= 1, "width never collapses");

        int[][] landscape = {{640, 360}, {732, 412}, {800, 360}, {915, 412}, {960, 540}, {1280, 800}, {568, 320}};
        for (int[] s : landscape) for (int bottom : new int[] {0, 24}) for (int top : new int[] {0, 24}) {
            NowPlayingGeometry g = NowPlayingGeometry.compute(s[0], s[1], top, bottom);
            check(g.twoPane, "landscape is two pane " + s[0] + "x" + s[1]);
            int half = s[0] / 2;
            check(g.art.x >= 0 && g.art.right() <= half, "art stays in the left half " + g.art);
            int left = g.art.x, right = half - g.art.right();
            check(Math.abs(left - right) <= 2 || g.art.x == NowPlayingLayout.MARGIN, "art centred in left half " + g.art + " " + s[0]);
            check(g.art.y >= top && g.art.bottom() <= s[1] - bottom, "art vertically inside insets");
            Box[] controls = {g.titleBlock, g.scrubber, g.previous, g.playPause, g.next, g.capsule, g.heart, g.more};
            for (Box b : controls) {
                check(b.x >= half - 1 && b.right() <= s[0], "controls in the right half " + b);
                check(b.bottom() <= s[1] - bottom && b.y >= top, "controls inside insets " + b);
            }
            check(g.scrubber.w <= NowPlayingGeometry.RIGHT_PANE_MAX, "right pane capped");
            check(!g.art.intersects(g.capsule), "art and capsule do not overlap");
        }
        System.out.println("landscape metrics checks passed");
    }
}
