package dev.lilt.player.screens.discover;

import dev.lilt.player.ui.ColorMath;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Initials;
import java.util.HashMap;
import java.util.Map;

/**
 * Curated tile colours for Browse genres: calm, earthy tones taken from the design's sleeve
 * palette (steel, rust, olive, teal, ochre, sage, pine, slate, clay...). Never purple, violet,
 * indigo or magenta; every colour carries {@link ColorRoles#ON_ART_PRIMARY} text at 4.5:1 or
 * better (tests/discover/GenrePaletteTest). Neighbouring tiles in the two-column grid differ.
 * Pure Java.
 */
public final class GenrePalette {
    private GenrePalette() {}

    private static final Map<String, Integer> BY_GENRE = new HashMap<>();
    /** Fallback rotation for genres the catalogue adds later. */
    static final int[] ROTATION = {0xFF2D4963, 0xFF8A3A22, 0xFF4E5930, 0xFF1F4B49, 0xFF7E5A1E, 0xFF5B5E57,
            0xFF24452F, 0xFF3A4652, 0xFF8A4A2C, 0xFF1E4F5E, 0xFF5A4632, 0xFF6E2A28};

    static {
        put("Electronic", 0xFF2D4963);   // steel
        put("Hip-Hop/Rap", 0xFF8A3A22);  // rust
        put("Pop", 0xFF1E5260);          // petrol
        put("R&B/Soul", 0xFF6A4128);     // umber
        put("Alternative", 0xFF4E5930);  // olive
        put("Rock", 0xFF6E2A28);         // oxblood
        put("Lo-Fi", 0xFF56602F);        // moss olive
        put("House", 0xFF2B4A6F);        // denim
        put("Techno", 0xFF38383C);       // graphite
        put("Drum & Bass", 0xFF8A4A2C);  // clay
        put("Dubstep", 0xFF7A2E2A);      // brick
        put("Trap", 0xFF2E4D3A);         // forest
        put("Ambient", 0xFF1F4B49);      // teal
        put("Downtempo", 0xFF6E5C40);    // sand
        put("Jazz", 0xFF7E5A1E);         // ochre
        put("Classical", 0xFF44505C);    // storm
        put("Soundtrack", 0xFF3A4652);   // slate
        put("Latin", 0xFF94402F);        // terracotta
        put("Reggae", 0xFF3F5A3A);       // leaf
        put("Dancehall", 0xFF80531A);    // amber
        put("Afrobeats", 0xFF8C4426);    // copper
        put("World", 0xFF24515A);        // lagoon
        put("Folk", 0xFF66563E);         // walnut
        put("Acoustic", 0xFF5B5E57);     // sage
        put("Country", 0xFF7A5030);      // saddle
        put("Funk", 0xFF2B4A6F);         // denim
        put("Blues", 0xFF24395A);        // navy
        put("Metal", 0xFF2F2F33);        // iron
        put("Punk", 0xFF742B2B);         // cherry-brown
        put("Experimental", 0xFF4A5560); // pewter
        put("Hyperpop", 0xFF9A3F33);     // coral
        put("Devotional", 0xFF57582F);   // laurel
    }

    private static void put(String genre, int color) { BY_GENRE.put(genre, color); }

    /** Tile colour for an Audius genre name (exact API spelling); unknown genres get a stable rotation colour. */
    public static int color(String genre) {
        Integer c = genre == null ? null : BY_GENRE.get(genre);
        int base = c != null ? c : ROTATION[Initials.tone(genre == null ? "" : genre, ROTATION.length)];
        return ColorMath.ensureBackgroundFor(base, ColorRoles.ON_ART_PRIMARY, 4.6);
    }

    /** Genres with a curated colour. */
    static java.util.Set<String> known() { return BY_GENRE.keySet(); }
}
