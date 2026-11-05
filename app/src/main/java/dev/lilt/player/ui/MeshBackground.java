package dev.lilt.player.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import dev.lilt.player.DesignTokens;

/**
 * Now Playing / Lyrics / Queue background: the artwork's colours as three large, soft radial
 * fields over the palette's base, darkened slightly, drifting slowly (24 s loop) while music
 * plays. Track changes cross-fade between palettes (600 ms). Without artwork the palette is
 * neutral graphite — never a decorative gradient.
 *
 * <p>Cheap by construction: shaders are created only when the palette or size changes; each
 * frame only moves their local matrices. Drift stops when paused, detached or when animations
 * are disabled.
 */
public class MeshBackground extends View {
    private static final int FIELDS = 3;

    private final Paint base = new Paint(), field = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint();
    private final Matrix matrix = new Matrix();
    private ArtworkPalette palette = ArtworkPalette.NEUTRAL, previous;
    private RadialGradient[] shaders = new RadialGradient[FIELDS], prevShaders = new RadialGradient[FIELDS];
    private float fade = 1f;
    private boolean animating;
    private ValueAnimator fadeAnim;

    public MeshBackground(Context c) {
        super(c);
        shade.setColor(0x38000000);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setPalette(ArtworkPalette p, boolean animate) {
        if (p == null) p = ArtworkPalette.NEUTRAL;
        if (p == palette) return;
        previous = palette;
        prevShaders = shaders;
        palette = p;
        shaders = new RadialGradient[FIELDS];
        build(shaders, palette);
        if (fadeAnim != null) fadeAnim.cancel();
        if (animate) fadeAnim = Motion.animate(0f, 1f, 600, Motion.standard(), v -> { fade = v; invalidate(); });
        else { fade = 1f; invalidate(); }
    }

    /** Drift on while playing. */
    public void setAnimating(boolean on) {
        animating = on;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        build(shaders, palette);
        if (previous != null) build(prevShaders, previous);
    }

    private void build(RadialGradient[] out, ArtworkPalette p) {
        if (getWidth() == 0) return;
        int[] mesh = p.mesh();
        float r = Math.max(getWidth(), getHeight()) * 0.62f;
        int[] colors = {mesh[0], mesh[2], mesh[3]};
        for (int i = 0; i < FIELDS; i++) {
            int col = colors[i];
            out[i] = new RadialGradient(0, 0, r, new int[] {col, col & 0x00FFFFFF | 0x99000000, col & 0x00FFFFFF}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        boolean drift = animating && Motion.enabled() && isShown();
        double t = drift ? (SystemClock.uptimeMillis() % DesignTokens.DURATION_MESH_LOOP) / (double) DesignTokens.DURATION_MESH_LOOP * Math.PI * 2 : 0.0;
        if (previous != null && fade < 1f) draw(canvas, previous, prevShaders, 1f, t);
        draw(canvas, palette, shaders, previous != null && fade < 1f ? fade : 1f, t);
        canvas.drawRect(0, 0, getWidth(), getHeight(), shade);
        if (drift) postInvalidateOnAnimation();
    }

    private void draw(Canvas canvas, ArtworkPalette p, RadialGradient[] s, float alpha, double t) {
        int w = getWidth(), h = getHeight();
        base.setColor(p.background);
        base.setAlpha(Math.round(255 * alpha));
        canvas.drawRect(0, 0, w, h, base);
        // Field centres: top-left glow, right-middle accent, bottom-left depth; each orbits gently.
        float[][] anchors = {{0.15f, 0.12f}, {0.95f, 0.5f}, {0.1f, 0.95f}};
        for (int i = 0; i < FIELDS; i++) {
            if (s[i] == null) continue;
            float cx = w * (anchors[i][0] + 0.08f * (float) Math.sin(t + i * 2.1));
            float cy = h * (anchors[i][1] + 0.05f * (float) Math.cos(t * 0.8 + i * 1.7));
            matrix.setTranslate(cx, cy);
            s[i].setLocalMatrix(matrix);
            field.setShader(s[i]);
            field.setAlpha(Math.round(255 * alpha * (i == 1 ? 0.55f : 0.9f)));
            canvas.drawRect(0, 0, w, h, field);
        }
    }
}
