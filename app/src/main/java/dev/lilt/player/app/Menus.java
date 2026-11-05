package dev.lilt.player.app;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import dev.lilt.player.MoodStore;
import dev.lilt.player.Track;
import dev.lilt.player.ui.BottomSheet;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.ListRow;
import dev.lilt.player.ui.Toggle;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the track and collection context menus and the playlist / mood sheets for the shell. */
final class Menus {
    private final Shell shell;
    private final Activity activity;
    private final ColorRoles roles;

    Menus(Shell shell) {
        this.shell = shell;
        this.activity = shell.activity();
        this.roles = shell.colors0();
    }

    private LibraryRepoImpl repo() { return shell.repoImpl(); }
    private PlayerHubImpl hub() { return shell.hubImpl(); }

    void track(Track t, View anchor, List<MenuAction> extra) {
        List<MenuAction> a = new ArrayList<>();
        a.add(new MenuAction("Play Next", "play_next", () -> { hub().playNext(t); shell.toast("Playing Next"); }));
        a.add(new MenuAction("Add to Queue", "add_to_queue", () -> { hub().addToQueue(t); shell.toast("Added to Queue"); }));
        final boolean fav = repo().isFavourite(t.id);
        a.add(new MenuAction(fav ? "Remove Favourite" : "Favourite", fav ? "heart_fill" : "heart", () -> repo().setFavourite(t, !fav)));
        if (t.isRemote()) {
            final boolean saved = repo().isSaved(t.id);
            a.add(new MenuAction(saved ? "Remove from Library" : "Save to Library", saved ? "cloud_check" : "cloud", () -> {
                repo().setSaved(t, !saved);
                shell.toast(saved ? "Removed from Library" : "Saved to Library");
            }));
        }
        a.add(new MenuAction("Add to Playlist…", "playlist_add", () -> playlistPicker(Collections.singletonList(t))));
        if (!t.isRemote()) a.add(new MenuAction("Moods…", "waveform", () -> moods(t)));
        boolean first = true;
        if (repo().album(t.albumKey()) != null) {
            a.add(new MenuAction("Go to Album", "album", () -> shell.openAlbum(t.albumKey())).group());
            first = false;
        }
        if (!t.isRemote() && repo().artist(t.artist) != null) {
            MenuAction go = new MenuAction("Go to Artist", "artist", () -> shell.openArtist(t.artist));
            a.add(first ? go.group() : go);
            first = false;
        } else if (t.isRemote() && !t.artistId.isEmpty()) {
            MenuAction go = new MenuAction("Go to Artist", "artist", () -> shell.openCatalogArtist(t.artistId, t.artist));
            a.add(first ? go.group() : go);
            first = false;
        }
        MenuAction share = new MenuAction("Share…", "share", () -> share(t));
        a.add(first ? share.group() : share);
        if (extra != null) {
            boolean firstExtra = true;
            for (MenuAction e : extra) { a.add(firstExtra ? e.group() : e); firstExtra = false; }
        }
        shell.showMenu(anchor, a);
    }

    void collection(String title, List<Track> tracks, View anchor, List<MenuAction> extra) {
        if (tracks == null) return;
        if (tracks.isEmpty() && (extra == null || extra.isEmpty())) return;
        List<MenuAction> a = new ArrayList<>();
        if (!tracks.isEmpty()) {
            a.add(new MenuAction("Play", "play", () -> hub().play(tracks, 0, false)));
            a.add(new MenuAction("Shuffle", "shuffle", () -> hub().play(tracks, 0, true)));
            a.add(new MenuAction("Play Next", "play_next", () -> { hub().playAllNext(tracks); shell.toast("Playing Next"); }));
            a.add(new MenuAction("Add to Queue", "add_to_queue", () -> { hub().addAllToQueue(tracks); shell.toast("Added to Queue"); }));
            a.add(new MenuAction("Add to Playlist…", "playlist_add", () -> playlistPicker(tracks)).group());
        }
        final List<Track> unsaved = new ArrayList<>();
        for (Track t : tracks) if (t.isRemote() && !repo().isSaved(t.id)) unsaved.add(t);
        if (!unsaved.isEmpty()) {
            a.add(new MenuAction("Save to Library", "cloud", () -> {
                for (Track t : unsaved) repo().setSaved(t, true);
                shell.toast("Saved to Library");
            }));
        }
        if (extra != null) {
            boolean firstExtra = true;
            for (MenuAction e : extra) { a.add(firstExtra && !a.isEmpty() ? e.group() : e); firstExtra = false; }
        }
        shell.showMenu(anchor, a);
    }

    private void share(Track t) {
        String text = t.title + " — " + t.artist;
        String web = t.webUrl();
        if (web != null) text += "\n" + web;
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        try { activity.startActivity(Intent.createChooser(send, null)); } catch (RuntimeException ignored) { shell.toast("Nothing to share with"); }
    }

    /** Sheet: New Playlist… plus the existing playlists. */
    void playlistPicker(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        Map<String, List<String>> lists = repo().playlists();
        BottomSheet sheet = new BottomSheet(activity, roles, "Add to Playlist", null);
        sheet.addOption("New Playlist…", false, () -> shell.prompt("New Playlist", "Name", "", "Create", name -> {
            String clean = LibraryRepoImpl.cleanName(name);
            if (clean == null) { shell.toast("Use a name up to 80 characters"); return; }
            if (repo().playlists().containsKey(clean)) { shell.toast("A playlist named “" + clean + "” already exists"); return; }
            repo().createPlaylist(clean);
            repo().addAllToPlaylist(clean, tracks);
            shell.toast("Added to " + clean);
        }));
        if (!lists.isEmpty()) {
            LinearLayout rows = Ui.column(activity);
            int count = 0;
            for (Map.Entry<String, List<String>> e : lists.entrySet()) {
                final String name = e.getKey();
                ListRow row = new ListRow(activity, roles).title(name).value(Integer.toString(e.getValue().size()));
                row.setPaddingRelative(0, 0, Ui.dp(activity, 4), 0);
                row.setOnClickListener(v -> {
                    sheet.dismiss();
                    repo().addAllToPlaylist(name, tracks);
                    shell.toast("Added to " + name);
                });
                row.separator(++count < lists.size());
                rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            ScrollView scroll = new ScrollView(activity);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
            int max = Ui.dp(activity, 52) * Math.min(lists.size(), 5);
            sheet.addContent(scroll);
            scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.min(max, Ui.dp(activity, 260))));
        }
        shell.showSheet(sheet);
    }

    /** Sheet with one toggle per mood for a song. */
    void moods(Track t) {
        BottomSheet sheet = new BottomSheet(activity, roles, "Moods", "Mixes are built from the songs you tag. Kept on this phone.");
        final Set<String> selected = new LinkedHashSet<>(repo().moods(t.id));
        List<String> labels = MoodStore.labels();
        for (int i = 0; i < labels.size(); i++) {
            final String mood = labels.get(i);
            ListRow row = new ListRow(activity, roles).title(mood);
            row.setPaddingRelative(0, 0, Ui.dp(activity, 4), 0);
            Toggle toggle = row.toggle(selected.contains(mood));
            toggle.setListener((x, on) -> {
                if (on) selected.add(mood); else selected.remove(mood);
                repo().setMoods(t.id, selected);
            });
            row.separator(i < labels.size() - 1);
            sheet.addContent(row);
        }
        shell.showSheet(sheet);
    }
}
