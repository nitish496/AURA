package dev.lilt.player.ui;

/**
 * AURA's type scale (an adaptation of Apple's text styles, set in Inter / Inter Display).
 *
 * <p>Sizes and line heights are in sp; tracking is in em (Android's {@code letterSpacing}).
 * Styles flagged {@link Style#display} use the Inter Display cut (tighter, for 22sp and up);
 * everything else uses Inter. Use {@link Type} to apply a style to a TextView. Pure Java.
 */
public final class TypeScale {
    private TypeScale() {}

    /** One text style. Immutable; derive variants with {@link #weight(int)}. */
    public static final class Style {
        public final String name;
        public final float size;
        public final float lineHeight;
        /** 400 regular, 500 medium, 600 semibold, 700 bold. */
        public final int weight;
        /** Letter spacing in em. */
        public final float tracking;
        /** True: Inter Display (only the bold cut is bundled). */
        public final boolean display;

        public Style(String name, float size, float lineHeight, int weight, float tracking, boolean display) {
            this.name = name;
            this.size = size;
            this.lineHeight = lineHeight;
            this.weight = weight;
            this.tracking = tracking;
            this.display = display;
        }
        /** The same style at another weight (Inter only ships 400/500/600/700 here). */
        public Style weight(int newWeight) {
            return new Style(name + "@" + newWeight, size, lineHeight, newWeight, tracking, display && newWeight >= 700);
        }
        @Override public String toString() { return name + " " + size + "/" + lineHeight + " w" + weight; }
    }

    // Apple-style ramp.
    public static final Style LARGE_TITLE = new Style("largeTitle", 34, 40, 700, -0.012f, true);
    public static final Style TITLE1 = new Style("title1", 28, 34, 700, -0.010f, true);
    public static final Style TITLE2 = new Style("title2", 22, 28, 700, -0.008f, true);
    public static final Style TITLE3 = new Style("title3", 20, 24, 600, -0.016f, false);
    public static final Style HEADLINE = new Style("headline", 17, 22, 600, -0.013f, false);
    public static final Style BODY = new Style("body", 17, 22, 400, -0.013f, false);
    public static final Style CALLOUT = new Style("callout", 16, 21, 400, -0.011f, false);
    public static final Style SUBHEAD = new Style("subhead", 15, 20, 400, -0.009f, false);
    public static final Style FOOTNOTE = new Style("footnote", 13, 18, 400, -0.003f, false);
    public static final Style CAPTION1 = new Style("caption1", 12, 16, 500, 0f, false);
    public static final Style CAPTION2 = new Style("caption2", 11, 13, 500, 0.005f, false);

    // Component styles (named so screens never improvise sizes).
    /** Song / list row title: 16/21 medium. */
    public static final Style ROW_TITLE = new Style("rowTitle", 16, 21, 500, -0.011f, false);
    /** Card title under artwork: 15/20 medium. */
    public static final Style CARD_TITLE = new Style("cardTitle", 15, 20, 500, -0.009f, false);
    /** Pill button label: 16/20 semibold. */
    public static final Style BUTTON = new Style("button", 16, 20, 600, -0.010f, false);
    /** Filter chip label: 14/18 medium. */
    public static final Style CHIP = new Style("chip", 14, 18, 500, -0.006f, false);
    /** Tab bar label: 10/12 medium. */
    public static final Style TAB = new Style("tab", 10, 12, 500, 0.010f, false);
    /** Synced lyric line: 30/37 Inter Display Bold. */
    public static final Style LYRIC = new Style("lyric", 30, 37, 700, -0.012f, true);
    /** Big statistic (Replay): 64/68 Inter Display Bold, tabular. */
    public static final Style STAT = new Style("stat", 64, 68, 700, -0.020f, true);
    /** Uppercase section label in grouped settings: 13/18 regular, +6% tracking. */
    public static final Style OVERLINE = new Style("overline", 13, 18, 400, 0.060f, false);

    private static final Style[] ALL = {LARGE_TITLE, TITLE1, TITLE2, TITLE3, HEADLINE, BODY, CALLOUT, SUBHEAD, FOOTNOTE,
            CAPTION1, CAPTION2, ROW_TITLE, CARD_TITLE, BUTTON, CHIP, TAB, LYRIC, STAT, OVERLINE};

    /** Every named style (the ramp first, then component styles). */
    public static Style[] all() { return ALL.clone(); }

    /** The ramp only, largest first. */
    public static Style[] ramp() {
        return new Style[] {LARGE_TITLE, TITLE1, TITLE2, TITLE3, HEADLINE, BODY, CALLOUT, SUBHEAD, FOOTNOTE, CAPTION1, CAPTION2};
    }

    /**
     * Inter's recommended tracking for text set at {@code sizeSp} ("dynamic metrics" from the
     * Inter project: a + b * e^(c * size), in em). Useful for sizes outside the scale.
     */
    public static float interTracking(float sizeSp) {
        return (float) (-0.0223 + 0.185 * Math.exp(-0.1745 * sizeSp));
    }

    /**
     * Extra spacing (px) to add between lines so each line box is exactly {@code lineHeightPx}
     * tall, given the font's ascent (negative, as Android reports it) and descent in px.
     */
    public static float extraLineSpacing(float lineHeightPx, float ascentPx, float descentPx) {
        return lineHeightPx - (descentPx - ascentPx);
    }
}
