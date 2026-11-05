package dev.lilt.player.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import dev.lilt.player.DesignTokens;
import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal shelf without RecyclerView: a HorizontalScrollView whose cells are created once
 * and re-bound in place, with artwork loaded only for visible (and next) cells via
 * {@link Adapter#onVisible}. 16dp leading/trailing padding, 12dp gaps, snaps to a card start
 * after a fling. Keep shelves to about 25 items; "See All" opens the full list.
 *
 * <pre>{@code
 * shelf.setAdapter(new ShelfView.Adapter() {
 *   public int count() { return albums.size(); }
 *   public View create(ViewGroup parent) { return new AlbumCard(ctx, cardDp); }
 *   public void bind(View v, int i) { ((AlbumCard) v).bind(albums.get(i).title, albums.get(i).artist); }
 *   public void onVisible(View v, int i) { artwork.load(((AlbumCard) v).cover(), albums.get(i)); }
 * });
 * }</pre>
 */
public class ShelfView extends HorizontalScrollView {
    public interface Adapter {
        int count();
        View create(ViewGroup parent);
        void bind(View view, int position);
        /** Called once per bind when the cell first comes within one card of the viewport. */
        default void onVisible(View view, int position) {}
    }

    private final LinearLayout strip;
    private final List<View> cells = new ArrayList<>();
    private final List<Boolean> shown = new ArrayList<>();
    private Adapter adapter;
    private boolean snap = true;
    private final Runnable settle = this::snapToNearest;

    public ShelfView(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        int m = Ui.dp(c, DesignTokens.MARGIN);
        setPadding(m, 0, m, 0);
        strip = Ui.row(c);
        strip.setGravity(android.view.Gravity.TOP);
        addView(strip);
    }

    public void setSnapToItems(boolean on) { snap = on; }

    /** Binds (and creates as needed) all cells; existing cells are re-used. */
    public void setAdapter(Adapter a) {
        adapter = a;
        int n = a == null ? 0 : a.count();
        for (int i = 0; i < n; i++) {
            if (i >= cells.size()) {
                View v = a.create(strip);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                if (i > 0) p.setMarginStart(Ui.dp(getContext(), DesignTokens.GUTTER));
                strip.addView(v, p);
                cells.add(v);
                shown.add(false);
            }
            cells.get(i).setVisibility(VISIBLE);
            a.bind(cells.get(i), i);
            shown.set(i, false);
        }
        for (int i = n; i < cells.size(); i++) cells.get(i).setVisibility(GONE);
        post(this::loadVisible);
    }

    public View cell(int position) { return cells.get(position); }

    @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        loadVisible();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        loadVisible();
    }

    private void loadVisible() {
        if (adapter == null || getWidth() == 0) return;
        int left = getScrollX() - getPaddingLeft(), right = left + getWidth();
        int n = Math.min(adapter.count(), cells.size());
        for (int i = 0; i < n; i++) {
            View v = cells.get(i);
            int slack = v.getWidth();
            if (!shown.get(i) && v.getRight() + slack > left && v.getLeft() - slack < right) {
                shown.set(i, true);
                adapter.onVisible(v, i);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        boolean r = super.onTouchEvent(e);
        if (snap && (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            removeCallbacks(settle);
            postDelayed(settle, 120);
        }
        return r;
    }

    private int lastX = -1;

    private void snapToNearest() {
        int x = getScrollX();
        if (x != lastX) { lastX = x; postDelayed(settle, 60); return; } // still flinging
        lastX = -1;
        if (cells.isEmpty() || x >= strip.getWidth() - getWidth() + getPaddingLeft() + getPaddingRight() - 2) return;
        int best = 0, bestDist = Integer.MAX_VALUE;
        for (View v : cells) {
            if (v.getVisibility() != VISIBLE) continue;
            int d = Math.abs(v.getLeft() - x);
            if (d < bestDist) { bestDist = d; best = v.getLeft(); }
        }
        if (bestDist > 0) smoothScrollTo(best, 0);
    }
}
