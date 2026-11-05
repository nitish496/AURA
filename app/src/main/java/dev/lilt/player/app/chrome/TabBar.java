package dev.lilt.player.app.chrome;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.app.Tab;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * Floating glass capsule with the Home / Browse / Library tabs. The selected tab sits on a lens
 * (a lighter capsule) that slides to the next tab. When minimised (scrolling down, or while the
 * search field is open) it collapses to a 48dp circle that shows one icon; tapping it expands
 * the bar again. Geometry is driven by {@link FloatingChrome}; this view only lays out its
 * contents for whatever size it is given.
 */
public final class TabBar extends GlassView {
    public interface Listener {
        void onTab(Tab tab);
        /** The collapsed circle was tapped. */
        void onExpand();
    }

    private static final Tab[] TABS = {Tab.HOME, Tab.BROWSE, Tab.LIBRARY};
    private static final int[] OUTLINE = {R.drawable.ic_home, R.drawable.ic_browse, R.drawable.ic_library};
    private static final int[] FILL = {R.drawable.ic_home_fill, R.drawable.ic_browse_fill, R.drawable.ic_library_fill};
    private static final String[] LABELS = {"Home", "Browse", "Library"};

    private final ColorRoles roles;
    private final Cell[] cells = new Cell[3];
    private final View lens;
    private final ImageView collapsedIcon;
    private final int pad;
    private Listener listener;
    private int selected = -1, collapsedTab = 0;
    private boolean collapsedAccent = true;
    private float collapse;

    private final class Cell extends LinearLayout {
        final ImageView icon;
        final TextView label;
        Cell(Context c, int index) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            icon = new ImageView(c);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(icon, new LayoutParams(Ui.dp(c, DesignTokens.ICON), Ui.dp(c, DesignTokens.ICON)));
            label = Type.text(c, TypeScale.TAB, roles.textPrimary, LABELS[index]);
            label.setGravity(Gravity.CENTER);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(c, 2);
            addView(label, lp);
            setClickable(true);
            setContentDescription(LABELS[index]);
            setOnClickListener(v -> { if (listener != null) listener.onTab(TABS[index]); });
            show(false, index);
        }
        void show(boolean on, int index) {
            icon.setImageDrawable(Icons.get(getContext(), on ? FILL[index] : OUTLINE[index], on ? roles.accent : roles.textPrimary));
            label.setTextColor(on ? roles.accent : roles.textPrimary);
            setSelected(on);
        }
    }

    public TabBar(Context c, ColorRoles roles) {
        super(c, roles);
        this.roles = roles;
        pad = Ui.dp(c, 4);
        setClipChildren(false);
        lens = new View(c);
        lens.setBackground(Ui.roundRect(roles.glassLens, Ui.dpf(c, 999)));
        addView(lens);
        for (int i = 0; i < 3; i++) { cells[i] = new Cell(c, i); addView(cells[i]); }
        collapsedIcon = new ImageView(c);
        collapsedIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        collapsedIcon.setAlpha(0f);
        addView(collapsedIcon);
        setClickable(true);
        setOnClickListener(v -> { if (collapse > 0.5f && listener != null) listener.onExpand(); });
        setCollapsedIcon(Tab.HOME, true);
    }

    public void setListener(Listener l) { listener = l; }

    /** Selects a tab (null or SEARCH: none). */
    public void select(Tab tab, boolean animate) {
        int index = indexOf(tab);
        int old = selected;
        selected = index;
        for (int i = 0; i < 3; i++) cells[i].show(i == index, i);
        lens.setVisibility(index < 0 ? INVISIBLE : VISIBLE);
        if (index >= 0) {
            int cellW = cellWidth();
            lens.animate().cancel();
            if (animate && old >= 0 && old != index && cellW > 0 && Motion.enabled()) {
                lens.setTranslationX((old - index) * cellW);
                lens.animate().translationX(0).setDuration(Motion.SPRING_MS).setInterpolator(Motion.spring()).start();
            } else lens.setTranslationX(0);
            requestLayout();
        }
        if (index >= 0) setCollapsedIcon(tab, true);
    }

    /** The icon shown when collapsed: Ember fill for "current tab" (scrolled), plain outline for "go back to" (search). */
    public void setCollapsedIcon(Tab tab, boolean accent) {
        int i = Math.max(0, indexOf(tab));
        collapsedTab = i;
        collapsedAccent = accent;
        collapsedIcon.setImageDrawable(Icons.get(getContext(), accent ? FILL[i] : OUTLINE[i], accent ? roles.accent : roles.textPrimary));
        collapsedIcon.setContentDescription(accent ? LABELS[i] + ", tab bar minimised" : "Back to " + LABELS[i]);
    }

    /** 0 = full bar, 1 = single circle. */
    public void setCollapse(float p) {
        collapse = p;
        float items = clamp(1f - p * 1.9f), single = clamp(p * 1.9f - 0.9f);
        for (Cell c : cells) c.setAlpha(items);
        lens.setAlpha(items);
        collapsedIcon.setAlpha(single);
    }

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(1, v); }

    private static int indexOf(Tab t) {
        for (int i = 0; i < TABS.length; i++) if (TABS[i] == t) return i;
        return -1;
    }

    private int cellWidth() { return Math.max(0, (getWidth() - 2 * pad) / 3); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        int cw = Math.max(0, (w - 2 * pad) / 3), ch = Math.max(0, h - 2 * pad);
        int cwSpec = MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), chSpec = MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY);
        lens.measure(cwSpec, chSpec);
        for (Cell c : cells) c.measure(cwSpec, chSpec);
        int icon = MeasureSpec.makeMeasureSpec(Ui.dp(getContext(), DesignTokens.ICON), MeasureSpec.EXACTLY);
        collapsedIcon.measure(icon, icon);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        int cw = Math.max(0, (w - 2 * pad) / 3);
        for (int i = 0; i < 3; i++) cells[i].layout(pad + i * cw, pad, pad + (i + 1) * cw, h - pad);
        int s = Math.max(0, selected);
        lens.layout(pad + s * cw, pad, pad + (s + 1) * cw, h - pad);
        int ic = collapsedIcon.getMeasuredWidth();
        collapsedIcon.layout((w - ic) / 2, (h - ic) / 2, (w + ic) / 2, (h + ic) / 2);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        // While collapsed the whole circle is one button.
        return collapse > 0.5f || super.onInterceptTouchEvent(e);
    }
}
