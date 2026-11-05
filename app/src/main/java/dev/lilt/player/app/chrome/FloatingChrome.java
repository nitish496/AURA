package dev.lilt.player.app.chrome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.app.Tab;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.Ui;

/**
 * The floating navigation layer: glass tab bar, glass Search control and the mini-player
 * accessory. Two independent states drive the geometry (see docs/DESIGN.md "Chrome"):
 *
 * <ul>
 *   <li><b>minimised</b> (scroll-driven): the tab bar collapses to a 48dp circle with the active
 *       tab, the mini player docks between the circle and Search, Search shrinks to 48dp.</li>
 *   <li><b>searching</b> (Search tab): the tab bar becomes a 48dp "back" circle and the Search
 *       circle widens into the text field; the mini player rises above them and lifts with the
 *       keyboard.</li>
 * </ul>
 * The view covers the whole window but only its children take touches.
 */
public final class FloatingChrome extends ViewGroup {
    public final TabBar tabBar;
    public final SearchPill search;
    public final MiniPlayer mini;

    private final int margin, gap, tabH, tabMin, searchH, miniH, miniDockedH;
    private int bottomInset, keyboardInset;
    private boolean miniVisible, minimised, searching;
    private float min, field; // animated 0..1
    private ValueAnimator minAnim, fieldAnim;

    public FloatingChrome(Context c, ColorRoles roles) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
        margin = Ui.dp(c, DesignTokens.MARGIN);
        gap = Ui.dp(c, DesignTokens.CHROME_GAP);
        tabH = Ui.dp(c, DesignTokens.TAB_BAR_HEIGHT);
        tabMin = Ui.dp(c, DesignTokens.TAB_BAR_MIN);
        searchH = Ui.dp(c, DesignTokens.SEARCH_BUTTON);
        miniH = Ui.dp(c, DesignTokens.MINI_PLAYER);
        miniDockedH = Ui.dp(c, 48);
        mini = new MiniPlayer(c, roles);
        tabBar = new TabBar(c, roles);
        search = new SearchPill(c, roles);
        addView(mini);
        addView(tabBar);
        addView(search);
        mini.setVisibility(GONE);
    }

    public void setBackdrop(View backdrop) {
        mini.setBackdrop(backdrop);
        tabBar.setBackdrop(backdrop);
        search.setBackdrop(backdrop);
    }

    /** Navigation-bar inset in px (the bar sits this far above the window bottom, plus a small margin). */
    public void setBottomInset(int px) { bottomInset = px; requestLayout(); }

    /** Extra lift while the keyboard is open (search field stays above it). */
    public void setKeyboardInset(int px) { if (px != keyboardInset) { keyboardInset = px; requestLayout(); } }

    public void setMiniVisible(boolean visible) {
        if (visible == miniVisible) return;
        miniVisible = visible;
        if (visible) { mini.setVisibility(VISIBLE); mini.setAlpha(0f); mini.animate().alpha(1f).setDuration(Motion.BASE).start(); }
        else { mini.animate().cancel(); mini.setVisibility(GONE); }
        requestLayout();
    }

    public boolean miniVisible() { return miniVisible; }
    public boolean minimised() { return minimised; }
    public boolean searching() { return searching; }

    public void setMinimised(boolean value, boolean animate) {
        if (value == minimised) return;
        minimised = value;
        if (minAnim != null) minAnim.cancel();
        float from = min, to = value ? 1f : 0f;
        if (!animate) { apply(to, field); return; }
        minAnim = Motion.animate(from, to, Motion.SPRING_MS, Motion.spring(), p -> apply(p, field));
    }

    public void setSearching(boolean value, boolean animate) {
        if (value == searching) return;
        searching = value;
        if (fieldAnim != null) fieldAnim.cancel();
        float from = field, to = value ? 1f : 0f;
        if (!animate) { apply(min, to); return; }
        fieldAnim = Motion.animate(from, to, Motion.SPRING_MS, Motion.spring(), p -> apply(min, p));
    }

    private void apply(float minimise, float fieldProgress) {
        min = minimise;
        field = fieldProgress;
        float collapse = Math.max(min, field);
        tabBar.setCollapse(collapse);
        search.setField(field);
        mini.setDocked(min * (1f - field));
        requestLayout();
    }

    /** Space (px) the expanded chrome covers from the window bottom, for content padding. */
    public int coveredHeight(int navInset, boolean withMini) {
        return navInset + gap / 2 + tabH + (withMini ? miniH + gap : 0) + gap * 2;
    }

    public int baseMargin() { return Math.max(bottomInset, Ui.dp(getContext(), 16)) + Ui.dp(getContext(), 4); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        float collapse = Math.max(min, field);
        int exact = MeasureSpec.EXACTLY;
        int tabW = Math.round(lerp(w - 2 * margin - searchH - gap, tabMin, collapse));
        int tabHeight = Math.round(lerp(tabH, tabMin, collapse));
        tabBar.measure(MeasureSpec.makeMeasureSpec(tabW, exact), MeasureSpec.makeMeasureSpec(tabHeight, exact));
        int sh = Math.round(lerp(searchH, tabMin, collapse));
        int sw = Math.round(lerp(sh, w - 2 * margin - tabMin - gap, field));
        search.measure(MeasureSpec.makeMeasureSpec(sw, exact), MeasureSpec.makeMeasureSpec(sh, exact));
        if (miniVisible) {
            float docked = min * (1f - field);
            int mw = Math.round(lerp(w - 2 * margin, w - 2 * (margin + tabMin + gap), docked));
            int mh = Math.round(lerp(miniH, miniDockedH, docked));
            mini.measure(MeasureSpec.makeMeasureSpec(mw, exact), MeasureSpec.makeMeasureSpec(mh, exact));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        int bottomEdge = h - Math.max(baseMargin(), keyboardInset > 0 ? keyboardInset + gap : 0);
        float collapse = Math.max(min, field);
        int tabHeight = tabBar.getMeasuredHeight();
        tabBar.layout(margin, bottomEdge - tabHeight, margin + tabBar.getMeasuredWidth(), bottomEdge);
        int sh = search.getMeasuredHeight(), sw = search.getMeasuredWidth();
        search.layout(w - margin - sw, bottomEdge - sh, w - margin, bottomEdge);
        if (miniVisible) {
            float docked = min * (1f - field);
            int mh = mini.getMeasuredHeight(), mw = mini.getMeasuredWidth();
            int aboveTop = bottomEdge - tabHeight - gap - miniH;      // expanded: above the bar row
            int dockedTop = bottomEdge - miniDockedH;                  // docked: inline with the circles
            int top = Math.round(lerp(aboveTop, dockedTop, docked));
            int left = Math.round(lerp(margin, margin + tabMin + gap, docked));
            mini.layout(left, top, left + mw, top + mh);
        }
    }

    /** Top edge (px from the chrome's top) of the highest chrome element, for toasts. */
    public int topEdge() {
        int top = getHeight();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == VISIBLE && v.getHeight() > 0) top = Math.min(top, v.getTop());
        }
        return top;
    }

    private static float lerp(float a, float b, float p) { return a + (b - a) * p; }
}
