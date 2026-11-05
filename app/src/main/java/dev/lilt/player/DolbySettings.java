package dev.lilt.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.Locale;

/**
 * Finds an accessible Dolby control on this phone. Never infers branded support from generic
 * spatial audio. Cheap checks first (the system Dolby action, then known Dolby apps); the last
 * resort scans launcher packages by name only, without loading app labels (which was slow).
 * Still involves package-manager calls, so call it off the main thread.
 */
public final class DolbySettings {
    private static final String ACTION = "android.settings.DOLBY_ATMOS_SETTINGS";
    /** Dolby control apps shipped by phone makers (package names are matched exactly). */
    private static final String[] KNOWN_PACKAGES = {
            "com.dolby.daxappui", "com.dolby.daxappUI", "com.dolby.dax2appUI", "com.dolby.ds1appUI",
            "com.motorola.dolby.dolbyui", "com.lenovo.dolby"};

    private DolbySettings() {}

    public static Intent find(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            Intent direct = new Intent(ACTION);
            if (direct.resolveActivity(pm) != null) return direct;
            for (String name : KNOWN_PACKAGES) {
                Intent launch = pm.getLaunchIntentForPackage(name);
                if (launch != null) return launch;
            }
            Intent launchers = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo r : pm.queryIntentActivities(launchers, 0)) {
                if (r.activityInfo == null || !r.activityInfo.exported) continue;
                if (r.activityInfo.packageName.toLowerCase(Locale.ROOT).contains("dolby"))
                    return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
            }
        } catch (RuntimeException unavailable) {
            // Package manager died or a vendor app is mid-update: fall through.
        }
        String maker = android.os.Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        if (maker.contains("xiaomi") || maker.contains("redmi")) {
            Intent sound = new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS);
            if (sound.resolveActivity(pm) != null) return sound;
        }
        return null;
    }
}
