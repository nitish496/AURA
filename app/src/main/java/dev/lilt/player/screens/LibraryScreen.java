package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.AlbumGroup;
import dev.lilt.player.app.ArtistGroup;
import dev.lilt.player.app.Tab;
import dev.lilt.player.app.chrome.TopBar;
import dev.lilt.player.screens.library.Kit;
import dev.lilt.player.screens.library.ListScreen;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.EmptyState;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.ListRow;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.ShelfView;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.widget.LinearLayout;

/**
 * Library, Apple Music style: category rows with counts (Songs, Favourites, Most Played...), then
 * Playlists, Artists and Albums, and a Recently Added album shelf.
 */
public final class LibraryScreen extends ListScreen {
    private LinearLayout header;

    public LibraryScreen() {}

    @Override public String key() { return "library"; }
    @Override public Tab tab() { return Tab.LIBRARY; }
    @Override protected CharSequence barTitle() { return "Library"; }

    @Override protected void buildTopBar(TopBar bar) {
        bar.addRight(R.drawable.ic_plus, "New Playlist", () -> newPlaylist());
    }

    @Override protected View buildHeader() {
        header = Ui.column(context);
        TextView title = Kit.largeTitle(context, roles, "Library");
        header.addView(title);
        applyHeaderInsets();
        return header;
    }

    private void applyHeaderInsets() {
        if (header == null) return;
        int top = Math.max(0, insetTop - Ui.dp(context, dev.lilt.player.DesignTokens.TOP_BAR)) + Ui.dp(context, 6);
        header.setPadding(Ui.dp(context, 16), top, Ui.dp(context, 64), Ui.dp(context, 8));
        titleBottomPx = top + Ui.dp(context, 40);
    }

    @Override protected void onInsetsChanged() { applyHeaderInsets(); }

    private void newPlaylist() {
        host.prompt("New Playlist", "Name", "", "Create", name -> host.library().createPlaylist(name));
    }

    private ListRow row(int icon, String title, int count, String mode) {
        ListRow r = new ListRow(context, roles).icon(icon, true).title(title).accessory(ListRow.CHEVRON).separator(true);
        if (count >= 0) r.value(String.format(Locale.getDefault(), "%,d", count));
        r.setOnClickListener(v -> host.nav().push(new SongsScreen(mode)));
        return r;
    }

    @Override protected List<Object> buildItems() {
        List<Object> items = new ArrayList<>();
        List<Track> all = host.library().allTracks();
        int favs = host.library().favourites().size();
        int saved = host.library().saved().tracks().size();

        items.add(row(R.drawable.ic_song, "Songs", all.size(), "songs"));
        items.add(row(R.drawable.ic_heart, "Favourites", favs, "favourites"));
        items.add(row(R.drawable.ic_clock, "Recently Played", -1, "recent"));
        items.add(row(R.drawable.ic_trending, "Most Played", -1, "most"));
        items.add(row(R.drawable.ic_plus, "Recently Added", -1, "added"));
        if (saved > 0 || SettingsScreen.showCatalogue(context)) items.add(row(R.drawable.ic_cloud, "Saved from Audius", saved, "saved"));

        // Playlists
        items.add(Kit.section(context, roles, "Playlists", null));
        List<String> names = new ArrayList<>(host.library().playlists().keySet());
        if (names.isEmpty()) {
            ListRow add = new ListRow(context, roles).icon(R.drawable.ic_playlist_add, true).title("New Playlist").separator(false);
            add.setOnClickListener(v -> newPlaylist());
            items.add(add);
        } else {
            for (final String n : names) {
                int count = host.library().playlistTracks(n).size();
                ListRow r = new ListRow(context, roles).icon(R.drawable.ic_playlist, true).title(n)
                        .value(String.valueOf(count)).accessory(ListRow.CHEVRON).separator(true);
                r.setOnClickListener(v -> host.openPlaylist(n));
                items.add(r);
            }
        }

        List<ArtistGroup> artists = host.library().artists();
        if (!artists.isEmpty()) {
            items.add(Kit.section(context, roles, "Artists", null));
            items.add(artistShelf(artists));
        }
        List<AlbumGroup> albums = host.library().albums();
        if (!albums.isEmpty()) {
            items.add(Kit.section(context, roles, "Albums", null));
            items.add(Kit.albumShelf(host, albums.size() > 40 ? albums.subList(0, 40) : albums));
        }
        if (all.isEmpty()) {
            items.add(host.library().hasPermission()
                    ? EmptyState.noMusic(context, () -> host.library().refresh(), () -> host.nav().switchTab(Tab.BROWSE))
                    : EmptyState.permission(context, () -> host.requestMediaPermission(), () -> host.nav().switchTab(Tab.BROWSE)));
        }
        items.add(Kit.spacer(context, 16));
        return items;
    }

    private View artistShelf(final List<ArtistGroup> artists) {
        final int dp = ShelfMetrics.artistCircle(host.contentWidthDp());
        ShelfView shelf = new ShelfView(context);
        shelf.setAdapter(new ShelfView.Adapter() {
            @Override public int count() { return Math.min(artists.size(), 40); }
            @Override public View create(ViewGroup parent) { return AlbumCard.artist(context, dp); }
            @Override public void bind(View v, int i) {
                final ArtistGroup a = artists.get(i);
                AlbumCard card = (AlbumCard) v;
                card.bind(a.name, null);
                card.setOnClickListener(x -> host.openArtist(a.name));
                card.setOnLongClickListener(x -> { Haptics.longPress(x); host.showCollectionMenu(a.name, a.tracks, x); return true; });
            }
            @Override public void onVisible(View v, int i) {
                Track cover = artists.get(i).cover();
                if (cover != null) host.artwork().load(((AlbumCard) v).cover(), cover);
            }
        });
        return shelf;
    }
}
