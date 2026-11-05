package dev.lilt.player.app;

import dev.lilt.player.Track;
import java.util.Collections;
import java.util.List;

/** An artist with their tracks and albums from the library. */
public final class ArtistGroup {
    public final String name;
    public final List<Track> tracks;
    public final List<AlbumGroup> albums;
    public ArtistGroup(String name, List<Track> tracks, List<AlbumGroup> albums) {
        this.name = name;
        this.tracks = Collections.unmodifiableList(tracks);
        this.albums = Collections.unmodifiableList(albums);
    }
    public Track cover() { return tracks.isEmpty() ? null : tracks.get(0); }
}
