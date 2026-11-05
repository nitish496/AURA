package dev.lilt.player.screens.player;

import dev.lilt.player.screens.player.NowPlayingGeometry.Box;

/** Every phone shape from 16:9 to 21:9, with gesture or 3-button navigation, fits without overlaps. */
public final class NowPlayingGeometryTest {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    public static void main(String[] args) {
        int[][] phones = {
            {360, 640}, {360, 720}, {360, 760}, {360, 800}, {393, 852}, {412, 915}, {390, 844}, {411, 891},
            {384, 854}, {412, 892}, {430, 932}, {360, 780}, {320, 568}, {480, 1066}, {412, 960}};
        int[][] insets = {{24, 16}, {24, 48}, {48, 24}, {32, 0}, {0, 0}};
        for (int[] p : phones) for (int[] in : insets) {
            NowPlayingGeometry g = NowPlayingGeometry.compute(p[0], p[1], in[0], in[1]);
            String id = p[0] + "x" + p[1] + " insets " + in[0] + "/" + in[1];
            if (g.twoPane) continue; // only very short windows
            int bottom = p[1] - in[1];
            for (Box b : g.all()) {
                check(b.x >= 0 && b.right() <= p[0], id + " horizontal bounds " + b);
                check(b.y >= in[0] - 1 && b.bottom() <= bottom + 4, id + " vertical bounds " + b);
            }
            check(g.art.w == g.art.h && g.art.w >= 160, id + " art square");
            check(Math.abs(g.art.centerX() - p[0] / 2) <= 1, id + " art centred");
            // Vertical order without overlaps between groups.
            check(g.art.y >= g.handleArea.bottom(), id + " handle above art");
            check(g.titleBlock.y >= g.art.bottom() + 20, id + " title below art");
            check(g.scrubber.y >= g.titleBlock.bottom() - NowPlayingGeometry.SCRUB_TOUCH_PAD, id + " scrubber below title");
            check(g.playPause.y >= g.scrubber.bottom() - 4, id + " transport below scrubber " + g.playPause + " " + g.scrubber);
            check(g.capsule.y >= g.playPause.bottom(), id + " capsule below transport");
            check(!g.heart.intersects(g.more), id + " heart/more overlap");
            check(!g.titleBlock.intersects(g.heart), id + " title/heart overlap");
            check(!g.previous.intersects(g.playPause) && !g.next.intersects(g.playPause), id + " transport overlap");
            check(g.heart.w >= 44 && g.previous.w >= 44 && g.playPause.w >= 48, id + " touch targets");
            // Visual right edge of the more button lines up with the margin.
            int inset = (NowPlayingGeometry.GLASS_TARGET - NowPlayingGeometry.GLASS_BUTTON) / 2;
            check(g.more.right() - inset == p[0] - 24, id + " more aligned to margin");
            check(Math.abs(g.capsule.centerX() - p[0] / 2) <= 1, id + " capsule centred");
            // No dead gap: the largest gap is less than the artwork.
            for (int i = 0; i < 6; i++) check(g.layout.gap(i) < g.art.h, id + " gap " + i + " too large");
        }
        // Landscape uses two panes and still keeps everything on screen.
        NowPlayingGeometry land = NowPlayingGeometry.compute(844, 390, 0, 16);
        check(land.twoPane, "landscape two-pane");
        check(land.titleBlock.x >= land.art.right(), "controls right of art");
        for (Box b : land.all()) check(b.right() <= 844 && b.x >= 0, "landscape bounds " + b);
        System.out.println("NowPlayingGeometryTest ok");
    }
}
