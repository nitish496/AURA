package dev.lilt.player.app.chrome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import dev.lilt.player.app.SectionIndex;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/** A–Z fast scroller along the trailing edge of long alphabetical lists. Touch or drag to jump. */
public final class IndexBar extends View {
    public interface Listener { void onSection(int section); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ColorRoles roles;
    private boolean[] present = new boolean[SectionIndex.LETTERS.length];
    private int active = -1, current = -1;
    private Listener listener;

    public IndexBar(Context c, ColorRoles roles) {
        super(c);
        this.roles = roles;
        paint.setTypeface(Type.typeface(c, TypeScale.CAPTION2.weight(600)));
        paint.setTextSize(Ui.dpf(c, 10) * c.getResources().getConfiguration().fontScale);
        paint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setListener(Listener l) { listener = l; }

    public void setSections(SectionIndex index) {
        for (int i = 0; i < present.length; i++) present[i] = index.hasSection(i);
        invalidate();
    }

    /** Highlights the section currently at the top of the list (while not touching). */
    public void setCurrent(int section) {
        if (section != current) { current = section; invalidate(); }
    }

    private float step() { return Math.min(Ui.dpf(getContext(), 18), getHeight() / (float) SectionIndex.LETTERS.length); }

    @Override protected void onDraw(Canvas canvas) {
        float step = step();
        float top = (getHeight() - step * SectionIndex.LETTERS.length) / 2f;
        float baseline = (paint.descent() + paint.ascent()) / 2f;
        for (int i = 0; i < SectionIndex.LETTERS.length; i++) {
            boolean on = i == (active >= 0 ? active : current);
            paint.setColor(on ? roles.accent : present[i] ? roles.textSecondary : roles.textDisabled);
            canvas.drawText(SectionIndex.LETTERS[i], getWidth() / 2f, top + step * (i + 0.5f) - baseline, paint);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                getParent().requestDisallowInterceptTouchEvent(true);
                float step = step();
                float top = (getHeight() - step * SectionIndex.LETTERS.length) / 2f;
                int s = Math.max(0, Math.min(SectionIndex.LETTERS.length - 1, (int) ((e.getY() - top) / step)));
                if (s != active) {
                    active = s;
                    Haptics.tick(this);
                    if (listener != null) listener.onSection(s);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
