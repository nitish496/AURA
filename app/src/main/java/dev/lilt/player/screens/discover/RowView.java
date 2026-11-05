package dev.lilt.player.screens.discover;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * A plain 60dp list row for artists, playlists and recent searches: 44dp image (round or
 * rounded-square), title over subtitle, optional trailing icon button. Recycled by the adapter.
 */
public final class RowView extends LinearLayout {
    private final ImageView image, trailing;
    private final TextView title, subtitle;
    private final boolean circle;

    public RowView(Context c, ColorRoles roles, boolean circle) {
        super(c);
        this.circle = circle;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(c, DesignTokens.ROW_SONG));
        setPaddingRelative(Ui.dp(c, DesignTokens.MARGIN), 0, Ui.dp(c, 2), 0);
        setBackground(Ui.rowRipple(roles));
        setClickable(true);
        int px = Ui.dp(c, DesignTokens.COVER_ROW);
        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (circle) Ui.ovalOutline(image); else Ui.roundOutline(image, Ui.dpf(c, DesignTokens.RADIUS_S));
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(image, new LayoutParams(px, px));
        LinearLayout text = Ui.column(c);
        title = Type.text(c, TypeScale.ROW_TITLE, roles.textPrimary);
        Type.singleLine(title);
        subtitle = Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary);
        Type.singleLine(subtitle);
        text.addView(title);
        text.addView(subtitle);
        LayoutParams tp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(Ui.dp(c, 12));
        tp.setMarginEnd(Ui.dp(c, 8));
        addView(text, tp);
        trailing = new ImageView(c);
        trailing.setScaleType(ImageView.ScaleType.CENTER);
        trailing.setBackground(Ui.borderlessRipple(roles, Ui.dp(c, 20)));
        trailing.setVisibility(GONE);
        int touch = Ui.dp(c, DesignTokens.TOUCH_TARGET);
        addView(trailing, new LayoutParams(touch, touch));
    }

    public ImageView image() { return image; }
    public boolean circle() { return circle; }

    public void bind(CharSequence titleText, CharSequence subtitleText) {
        title.setText(titleText);
        subtitle.setText(subtitleText);
        subtitle.setVisibility(subtitleText == null || subtitleText.length() == 0 ? GONE : VISIBLE);
        setContentDescription(subtitleText == null || subtitleText.length() == 0 ? titleText : titleText + ", " + subtitleText);
    }

    /** Trailing icon button (e.g. remove a recent search); {@code res} 0 hides it. */
    public ImageView trailing(int res, int color, CharSequence description) {
        trailing.setVisibility(res == 0 ? GONE : VISIBLE);
        if (res != 0) {
            trailing.setImageDrawable(Icons.get(getContext(), res, color, Ui.dp(getContext(), 18)));
            trailing.setContentDescription(description);
        }
        return trailing;
    }

    /** Fills the image with a circular fill and a centred icon (the "search" row of a typed query). */
    public void iconImage(ColorRoles roles, int iconRes) {
        image.setTag(null);
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setBackground(Ui.oval(roles.fill));
        image.setImageDrawable(Icons.get(getContext(), iconRes, roles.textSecondary, Ui.dp(getContext(), 20)));
    }

    /** Back to artwork mode after {@link #iconImage}. */
    public void artImage() {
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(null);
    }

    public static int searchIcon() { return R.drawable.ic_search; }
}
