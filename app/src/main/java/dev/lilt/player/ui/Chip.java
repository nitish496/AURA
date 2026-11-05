package dev.lilt.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import java.util.ArrayList;
import java.util.List;

/**
 * Filter chip: 32dp capsule inside a 48dp touch target, label {@link TypeScale#CHIP}.
 * Selected chips invert (primary-text fill, canvas label); there is no accent on chips.
 * Use {@link Row} for a single-select, horizontally scrolling set.
 */
public class Chip extends TextView {
    private final ColorRoles roles;

    public Chip(Context c, CharSequence label) { this(c, Theme.colors(c), label); }

    public Chip(Context c, ColorRoles roles, CharSequence label) {
        super(c);
        this.roles = roles;
        Type.apply(this, TypeScale.CHIP);
        Type.singleLine(this);
        setText(label);
        setGravity(Gravity.CENTER);
        setMinHeight(Ui.dp(c, DesignTokens.TOUCH_TARGET));
        setClickable(true);
        setFocusable(true);
        apply();
    }

    @Override public void setSelected(boolean selected) {
        boolean changed = selected != isSelected();
        super.setSelected(selected);
        if (changed && roles != null) apply();
    }

    private void apply() {
        boolean on = isSelected();
        int inset = (Ui.dp(getContext(), DesignTokens.TOUCH_TARGET) - Ui.dp(getContext(), DesignTokens.CHIP_HEIGHT)) / 2;
        setBackground(Ui.pressableInset(roles, on ? roles.textPrimary : roles.fill, -1, inset));
        setTextColor(on ? roles.canvas : roles.textPrimary);
        // setBackground() replaces the padding with the drawable's own (0 horizontally): set it last.
        setPadding(Ui.dp(getContext(), 14), 0, Ui.dp(getContext(), 14), 0);
    }

    /** Horizontally scrolling, single-select chip row with 16dp side padding and 8dp gaps. */
    public static class Row extends HorizontalScrollView {
        public interface Listener { void onSelected(int index); }
        private final LinearLayout strip;
        private final List<Chip> chips = new ArrayList<>();
        private Listener listener;
        private int selected = -1;

        public Row(Context c) {
            super(c);
            setHorizontalScrollBarEnabled(false);
            setClipToPadding(false);
            int m = Ui.dp(c, DesignTokens.MARGIN);
            strip = Ui.row(c);
            strip.setPadding(m, 0, m, 0);
            addView(strip);
        }

        public Row setChips(ColorRoles roles, CharSequence... labels) {
            strip.removeAllViews();
            chips.clear();
            for (int i = 0; i < labels.length; i++) {
                Chip chip = new Chip(getContext(), roles, labels[i]);
                final int index = i;
                chip.setOnClickListener(v -> { if (index != selected) { Haptics.tick(v); select(index); if (listener != null) listener.onSelected(index); } });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(getContext(), DesignTokens.TOUCH_TARGET));
                if (i > 0) p.setMarginStart(Ui.dp(getContext(), 8));
                strip.addView(chip, p);
                chips.add(chip);
            }
            if (!chips.isEmpty()) select(Math.max(0, Math.min(selected, chips.size() - 1)));
            return this;
        }

        public void select(int index) {
            selected = index;
            for (int i = 0; i < chips.size(); i++) chips.get(i).setSelected(i == index);
        }

        public int selected() { return selected; }
        public void setListener(Listener l) { listener = l; }
    }
}
