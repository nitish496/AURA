package dev.lilt.player.screens;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import dev.lilt.player.screens.discover.Rows;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.stream.CatalogArtist;
import dev.lilt.player.stream.CatalogPlaylist;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Audius artist: circular picture, name with verified mark, followers, Play / Shuffle,
 * popular songs (five, then "See all" expands in place) and the artist's playlists when found.
 * The profile and playlists load beside the songs and fill in when they arrive.
 */
public final class CatalogArtistScreen extends DiscoverScreen {
    private static final int TOP = 5, FETCH = 50;

    private final String userId, name;
    private final QueryGate gate = new QueryGate();
    private List<Track> tracks = Collections.emptyList();
    private List<CatalogPlaylist> playlists = Collections.emptyList();
    private CatalogArtist profile;
    private boolean loaded, failed, expanded;

    public CatalogArtistScreen(String userId, String name) { this.userId = userId; this.name = name == null ? "" : name; }

    @Override public String key() { return "cartist:" + userId; }
    @Override public Tab tab() { return Tab.BROWSE; }

    @Override protected void onReady() { load(); }

    @Override protected void onConnectivity(boolean nowOnline) {
        if (nowOnline && !loaded) load(); else render();
    }

    private String displayName() { return profile != null ? profile.name : name; }

    private void load() {
        if (!online) { failed = !loaded; render(); return; }
        failed = false;
        int gen = gate.begin(userId);
        render();
        work(gate, gen, () -> AudiusClient.get(ctx).artistTracks(userId, FETCH), found -> {
            tracks = found;
            loaded = true;
            render();
        }, e -> { failed = !loaded; render(); });
        if (!name.isEmpty()) {
            work(gate, gen, () -> {
                for (CatalogArtist a : AudiusClient.get(ctx).searchArtists(name, 10)) if (a.id.equals(userId)) return a;
                return null;
            }, found -> { if (found != null) { profile = found; render(); } }, null);
            work(gate, gen, () -> {
                List<CatalogPlaylist> mine = new ArrayList<>();
                for (CatalogPlaylist p : AudiusClient.get(ctx).searchPlaylists(name, 25)) if (userId.equals(p.ownerId)) mine.add(p);
                return mine;
            }, found -> { playlists = found; render(); }, null);
        }
    }

    @Override protected List<Rows.Item> build() {
        List<Rows.Item> out = new ArrayList<>();
        out.add(Rows.of(headerView()));
        if (!loaded) {
            if (failed) {
                out.add(online
                        ? stateBlock(R.drawable.ic_alert, "Couldn’t load songs", "The catalogue didn’t answer. Try again in a moment.", "Try Again", PillButton.SECONDARY, this::load)
                        : stateBlock(R.drawable.ic_cloud_off, "You’re offline", "Artist pages need a connection. Songs you saved still play from your library.", null, 0, null));
            } else {
                out.add(skeletonHeader());
                out.addAll(skeletonSongs(5));
            }
            return out;
        }
        if (tracks.isEmpty()) {
            out.add(stateBlock(R.drawable.ic_song, "No songs to stream", "This artist has no playable songs on Audius right now.", null, 0, null));
        } else {
            boolean more = tracks.size() > TOP && !expanded;
            out.add(header(expanded ? "Songs" : "Popular Songs", null, more ? () -> { expanded = true; render(); } : null));
            int shown = expanded ? tracks.size() : Math.min(TOP, tracks.size());
            for (int i = 0; i < shown; i++) out.add(song(tracks.get(i), tracks, i).album(true));
        }
        if (!playlists.isEmpty()) {
            out.add(header("Playlists", null, null));
            out.add(shelf(playlists.size(), cardDp(2.3f, 136, 200), false, new CardBinder() {
                @Override public void bind(AlbumCard card, int i) {
                    CatalogPlaylist p = playlists.get(i);
                    card.bind(p.name, p.isAlbum ? "Album" : DiscoverText.collection(p.trackCount, 0));
                    Art.placeholder(card.cover(), p.name, false);
                }
                @Override public void art(AlbumCard card, int i) {
                    CatalogPlaylist p = playlists.get(i);
                    Art.url(host, card.cover(), "pl" + p.id, p.name, p.artworkUrl, false);
                }
                @Override public void click(int i, View card) { host.openCatalogPlaylist(playlists.get(i)); }
            }));
        }
        return out;
    }

    private View headerView() {
        LinearLayout col = Ui.column(ctx);
        col.setPadding(dp(DesignTokens.MARGIN), dp(4), dp(DesignTokens.MARGIN), dp(8));
        LinearLayout top = Ui.row(ctx);
        int size = dp(104);
        ImageView picture = new ImageView(ctx);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.ovalOutline(picture);
        picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Art.url(host, picture, "ah" + userId, displayName(), profile == null ? null : profile.pictureUrl, true);
        top.addView(picture, new LinearLayout.LayoutParams(size, size));

        LinearLayout text = Ui.column(ctx);
        LinearLayout nameLine = Ui.row(ctx);
        TextView title = Type.text(ctx, TypeScale.TITLE1, roles.textPrimary, displayName());
        Type.maxLines(title, 2);
        nameLine.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (profile != null && profile.verified) {
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(18), dp(18));
            vp.setMarginStart(dp(6));
            nameLine.addView(verifiedMark(), vp);
        }
        text.addView(nameLine);
        String followers = profile == null ? "" : DiscoverText.followersOnAudius(profile.followers);
        if (!followers.isEmpty()) {
            TextView f = Type.text(ctx, TypeScale.FOOTNOTE, roles.textSecondary, followers);
            Type.tabular(f);
            text.addView(f, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 4, 0, 0));
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(dp(16));
        top.addView(text, tp);
        col.addView(top);

        LinearLayout actions = Ui.row(ctx);
        boolean ready = loaded && !tracks.isEmpty();
        PillButton play = new PillButton(ctx, roles, PillButton.PRIMARY, "Play", R.drawable.ic_play);
        play.setOnClickListener(v -> play(tracks, 0, false));
        play.setEnabled(ready);
        PillButton shuffle = new PillButton(ctx, roles, PillButton.SECONDARY, "Shuffle", R.drawable.ic_shuffle);
        shuffle.setOnClickListener(v -> play(tracks, 0, true));
        shuffle.setEnabled(ready);
        actions.addView(play, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sp.setMarginStart(dp(8));
        actions.addView(shuffle, sp);
        col.addView(actions, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 16, 0, 0));
        return col;
    }

    /** Ember disc with a check, the verified mark. */
    private View verifiedMark() {
        FrameLayout mark = new FrameLayout(ctx);
        mark.setBackground(Ui.oval(roles.accent));
        ImageView check = new ImageView(ctx);
        check.setImageDrawable(Icons.get(ctx, R.drawable.ic_check, roles.onAccent, dp(12)));
        mark.addView(check, new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.CENTER));
        mark.setContentDescription("Verified");
        return mark;
    }
}
