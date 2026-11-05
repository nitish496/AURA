package dev.lilt.player.ui;

import android.app.Dialog;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

/**
 * Glass for content that lives in a separate window (system dialogs, permission rationale):
 * uses the platform's cross-window blur on API 31+ when the device allows it
 * ({@link WindowManager#isCrossWindowBlurEnabled}), otherwise a dim behind and the dense
 * fallback tint. In-window overlays ({@link BottomSheet}) should use {@link GlassView} instead.
 */
public final class WindowBlur {
    private WindowBlur() {}

    /** Call after {@code dialog.create()} / before {@code show()}. */
    public static void apply(Dialog dialog, ColorRoles roles, float cornerRadiusPx) {
        Window w = dialog.getWindow();
        if (w == null) return;
        boolean blur = Build.VERSION.SDK_INT >= 31 && w.getWindowManager().isCrossWindowBlurEnabled()
                && Theme.glassBlurEnabled(dialog.getContext());
        GlassDrawable bg = GlassDrawable.chrome(roles, blur);
        bg.setCornerRadius(cornerRadiusPx);
        w.setBackgroundDrawable(bg);
        if (blur) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams a = w.getAttributes();
            a.setBlurBehindRadius(Ui.dp(dialog.getContext(), 24));
            a.dimAmount = 0.2f;
            w.setAttributes(a);
            w.setBackgroundBlurRadius(Ui.dp(dialog.getContext(), 40));
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
    }
}
