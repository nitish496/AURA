package dev.lilt.player.app.chrome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassButton;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * The glass top bar of a screen: back / action buttons that always float, plus a glass strip with
 * the small title that fades in once the large title has scrolled away ({@code setProgress}).
 * Put it above the scrolling content in the same FrameLayout and pass that content as the backdrop.
 */
public final class TopBar extends FrameLayout {
    private final ColorRoles roles;
    private final GlassView bar;
    private final TextView title;
    private final View backdrop;
    private final List<GlassButton> left = new ArrayList<>(), right = new ArrayList<>();
    private int statusPx;
    private boolean onArtwork;

    public TopBar(Context c, ColorRoles roles, View backdrop) {
        super(c);
        this.roles = roles;
        this.backdrop = backdrop;
        setClipChildren(false);
        bar = new GlassView(c, roles);
        bar.setCornerRadius(0);
        bar.setRimVisible(false);
        bar.setBottomHairline(roles.separator);
        bar.setElevation(0);
        bar.setBlurAllowed(false);
        bar.setAlpha(0f);
        addView(bar);
        title = Type.text(c, TypeScale.HEADLINE, roles.textPrimary);
        Type.singleLine(title);
        title.setGravity(Gravity.CENTER);
        title.setAlpha(0f);
        addView(title);
    }

    public int barHeight() { return statusPx + Ui.dp(getContext(), DesignTokens.TOP_BAR); }

    public GlassButton addLeft(int icon, String description, Runnable action) {
        GlassButton b = button(icon, description, action);
        left.add(b);
        return b;
    }

    public GlassButton addRight(int icon, String description, Runnable action) {
        GlassButton b = button(icon, description, action);
        right.add(b);
        return b;
    }

    private GlassButton button(int icon, String description, Runnable action) {
        GlassButton b = new GlassButton(getContext(), roles, icon, description, DesignTokens.GLASS_BUTTON);
        b.setBlurAllowed(false); // tiny glass: dense tint + rim (blur path drew nothing over the list)
        b.setOnArtwork(onArtwork);
        b.setOnClickListener(v -> action.run());
        addView(b, new LayoutParams(b.size(), b.size()));
        return b;
    }

    public void setTitle(CharSequence t) { title.setText(t); }

    /** Buttons over artwork (album / artist heroes) use the lighter on-artwork glass. */
    public void setOnArtwork(boolean on) {
        onArtwork = on;
        for (GlassButton b : left) b.setOnArtwork(on);
        for (GlassButton b : right) b.setOnArtwork(on);
    }

    /** Status-bar height in px; the bar covers status + 52dp. */
    public void setInsetTop(int px) {
        if (px == statusPx) return;
        statusPx = px;
        requestLayout();
    }

    /** 0 = only the buttons, 1 = glass strip and small title. */
    public void setProgress(float p) {
        float v = p < 0 ? 0 : Math.min(1, p);
        if (bar.getAlpha() == v) return;
        bar.setAlpha(v);
        title.setAlpha(v);
        bar.setVisibility(v <= 0.001f ? INVISIBLE : VISIBLE);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = barHeight();
        setMeasuredDimension(w, h);
        int exact = MeasureSpec.EXACTLY;
        bar.measure(MeasureSpec.makeMeasureSpec(w, exact), MeasureSpec.makeMeasureSpec(h, exact));
        int btn = left.isEmpty() && right.isEmpty() ? 0 : (left.isEmpty() ? right : left).get(0).size();
        int titleW = Math.max(0, w - 2 * (Ui.dp(getContext(), 16) + Math.max(left.size(), right.size()) * btn + Ui.dp(getContext(), 8)));
        title.measure(MeasureSpec.makeMeasureSpec(titleW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        for (GlassButton b : left) b.measure(MeasureSpec.makeMeasureSpec(b.size(), exact), MeasureSpec.makeMeasureSpec(b.size(), exact));
        for (GlassButton b : right) b.measure(MeasureSpec.makeMeasureSpec(b.size(), exact), MeasureSpec.makeMeasureSpec(b.size(), exact));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t, inset = Ui.dp(getContext(), 12);
        bar.layout(0, 0, w, h);
        int bandTop = statusPx, band = h - statusPx;
        int tw = title.getMeasuredWidth(), th = title.getMeasuredHeight();
        title.layout((w - tw) / 2, bandTop + (band - th) / 2, (w + tw) / 2, bandTop + (band + th) / 2);
        int x = inset;
        for (GlassButton btn : left) { btn.layout(x, bandTop + (band - btn.size()) / 2, x + btn.size(), bandTop + (band + btn.size()) / 2); x += btn.size(); }
        x = w - inset;
        for (GlassButton btn : right) { btn.layout(x - btn.size(), bandTop + (band - btn.size()) / 2, x, bandTop + (band + btn.size()) / 2); x -= btn.size(); }
    }

    @Override public boolean shouldDelayChildPressedState() { return false; }

    /** Matches the parent's width; height is the bar height. */
    public static FrameLayout.LayoutParams params() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
    }
}
