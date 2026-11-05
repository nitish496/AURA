package dev.lilt.player.stream;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses Audius API v1 responses ({@code {"data": ...}}) into pure value objects. Tolerant of
 * missing fields and JSON nulls (Android's org.json returns "null" from optString for JSON null,
 * so all reads go through null-safe helpers). Unplayable tracks are dropped here: deleted,
 * {@code is_streamable == false}, unavailable, stream-gated (purchase/follow) without access, or
 * owned by a deactivated user.
 */
final class AudiusParser {
    private AudiusParser() {}

    /** Album label for tracks that are not part of a known album. */
    static final String SINGLE = "Single";

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9]{1,32}");
    private static final Pattern HTTPS_ORIGIN = Pattern.compile("https://[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?(?::\\d{1,5})?");
    private static final Pattern LOCAL_ORIGIN = Pattern.compile("http://(?:127\\.0\\.0\\.1|localhost)(?::\\d{1,5})?");
    private static final String[] ARTWORK_SIZES = {"1000x1000", "480x480", "150x150"};

    static boolean isId(String id) { return id != null && ID.matcher(id).matches(); }

    /** Host list from {@code GET https://api.audius.co}: valid https origins only, de-duplicated, order kept. */
    static List<String> hosts(String body, boolean allowLocal) throws JSONException {
        JSONArray data = new JSONObject(body).optJSONArray("data");
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (data != null) for (int i = 0; i < data.length(); i++) {
            Object value = data.opt(i);
            String origin = value instanceof String ? origin((String) value, allowLocal) : null;
            if (origin != null) hosts.add(origin);
        }
        return new ArrayList<>(hosts);
    }

    /** Normalised origin ({@code https://host[:port]}, no trailing slash) or null when not acceptable. */
    static String origin(String url, boolean allowLocal) {
        if (url == null) return null;
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (HTTPS_ORIGIN.matcher(lower).matches()) return lower;
        if (allowLocal && LOCAL_ORIGIN.matcher(lower).matches()) return lower;
        return null;
    }

    /** Track list ({@code {"data":[...]}}). {@code album} labels every track (album context) or null. */
    static List<AudiusTrack> tracks(String body, String album) throws JSONException {
        JSONArray data = new JSONObject(body).optJSONArray("data");
        List<AudiusTrack> tracks = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (data != null) for (int i = 0; i < data.length(); i++) {
            AudiusTrack track = track(data.optJSONObject(i), album);
            if (track != null && seen.add(track.id)) tracks.add(track);
        }
        return tracks;
    }

    /** Single-track response ({@code {"data":{...}}}); null when missing or unplayable. */
    static AudiusTrack singleTrack(String body) throws JSONException {
        return track(new JSONObject(body).optJSONObject("data"), null);
    }

    /** One track object, or null when it cannot be played. */
    static AudiusTrack track(JSONObject o, String album) {
        if (o == null) return null;
        if (flag(o, "is_delete", false) || !flag(o, "is_streamable", true) || !flag(o, "is_available", true)) return null;
        JSONObject access = o.optJSONObject("access");
        if (access != null && access.has("stream") && !flag(access, "stream", true)) return null;
        if (flag(o, "is_stream_gated", false) && (access == null || !flag(access, "stream", false))) return null;
        JSONObject user = o.optJSONObject("user");
        if (user != null && (flag(user, "is_deactivated", false) || !flag(user, "is_available", true))) return null;
        String id = text(o, "id"), title = text(o, "title");
        if (!isId(id) || title.isEmpty()) return null;
        String artist = user == null ? "" : text(user, "name");
        if (artist.isEmpty() && user != null) artist = text(user, "handle");
        String artistId = user == null ? "" : text(user, "id");
        String label = album;
        if (label == null || label.trim().isEmpty()) {
            JSONObject backlink = o.optJSONObject("album_backlink");
            label = backlink == null ? "" : text(backlink, "playlist_name");
        }
        double seconds = decimal(o, "duration");
        return new AudiusTrack(id, title, artist, isId(artistId) ? artistId : "", user == null ? "" : text(user, "handle"),
                label, artwork(o.opt("artwork")), text(o, "genre"), text(o, "mood"), text(o, "permalink"),
                Math.round(seconds * 1000), number(o, "play_count"));
    }

    /** User search results; deactivated accounts are skipped. */
    static List<CatalogArtist> users(String body) throws JSONException {
        JSONArray data = new JSONObject(body).optJSONArray("data");
        List<CatalogArtist> users = new ArrayList<>();
        if (data != null) for (int i = 0; i < data.length(); i++) {
            JSONObject u = data.optJSONObject(i);
            if (u == null || flag(u, "is_deactivated", false) || !flag(u, "is_available", true)) continue;
            String id = text(u, "id");
            String name = text(u, "name");
            if (name.isEmpty()) name = text(u, "handle");
            if (!isId(id) || name.isEmpty()) continue;
            users.add(new CatalogArtist(id, name, text(u, "handle"), artwork(u.opt("profile_picture")),
                    (int) Math.min(Integer.MAX_VALUE, number(u, "follower_count")), flag(u, "is_verified", false),
                    (int) Math.min(Integer.MAX_VALUE, number(u, "track_count"))));
        }
        return users;
    }

    /** Playlist/album results; deleted, private, gated and empty collections are skipped. */
    static List<CatalogPlaylist> playlists(String body) throws JSONException {
        JSONArray data = new JSONObject(body).optJSONArray("data");
        List<CatalogPlaylist> playlists = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (data != null) for (int i = 0; i < data.length(); i++) {
            CatalogPlaylist playlist = playlist(data.optJSONObject(i));
            if (playlist != null && seen.add(playlist.id)) playlists.add(playlist);
        }
        return playlists;
    }

    static CatalogPlaylist playlist(JSONObject p) {
        if (p == null || flag(p, "is_delete", false) || flag(p, "is_private", false)) return null;
        JSONObject access = p.optJSONObject("access");
        if (access != null && access.has("stream") && !flag(access, "stream", true)) return null;
        if (flag(p, "is_stream_gated", false) && (access == null || !flag(access, "stream", false))) return null;
        String id = text(p, "id"), name = text(p, "playlist_name");
        if (!isId(id) || name.isEmpty()) return null;
        int count = (int) Math.min(Integer.MAX_VALUE, number(p, "track_count"));
        JSONArray contents = p.optJSONArray("playlist_contents");
        if (count == 0 && contents != null) count = contents.length();
        boolean countKnown = p.opt("track_count") instanceof Number || contents != null;
        if (countKnown && count == 0) return null;
        JSONObject user = p.optJSONObject("user");
        String ownerId = user == null ? "" : text(user, "id");
        String owner = user == null ? "" : text(user, "name");
        if (owner.isEmpty() && user != null) owner = text(user, "handle");
        return new CatalogPlaylist(id, name, owner, artwork(p.opt("artwork")), text(p, "description"), count,
                isId(ownerId) ? ownerId : "", flag(p, "is_album", false), text(p, "permalink"));
    }

    /** Largest https variant from an Audius artwork object ({@code {"150x150":..,"480x480":..,"1000x1000":..}}). */
    static String artwork(Object artwork) {
        if (!(artwork instanceof JSONObject)) return null;
        JSONObject sizes = (JSONObject) artwork;
        for (String size : ARTWORK_SIZES) {
            String url = text(sizes, size);
            if (StreamArt.isRemote(url)) return url;
        }
        return null;
    }

    /** String value; "" for missing, JSON null or non-scalar values. Numbers are rendered without ".0". */
    static String text(JSONObject o, String key) {
        Object value = o.opt(key);
        if (value == null || JSONObject.NULL.equals(value) || value instanceof JSONObject || value instanceof JSONArray) return "";
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        }
        return String.valueOf(value).trim();
    }

    static boolean flag(JSONObject o, String key, boolean fallback) {
        Object value = o.opt(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            if ("true".equalsIgnoreCase((String) value)) return true;
            if ("false".equalsIgnoreCase((String) value)) return false;
        }
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return fallback;
    }

    static long number(JSONObject o, String key) {
        double value = decimal(o, key);
        return value <= 0 ? 0 : (long) Math.min(Long.MAX_VALUE, value);
    }

    static double decimal(JSONObject o, String key) {
        Object value = o.opt(key);
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? 0 : d;
        }
        if (value instanceof String) {
            try {
                double d = Double.parseDouble(((String) value).trim());
                return Double.isNaN(d) || Double.isInfinite(d) ? 0 : d;
            } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }
}
