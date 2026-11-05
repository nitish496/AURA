package dev.lilt.player.ui;

import java.util.Locale;

/** NowPlayingLayout fit, ArtworkPalette contrast, SvgPath, IconPaths, TimeText, Initials, ScrollBehavior, SpringCurve. */
public final class UiLogicTest {
    private static int checks;
    private static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        // Now Playing: fits every phone aspect ratio with gesture and 3-button nav, no gap larger than 3x the minimum.
        int[][] screens = {{360, 640}, {360, 740}, {390, 844}, {412, 915}, {393, 873}, {360, 800}, {411, 1000}};
        for (int[] s : screens) for (int[] ins : new int[][] {{24, 16}, {32, 48}, {40, 24}}) {
            NowPlayingLayout l = NowPlayingLayout.compute(s[0], s[1], ins[0], ins[1]);
            String t = s[0] + "x" + s[1] + " insets " + ins[0] + "/" + ins[1];
            check(!l.twoPane, t + " is stacked");
            check(l.artSize >= NowPlayingLayout.MIN_ART && l.artSize <= s[0] - 2 * NowPlayingLayout.MARGIN, t + " art size " + l.artSize);
            check(l.contentBottom() == s[1] - ins[1], t + " bottom lands on inset: " + l.contentBottom());
            check(l.artY >= l.handleY + NowPlayingLayout.HANDLE && l.titleY > l.artY + l.artSize && l.scrubberY > l.titleY
                    && l.transportY > l.scrubberY && l.capsuleY > l.transportY, t + " order");
            for (int i = 0; i < 6; i++) check(l.gap(i) >= NowPlayingLayout.MIN_GAPS[i], t + " gap " + i);
            check(l.artX * 2 + l.artSize == s[0], t + " art centred");
        }
        NowPlayingLayout tall = NowPlayingLayout.compute(390, 844, 40, 24);
        check(tall.artSize == 342, "390x844 uses full-width art: " + tall.artSize);
        check(NowPlayingLayout.compute(800, 360, 24, 0).twoPane, "landscape uses two panes");
        float sum = 0;
        for (float w : NowPlayingLayout.WEIGHTS) sum += w;
        check(Math.abs(sum - 1f) < 1e-5, "weights sum to 1");

        // ArtworkPalette: text contrast is guaranteed for any artwork.
        java.util.Random rnd = new java.util.Random(7);
        for (int k = 0; k < 300; k++) {
            int[] px = new int[576];
            int base = 0xFF000000 | rnd.nextInt(0x1000000);
            for (int i = 0; i < px.length; i++) px[i] = rnd.nextInt(4) == 0 ? 0xFF000000 | rnd.nextInt(0x1000000) : base;
            ArtworkPalette p = ArtworkPalette.from(px);
            check(ColorMath.contrast(p.textPrimary, p.background) >= ColorMath.AAA_TEXT, "primary on background " + ColorMath.hex(base));
            for (int m : p.mesh()) {
                check(ColorMath.contrast(p.textPrimary, m) >= ColorMath.AAA_TEXT - 0.01, "primary on mesh " + ColorMath.hex(m));
                check(ColorMath.contrast(p.textSecondary, m) >= ColorMath.AA_TEXT - 0.01, "secondary on mesh " + ColorMath.hex(m));
            }
            float[] hsl = ColorMath.toHsl(p.background);
            check(hsl[1] <= 0.6f || hsl[2] < 0.15f, "calm saturation " + ColorMath.hex(p.background));
        }
        check(ArtworkPalette.from(new int[0]) == ArtworkPalette.NEUTRAL, "no pixels is neutral");
        check(ArtworkPalette.from(new int[] {0x00FF0000}) == ArtworkPalette.NEUTRAL, "transparent is neutral");
        check(!ArtworkPalette.from(new int[] {0xFF808080, 0xFF909090, 0xFF707070}).fromArtwork == false
                && ColorMath.toHsl(ArtworkPalette.from(new int[] {0xFF808080, 0xFF909090}).background)[1] < 0.05f, "greyscale artwork stays neutral");

        // SvgPath
        SvgPath sq = SvgPath.parse("M1,1H5V5H1Z");
        check(sq.opCount() == 5 && sq.op(0) == SvgPath.MOVE && sq.op(4) == SvgPath.CLOSE, "H/V/Z ops");
        SvgPath arc = SvgPath.parse("M0,0A5,5 0 0,1 10,0");
        float[] b = arc.controlBounds();
        check(arc.op(1) == SvgPath.CUBIC && b[2] >= 9.99f && b[1] <= -4f, "semicircle arc becomes cubics: " + java.util.Arrays.toString(b));
        check(SvgPath.parse("m1,1l2,0 0,2z").opCount() == 4, "relative commands");
        boolean threw = false;
        try { SvgPath.parse("M1,1X2"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "bad command rejected");
        // Every icon parses and stays inside the 24 grid (with the stroke margin).
        for (String name : IconPaths.names()) {
            IconPaths.Glyph g = IconPaths.get(name);
            check(g != null && g.layers() > 0, name + " exists");
            for (int i = 0; i < g.layers(); i++) {
                float[] cb = SvgPath.parse(g.path(i)).controlBounds();
                check(cb[0] >= 0.5f && cb[1] >= 0.5f && cb[2] <= 23.5f && cb[3] <= 23.5f, name + " inside the live area " + java.util.Arrays.toString(cb));
            }
        }
        check(IconPaths.get("down") == IconPaths.get("chevron_down"), "legacy alias");

        // TimeText
        check(TimeText.clock(7_000).equals("0:07") && TimeText.clock(221_000).equals("3:41") && TimeText.clock(3_723_000).equals("1:02:03"), "clock");
        check(TimeText.remaining(84_000, 252_000).equals("−2:48"), "remaining");
        check(TimeText.duration(6_840_000).equals("1 hr 54 min") && TimeText.duration(7_200_000).equals("2 hr") && TimeText.duration(10_000).equals("1 min"), "duration");
        check(TimeText.songs(1248, Locale.US).equals("1,248 songs") && TimeText.songs(1, Locale.US).equals("1 song"), "songs");
        check(TimeText.compact(950).equals("950") && TimeText.compact(48_300).equals("48K") && TimeText.compact(1_250).equals("1.2K") && TimeText.compact(1_300_000).equals("1.3M"), "compact");

        // Initials
        check(Initials.of("Low Tide").equals("LT") && Initials.of("The Quiet Hours").equals("QH") && Initials.of("Björk").equals("B"), "initials");
        check(Initials.of("2 Lane Road").equals("2L") && Initials.of("???").isEmpty() && Initials.of(null).isEmpty(), "initials edge cases");
        check(Initials.tone("x", 4) == Initials.tone("x", 4) && Initials.tone("a", 4) >= 0 && Initials.tone("a", 4) < 4, "tone stable");

        // ScrollBehavior hysteresis
        ScrollBehavior sb = new ScrollBehavior(60, 100);
        check(!sb.onScroll(50, 50), "top zone stays expanded");
        check(!sb.onScroll(130, 30) && sb.onScroll(170, 40), "minimises after 60px down");
        check(sb.onScroll(160, -10) && !sb.onScroll(120, -40), "restores after 30px up");
        sb.onScroll(400, 280);
        check(sb.minimized() && !sb.onScroll(20, -380), "top zone restores");
        check(ScrollBehavior.collapseProgress(0, 140, 90, 16) == 0f && ScrollBehavior.collapseProgress(200, 140, 90, 16) == 1f, "collapse progress");

        // SpringCurve + lyrics
        check(SpringCurve.DEFAULT.value(0) == 0f && SpringCurve.DEFAULT.value(1) == 1f && SpringCurve.DEFAULT.peak() < 1.12f, "spring endpoints and gentle overshoot");
        check(SpringCurve.GENTLE.value(0.5f) > 0.5f, "spring front-loaded");
        long[] times = {1000, 5000, 9000};
        check(LyricsTiming.indexAt(times, 500) == -1 && LyricsTiming.indexAt(times, 1000) == 0 && LyricsTiming.indexAt(times, 8999) == 1 && LyricsTiming.indexAt(times, 99999) == 2, "lyrics index");
        check(LyricsTiming.untilNext(times, 1500) == 3500 && LyricsTiming.untilNext(times, 9500) == -1, "lyrics next");

        // ShelfMetrics
        check(ShelfMetrics.squareCard(390) == 152 && ShelfMetrics.featureCard(390) == 272 && ShelfMetrics.artistCircle(390) == 112, "390dp card sizes: "
                + ShelfMetrics.squareCard(390) + "/" + ShelfMetrics.featureCard(390) + "/" + ShelfMetrics.artistCircle(390));
        System.out.println(checks + " ui logic checks passed");
    }
}
