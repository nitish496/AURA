package dev.lilt.player.screens.player;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import dev.lilt.player.ui.Motion;

/**
 * The Now Playing canvas: lays out the backdrop and every control from {@link NowPlayingGeometry}
 * (adaptive to any window height) and implements the drag-down-to-dismiss gesture: the whole
 * stage follows the finger, then either flies off (fast or far enough) or springs back
 * ({@link DismissGesture}). Touches that start on the scrubber never start a dismiss.
 */
public final class NowPlayingStage extends ViewGroup {
    public interface Listener {
        /** 0..1 while dragging. */
        void onDismissProgress(float progress);
        /** The stage left the screen; navigate away. */
        void onDismissed();
    }

    private final View backdrop, handle, art, titleBlock, heart, more, scrubber, prev, play, next, capsule;
    private final float density;
    private final int slop;
    private int insetTop, insetBottom;
    private NowPlayingGeometry geometry;
    private Listener listener;
    private boolean dragging, startedOnScrubber, dismissing;
    private float downRawX, downRawY;
    private VelocityTracker velocity;

    public NowPlayingStage(Context context, View backdrop, View handle, View art, View titleBlock, View heart, View more,
                           View scrubber, View prev, View play, View next, View capsule) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.backdrop = backdrop; this.handle = handle; this.art = art; this.titleBlock = titleBlock; this.heart = heart;
        this.more = more; this.scrubber = scrubber; this.prev = prev; this.play = play; this.next = next; this.capsule = capsule;
        setClipChildren(false);
        for (View v : new View[] {backdrop, handle, art, titleBlock, heart, more, scrubber, prev, play, next, capsule}) addView(v);
    }

    public void setListener(Listener l) { listener = l; }

    public NowPlayingGeometry geometry() { return geometry; }

    /** System bar insets in px. */
    public void setInsets(int topPx, int bottomPx) {
        if (topPx == insetTop && bottomPx == insetBottom) return;
        insetTop = topPx; insetBottom = bottomPx;
        requestLayout();
    }

    /** Back to rest after being shown again. */
    public void reset() {
        animate().cancel();
        dismissing = false;
        dragging = false;
        setTranslationY(0);
        if (listener != null) listener.onDismissProgress(0);
    }

    private int px(float dp) { return Math.round(dp * density); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        if (w <= 0 || h <= 0) return;
        geometry = NowPlayingGeometry.compute(Math.round(w / density), Math.round(h / density),
                Math.round(insetTop / density), Math.round(insetBottom / density));
        backdrop.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        measureBox(handle, geometry.handleArea, 0);
        measureBox(art, null, px(geometry.art.w));
        measureBox(titleBlock, geometry.titleBlock, 0);
        measureBox(heart, geometry.heart, 0);
        measureBox(more, geometry.more, 0);
        measureBox(scrubber, geometry.scrubber, 0);
        measureBox(prev, geometry.previous, 0);
        measureBox(play, geometry.playPause, 0);
        measureBox(next, geometry.next, 0);
        measureBox(capsule, geometry.capsule, 0);
    }

    private void measureBox(View v, NowPlayingGeometry.Box b, int square) {
        int bw = square > 0 ? square : px(b.w), bh = square > 0 ? square : px(b.h);
        v.measure(MeasureSpec.makeMeasureSpec(Math.max(bw, v.getMinimumWidth()), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(bh, v.getMinimumHeight()), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (geometry == null) return;
        backdrop.layout(0, 0, r - l, b - t);
        NowPlayingGeometry g = geometry;
        place(handle, g.handleArea);
        art.layout(px(g.art.x), px(g.art.y), px(g.art.x) + art.getMeasuredWidth(), px(g.art.y) + art.getMeasuredHeight());
        place(titleBlock, g.titleBlock);
        place(heart, g.heart);
        place(more, g.more);
        place(scrubber, g.scrubber);
        place(prev, g.previous);
        place(play, g.playPause);
        place(next, g.next);
        place(capsule, g.capsule);
    }

    /** Centres a child on its box (children may be a little larger than the box for touch). */
    private void place(View v, NowPlayingGeometry.Box box) {
        int cx = px(box.x) + px(box.w) / 2, cy = px(box.y) + px(box.h) / 2;
        int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
        if (v == titleBlock || v == scrubber || v == handle || v == capsule) {
            v.layout(px(box.x), px(box.y), px(box.x) + w, px(box.y) + h);
        } else {
            v.layout(cx - w / 2, cy - h / 2, cx - w / 2 + w, cy - h / 2 + h);
        }
    }

    // ---------------------------------------------------------------- dismiss gesture

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (dismissing) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX(); downRawY = e.getRawY();
                dragging = false;
                startedOnScrubber = e.getX() >= scrubber.getLeft() && e.getX() <= scrubber.getRight()
                        && e.getY() >= scrubber.getTop() && e.getY() <= scrubber.getBottom();
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                break;
            case MotionEvent.ACTION_MOVE:
                if (velocity != null) velocity.addMovement(e);
                if (!dragging && !startedOnScrubber) {
                    float dy = e.getRawY() - downRawY, dx = e.getRawX() - downRawX;
                    if (dy > slop && dy > Math.abs(dx) * 1.2f) { dragging = true; return true; }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                recycle();
                break;
            default:
                break;
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (dismissing) return true;
        if (velocity != null) velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX(); downRawY = e.getRawY();
                if (velocity == null) velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                startedOnScrubber = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = e.getRawY() - downRawY;
                if (!dragging && dy > slop && dy > Math.abs(e.getRawX() - downRawX)) dragging = true;
                if (dragging) {
                    float offset = DismissGesture.follow(dy - slop, density);
                    setTranslationY(offset);
                    if (listener != null) listener.onDismissProgress(DismissGesture.progress(offset, getHeight()));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) { recycle(); return true; }
                float v = 0;
                if (velocity != null) { velocity.computeCurrentVelocity(1000); v = velocity.getYVelocity(); }
                dragging = false;
                recycle();
                float offset = getTranslationY();
                boolean dismiss = e.getActionMasked() == MotionEvent.ACTION_UP && DismissGesture.shouldDismiss(offset, v, getHeight(), density);
                settle(dismiss, v);
                return true;
            }
            default:
                return true;
        }
    }

    private void recycle() { if (velocity != null) { velocity.recycle(); velocity = null; } }

    private void settle(boolean dismiss, float velocityPxS) {
        animate().cancel();
        if (dismiss) {
            dismissing = true;
            float remaining = getHeight() - getTranslationY();
            animate().translationY(getHeight()).setDuration(DismissGesture.settleDurationMs(remaining, velocityPxS))
                    .setInterpolator(Motion.accelerate()).withEndAction(() -> { if (listener != null) listener.onDismissed(); }).start();
        } else {
            animate().translationY(0).setDuration(Motion.GENTLE_SPRING_MS).setInterpolator(Motion.gentleSpring())
                    .withEndAction(() -> { if (listener != null) listener.onDismissProgress(0); }).start();
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animate().cancel();
        recycle();
    }
}
