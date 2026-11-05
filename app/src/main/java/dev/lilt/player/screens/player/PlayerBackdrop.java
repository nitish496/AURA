package dev.lilt.player.screens.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.ui.ArtworkPalette;
import dev.lilt.player.ui.ColorMath;
import dev.lilt.player.ui.Motion;

/**
 * Full-bleed artwork-colour background of Now Playing, Lyrics and Queue: four large, soft colour
 * fields from {@link ArtworkPalette#mesh()} over its deep base, drifting very slowly (one loop
 * per {@link DesignTokens#DURATION_MESH_LOOP}), under a vertical scrim that deepens towards the
 * controls. Graphite when there is no artwork. Palette changes cross-fade.
 *
 * <p>Cheap by design: shaders are built once per palette, onDraw only updates matrices and
 * alphas (no allocation), and while drifting it repaints at 25 fps (the motion is far below a
 * pixel per frame). Drift runs only while {@link #setAnimating(boolean)} is on (visible and
 * playing) and animations are enabled.
 */
public final class PlayerBackdrop extends View {
    /** Field centres and radii as fractions of the width/height (from the design prototype). */
    private static final float[][] FIELDS = {
        // cx, cy, radius (x width), opacity, mesh colour index, drift period factor, phase
        {0.28f, 0.15f, 0.90f, 0.95f, 0, 1.00f, 0.0f},
        {0.87f, 0.60f, 0.80f, 0.40f, 2, 0.83f, 1.7f},
        {0.41f, 0.90f, 0.95f, 1.00f, 1, 1.27f, 3.1f},
        {0.59f, 0.48f, 0.60f, 0.40f, 0, 0.91f, 4.4f},
    };
    private static final long FRAME_MS = 40;

    private static final class Layer {
        int base;
        final RadialGradient[] shaders = new RadialGradient[FIELDS.length];
        final Paint[] paints = new Paint[FIELDS.length];
        Layer(ArtworkPalette p) {
            int[] mesh = p.mesh();
            base = mesh.length > 3 ? mesh[3] : p.backgroundDeep;
            for (int i = 0; i < FIELDS.length; i++) {
                int c = mesh[(int) FIELDS[i][4]] | 0xFF000000;
                shaders[i] = new RadialGradient(0, 0, 1f,
                        new int[] {c, ColorMath.withAlpha(c, 0.55f), ColorMath.withAlpha(c, 0f)},
                        new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                paint.setShader(shaders[i]);
                paints[i] = paint;
            }
        }
    }

    private Layer current, previous;
    private float mix = 1f;
    private ValueAnimator fade;
    private final Matrix matrix = new Matrix();
    private final Paint scrim = new Paint(Paint.DITHER_FLAG);
    private boolean animating;
    private long driftMs, lastFrame;
    private ArtworkPalette palette;

    public PlayerBackdrop(Context context) {
        super(context);
        setPalette(ArtworkPalette.NEUTRAL, false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public ArtworkPalette palette() { return palette; }

    public void setPalette(ArtworkPalette p, boolean animate) {
        if (p == null) p = ArtworkPalette.NEUTRAL;
        if (p == palette) return;
        palette = p;
        if (fade != null) fade.cancel();
        Layer next = new Layer(p);
        if (current == null || !animate || !Motion.enabled()) {
            current = next; previous = null; mix = 1f; invalidate();
            return;
        }
        previous = current;
        current = next;
        mix = 0f;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(700);
        fade.setInterpolator(Motion.standard());
        fade.addUpdateListener(a -> { mix = (float) a.getAnimatedValue(); if (mix >= 1f) previous = null; invalidate(); });
        fade.start();
    }

    /** Drift on/off (visible and playing). */
    public void setAnimating(boolean on) {
        if (animating == on) return;
        animating = on;
        lastFrame = SystemClock.uptimeMillis();
        if (on) invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        // Deepens towards the bottom, where secondary text and controls sit (contrast is already
        // guaranteed by ArtworkPalette; the scrim only adds depth, it never lightens).
        scrim.setShader(new LinearGradient(0, 0, 0, h, new int[] {0x14000000, 0x1F000000, 0x47000000},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (fade != null) fade.cancel();
        animating = false;
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean drift = animating && Motion.enabled();
        if (drift) driftMs += Math.min(200, now - lastFrame);
        lastFrame = now;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (previous != null && mix < 1f) {
            canvas.drawColor(ColorMath.mix(previous.base, current.base, mix));
            drawLayer(canvas, previous, 1f - mix, w, h);
            drawLayer(canvas, current, mix, w, h);
        } else {
            canvas.drawColor(current.base);
            drawLayer(canvas, current, 1f, w, h);
        }
        canvas.drawRect(0, 0, w, h, scrim);
        if (drift) postInvalidateDelayed(FRAME_MS);
    }

    private void drawLayer(Canvas canvas, Layer layer, float alpha, int w, int h) {
        double loop = DesignTokens.DURATION_MESH_LOOP;
        for (int i = 0; i < FIELDS.length; i++) {
            float[] f = FIELDS[i];
            double t = driftMs / (loop * f[5]) * 2 * Math.PI + f[6];
            float cx = (float) ((f[0] + 0.07 * Math.sin(t)) * w);
            float cy = (float) ((f[1] + 0.05 * Math.cos(t * 0.8)) * h);
            float r = f[2] * w * (float) (1 + 0.06 * Math.sin(t * 1.3));
            matrix.setScale(r, r);
            matrix.postTranslate(cx, cy);
            layer.shaders[i].setLocalMatrix(matrix);
            Paint p = layer.paints[i];
            p.setAlpha(Math.round(255 * f[3] * alpha));
            canvas.drawCircle(cx, cy, r, p);
        }
    }
}
