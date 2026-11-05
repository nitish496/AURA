package dev.lilt.player.ui;

import java.util.Random;

public final class ColorMathTest {
    private static int checks;
    private static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }
    private static void near(double a, double b, double eps, String what) { check(Math.abs(a - b) <= eps, what + ": " + a + " vs " + b); }

    public static void main(String[] args) {
        near(ColorMath.contrast(0xFFFFFFFF, 0xFF000000), 21.0, 1e-9, "white on black is 21:1");
        near(ColorMath.contrast(0xFF000000, 0xFFFFFFFF), 21.0, 1e-9, "contrast is symmetric");
        near(ColorMath.contrast(0xFF777777, 0xFFFFFFFF), 4.48, 0.01, "#777 on white (WCAG reference)");
        near(ColorMath.luminance(0xFFFFFFFF), 1.0, 1e-9, "white luminance");
        near(ColorMath.luminance(0xFF000000), 0.0, 1e-9, "black luminance");
        check(ColorMath.composite(0x80FFFFFF, 0xFF000000) == 0xFF808080, "50% white over black");
        check(ColorMath.withAlpha(0xFF123456, 0.5f) == 0x80123456, "withAlpha");
        check(ColorMath.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080, "mix midpoint");
        near(ColorMath.contrast(0x80FFFFFF, 0xFF000000), ColorMath.contrast(0xFF808080, 0xFF000000), 1e-9, "translucent fg composited first");
        check(ColorMath.bestText(0xFF0B0B0C) == 0xFFFFFFFF, "white text on near-black");
        check(ColorMath.bestText(0xFFF4F4F6) != 0xFFFFFFFF, "dark text on light grey");
        check(ColorMath.hex(0xFFFF6A3D).equals("#FF6A3D") && ColorMath.hex(0x991C1C1E).equals("#991C1C1E"), "hex");

        // HSL round trip
        Random rnd = new Random(42);
        for (int i = 0; i < 400; i++) {
            int c = 0xFF000000 | rnd.nextInt(0x1000000);
            float[] hsl = ColorMath.toHsl(c);
            check(ColorMath.distance(ColorMath.fromHsl(hsl[0], hsl[1], hsl[2]), c) <= 2.0, "HSL round trip " + ColorMath.hex(c));
        }
        // Contrast repair always delivers the requested ratio (or the best possible text colour).
        for (int i = 0; i < 400; i++) {
            int fg = 0xFF000000 | rnd.nextInt(0x1000000), bg = 0xFF000000 | rnd.nextInt(0x1000000);
            int fixed = ColorMath.ensureContrast(fg, bg, 4.5);
            double best = Math.max(ColorMath.contrast(0xFFFFFFFF, bg), ColorMath.contrast(0xFF0B0B0C, bg));
            check(ColorMath.contrast(fixed, bg) >= Math.min(4.5, best) - 1e-6, "ensureContrast " + ColorMath.hex(fg) + " on " + ColorMath.hex(bg));
            int safeBg = ColorMath.ensureBackgroundFor(bg, 0xFFF5F5F7, 7.0);
            check(ColorMath.contrast(0xFFF5F5F7, safeBg) >= 7.0 - 1e-6, "ensureBackgroundFor " + ColorMath.hex(bg));
            float[] a = ColorMath.toHsl(bg), b = ColorMath.toHsl(safeBg);
            float hueGap = Math.abs(a[0] - b[0]);
            check(a[1] < 0.08f || b[2] < 0.02f || Math.min(hueGap, 360 - hueGap) < 6, "darkening keeps hue " + ColorMath.hex(bg));
        }
        // An already-compliant colour is returned unchanged.
        check(ColorMath.ensureContrast(0xFFF5F5F7, 0xFF0B0B0C, 4.5) == 0xFFF5F5F7, "no-op when compliant");
        float alpha = ColorMath.minimumAlpha(0xFFFFFFFF, 0xFF183B39, 4.5, 0.3f);
        check(alpha > 0.3f && alpha < 1f && ColorMath.contrast(ColorMath.withAlpha(0xFFFFFFFF, alpha), 0xFF183B39) >= 4.5, "minimumAlpha");
        check(ColorMath.limitSaturation(0xFFFF0000, 0.5f) != 0xFFFF0000 && ColorMath.toHsl(ColorMath.limitSaturation(0xFFFF0000, 0.5f))[1] <= 0.51f, "limitSaturation");
        System.out.println(checks + " colour-math checks passed");
    }
}
