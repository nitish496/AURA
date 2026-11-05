package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;

/**
 * Navigation / settings row, 52dp (64dp with a subtitle): optional 24dp leading icon (Ember
 * in Library, secondary in Settings), title (16/21), optional subtitle, trailing value
 * (tertiary, tabular), and a trailing accessory: chevron, {@link Toggle}, check, or none.
 * Draws its own inset hairline separator.
 */
public class ListRow extends LinearLayout {
    public static final int NONE = 0, CHEVRON = 1, CHECK = 2;

    private final ColorRoles roles;
    private final ImageView icon, accessory;
    private final TextView title, subtitle, value;
    private final Paint sepPaint = new Paint();
    private boolean separator;
    private Toggle toggle;

    public ListRow(Context c) { this(c, Theme.colors(c)); }

    public ListRow(Context c, ColorRoles roles) {
        super(c);
        this.roles = roles;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        setMinimumHeight(Ui.dp(c, DesignTokens.ROW_LIST));
        setPaddingRelative(Ui.dp(c, DesignTokens.MARGIN), 0, Ui.dp(c, 12), 0);
        setBackground(Ui.rowRipple(roles));
        sepPaint.setColor(roles.separator);
        icon = new ImageView(c);
        icon.setVisibility(GONE);
        LayoutParams ip = new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        ip.setMarginEnd(Ui.dp(c, 16));
        addView(icon, ip);
        LinearLayout text = Ui.column(c);
        title = Type.text(c, TypeScale.CALLOUT, roles.textPrimary);
        Type.singleLine(title);
        subtitle = Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary);
        Type.maxLines(subtitle, 2);
        subtitle.setVisibility(GONE);
        text.addView(title);
        text.addView(subtitle);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        value = Type.text(c, TypeScale.CALLOUT, roles.textTertiary);
        Type.tabular(value);
        value.setVisibility(GONE);
        LayoutParams vp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        vp.setMarginStart(Ui.dp(c, 8));
        addView(value, vp);
        accessory = new ImageView(c);
        accessory.setVisibility(GONE);
        LayoutParams ap = new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        ap.setMarginStart(Ui.dp(c, 4));
        addView(accessory, ap);
        setClickable(true);
    }

    public ListRow title(CharSequence t) { title.setText(t); return this; }

    public ListRow subtitle(CharSequence s) {
        subtitle.setText(s);
        subtitle.setVisibility(s == null ? GONE : VISIBLE);
        setMinimumHeight(Ui.dp(getContext(), s == null ? DesignTokens.ROW_LIST : DesignTokens.ROW_LIST_TALL));
        return this;
    }

    /** Leading icon; {@code accent} = Ember (Library categories), else secondary (Settings). */
    public ListRow icon(int res, boolean accent) {
        icon.setImageDrawable(Icons.get(getContext(), res, accent ? roles.accent : roles.textSecondary));
        icon.setVisibility(VISIBLE);
        return this;
    }

    /** Trailing value: counts ("1,248"), settings values ("Off", "6 s"). */
    public ListRow value(CharSequence v) { value.setText(v); value.setVisibility(v == null ? GONE : VISIBLE); return this; }

    public ListRow accessory(int kind) {
        int res = kind == CHEVRON ? R.drawable.ic_chevron_right : kind == CHECK ? R.drawable.ic_check : 0;
        accessory.setVisibility(res == 0 ? GONE : VISIBLE);
        if (res != 0) accessory.setImageDrawable(Icons.get(getContext(), res, kind == CHECK ? roles.accent : roles.textTertiary));
        return this;
    }

    /** Adds a trailing {@link Toggle}; tapping the row flips it. */
    public Toggle toggle(boolean on) {
        if (toggle == null) {
            toggle = new Toggle(getContext(), roles);
            addView(toggle, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            setOnClickListener(v -> toggle.performClick());
        }
        toggle.setOn(on, false, false);
        return toggle;
    }

    /** Hairline at the bottom, inset to the text start. */
    public ListRow separator(boolean show) { separator = show; invalidate(); return this; }

    public TextView titleView() { return title; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!separator) return;
        int start = getPaddingStart() + (icon.getVisibility() == VISIBLE ? Ui.dp(getContext(), 40) : 0);
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float h = Math.max(1f, Ui.dpf(getContext(), 0.5f));
        if (rtl) canvas.drawRect(0, getHeight() - h, getWidth() - start, getHeight(), sepPaint);
        else canvas.drawRect(start, getHeight() - h, getWidth(), getHeight(), sepPaint);
    }
}
