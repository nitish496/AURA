package dev.lilt.player.audio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Bundle;
import android.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Equaliser and loudness-normalisation boost for each player audio session (the main player and
 * the crossfade spare). Settings persist in the "audio_effects" preferences. Many phones throw from
 * audiofx (missing effect libraries, too many instances, a vendor sound mod holding the session),
 * so every audiofx call is guarded and a failure only marks the equaliser unavailable.
 *
 * <p>Main thread only. Bundle keys are documented in docs/ARCHITECTURE.md (Audio).
 */
public final class AudioEffects {
    public static final String KEY_EQ_ENABLED = "eqEnabled", KEY_EQ_PRESET = "eqPreset", KEY_EQ_BANDS = "eqBands",
            KEY_NORMALIZE = "normalize", KEY_SPEED = "speed", KEY_EQ_AVAILABLE = "eqAvailable", KEY_EQ_PRESETS = "eqPresets",
            KEY_EQ_FREQUENCIES = "eqBandFrequencies", KEY_EQ_RANGE = "eqLevelRange", KEY_SPEED_RANGE = "speedRange";
    /** Results of {@link #update(Bundle)}; mapped to SessionResult/SessionError codes by the service. */
    public static final int UPDATED = 0, BAD_VALUE = 1, NOT_SUPPORTED = 2;
    private static final String TAG = "AuraEffects";

    /** The device equaliser's layout. */
    public static final class Spec {
        public final int[] centreHz;
        public final short minMb, maxMb;
        Spec(int[] centreHz, short minMb, short maxMb) { this.centreHz = centreHz; this.minMb = minMb; this.maxMb = maxMb; }
    }

    private static final class Chain {
        Equalizer equalizer;
        LoudnessEnhancer enhancer;
        int boostMb;
        boolean equalizerFailed, enhancerFailed;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Map<Integer, Chain> chains = new HashMap<>();
    private AudioEffectSettings settings;
    private Spec spec;
    private boolean released;

    public AudioEffects(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("audio_effects", Context.MODE_PRIVATE);
        settings = AudioEffectSettings.decode(prefs.getString("settings", null));
        spec = decodeSpec(prefs.getString("spec", null));
    }

    public AudioEffectSettings settings() { return settings.copy(); }

    /** Binds effects to an audio session (idempotent). */
    public void attach(int sessionId) {
        if (released || sessionId <= 0 || chains.containsKey(sessionId)) return;
        chains.put(sessionId, new Chain());
        broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, sessionId);
        applyEqualizer(sessionId);
    }

    public void detach(int sessionId) {
        Chain chain = chains.remove(sessionId);
        if (chain == null) return;
        releaseChain(chain);
        broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, sessionId);
    }

    /** Normalisation boost for the song now playing on a session; 0 disables the enhancer. */
    public void setBoost(int sessionId, int millibels) {
        Chain chain = chains.get(sessionId);
        if (chain == null || released) return;
        int target = Math.max(0, millibels);
        if (target == chain.boostMb && (target == 0 || chain.enhancer != null)) return;
        chain.boostMb = target;
        if (target == 0) {
            if (chain.enhancer != null) try { chain.enhancer.setEnabled(false); } catch (RuntimeException e) { Log.w(TAG, "Enhancer disable failed"); }
            return;
        }
        if (chain.enhancer == null && !chain.enhancerFailed) {
            try { chain.enhancer = new LoudnessEnhancer(sessionId); }
            catch (RuntimeException e) { chain.enhancerFailed = true; Log.w(TAG, "LoudnessEnhancer unavailable"); return; }
        }
        if (chain.enhancer == null) return;
        try { chain.enhancer.setTargetGain(target); chain.enhancer.setEnabled(true); }
        catch (RuntimeException e) { Log.w(TAG, "Enhancer gain failed"); }
    }

    /**
     * Applies a SET_AUDIO_EFFECTS bundle. Absent keys keep their value. Speed and normalisation
     * are stored here and applied to the players by the service.
     */
    public int update(Bundle args) {
        if (args == null) return BAD_VALUE;
        AudioEffectSettings next = settings.copy();
        if (args.containsKey(KEY_EQ_ENABLED)) next.eqEnabled = args.getBoolean(KEY_EQ_ENABLED);
        if (args.containsKey(KEY_EQ_PRESET)) {
            String preset = EqualizerPresets.canonical(args.getString(KEY_EQ_PRESET));
            if (preset == null) return BAD_VALUE;
            next.eqPreset = preset;
        }
        if (args.containsKey(KEY_EQ_BANDS)) {
            short[] levels = args.getShortArray(KEY_EQ_BANDS);
            Spec layout = ensureSpec();
            if (layout == null) return NOT_SUPPORTED;
            if (levels == null || levels.length != layout.centreHz.length) return BAD_VALUE;
            short[] clamped = new short[levels.length];
            for (int i = 0; i < levels.length; i++) clamped[i] = EqualizerPresets.clampMb(levels[i], layout.minMb, layout.maxMb);
            next.customCurve = EqualizerPresets.curveFromLevels(clamped, layout.centreHz);
            next.eqPreset = EqualizerPresets.CUSTOM;
        }
        if (args.containsKey(KEY_NORMALIZE)) next.normalize = args.getBoolean(KEY_NORMALIZE);
        if (args.containsKey(KEY_SPEED)) {
            float speed = args.getFloat(KEY_SPEED, Float.NaN);
            if (!AudioEffectSettings.validSpeed(speed)) return BAD_VALUE;
            next.speed = Math.round(speed * 100) / 100f;
        }
        settings = next;
        prefs.edit().putString("settings", settings.encode()).apply();
        for (Integer session : chains.keySet()) applyEqualizer(session);
        return UPDATED;
    }

    /** State for GET_AUDIO_EFFECTS (and the SET result). */
    public Bundle describe() {
        Bundle out = new Bundle();
        Spec layout = ensureSpec();
        out.putBoolean(KEY_EQ_ENABLED, settings.eqEnabled);
        out.putString(KEY_EQ_PRESET, settings.eqPreset);
        out.putBoolean(KEY_EQ_AVAILABLE, layout != null);
        List<String> names = EqualizerPresets.names();
        out.putStringArray(KEY_EQ_PRESETS, names.toArray(new String[0]));
        if (layout != null) {
            out.putShortArray(KEY_EQ_BANDS, EqualizerPresets.levels(settings.activeCurve(), layout.centreHz, layout.minMb, layout.maxMb));
            out.putIntArray(KEY_EQ_FREQUENCIES, layout.centreHz.clone());
            out.putShortArray(KEY_EQ_RANGE, new short[]{layout.minMb, layout.maxMb});
        } else {
            out.putShortArray(KEY_EQ_BANDS, new short[0]);
            out.putIntArray(KEY_EQ_FREQUENCIES, new int[0]);
            out.putShortArray(KEY_EQ_RANGE, new short[0]);
        }
        out.putBoolean(KEY_NORMALIZE, settings.normalize);
        out.putFloat(KEY_SPEED, settings.speed);
        out.putFloatArray(KEY_SPEED_RANGE, new float[]{AudioEffectSettings.MIN_SPEED, AudioEffectSettings.MAX_SPEED});
        return out;
    }

    public void release() {
        released = true;
        for (Map.Entry<Integer, Chain> entry : chains.entrySet()) {
            releaseChain(entry.getValue());
            broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, entry.getKey());
        }
        chains.clear();
    }

    private void applyEqualizer(int sessionId) {
        Chain chain = chains.get(sessionId);
        if (chain == null || released) return;
        boolean wanted = settings.eqEnabled;
        // Create the equaliser when it is needed, or once to learn the device's band layout.
        if (chain.equalizer == null && !chain.equalizerFailed && (wanted || spec == null)) {
            try {
                chain.equalizer = new Equalizer(0, sessionId);
                Spec found = readSpec(chain.equalizer);
                if (found != null && !sameSpec(found, spec)) { spec = found; prefs.edit().putString("spec", encodeSpec(found)).apply(); }
            } catch (RuntimeException unavailable) {
                chain.equalizerFailed = true;
                chain.equalizer = null;
                Log.w(TAG, "Equalizer unavailable on this device");
            }
        }
        Equalizer eq = chain.equalizer;
        if (eq == null) return;
        try {
            if (wanted && spec != null) {
                short[] levels = EqualizerPresets.levels(settings.activeCurve(), spec.centreHz, spec.minMb, spec.maxMb);
                int bands = Math.min(levels.length, eq.getNumberOfBands());
                for (short b = 0; b < bands; b++) eq.setBandLevel(b, levels[b]);
            }
            eq.setEnabled(wanted);
        } catch (RuntimeException failed) {
            Log.w(TAG, "Equalizer update failed");
        }
    }

    private Spec ensureSpec() {
        if (spec == null) for (Integer session : chains.keySet()) { applyEqualizer(session); if (spec != null) break; }
        return spec;
    }

    private static Spec readSpec(Equalizer eq) {
        short bands = eq.getNumberOfBands();
        short[] range = eq.getBandLevelRange();
        if (bands <= 0 || bands > 32 || range == null || range.length < 2) return null;
        int[] centres = new int[bands];
        for (short b = 0; b < bands; b++) centres[b] = Math.max(1, eq.getCenterFreq(b) / 1000);
        return new Spec(centres, range[0], range[1]);
    }

    private static boolean sameSpec(Spec a, Spec b) {
        return b != null && a.minMb == b.minMb && a.maxMb == b.maxMb && java.util.Arrays.equals(a.centreHz, b.centreHz);
    }

    private static String encodeSpec(Spec s) {
        StringBuilder text = new StringBuilder().append(s.minMb).append(';').append(s.maxMb).append(';');
        for (int i = 0; i < s.centreHz.length; i++) { if (i > 0) text.append(','); text.append(s.centreHz[i]); }
        return text.toString();
    }

    private static Spec decodeSpec(String text) {
        if (text == null) return null;
        try {
            String[] f = text.split(";");
            String[] hz = f[2].split(",");
            int[] centres = new int[hz.length];
            for (int i = 0; i < hz.length; i++) centres[i] = Integer.parseInt(hz[i]);
            return centres.length == 0 ? null : new Spec(centres, Short.parseShort(f[0]), Short.parseShort(f[1]));
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private void releaseChain(Chain chain) {
        if (chain.equalizer != null) try { chain.equalizer.release(); } catch (RuntimeException ignored) {}
        if (chain.enhancer != null) try { chain.enhancer.release(); } catch (RuntimeException ignored) {}
        chain.equalizer = null; chain.enhancer = null;
    }

    private void broadcast(String action, int sessionId) {
        try {
            Intent intent = new Intent(action)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName())
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
            context.sendBroadcast(intent);
        } catch (RuntimeException ignored) { /* optional integration with system sound apps */ }
    }
}
