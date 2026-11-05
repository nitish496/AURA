package dev.lilt.player.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.MenuAction;
import dev.lilt.player.app.Tab;
import dev.lilt.player.app.chrome.TopBar;
import dev.lilt.player.screens.library.Kit;
import dev.lilt.player.screens.library.ListScreen;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.EmptyState;
import dev.lilt.player.ui.PlaceholderDrawable;
import dev.lilt.player.ui.TimeText;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A local playlist (songs from the phone and saved streams): artwork mosaic, Play / Shuffle, rows with remove. */
public final class PlaylistScreen extends ListScreen {
    private final String name;
    private AlbumCard art;
    private TextView title, meta;
    private View pills;

    public PlaylistScreen(String name) { this.name = name; }

    @Override public String key() { return "playlist:" + name; }
    @Override public Tab tab() { return Tab.LIBRARY; }

    @Override protected CharSequence barTitle() { return name; }
    @Override protected boolean showLocalBadge() { return true; }

    @Override protected void buildTopBar(TopBar bar) {
        bar.addLeft(R.drawable.ic_back, "Back", () -> host.nav().pop());
        bar.addRight(R.drawable.ic_more, "More", () -> {
            List<MenuAction> extra = new ArrayList<>();
            extra.add(new MenuAction("Rename…", "edit", this::rename));
            extra.add(new MenuAction("Delete Playlist", "trash", this::delete, false, true));
            host.showCollectionMenu(name, host.library().playlistTracks(name), bar, extra);
        });
    }

    private void rename() {
        host.prompt("Rename Playlist", "Name", name, "Rename", text -> {
            if (text.equals(name)) return;
            if (host.library().renamePlaylist(name, text)) {
                host.toast("Renamed to “" + text + "”");
                host.nav().pop();
                host.openPlaylist(text);
            } else host.toast("That name is taken or too long");
        });
    }

    private void delete() {
        host.confirm("Delete “" + name + "”?", "Your songs stay on the phone.", "Delete", true, () -> {
            host.library().deletePlaylist(name);
            host.nav().pop();
        });
    }

    @Override protected View buildHeader() {
        LinearLayout col = Ui.column(context);
        col.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 8));
        LinearLayout top = Ui.row(context);
        top.setGravity(Gravity.TOP);
        art = AlbumCard.mosaic(context, 148);
        art.setClickable(false);
        top.addView(art, new LinearLayout.LayoutParams(-2, -2));
        // The mosaic card carries a title row we do not use here.
        art.bind("", "");
        LinearLayout text = Ui.column(context);
        text.setPadding(Ui.dp(context, 16), Ui.dp(context, 4), 0, 0);
        TextView eyebrow = Type.text(context, TypeScale.CAPTION2, roles.textTertiary, "PLAYLIST");
        eyebrow.setLetterSpacing(0.06f);
        title = Type.text(context, TypeScale.TITLE2, roles.textPrimary);
        Type.maxLines(title, 3);
        meta = Kit.footnote(context, roles, "");
        text.addView(eyebrow);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Ui.dp(context, 6);
        text.addView(title, tp);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
        mp.topMargin = Ui.dp(context, 6);
        text.addView(meta, mp);
        top.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(top);
        pills = Kit.pills(context, roles, () -> playAll(false), () -> playAll(true));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = Ui.dp(context, 20);
        col.addView(pills, pp);
        header = col;
        applyInsets();
        return col;
    }

    private LinearLayout header;

    private void applyInsets() {
        if (header == null) return;
        header.setPadding(Ui.dp(context, 16), insetTop + Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8));
        titleBottomPx = insetTop + Ui.dp(context, 8) + Ui.dp(context, 96);
    }

    @Override protected void onInsetsChanged() { applyInsets(); }

    @Override protected List<Object> buildItems() {
        List<Track> tracks = host.library().playlistTracks(name);
        List<Object> items = new ArrayList<>();
        title.setText(name);
        boolean exists = host.library().playlists().containsKey(name);
        long total = 0;
        for (Track t : tracks) total += Math.max(0, t.duration);
        meta.setText(tracks.isEmpty() ? "No songs" : TimeText.summary(tracks.size(), total, Locale.getDefault()));
        pills.setVisibility(tracks.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < 4; i++) {
            ImageView tile = art.tile(i);
            if (i < tracks.size()) host.artwork().load(tile, tracks.get(i));
            else { tile.setTag(null); tile.setImageDrawable(i == 0 ? new PlaceholderDrawable(context, roles, name, false) : null); }
        }
        if (!exists) {
            EmptyState e = new EmptyState(context, roles, R.drawable.ic_playlist, "This playlist was deleted", "");
            e.setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0);
            items.add(e);
        } else if (tracks.isEmpty()) {
            EmptyState e = new EmptyState(context, roles, R.drawable.ic_playlist_add, "Ready for your first song",
                    "Choose Add to Playlist… in a song’s menu to put it here.");
            e.setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0);
            items.add(e);
        } else {
            for (Track t : tracks) items.add(new TrackRow(t, 0, null));
            items.add(Kit.spacer(context, 8));
        }
        return items;
    }

    @Override protected List<MenuAction> rowExtras(TrackRow row) {
        List<MenuAction> extra = new ArrayList<>();
        extra.add(new MenuAction("Remove from Playlist", "minus", () -> host.library().removeFromPlaylist(name, row.track.id), false, true));
        return extra;
    }
}
