package dev.lilt.player.ui;

/**
 * Artwork-derived colour for Now Playing, Lyrics, Queue, the mini player and feature-card
 * footers. Colour always comes from the artwork; when there is none (or it is greyscale) the
 * palette is neutral graphite — never a decorative gradient.
 *
 * <p>Contrast guarantees (tests/ui/ArtworkPaletteTest): {@link #textPrimary} reaches 7:1 on
 * {@link #background} and on every {@link #mesh} colour; {@link #textSecondary} reaches 4.5:1 on
 * all of them. Saturation is capped so tints stay calm rather than neon.
 *
 * <p>Feed it a small sample of ARGB pixels (e.g. 24 x 24 from {@code ArtworkColors}). Pure Java.
 */
public final class ArtworkPalette {
    /** Graphite fallback used when there is no artwork. */
    public static final ArtworkPalette NEUTRAL = neutral(false);

    /** True when the colours came from real artwork with discernible colour. */
    public final boolean fromArtwork;
    /** The artwork's most characteristic colour, unmodified. */
    public final int dominant;
    /** Deep tint behind white text (Now Playing base, mini-player tint, card footers). */
    public final int background;
    /** Darker companion of {@link #background}, for the lower part of the mesh. */
    public final int backgroundDeep;
    /** Four colours for {@link MeshBackground}: [glow, base, accent, deep]. */
    private final int[] mesh;
    /** Primary text and controls on the tint (always near-white). */
    public final int textPrimary;
    /** Secondary text on the tint: white with an alpha that keeps 4.5:1. */
    public final int textSecondary;
    /** Played part of scrubbers/sliders on the tint. */
    public final int controlFill;
    /** Unplayed part of scrubbers/sliders on the tint. */
    public final int controlTrack;

    private ArtworkPalette(boolean fromArtwork, int dominant, int background, int backgroundDeep, int[] mesh) {
        this.fromArtwork = fromArtwork;
        this.dominant = dominant;
        this.background = background;
        this.backgroundDeep = backgroundDeep;
        this.mesh = mesh;
        this.textPrimary = ColorRoles.ON_ART_PRIMARY;
        int lightest = background;
        for (int c : mesh) if (ColorMath.luminance(c) > ColorMath.luminance(lightest)) lightest = c;
        float alpha = ColorMath.minimumAlpha(ColorRoles.ON_ART_PRIMARY, lightest, ColorMath.AA_TEXT, 0.64f);
        this.textSecondary = ColorMath.withAlpha(ColorRoles.ON_ART_PRIMARY, alpha);
        this.controlFill = ColorMath.withAlpha(ColorRoles.ON_ART_PRIMARY, 0.88f);
        this.controlTrack = ColorRoles.ON_ART_TRACK;
    }

    /** The mesh colours (copy): glow, base, accent, deep. */
    public int[] mesh() { return mesh.clone(); }

    /** Graphite palette for the given theme (both are dark: these surfaces carry white text). */
    public static ArtworkPalette neutral(boolean lighter) {
        int base = lighter ? 0xFF2A2A2D : 0xFF1C1C1E;
        return new ArtworkPalette(false, base, base, 0xFF101012,
                new int[] {lighter ? 0xFF38383C : 0xFF2C2C2F, base, 0xFF232326, 0xFF0E0E10});
    }

    /** Builds a palette from ARGB pixels; fully transparent or empty input gives {@link #NEUTRAL}. */
    public static ArtworkPalette from(int[] pixels) {
        if (pixels == null || pixels.length == 0) return NEUTRAL;
        int[] count = new int[4096];
        long[] rs = new long[4096], gs = new long[4096], bs = new long[4096];
        int total = 0;
        double satSum = 0;
        for (int p : pixels) {
            if ((p >>> 24) < 128) continue;
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            int bucket = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            count[bucket]++;
            rs[bucket] += r; gs[bucket] += g; bs[bucket] += b;
            total++;
            satSum += (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255.0;
        }
        if (total == 0) return NEUTRAL;
        // Score each bucket by population and chroma; extremes (near-black/near-white) count less.
        int best = -1, second = -1, third = -1;
        double bestScore = -1, secondScore = -1, thirdScore = -1;
        int[] avg = new int[4096];
        for (int i = 0; i < 4096; i++) {
            if (count[i] == 0) continue;
            int c = 0xFF000000 | (int) (rs[i] / count[i]) << 16 | (int) (gs[i] / count[i]) << 8 | (int) (bs[i] / count[i]);
            avg[i] = c;
            double score = score(c, count[i] / (double) total);
            if (score > bestScore) {
                third = second; thirdScore = secondScore; second = best; secondScore = bestScore; best = i; bestScore = score;
            } else if (score > secondScore) {
                third = second; thirdScore = secondScore; second = i; secondScore = score;
            } else if (score > thirdScore) {
                third = i; thirdScore = score;
            }
        }
        int dominant = avg[best];
        boolean greyscale = satSum / total < 0.06;
        if (greyscale) {
            ArtworkPalette n = neutral(ColorMath.luminance(dominant) > 0.4);
            return new ArtworkPalette(true, dominant, n.background, n.backgroundDeep, n.mesh);
        }
        // Prefer companions that are visibly different from the dominant colour.
        int accent = pickDistinct(avg, count, total, dominant);
        int glowSource = second >= 0 ? avg[second] : dominant;
        if (ColorMath.distance(glowSource, dominant) < 24) glowSource = ColorMath.withLightness(dominant, Math.min(0.7f, ColorMath.toHsl(dominant)[2] + 0.12f));

        int background = tint(dominant, ColorMath.AAA_TEXT);
        int deep = ColorMath.withLightness(background, ColorMath.toHsl(background)[2] * 0.62f);
        int glow = tint(glowSource, ColorMath.AAA_TEXT);
        int accentTint = tint(accent, ColorMath.AAA_TEXT);
        return new ArtworkPalette(true, dominant, background, deep, new int[] {glow, background, accentTint, deep});
    }

    /** Calms a colour (saturation ≤ 0.55) and darkens it until white text reaches {@code ratio}. */
    static int tint(int color, double ratio) {
        int calm = ColorMath.limitSaturation(color | 0xFF000000, 0.55f);
        int safe = ColorMath.ensureBackgroundFor(calm, ColorRoles.ON_ART_PRIMARY, ratio + 0.15);
        float l = ColorMath.toHsl(safe)[2];
        return l < 0.07f ? ColorMath.withLightness(safe, 0.07f) : safe; // keep a trace of colour
    }

    private static double score(int c, double share) {
        float[] hsl = ColorMath.toHsl(c);
        double chroma = (Math.max(ColorMath.red(c), Math.max(ColorMath.green(c), ColorMath.blue(c)))
                - Math.min(ColorMath.red(c), Math.min(ColorMath.green(c), ColorMath.blue(c)))) / 255.0;
        double extreme = hsl[2] < 0.08 || hsl[2] > 0.94 ? 0.3 : 1.0;
        return share * (0.35 + chroma) * extreme;
    }

    private static int pickDistinct(int[] avg, int[] count, int total, int dominant) {
        int pick = dominant;
        double pickScore = -1;
        float domHue = ColorMath.toHsl(dominant)[0];
        for (int i = 0; i < avg.length; i++) {
            if (count[i] == 0) continue;
            float[] hsl = ColorMath.toHsl(avg[i]);
            float hueGap = Math.abs(hsl[0] - domHue);
            hueGap = Math.min(hueGap, 360 - hueGap);
            if (hueGap < 28 && ColorMath.distance(avg[i], dominant) < 70) continue;
            double s = score(avg[i], count[i] / (double) total);
            if (s > pickScore) { pickScore = s; pick = avg[i]; }
        }
        return pick;
    }
}
