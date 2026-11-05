package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * The "this is playing" mark: three 3dp bars (14dp box) that bounce while playing and rest
 * low when paused. Animates only while attached, visible and playing, and stays still when
 * animations are disabled. Used over row artwork and in place of track numbers.
 */
public class NowPlayingIndicator extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean playing;

    public NowPlayingIndicator(Context c, int color) {
        super(c);
        paint.setColor(color);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setColor(int color) { paint.setColor(color); invalidate(); }

    public void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int s = Ui.dp(getContext(), 14);
        setMeasuredDimension(resolveSize(s, w), resolveSize(s, h));
    }

    @Override protected void onDraw(Canvas canvas) {
        float bar = Ui.dpf(getContext(), 3), gap = Ui.dpf(getContext(), 2), box = Ui.dpf(getContext(), 14);
        float left = (getWidth() - (3 * bar + 2 * gap)) / 2f, bottom = (getHeight() + box) / 2f;
        boolean animate = playing && Motion.enabled() && isShown();
        double t = SystemClock.uptimeMillis() / 1000.0;
        for (int i = 0; i < 3; i++) {
            float level = animate ? (float) (0.35 + 0.65 * Math.abs(Math.sin(t * (2.2 + i * 0.7) + i * 1.3))) : (playing ? 0.6f - i * 0.12f : 0.3f);
            float h = Math.max(bar, box * level), x = left + i * (bar + gap);
            canvas.drawRoundRect(x, bottom - h, x + bar, bottom, bar / 2, bar / 2, paint);
        }
        if (animate) postInvalidateOnAnimation();
    }
}
