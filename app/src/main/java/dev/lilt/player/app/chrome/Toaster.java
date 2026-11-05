package dev.lilt.player.app.chrome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/** A short glass message above the floating chrome ("Added to Queue"). One at a time; fades after ~2.4 s. */
public final class Toaster extends GlassView {
    private final TextView text;
    private final Runnable hide = this::fade;

    public Toaster(Context c, ColorRoles roles) {
        super(c, roles);
        text = Type.text(c, TypeScale.SUBHEAD.weight(500), roles.textPrimary);
        text.setMaxLines(2);
        text.setGravity(Gravity.CENTER);
        text.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(text);
        int h = Ui.dp(c, 20), v = Ui.dp(c, 12);
        setPadding(h, v, h, v);
        setVisibility(GONE);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Shows {@code message} centred, with its bottom {@code bottomMarginPx} above the parent's bottom. */
    public void show(String message, int bottomMarginPx) {
        removeCallbacks(hide);
        animate().cancel();
        text.setText(message);
        announceForAccessibility(message);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.bottomMargin = bottomMarginPx;
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        setLayoutParams(lp);
        if (getVisibility() != VISIBLE) { setAlpha(0f); setTranslationY(Ui.dpf(getContext(), 8)); setVisibility(VISIBLE); }
        animate().alpha(1f).translationY(0).setDuration(Motion.BASE).setInterpolator(Motion.decelerate()).start();
        postDelayed(hide, 2400);
    }

    private void fade() {
        animate().alpha(0f).setDuration(Motion.FAST).withEndAction(() -> setVisibility(GONE)).start();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(hide);
        super.onDetachedFromWindow();
    }

    public static FrameLayout.LayoutParams params(Context c) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.leftMargin = lp.rightMargin = Ui.dp(c, 16);
        return lp;
    }
}
