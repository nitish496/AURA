package dev.lilt.player;
import android.content.*;import android.content.pm.*;
/** Detect only an accessible Dolby control, never infer branded support from generic spatial audio. */
public final class DolbySettings {
 private DolbySettings(){}
 public static Intent find(Context c){Intent direct=new Intent("android.settings.DOLBY_ATMOS_SETTINGS");if(direct.resolveActivity(c.getPackageManager())!=null)return direct;
 Intent launchers=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
 for(ResolveInfo r:c.getPackageManager().queryIntentActivities(launchers,0)){String name=(r.activityInfo.packageName+" "+r.loadLabel(c.getPackageManager())).toLowerCase(java.util.Locale.ROOT);if(name.contains("dolby")&&r.activityInfo.exported)return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name));}String maker=android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);if(maker.contains("xiaomi")||maker.contains("redmi")){Intent sound=new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS);if(sound.resolveActivity(c.getPackageManager())!=null)return sound;}return null;}
}
