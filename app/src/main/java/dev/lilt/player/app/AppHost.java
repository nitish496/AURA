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
}
