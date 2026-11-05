package dev.lilt.player.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Device-independent equaliser presets. Each preset is a response curve given at ISO octave
 * centres (31 Hz .. 16 kHz). Phones expose different equalisers (commonly 5 bands at 60 Hz,
 * 230 Hz, 910 Hz, 3.6 kHz and 14 kHz, but 3 to 10 bands exist), so a curve is sampled at each of
 * the device's band centres by linear interpolation in log frequency and clamped to the device's
 * level range. Boosts are kept modest (6 dB or less) so presets do not drive the signal into
 * clipping.
 */
public final class EqualizerPresets {
    public static final String FLAT = "Flat", CUSTOM = "Custom";
    /** Octave centres the preset tables are written at. */
    static final double[] OCTAVES = {31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    private static final Map<String, double[]> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put(FLAT,             new double[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        PRESETS.put("Bass Boost",     new double[]{5.5, 5, 4, 2.5, 1, 0, 0, 0, 0, 0});
        PRESETS.put("Bass Reducer",   new double[]{-5.5, -5, -4, -2.5, -1, 0, 0, 0, 0, 0});
        PRESETS.put("Treble Boost",   new double[]{0, 0, 0, 0, 0, 0.5, 1.5, 3, 4.5, 5.5});
        PRESETS.put("Treble Reducer", new double[]{0, 0, 0, 0, 0, -0.5, -1.5, -3, -4.5, -5.5});
        PRESETS.put("Vocal",          new double[]{-2, -2, -1, 0.5, 2.5, 3.5, 3, 1.5, 0, -1});
        PRESETS.put("Loudness",       new double[]{5, 4, 2, 0, -1, -1, 0, 1, 3, 4});
        PRESETS.put("Acoustic",       new double[]{3, 3, 2.5, 1, 1.5, 1.5, 2.5, 3, 2.5, 1.5});
        PRESETS.put("Classical",      new double[]{3.5, 3, 2.5, 1.5, -1, -1, 0, 2, 2.5, 3});
        PRESETS.put("Electronic",     new double[]{4, 3.5, 1, 0, -1.5, 1, 0.5, 1, 3.5, 4.5});
        PRESETS.put("Hip-Hop",        new double[]{5, 4, 1.5, 3, -1, -1, 1, -1, 2, 3});
        PRESETS.put("Jazz",           new double[]{3, 2.5, 1, 2, -1.5, -1.5, 0, 1.5, 2.5, 3});
        PRESETS.put("Pop",            new double[]{-1, -0.5, 0, 2, 3.5, 3.5, 2, 0, -1, -1.5});
        PRESETS.put("Rock",           new double[]{4.5, 3.5, 2.5, 1, -0.5, -1, 0.5, 2.5, 3.5, 4.5});
        PRESETS.put("Late Night",     new double[]{3, 2.5, 1.5, 0.5, 0, 0, 0.5, 1, 1.5, 1});
        PRESETS.put("Spoken Word",    new double[]{-3, -2, 0, 1, 3, 3.5, 3, 2, 1, -1});
        PRESETS.put("Small Speakers", new double[]{-6, -4, 2, 3, 2, 0, 0, 1.5, 2, 1});
    }

    private EqualizerPresets() {}

    /** Preset names in display order (Custom is not a preset; it is the user's own curve). */
    public static List<String> names() { return Collections.unmodifiableList(new ArrayList<>(PRESETS.keySet())); }

    /** Canonical preset name for a case-insensitive match, CUSTOM for "custom", else null. */
    public static String canonical(String name) {
        if (name == null) return null;
        String wanted = name.trim();
        if (wanted.equalsIgnoreCase(CUSTOM)) return CUSTOM;
        for (String preset : PRESETS.keySet()) if (preset.equalsIgnoreCase(wanted)) return preset;
        String loose = wanted.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        for (String preset : PRESETS.keySet()) if (preset.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").equals(loose)) return preset;
        return null;
    }

    /** Control points {hz, dB} for a preset, or null for unknown names and CUSTOM. */
    public static double[][] curve(String name) {
        String key = canonical(name);
        double[] gains = key == null ? null : PRESETS.get(key);
        if (gains == null) return null;
        double[][] points = new double[OCTAVES.length][];
        for (int i = 0; i < OCTAVES.length; i++) points[i] = new double[]{OCTAVES[i], gains[i]};
        return points;
    }

    /** Gain of a curve at a frequency: linear in log2(frequency), flat beyond the end points. */
    public static double gainAt(double[][] curve, double hz) {
        if (curve == null || curve.length == 0 || !(hz > 0)) return 0;
        if (hz <= curve[0][0]) return curve[0][1];
        if (hz >= curve[curve.length - 1][0]) return curve[curve.length - 1][1];
        for (int i = 1; i < curve.length; i++) {
            if (hz <= curve[i][0]) {
                double x0 = log2(curve[i - 1][0]), x1 = log2(curve[i][0]);
                double t = x1 == x0 ? 0 : (log2(hz) - x0) / (x1 - x0);
                return curve[i - 1][1] + t * (curve[i][1] - curve[i - 1][1]);
            }
        }
        return curve[curve.length - 1][1];
    }

    /** Samples a curve at the device's band centres (Hz) and returns millibel levels. */
    public static short[] levels(double[][] curve, int[] centreHz, int minMb, int maxMb) {
        int bands = centreHz == null ? 0 : centreHz.length;
        short[] result = new short[bands];
        for (int b = 0; b < bands; b++) result[b] = clampMb(Math.round(gainAt(curve, centreHz[b]) * 100), minMb, maxMb);
        return result;
    }

    /** Device levels for a named preset; all zeros for unknown names. */
    public static short[] levelsFor(String name, int[] centreHz, int minMb, int maxMb) {
        double[][] curve = curve(name);
        if (curve == null) curve = curve(FLAT);
        return levels(curve, centreHz, minMb, maxMb);
    }

    /** Turns user-set device band levels into a device-independent curve sorted by frequency. */
    public static double[][] curveFromLevels(short[] levelsMb, int[] centreHz) {
        if (levelsMb == null || centreHz == null || levelsMb.length != centreHz.length || levelsMb.length == 0) return null;
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < levelsMb.length; i++) if (centreHz[i] > 0) points.add(new double[]{centreHz[i], levelsMb[i] / 100.0});
        if (points.isEmpty()) return null;
        points.sort((a, b) -> Double.compare(a[0], b[0]));
        return points.toArray(new double[0][]);
    }

    static short clampMb(long value, int minMb, int maxMb) {
        int low = Math.min(minMb, maxMb), high = Math.max(minMb, maxMb);
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.max(low, Math.min(high, value))));
    }

    private static double log2(double value) { return Math.log(value) / Math.log(2); }
}
