package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Horizontal slider / scrubber. Two looks:
 * <ul>
 *   <li>{@link #SETTINGS}: 4dp track, Ember fill, 24dp white thumb (crossfade, sleep timer).</li>
 *   <li>{@link #PLAYER}: 4dp track on artwork that grows to 8dp while dragged, no thumb,
 *       {@link ArtworkPalette} control colours (Now Playing scrubber).</li>
 * </ul>
 * Optional snap steps tick a haptic at each step. 48dp touch height; accessibility exposes a
 * range with ±1 step (or ±5%) scroll actions.
 */
public class Slider extends View {
    public static final int SETTINGS = 0, PLAYER = 1;

    public interface Listener {
        default void onStart(Slider s) {}
        void onChange(Slider s, float value, boolean fromUser);
        default void onStop(Slider s, float value) {}
    }

    private final int style;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG), fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
            thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float value, grow;
    private int steps; // 0 = continuous; n = n intervals
    private boolean dragging;
    private Listener listener;

    public Slider(Context c, int style) { this(c, Theme.colors(c), style); }

    public Slider(Context c, ColorRoles roles, int style) {
        super(c);
        this.style = style;
        if (style == PLAYER) { trackPaint.setColor(ColorRoles.ON_ART_TRACK); fillPaint.setColor(0xE0F5F5F7); }
        else { trackPaint.setColor(roles.fillStrong); fillPaint.setColor(roles.accent); }
        thumbPaint.setColor(0xFFFFFFFF);
        thumbPaint.setShadowLayer(Ui.dpf(c, 3), 0, Ui.dpf(c, 1), 0x40000000);
        setFocusable(true);
        setContentDescription(style == PLAYER ? "Playback position" : null);
    }

    /** Colours for the player style from an artwork palette. */
    public void setPalette(ArtworkPalette p) { trackPaint.setColor(p.controlTrack); fillPaint.setColor(p.controlFill); invalidate(); }
    public void setColors(int track, int fill) { trackPaint.setColor(track); fillPaint.setColor(fill); invalidate(); }
    /** Number of snap intervals (e.g. 12 for a 0–12 s crossfade); 0 = continuous. */
    public void setSteps(int n) { steps = Math.max(0, n); }
    public void setListener(Listener l) { listener = l; }
    public boolean isDragging() { return dragging; }
    public float value() { return value; }

    /** Programmatic update (ignored while the user drags). */
    public void setValue(float v) { if (!dragging) { value = clamp(v); invalidate(); } }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 200), w), resolveSize(Ui.dp(getContext(), 48), h));
    }

    @Override protected void onDraw(Canvas canvas) {
        float pad = style == SETTINGS ? Ui.dpf(getContext(), 12) : 0;
        float left = getPaddingLeft() + pad, right = getWidth() - getPaddingRight() - pad, cy = getHeight() / 2f;
        float h = Ui.dpf(getContext(), 4 + 4 * grow), x = left + (right - left) * value;
        canvas.drawRoundRect(left, cy - h / 2, right, cy + h / 2, h / 2, h / 2, trackPaint);
        if (x > left) canvas.drawRoundRect(left, cy - h / 2, x, cy + h / 2, h / 2, h / 2, fillPaint);
        if (style == SETTINGS) canvas.drawCircle(x, cy, Ui.dpf(getContext(), 12), thumbPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                if (listener != null) listener.onStart(this);
                if (style == PLAYER) Motion.animate(grow, 1f, Motion.FAST, Motion.standard(), g -> { grow = g; invalidate(); });
                track(e.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                track(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                track(e.getX());
                dragging = false;
                if (style == PLAYER) Motion.animate(grow, 0f, Motion.BASE, Motion.spring(), g -> { grow = g; invalidate(); });
                if (listener != null) listener.onStop(this, value);
                if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    private void track(float x) {
        float pad = style == SETTINGS ? Ui.dpf(getContext(), 12) : 0;
        float left = getPaddingLeft() + pad, right = getWidth() - getPaddingRight() - pad;
        set(clamp((x - left) / Math.max(1f, right - left)), true);
    }

    private void set(float v, boolean fromUser) {
        if (steps > 0) v = Math.round(v * steps) / (float) steps;
        if (v == value) return;
        boolean edge = (v == 0f || v == 1f) && value != v;
        boolean stepChanged = steps > 0;
        value = v;
        if (fromUser && (stepChanged || edge)) Haptics.tick(this);
        invalidate();
        if (listener != null) listener.onChange(this, value, fromUser);
    }

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(1, v); }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0, 100, value * 100));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        float step = steps > 0 ? 1f / steps : 0.05f;
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { set(clamp(value + step), true); notifyStop(); return true; }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { set(clamp(value - step), true); notifyStop(); return true; }
        if (action == android.R.id.accessibilityActionSetProgress && args != null) {
            set(clamp(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) / 100f), true);
            notifyStop();
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private void notifyStop() { if (listener != null) listener.onStop(this, value); }
}
