package dev.lilt.player.screens.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The 36 x 5dp pill drag handle at the top of the player screens (no allocation while drawing). */
public final class HandleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float w, h;

    public HandleView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        w = NowPlayingGeometry.HANDLE_W * d;
        h = NowPlayingGeometry.HANDLE_H * d;
        paint.setColor(0x66F5F5F7);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        rect.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        canvas.drawRoundRect(rect, h / 2, h / 2, paint);
    }
}
