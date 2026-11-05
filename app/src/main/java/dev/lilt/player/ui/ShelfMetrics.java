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

    /** Widest the tab content column gets (tablets, landscape foldables). Presentations are full width. */
    public static final int COLUMN_MAX_DP = 840;
    /** Widest a bottom sheet / dialog gets, in dp. */
    public static final int SHEET_MAX_DP = 480;
    /** Windows shorter than this (landscape phones) start with the compact chrome. */
    public static final int COMPACT_CHROME_BELOW_DP = 480;

    /** True when the window is too short for the full 64dp tab bar + 56dp mini player + 52dp top bar. */
    public static boolean compactChrome(int windowHeightDp) { return windowHeightDp > 0 && windowHeightDp < COMPACT_CHROME_BELOW_DP; }

    /** Max height (px) of a floating panel: window height minus insets minus a 16dp margin (never below {@code minPx}). */
    public static int maxPanelHeightPx(int windowHeightPx, int insetTopPx, int insetBottomPx, int marginPx, int minPx) {
        return Math.max(minPx, windowHeightPx - insetTopPx - insetBottomPx - marginPx);
    }

    /** Width (px) of a sheet or dialog: the window minus side margins, capped at {@link #SHEET_MAX_DP}. */
    public static int panelWidthPx(int windowWidthPx, int sideMarginPx, int capPx) {
        return Math.max(1, Math.min(capPx, windowWidthPx - 2 * sideMarginPx));
    }
}
