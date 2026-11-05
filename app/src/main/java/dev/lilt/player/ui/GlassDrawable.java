package dev.lilt.player.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The Liquid Glass material without the blur: a tint layer, a one-pixel specular rim (light
 * from the top: bright → faint) and an outline for the soft shadow. {@link GlassView} draws the
 * blurred backdrop underneath it on API 31+. On its own it is the API 26–30 fallback and a
 * background for separate windows (dialogs) — see {@link WindowBlur}.
 *
 * <p>No allocations while drawing: the rim gradient is rebuilt only when the height changes.
 */
public final class GlassDrawable extends Drawable {
    /** Corner radius value meaning "half the height" (capsule / circle). */
    public static final float CAPSULE = -1f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hairline = new Paint();
    private final RectF shape = new RectF(), rimRect = new RectF();
    private float radius = CAPSULE, rimWidth = 1f;
    private int inset, rimTop, rimBottom, hairlineColor;
    private boolean rimVisible = true;
    private LinearGradient rimShader;
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;

    public GlassDrawable(int fillColor, int rimTop, int rimBottom) {
        fill.setColor(fillColor);
        this.rimTop = rimTop;
        this.rimBottom = rimBottom;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
    }

    /** Navigation-layer glass for the theme; {@code blurred} picks the lighter tint used over a blur. */
    public static GlassDrawable chrome(ColorRoles roles, boolean blurred) {
        return new GlassDrawable(blurred ? roles.glassTint : roles.glassTintFallback, roles.glassRimTop, roles.glassRimBottom);
    }

    /** Glass for controls that sit on artwork tints (Now Playing capsule, player buttons). */
    public static GlassDrawable onArtwork(boolean blurred) {
        return new GlassDrawable(blurred ? ColorRoles.ON_ART_GLASS : 0x33FFFFFF, 0x66FFFFFF, 0x14FFFFFF);
    }

    public void setFillColor(int color) { if (fill.getColor() != color) { fill.setColor(color); invalidateSelf(); } }
    public int getFillColor() { return fill.getColor(); }

    public void setRimColors(int top, int bottom) {
        if (top == rimTop && bottom == rimBottom) return;
        rimTop = top;
        rimBottom = bottom;
        shaderTop = Float.NaN; // rebuild
        invalidateSelf();
    }

    public void setRimVisible(boolean visible) { rimVisible = visible; invalidateSelf(); }
    /** Rim stroke width in px (default one physical pixel). */
    public void setRimWidth(float px) { rimWidth = px; rim.setStrokeWidth(px); invalidateSelf(); }
    /** A 1px line along the bottom edge (top bars), or 0 for none. */
    public void setBottomHairline(int color) { hairlineColor = color; hairline.setColor(color); invalidateSelf(); }

    /** Corner radius in px, or {@link #CAPSULE}. */
    public void setCornerRadius(float px) { radius = px; invalidateSelf(); }
    public float getCornerRadius() { return radius; }

    /** Insets the shape from the drawable bounds (a 40dp circle inside a 48dp touch target). */
    public void setShapeInset(int px) { inset = px; invalidateSelf(); }

    /** The current shape in bounds coordinates. */
    public RectF shape() {
        Rect b = getBounds();
        shape.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        return shape;
    }

    public float effectiveRadius() {
        RectF s = shape();
        return radius < 0 ? Math.min(s.width(), s.height()) / 2f : radius;
    }

    @Override public void draw(Canvas canvas) {
        RectF s = shape();
        if (s.isEmpty()) return;
        float r = effectiveRadius();
        canvas.drawRoundRect(s, r, r, fill);
        if (rimVisible) {
            if (shaderTop != s.top || shaderBottom != s.bottom) {
                rimShader = new LinearGradient(0, s.top, 0, s.bottom, rimTop, rimBottom, Shader.TileMode.CLAMP);
                rim.setShader(rimShader);
                shaderTop = s.top;
                shaderBottom = s.bottom;
            }
            float h = rimWidth / 2f;
            rimRect.set(s.left + h, s.top + h, s.right - h, s.bottom - h);
            canvas.drawRoundRect(rimRect, Math.max(0, r - h), Math.max(0, r - h), rim);
        }
        if ((hairlineColor >>> 24) != 0) canvas.drawRect(s.left, s.bottom - 1, s.right, s.bottom, hairline);
    }

    @Override public void getOutline(Outline outline) {
        RectF s = shape();
        outline.setRoundRect(Math.round(s.left), Math.round(s.top), Math.round(s.right), Math.round(s.bottom), effectiveRadius());
        outline.setAlpha(1f);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); invalidateSelf(); }
    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
