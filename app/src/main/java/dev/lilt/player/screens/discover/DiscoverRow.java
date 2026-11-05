package dev.lilt.player.screens.discover;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.SongRow;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * {@link SongRow} plus what catalogue lists need: a chart rank shown beside the artwork
 * ("1 [art] Overpass") and a one-tap save accessory (plus → check) before "more".
 */
public final class DiscoverRow extends SongRow {
    public static final int SAVE_HIDDEN = 0, SAVE_OFF = 1, SAVE_ON = 2;
    private final ColorRoles roles;
    private final TextView rank;
    private final ImageView save;
    private int saveState = -1;

    public DiscoverRow(Context c, ColorRoles roles) {
        super(c, roles);
        this.roles = roles;
        rank = Type.text(c, TypeScale.SUBHEAD.weight(500), roles.textSecondary);
        Type.tabular(rank);
        rank.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        rank.setVisibility(GONE);
        rank.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(rank, 0, new LayoutParams(Ui.dp(c, 24), LayoutParams.WRAP_CONTENT));
        save = new ImageView(c);
        save.setScaleType(ImageView.ScaleType.CENTER);
        save.setBackground(Ui.borderlessRipple(roles, Ui.dp(c, 20)));
        save.setVisibility(GONE);
        addView(save, indexOfChild(more()), new LayoutParams(Ui.dp(c, 40), Ui.dp(c, DesignTokens.TOUCH_TARGET)));
    }

    /** Chart rank (1-based) beside the artwork; 0 hides it. */
    public void setRank(int n) {
        rank.setVisibility(n > 0 ? VISIBLE : GONE);
        if (n > 0) rank.setText(Integer.toString(n));
    }

    public ImageView save() { return save; }

    public void setSaveState(int state) {
        if (state == saveState) return;
        saveState = state;
        save.setVisibility(state == SAVE_HIDDEN ? GONE : VISIBLE);
        if (state == SAVE_HIDDEN) return;
        boolean on = state == SAVE_ON;
        save.setImageDrawable(Icons.get(getContext(), on ? R.drawable.ic_check : R.drawable.ic_plus,
                on ? roles.accent : roles.textSecondary, Ui.dp(getContext(), DesignTokens.ICON_SMALL)));
        save.setContentDescription(on ? "Remove from Library" : "Save to Library");
    }
}
