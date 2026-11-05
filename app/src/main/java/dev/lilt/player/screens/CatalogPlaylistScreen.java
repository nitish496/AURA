package dev.lilt.player.screens;

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
import dev.lilt.player.screens.discover.Rows;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.stream.CatalogPlaylist;
import dev.lilt.player.stream.SavedCatalog;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Audius playlist or album: artwork, title, owner, length, Play / Shuffle / Save and the track
 * list. A playlist already saved opens instantly from its offline snapshot, then refreshes.
 */
public final class CatalogPlaylistScreen extends DiscoverScreen {
    private final CatalogPlaylist playlist;
    private final QueryGate gate = new QueryGate();
    private List<Track> tracks = Collections.emptyList();
    private boolean loaded, failed;

    public CatalogPlaylistScreen(CatalogPlaylist playlist) { this.playlist = playlist; }

    @Override public String key() { return "cplaylist:" + playlist.id; }
    @Override public Tab tab() { return Tab.BROWSE; }

    @Override protected void onReady() {
        List<Track> snapshot = host.library().saved().playlistTracks(playlist.id);
        if (!snapshot.isEmpty()) { tracks = snapshot; loaded = true; }
        load();
    }

    @Override protected void onConnectivity(boolean nowOnline) {
        if (nowOnline && (failed || !loaded)) load(); else render();
    }

    @Override public void onLibraryChanged() { render(); }

    private void load() {
        if (!online) { failed = !loaded; render(); return; }
        failed = false;
        int gen = gate.begin(playlist.id);
        render();
        work(gate, gen, () -> AudiusClient.get(ctx).playlistTracks(playlist), found -> {
            tracks = found;
            loaded = true;
            SavedCatalog saved = host.library().saved();
            if (saved.containsPlaylist(playlist.id)) saved.updatePlaylistTracks(playlist.id, found);
            render();
        }, e -> { failed = !loaded; render(); });
    }

    @Override protected List<Rows.Item> build() {
        List<Rows.Item> out = new ArrayList<>();
        out.add(Rows.of(headerView()));
        if (!loaded) {
            if (failed) {
                out.add(online
                        ? stateBlock(R.drawable.ic_alert, "Couldn’t load songs", "The catalogue didn’t answer. Try again in a moment.", "Try Again", PillButton.SECONDARY, this::load)
                        : stateBlock(R.drawable.ic_cloud_off, "You’re offline", "Save this playlist while you’re online and it opens here without a connection.", null, 0, null));
            } else {
                out.addAll(skeletonSongs(Math.max(4, Math.min(8, playlist.trackCount))));
            }
            return out;
        }
        if (tracks.isEmpty()) {
            out.add(stateBlock(R.drawable.ic_song, "No playable songs", "Every song in this " + (playlist.isAlbum ? "album" : "playlist") + " is unavailable right now.", null, 0, null));
            return out;
        }
        for (int i = 0; i < tracks.size(); i++) out.add(song(tracks.get(i), tracks, i).album(false));
        return out;
    }

    private View headerView() {
        LinearLayout col = Ui.column(ctx);
        col.setPadding(dp(DesignTokens.MARGIN), dp(4), dp(DesignTokens.MARGIN), dp(16));

        LinearLayout top = Ui.row(ctx);
        top.setGravity(Gravity.TOP);
        int art = Math.min(dp(160), dp(Math.round(host.contentWidthDp() * 0.40f)));
        ImageView cover = new ImageView(ctx);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.roundOutline(cover, dpf(DesignTokens.artworkRadius(Ui.toDp(ctx, art))));
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Art.url(host, cover, "plh" + playlist.id, playlist.name, playlist.artworkUrl, false);
        cover.setOnLongClickListener(v -> { Haptics.longPress(v); host.showCollectionMenu(playlist.name, tracks, v); return true; });
        top.addView(cover, new LinearLayout.LayoutParams(art, art));

        LinearLayout text = Ui.column(ctx);
        TextView eyebrow = Type.text(ctx, TypeScale.CAPTION1, roles.textTertiary, playlist.isAlbum ? "ALBUM · AUDIUS" : "PLAYLIST · AUDIUS");
        eyebrow.setLetterSpacing(0.08f);
        text.addView(eyebrow);
        TextView name = Type.text(ctx, TypeScale.TITLE2, roles.textPrimary, playlist.name);
        Type.maxLines(name, 3);
        text.addView(name, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 4, 0, 0));
        if (!playlist.owner.isEmpty()) {
            TextView owner = Type.text(ctx, TypeScale.SUBHEAD, roles.textSecondary, playlist.owner);
            Type.singleLine(owner);
            if (!playlist.ownerId.isEmpty()) {
                owner.setOnClickListener(v -> host.openCatalogArtist(playlist.ownerId, playlist.owner));
                owner.setMinHeight(dp(32));
                owner.setGravity(Gravity.CENTER_VERTICAL);
            }
            text.addView(owner, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 2, 0, 0));
        }
        int count = loaded ? tracks.size() : playlist.trackCount;
        long total = 0;
        if (loaded) for (Track t : tracks) total += Math.max(0, t.duration);
        String meta = DiscoverText.collection(count, total);
        if (!meta.isEmpty()) {
            TextView m = Type.text(ctx, TypeScale.FOOTNOTE, roles.textTertiary, meta);
            Type.tabular(m);
            text.addView(m, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 6, 0, 0));
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(dp(16));
        top.addView(text, tp);
        col.addView(top);

        if (!playlist.description.isEmpty()) {
            TextView d = Type.text(ctx, TypeScale.FOOTNOTE, roles.textSecondary, playlist.description);
            Type.maxLines(d, 4);
            col.addView(d, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 16, 0, 0));
        }

        LinearLayout actions = Ui.row(ctx);
        PillButton play = new PillButton(ctx, roles, PillButton.PRIMARY, "Play", R.drawable.ic_play);
        play.setOnClickListener(v -> play(tracks, 0, false));
        PillButton shuffle = new PillButton(ctx, roles, PillButton.SECONDARY, "Shuffle", R.drawable.ic_shuffle);
        shuffle.setOnClickListener(v -> play(tracks, 0, true));
        play.setEnabled(loaded && !tracks.isEmpty());
        shuffle.setEnabled(loaded && !tracks.isEmpty());
        actions.addView(play, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sp.setMarginStart(dp(8));
        actions.addView(shuffle, sp);
        boolean saved = host.library().saved().containsPlaylist(playlist.id);
        ImageView save = iconPill(saved ? R.drawable.ic_check : R.drawable.ic_plus, saved ? roles.accent : roles.textPrimary,
                saved ? "Remove from Library" : "Save to Library", () -> toggleSave(saved));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(48), dp(48));
        vp.setMarginStart(dp(8));
        actions.addView(save, vp);
        col.addView(actions, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 16, 0, 0));
        return col;
    }

    private void toggleSave(boolean wasSaved) {
        SavedCatalog saved = host.library().saved();
        if (wasSaved) saved.removePlaylist(playlist.id);
        else saved.addPlaylist(playlist, loaded ? tracks : null);
        host.toast(wasSaved ? "Removed from Library" : "Saved to Library");
        render();
    }
}
