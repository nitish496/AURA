package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;

/**
 * Song row, 60dp: 44dp artwork (radius 6), title (16/21 medium) over subtitle (13/18 secondary)
 * with optional explicit + source badges, trailing "more" button. Also the album track row:
 * {@link #setNumber} swaps the artwork for a tabular track number (52dp tall).
 * Built for recycling: create once per ListView cell and call the setters in getView().
 *
 * <pre>{@code
 * SongRow row = reused instanceof SongRow ? (SongRow) reused : new SongRow(ctx);
 * row.bind(track.title, track.artist, explicit, SongRow.SOURCE_STREAM);
 * row.setPlaying(isCurrent, isPlaying);
 * artwork.load(row.cover(), track);
 * row.more().setOnClickListener(v -> showMenu(track));
 * }</pre>
 */
public class SongRow extends LinearLayout {
    /** Source badges: none (on this phone), streamed from the catalogue, saved and cached offline. */
    public static final int SOURCE_LOCAL = 0, SOURCE_STREAM = 1, SOURCE_SAVED = 2, SOURCE_LOCAL_BADGE = 3;

    private final ColorRoles roles;
    private final FrameLayout lead;
    private final ImageView cover;
    private final TextView number, title, subtitle, trailingText;
    private final NowPlayingIndicator indicator;
    private final View dim;
    private final ImageView more, save;
    private final TextView rank;
    private boolean numbered, current, saved;

    public SongRow(Context c) { this(c, Theme.colors(c)); }

    public SongRow(Context c, ColorRoles roles) {
        super(c);
        this.roles = roles;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(c, DesignTokens.ROW_SONG));
        setPaddingRelative(Ui.dp(c, DesignTokens.MARGIN), 0, Ui.dp(c, 2), 0);
        setBackground(Ui.rowRipple(roles));
        int coverPx = Ui.dp(c, DesignTokens.COVER_ROW);
        rank = Type.text(c, TypeScale.SUBHEAD.weight(500), roles.textSecondary);
        Type.tabular(rank);
        rank.setVisibility(GONE);
        addView(rank, new LayoutParams(Ui.dp(c, 24), LayoutParams.WRAP_CONTENT));
        lead = new FrameLayout(c);
        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.roundOutline(cover, Ui.dpf(c, DesignTokens.RADIUS_S));
        cover.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        lead.addView(cover, new FrameLayout.LayoutParams(coverPx, coverPx));
        dim = new View(c);
        dim.setBackground(Ui.roundRect(0x73000000, Ui.dpf(c, DesignTokens.RADIUS_S)));
        dim.setVisibility(GONE);
        lead.addView(dim, new FrameLayout.LayoutParams(coverPx, coverPx));
        indicator = new NowPlayingIndicator(c, 0xFFF5F5F7);
        indicator.setVisibility(GONE);
        lead.addView(indicator, new FrameLayout.LayoutParams(coverPx, coverPx, Gravity.CENTER));
        number = Type.text(c, TypeScale.SUBHEAD, roles.textTertiary);
        Type.tabular(number);
        number.setGravity(Gravity.CENTER);
        number.setVisibility(GONE);
        lead.addView(number, new FrameLayout.LayoutParams(Ui.dp(c, 28), LayoutParams.MATCH_PARENT, Gravity.CENTER));
        addView(lead, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout text = Ui.column(c);
        title = Type.text(c, TypeScale.ROW_TITLE, roles.textPrimary);
        Type.singleLine(title);
        subtitle = Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary);
        Type.singleLine(subtitle);
        subtitle.setCompoundDrawablePadding(Ui.dp(c, 4));
        text.addView(title);
        text.addView(subtitle);
        LayoutParams tp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(Ui.dp(c, 12));
        tp.setMarginEnd(Ui.dp(c, 8));
        addView(text, tp);

        trailingText = Type.text(c, TypeScale.FOOTNOTE, roles.textTertiary);
        Type.tabular(trailingText);
        trailingText.setVisibility(GONE);
        addView(trailingText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        save = new ImageView(c);
        save.setScaleType(ImageView.ScaleType.CENTER);
        save.setBackground(Ui.borderlessRipple(roles, Ui.dp(c, 20)));
        save.setVisibility(GONE);
        addView(save, new LayoutParams(Ui.dp(c, DesignTokens.TOUCH_TARGET), Ui.dp(c, DesignTokens.TOUCH_TARGET)));
        more = new ImageView(c);
        more.setScaleType(ImageView.ScaleType.CENTER);
        more.setImageDrawable(Icons.get(c, R.drawable.ic_more, roles.textSecondary, Ui.dp(c, DesignTokens.ICON_SMALL)));
        more.setBackground(Ui.borderlessRipple(roles, Ui.dp(c, 20)));
        more.setContentDescription("More options");
        int touch = Ui.dp(c, DesignTokens.TOUCH_TARGET);
        addView(more, new LayoutParams(touch, touch));
        setClickable(true);
        setLongClickable(true);
    }

    public ImageView cover() { return cover; }
    public ImageView more() { return more; }
    /** One-tap save accessory (hidden until {@link #setSaveState} is called). */
    public ImageView save() { return save; }

    /** Chart rank shown before the artwork ("1 [art] Title"); ≤ 0 hides it. */
    public void setRank(int n) {
        rank.setVisibility(n > 0 ? VISIBLE : GONE);
        rank.setText(n > 0 ? Integer.toString(n) : "");
    }

    /** Shows the save accessory: plus (not saved) or Ember check (saved to library). */
    public void setSaveState(boolean isSaved) {
        saved = isSaved;
        save.setVisibility(VISIBLE);
        save.setImageDrawable(Icons.get(getContext(), isSaved ? R.drawable.ic_check : R.drawable.ic_plus,
                isSaved ? roles.accent : roles.textSecondary, Ui.dp(getContext(), DesignTokens.ICON_SMALL)));
        save.setContentDescription(isSaved ? "Saved to library. Remove" : "Save to library");
    }

    public void hideSave() { save.setVisibility(GONE); }
    public boolean isSaved() { return saved; }
    public TextView title() { return title; }
    public TextView subtitle() { return subtitle; }

    /** Title, subtitle ("Artist" or "Artist · Album"), explicit flag and source badge. */
    public void bind(CharSequence titleText, CharSequence subtitleText, boolean explicit, int source) {
        title.setText(titleText);
        subtitle.setText(subtitleText);
        subtitle.setCompoundDrawablesRelative(badges(explicit, source), null, null, null);
        more.setContentDescription("More options for " + titleText);
        setContentDescription(titleText + ", " + subtitleText + (explicit ? ", explicit" : ""));
    }

    private Drawable badges(boolean explicit, int source) {
        int icon = source == SOURCE_STREAM ? R.drawable.ic_cloud : source == SOURCE_SAVED ? R.drawable.ic_cloud_check
                : source == SOURCE_LOCAL_BADGE ? R.drawable.ic_phone : 0;
        if (!explicit && icon == 0) return null;
        int s = Ui.dp(getContext(), DesignTokens.ICON_INLINE), gap = Ui.dp(getContext(), 4);
        Drawable e = explicit ? Icons.get(getContext(), R.drawable.ic_explicit, roles.textTertiary) : null;
        Drawable src = icon != 0 ? Icons.get(getContext(), icon, roles.textSecondary) : null;
        Drawable[] parts = e != null && src != null ? new Drawable[] {e, src} : new Drawable[] {e != null ? e : src};
        LayerDrawable layers = new LayerDrawable(parts);
        for (int i = 0; i < parts.length; i++) {
            layers.setLayerSize(i, s, s);
            layers.setLayerInsetStart(i, i * (s + gap));
        }
        layers.setBounds(0, 0, parts.length * s + (parts.length - 1) * gap, s);
        return layers;
    }

    /** Album track row: shows {@code n} instead of artwork (row becomes 52dp). n ≤ 0 restores artwork. */
    public void setNumber(int n) {
        numbered = n > 0;
        number.setText(numbered ? Integer.toString(n) : "");
        cover.setVisibility(numbered ? GONE : VISIBLE);
        setMinimumHeight(Ui.dp(getContext(), numbered ? DesignTokens.ROW_TRACK : DesignTokens.ROW_SONG));
        refreshState(false);
    }

    /** Trailing duration or play count ("3:41", "38 plays"); null hides it. */
    public void setTrailingText(CharSequence text) {
        trailingText.setText(text);
        trailingText.setVisibility(text == null ? GONE : VISIBLE);
    }

    /** Marks the current track: Ember title + indicator (animated while {@code playing}). */
    public void setPlaying(boolean isCurrent, boolean playing) {
        current = isCurrent;
        indicator.setPlaying(playing);
        refreshState(isCurrent);
    }

    private void refreshState(boolean isCurrent) {
        title.setTextColor(current ? roles.accent : roles.textPrimary);
        indicator.setColor(numbered ? roles.accent : 0xFFF5F5F7);
        indicator.setVisibility(current ? VISIBLE : GONE);
        dim.setVisibility(current && !numbered ? VISIBLE : GONE);
        number.setVisibility(numbered && !current ? VISIBLE : GONE);
    }

    /** Unavailable (e.g. a stream while offline): dimmed, not playable. */
    public void setUnavailable(boolean unavailable) {
        setAlpha(unavailable ? 0.45f : 1f);
        setEnabled(!unavailable);
    }

    /** Rows on artwork tints (Queue): white text, secondary at the palette's safe alpha. */
    public void setOnArtwork(ArtworkPalette p) {
        title.setTextColor(p.textPrimary);
        subtitle.setTextColor(p.textSecondary);
        ((android.graphics.drawable.Drawable) more.getDrawable()).setTint(p.textSecondary);
    }
}
