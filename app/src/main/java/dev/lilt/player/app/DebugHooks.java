package dev.lilt.player.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import dev.lilt.player.Track;
import dev.lilt.player.screens.SearchScreen;
import dev.lilt.player.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Screenshot / test hooks for CI (scripts/ci/screens.txt). Only active in debuggable builds.
 *
 * <pre>
 * aura.screen  home|browse|search|library|album|artist|nowplaying|lyrics|queue|equalizer|stats|settings
 * aura.query   initial text of the Search field        aura.album   album title (case-insensitive)
 * aura.artist  artist name (local, else search)        aura.play    title substring: plays its album from there
 * aura.seek    position in ms (after play / current)   aura.theme   dark|light
 * </pre>
 * Work that needs the library scan or the player connection is retried until ready (20 s max).
 * Every aura.screen request starts from a clean state.
 */
public final class DebugHooks {
    private static boolean themeForced;

    private final Activity activity;
    private final Shell shell;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final boolean enabled;
    private Runnable pending;
    private long deadline;

    public DebugHooks(Activity activity, Shell shell) {
        this.activity = activity;
        this.shell = shell;
        this.enabled = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public void cancel() { main.removeCallbacksAndMessages(null); pending = null; }

    public void handle(Intent intent) {
        if (!enabled || intent == null) return;
        final String screen = intent.getStringExtra("aura.screen");
        final String play = intent.getStringExtra("aura.play");
        final String theme = intent.getStringExtra("aura.theme");
        if (screen == null && play == null && theme == null && !intent.hasExtra("aura.seek")) return;
        if (applyTheme(theme, screen != null || play != null)) return; // activity recreates and re-reads the intent
        cancel();
        final String query = intent.getStringExtra("aura.query"), album = intent.getStringExtra("aura.album"),
                artist = intent.getStringExtra("aura.artist");
        final long seek = intent.getLongExtra("aura.seek", -1);
        deadline = System.currentTimeMillis() + 20_000;
        if (screen != null) shell.debugReset(tabFor(screen));
        if (play != null) retry(() -> playMatching(play));
        retry(() -> showScreen(screen, query, album, artist));
        if (seek >= 0) retry(() -> seekWhenReady(seek));
        intent.removeExtra("aura.play");
        intent.removeExtra("aura.screen");
        intent.removeExtra("aura.seek");
    }

    /** Returns true when the theme had to change (the activity is recreating). */
    private boolean applyTheme(String theme, boolean hasScreen) {
        int wanted;
        if ("light".equals(theme)) { wanted = Theme.MODE_LIGHT; themeForced = true; }
        else if ("dark".equals(theme)) { wanted = Theme.MODE_DARK; themeForced = true; }
        else if (themeForced && hasScreen) { wanted = Theme.MODE_SYSTEM; themeForced = false; }
        else return false;
        if (Theme.mode(activity) == wanted) return false;
        Theme.setMode(activity, wanted);
        activity.recreate();
        return true;
    }

    private static Tab tabFor(String screen) {
        switch (screen) {
            case "browse": return Tab.BROWSE;
            case "library": case "album": case "artist": return Tab.LIBRARY;
            case "search": return Tab.SEARCH;
            default: return Tab.HOME;
        }
    }

    /** Runs {@code step} until it returns true or the deadline passes. */
    private void retry(final StepReturn step) {
        final Runnable[] self = new Runnable[1];
        self[0] = () -> {
            boolean done;
            try { done = step.run(); } catch (RuntimeException e) { done = true; }
            if (!done && System.currentTimeMillis() < deadline) main.postDelayed(self[0], 250);
        };
        main.post(self[0]);
    }

    private interface StepReturn { boolean run(); }

    private boolean showScreen(String screen, String query, String album, String artist) {
        if (screen == null) return true;
        switch (screen) {
            case "home": case "browse": case "library": return true;
            case "search":
                if (query != null) shell.setSearchQuery(query);
                return true;
            case "album": {
                if (!shell.libraryReady()) return false;
                AlbumGroup found = null;
                if (album != null) for (AlbumGroup a : shell.repoImpl().albums()) if (a.title.equalsIgnoreCase(album.trim())) { found = a; break; }
                if (found == null && album != null) for (AlbumGroup a : shell.repoImpl().albums()) if (a.title.toLowerCase(Locale.ROOT).contains(album.trim().toLowerCase(Locale.ROOT))) { found = a; break; }
                if (found == null && !shell.repoImpl().albums().isEmpty()) found = shell.repoImpl().albums().get(0);
                if (found != null) shell.openAlbum(found.key);
                return true;
            }
            case "artist": {
                if (!shell.libraryReady()) return false;
                ArtistGroup found = artist == null ? null : shell.repoImpl().artist(artist.trim());
                if (found != null) shell.openArtist(found.name);
                else shell.setSearchQuery(artist == null ? "" : artist);
                return true;
            }
            case "nowplaying": shell.openNowPlaying(); return true;
            case "lyrics": shell.openNowPlaying(); shell.openLyrics(); return true;
            case "queue": shell.openQueue(); return true;
            case "equalizer": shell.present(new dev.lilt.player.screens.SoundScreen()); return true;
            case "stats": shell.present(new dev.lilt.player.screens.ReplayScreen()); return true;
            case "settings": shell.present(new dev.lilt.player.screens.SettingsScreen()); return true;
            default: return true;
        }
    }

    private boolean playMatching(String title) {
        if (!shell.libraryReady() || !shell.hubImpl().connected()) return false;
        String needle = title.toLowerCase(Locale.ROOT);
        for (Track t : shell.repoImpl().localTracks()) {
            if (!t.title.toLowerCase(Locale.ROOT).contains(needle)) continue;
            AlbumGroup album = shell.repoImpl().album(t.albumKey());
            List<Track> queue = album == null ? new ArrayList<>(java.util.Collections.singletonList(t)) : album.tracks;
            int index = 0;
            for (int i = 0; i < queue.size(); i++) if (queue.get(i).id.equals(t.id)) { index = i; break; }
            shell.hubImpl().play(queue, index, false);
            return true;
        }
        return true;
    }

    private boolean seekWhenReady(long ms) {
        PlayerHubImpl hub = shell.hubImpl();
        if (!hub.connected() || hub.current() == null || hub.duration() <= 0) return false;
        hub.seek(ms);
        return true;
    }
}
