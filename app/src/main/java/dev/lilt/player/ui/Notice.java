package dev.lilt.player.ui;

import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;

/**
 * Inline notice for partial failures (catalogue unreachable while library results still show):
 * surface block, radius 14, 22dp icon, 15/20 medium title over 13/18 secondary body, and an
 * optional Ember text action ("Try Again"). Content, not chrome: no glass, no shadow.
 */
public class Notice extends LinearLayout {
    public Notice(Context c, int iconRes, CharSequence title, CharSequence body, CharSequence action, Runnable onAction) {
        this(c, Theme.colors(c), iconRes, title, body, action, onAction);
    }

    public Notice(Context c, ColorRoles roles, int iconRes, CharSequence title, CharSequence body, CharSequence action, Runnable onAction) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setBackground(Ui.roundRect(roles.surface, Ui.dpf(c, DesignTokens.RADIUS_L)));
        setPaddingRelative(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14));
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(Icons.get(c, iconRes, roles.textSecondary));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams ip = new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        ip.setMarginEnd(Ui.dp(c, 12));
        addView(icon, ip);
        LinearLayout text = Ui.column(c);
        text.addView(Type.text(c, TypeScale.SUBHEAD.weight(500), roles.textPrimary, title));
        if (body != null) text.addView(Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary, body));
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        if (action != null) {
            PillButton b = new PillButton(c, roles, PillButton.TEXT, action, 0);
            b.setOnClickListener(v -> onAction.run());
            addView(b);
        }
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    }
}
