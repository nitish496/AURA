package dev.lilt.player.app;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import dev.lilt.player.Artwork;
import dev.lilt.player.Track;
import dev.lilt.player.stream.CatalogPlaylist;
import dev.lilt.player.ui.ColorRoles;
import java.util.List;
import java.util.concurrent.Executor;

/** Services the shell provides to every {@link Screen}. Main thread only unless noted. */
public interface AppHost {
    Activity activity();
    Navigator nav();
    PlayerHub player();
    LibraryRepo library();
    Artwork artwork();
    /** Shared background executor for I/O and network (bounded pool). Thread-safe. */
    Executor io();
    Handler main();
    ColorRoles colors();
    boolean dark();

    /** Brief glass toast/snackbar at the bottom above the mini player. */
    void toast(String message);

    /** Glass context menu for a track (play next, add to queue, save/like, playlist, album, artist, share). */
    void showTrackMenu(Track track, View anchor);
    /** Glass context menu for a list of tracks (album/playlist: play, shuffle, add to queue, save). */
    void showCollectionMenu(String title, List<Track> tracks, View anchor);

    void openNowPlaying();
    void openLyrics();
    void openQueue();
    void openAlbum(String albumKey);
    void openArtist(String artistName);
    /** Remote Audius artist page by user id (name for the title until loaded). */
    void openCatalogArtist(String userId, String name);
    void openCatalogPlaylist(CatalogPlaylist playlist);
    void openPlaylist(String name);

    /**
     * Report scrolling of the screen's main scroller (px offset from the top, change since the
     * last call; positive = scrolled down). The shell minimises the tab bar on scroll-down and
     * restores it on scroll-up ({@code ui.ScrollBehavior}). Ignored unless {@code source} is visible.
     */
    void onScroll(Screen source, int scrollY, int dy);

    /** Re-reads {@link Screen#lightSystemBars()} of the visible screen. */
    void invalidateSystemBars();

    /** Switches to the SEARCH tab and puts {@code query} into the bottom search field. */
    void setSearchQuery(String query);

    /** Theme preference values for {@link #setThemeMode(int)}. */
    int THEME_SYSTEM = 0, THEME_DARK = 1, THEME_LIGHT = 2;
    int themeMode();
    /** Persists the preference; visible screens get {@link Screen#onThemeChanged()}. */
    void setThemeMode(int mode);

    /** Asks for the media (audio files) permission, or opens App info after a permanent denial. */
    void requestMediaPermission();
}
