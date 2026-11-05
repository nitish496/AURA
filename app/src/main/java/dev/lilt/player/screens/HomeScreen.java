package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.AlbumGroup;
import dev.lilt.player.app.MixRules;
import dev.lilt.player.app.Tab;
import dev.lilt.player.app.chrome.MixArt;
import dev.lilt.player.app.chrome.TopBar;
import dev.lilt.player.audio.ListeningLedger;
import dev.lilt.player.audio.ListeningStats;
import dev.lilt.player.screens.library.Kit;
import dev.lilt.player.screens.library.ListScreen;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.ArtworkPalette;
import dev.lilt.player.ui.EmptyState;
import dev.lilt.player.ui.FeatureCard;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.ShelfView;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Home: "Top Picks for You" mixes built from real listening data, Recently Played, Recently Added
 * and (when the streaming catalogue is shown) Trending on Audius. Content only; the glass layer
 * is the shell's top bar, tab bar and mini player.
 */
public final class HomeScreen extends ListScreen {
    private static final int[] MIX_TINTS = {0xFF16403B, 0xFF1D4357, 0xFF5A2224, 0xFF6B4E1A};

    private static final class Mix {
        final String eyebrow, title;
        final List<Track> tracks;
        final int tint;
        Mix(String eyebrow, String title, List<Track> tracks, int tint) { this.eyebrow = eyebrow; this.title = title; this.tracks = tracks; this.tint = tint; }
    }

    private LinearLayout header;
    private List<Mix> mixes = new ArrayList<>();
    private List<Track> trending = new ArrayList<>();
    private boolean mixesLoading, trendingLoading, trendingTried;
    private boolean destroyed;
    private long lastMixesAt, lastTrendingTry;

    public HomeScreen() {}

    @Override public String key() { return "home"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override protected CharSequence barTitle() { return "Home"; }

    @Override protected void buildTopBar(TopBar bar) {
        bar.addRight(R.drawable.ic_account, "Settings", () -> host.nav().push(new SettingsScreen()));
    }

    @Override protected View buildHeader() {
        header = Ui.column(context);
        TextView title = Kit.largeTitle(context, roles, "Home");
        header.addView(title);
        applyHeaderInsets();
        return header;
    }

    private void applyHeaderInsets() {
        if (header == null) return;
        int top = Math.max(0, insetTop - Ui.dp(context, dev.lilt.player.DesignTokens.TOP_BAR)) + Ui.dp(context, 6);
        header.setPadding(Ui.dp(context, 16), top, Ui.dp(context, 64), 0);
        titleBottomPx = top + Ui.dp(context, 40);
    }

    @Override protected void onInsetsChanged() { applyHeaderInsets(); }

    @Override public void onShow() { loadMixes(); loadTrending(); }

    @Override public void onLibraryChanged() { loadMixes(); super.onLibraryChanged(); }

    @Override public void onDestroy() { destroyed = true; super.onDestroy(); }

    // ---- data

    private void loadMixes() {
        if (mixesLoading || context == null) return;
        long nowMs = android.os.SystemClock.elapsedRealtime();
        if (!mixes.isEmpty() && nowMs - lastMixesAt < 30_000) return;
        lastMixesAt = nowMs;
        mixesLoading = true;
        final long now = System.currentTimeMillis();
        host.io().execute(() -> {
            List<ListeningLedger.TrackStat> recent = new ArrayList<>(), all = new ArrayList<>();
            try {
                ListeningStats s = ListeningStats.get(context);
                recent = s.topTracks(100, now - MixRules.RECENT_MS);
                all = s.topTracks(1000, 0);
            } catch (RuntimeException ignored) { }
            final List<ListeningLedger.TrackStat> r = recent, a = all;
            host.main().post(() -> {
                mixesLoading = false;
                if (destroyed) return;
                mixes = computeMixes(r, a, now);
                refreshItems();
            });
        });
    }

    private List<Mix> computeMixes(List<ListeningLedger.TrackStat> recent, List<ListeningLedger.TrackStat> all, long now) {
        List<Mix> out = new ArrayList<>();
        java.util.function.Predicate<String> playable = id -> host.library().find(id) != null;

        List<String> recentIds = new ArrayList<>();
        for (ListeningLedger.TrackStat t : recent) recentIds.add(t.id);
        List<Track> heavy = resolve(MixRules.heavyRotation(recentIds, playable));
        if (!heavy.isEmpty()) out.add(new Mix("Made for You", "Heavy Rotation", heavy, MIX_TINTS[0]));

        List<Track> favs = host.library().favourites();
        if (favs.size() >= MixRules.MIN_MIX) {
            List<String> ids = new ArrayList<>();
            Map<String, String> albumOf = new HashMap<>();
            for (Track t : favs) { ids.add(t.id); albumOf.put(t.id, t.albumKey()); }
            long seed = MixRules.daySeed(now, TimeZone.getDefault().getOffset(now));
            out.add(new Mix("Your Favourites", "Favourites Mix", resolve(MixRules.spread(ids, albumOf, seed)), MIX_TINTS[1]));
        }

        List<MixRules.Played> played = new ArrayList<>();
        for (ListeningLedger.TrackStat t : all) played.add(new MixRules.Played(t.id, t.plays, t.lastPlayedAt));
        List<Track> back = resolve(MixRules.rediscover(played, now, playable));
        if (!back.isEmpty()) out.add(new Mix("Because you played them", "Rediscover", back, MIX_TINTS[2]));

        String[] moods = {"Chill", "Focus", "Workout", "Party", "Sleep"};
        for (String m : moods) {
            List<Track> t = host.library().moodTracks(m);
            if (t.size() >= MixRules.MIN_MIX) { out.add(new Mix("Mood", m + " Mix", new ArrayList<>(t), MIX_TINTS[out.size() % MIX_TINTS.length])); break; }
        }
        return out;
    }

    private List<Track> resolve(List<String> ids) {
        List<Track> out = new ArrayList<>();
        for (String id : ids) { Track t = host.library().find(id); if (t != null) out.add(t); }
        return out;
    }

    private void loadTrending() {
        if (trendingTried || trendingLoading || context == null || !SettingsScreen.showCatalogue(context)) return;
        long t = android.os.SystemClock.elapsedRealtime();
        if (lastTrendingTry != 0 && t - lastTrendingTry < 60_000) return;
        lastTrendingTry = t;
        trendingLoading = true;
        host.io().execute(() -> {
            List<Track> found = null;
            try { found = AudiusClient.get(context).trending(null, null, 20); } catch (Exception ignored) { }
            final List<Track> result = found;
            host.main().post(() -> {
                trendingLoading = false;
                if (destroyed) return;
                if (result != null && !result.isEmpty()) { trending = result; trendingTried = true; refreshItems(); }
            });
        });
    }

    // ---- items

    @Override protected List<Object> buildItems() {
        List<Object> items = new ArrayList<>();
        boolean hasMusic = !host.library().allTracks().isEmpty();
        if (!hasMusic && trending.isEmpty()) {
            items.add(emptyState());
            return items;
        }
        List<AlbumGroup> recent = albumsOf(host.library().recentlyPlayed(40), 12);
        if (!recent.isEmpty()) {
            items.add(Kit.section(context, roles, "Recently Played", () -> host.nav().push(new SongsScreen("recent"))));
            items.add(Kit.albumShelf(host, recent));
        }
        List<AlbumGroup> added = albumsOf(host.library().recentlyAdded(60), 12);
        if (!added.isEmpty()) {
            items.add(Kit.section(context, roles, "Recently Added", () -> host.nav().push(new SongsScreen("added"))));
            items.add(Kit.albumShelf(host, added));
        }
        final List<Track> liked = new ArrayList<>(host.library().favourites());
        if (!liked.isEmpty()) {
            items.add(Kit.section(context, roles, "Liked Songs", () -> host.nav().push(new SongsScreen("favourites"))));
            items.add(trackShelf(liked.size() > 30 ? new ArrayList<>(liked.subList(0, 30)) : liked));
        }
        if (!mixes.isEmpty()) {
            items.add(Kit.section(context, roles, "Top Picks for You", null));
            items.add(mixShelf());
        }
        if (!trending.isEmpty() && SettingsScreen.showCatalogue(context)) {
            items.add(Kit.section(context, roles, "Trending on Audius", null));
            items.add(trackShelf(trending));
        }
        items.add(Kit.spacer(context, 16));
        return items;
    }

    private View emptyState() {
        EmptyState e;
        if (!host.library().hasPermission()) e = EmptyState.permission(context, () -> host.requestMediaPermission(), () -> host.nav().switchTab(Tab.BROWSE));
        else e = EmptyState.noMusic(context, () -> host.library().refresh(), () -> host.nav().switchTab(Tab.BROWSE));
        return e;
    }

    /** Distinct albums of {@code tracks}, in order, from the library where possible. */
    private List<AlbumGroup> albumsOf(List<Track> tracks, int limit) {
        Map<String, AlbumGroup> out = new LinkedHashMap<>();
        for (Track t : tracks) {
            String key = t.albumKey();
            if (out.containsKey(key)) continue;
            AlbumGroup g = host.library().album(key);
            if (g == null) {
                List<Track> one = new ArrayList<>();
                one.add(t);
                g = new AlbumGroup(key, t.album, t.artist, 0, one);
            }
            out.put(key, g);
            if (out.size() >= limit) break;
        }
        return new ArrayList<>(out.values());
    }

    private View mixShelf() {
        final int dp = ShelfMetrics.featureCard(host.contentWidthDp());
        ShelfView shelf = new ShelfView(context);
        shelf.setAdapter(new ShelfView.Adapter() {
            @Override public int count() { return mixes.size(); }
            @Override public View create(ViewGroup parent) { return new FeatureCard(context, roles, dp); }
            @Override public void bind(View v, int i) {
                final Mix m = mixes.get(i);
                FeatureCard card = (FeatureCard) v;
                List<String> names = new ArrayList<>();
                for (Track t : m.tracks) { names.add(t.title); if (names.size() == 6) break; }
                card.bind(m.eyebrow, m.title, MixRules.summary(names));
                card.setPalette(ArtworkPalette.from(new int[] {m.tint}));
                MixArt art = new MixArt(context, m.title, m.tint);
                card.cover().setImageDrawable(art);
                card.setOnClickListener(x -> { Haptics.click(x); host.player().play(new ArrayList<>(m.tracks), 0, false); });
                card.setOnLongClickListener(x -> { Haptics.longPress(x); host.showCollectionMenu(m.title, m.tracks, x); return true; });
            }
        });
        return shelf;
    }

    private View trackShelf(final List<Track> tracks) {
        final int dp = Kit.cardDp(host);
        ShelfView shelf = new ShelfView(context);
        shelf.setAdapter(new ShelfView.Adapter() {
            @Override public int count() { return tracks.size(); }
            @Override public View create(ViewGroup parent) { return new AlbumCard(context, roles, dp, false, false); }
            @Override public void bind(View v, int i) {
                final Track t = tracks.get(i);
                AlbumCard card = (AlbumCard) v;
                card.bind(t.title, t.artist);
                card.setOnClickListener(x -> { Haptics.click(x); host.player().play(new ArrayList<>(tracks), i, false); });
                card.setOnLongClickListener(x -> { Haptics.longPress(x); host.showTrackMenu(t, x); return true; });
            }
            @Override public void onVisible(View v, int i) { host.artwork().load(((AlbumCard) v).cover(), tracks.get(i)); }
        });
        return shelf;
    }
}
