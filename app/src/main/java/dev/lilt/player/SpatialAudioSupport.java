package dev.lilt.player;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Spatializer;
import android.os.Build;
import android.provider.Settings;

/** Device capability information, never a claim that the current song is spatialized. */
public final class SpatialAudioSupport {
    public enum State { UNSUPPORTED, DISABLED, OUTPUT_UNAVAILABLE, AVAILABLE, UNKNOWN }

    private SpatialAudioSupport() {}

    public static State inspect(Context context) {
        if (Build.VERSION.SDK_INT < 33) return State.UNSUPPORTED;
        try {
            return Api33.inspect(context);
        } catch (RuntimeException unavailable) {
            // A vendor audio service may be unavailable while its route is changing.
            return State.UNKNOWN;
        }
    }

    public static String description(Context context) {
        switch (inspect(context)) {
            case AVAILABLE: return "Ready for compatible audio. Android handles supported tracks automatically; ordinary stereo songs may stay stereo.";
            case DISABLED: return "Supported by your phone, but switched off in system sound settings.";
            case OUTPUT_UNAVAILABLE: return "Supported by your phone. Connect a compatible audio output to use it.";
            case UNSUPPORTED: return "Android spatial audio is not available here. Your phone may offer its own sound effects in system settings.";
            default: return "Spatial audio status could not be checked. Try system sound settings.";
        }
    }

    /** Android owns the global switch; apps cannot change it through the public API. */
    public static Intent settingsIntent(Context context) {
        Intent sound = new Intent(Settings.ACTION_SOUND_SETTINGS);
        return sound.resolveActivity(context.getPackageManager()) != null
                ? sound : new Intent(Settings.ACTION_SETTINGS);
    }

    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static State inspect(Context context) {
            AudioManager manager = context.getSystemService(AudioManager.class);
            if (manager == null) return State.UNKNOWN;
            Spatializer spatializer = manager.getSpatializer();
            if (spatializer.getImmersiveAudioLevel() == Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE)
                return State.UNSUPPORTED;
            if (!spatializer.isEnabled()) return State.DISABLED;
            return spatializer.isAvailable() ? State.AVAILABLE : State.OUTPUT_UNAVAILABLE;
        }
    }
}
