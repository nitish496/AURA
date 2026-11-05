package dev.lilt.player.screens.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.TimeText;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;

/**
 * The slim Now Playing scrubber: a 4dp track that grows to 10dp while dragged, elapsed and
 * remaining time in tabular figures underneath, and an optional centred format line. Drag from
 * anywhere (relative: the position moves with the finger's travel) or tap to jump. Light haptic
 * ticks mark round positions while dragging ({@link ScrubMath#tickStepMs}) and the two ends.
 *
 * <p>No allocation in onDraw: labels are formatted only when the shown second changes.
 */
public final class Scrubber extends View {
    public interface Listener {
        /** The user released at {@code positionMs}. */
        void onSeek(long positionMs);
        /** Dragging started / ended (stop auto-updates meanwhile). */
        default void onScrubbing(boolean scrubbing) {}
    }

    private final float density;
    private final float padTop, idleH, activeH, labelGap;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG), center = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int secondaryColor = ColorRoles.ON_ART_SECONDARY, primaryColor = ColorRoles.ON_ART_PRIMARY;
    private long position, duration, dragPosition, dragStart;
    private String elapsedText = "0:00", remainingText = TimeText.MINUS + "0:00", formatText = "";
    private long shownElapsedSec = -1, shownRemainSec = -1;
    private boolean dragging, maybeTap;
    private float downX, downY, grow;
    private long lastTick = -1;
    private final int touchSlop;
    private ValueAnimator growAnim;
    private Listener listener;

    public Scrubber(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        padTop = NowPlayingGeometry.SCRUB_TOUCH_PAD * density;
        idleH = 4 * density;
        activeH = 10 * density;
        labelGap = 10 * density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        label.setTypeface(Type.typeface(context, TypeScale.CAPTION1));
        label.setTextSize(TypeScale.CAPTION1.size * context.getResources().getDisplayMetrics().scaledDensity);
        label.setFontFeatureSettings("tnum");
        center.setTypeface(Type.typeface(context, TypeScale.CAPTION2));
        center.setTextSize(TypeScale.CAPTION2.size * context.getResources().getDisplayMetrics().scaledDensity);
        center.setLetterSpacing(0.02f);
        center.setTextAlign(Paint.Align.CENTER);
        setColors(0xE0F5F5F7, ColorRoles.ON_ART_TRACK, ColorRoles.ON_ART_SECONDARY, ColorRoles.ON_ART_PRIMARY);
        setFocusable(true);
        setContentDescription("Playback position");
    }

    public void setListener(Listener l) { listener = l; }

    public void setColors(int fillColor, int trackColor, int secondary, int primary) {
        fill.setColor(fillColor);
        track.setColor(trackColor);
        secondaryColor = secondary;
        primaryColor = primary;
        center.setColor(secondary);
        invalidate();
    }

    /** Centred format line ("FLAC · 44.1 kHz"); "" hides it. */
    public void setFormat(String text) {
        String t = text == null ? "" : text;
        if (!t.equals(formatText)) { formatText = t; invalidate(); }
    }

    public boolean isScrubbing() { return dragging; }

    /** Current playback position; ignored while the user drags. */
    public void setProgress(long positionMs, long durationMs) {
        position = Math.max(0, positionMs);
        duration = Math.max(0, durationMs);
        if (!dragging) { updateLabels(position); invalidate(); }
    }

    private void updateLabels(long pos) {
        long e = Math.max(0, pos) / 1000;
        long left = (Math.max(0, duration - Math.max(0, pos)) + 999) / 1000;
        if (e != shownElapsedSec) { shownElapsedSec = e; elapsedText = TimeText.clock(pos); }
        if (left != shownRemainSec) { shownRemainSec = left; remainingText = duration > 0 ? TimeText.remaining(pos, duration) : "--:--"; }
    }

    private float trackLeft() { return getPaddingLeft(); }
    private float trackWidth() { return Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight()); }

    @Override protected void onMeasure(int w, int h) {
        int height = Math.round(padTop * 2 + dev.lilt.player.ui.NowPlayingLayout.SCRUBBER * density);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), resolveSize(height, h));
    }

    @Override protected void onDraw(Canvas canvas) {
        float left = trackLeft(), width = trackWidth();
        float cy = padTop + 6 * density;
        float th = ScrubMath.trackHeight(idleH, activeH, grow);
        long shown = dragging ? dragPosition : position;
        float f = duration > 0 ? Math.min(1f, shown / (float) duration) : 0f;
        float r = th / 2;
        rect.set(left, cy - th / 2, left + width, cy + th / 2);
        canvas.drawRoundRect(rect, r, r, track);
        if (f > 0) {
            rect.right = left + Math.max(th, width * f);
            canvas.drawRoundRect(rect, r, r, fill);
        }
        float baseline = cy + activeH / 2 + labelGap + 10 * density;
        int c = dragging ? primaryColor : secondaryColor;
        label.setColor(c);
        label.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(elapsedText, left, baseline, label);
        label.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(remainingText, left + width, baseline, label);
        if (!formatText.isEmpty() && !dragging) canvas.drawText(formatText, left + width / 2, baseline, center);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || duration <= 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                maybeTap = true;
                dragStart = position;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(e.getY() - downY)) {
                    maybeTap = false;
                    startDrag();
                    downX = e.getX();
                    dragStart = position;
                }
                if (!dragging && Math.abs(e.getY() - downY) > touchSlop * 2) maybeTap = false;
                if (dragging) {
                    dragPosition = ScrubMath.dragPosition(dragStart, e.getX() - downX, trackWidth(), duration);
                    tickIfNeeded();
                    updateLabels(dragPosition);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    endDrag(true);
                } else if (maybeTap) {
                    long target = ScrubMath.positionAt(ScrubMath.fraction(e.getX(), trackLeft(), trackWidth()), duration);
                    position = target;
                    updateLabels(target);
                    invalidate();
                    Haptics.tick(this);
                    if (listener != null) listener.onSeek(target);
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) endDrag(false);
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    private void startDrag() {
        dragging = true;
        dragPosition = position;
        lastTick = ScrubMath.tickIndex(position, ScrubMath.tickStepMs(duration));
        ViewParent p = getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(true);
        animateGrow(1f);
        Haptics.tick(this);
        if (listener != null) listener.onScrubbing(true);
    }

    private void endDrag(boolean commit) {
        dragging = false;
        animateGrow(0f);
        if (commit) {
            position = dragPosition;
            if (listener != null) listener.onSeek(dragPosition);
        }
        updateLabels(position);
        invalidate();
        if (listener != null) listener.onScrubbing(false);
    }

    private void tickIfNeeded() {
        long step = ScrubMath.tickStepMs(duration);
        long idx = dragPosition <= 0 ? -1 : dragPosition >= duration ? Long.MAX_VALUE : ScrubMath.tickIndex(dragPosition, step);
        if (idx != lastTick) { lastTick = idx; Haptics.tick(this); }
    }

    private void animateGrow(float to) {
        if (growAnim != null) growAnim.cancel();
        growAnim = Motion.animate(grow, to, Motion.SPRING_MS, Motion.spring(), v -> { grow = v; invalidate(); });
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (growAnim != null) growAnim.cancel();
    }

    // ---------------------------------------------------------------- accessibility

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, Math.max(1, duration / 1000), position / 1000));
        if (android.os.Build.VERSION.SDK_INT >= 30) info.setStateDescription(TimeText.clock(position) + " of " + TimeText.clock(duration));
        if (duration > 0) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (duration > 0 && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            long target = Math.max(0, Math.min(duration, position + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 10_000 : -10_000)));
            position = target;
            updateLabels(target);
            invalidate();
            if (listener != null) listener.onSeek(target);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
