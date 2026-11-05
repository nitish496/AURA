package dev.lilt.player.audio;

import java.util.Locale;

/**
 * The user's sound settings, persisted as one small text record. Custom equaliser curves are
 * stored as frequency/gain points so they survive a change of phone (different band layout).
 */
public final class AudioEffectSettings {
    public static final float MIN_SPEED = 0.5f, MAX_SPEED = 2.0f;
    private static final String VERSION = "v1";

    public boolean eqEnabled;
    public String eqPreset = EqualizerPresets.FLAT;
    /** {hz, dB} points of the user's custom curve, or null. */
    public double[][] customCurve;
    public boolean normalize;
    public float speed = 1f;

    public static boolean validSpeed(float speed) { return speed >= MIN_SPEED && speed <= MAX_SPEED && !Float.isNaN(speed); }

    /** Curve currently selected: the custom curve for CUSTOM, otherwise the preset's curve. */
    public double[][] activeCurve() {
        if (EqualizerPresets.CUSTOM.equals(eqPreset)) return customCurve != null ? customCurve : EqualizerPresets.curve(EqualizerPresets.FLAT);
        double[][] curve = EqualizerPresets.curve(eqPreset);
        return curve != null ? curve : EqualizerPresets.curve(EqualizerPresets.FLAT);
    }

    public AudioEffectSettings copy() {
        AudioEffectSettings copy = new AudioEffectSettings();
        copy.eqEnabled = eqEnabled; copy.eqPreset = eqPreset; copy.normalize = normalize; copy.speed = speed;
        if (customCurve != null) {
            copy.customCurve = new double[customCurve.length][];
            for (int i = 0; i < customCurve.length; i++) copy.customCurve[i] = customCurve[i].clone();
        }
        return copy;
    }

    public String encode() {
        StringBuilder text = new StringBuilder(VERSION);
        text.append(";eq=").append(eqEnabled ? 1 : 0);
        text.append(";preset=").append(eqPreset.replace(";", "").replace("=", ""));
        text.append(";norm=").append(normalize ? 1 : 0);
        text.append(";speed=").append(String.format(Locale.ROOT, "%.3f", speed));
        if (customCurve != null && customCurve.length > 0) {
            text.append(";custom=");
            for (int i = 0; i < customCurve.length; i++) {
                if (i > 0) text.append(',');
                text.append(String.format(Locale.ROOT, "%.1f:%.2f", customCurve[i][0], customCurve[i][1]));
            }
        }
        return text.toString();
    }

    /** Decodes a record; malformed or missing fields fall back to defaults. */
    public static AudioEffectSettings decode(String text) {
        AudioEffectSettings settings = new AudioEffectSettings();
        if (text == null || !text.startsWith(VERSION)) return settings;
        for (String field : text.split(";")) {
            int eq = field.indexOf('=');
            if (eq <= 0) continue;
            String key = field.substring(0, eq), value = field.substring(eq + 1);
            try {
                switch (key) {
                    case "eq": settings.eqEnabled = value.equals("1"); break;
                    case "preset": { String preset = EqualizerPresets.canonical(value); if (preset != null) settings.eqPreset = preset; break; }
                    case "norm": settings.normalize = value.equals("1"); break;
                    case "speed": { float speed = Float.parseFloat(value); if (validSpeed(speed)) settings.speed = speed; break; }
                    case "custom": settings.customCurve = parseCurve(value); break;
                    default: break;
                }
            } catch (RuntimeException malformed) { /* keep the default for this field */ }
        }
        if (EqualizerPresets.CUSTOM.equals(settings.eqPreset) && settings.customCurve == null) settings.eqPreset = EqualizerPresets.FLAT;
        return settings;
    }

    private static double[][] parseCurve(String value) {
        String[] parts = value.split(",");
        if (parts.length == 0 || parts.length > 32) return null;
        double[][] curve = new double[parts.length][];
        double previous = 0;
        for (int i = 0; i < parts.length; i++) {
            String[] pair = parts[i].split(":");
            if (pair.length != 2) return null;
            double hz = Double.parseDouble(pair[0]), db = Double.parseDouble(pair[1]);
            if (!(hz > previous) || hz > 48000 || !Double.isFinite(db) || Math.abs(db) > 30) return null;
            curve[i] = new double[]{hz, db};
            previous = hz;
        }
        return curve;
    }
}
