package dev.lilt.player;

import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.SpringCurve;
import dev.lilt.player.ui.TypeScale;

/**
 * AURA 2.0 design tokens: spacing, radii, sizes, elevation and motion. Colours live in
 * {@link ColorRoles} (dark + light), text styles in {@link TypeScale}. Values are dp / ms.
 *
 * <p>Source of truth for the HTML prototype is design/src/tokens.mjs, exported as
 * design/tokens.json; tests/ui/DesignTokensTest keeps the two in sync. See docs/DESIGN.md.
 *
 * <p>Pure Java: no Android imports (the logic tests compile it on the JVM).
 */
public final class DesignTokens {
    private DesignTokens() {}

    // ---------------------------------------------------------------- colour
    /** Default (dark) colour roles. Prefer {@code ui.Theme.colors(context)} in views. */
    public static final ColorRoles DARK = ColorRoles.DARK;
    /** Light colour roles. */
    public static final ColorRoles LIGHT = ColorRoles.LIGHT;

    // ---------------------------------------------------------------- spacing (4dp grid)
    public static final int SPACE_2 = 2, SPACE_4 = 4, SPACE_8 = 8, SPACE_12 = 12, SPACE_16 = 16, SPACE_20 = 20,
            SPACE_24 = 24, SPACE_32 = 32, SPACE_40 = 40, SPACE_48 = 48, SPACE_64 = 64;
    /** Horizontal screen margin for text, rows and the first card of a shelf. */
    public static final int MARGIN = 16;
    /** Gap between cards in shelves and grids. */
    public static final int GUTTER = 12;
    /** Space above a section header (between two sections). */
    public static final int SECTION_GAP = 28;
    /** Space between a section header and its content. */
    public static final int SECTION_HEADER_GAP = 12;
    /** Side margin of Now Playing, Lyrics and Queue. */
    public static final int PLAYER_MARGIN = 24;

    // ---------------------------------------------------------------- radii
    /** Badges (explicit). */
    public static final int RADIUS_XS = 4;
    /** Row artwork (44dp). */
    public static final int RADIUS_S = 6;
    /** Grid and shelf artwork, genre tiles, inset groups' inner items. */
    public static final int RADIUS_M = 10;
    /** Feature cards, grouped sections, notices, lifted rows. */
    public static final int RADIUS_L = 14;
    /** Menus and popovers. */
    public static final int RADIUS_XL = 22;
    /** Bottom sheets (top corners). */
    public static final int RADIUS_SHEET = 32;
    /** Marker for capsules and circles: radius = half the height. */
    public static final int RADIUS_CAPSULE = -1;

    /** Artwork corner radius by artwork size: 6 (≤48) · 8 (≤96) · 10 (≤200) · 12 (larger). */
    public static int artworkRadius(int sizeDp) {
        return sizeDp <= 48 ? 6 : sizeDp <= 96 ? 8 : sizeDp <= 200 ? 10 : 12;
    }

    /**
     * Concentric corners: an element inset by {@code inset} inside a container with radius
     * {@code outer} uses {@code outer - inset} (never below {@code min}).
     */
    public static int concentric(int outer, int inset, int min) {
        return Math.max(min, outer - inset);
    }

    // ---------------------------------------------------------------- sizes
    /** Minimum touch target in both dimensions. */
    public static final int TOUCH_TARGET = 48;
    public static final int ICON = 24, ICON_SMALL = 20, ICON_LARGE = 28, ICON_INLINE = 14;
    /** Song row with artwork: 60dp tall, 44dp artwork. */
    public static final int ROW_SONG = 60;
    /** Album track row (number instead of artwork). */
    public static final int ROW_TRACK = 52;
    /** Navigation / settings row. */
    public static final int ROW_LIST = 52;
    /** Settings row with a subtitle. */
    public static final int ROW_LIST_TALL = 64;
    public static final int COVER_ROW = 44, COVER_MINI = 40, COVER_MINI_DOCKED = 32, COVER_HEADER = 48;
    public static final int CARD = 152, CARD_FEATURE = 272, CARD_FEATURE_FOOTER = 84, ARTIST_CIRCLE = 112, GENRE_TILE_HEIGHT = 96;
    public static final int CHIP_HEIGHT = 32, BUTTON_HEIGHT = 48, SEGMENTED_HEIGHT = 36;
    /** Floating chrome (Liquid Glass layer). */
    public static final int TAB_BAR_HEIGHT = 64, TAB_BAR_MIN = 48, SEARCH_BUTTON = 64, MINI_PLAYER = 56,
            TOP_BAR = 52, GLASS_BUTTON = 40, GLASS_BUTTON_SMALL = 36, CHROME_GAP = 8, CHROME_BOTTOM_MARGIN = 8;
    /** Large title block height (34/40 text plus 12dp below). */
    public static final int LARGE_TITLE_BLOCK = 52;

    // ---------------------------------------------------------------- elevation (shadow) in dp
    /** Floating glass chrome: soft, wide. */
    public static final int ELEVATION_GLASS = 12;
    /** Now Playing artwork. */
    public static final int ELEVATION_ARTWORK = 16;
    /** Lifted (dragged) row, context-menu preview. */
    public static final int ELEVATION_LIFT = 8;

    // ---------------------------------------------------------------- glass material
    /** Backdrop blur radius of glass chrome (dp). */
    public static final int GLASS_BLUR = 24;
    /** Saturation boost applied to the blurred backdrop. */
    public static final float GLASS_SATURATION = 1.8f;
    /** Specular rim width (px, not dp: always one physical pixel). */
    public static final int GLASS_RIM_PX = 1;

    // ---------------------------------------------------------------- motion (ms)
    public static final int DURATION_FAST = 150, DURATION_BASE = 250, DURATION_SLOW = 350, DURATION_SHEET = 300;
    /** Shimmer cycle for skeletons. */
    public static final int DURATION_SHIMMER = 1400;
    /** One loop of the Now Playing mesh drift (slow on purpose). */
    public static final int DURATION_MESH_LOOP = 24000;
    /** Standard spring for chrome (tab bar minimise, mini player docking). */
    public static final SpringCurve SPRING = SpringCurve.DEFAULT;

    // ---------------------------------------------------------------- 1.x aliases
    // The 1.x MainActivity uses these until it is rewritten on the ui kit. They now resolve to the
    // 2.0 roles so the current screens pick up the new palette. Do not use in new code.
    /** @deprecated use {@link ColorRoles#canvas}. */ @Deprecated public static final int BG = ColorRoles.DARK.canvas;
    /** @deprecated use {@link ColorRoles#surface}. */ @Deprecated public static final int SURFACE = ColorRoles.DARK.surface;
    /** @deprecated use {@link ColorRoles#textPrimary}. */ @Deprecated public static final int INK = ColorRoles.DARK.textPrimary;
    /** @deprecated use {@link ColorRoles#textSecondary}. */ @Deprecated public static final int MUTED = ColorRoles.DARK.textSecondary;
    /** @deprecated the 1.x "accent" was the ink colour of controls; use {@link ColorRoles#textPrimary}. */
    @Deprecated public static final int ACCENT = ColorRoles.DARK.textPrimary;
    /** @deprecated use {@link ColorRoles#fill}. */ @Deprecated public static final int TINT = ColorRoles.DARK.fill;
    /** @deprecated use {@link ColorRoles#separator}. */ @Deprecated public static final int BORDER = 0xFF2C2C2F;
    /** @deprecated use {@link ColorRoles#canvas}. */ @Deprecated public static final int HOME_BG = ColorRoles.DARK.canvas;
    /** @deprecated flat canvas; no top gradient in 2.0. */ @Deprecated public static final int HOME_TOP = ColorRoles.DARK.canvas;
    /** @deprecated use {@link ColorRoles#surfaceElevated}. */ @Deprecated public static final int HOME_SURFACE = ColorRoles.DARK.surfaceElevated;
    /** @deprecated use {@link ColorRoles#accent}. */ @Deprecated public static final int HOME_ACCENT = ColorRoles.DARK.accent;
    /** @deprecated the second (gold) accent is retired; maps to {@link ColorRoles#textSecondary}. */
    @Deprecated public static final int HOME_GOLD = ColorRoles.DARK.textSecondary;
    /** @deprecated use {@link ColorRoles#textPrimary}. */ @Deprecated public static final int HOME_TEXT = ColorRoles.DARK.textPrimary;
    /** @deprecated use {@link ColorRoles#textSecondary}. */ @Deprecated public static final int HOME_MUTED = ColorRoles.DARK.textSecondary;
    /** @deprecated use {@link #TOUCH_TARGET}. */ @Deprecated public static final int TOUCH = TOUCH_TARGET;
    /** @deprecated use {@link #ROW_SONG}. */ @Deprecated public static final int ROW = ROW_SONG;
    /** @deprecated use {@link #COVER_ROW}. */ @Deprecated public static final int ROW_COVER = COVER_ROW;
    /** @deprecated use {@link #CARD}. */ @Deprecated public static final int GRID_COVER = CARD;
    /** @deprecated use {@link #artworkRadius(int)}. */ @Deprecated public static final int COVER_RADIUS = 8;
}
