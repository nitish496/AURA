package dev.lilt.player.stream;

import org.json.JSONException;
import org.json.JSONObject;

/** An Audius playlist or album. Pure Java (org.json only) value object with JSON persistence. */
public final class CatalogPlaylist {
    /** Raw Audius playlist id; pass to {@link AudiusClient#playlistTracks(String)}. */
    public final String id;
    public final String name, owner;
    /** Best https artwork, or null. */
    public final String artworkUrl;
    public final String description;
    public final int trackCount;
    /** Owner's raw Audius user id ("" when unknown). */
    public final String ownerId;
    /** True for albums (their tracks then carry the album name instead of "Single"). */
    public final boolean isAlbum;
    /** Audius permalink path such as {@code /handle/playlist/name-123}; "" when unknown. */
    public final String permalink;

    public CatalogPlaylist(String id, String name, String owner, String artworkUrl, String description, int trackCount,
                           String ownerId, boolean isAlbum, String permalink) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.owner = owner == null ? "" : owner;
        this.artworkUrl = artworkUrl;
        this.description = description == null ? "" : description;
        this.trackCount = Math.max(0, trackCount);
        this.ownerId = ownerId == null ? "" : ownerId;
        this.isAlbum = isAlbum;
        this.permalink = permalink == null ? "" : permalink;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("name", name).put("owner", owner).put("description", description)
                    .put("trackCount", trackCount).put("ownerId", ownerId).put("isAlbum", isAlbum).put("permalink", permalink);
            if (artworkUrl != null) o.put("artworkUrl", artworkUrl);
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return o;
    }

    /** Restores {@link #toJson()} output; null when the record is unusable. */
    public static CatalogPlaylist fromJson(JSONObject o) {
        if (o == null) return null;
        String id = AudiusParser.text(o, "id");
        if (!AudiusParser.isId(id)) return null;
        String art = AudiusParser.text(o, "artworkUrl");
        return new CatalogPlaylist(id, AudiusParser.text(o, "name"), AudiusParser.text(o, "owner"),
                art.isEmpty() ? null : art, AudiusParser.text(o, "description"), (int) AudiusParser.number(o, "trackCount"),
                AudiusParser.text(o, "ownerId"), AudiusParser.flag(o, "isAlbum", false), AudiusParser.text(o, "permalink"));
    }

    @Override public String toString() { return id + " " + name; }
}
