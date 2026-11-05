package dev.lilt.player.ui;

import dev.lilt.player.DesignTokens;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Contrast rules of the colour roles, grid alignment, type scale sanity and JSON sync. */
public final class DesignTokensTest {
    private static int checks;
    private static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        for (ColorRoles r : new ColorRoles[] {ColorRoles.DARK, ColorRoles.LIGHT}) {
            String t = r.dark ? "dark" : "light";
            for (int bg : new int[] {r.canvas, r.surface, r.surfaceElevated}) {
                for (int fg : new int[] {r.textPrimary, r.textSecondary, r.textTertiary, r.accent, r.error})
                    check(ColorMath.contrast(fg, bg) >= ColorMath.AA_TEXT, t + " text role " + ColorMath.hex(fg) + " on " + ColorMath.hex(bg));
            }
            for (int fg : new int[] {r.textPrimary, r.textSecondary, r.accent})
                check(ColorMath.contrast(fg, r.fill) >= ColorMath.AA_TEXT, t + " " + ColorMath.hex(fg) + " on fill");
            check(ColorMath.contrast(r.onAccent, r.accent) >= ColorMath.AA_TEXT, t + " onAccent on accent");
            check(ColorMath.contrast(r.textPrimary, r.canvas) >= ColorMath.AAA_TEXT, t + " primary text is AAA");
            check(ColorMath.contrast(r.textPrimary, ColorMath.composite(r.glassTintFallback, r.canvas)) >= ColorMath.AAA_TEXT, t + " text on fallback glass");
            check(ColorMath.contrast(r.accent, r.canvas) >= ColorMath.AA_LARGE, t + " accent visible as a UI element");
            check(ColorMath.alpha(r.glassTint) > 0x80 && ColorMath.alpha(r.glassTint) < ColorMath.alpha(r.glassTintFallback), t + " fallback glass is denser");
            check(ColorMath.alpha(r.separator) < 0x40, t + " separators are hairlines, not borders");
            float[] accentHsl = ColorMath.toHsl(r.accent);
            check(accentHsl[0] < 30 || accentHsl[0] > 345, t + " accent is ember (red-orange), never violet");
        }
        // No purple, violet, indigo or magenta anywhere in the roles.
        for (ColorRoles r : new ColorRoles[] {ColorRoles.DARK, ColorRoles.LIGHT}) {
            for (Field f : ColorRoles.class.getFields()) {
                if (f.getType() != int.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                int c = f.getInt(r);
                float[] hsl = ColorMath.toHsl(c);
                check(!(hsl[1] > 0.15f && hsl[0] > 250 && hsl[0] < 330), "no purple role: " + f.getName());
            }
        }
        // Spacing and sizes sit on the 4dp grid (2dp only for hairline gaps).
        for (Field f : DesignTokens.class.getFields()) {
            if (f.getType() != int.class || f.isAnnotationPresent(Deprecated.class)) continue;
            String n = f.getName();
            int v = f.getInt(null);
            if (n.startsWith("SPACE_") && v != 2) check(v % 4 == 0, n + " on 4dp grid");
            if (n.startsWith("ROW_") || n.startsWith("TAB_") || n.equals("MINI_PLAYER") || n.equals("TOUCH_TARGET") || n.equals("BUTTON_HEIGHT") || n.startsWith("COVER_") || n.startsWith("CARD"))
                check(v % 4 == 0, n + " on 4dp grid");
        }
        check(DesignTokens.TOUCH_TARGET >= 48, "touch target");
        check(DesignTokens.artworkRadius(44) == 6 && DesignTokens.artworkRadius(152) == 10 && DesignTokens.artworkRadius(342) == 12, "artwork radii");
        check(DesignTokens.concentric(DesignTokens.RADIUS_XL, 8, 4) == 14, "concentric radius");
        check(DesignTokens.MINI_PLAYER >= DesignTokens.COVER_MINI + 2 * 8, "mini player fits its artwork with 8dp padding");
        // Type scale: ramp strictly decreasing in size, sensible line heights, real weights.
        TypeScale.Style[] ramp = TypeScale.ramp();
        for (int i = 1; i < ramp.length; i++) check(ramp[i].size <= ramp[i - 1].size, "ramp ordered at " + ramp[i].name);
        for (TypeScale.Style s : TypeScale.all()) {
            check(s.lineHeight >= s.size * 1.15f && s.lineHeight <= s.size * 1.45f, "line height of " + s);
            check(s.weight == 400 || s.weight == 500 || s.weight == 600 || s.weight == 700, "bundled weight for " + s);
            check(!s.display || s.weight == 700, "Inter Display is bundled in bold only: " + s);
            check(Math.abs(s.tracking) <= 0.07f, "tracking range for " + s);
        }
        check(Math.abs(TypeScale.interTracking(17) - (-0.0128f)) < 0.001f, "Inter dynamic tracking at 17sp");
        check(TypeScale.extraLineSpacing(22, -16, 4) == 2, "extra line spacing");

        // design/tokens.json (generated from design/src/tokens.mjs) agrees with the Java roles.
        Path json = Path.of("design/tokens.json");
        if (Files.exists(json)) {
            String text = Files.readString(json, StandardCharsets.UTF_8);
            for (String theme : new String[] {"dark", "light"}) {
                ColorRoles r = theme.equals("dark") ? ColorRoles.DARK : ColorRoles.LIGHT;
                String block = section(text, "\"" + theme + "\"");
                String[][] keys = {{"canvas", hex(r.canvas)}, {"surface", hex(r.surface)}, {"surfaceElevated", hex(r.surfaceElevated)},
                        {"fill", hex(r.fill)}, {"fillStrong", hex(r.fillStrong)}, {"textPrimary", hex(r.textPrimary)},
                        {"textSecondary", hex(r.textSecondary)}, {"textTertiary", hex(r.textTertiary)}, {"textDisabled", hex(r.textDisabled)},
                        {"accent", hex(r.accent)}, {"onAccent", hex(r.onAccent)}, {"error", hex(r.error)},
                        {"skeleton", hex(r.skeleton)}, {"skeletonHighlight", hex(r.skeletonHighlight)}, {"placeholder", hex(r.placeholder)},
                        {"placeholderText", hex(r.placeholderText)}, {"toggleOff", hex(r.toggleOff)}};
                for (String[] k : keys) {
                    Matcher m = Pattern.compile("\"" + k[0] + "\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"").matcher(block);
                    check(m.find() && m.group(1).equalsIgnoreCase(k[1]), theme + "." + k[0] + " matches tokens.json");
                }
            }
            check(text.contains("\"largeTitle\"") && text.contains("\"size\": 34"), "type scale exported");
        }
        System.out.println(checks + " design-token checks passed");
    }

    private static String hex(int c) { return String.format("#%06X", c & 0xFFFFFF); }

    private static String section(String text, String key) {
        int start = text.indexOf(key);
        if (start < 0) throw new AssertionError("missing " + key + " in tokens.json");
        int open = text.indexOf('{', start), depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == '{') depth++;
            else if (text.charAt(i) == '}' && --depth == 0) return text.substring(open, i + 1);
        }
        throw new AssertionError("unterminated " + key);
    }
}
