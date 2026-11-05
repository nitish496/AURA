package dev.lilt.player.screens;

import android.content.SharedPreferences;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.Tab;
import dev.lilt.player.screens.discover.Art;
import dev.lilt.player.screens.discover.DiscoverScreen;
import dev.lilt.player.screens.discover.DiscoverText;
import dev.lilt.player.screens.discover.QueryGate;
import dev.lilt.player.screens.discover.RecentSearches;
import dev.lilt.player.screens.discover.RowView;
import dev.lilt.player.screens.discover.Rows;
import dev.lilt.player.screens.discover.SearchRanking;
import dev.lilt.player.screens.discover.SearchRanking.Candidate;
import dev.lilt.player.screens.discover.SearchText;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.stream.CatalogArtist;
import dev.lilt.player.stream.CatalogPlaylist;
import dev.lilt.player.stream.Connectivity;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.Chip;
import dev.lilt.player.ui.ColorMath;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Search. The shell owns the glass search field and reports it through {@link #onSearchQuery};
 * this screen debounces it (250 ms), searches the library offline-first and the Audius catalogue
 * in parallel, and shows one ranked answer: a top result, songs, artists and playlists, with a
 * scope control (Top Results, Songs, Artists, Playlists, Library). Stale answers are ignored.
 * With no query it shows recent searches and the genre categories.
 */
public final class SearchScreen extends DiscoverScreen {
    private static final String PREFS = "aura_discover", RECENTS_KEY = "recents";
    private static final int SCOPE_TOP = 0, SCOPE_SONGS = 1, SCOPE_ARTISTS = 2, SCOPE_PLAYLISTS = 3, SCOPE_LIBRARY = 4;

    private final String initialQuery;
    private final QueryGate gate = new QueryGate();
    private String pending = "";
    private final Runnable debounced = () -> run(this.pending);
    private String query = "";
    private boolean started;
    private int scope = SCOPE_TOP;
    private Chip.Row scopes;

    private List<Track> libSongs = Collections.emptyList(), catSongs = Collections.emptyList();
    private List<CatalogArtist> artists = Collections.emptyList();
    private List<CatalogPlaylist> playlists = Collections.emptyList();
    private boolean libDone, songsDone, artistsDone, playlistsDone;
    private int catFailures;
    private boolean offlineSearch;
    private List<Track> songs = Collections.emptyList();
    private Candidate<Object> top;

    private RecentSearches recents = new RecentSearches();
    private boolean recentsLoaded;
    private final Map<String, Track> recentTracks = new HashMap<>();

    public SearchScreen(String initialQuery) { this.initialQuery = initialQuery == null ? "" : QueryGate.normalize(initialQuery); }

    @Override public String key() { return "search"; }
    @Override public Tab tab() { return Tab.SEARCH; }

    // ---- Lifecycle ----------------------------------------------------------------------------

    @Override protected void onReady() {
        scopes = new Chip.Row(ctx).setChips(roles, "Top Results", "Songs", "Artists", "Playlists", "Library");
        scopes.setListener(i -> { scope = i; render(); });
        host.io().execute(() -> {
            RecentSearches loaded = RecentSearches.decode(ctx.getSharedPreferences(PREFS, 0).getString(RECENTS_KEY, null));
            host.main().post(() -> {
                if (destroyed) return;
                recents = loaded;
                recentsLoaded = true;
                render();
            });
        });
        if (!initialQuery.isEmpty()) { query = initialQuery; run(initialQuery); }
    }

    @Override public void onShow() {
        super.onShow();
        if (!started && !initialQuery.isEmpty()) run(initialQuery);
    }

    @Override public void onDestroy() {
        host.main().removeCallbacks(debounced);
        gate.cancel();
        super.onDestroy();
    }

    @Override protected void onConnectivity(boolean nowOnline) {
        if (nowOnline && !query.isEmpty() && (offlineSearch || catFailures >= 3)) run(query);
        else render();
    }

    @Override public void onSearchQuery(String raw, boolean submitted) {
        String n = QueryGate.normalize(raw);
        if (ctx == null) return; // the view is not built yet; the constructor's initial query covers this
        host.main().removeCallbacks(debounced);
        if (n.isEmpty()) {
            gate.cancel();
            started = false;
            query = "";
            resetResults();
            render();
            return;
        }
        if (submitted) {
            recents.add(RecentSearches.Entry.query(n));
            saveRecents();
            run(n);
            return;
        }
        if (started && !gate.changes(n)) return;
        pending = n;
        host.main().postDelayed(debounced, QueryGate.DEBOUNCE_MS);
    }

    // ---- Searching ----------------------------------------------------------------------------

    private void resetResults() {
        libSongs = catSongs = Collections.emptyList();
        artists = Collections.emptyList();
        playlists = Collections.emptyList();
        songs = Collections.emptyList();
        top = null;
        libDone = songsDone = artistsDone = playlistsDone = false;
        catFailures = 0;
        offlineSearch = false;
    }

    private void run(String n) {
        if (destroyed || n.isEmpty()) return;
        started = true;
        query = n;
        int gen = gate.begin(n);
        resetResults();
        offlineSearch = !online;
        render();
        work(gate, gen, () -> host.library().searchLocal(n, 40), found -> {
            libSongs = found; libDone = true; recompute(); render();
        }, e -> { libDone = true; recompute(); render(); });
        if (!online) { songsDone = artistsDone = playlistsDone = true; recompute(); render(); return; }
        work(gate, gen, () -> AudiusClient.get(ctx).searchTracks(n, 30), found -> {
            catSongs = found; songsDone = true; recompute(); render();
        }, e -> { songsDone = true; catFailures++; recompute(); render(); });
        work(gate, gen, () -> AudiusClient.get(ctx).searchArtists(n, 12), found -> {
            artists = found; artistsDone = true; recompute(); render();
        }, e -> { artistsDone = true; catFailures++; recompute(); render(); });
        work(gate, gen, () -> AudiusClient.get(ctx).searchPlaylists(n, 12), found -> {
            playlists = found; playlistsDone = true; recompute(); render();
        }, e -> { playlistsDone = true; catFailures++; recompute(); render(); });
    }

    /** Merges library and catalogue songs and picks the top result. */
    private void recompute() {
        List<Candidate<Object>> lib = new ArrayList<>(), cat = new ArrayList<>();
        for (int i = 0; i < libSongs.size(); i++) {
            Track t = libSongs.get(i);
            lib.add(new Candidate<Object>(SearchRanking.SONG, t.title, t.artist, true, 0, i, t));
        }
        for (int i = 0; i < catSongs.size(); i++) {
            Track t = catSongs.get(i);
            cat.add(new Candidate<Object>(SearchRanking.SONG, t.title, t.artist, false, 0, i, t));
        }
        List<Candidate<Object>> merged = SearchRanking.mergeSongs(query, lib, cat, o -> ((Track) o).id, 50);
        songs = new ArrayList<>(merged.size());
        for (Candidate<Object> c : merged) songs.add((Track) c.item);
        List<Candidate<Object>> ac = new ArrayList<>(), pc = new ArrayList<>();
        for (int i = 0; i < artists.size(); i++) {
            CatalogArtist a = artists.get(i);
            ac.add(new Candidate<Object>(SearchRanking.ARTIST, a.name, "", false, a.followers, i, a));
        }
        for (int i = 0; i < playlists.size(); i++) {
            CatalogPlaylist p = playlists.get(i);
            pc.add(new Candidate<Object>(p.isAlbum ? SearchRanking.ALBUM : SearchRanking.PLAYLIST, p.name, p.owner, false, 0, i, p));
        }
        top = SearchRanking.top(query, new ArrayList<>(merged.subList(0, Math.min(10, merged.size()))), ac, pc);
    }

    private boolean allDone() { return libDone && songsDone && artistsDone && playlistsDone; }

    // ---- Items --------------------------------------------------------------------------------

    @Override protected List<Rows.Item> build() {
        List<Rows.Item> out = new ArrayList<>();
        if (query.isEmpty()) { buildEmpty(out); return out; }
        if (scopes != null) out.add(Rows.of(scopes));
        if (offlineSearch) out.add(notice("You’re offline", "Showing your library only.", null, null));
        else if (catFailures >= 3 && allDone()) out.add(notice("Couldn’t reach Audius", "Showing your library only.", "Try Again", () -> run(query)));
        switch (scope) {
            case SCOPE_SONGS: buildSongs(out, songs, songsDone && libDone); break;
            case SCOPE_ARTISTS: buildArtists(out); break;
            case SCOPE_PLAYLISTS: buildPlaylists(out); break;
            case SCOPE_LIBRARY: buildSongs(out, libSongs, libDone); break;
            default: buildTop(out); break;
        }
        return out;
    }

    private void buildTop(List<Rows.Item> out) {
        boolean any = !songs.isEmpty() || !artists.isEmpty() || !playlists.isEmpty();
        if (!any) {
            if (allDone()) out.add(noResults());
            else out.addAll(skeletonSongs(5));
            return;
        }
        if (top != null) out.add(Rows.of(topCard(top)));
        if (!songs.isEmpty()) {
            out.add(header("Songs", null, songs.size() > 4 ? () -> { scope = SCOPE_SONGS; scopes.select(scope); render(); } : null));
            for (int i = 0; i < Math.min(4, songs.size()); i++) out.add(songItem(songs, i));
        } else if (!(songsDone && libDone)) out.addAll(skeletonSongs(3));
        if (!artists.isEmpty()) {
            out.add(header("Artists", null, artists.size() > 3 ? () -> { scope = SCOPE_ARTISTS; scopes.select(scope); render(); } : null));
            out.add(shelf(Math.min(artists.size(), 10), 96 /* spec: 96dp search artist circles */, true, new CardBinder() {
                @Override public void bind(AlbumCard card, int i) {
                    CatalogArtist a = artists.get(i);
                    card.bind(a.name, a.followers > 0 ? DiscoverText.followers(a.followers) : "Artist");
                }
                @Override public void art(AlbumCard card, int i) {
                    CatalogArtist a = artists.get(i);
                    Art.url(host, card.cover(), "ar" + a.id, a.name, a.pictureUrl, true);
                }
                @Override public void click(int i, View card) { openArtist(artists.get(i)); }
            }));
        }
        if (!playlists.isEmpty()) {
            out.add(header("Playlists", null, playlists.size() > 3 ? () -> { scope = SCOPE_PLAYLISTS; scopes.select(scope); render(); } : null));
            out.add(shelf(Math.min(playlists.size(), 10), cardDp(2.3f, 136, 200), false, new CardBinder() {
                @Override public void bind(AlbumCard card, int i) {
                    CatalogPlaylist p = playlists.get(i);
                    card.bind(p.name, p.owner);
                    Art.placeholder(card.cover(), p.name, false);
                }
                @Override public void art(AlbumCard card, int i) {
                    CatalogPlaylist p = playlists.get(i);
                    Art.url(host, card.cover(), "pl" + p.id, p.name, p.artworkUrl, false);
                }
                @Override public void click(int i, View card) { openPlaylist(playlists.get(i)); }
            }));
        }
    }

    private void buildSongs(List<Rows.Item> out, List<Track> list, boolean done) {
        if (list.isEmpty()) {
            if (done) out.add(noResults()); else out.addAll(skeletonSongs(6));
            return;
        }
        out.add(spacer(4));
        for (int i = 0; i < list.size(); i++) out.add(songItem(list, i));
    }

    private Rows.Item songItem(List<Track> list, int i) {
        Track t = list.get(i);
        SongItem item = song(t, list, i).album(false).onOpen(() -> rememberSong(t));
        if (!isCurrent(t)) item.highlighted(highlight(t.isRemote() ? dev.lilt.player.stream.RemoteText.stripEmoji(t.title) : t.title));
        return item;
    }

    private void buildArtists(List<Rows.Item> out) {
        if (artists.isEmpty()) {
            if (artistsDone) out.add(offlineSearch ? noCatalogue("artists") : noResults()); else out.addAll(skeletonSongs(5));
            return;
        }
        out.add(spacer(4));
        for (CatalogArtist a : artists) {
            out.add(new Rows.Item() {
                @Override public int type() { return Rows.ARTIST; }
                @Override public View create(ViewGroup parent) { return new RowView(ctx, roles, true); }
                @Override public void bind(View v) {
                    RowView row = (RowView) v;
                    row.artImage();
                    row.bind(highlight(a.name), a.followers > 0 ? "Artist · " + DiscoverText.followers(a.followers) : "Artist");
                    row.trailing(0, 0, null);
                    row.setOnClickListener(x -> openArtist(a));
                    Art.url(host, row.image(), "ar" + a.id, a.name, a.pictureUrl, true);
                }
            });
        }
    }

    private void buildPlaylists(List<Rows.Item> out) {
        if (playlists.isEmpty()) {
            if (playlistsDone) out.add(offlineSearch ? noCatalogue("playlists") : noResults()); else out.addAll(skeletonSongs(5));
            return;
        }
        out.add(spacer(4));
        for (CatalogPlaylist p : playlists) {
            out.add(new Rows.Item() {
                @Override public int type() { return Rows.PLAYLIST; }
                @Override public View create(ViewGroup parent) { return new RowView(ctx, roles, false); }
                @Override public void bind(View v) {
                    RowView row = (RowView) v;
                    row.artImage();
                    row.bind(highlight(p.name), DiscoverText.joined(p.isAlbum ? "Album" : "Playlist", p.owner, p.trackCount > 0 ? TimeSongs.of(p.trackCount) : ""));
                    row.trailing(0, 0, null);
                    row.setOnClickListener(x -> openPlaylist(p));
                    Art.url(host, row.image(), "pl" + p.id, p.name, p.artworkUrl, false);
                }
            });
        }
    }

    /** "12 songs" without pulling in a locale-dependent formatter twice. */
    private static final class TimeSongs {
        static String of(int n) { return DiscoverText.collection(n, 0); }
    }

    private Rows.Item noResults() {
        return stateBlock(R.drawable.ic_search, "No results for “" + query + "”",
                "Check the spelling or try an artist, song or playlist name.", null, 0, null);
    }

    private Rows.Item noCatalogue(String what) {
        return stateBlock(R.drawable.ic_cloud_off, "You’re offline", "Catalogue " + what + " need a connection.", null, 0, null);
    }

    // ---- Top result ---------------------------------------------------------------------------

    private View topCard(Candidate<Object> c) {
        LinearLayout row = Ui.row(ctx);
        row.setPadding(dp(DesignTokens.MARGIN), dp(8), dp(DesignTokens.MARGIN), dp(8));
        row.setMinimumHeight(dp(96));
        row.setBackground(Ui.rowRipple(roles));
        row.setClickable(true);
        boolean artist = c.kind == SearchRanking.ARTIST;
        int size = dp(80);
        ImageView art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (artist) Ui.ovalOutline(art); else Ui.roundOutline(art, dpf(DesignTokens.RADIUS_M));
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(art, new LinearLayout.LayoutParams(size, size));

        LinearLayout text = Ui.column(ctx);
        TextView title = Type.text(ctx, TypeScale.TITLE3, roles.textPrimary, c.title);
        Type.maxLines(title, 2);
        text.addView(title);
        String kind, source;
        Runnable open;
        boolean playable = true;
        if (c.item instanceof Track) {
            Track t = (Track) c.item;
            kind = DiscoverText.kind("Song", t.artist);
            source = !t.isRemote() ? "On this phone" : host.library().isSaved(t.id) ? "Saved from Audius" : "Streaming from Audius";
            Art.track(host, art, t);
            open = () -> { rememberSong(t); play(songs, Math.max(0, songs.indexOf(t)), false); };
        } else if (c.item instanceof CatalogArtist) {
            CatalogArtist a = (CatalogArtist) c.item;
            kind = "Artist";
            source = a.followers > 0 ? DiscoverText.followers(a.followers) : "";
            Art.url(host, art, "ar" + a.id, a.name, a.pictureUrl, true);
            open = () -> openArtist(a);
            playable = false;
        } else {
            CatalogPlaylist p = (CatalogPlaylist) c.item;
            kind = DiscoverText.kind(p.isAlbum ? "Album" : "Playlist", p.owner);
            source = "Audius";
            Art.url(host, art, "pl" + p.id, p.name, p.artworkUrl, false);
            open = () -> openPlaylist(p);
            playable = false;
        }
        TextView k = Type.text(ctx, TypeScale.FOOTNOTE, roles.textSecondary, kind);
        Type.singleLine(k);
        text.addView(k, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 2, 0, 0));
        if (!source.isEmpty()) {
            TextView s = Type.text(ctx, TypeScale.FOOTNOTE, roles.textTertiary, source);
            Type.singleLine(s);
            text.addView(s, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 4, 0, 0));
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(dp(16));
        row.addView(text, tp);
        if (playable) {
            ImageView play = new ImageView(ctx);
            play.setScaleType(ImageView.ScaleType.CENTER);
            play.setImageDrawable(Icons.get(ctx, R.drawable.ic_play, roles.accent, dp(18)));
            play.setBackground(Ui.oval(roles.fill));
            play.setContentDescription("Play " + c.title);
            play.setOnClickListener(v -> open.run());
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(40), dp(40));
            pp.setMarginStart(dp(8));
            row.addView(play, pp);
        }
        row.setContentDescription(c.title + ", " + kind);
        row.setOnClickListener(v -> open.run());
        return row;
    }

    // ---- Opening and remembering --------------------------------------------------------------

    private void openArtist(CatalogArtist a) {
        recents.add(new RecentSearches.Entry(RecentSearches.ARTIST, a.id, a.name, "Artist", a.pictureUrl, a.name));
        saveRecents();
        host.openCatalogArtist(a.id, a.name);
    }

    private void openPlaylist(CatalogPlaylist p) {
        recents.add(new RecentSearches.Entry(RecentSearches.PLAYLIST, p.id, p.name, DiscoverText.joined(p.isAlbum ? "Album" : "Playlist", p.owner),
                p.artworkUrl, p.toJson().toString()));
        saveRecents();
        host.openCatalogPlaylist(p);
    }

    private void rememberSong(Track t) {
        recents.add(new RecentSearches.Entry(RecentSearches.SONG, t.id, t.title, DiscoverText.kind("Song", t.artist), t.artworkUrl, t.toJson().toString()));
        saveRecents();
    }

    private void saveRecents() {
        if (!recentsLoaded) return;
        String encoded = recents.encode();
        host.io().execute(() -> ctx.getSharedPreferences(PREFS, 0).edit().putString(RECENTS_KEY, encoded).apply());
    }

    // ---- Empty state: recents and categories --------------------------------------------------

    private void buildEmpty(List<Rows.Item> out) {
        out.add(title("Search"));
        List<RecentSearches.Entry> entries = recents.entries();
        if (!entries.isEmpty()) {
            out.add(recentsHeader());
            for (RecentSearches.Entry e : entries) out.add(recentItem(e));
        }
        out.add(header("Browse Categories", null, null));
        out.addAll(genreGrid(AudiusClient.genres(), g -> host.nav().push(new GenreScreen(g))));
    }

    private Rows.Item recentsHeader() {
        LinearLayout row = Ui.row(ctx);
        row.setPadding(dp(DesignTokens.MARGIN), dp(8), dp(4), 0);
        TextView t = Type.text(ctx, TypeScale.TITLE3, roles.textPrimary, "Recently Searched");
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        PillButton clear = new PillButton(ctx, roles, PillButton.TEXT, "Clear", 0);
        clear.setOnClickListener(v -> {
            Haptics.tick(v);
            recents.clear();
            saveRecents();
            render();
        });
        row.addView(clear);
        return Rows.of(row);
    }

    private Rows.Item recentItem(RecentSearches.Entry e) {
        boolean square = RecentSearches.SONG.equals(e.kind) || RecentSearches.PLAYLIST.equals(e.kind);
        return new Rows.Item() {
            @Override public int type() { return square ? Rows.RECENT_SQUARE : Rows.RECENT; }
            @Override public View create(ViewGroup parent) { return new RowView(ctx, roles, !square); }
            @Override public void bind(View v) {
                RowView row = (RowView) v;
                if (RecentSearches.QUERY.equals(e.kind)) {
                    row.iconImage(roles, R.drawable.ic_search);
                    row.bind(e.title, null);
                } else {
                    row.artImage();
                    row.bind(e.title, e.subtitle);
                    Track t = recentTrack(e);
                    if (t != null) Art.track(host, row.image(), t);
                    else Art.url(host, row.image(), "rc" + e.key, e.title, e.artworkUrl, !square);
                }
                row.trailing(R.drawable.ic_close, roles.textTertiary, "Remove " + e.title + " from recent searches")
                        .setOnClickListener(x -> { recents.remove(e.kind, e.key); saveRecents(); render(); });
                row.setOnClickListener(x -> openRecent(e));
            }
        };
    }

    private Track recentTrack(RecentSearches.Entry e) {
        if (!RecentSearches.SONG.equals(e.kind)) return null;
        Track cached = recentTracks.get(e.key);
        if (cached != null) return cached;
        try {
            Track t = Track.fromJson(new JSONObject(e.payload));
            if (t != null) { t = AudiusClient.get().refresh(t); recentTracks.put(e.key, t); }
            return t;
        } catch (JSONException | RuntimeException bad) {
            return null;
        }
    }

    private void openRecent(RecentSearches.Entry e) {
        switch (e.kind) {
            case RecentSearches.SONG: {
                Track t = recentTrack(e);
                if (t != null) { recents.add(e); saveRecents(); play(Collections.singletonList(t), 0, false); }
                break;
            }
            case RecentSearches.ARTIST:
                recents.add(e);
                saveRecents();
                host.openCatalogArtist(e.key, e.payload.isEmpty() ? e.title : e.payload);
                break;
            case RecentSearches.PLAYLIST:
                try {
                    CatalogPlaylist p = CatalogPlaylist.fromJson(new JSONObject(e.payload));
                    if (p != null) { recents.add(e); saveRecents(); host.openCatalogPlaylist(p); }
                } catch (JSONException bad) { /* damaged record: ignore the tap */ }
                break;
            default:
                host.setSearchQuery(e.title);
        }
    }

    // ---- Highlighting -------------------------------------------------------------------------

    /** Dims the parts of {@code text} that did not match the query, so the match reads as emphasis. */
    private CharSequence highlight(String text) {
        if (query.isEmpty()) return text;
        int[] r = SearchText.highlight(text, query);
        if (r.length == 0) return text;
        SpannableString s = new SpannableString(text);
        int muted = ColorMath.mix(roles.textPrimary, roles.textSecondary, 0.5f);
        int at = 0;
        for (int i = 0; i < r.length; i += 2) {
            if (r[i] > at) s.setSpan(new ForegroundColorSpan(muted), at, r[i], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            at = r[i + 1];
        }
        if (at < text.length()) s.setSpan(new ForegroundColorSpan(muted), at, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
}
