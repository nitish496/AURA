package dev.lilt.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;

/**
 * Section header: Title 2 (22/28 Inter Display Bold), left aligned at the 16dp margin, with an
 * inline 20dp tertiary chevron when the section opens a full list ("Recently Played ›").
 * Optional footnote subtitle below ("This week"). The whole header is the tap target (≥48dp).
 */
public class SectionHeader extends LinearLayout {
    private final TextView title, subtitle;
    private final ImageView chevron;

    public SectionHeader(Context c, CharSequence text) { this(c, Theme.colors(c), text); }

    public SectionHeader(Context c, ColorRoles roles, CharSequence text) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(c, DesignTokens.TOUCH_TARGET));
        int m = Ui.dp(c, DesignTokens.MARGIN);
        setPaddingRelative(m, 0, m, 0);
        LinearLayout line = Ui.row(c);
        title = Type.text(c, TypeScale.TITLE2, roles.textPrimary, text);
        Type.singleLine(title);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        line.addView(title, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f));
        chevron = new ImageView(c);
        chevron.setImageDrawable(Icons.get(c, R.drawable.ic_chevron_right, roles.textTertiary));
        chevron.setVisibility(GONE);
        LayoutParams cp = new LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
        cp.setMarginStart(Ui.dp(c, 2));
        line.addView(chevron, cp);
        addView(line);
        subtitle = Type.text(c, TypeScale.FOOTNOTE, roles.textTertiary);
        subtitle.setVisibility(GONE);
        addView(subtitle);
    }

    /** Shows the chevron and makes the header open the full list. */
    public SectionHeader onSeeAll(Runnable action) {
        chevron.setVisibility(action == null ? GONE : VISIBLE);
        setClickable(action != null);
        setOnClickListener(action == null ? null : v -> action.run());
        setContentDescription(action == null ? null : title.getText() + ", see all");
        return this;
    }

    public SectionHeader subtitle(CharSequence s) { subtitle.setText(s); subtitle.setVisibility(s == null ? GONE : VISIBLE); return this; }
    public TextView titleView() { return title; }
}
