package dev.lilt.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;

/**
 * Square artwork card for shelves and grids: artwork (radius by size, see
 * {@link DesignTokens#artworkRadius}), 8dp gap, title (15/20 medium, up to 2 lines) and
 * subtitle (13/18 secondary, 1 line). {@link #artist} makes the circular artist variant with
 * centred text. {@link #mosaic} makes the 2 x 2 collection cover. Press feedback included.
 *
 * <pre>{@code
 * AlbumCard card = new AlbumCard(ctx, ShelfMetrics.squareCard(widthDp));
 * card.bind("Low Tide", "Field Theory");
 * artwork.load(card.cover(), track);   // or card.cover().setImageDrawable(new PlaceholderDrawable(...))
 * }</pre>
 */
public class AlbumCard extends LinearLayout {
    private final ImageView cover;
    private final ImageView[] tiles;
    private final TextView title, subtitle;
    private final boolean circle;

    public AlbumCard(Context c, int sizeDp) { this(c, Theme.colors(c), sizeDp, false, false); }

    /** Circular artist card. */
    public static AlbumCard artist(Context c, int sizeDp) { return new AlbumCard(c, Theme.colors(c), sizeDp, true, false); }

    /** 2 x 2 mosaic cover (Favourites, Most Played, playlists without art): fill {@link #tile(int)}. */
    public static AlbumCard mosaic(Context c, int sizeDp) { return new AlbumCard(c, Theme.colors(c), sizeDp, false, true); }

    public AlbumCard(Context c, ColorRoles roles, int sizeDp, boolean circle, boolean mosaic) {
        super(c);
        this.circle = circle;
        setOrientation(VERTICAL);
        int size = Ui.dp(c, sizeDp);
        float radius = Ui.dpf(c, DesignTokens.artworkRadius(sizeDp));
        if (mosaic) {
            GridLayout grid = new GridLayout(c);
            grid.setColumnCount(2);
            tiles = new ImageView[4];
            for (int i = 0; i < 4; i++) {
                tiles[i] = new ImageView(c);
                tiles[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
                tiles[i].setBackgroundColor(roles.placeholder);
                grid.addView(tiles[i], new GridLayout.LayoutParams(new android.view.ViewGroup.LayoutParams(size / 2, size / 2)));
            }
            Ui.roundOutline(grid, radius);
            addView(grid, new LayoutParams(size, size));
            cover = tiles[0];
        } else {
            tiles = null;
            cover = new ImageView(c);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (circle) Ui.ovalOutline(cover); else Ui.roundOutline(cover, radius);
            addView(cover, new LayoutParams(size, size));
        }
        cover.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        title = Type.text(c, TypeScale.CARD_TITLE, roles.textPrimary);
        Type.maxLines(title, circle ? 1 : 2);
        subtitle = Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary);
        Type.singleLine(subtitle);
        if (circle) { title.setGravity(Gravity.CENTER_HORIZONTAL); subtitle.setGravity(Gravity.CENTER_HORIZONTAL); }
        LayoutParams tp = new LayoutParams(size, LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(c, 8);
        addView(title, tp);
        addView(subtitle, new LayoutParams(size, LayoutParams.WRAP_CONTENT));
        setClickable(true);
        setLongClickable(true);
        Motion.pressFeedback(this);
    }

    public ImageView cover() { return cover; }
    /** Mosaic tile 0–3 (only for {@link #mosaic}). */
    public ImageView tile(int i) { return tiles == null ? cover : tiles[i]; }

    public void bind(CharSequence titleText, CharSequence subtitleText) {
        title.setText(titleText);
        subtitle.setText(subtitleText);
        subtitle.setVisibility(subtitleText == null || subtitleText.length() == 0 ? GONE : VISIBLE);
        setContentDescription(subtitleText == null ? titleText : titleText + ", " + subtitleText);
    }

    public boolean isCircle() { return circle; }
}
