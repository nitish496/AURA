package dev.lilt.player.screens.player;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.MediaRouter2;
import android.os.Build;
import android.provider.Settings;

/**
 * Opens the system's media output switcher (speaker, headphones, Bluetooth, cast) for AURA:
 * the public {@link MediaRouter2} switcher on Android 14+, the Settings media-output panel with
 * AURA's package on older releases, and Bluetooth settings as the last resort.
 */
public final class OutputSwitcher {
    private OutputSwitcher() {}

    private static final String[] PANEL_ACTIONS = {
        "com.android.settings.panel.action.MEDIA_OUTPUT",
        "android.settings.panel.action.MEDIA_OUTPUT",
    };
    private static final String EXTRA_PACKAGE = "com.android.settings.panel.extra.PACKAGE_NAME";

    /** True when something opened. */
    public static boolean open(Activity activity) {
        if (activity == null) return false;
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                if (MediaRouter2.getInstance(activity).showSystemOutputSwitcher()) return true;
            } catch (RuntimeException unavailable) { /* fall through to the panels */ }
        }
        for (String action : PANEL_ACTIONS) {
            Intent i = new Intent(action)
                    .putExtra(EXTRA_PACKAGE, activity.getPackageName())
                    .putExtra("android.provider.extra.PACKAGE_NAME", activity.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (start(activity, i)) return true;
        }
        return start(activity, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static boolean start(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException missing) {
            return false;
        }
    }
}
