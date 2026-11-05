package dev.lilt.player.screens.library;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.AlbumGroup;
import dev.lilt.player.app.AppHost;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.SectionHeader;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.ShelfView;
import dev.lilt.player.ui.TimeText;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.List;
import java.util.Locale;

/** Small view builders shared by the library screens. */
public final class Kit {
    private Kit() {}

    public static TextView largeTitle(Context c, ColorRoles roles, CharSequence text) {
        TextView t = Type.text(c, TypeScale.LARGE_TITLE, roles.textPrimary, text);
        Type.singleLine(t);
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        return t;
    }

    public static TextView footnote(Context c, ColorRoles roles, CharSequence text) {
        TextView t = Type.text(c, TypeScale.FOOTNOTE, roles.textTertiary, text);
        Type.tabular(t);
        return t;
    }

    /** Play (Ember label) and Shuffle side by side, equal width. */
    public static LinearLayout pills(Context c, ColorRoles roles, Runnable play, Runnable shuffle) {
        LinearLayout row = Ui.row(c);
        PillButton p = new PillButton(c, roles, PillButton.PRIMARY, "Play", R.drawable.ic_play);
        PillButton s = new PillButton(c, roles, PillButton.SECONDARY, "Shuffle", R.drawable.ic_shuffle);
        p.setOnClickListener(v -> { Haptics.click(v); play.run(); });
        s.setOnClickListener(v -> { Haptics.click(v); shuffle.run(); });
        row.addView(p, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginStart(Ui.dp(c, 8));
        row.addView(s, lp);
        return row;
    }

    /** Section header with the standard 28dp gap above it. */
    public static View section(Context c, ColorRoles roles, CharSequence title, Runnable seeAll) {
        LinearLayout box = Ui.column(c);
        box.setPadding(0, Ui.dp(c, DesignTokens.SECTION_GAP - 8), 0, Ui.dp(c, 4));
        SectionHeader h = new SectionHeader(c, roles, title);
        if (seeAll != null) h.onSeeAll(seeAll);
        box.addView(h, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    public static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new ViewGroup.LayoutParams(1, Ui.dp(c, heightDp)));
        return v;
    }

    public static TextView footer(Context c, ColorRoles roles, CharSequence text) {
        TextView t = footnote(c, roles, text);
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 8));
        return t;
    }

    public static String songsLine(List<Track> tracks) {
        long d = 0;
        for (Track t : tracks) d += Math.max(0, t.duration);
        return TimeText.summary(tracks.size(), d, Locale.getDefault());
    }

    public static int cardDp(AppHost host) { return ShelfMetrics.squareCard(host.contentWidthDp()); }

    /** Shelf of album cards: tap opens the album, long-press the collection menu. Artwork loads when a card nears the viewport. */
    public static ShelfView albumShelf(final AppHost host, final List<AlbumGroup> albums) {
        final Context c = host.activity();
        final ColorRoles roles = host.colors();
        final int dp = cardDp(host);
        ShelfView shelf = new ShelfView(c);
        shelf.setAdapter(new ShelfView.Adapter() {
            @Override public int count() { return albums.size(); }
            @Override public View create(ViewGroup parent) { return new AlbumCard(c, roles, dp, false, false); }
            @Override public void bind(View v, int i) {
                AlbumGroup a = albums.get(i);
                AlbumCard card = (AlbumCard) v;
                card.bind(a.title, a.artist);
                card.setOnClickListener(x -> host.openAlbum(a.key));
                card.setOnLongClickListener(x -> { Haptics.longPress(x); host.showCollectionMenu(a.title, a.tracks, x); return true; });
            }
            @Override public void onVisible(View v, int i) {
                Track cover = albums.get(i).cover();
                if (cover != null) host.artwork().load(((AlbumCard) v).cover(), cover);
            }
        });
        return shelf;
    }
}
