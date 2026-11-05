package dev.lilt.player.ui;

/**
 * Typographic placeholders for missing artwork and artist pictures: up to two initials and a
 * deterministic tone, so the same album always gets the same placeholder. Pure Java.
 */
public final class Initials {
    private Initials() {}

    /**
     * Up to two initials: "Low Tide" → "LT", "The Quiet Hours" → "QH", "Björk" → "B",
     * "2 Lane Road" → "2L". Leading articles (the / a / an) are skipped when more words follow.
     * Returns "" when the text has no letters or digits (the placeholder then shows the AURA mark).
     */
    public static String of(String text) {
        if (text == null) return "";
        String[] words = text.trim().split("[\\s\\-_/·.,:;&+()\\[\\]]+");
        int start = 0;
        if (words.length > 1 && isArticle(words[0])) start = 1;
        StringBuilder out = new StringBuilder(4);
        for (int i = start; i < words.length && out.codePointCount(0, out.length()) < 2; i++) {
            String w = words[i];
            for (int k = 0; k < w.length(); ) {
                int cp = w.codePointAt(k);
                if (Character.isLetterOrDigit(cp)) { out.appendCodePoint(Character.toUpperCase(cp)); break; }
                k += Character.charCount(cp);
            }
        }
        return out.toString();
    }

    /** A stable index in {@code 0..tones-1} for {@code key} (album key, artist name, …). */
    public static int tone(String key, int tones) {
        if (tones <= 1 || key == null) return 0;
        int h = 7;
        for (int i = 0; i < key.length(); i++) h = h * 31 + key.charAt(i);
        return Math.floorMod(h, tones);
    }

    /**
     * Placeholder background for {@code key}: the theme's placeholder graphite shifted by a small,
     * deterministic amount of lightness (±2.5%) so neighbouring placeholders are distinguishable
     * without introducing colour.
     */
    public static int background(String key, ColorRoles roles) {
        int t = tone(key, 4); // 0..3
        float[] hsl = ColorMath.toHsl(roles.placeholder);
        float delta = (t - 1.5f) * (roles.dark ? 0.017f : 0.012f);
        return ColorMath.fromHsl(hsl[0], hsl[1], hsl[2] + delta);
    }

    private static boolean isArticle(String w) {
        return w.equalsIgnoreCase("the") || w.equalsIgnoreCase("a") || w.equalsIgnoreCase("an");
    }
}
