package dev.lilt.player.ui;

import android.view.View;
import android.widget.AbsListView;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports vertical scroll offset and delta for any scrolling container, so chrome (top bar,
 * tab bar) can react without knowing whether the screen is a ScrollView or a ListView.
 *
 * <ul>
 *   <li>ScrollView / NestedScrolling views: uses {@link View#setOnScrollChangeListener}.</li>
 *   <li>ListView / GridView: estimates the offset from the first visible child and keeps
 *       measured row heights, so deltas stay smooth while recycling.</li>
 * </ul>
 * One tracker per scroll container; add as many listeners as needed.
 */
public final class ScrollTracker {
    /** {@code scrollY} from the top (px, ≥ 0) and {@code dy} since the last event. */
    public interface Listener { void onScroll(int scrollY, int dy); }

    private static final int KNOWN = 8; // positions whose individual heights are remembered
    private final List<Listener> listeners = new ArrayList<>(2);
    private int lastY;

    private ScrollTracker() {}

    /** For views that scroll themselves (ScrollView). Replaces any OnScrollChangeListener. */
    public static ScrollTracker attach(View scrollingView) {
        ScrollTracker t = new ScrollTracker();
        scrollingView.setOnScrollChangeListener((v, x, y, ox, oy) -> t.dispatch(Math.max(0, y)));
        return t;
    }

    /**
     * For ListView / GridView. Replaces the list's OnScrollListener; pass your own as
     * {@code chained} to keep receiving scroll-state callbacks.
     */
    public static ScrollTracker attach(AbsListView list, AbsListView.OnScrollListener chained) {
        ScrollTracker t = new ScrollTracker();
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            private final android.util.SparseIntArray heights = new android.util.SparseIntArray();
            @Override public void onScrollStateChanged(AbsListView view, int state) {
                if (chained != null) chained.onScrollStateChanged(view, state);
            }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                if (chained != null) chained.onScroll(view, first, visible, total);
                if (view.getChildCount() == 0) return;
                for (int i = 0; i < view.getChildCount() && first + i < KNOWN; i++) heights.put(first + i, view.getChildAt(i).getHeight());
                // Headers (large title, hero) can differ in height; rows below them are uniform.
                int row = view.getChildAt(view.getChildCount() - 1).getHeight();
                int y = view.getPaddingTop() - view.getChildAt(0).getTop();
                for (int i = 0; i < Math.min(first, KNOWN); i++) y += heights.get(i, row);
                y += Math.max(0, first - KNOWN) * row;
                t.dispatch(Math.max(0, y));
            }
        });
        return t;
    }

    public ScrollTracker add(Listener l) { listeners.add(l); return this; }

    public int scrollY() { return lastY; }

    private void dispatch(int y) {
        int dy = y - lastY;
        lastY = y;
        for (int i = 0; i < listeners.size(); i++) listeners.get(i).onScroll(y, dy);
    }
}
