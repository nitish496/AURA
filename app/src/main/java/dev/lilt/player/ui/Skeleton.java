package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import dev.lilt.player.DesignTokens;

/**
 * Loading placeholders that mirror the real layout (never spinners on content). A soft
 * highlight sweeps across every block in phase (1.4 s cycle); static when animations are off.
 *
 * <pre>{@code
 * column.addView(Skeleton.box(ctx, 190, 22, 6));
 * column.addView(Skeleton.songRows(ctx, 6));
 * }</pre>
 */
public final class Skeleton {
    private Skeleton() {}

    /** A shimmering rounded block. */
    public static final class ShimmerDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF rect = new RectF();
        private final float radius, sweep;
        private LinearGradient shader;
        private final int base, highlight;

        public ShimmerDrawable(ColorRoles roles, float radiusPx, float sweepPx) {
            base = roles.skeleton;
            highlight = roles.skeletonHighlight;
            radius = radiusPx;
            sweep = sweepPx;
            paint.setColor(base);
        }

        @Override public void draw(Canvas canvas) {
            rect.set(getBounds());
            if (shader == null) {
                shader = new LinearGradient(0, 0, sweep, 0, new int[] {base, highlight, base}, new float[] {0.3f, 0.5f, 0.7f}, Shader.TileMode.CLAMP);
                paint.setShader(shader);
            }
            boolean animate = Motion.enabled();
            float phase = animate ? (SystemClock.uptimeMillis() % DesignTokens.DURATION_SHIMMER) / (float) DesignTokens.DURATION_SHIMMER : 0.4f;
            matrix.setTranslate(-sweep + phase * (rect.width() + sweep) + rect.left, 0);
            shader.setLocalMatrix(matrix);
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (animate && isVisible()) invalidateSelf();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @SuppressWarnings("deprecation")
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A skeleton block of {@code wDp} x {@code hDp} (wDp ≤ 0 = match parent). */
    public static View box(Context c, int wDp, int hDp, int radiusDp) {
        View v = new View(c);
        v.setBackground(new ShimmerDrawable(Theme.colors(c), Ui.dpf(c, radiusDp), Ui.dpf(c, 240)));
        v.setLayoutParams(new LinearLayout.LayoutParams(wDp <= 0 ? LinearLayout.LayoutParams.MATCH_PARENT : Ui.dp(c, wDp), Ui.dp(c, hDp)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** {@code n} song-row skeletons (44dp art, two text lines) at the real 60dp rhythm. */
    public static LinearLayout songRows(Context c, int n) {
        LinearLayout col = Ui.column(c);
        col.setContentDescription("Loading");
        for (int i = 0; i < n; i++) {
            LinearLayout row = Ui.row(c);
            row.setMinimumHeight(Ui.dp(c, DesignTokens.ROW_SONG));
            row.setPaddingRelative(Ui.dp(c, DesignTokens.MARGIN), 0, Ui.dp(c, DesignTokens.MARGIN), 0);
            row.addView(box(c, DesignTokens.COVER_ROW, DesignTokens.COVER_ROW, DesignTokens.RADIUS_S));
            LinearLayout text = Ui.column(c);
            text.addView(box(c, 160 - (i % 3) * 24, 14, 4));
            View sub = box(c, 96 + (i % 2) * 20, 12, 4);
            ((LinearLayout.LayoutParams) sub.getLayoutParams()).topMargin = Ui.dp(c, 8);
            text.addView(sub);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tp.setMarginStart(Ui.dp(c, 12));
            row.addView(text, tp);
            col.addView(row);
        }
        return col;
    }

    /** A shelf of {@code n} square-card skeletons ({@code cardDp} wide) with two text lines each. */
    public static LinearLayout shelf(Context c, int n, int cardDp) {
        LinearLayout strip = Ui.row(c);
        strip.setGravity(android.view.Gravity.TOP);
        strip.setPaddingRelative(Ui.dp(c, DesignTokens.MARGIN), 0, 0, 0);
        for (int i = 0; i < n; i++) {
            LinearLayout cell = Ui.column(c);
            cell.addView(box(c, cardDp, cardDp, DesignTokens.artworkRadius(cardDp)));
            View t = box(c, cardDp * 3 / 4, 14, 4);
            ((LinearLayout.LayoutParams) t.getLayoutParams()).topMargin = Ui.dp(c, 10);
            cell.addView(t);
            View s = box(c, cardDp / 2, 12, 4);
            ((LinearLayout.LayoutParams) s.getLayoutParams()).topMargin = Ui.dp(c, 6);
            cell.addView(s);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) p.setMarginStart(Ui.dp(c, DesignTokens.GUTTER));
            strip.addView(cell, p);
        }
        return strip;
    }
}
