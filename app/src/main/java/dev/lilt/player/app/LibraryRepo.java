package dev.lilt.player.app;

import dev.lilt.player.Track;
import dev.lilt.player.stream.SavedCatalog;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the user's library: on-device songs plus streamed songs saved to
 * the library (no download needed), favourites, playlists (mixed local/remote ids) and history.
 * Reads are cheap in-memory snapshots on the main thread; scans happen on a background thread.
 */
public interface LibraryRepo {
    interface Listener { void onLibraryChanged(); }

    boolean loading();
    boolean hasPermission();
    void refresh();

    /** On-device tracks, title order. */
    List<Track> localTracks();
    /** Local + saved streamed tracks, title order. */
    List<Track> allTracks();
    List<AlbumGroup> albums();
    List<ArtistGroup> artists();
    AlbumGroup album(String key);
    ArtistGroup artist(String name);

    /** Any known track by id (local, saved remote, or recently seen remote). */
    Track find(String id);

    boolean isFavourite(String id);
    void setFavourite(Track track, boolean favourite);
    List<Track> favourites();

    Map<String, List<String>> playlists();
    void createPlaylist(String name);
    void addToPlaylist(String name, Track track);
    void removeFromPlaylist(String name, String trackId);
    void deletePlaylist(String name);
    List<Track> playlistTracks(String name);

    SavedCatalog saved();
    boolean isSaved(String id);
    void setSaved(Track track, boolean saved);

    List<Track> recentlyPlayed(int limit);
    List<Track> recentlyAdded(int limit);
    List<Track> mostPlayed(int limit);
    void clearHistory();

    /** Offline ranked search over local + saved tracks. Off the main thread is fine too. */
    List<Track> searchLocal(String query, int limit);

    void addListener(Listener listener);
    void removeListener(Listener listener);
}
