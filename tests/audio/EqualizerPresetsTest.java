package dev.lilt.player.audio;

import java.util.Arrays;
import java.util.List;

/** Preset maths: log-frequency interpolation onto any device band layout, clamping, custom curves. */
public final class EqualizerPresetsTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static boolean near(double a, double b, double tolerance) { return Math.abs(a - b) <= tolerance; }

    public static void main(String[] args) {
        List<String> names = EqualizerPresets.names();
        check(names.get(0).equals(EqualizerPresets.FLAT), "Flat first");
        for (String wanted : new String[]{"Bass Boost", "Vocal", "Treble Boost", "Loudness", "Acoustic", "Electronic", "Late Night"})
            check(names.contains(wanted), "preset " + wanted);
        check(!names.contains(EqualizerPresets.CUSTOM), "Custom is not a preset");
        check("Bass Boost".equals(EqualizerPresets.canonical("bass boost")), "case-insensitive name");
        check("Bass Boost".equals(EqualizerPresets.canonical("BASS_BOOST")), "loose name");
        check("Hip-Hop".equals(EqualizerPresets.canonical("hip hop")), "punctuation-insensitive name");
        check(EqualizerPresets.CUSTOM.equals(EqualizerPresets.canonical("custom")), "custom name");
        check(EqualizerPresets.canonical("Disco Inferno") == null && EqualizerPresets.canonical(null) == null, "unknown name");
        check(EqualizerPresets.curve("Custom") == null && EqualizerPresets.curve("nope") == null, "no curve for custom/unknown");

        // Every preset stays within +-6 dB so presets cannot drive a hot master into clipping.
        for (String name : names) for (double[] point : EqualizerPresets.curve(name))
            check(Math.abs(point[1]) <= 6.0, "modest gains in " + name);

        double[][] bass = EqualizerPresets.curve("Bass Boost");
        check(EqualizerPresets.gainAt(bass, 31) == 5.5 && EqualizerPresets.gainAt(bass, 16000) == 0, "exact at control points");
        check(EqualizerPresets.gainAt(bass, 10) == 5.5 && EqualizerPresets.gainAt(bass, 22000) == 0, "flat beyond the ends");
        check(near(EqualizerPresets.gainAt(bass, Math.sqrt(31.0 * 63.0)), 5.25, 1e-9), "log-frequency midpoint");
        check(EqualizerPresets.gainAt(bass, 0) == 0 && EqualizerPresets.gainAt(null, 100) == 0, "degenerate input");

        int[] fiveBand = {60, 230, 910, 3600, 14000};   // the common AOSP layout
        short[] flat = EqualizerPresets.levelsFor("Flat", fiveBand, -1500, 1500);
        check(Arrays.equals(flat, new short[5]), "flat is zero everywhere");
        short[] boost = EqualizerPresets.levelsFor("Bass Boost", fiveBand, -1500, 1500);
        check(boost[0] >= 480 && boost[0] <= 520, "60 Hz boosted about 5 dB: " + boost[0]);
        check(boost[1] > 200 && boost[1] < 300, "230 Hz boosted about 2.7 dB: " + boost[1]);
        for (int i = 1; i < boost.length; i++) check(boost[i] <= boost[i - 1], "bass boost slopes down");
        check(boost[3] == 0 && boost[4] == 0, "treble untouched");
        short[] treble = EqualizerPresets.levelsFor("Treble Boost", fiveBand, -1500, 1500);
        check(treble[0] == 0 && treble[4] > 400, "treble boost lifts 14 kHz");
        check(Arrays.equals(EqualizerPresets.levelsFor("unknown", fiveBand, -1500, 1500), flat), "unknown preset is flat");

        check(EqualizerPresets.levelsFor("Bass Boost", new int[]{60}, -300, 300)[0] == 300, "clamped to device max");
        check(EqualizerPresets.levelsFor("Bass Reducer", new int[]{60}, -300, 300)[0] == -300, "clamped to device min");
        check(EqualizerPresets.levelsFor("Bass Boost", new int[]{60}, 300, -300)[0] == 300, "range given backwards");
        check(EqualizerPresets.levelsFor("Bass Boost", new int[0], -1500, 1500).length == 0, "no bands");

        int[] tenBand = {31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
        short[] vocal = EqualizerPresets.levelsFor("Vocal", tenBand, -1500, 1500);
        double[][] vocalCurve = EqualizerPresets.curve("Vocal");
        for (int i = 0; i < tenBand.length; i++) check(vocal[i] == Math.round(vocalCurve[i][1] * 100), "10-band device gets the table");

        // Custom bands become a device-independent curve and map back exactly.
        short[] custom = {300, -200, 0, 100, 500};
        double[][] curve = EqualizerPresets.curveFromLevels(custom, fiveBand);
        check(curve != null && curve.length == 5 && curve[0][0] == 60 && curve[4][1] == 5.0, "custom curve points");
        check(Arrays.equals(EqualizerPresets.levels(curve, fiveBand, -1500, 1500), custom), "custom round trip");
        double[][] unsorted = EqualizerPresets.curveFromLevels(new short[]{100, 200}, new int[]{1000, 100});
        check(unsorted[0][0] == 100 && unsorted[1][0] == 1000 && unsorted[0][1] == 2.0, "curve sorted by frequency");
        check(EqualizerPresets.curveFromLevels(new short[]{1}, new int[]{1, 2}) == null, "mismatched lengths rejected");
        // A custom curve made on a 5-band phone still works on a 10-band phone.
        short[] moved = EqualizerPresets.levels(curve, tenBand, -1500, 1500);
        check(moved[0] == 300 && moved[9] == 500, "custom curve on another layout");

        System.out.println("EqualizerPresetsTest: " + checks + " checks passed");
    }
}
