package dev.lilt.player.ui;

import android.content.Context;
import android.widget.ScrollView;

/** A ScrollView that never grows taller than {@link #setMaxHeight(int)}; used for sheet, menu and dialog bodies in short windows. */
public final class CappedScrollView extends ScrollView {
    private int maxHeight = Integer.MAX_VALUE;

    public CappedScrollView(Context c) {
        super(c);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void setMaxHeight(int px) {
        if (px == maxHeight) return;
        maxHeight = Math.max(1, px);
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int size = MeasureSpec.getSize(heightSpec);
        int mode = MeasureSpec.getMode(heightSpec);
        int cap = mode == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(size, maxHeight);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
    }
}
