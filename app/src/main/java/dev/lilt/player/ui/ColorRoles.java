package dev.lilt.player.ui;

/**
 * The colour roles of one theme. Screens never use raw hex values: they ask for a role
 * ({@code roles.textSecondary}) so dark and light themes stay consistent and contrast-safe.
 *
 * <p>Rules (checked by tests/ui/DesignTokensTest): every text role reaches WCAG AA (4.5:1) on
 * {@link #canvas}, {@link #surface} and {@link #surfaceElevated}; {@link #textPrimary} and
 * {@link #textSecondary} also reach AA on {@link #fill}. {@link #textTertiary} is for metadata on
 * canvas/surfaces only, never on fills. {@link #accent} ("Ember") marks state and the one primary
 * action of a screen; it is never a background wash.
 *
 * <p>Values mirror design/tokens.json (generated from design/src/tokens.mjs). Pure Java.
 */
public final class ColorRoles {
    /** Default theme: neutral near-black. */
    public static final ColorRoles DARK = new ColorRoles(true);
    /** Light theme. Now Playing, Lyrics and Queue stay artwork-tinted dark in both themes. */
    public static final ColorRoles LIGHT = new ColorRoles(false);

    public final boolean dark;
    /** Window background behind all content. */
    public final int canvas;
    /** Grouped sections, inset lists, notices. */
    public final int surface;
    /** Sheets, menus and lifted rows when glass is unavailable. */
    public final int surfaceElevated;
    /** Secondary buttons, chips, search fields, artwork placeholders behind icons. */
    public final int fill;
    /** Pressed fills, selected segment, slider tracks. */
    public final int fillStrong;
    /** 1px hairlines between rows (translucent). */
    public final int separator;
    public final int textPrimary;
    public final int textSecondary;
    public final int textTertiary;
    /** Disabled text and icons; exempt from contrast requirements by definition. */
    public final int textDisabled;
    /** Ember: selected tab, playing row, toggles on, primary-action labels. */
    public final int accent;
    /** Text and icons drawn on an {@link #accent} fill. */
    public final int onAccent;
    public final int error;
    /** Liquid Glass tint layer drawn over the blurred backdrop (API 31+). */
    public final int glassTint;
    /** Denser tint used when there is no blur (API 26-30, power saver, reduce transparency). */
    public final int glassTintFallback;
    /** Specular rim gradient, top colour. */
    public final int glassRimTop;
    /** Specular rim gradient, bottom colour. */
    public final int glassRimBottom;
    /** Colour of the soft shadow under floating glass. */
    public final int glassShadow;
    /** Highlight capsule behind the selected item inside glass (selected tab, menu row). */
    public final int glassLens;
    /** Dims content behind sheets and menus. */
    public final int scrim;
    public final int skeleton;
    public final int skeletonHighlight;
    /** Graphite behind missing artwork (see {@link Initials}). */
    public final int placeholder;
    /** Initials drawn on {@link #placeholder}. */
    public final int placeholderText;
    /** Track colour of an "off" toggle. */
    public final int toggleOff;

    private ColorRoles(boolean dark) {
        this.dark = dark;
        canvas = dark ? 0xFF0B0B0C : 0xFFFFFFFF;
        surface = dark ? 0xFF161618 : 0xFFF4F4F6;
        surfaceElevated = dark ? 0xFF1E1E21 : 0xFFFFFFFF;
        fill = dark ? 0xFF262629 : 0xFFEDEDF0;
        fillStrong = dark ? 0xFF313135 : 0xFFE2E2E6;
        separator = dark ? 0x1AFFFFFF : 0x1A000000;
        textPrimary = dark ? 0xFFF5F5F7 : 0xFF0B0B0C;
        textSecondary = dark ? 0xFFA1A1A8 : 0xFF545459;
        textTertiary = dark ? 0xFF85858C : 0xFF6E6E74;
        textDisabled = dark ? 0xFF4A4A4F : 0xFFB8B8BE;
        accent = dark ? 0xFFFF6A3D : 0xFFC23A12;
        onAccent = dark ? 0xFF0B0B0C : 0xFFFFFFFF;
        error = dark ? 0xFFFF6961 : 0xFFC4261D;
        glassTint = dark ? 0x991C1C1E : 0xA6FFFFFF;
        glassTintFallback = dark ? 0xEB1E1E21 : 0xF0FAFAFC;
        glassRimTop = dark ? 0x59FFFFFF : 0xE6FFFFFF;
        glassRimBottom = dark ? 0x0DFFFFFF : 0x4DFFFFFF;
        glassShadow = dark ? 0x59000000 : 0x1F000000;
        glassLens = dark ? 0x1FFFFFFF : 0x0F000000;
        scrim = dark ? 0x73000000 : 0x4D000000;
        skeleton = dark ? 0xFF1A1A1D : 0xFFEFEFF2;
        skeletonHighlight = dark ? 0xFF242428 : 0xFFF7F7F9;
        placeholder = dark ? 0xFF1C1C1F : 0xFFE9E9EC;
        placeholderText = dark ? 0xFF6A6A70 : 0xFF8E8E94;
        toggleOff = dark ? 0xFF39393D : 0xFFE3E3E8;
    }

    /** Roles for a theme: {@code dark} picks {@link #DARK}, otherwise {@link #LIGHT}. */
    public static ColorRoles of(boolean dark) { return dark ? DARK : LIGHT; }

    // Roles on artwork-tinted backgrounds (Now Playing, Lyrics, Queue, feature-card footers).
    /** Primary text and controls on artwork tints. */
    public static final int ON_ART_PRIMARY = 0xFFF5F5F7;
    /** Secondary text on artwork tints; {@link ArtworkPalette} guarantees 4.5:1. */
    public static final int ON_ART_SECONDARY = 0xA3F5F5F7;
    /** Tertiary icons (drag handles, dim rows) on artwork tints. */
    public static final int ON_ART_TERTIARY = 0x73F5F5F7;
    /** Glass tint for controls placed on artwork tints (lighter, so the colour shows through). */
    public static final int ON_ART_GLASS = 0x24FFFFFF;
    /** Inactive part of a scrubber or slider track on artwork tints. */
    public static final int ON_ART_TRACK = 0x38FFFFFF;
}
