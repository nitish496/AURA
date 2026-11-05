package dev.lilt.player.app;

import dev.lilt.player.Track;
import java.util.Collections;
import java.util.List;

/** An album (or "Other songs") grouped from tracks. */
public final class AlbumGroup {
    public final String key, title, artist;
    public final int year;
    public final List<Track> tracks;
    public AlbumGroup(String key, String title, String artist, int year, List<Track> tracks) {
        this.key = key; this.title = title; this.artist = artist; this.year = year;
        this.tracks = Collections.unmodifiableList(tracks);
    }
    public Track cover() { return tracks.isEmpty() ? null : tracks.get(0); }
    public long durationMs() { long d = 0; for (Track t : tracks) d += Math.max(0, t.duration); return d; }
}
