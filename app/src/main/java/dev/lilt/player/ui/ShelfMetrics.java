package dev.lilt.player.ui;

import dev.lilt.player.DesignTokens;

/**
 * Card and grid sizing for any window width (phones, foldables, tablets, split screen).
 * Shelves always show a partial "peek" card so it is obvious they scroll sideways.
 * All values are dp. Pure Java.
 */
public final class ShelfMetrics {
    private ShelfMetrics() {}

    /**
     * Width of a square shelf card so that {@code visible} cards (e.g. 2.4) fit between the
     * leading margin and the window edge, clamped to [{@code min}, {@code max}] and rounded to
     * the 4dp grid.
     */
    public static int cardWidth(int windowDp, float visible, int min, int max) {
        float usable = windowDp - DesignTokens.MARGIN;
        float w = (usable - DesignTokens.GUTTER * (float) Math.floor(visible)) / visible;
        int rounded = Math.round(w / 4f) * 4;
        return Math.max(min, Math.min(max, rounded));
    }

    /** Standard square card (Recently Played, Made for You): 152dp on a 390dp phone. */
    public static int squareCard(int windowDp) { return cardWidth(windowDp, 2.3f, 136, 200); }

    /** Large feature card (Top Picks): 272dp on a 390dp phone. */
    public static int featureCard(int windowDp) { return cardWidth(windowDp, 1.33f, 240, 340); }

    /** Artist circle diameter: 112dp on a 390dp phone. */
    public static int artistCircle(int windowDp) { return cardWidth(windowDp, 3.0f, 96, 148); }

    /** Columns for an album grid with cells of at least {@code minCell} dp. */
    public static int columns(int contentWidthDp, int minCellDp) {
        int usable = contentWidthDp - 2 * DesignTokens.MARGIN + DesignTokens.GUTTER;
        return Math.max(2, usable / (minCellDp + DesignTokens.GUTTER));
    }

    /** Cell width for {@code columns} columns across {@code contentWidthDp}. */
    public static int cellWidth(int contentWidthDp, int columns) {
        int usable = contentWidthDp - 2 * DesignTokens.MARGIN - DesignTokens.GUTTER * (columns - 1);
        return usable / Math.max(1, columns);
    }

    /**
     * Now Playing artwork size: the content width minus 24dp side margins, capped so artwork,
     * metadata and controls fit the window height, and never larger than 520dp.
     */
    public static int nowPlayingArtwork(int windowDp, int windowHeightDp) {
        int byWidth = windowDp - 48;
        int byHeight = windowHeightDp - 500; // title, scrubber, transport, volume and utility row
        return Math.max(160, Math.min(520, Math.min(byWidth, byHeight)));
    }
}
