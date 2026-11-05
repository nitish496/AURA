package dev.lilt.player.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Edge-to-edge windows (forced by targetSdk 35 on Android 15; applied the same way on 8–14).
 * Content draws behind the status and navigation bars; the glass chrome and scroll padding
 * use the reported insets.
 *
 * <pre>{@code
 * EdgeToEdge.apply(activity, Theme.isDark(activity));
 * EdgeToEdge.onInsets(root, (l, t, r, b) -> { topBar.setInsetTop(t); bottomChrome.setInsetBottom(b); });
 * EdgeToEdge.padding(listView, EdgeToEdge.TOP | EdgeToEdge.BOTTOM);
 * }</pre>
 */
public final class EdgeToEdge {
    private EdgeToEdge() {}

    public static final int LEFT = 1, TOP = 2, RIGHT = 4, BOTTOM = 8, ALL = 15;

    /** Receives system-bar + display-cutout insets in px. */
    public interface Listener { void onInsets(int left, int top, int right, int bottom); }

    /** Makes the window edge to edge with transparent bars; {@code darkUi} picks light bar icons. */
    @SuppressWarnings("deprecation")
    public static void apply(Activity activity, boolean darkUi) {
        Window w = activity.getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attrs = w.getAttributes();
            attrs.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(attrs);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            View decor = w.getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        setBarIcons(w, !darkUi);
    }

    /** Dark icons on light content ({@code lightBackground = true}) or light icons on dark content. */
    @SuppressWarnings("deprecation")
    public static void setBarIcons(Window w, boolean lightBackground) {
        if (Build.VERSION.SDK_INT >= 30) {
            w.getDecorView(); // installs the decor so the controller exists before setContentView
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(lightBackground ? mask : 0, mask);
        } else {
            View decor = w.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(lightBackground ? flags | light : flags & ~light);
        }
    }

    /**
     * Reports insets to {@code listener} whenever they change, without consuming them (children
     * still receive them). Call {@code view.requestApplyInsets()} if the view is already attached.
     */
    public static void onInsets(View view, Listener listener) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] i = read(insets);
            listener.onInsets(i[0], i[1], i[2], i[3]);
            return insets;
        });
        if (view.isAttachedToWindow()) view.requestApplyInsets();
    }

    /** Adds the selected insets to {@code view}'s current padding (keeping the original padding). */
    public static void padding(View view, int sides) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        onInsets(view, (il, it, ir, ib) -> view.setPadding(l + ((sides & LEFT) != 0 ? il : 0), t + ((sides & TOP) != 0 ? it : 0),
                r + ((sides & RIGHT) != 0 ? ir : 0), b + ((sides & BOTTOM) != 0 ? ib : 0)));
    }

    /** {left, top, right, bottom} system-bar + cutout insets in px. */
    @SuppressWarnings("deprecation")
    public static int[] read(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[] {s.left, s.top, s.right, s.bottom};
        }
        return new int[] {insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()};
    }
}
