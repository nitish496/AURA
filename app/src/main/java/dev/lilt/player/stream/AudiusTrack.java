package dev.lilt.player.stream;

/**
 * One playable Audius track as parsed from the API (pure Java, no Android types). Converted to a
 * {@code dev.lilt.player.Track} by {@link AudiusClient}; the stream URL is built at that point so
 * it always uses the currently healthy host.
 */
public final class AudiusTrack {
    /** Raw Audius id such as {@code "D7KyD"} (no {@code audius:} prefix). */
    public final String id;
    public final String title, artist, artistId, artistHandle, album, genre, mood, permalink;
    /** Best available https artwork (1000x1000, else 480x480, else 150x150); null when none. */
    public final String artworkUrl;
    public final long durationMs, playCount;

    public AudiusTrack(String id, String title, String artist, String artistId, String artistHandle, String album,
                       String artworkUrl, String genre, String mood, String permalink, long durationMs, long playCount) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.artistId = artistId == null ? "" : artistId;
        this.artistHandle = artistHandle == null ? "" : artistHandle;
        this.album = album == null || album.trim().isEmpty() ? AudiusParser.SINGLE : album.trim();
        this.artworkUrl = artworkUrl;
        this.genre = genre == null ? "" : genre.trim();
        this.mood = mood == null ? "" : mood.trim();
        this.permalink = permalink == null ? "" : permalink;
        this.durationMs = Math.max(0, durationMs);
        this.playCount = Math.max(0, playCount);
    }

    /** App-wide track id: {@code "audius:" + id}. */
    public String trackId() { return AudiusApi.ID_PREFIX + id; }

    AudiusTrack withAlbum(String newAlbum) {
        return new AudiusTrack(id, title, artist, artistId, artistHandle, newAlbum, artworkUrl, genre, mood, permalink, durationMs, playCount);
    }

    @Override public String toString() { return trackId() + " " + artist + " - " + title; }
}
