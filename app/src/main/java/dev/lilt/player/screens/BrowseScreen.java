package dev.lilt.player.screens;

import android.view.View;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.Tab;
import dev.lilt.player.screens.discover.Art;
import dev.lilt.player.screens.discover.DiscoverScreen;
import dev.lilt.player.screens.discover.DiscoverText;
import dev.lilt.player.screens.discover.QueryGate;
import dev.lilt.player.screens.discover.Rows;
import dev.lilt.player.screens.discover.TopArtists;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.stream.CatalogArtist;
import dev.lilt.player.stream.CatalogPlaylist;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.ShelfView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Browse: the Audius catalogue. Trending chart (numbered songs; tap plays the chart as a queue),
 * trending playlists, top artists derived from the chart, and a genre grid. Skeletons while
 * loading, a cloud-off state with "Available Offline" when there is no connection, and a retry
 * state when the catalogue cannot be reached.
 */
public final class BrowseScreen extends DiscoverScreen {
    private static final int LOADING = 0, READY = 1, FAILED = 2;
    private static final int CHART_SIZE = 50, CHART_SHOWN = 10;

    private final QueryGate gate = new QueryGate();
    private int chartState = LOADING, playlistState = LOADING;
    private List<Track> chart = Collections.emptyList();
    private List<CatalogPlaylist> playlists = Collections.emptyList();
    private List<TopArtists.Entry> artists = Collections.emptyList();
    private final Map<String, CatalogArtist> profiles = new HashMap<>();
    private ShelfView artistShelf;
    private final ArtistBinder artistBinder = new ArtistBinder();

    public BrowseScreen() { }

    @Override public String key() { return "browse"; }
    @Override public Tab tab() { return Tab.BROWSE; }

    @Override protected void onReady() { load(); }

    @Override protected void onConnectivity(boolean nowOnline) {
        if (nowOnline && chartState != READY) load();
        else render();
    }

    private void load() {
        if (!online) { chartState = FAILED; playlistState = FAILED; render(); return; }
        int gen = gate.begin("browse");
        chartState = chart.isEmpty() ? LOADING : READY;
        playlistState = playlists.isEmpty() ? LOADING : READY;
        render();
        work(gate, gen, () -> AudiusClient.get(ctx).trending(null, "week", CHART_SIZE), found -> {
            chart = found;
            chartState = READY;
            artists = topArtists(found);
            render();
            enrich(gen);
        }, e -> { chartState = FAILED; render(); });
        work(gate, gen, () -> AudiusClient.get(ctx).trendingPlaylists(20), found -> {
            playlists = found;
            playlistState = READY;
            render();
        }, e -> { playlistState = FAILED; render(); });
    }

    private static List<TopArtists.Entry> topArtists(List<Track> tracks) {
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        for (Track t : tracks) { ids.add(t.artistId); names.add(t.artist); }
        List<TopArtists.Entry> all = TopArtists.fromChart(ids, names, 20);
        List<TopArtists.Entry> withId = new ArrayList<>();
        for (TopArtists.Entry e : all) if (!e.id.isEmpty() && withId.size() < 10) withId.add(e);
        return withId;
    }

    /** Looks up each top artist's picture and follower count (search by name, match by id). */
    private void enrich(int gen) {
        for (TopArtists.Entry entry : artists) {
            if (profiles.containsKey(entry.id)) continue;
            work(gate, gen, () -> {
                for (CatalogArtist a : AudiusClient.get(ctx).searchArtists(entry.name, 5)) if (a.id.equals(entry.id)) return a;
                return null;
            }, found -> {
                if (found == null) return;
                profiles.put(entry.id, found);
                int at = artists.indexOf(entry);
                if (artistShelf != null && at >= 0 && at < artists.size()) {
                    AlbumCard cell = (AlbumCard) artistShelf.cell(at);
                    artistBinder.bind(cell, at);
                    artistBinder.art(cell, at);
                }
            }, null);
        }
    }

    @Override protected List<Rows.Item> build() {
        List<Rows.Item> out = new ArrayList<>();
        out.add(title("Browse"));
        boolean data = !chart.isEmpty() || !playlists.isEmpty();
        if (!data && !online) { offline(out); return out; }
        if (!data && chartState == LOADING && playlistState == LOADING) { skeleton(out); return out; }
        if (!data && chartState == FAILED && playlistState != LOADING) {
            out.add(stateBlock(R.drawable.ic_alert, "Couldn’t load Browse", "The catalogue didn’t answer. Check your connection and try again.",
                    "Try Again", PillButton.SECONDARY, this::load));
            return out;
        }
        if (playlistState == LOADING && playlists.isEmpty()) {
            out.add(skeletonHeader());
            out.add(skeletonShelf(cardDp(1.9f, 150, 220), false));
        } else if (!playlists.isEmpty()) {
            out.add(header("Trending Playlists", null, null));
            out.add(shelf(playlists.size(), cardDp(1.9f, 150, 220), false, new PlaylistBinder()));
        }
        if (!chart.isEmpty()) {
            out.add(header(DiscoverText.chartTitle("week"), null, () -> host.nav().push(new GenreScreen(null))));
            int shown = Math.min(CHART_SHOWN, chart.size());
            for (int i = 0; i < shown; i++) out.add(song(chart.get(i), chart, i).rank(i + 1));
        } else if (chartState == LOADING) {
            out.add(skeletonHeader());
            out.addAll(skeletonSongs(5));
        }
        if (!artists.isEmpty()) {
            out.add(header("Top Artists", null, null));
            Rows.Item item = shelf(artists.size(), ShelfMetrics.artistCircle(windowDp()), true, artistBinder);
            artistShelf = (ShelfView) item.view();
            out.add(item);
        }
        out.add(header("Genres", null, null));
        out.addAll(genreGrid(AudiusClient.genres(), g -> host.nav().push(new GenreScreen(g))));
        return out;
    }

    private void offline(List<Rows.Item> out) {
        out.add(stateBlock(R.drawable.ic_cloud_off, "You’re offline",
                "Browse needs a connection. Your library, saved songs and anything you played recently still work.",
                "Go to Library", PillButton.SECONDARY, () -> host.nav().switchTab(Tab.LIBRARY)));
        List<Track> recent = host.library().recentlyPlayed(12);
        if (recent.isEmpty()) return;
        out.add(header("Available Offline", "Played recently, kept on this phone", null));
        out.add(shelf(recent.size(), cardDp(2.3f, 136, 200), false, new CardBinderBase() {
            @Override public void bind(AlbumCard card, int i) { card.bind(recent.get(i).title, recent.get(i).artist); }
            @Override public void art(AlbumCard card, int i) { Art.track(host, card.cover(), recent.get(i)); }
            @Override public void click(int i, View card) { play(recent, i, false); }
        }));
    }

    private void skeleton(List<Rows.Item> out) {
        out.add(skeletonHeader());
        out.add(skeletonShelf(cardDp(1.9f, 150, 220), false));
        out.add(skeletonHeader());
        out.addAll(skeletonSongs(5));
    }

    /** Card binder with default no-ops, so offline shelves only override what they use. */
    private abstract static class CardBinderBase implements CardBinder {
        @Override public void bind(AlbumCard card, int i) {}
        @Override public void art(AlbumCard card, int i) {}
        @Override public void click(int i, View card) {}
    }

    private final class PlaylistBinder extends CardBinderBase {
        @Override public void bind(AlbumCard card, int i) {
            CatalogPlaylist p = playlists.get(i);
            card.bind(p.name, p.owner);
            Art.placeholder(card.cover(), p.name, false);
        }
        @Override public void art(AlbumCard card, int i) {
            CatalogPlaylist p = playlists.get(i);
            Art.url(host, card.cover(), "pl" + p.id, p.name, p.artworkUrl, false);
        }
        @Override public void click(int i, View card) { host.openCatalogPlaylist(playlists.get(i)); }
    }

    private final class ArtistBinder extends CardBinderBase {
        @Override public void bind(AlbumCard card, int i) {
            TopArtists.Entry e = artists.get(i);
            CatalogArtist profile = profiles.get(e.id);
            card.bind(e.name, profile == null ? "Artist" : DiscoverText.followers(profile.followers));
        }
        @Override public void art(AlbumCard card, int i) {
            TopArtists.Entry e = artists.get(i);
            CatalogArtist profile = profiles.get(e.id);
            Art.url(host, card.cover(), "ar" + e.id, e.name, profile == null ? null : profile.pictureUrl, true);
        }
        @Override public void click(int i, View card) { TopArtists.Entry e = artists.get(i); host.openCatalogArtist(e.id, e.name); }
    }
}
