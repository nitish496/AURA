package dev.lilt.player.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.PowerManager;

/**
 * Resolves the active {@link ColorRoles} and material switches for a context.
 *
 * <p>Appearance follows the system (dark / light) unless the user picks one in Settings
 * ({@link #setMode}); recreate the activity after changing it. "Reduce transparency" swaps the
 * blurred glass for the denser fallback tint everywhere ({@link #glassBlurEnabled}); battery
 * saver does the same automatically.
 */
public final class Theme {
    private Theme() {}

    public static final int MODE_SYSTEM = 0, MODE_DARK = 1, MODE_LIGHT = 2;
    private static final String PREFS = "aura_ui";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int mode(Context c) { return prefs(c).getInt("theme_mode", MODE_SYSTEM); }
    public static void setMode(Context c, int mode) { prefs(c).edit().putInt("theme_mode", mode).apply(); }

    /** True when the dark roles apply. */
    public static boolean isDark(Context c) {
        int mode = mode(c);
        if (mode == MODE_DARK) return true;
        if (mode == MODE_LIGHT) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_NO; // undefined counts as dark: AURA is dark-first
    }

    /** Colour roles for {@code c}. */
    public static ColorRoles colors(Context c) { return ColorRoles.of(isDark(c)); }

    public static boolean reduceTransparency(Context c) { return prefs(c).getBoolean("reduce_transparency", false); }
    public static void setReduceTransparency(Context c, boolean on) { prefs(c).edit().putBoolean("reduce_transparency", on).apply(); }

    /** In-window backdrop blur for glass (API 31+, not in battery saver, not with reduce transparency). */
    public static boolean glassBlurEnabled(Context c) {
        if (Build.VERSION.SDK_INT < 31 || reduceTransparency(c)) return false;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || !pm.isPowerSaveMode();
    }
}
