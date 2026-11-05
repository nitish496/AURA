package dev.lilt.player.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Small helpers for building programmatic views with the design system: dp conversion,
 * shapes, ripples, outlines and layout params. Everything else in {@code ui} builds on these.
 */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float dp) { return Math.round(dp * c.getResources().getDisplayMetrics().density); }
    public static float dpf(Context c, float dp) { return dp * c.getResources().getDisplayMetrics().density; }
    /** px → dp (rounded). */
    public static int toDp(Context c, int px) { return Math.round(px / c.getResources().getDisplayMetrics().density); }

    /** Filled rounded rectangle; {@code radiusPx < 0} makes a capsule. */
    public static GradientDrawable roundRect(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusPx < 0 ? 10_000f : radiusPx);
        return d;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** Ripple colour for the theme: a soft wash of the text colour. */
    public static int rippleColor(ColorRoles roles) { return roles.dark ? 0x29FFFFFF : 0x1F000000; }

    /** A pressable background: {@code fill} (may be transparent) with a ripple clipped to the same shape. */
    public static Drawable pressable(ColorRoles roles, int fill, float radiusPx) {
        GradientDrawable shape = roundRect(fill, radiusPx);
        GradientDrawable mask = roundRect(0xFFFFFFFF, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(roles)), shape, mask);
    }

    /** A pressable background inset vertically (e.g. a 32dp chip inside a 48dp touch target). */
    public static Drawable pressableInset(ColorRoles roles, int fill, float radiusPx, int insetTopBottomPx) {
        Drawable content = new InsetDrawable(roundRect(fill, radiusPx), 0, insetTopBottomPx, 0, insetTopBottomPx);
        Drawable mask = new InsetDrawable(roundRect(0xFFFFFFFF, radiusPx), 0, insetTopBottomPx, 0, insetTopBottomPx);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(roles)), content, mask);
    }

    /** Full-bleed rectangular ripple for list rows (no fill). */
    public static Drawable rowRipple(ColorRoles roles) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(roles)), null, new android.graphics.drawable.ColorDrawable(0xFFFFFFFF));
    }

    /** Unbounded circular ripple for icon buttons. */
    public static Drawable borderlessRipple(ColorRoles roles, int radiusPx) {
        RippleDrawable r = new RippleDrawable(ColorStateList.valueOf(rippleColor(roles)), null, null);
        if (radiusPx > 0) r.setRadius(radiusPx);
        return r;
    }

    /** Clips {@code v} (and its shadow) to a rounded rectangle; {@code radiusPx < 0} = capsule. */
    public static void roundOutline(View v, float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                float r = radiusPx < 0 ? Math.min(view.getWidth(), view.getHeight()) / 2f : radiusPx;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** Clips {@code v} to a circle (artist pictures). */
    public static void ovalOutline(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        v.setClipToOutline(true);
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    public static LinearLayout.LayoutParams weight(float weight) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight); }
    public static FrameLayout.LayoutParams frame(int w, int h, int gravity) { return new FrameLayout.LayoutParams(w, h, gravity); }

    /** A fixed-size spacer (dp) for LinearLayouts. */
    public static View space(Context c, int widthDp, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.max(1, dp(c, widthDp)), Math.max(1, dp(c, heightDp))));
        return v;
    }

    /** Applies margins (dp) to an existing MarginLayoutParams. */
    public static <T extends ViewGroup.MarginLayoutParams> T margins(Context c, T params, int l, int t, int r, int b) {
        params.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return params;
    }
}
