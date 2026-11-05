package dev.lilt.player.screens.discover;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.ui.ColorRoles;

/**
 * Loading placeholder: a rounded block in the skeleton colour with a slow highlight sweeping
 * across. All skeletons share one clock so neighbours shimmer in step; still when the user
 * disabled animations. Allocation-free while drawing.
 */
public final class Skeleton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final int base, highlight;
    private final float radius;
    private LinearGradient shader;

    public Skeleton(Context c, ColorRoles roles, float radiusPx) {
        super(c);
        base = roles.skeleton;
        highlight = roles.skeletonHighlight;
        radius = radiusPx;
        paint.setColor(base);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** A capsule or circle (radius = half the shorter side). */
    public static Skeleton round(Context c, ColorRoles roles) { return new Skeleton(c, roles, 10_000f); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float band = Math.max(1, w);
        shader = new LinearGradient(0, 0, band, 0, new int[] {base, highlight, base}, new float[] {0.15f, 0.5f, 0.85f}, Shader.TileMode.CLAMP);
        rect.set(0, 0, w, h);
    }

    @Override protected void onDraw(Canvas canvas) {
        float r = Math.min(radius, Math.min(getWidth(), getHeight()) / 2f);
        if (shader == null || !ValueAnimator.areAnimatorsEnabled()) {
            paint.setShader(null);
            paint.setColor(base);
            canvas.drawRoundRect(rect, r, r, paint);
            return;
        }
        float phase = (SystemClock.uptimeMillis() % DesignTokens.DURATION_SHIMMER) / (float) DesignTokens.DURATION_SHIMMER;
        matrix.setTranslate((phase * 2f - 1f) * getWidth(), 0);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawRoundRect(rect, r, r, paint);
        postInvalidateOnAnimation();
    }
}
