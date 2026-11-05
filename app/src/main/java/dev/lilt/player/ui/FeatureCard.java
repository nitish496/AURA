package dev.lilt.player.ui;

import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;

/**
 * Large "Top Picks" card: footnote eyebrow above ("Made for You"), then a 14dp-radius card of
 * square artwork over an 84dp footer tinted from the artwork ({@link ArtworkPalette#background})
 * carrying title (17/22 semibold, white) and a two-line description (13/18, white 72%).
 * Content, not chrome: no glass, no shadow.
 */
public class FeatureCard extends LinearLayout {
    private final ImageView cover;
    private final TextView eyebrow, title, subtitle;
    private final LinearLayout card, footer;

    public FeatureCard(Context c, int widthDp) { this(c, Theme.colors(c), widthDp); }

    public FeatureCard(Context c, ColorRoles roles, int widthDp) {
        super(c);
        setOrientation(VERTICAL);
        int w = Ui.dp(c, widthDp);
        eyebrow = Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary);
        Type.singleLine(eyebrow);
        LayoutParams ep = new LayoutParams(w, LayoutParams.WRAP_CONTENT);
        ep.bottomMargin = Ui.dp(c, 8);
        addView(eyebrow, ep);
        card = Ui.column(c);
        Ui.roundOutline(card, Ui.dpf(c, DesignTokens.RADIUS_L));
        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(cover, new LayoutParams(w, w));
        footer = Ui.column(c);
        footer.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        footer.setBackgroundColor(ArtworkPalette.NEUTRAL.background);
        title = Type.text(c, TypeScale.HEADLINE, ColorRoles.ON_ART_PRIMARY);
        Type.singleLine(title);
        subtitle = Type.text(c, TypeScale.FOOTNOTE, 0xB8F5F5F7);
        Type.maxLines(subtitle, 2);
        footer.addView(title);
        footer.addView(subtitle);
        card.addView(footer, new LayoutParams(w, Ui.dp(c, DesignTokens.CARD_FEATURE_FOOTER)));
        addView(card, new LayoutParams(w, LayoutParams.WRAP_CONTENT));
        setClickable(true);
        Motion.pressFeedback(this);
    }

    public ImageView cover() { return cover; }

    public void bind(CharSequence eyebrowText, CharSequence titleText, CharSequence description) {
        eyebrow.setText(eyebrowText);
        title.setText(titleText);
        subtitle.setText(description);
        setContentDescription(eyebrowText + ", " + titleText + ", " + description);
    }

    /** Footer tint from the artwork (call when the palette is ready; neutral graphite until then). */
    public void setPalette(ArtworkPalette p) {
        footer.setBackgroundColor(p.background);
        subtitle.setTextColor(p.textSecondary);
    }
}
