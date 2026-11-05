package dev.lilt.player.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import dev.lilt.player.DesignTokens;

/**
 * Liquid Glass container for the floating navigation / control layer — tab bar, mini player,
 * top bar, glass buttons, menus and sheets. Never use it for content (cards, rows).
 *
 * <p><b>API 31+</b>: on every pre-draw the {@linkplain #setBackdrop backdrop} view's display list
 * is recorded into a private {@link RenderNode} (offset to this view's position, padded by the
 * blur radius), blurred with {@link RenderEffect#createBlurEffect} and saturation-boosted, then
 * drawn clipped to the glass shape, followed by the tint and the specular rim. Recording only
 * references the content's existing display lists, so it is cheap; nothing is allocated per
 * frame and nothing is invalidated from pre-draw (no redraw loop) — the glass refreshes exactly
 * when the content behind it redraws.
 *
 * <p><b>API 26–30</b>, battery saver, "reduce transparency" or a missing backdrop: the same
 * shape with a denser tint ({@link ColorRoles#glassTintFallback}), rim and shadow, no blur.
 *
 * <p>Layer the screen so the backdrop is a <em>sibling</em> of the chrome, never an ancestor:
 * <pre>{@code
 * FrameLayout root = new FrameLayout(ctx);
 * FrameLayout content = new FrameLayout(ctx);   // scrolling screens live here
 * root.addView(content, MATCH_PARENT, MATCH_PARENT);
 * BottomChrome chrome = new BottomChrome(ctx);  // GlassViews inside
 * chrome.setBackdrop(content);
 * root.addView(chrome, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM));
 * }</pre>
 * If the backdrop contains this view, blur is disabled (it would capture itself).
 */
public class GlassView extends FrameLayout {
    /** True while any GlassView records a backdrop, so glass never draws into a backdrop. */
    private static boolean capturing;

    private ColorRoles roles;
    private final GlassDrawable material;
    private View backdrop;
    private Engine engine;
    private boolean onArtwork, blurAllowed = true, listening, customTint;
    private int customTintColor;
    private float blurRadiusPx;

    private final ViewTreeObserver.OnPreDrawListener preDraw = new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
            if (engine != null && blurActive() && getVisibility() == VISIBLE && getAlpha() > 0f) engine.record(backdrop, GlassView.this, backdropBase());
            return true;
        }
    };

    public GlassView(Context context) { this(context, Theme.colors(context)); }

    public GlassView(Context context, ColorRoles roles) {
        super(context);
        this.roles = roles;
        setWillNotDraw(false);
        blurRadiusPx = Ui.dpf(context, DesignTokens.GLASS_BLUR);
        material = GlassDrawable.chrome(roles, false);
        material.setCallback(this);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { material.getOutline(outline); }
        });
        setClipToOutline(true);
        setElevation(Ui.dpf(context, DesignTokens.ELEVATION_GLASS));
        applyShadowColor();
        refreshMaterial();
    }

    /** The content to blur (a sibling layer behind the chrome). {@code null} = tint only. */
    public void setBackdrop(View source) {
        if (backdrop == source) return;
        stopListening();
        backdrop = source;
        if (isAttachedToWindow()) startListening();
        refreshMaterial();
    }

    public View getBackdrop() { return backdrop; }

    /** Corner radius in px or {@link GlassDrawable#CAPSULE} (default). */
    public void setCornerRadius(float px) { material.setCornerRadius(px); invalidateOutline(); invalidate(); }

    /** Draws the glass shape inset from the view bounds (keeps a 48dp touch target around a 40dp circle). */
    public void setShapeInset(int px) { material.setShapeInset(px); invalidateOutline(); invalidate(); }

    /** Glass for controls placed on artwork tints (lighter tint, brighter rim, softer shadow). */
    public void setOnArtwork(boolean onArt) { onArtwork = onArt; applyShadowColor(); refreshMaterial(); }

    /** Overrides the tint colour (e.g. a selected glass toggle). */
    public void setTintColor(int argb) { customTint = true; customTintColor = argb; refreshMaterial(); }
    public void clearTintColor() { customTint = false; refreshMaterial(); }

    public void setRimVisible(boolean visible) { material.setRimVisible(visible); }
    public void setBottomHairline(int color) { material.setBottomHairline(color); }

    /** Allows or forbids blur for this view (forbid for tiny or very fast-moving glass). */
    public void setBlurAllowed(boolean allowed) { blurAllowed = allowed; refreshMaterial(); }
    public void setBlurRadius(float px) { blurRadiusPx = px; if (engine != null) engine.setBlur(px); }

    /** Re-applies theme roles (after a theme switch). */
    public void setRoles(ColorRoles newRoles) { roles = newRoles; applyShadowColor(); refreshMaterial(); }
    public ColorRoles roles() { return roles; }

    /** True when the blurred backdrop is drawn (otherwise the dense fallback tint). */
    public boolean blurActive() {
        return backdrop != null && blurAllowed && Build.VERSION.SDK_INT >= 31 && Theme.glassBlurEnabled(getContext())
                && isHardwareAccelerated() && !isInside(backdrop);
    }

    private void refreshMaterial() {
        boolean blur = blurActive();
        if (blur && engine == null && Build.VERSION.SDK_INT >= 31) { engine = new Engine(); engine.setBlur(blurRadiusPx); }
        GlassDrawable preset = onArtwork ? GlassDrawable.onArtwork(blur) : GlassDrawable.chrome(roles, blur);
        material.setFillColor(customTint ? customTintColor : preset.getFillColor());
        if (onArtwork) material.setRimColors(0x66FFFFFF, 0x14FFFFFF); else material.setRimColors(roles.glassRimTop, roles.glassRimBottom);
        invalidate();
    }

    private void applyShadowColor() {
        if (Build.VERSION.SDK_INT >= 28) {
            int shadow = onArtwork ? 0x33000000 : roles.glassShadow;
            setOutlineAmbientShadowColor(shadow);
            setOutlineSpotShadowColor(shadow);
        }
    }

    private int backdropBase() { return onArtwork ? 0xFF101012 : roles.canvas; }

    private boolean isInside(View ancestor) {
        for (ViewParent p = getParent(); p != null; p = p.getParent()) if (p == ancestor) return true;
        return false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        material.setBounds(0, 0, w, h);
        invalidateOutline();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startListening();
        refreshMaterial();
    }

    @Override protected void onDetachedFromWindow() {
        stopListening();
        super.onDetachedFromWindow();
    }

    private void startListening() {
        if (listening || backdrop == null) return;
        getViewTreeObserver().addOnPreDrawListener(preDraw);
        listening = true;
    }

    private void stopListening() {
        if (!listening) return;
        ViewTreeObserver vto = getViewTreeObserver();
        if (vto.isAlive()) vto.removeOnPreDrawListener(preDraw);
        listening = false;
    }

    /*
     * Do NOT skip draw() while another glass records its backdrop. A hardware child's display
     * list is rebuilt by View.draw(); recording it empty during a capture clears its dirty flag,
     * so the real frame reuses the empty list and the view (e.g. every TopBar button, which sits
     * inside the content the tab bar / mini player capture) never appears. Only the blur layer
     * is skipped during a capture (no glass-in-glass recursion); the material still draws.
     */
    @Override protected void onDraw(Canvas canvas) {
        if (!capturing && engine != null && blurActive() && canvas.isHardwareAccelerated()) engine.draw(canvas);
        material.draw(canvas);
    }

    @Override protected boolean verifyDrawable(android.graphics.drawable.Drawable who) { return who == material || super.verifyDrawable(who); }

    /** API 31+ backdrop recorder. Everything is allocated once. */
    @TargetApi(31)
    private static final class Engine {
        private final RenderNode node = new RenderNode("aura-glass");
        private final int[] glassAt = new int[2], sourceAt = new int[2];
        private int pad;

        void setBlur(float radiusPx) {
            pad = (int) Math.ceil(radiusPx);
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(DesignTokens.GLASS_SATURATION);
            RenderEffect blur = RenderEffect.createBlurEffect(Math.max(1f, radiusPx), Math.max(1f, radiusPx), Shader.TileMode.CLAMP);
            node.setRenderEffect(RenderEffect.createChainEffect(RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(saturation)), blur));
        }

        void record(View source, View glass, int baseColor) {
            int w = glass.getWidth(), h = glass.getHeight();
            if (w <= 0 || h <= 0 || source.getWidth() <= 0 || !source.isAttachedToWindow()) return;
            glass.getLocationInWindow(glassAt);
            source.getLocationInWindow(sourceAt);
            node.setPosition(0, 0, w + 2 * pad, h + 2 * pad);
            RecordingCanvas c = node.beginRecording();
            try {
                c.drawColor(baseColor);
                c.translate(pad - (glassAt[0] - sourceAt[0]), pad - (glassAt[1] - sourceAt[1]));
                capturing = true;
                source.draw(c);
            } finally {
                capturing = false;
                node.endRecording();
            }
        }

        void draw(Canvas canvas) {
            if (!node.hasDisplayList()) return;
            canvas.save();
            canvas.translate(-pad, -pad);
            canvas.drawRenderNode(node);
            canvas.restore();
        }
    }
}
