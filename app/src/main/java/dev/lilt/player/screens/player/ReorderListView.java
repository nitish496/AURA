package dev.lilt.player.screens.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Motion;

/**
 * A ListView whose data rows can be reordered (touch the drag handle at the right edge, or
 * long-press a row) and swiped away. Header and footer views are never moved. Rows are addressed
 * by data index (adapter position minus headers).
 *
 * <p>While a row is dragged, a snapshot follows the finger and {@link Callbacks#onMove} is called
 * each time it passes a neighbour, so the adapter can swap its data live; the adapter must keep
 * the row at {@link #draggingIndex()} invisible and reset {@code translationX} / {@code alpha}
 * when it binds a row. {@link Callbacks#onDrop} reports the whole move once the finger lifts.
 * Swiping never starts a drag and vice versa; vertical flings scroll normally.
 */
public final class ReorderListView extends ListView {
    public interface Callbacks {
        /** Number of data rows. */
        int count();
        boolean canDrag(int index);
        boolean canSwipe(int index);
        /** Live swap while dragging: the row moved from {@code from} to {@code to}. */
        void onMove(int from, int to);
        /** Finger lifted: the row that started at {@code from} ended at {@code to} (may be equal). */
        void onDrop(int from, int to);
        void onSwipeRemove(int index);
    }

    private static final long LONG_PRESS_MS = 320;

    private final float density;
    private final int slop;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), liftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Callbacks callbacks;
    private int handleWidthPx;

    private float downX, downY, lastY;
    private int downIndex = -1;
    private boolean dragging, swiping, pending;
    private int dragFrom, dragCur;
    private Bitmap snapshot;
    private float hoverTop, touchOffset;
    private View swipeView;
    private int swipeIndex;
    private VelocityTracker velocity;
    private final Runnable longPress = this::startDragFromPending;
    private final Runnable autoScroll = new Runnable() {
        @Override public void run() {
            if (!dragging) return;
            int step = 0;
            float edge = 72 * density;
            if (hoverTop < edge) step = -Math.round(Math.min(14 * density, (edge - hoverTop) / 4f + 2));
            else if (hoverTop + snapshotHeight() > getHeight() - edge) step = Math.round(Math.min(14 * density, (hoverTop + snapshotHeight() - (getHeight() - edge)) / 4f + 2));
            if (step != 0) { scrollListBy(step); hoverTop = clampHover(hoverTop); retarget(); invalidate(); }
            postOnAnimation(this);
        }
    };

    public ReorderListView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        handleWidthPx = Math.round(56 * density);
        liftPaint.setColor(0x33FFFFFF);
        setDivider(null);
        setSelector(android.R.color.transparent);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void setCallbacks(Callbacks c) { callbacks = c; }

    public boolean isDragging() { return dragging; }

    /** Data index of the row being dragged, or -1. */
    public int draggingIndex() { return dragging ? dragCur : -1; }

    private int dataIndexAt(float x, float y) {
        int pos = pointToPosition((int) x, (int) y);
        if (pos == INVALID_POSITION) return -1;
        int index = pos - getHeaderViewsCount();
        return callbacks != null && index >= 0 && index < callbacks.count() ? index : -1;
    }

    private View rowView(int index) {
        int child = index + getHeaderViewsCount() - getFirstVisiblePosition();
        return child >= 0 && child < getChildCount() ? getChildAt(child) : null;
    }

    private int snapshotHeight() { return snapshot == null ? 0 : snapshot.getHeight(); }

    // ---------------------------------------------------------------- touch

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (callbacks == null) return super.onInterceptTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = lastY = e.getY();
                downIndex = dataIndexAt(downX, downY);
                dragging = false; swiping = false; pending = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                if (downIndex >= 0) {
                    if (callbacks.canDrag(downIndex) && downX >= getWidth() - handleWidthPx) { pending = true; startDragFromPending(); return true; }
                    if (callbacks.canDrag(downIndex)) { pending = true; postDelayed(longPress, LONG_PRESS_MS); }
                }
                break;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(e);
                if (dragging || swiping) return true;
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > slop || Math.abs(dy) > slop) { removeCallbacks(longPress); pending = false; }
                if (downIndex >= 0 && callbacks.canSwipe(downIndex) && Math.abs(dx) > slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    swipeView = rowView(downIndex);
                    if (swipeView != null) { swiping = true; swipeIndex = downIndex; getParent().requestDisallowInterceptTouchEvent(true); return true; }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                pending = false;
                if (velocity != null) { velocity.recycle(); velocity = null; }
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (dragging) { handleDrag(e); return true; }
        if (swiping) { handleSwipe(e); return true; }
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress);
            pending = false;
        }
        return super.onTouchEvent(e);
    }

    // ---------------------------------------------------------------- drag

    private void startDragFromPending() {
        if (!pending || callbacks == null || downIndex < 0) return;
        View row = rowView(downIndex);
        if (row == null) return;
        pending = false;
        dragging = true;
        dragFrom = dragCur = downIndex;
        snapshot = Bitmap.createBitmap(Math.max(1, row.getWidth()), Math.max(1, row.getHeight()), Bitmap.Config.ARGB_8888);
        row.draw(new Canvas(snapshot));
        touchOffset = downY - row.getTop();
        hoverTop = row.getTop();
        row.setVisibility(INVISIBLE);
        getParent().requestDisallowInterceptTouchEvent(true);
        Haptics.dragStart(this);
        postOnAnimation(autoScroll);
        invalidate();
    }

    private void handleDrag(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                lastY = e.getY();
                hoverTop = clampHover(lastY - touchOffset);
                retarget();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishDrag();
                break;
            default:
                break;
        }
    }

    private float clampHover(float top) {
        return Math.max(-snapshotHeight() / 2f, Math.min(getHeight() - snapshotHeight() / 2f, top));
    }

    /** Moves the dragged row to the slot under the snapshot's centre. */
    private void retarget() {
        int centre = Math.round(hoverTop + snapshotHeight() / 2f);
        int target = dataIndexAt(getWidth() / 2f, Math.max(0, Math.min(getHeight() - 1, centre)));
        if (target < 0 || target == dragCur) return;
        int from = dragCur;
        dragCur = target;
        callbacks.onMove(from, target);
        Haptics.tick(this);
    }

    private void finishDrag() {
        removeCallbacks(autoScroll);
        dragging = false;
        int from = dragFrom, to = dragCur;
        if (snapshot != null) { snapshot.recycle(); snapshot = null; }
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setVisibility(VISIBLE);
        callbacks.onDrop(from, to);
        Haptics.gestureEnd(this);
        invalidate();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (dragging && snapshot != null) {
            float r = 14 * density;
            rect.set(0, hoverTop, getWidth(), hoverTop + snapshot.getHeight());
            canvas.drawRoundRect(rect, r, r, liftPaint);
            canvas.drawBitmap(snapshot, 0, hoverTop, paint);
        }
    }

    // ---------------------------------------------------------------- swipe to remove

    private void handleSwipe(MotionEvent e) {
        if (velocity != null) velocity.addMovement(e);
        float dx = e.getX() - downX;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                swipeView.setTranslationX(dx);
                swipeView.setAlpha(Math.max(0.2f, 1f - Math.abs(dx) / Math.max(1f, getWidth()) * 0.9f));
                break;
            case MotionEvent.ACTION_UP: {
                float vx = 0;
                if (velocity != null) { velocity.computeCurrentVelocity(1000); vx = velocity.getXVelocity(); }
                boolean remove = Math.abs(dx) > getWidth() * 0.35f || (Math.abs(vx) > 1200 * density && Math.abs(dx) > 24 * density && Math.signum(vx) == Math.signum(dx));
                endSwipe(remove, dx < 0 ? -1 : 1);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                endSwipe(false, 1);
                break;
            default:
                break;
        }
    }

    private void endSwipe(boolean remove, int direction) {
        final View v = swipeView;
        final int index = swipeIndex;
        swiping = false;
        swipeView = null;
        if (velocity != null) { velocity.recycle(); velocity = null; }
        if (remove) {
            Haptics.confirm(this);
            v.animate().translationX(direction * getWidth()).alpha(0f).setDuration(Motion.FAST).setInterpolator(Motion.accelerate())
                    .withEndAction(() -> { v.setTranslationX(0f); v.setAlpha(1f); callbacks.onSwipeRemove(index); }).start();
        } else {
            v.animate().translationX(0f).alpha(1f).setDuration(Motion.SPRING_MS).setInterpolator(Motion.spring()).start();
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(longPress);
        removeCallbacks(autoScroll);
        if (snapshot != null) { snapshot.recycle(); snapshot = null; }
        dragging = false;
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }
}
