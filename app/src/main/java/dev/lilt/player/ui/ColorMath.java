package dev.lilt.player.ui;

/**
 * Colour maths for the design system: WCAG 2.x contrast, compositing, HSL adjustments and
 * contrast repair. Pure Java (no Android types) so it is covered by the logic tests.
 *
 * <p>All colours are packed ARGB ints ({@code 0xAARRGGBB}), the same format Android uses.
 */
public final class ColorMath {
    private ColorMath() {}

    /** WCAG AA for body text. */
    public static final double AA_TEXT = 4.5;
    /** WCAG AA for large text (18sp regular / 14sp bold) and for UI component boundaries. */
    public static final double AA_LARGE = 3.0;
    /** WCAG AAA for body text. */
    public static final double AAA_TEXT = 7.0;

    public static int alpha(int c) { return c >>> 24; }
    public static int red(int c) { return (c >> 16) & 0xFF; }
    public static int green(int c) { return (c >> 8) & 0xFF; }
    public static int blue(int c) { return c & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (clamp8(a) << 24) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    /** {@code color} with its alpha replaced by {@code alpha} (0..1). */
    public static int withAlpha(int color, float alpha) {
        return (Math.round(clamp01(alpha) * 255f) << 24) | (color & 0x00FFFFFF);
    }

    /** Source-over composite of {@code fg} onto an opaque {@code bg}; the result is opaque. */
    public static int composite(int fg, int bg) {
        float a = alpha(fg) / 255f;
        int r = Math.round(red(fg) * a + red(bg) * (1 - a));
        int g = Math.round(green(fg) * a + green(bg) * (1 - a));
        int b = Math.round(blue(fg) * a + blue(bg) * (1 - a));
        return argb(255, r, g, b);
    }

    /** Linear interpolation in sRGB, including alpha. {@code t} = 0 gives {@code a}. */
    public static int mix(int a, int b, float t) {
        t = clamp01(t);
        return argb(Math.round(alpha(a) + (alpha(b) - alpha(a)) * t), Math.round(red(a) + (red(b) - red(a)) * t),
                Math.round(green(a) + (green(b) - green(a)) * t), Math.round(blue(a) + (blue(b) - blue(a)) * t));
    }

    /** WCAG relative luminance (0 = black, 1 = white) of the colour's RGB channels. */
    public static double luminance(int color) {
        return 0.2126 * linear(red(color)) + 0.7152 * linear(green(color)) + 0.0722 * linear(blue(color));
    }

    /**
     * WCAG contrast ratio (1..21) of {@code fg} drawn on {@code bg}. A translucent {@code fg} is
     * first composited over {@code bg}; {@code bg} is treated as opaque.
     */
    public static double contrast(int fg, int bg) {
        int opaqueBg = bg | 0xFF000000;
        int shown = alpha(fg) == 255 ? fg : composite(fg, opaqueBg);
        double l1 = luminance(shown), l2 = luminance(opaqueBg);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    /** White or near-black (#0B0B0C), whichever reads better on {@code bg}. */
    public static int bestText(int bg) {
        int white = 0xFFFFFFFF, black = 0xFF0B0B0C;
        return contrast(white, bg) >= contrast(black, bg) ? white : black;
    }

    /** HSL of an opaque colour: {hue 0..360, saturation 0..1, lightness 0..1}. */
    public static float[] toHsl(int color) {
        float r = red(color) / 255f, g = green(color) / 255f, b = blue(color) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), l = (max + min) / 2f;
        float h = 0, s = 0, d = max - min;
        if (d > 1e-6f) {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = ((g - b) / d + (g < b ? 6 : 0));
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60f;
        }
        return new float[] {h, s, l};
    }

    /** Opaque colour from HSL ({@code h} in degrees, {@code s}, {@code l} 0..1). */
    public static int fromHsl(float h, float s, float l) {
        h = ((h % 360f) + 360f) % 360f;
        s = clamp01(s);
        l = clamp01(l);
        float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = l - c / 2;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return argb(255, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    /** Same hue and saturation with HSL lightness set to {@code lightness}. Keeps alpha. */
    public static int withLightness(int color, float lightness) {
        float[] hsl = toHsl(color);
        return (fromHsl(hsl[0], hsl[1], lightness) & 0x00FFFFFF) | (color & 0xFF000000);
    }

    /** Caps HSL saturation at {@code max} (keeps hue and lightness). Keeps alpha. */
    public static int limitSaturation(int color, float max) {
        float[] hsl = toHsl(color);
        if (hsl[1] <= max) return color;
        return (fromHsl(hsl[0], max, hsl[2]) & 0x00FFFFFF) | (color & 0xFF000000);
    }

    /**
     * Returns {@code fg}, lightened or darkened (same hue) just enough to reach {@code minRatio}
     * against {@code bg}. Moves toward white on dark backgrounds and toward black on light ones;
     * if even that cannot reach the ratio, returns the best of white / near-black.
     */
    public static int ensureContrast(int fg, int bg, double minRatio) {
        int opaque = fg | 0xFF000000;
        if (contrast(opaque, bg) >= minRatio) return opaque;
        boolean lighten = luminance(bg | 0xFF000000) < 0.18;
        float[] hsl = toHsl(opaque);
        float lo = hsl[2], hi = lighten ? 1f : 0f;
        int best = bestText(bg);
        if (contrast(fromHsl(hsl[0], hsl[1], hi), bg) < minRatio) return best;
        for (int i = 0; i < 24; i++) { // binary search the smallest lightness change that works
            float mid = (lo + hi) / 2f;
            if (contrast(fromHsl(hsl[0], hsl[1], mid), bg) >= minRatio) hi = mid; else lo = mid;
        }
        return fromHsl(hsl[0], hsl[1], hi);
    }

    /**
     * Darkens (or, for {@code textColor} darker than mid-grey, lightens) {@code bg} along its own
     * hue until {@code textColor} reaches {@code minRatio} on it. Used to make artwork tints safe
     * behind white text.
     */
    public static int ensureBackgroundFor(int bg, int textColor, double minRatio) {
        int opaque = bg | 0xFF000000;
        if (contrast(textColor, opaque) >= minRatio) return opaque;
        boolean darken = luminance(textColor | 0xFF000000) > 0.18;
        float[] hsl = toHsl(opaque);
        float lo = hsl[2], hi = darken ? 0f : 1f;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) / 2f;
            if (contrast(textColor, fromHsl(hsl[0], hsl[1], mid)) >= minRatio) hi = mid; else lo = mid;
        }
        return fromHsl(hsl[0], hsl[1], hi);
    }

    /**
     * The smallest alpha (at least {@code minAlpha}) at which {@code color} drawn over {@code bg}
     * reaches {@code minRatio}; 1 when even an opaque colour cannot.
     */
    public static float minimumAlpha(int color, int bg, double minRatio, float minAlpha) {
        if (contrast(withAlpha(color, 1f), bg) < minRatio) return 1f;
        float lo = clamp01(minAlpha), hi = 1f;
        if (contrast(withAlpha(color, lo), bg) >= minRatio) return lo;
        for (int i = 0; i < 20; i++) {
            float mid = (lo + hi) / 2f;
            if (contrast(withAlpha(color, mid), bg) >= minRatio) hi = mid; else lo = mid;
        }
        return hi;
    }

    /** Euclidean distance in RGB space (0..441), a cheap "are these visibly different" test. */
    public static double distance(int a, int b) {
        int dr = red(a) - red(b), dg = green(a) - green(b), db = blue(a) - blue(b);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /** {@code #RRGGBB} or {@code #AARRGGBB} (when translucent). */
    public static String hex(int color) {
        return alpha(color) == 255 ? String.format("#%06X", color & 0xFFFFFF) : String.format("#%08X", color);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
    private static int clamp8(int v) { return v < 0 ? 0 : Math.min(255, v); }
    static float clamp01(float v) { return v < 0 ? 0 : Math.min(1, v); }
}
