package dev.lilt.player.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import dev.lilt.player.DesignTokens;

/**
 * Circular glass button for top bars and player controls: a {@code visualDp} glass circle
 * centred in a touch target of at least 48dp. Add it with {@code size() x size()} params.
 *
 * <pre>{@code
 * GlassButton back = new GlassButton(ctx, R.drawable.ic_back, "Back", DesignTokens.GLASS_BUTTON);
 * back.setBackdrop(contentLayer);
 * topBar.addView(back, new FrameLayout.LayoutParams(back.size(), back.size()));
 * }</pre>
 */
public class GlassButton extends GlassView {
    private final ImageView icon;
    private final int visualPx, touchPx;
    private int iconColor;
    private boolean active;

    public GlassButton(Context c, int iconRes, String contentDescription, int visualDp) {
        this(c, Theme.colors(c), iconRes, contentDescription, visualDp);
    }

    public GlassButton(Context c, ColorRoles roles, int iconRes, String contentDescription, int visualDp) {
        super(c, roles);
        visualPx = Ui.dp(c, visualDp);
        touchPx = Math.max(visualPx, Ui.dp(c, DesignTokens.TOUCH_TARGET));
        int inset = (touchPx - visualPx) / 2;
        setShapeInset(inset);
        setCornerRadius(GlassDrawable.CAPSULE);
        iconColor = roles.textPrimary;
        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconPx = Ui.dp(c, visualDp >= 44 ? DesignTokens.ICON : 22);
        addView(icon, new LayoutParams(iconPx, iconPx, Gravity.CENTER));
        setIcon(iconRes);
        setContentDescription(contentDescription);
        setClickable(true);
        setFocusable(true);
        setForeground(new RippleDrawable(ColorStateList.valueOf(Ui.rippleColor(roles)), null,
                new InsetDrawable(Ui.oval(0xFFFFFFFF), inset)));
        setMinimumWidth(touchPx);
        setMinimumHeight(touchPx);
    }

    /** Touch-target size in px (use for both layout dimensions). */
    public int size() { return touchPx; }

    public void setIcon(int res) { icon.setImageDrawable(Icons.get(getContext(), res, active ? 0xFF151517 : iconColor)); icon.setTag(res); }

    public void setIconColor(int color) { iconColor = color; refreshIcon(); }

    /** Selected state (e.g. repeat on): white lens with a dark icon. */
    public void setActive(boolean on) {
        if (active == on) return;
        active = on;
        if (on) setTintColor(0xFFF5F5F7); else clearTintColor();
        setSelected(on);
        refreshIcon();
    }

    public boolean isActive() { return active; }

    private void refreshIcon() { Object res = icon.getTag(); if (res instanceof Integer) setIcon((Integer) res); }

    @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }
}
