package dev.lilt.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;

/**
 * Capsule button, 48dp tall, label {@link TypeScale#BUTTON}, optional 20dp leading icon.
 * <ul>
 *   <li>{@link #PRIMARY}: fill + Ember label. The one main action of a screen (Play).</li>
 *   <li>{@link #SECONDARY}: fill + primary label (Shuffle, Go to Library).</li>
 *   <li>{@link #FILLED}: Ember fill + dark label. Only for blocking calls to action (Allow Access).</li>
 *   <li>{@link #TEXT}: no fill, Ember label (Clear, Try Again, Browse the catalogue).</li>
 * </ul>
 */
public class PillButton extends TextView {
    public static final int PRIMARY = 0, SECONDARY = 1, FILLED = 2, TEXT = 3;

    private final ColorRoles roles;
    private final int kind;

    public PillButton(Context c, int kind, CharSequence label, int iconRes) { this(c, Theme.colors(c), kind, label, iconRes); }

    public PillButton(Context c, ColorRoles roles, int kind, CharSequence label, int iconRes) {
        super(c);
        this.roles = roles;
        this.kind = kind;
        Type.apply(this, TypeScale.BUTTON);
        Type.singleLine(this);
        setText(label);
        setGravity(Gravity.CENTER);
        setMinHeight(Ui.dp(c, DesignTokens.BUTTON_HEIGHT));
        setMinimumHeight(Ui.dp(c, DesignTokens.BUTTON_HEIGHT));
        int pad = Ui.dp(c, kind == TEXT ? 12 : 20);
        setPadding(pad, 0, pad, 0);
        int fg = kind == PRIMARY || kind == TEXT ? roles.accent : kind == FILLED ? roles.onAccent : roles.textPrimary;
        int fill = kind == FILLED ? roles.accent : kind == TEXT ? 0 : roles.fill;
        setTextColor(fg);
        setBackground(Ui.pressable(roles, fill, -1));
        if (iconRes != 0) {
            setCompoundDrawablesRelative(Icons.get(c, iconRes, fg, Ui.dp(c, DesignTokens.ICON_SMALL)), null, null, null);
            setCompoundDrawablePadding(Ui.dp(c, 8));
        }
        setClickable(true);
        setFocusable(true);
    }

    public int kind() { return kind; }

    @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); setAlpha(enabled ? 1f : 0.4f); }

    @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }
}
