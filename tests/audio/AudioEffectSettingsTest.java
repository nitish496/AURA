package dev.lilt.player.audio;

/** Persistence of the sound settings record (equaliser, normalisation, speed). */
public final class AudioEffectSettingsTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }

    public static void main(String[] args) {
        AudioEffectSettings defaults = AudioEffectSettings.decode(null);
        check(!defaults.eqEnabled && defaults.eqPreset.equals("Flat") && !defaults.normalize && defaults.speed == 1f && defaults.customCurve == null, "defaults");
        check(AudioEffectSettings.decode("garbage").eqPreset.equals("Flat"), "unknown record");

        AudioEffectSettings s = new AudioEffectSettings();
        s.eqEnabled = true; s.eqPreset = EqualizerPresets.CUSTOM; s.normalize = true; s.speed = 1.25f;
        s.customCurve = new double[][]{{60, 3}, {230, -2}, {910, 0}, {3600, 1}, {14000, 5}};
        AudioEffectSettings back = AudioEffectSettings.decode(s.encode());
        check(back.eqEnabled && back.normalize && back.speed == 1.25f && back.eqPreset.equals("Custom"), "round trip flags");
        check(back.customCurve.length == 5 && back.customCurve[1][0] == 230 && back.customCurve[1][1] == -2, "round trip curve");
        check(back.activeCurve() == back.customCurve, "custom curve active");

        AudioEffectSettings preset = new AudioEffectSettings();
        preset.eqPreset = "Late Night";
        AudioEffectSettings presetBack = AudioEffectSettings.decode(preset.encode());
        check(presetBack.eqPreset.equals("Late Night") && presetBack.activeCurve()[0][1] == EqualizerPresets.curve("Late Night")[0][1], "preset curve active");

        check(AudioEffectSettings.decode("v1;speed=9;preset=Nope;eq=1").speed == 1f, "out-of-range speed ignored");
        check(AudioEffectSettings.decode("v1;speed=9;preset=Nope;eq=1").eqPreset.equals("Flat"), "unknown preset ignored");
        check(AudioEffectSettings.decode("v1;preset=Custom").eqPreset.equals("Flat"), "custom without curve falls back");
        check(AudioEffectSettings.decode("v1;preset=Custom;custom=100:1,50:2").customCurve == null, "unsorted curve rejected");
        check(AudioEffectSettings.decode("v1;eq=1;custom=x").eqEnabled, "a bad field keeps the others");

        AudioEffectSettings copy = back.copy();
        copy.customCurve[0][1] = 9;
        check(back.customCurve[0][1] == 3, "copy is deep");
        check(AudioEffectSettings.validSpeed(0.5f) && AudioEffectSettings.validSpeed(2f), "speed bounds inclusive");
        check(!AudioEffectSettings.validSpeed(0.49f) && !AudioEffectSettings.validSpeed(2.01f) && !AudioEffectSettings.validSpeed(Float.NaN), "speed outside range");
        System.out.println("AudioEffectSettingsTest: " + checks + " checks passed");
    }
}
