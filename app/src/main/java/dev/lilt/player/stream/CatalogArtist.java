package dev.lilt.player.stream;

/** An Audius artist (user) from search. Pure Java value object. */
public final class CatalogArtist {
    /** Raw Audius user id; pass to {@link AudiusClient#artistTracks(String, int)}. */
    public final String id;
    public final String name, handle;
    /** Best https profile picture, or null. Use {@link StreamArt#forSize(String, int)} for thumbnails. */
    public final String pictureUrl;
    public final int followers;
    public final boolean verified;
    /** Number of public tracks; 0 when unknown. */
    public final int trackCount;

    public CatalogArtist(String id, String name, String handle, String pictureUrl, int followers, boolean verified, int trackCount) {
        this.id = id;
        this.name = name;
        this.handle = handle == null ? "" : handle;
        this.pictureUrl = pictureUrl;
        this.followers = Math.max(0, followers);
        this.verified = verified;
        this.trackCount = Math.max(0, trackCount);
    }

    @Override public String toString() { return id + " " + name; }
}
