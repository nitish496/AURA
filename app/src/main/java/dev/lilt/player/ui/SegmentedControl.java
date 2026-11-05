package dev.lilt.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;

/**
 * iOS-style segmented control (Week | Month | Year): fill track radius 10, a raised selected
 * segment that slides with the spring, labels 13sp medium (semibold when selected). Visual
 * height 36dp inside a 48dp touch target.
 */
public class SegmentedControl extends FrameLayout {
    public interface Listener { void onSelected(int index); }

    private final ColorRoles roles;
    private final LinearLayout labels;
    private final View thumb;
    private int selected;
    private float position;
    private Listener listener;

    public SegmentedControl(Context c, CharSequence... segments) { this(c, Theme.colors(c), segments); }

    public SegmentedControl(Context c, ColorRoles roles, CharSequence... segments) {
        super(c);
        this.roles = roles;
        int vInset = (Ui.dp(c, DesignTokens.TOUCH_TARGET) - Ui.dp(c, DesignTokens.SEGMENTED_HEIGHT)) / 2;
        setPadding(0, vInset, 0, vInset);
        FrameLayout track = new FrameLayout(c);
        track.setBackground(Ui.roundRect(roles.fill, Ui.dpf(c, 10)));
        int p = Ui.dp(c, 2);
        track.setPadding(p, p, p, p);
        thumb = new View(c);
        thumb.setBackground(Ui.roundRect(roles.dark ? roles.fillStrong : 0xFFFFFFFF, Ui.dpf(c, 8)));
        thumb.setElevation(Ui.dpf(c, 1));
        track.addView(thumb, new LayoutParams(0, LayoutParams.MATCH_PARENT));
        labels = Ui.row(c);
        for (int i = 0; i < segments.length; i++) {
            TextView t = Type.text(c, TypeScale.CAPTION1.weight(500), roles.textPrimary, segments[i]);
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            final int index = i;
            t.setOnClickListener(v -> { if (index != selected) { Haptics.tick(v); select(index, true); if (listener != null) listener.onSelected(index); } });
            labels.addView(t, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        }
        labels.setElevation(Ui.dpf(c, 2)); // labels must draw above the elevated thumb
        track.addView(labels, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(track, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, DesignTokens.SEGMENTED_HEIGHT), Gravity.CENTER_VERTICAL));
        select(0, false);
    }

    public void setListener(Listener l) { listener = l; }
    public int selected() { return selected; }

    public void select(int index, boolean animate) {
        selected = index;
        for (int i = 0; i < labels.getChildCount(); i++) {
            TextView t = (TextView) labels.getChildAt(i);
            t.setTypeface(Type.typeface(getContext(), i == index ? TypeScale.HEADLINE : TypeScale.CAPTION1));
            t.setSelected(i == index);
        }
        if (animate) Motion.animate(position, index, Motion.SPRING_MS, Motion.spring(), v -> { position = v; thumb.setTranslationX(position * thumb.getWidth()); });
        else { position = index; thumb.setTranslationX(position * thumb.getWidth()); }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int n = Math.max(1, labels.getChildCount());
        int w = labels.getWidth() / n;
        if (thumb.getWidth() != w) { thumb.getLayoutParams().width = w; thumb.post(thumb::requestLayout); }
        thumb.setTranslationX(position * w);
    }
}
