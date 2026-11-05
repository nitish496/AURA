package dev.lilt.player.app;

import dev.lilt.player.screens.*;
import dev.lilt.player.stream.CatalogPlaylist;

/** Rebuilds screens from their stable keys (state restoration after rotation or process death). */
final class Screens {
    private Screens() {}

    static Screen create(String key, Shell shell, String searchQuery) {
        if (key == null) return null;
        switch (key) {
            case "home": return new HomeScreen();
            case "browse": return new BrowseScreen();
            case "library": return new LibraryScreen();
            case "search": return new SearchScreen(searchQuery);
            case "nowplaying": return new NowPlayingScreen();
            case "lyrics": return new LyricsScreen();
            case "queue": return new QueueScreen();
            case "sound": return new SoundScreen();
            case "replay": return new ReplayScreen();
            case "settings": return new SettingsScreen();
            default: break;
        }
        int colon = key.indexOf(':');
        if (colon < 0) return null;
        String kind = key.substring(0, colon), arg = key.substring(colon + 1);
        switch (kind) {
            case "album": return new AlbumScreen(arg);
            case "artist": return new ArtistScreen(arg);
            case "playlist": return new PlaylistScreen(arg);
            case "songs": return new SongsScreen(arg);
            case "genre": return new GenreScreen(arg);
            case "cartist": return new CatalogArtistScreen(arg, "");
            case "cplaylist": {
                CatalogPlaylist p = shell.repoImpl().saved().playlist(arg);
                return new CatalogPlaylistScreen(p != null ? p : new CatalogPlaylist(arg, "", "", null, "", 0, "", false, ""));
            }
            default: return null;
        }
    }
}
