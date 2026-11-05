package dev.lilt.player.screens.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import dev.lilt.player.ui.NowPlayingLayout;

/**
 * The 36 x 5dp pill drag handle at the top of the player screens (no allocation while drawing),
 * plus an optional close chevron at the start edge. The view is 48dp tall so the chevron has a
 * proper touch target; only the chevron takes touches, everything else falls through to the
 * views below (and to the stage's drag-to-dismiss, which starts in this strip).
 */
public final class HandleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float w, h, d;
    private Runnable onClose;

    public HandleView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        w = NowPlayingGeometry.HANDLE_W * d;
        h = NowPlayingGeometry.HANDLE_H * d;
        paint.setColor(0x66F5F5F7);
        chevron.setColor(0xCCF5F5F7);
        chevron.setStyle(Paint.Style.STROKE);
        chevron.setStrokeCap(Paint.Cap.ROUND);
        chevron.setStrokeJoin(Paint.Join.ROUND);
        chevron.setStrokeWidth(2.5f * d);
        setMinimumHeight(Math.round(NowPlayingGeometry.CHEVRON_TARGET * d));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Shows a visible close chevron at the start edge that runs {@code r} when tapped. */
    public void setOnClose(Runnable r) { onClose = r; invalidate(); }

    private boolean inTarget(float x, float y) {
        float t = NowPlayingGeometry.CHEVRON_TARGET * d;
        return onClose != null && x >= 0 && x <= t + 8 * d && y >= 0 && y <= t;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return inTarget(e.getX(), e.getY());
        if (e.getActionMasked() == MotionEvent.ACTION_UP && inTarget(e.getX(), e.getY()) && onClose != null) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClose.run();
        }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        float cy = NowPlayingLayout.HANDLE * d / 2f;
        rect.set((getWidth() - w) / 2f, cy - h / 2f, (getWidth() + w) / 2f, cy + h / 2f);
        canvas.drawRoundRect(rect, h / 2, h / 2, paint);
        if (onClose != null) {
            float cx = 28 * d, half = NowPlayingGeometry.CHEVRON * d / 2f;
            canvas.drawLine(cx - half, cy - half / 2.2f, cx, cy + half / 2.2f, chevron);
            canvas.drawLine(cx, cy + half / 2.2f, cx + half, cy - half / 2.2f, chevron);
        }
    }
}
