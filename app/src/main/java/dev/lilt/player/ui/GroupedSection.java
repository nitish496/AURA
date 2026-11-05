package dev.lilt.player.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;

/**
 * Inset grouped section for Settings / Sound: optional overline header ("PLAYBACK", 13sp
 * tertiary, +6% tracking) above a surface block (radius 14, 16dp side margins) whose rows get
 * inset hairlines automatically. Add {@link ListRow}s (52 / 64dp) or any custom block.
 *
 * <pre>{@code
 * GroupedSection playback = new GroupedSection(ctx, "Playback");
 * playback.add(new ListRow(ctx).title("Gapless playback")).toggle(true);
 * }</pre>
 */
public class GroupedSection extends LinearLayout {
    private final LinearLayout block;

    public GroupedSection(Context c, CharSequence header) { this(c, Theme.colors(c), header); }

    public GroupedSection(Context c, ColorRoles roles, CharSequence header) {
        super(c);
        setOrientation(VERTICAL);
        int m = Ui.dp(c, DesignTokens.MARGIN);
        if (header != null) {
            TextView h = Type.text(c, TypeScale.OVERLINE, roles.textTertiary, header.toString().toUpperCase(java.util.Locale.getDefault()));
            h.setPaddingRelative(m * 2, Ui.dp(c, 16), m, Ui.dp(c, 8));
            if (android.os.Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
            addView(h);
        }
        block = Ui.column(c);
        block.setBackground(Ui.roundRect(roles.surface, Ui.dpf(c, DesignTokens.RADIUS_L)));
        Ui.roundOutline(block, Ui.dpf(c, DesignTokens.RADIUS_L));
        LayoutParams bp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bp.setMargins(m, 0, m, 0);
        addView(block, bp);
    }

    /** Appends a row; the previous row gets a separator. Returns the row for chaining. */
    public <T extends View> T add(T row) {
        int n = block.getChildCount();
        if (n > 0 && block.getChildAt(n - 1) instanceof ListRow) ((ListRow) block.getChildAt(n - 1)).separator(true);
        block.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    public LinearLayout block() { return block; }
}
