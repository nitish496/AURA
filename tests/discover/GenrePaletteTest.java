package dev.lilt.player.screens.discover;

import dev.lilt.player.stream.AudiusApi;
import dev.lilt.player.ui.ColorMath;
import dev.lilt.player.ui.ColorRoles;
import java.util.List;

/** Genre tiles: every Browse genre has a curated, calm, non-purple colour that carries white text. */
public final class GenrePaletteTest {
    public static void main(String[] args) {
        List<String> genres = AudiusApi.genres();
        for (String g : genres) {
            if (!GenrePalette.known().contains(g)) throw new AssertionError("no curated colour for " + g);
        }
        for (String g : GenrePalette.known()) assertCalm(g, GenrePalette.color(g));
        for (int c : GenrePalette.ROTATION) assertCalm("rotation " + ColorMath.hex(c), GenrePalette.color(null) == 0 ? 0 : c);
        assertCalm("unknown", GenrePalette.color("Spoken Word"));
        if (GenrePalette.color("Spoken Word") != GenrePalette.color("Spoken Word")) throw new AssertionError("stable fallback");
        // Horizontal and vertical neighbours in the two-column grid differ visibly.
        for (int i = 0; i < genres.size(); i++) {
            int c = GenrePalette.color(genres.get(i));
            if (i % 2 == 0 && i + 1 < genres.size()) distinct(genres.get(i), genres.get(i + 1), c, GenrePalette.color(genres.get(i + 1)));
            if (i + 2 < genres.size()) distinct(genres.get(i), genres.get(i + 2), c, GenrePalette.color(genres.get(i + 2)));
        }
        System.out.println("GenrePaletteTest OK (" + genres.size() + " genres)");
    }

    static void assertCalm(String what, int c) {
        float[] hsl = ColorMath.toHsl(c);
        boolean purple = hsl[1] > 0.10f && hsl[0] >= 245f && hsl[0] <= 345f;
        if (purple) throw new AssertionError(what + " is purple/magenta: " + ColorMath.hex(c) + " hue " + hsl[0]);
        if (hsl[1] > 0.62f) throw new AssertionError(what + " is too saturated: " + ColorMath.hex(c));
        double ratio = ColorMath.contrast(ColorRoles.ON_ART_PRIMARY, c);
        if (ratio < 4.5) throw new AssertionError(what + " text contrast " + ratio + " on " + ColorMath.hex(c));
    }

    static void distinct(String a, String b, int ca, int cb) {
        if (ColorMath.distance(ca, cb) < 18) throw new AssertionError(a + " and " + b + " look the same");
    }
}
