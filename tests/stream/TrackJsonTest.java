package dev.lilt.player.stream;

import dev.lilt.player.Track;
import org.json.JSONObject;

/** Track remote factory, persistence round trip and remote album keys (android-all Uri + org.json). */
public final class TrackJsonTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static void equal(Object expected, Object actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected <" + expected + "> but was <" + actual + ">");
        checks++;
    }

    public static void main(String[] args) throws Exception {
        String stream = "https://dn1.example/v1/tracks/Lb9Ey/stream?app_name=AURA";
        Track remote = Track.remote("Lb9Ey", "Low Tide Signals", "Marlow Fields", "", stream,
                "https://cn.example/content/abc/1000x1000.jpg", 213_000, "Electronic", "nlGNe", "/marlowfields/low-tide-signals");
        equal("audius:Lb9Ey", remote.id, "prefix added");
        check(remote.isRemote(), "remote");
        equal(Track.SOURCE_AUDIUS, remote.source, "source");
        equal("Single", remote.album, "blank album is Single");
        equal(stream, remote.uri.toString(), "uri is the stream URL");
        equal(213_000L, remote.duration, "duration ms");
        equal("https://cn.example/content/abc/480x480.jpg", remote.artworkUrl(300), "thumbnail variant");
        equal("https://audius.co/marlowfields/low-tide-signals", remote.webUrl(), "share link");
        equal("remote-single:audius:Lb9Ey", remote.albumKey(), "singles never merge");
        Track sibling = Track.remote("audius:Qx2Wm", "Other", "Marlow Fields", null, stream, null, 0, null);
        check(!remote.albumKey().equals(sibling.albumKey()), "two singles are two collections");
        Track albumTrack = Track.remote("audius:Ab1", "A", "Juniper Static", "Night Ferries", stream, null, 1, "");
        Track albumTrack2 = Track.remote("audius:Ab2", "B", "Juniper Static", "Night Ferries", stream, null, 1, "");
        Track local = new Track("content://media/external/audio/media/7", "C", "Juniper Static", "Night Ferries",
                android.net.Uri.parse("content://media/external/audio/media/7"), 1000, 4, 1, 99, "Juniper Static");
        equal(albumTrack.albumKey(), albumTrack2.albumKey(), "remote album groups its tracks");
        check(!albumTrack.albumKey().equals(local.albumKey()), "remote album never merges with a local album");
        equal("title:13:night ferries", local.albumKey(), "local album key unchanged");
        check(!local.isRemote() && local.artworkUrl == null && local.genre.isEmpty() && local.artworkUrl(100) == null && local.webUrl() == null, "local defaults");

        Track restored = Track.fromJson(new JSONObject(remote.toJson().toString()));
        equal(remote.id + remote.title + remote.artist + remote.album + remote.uri + remote.duration + remote.artworkUrl + remote.genre + remote.artistId + remote.permalink,
                restored.id + restored.title + restored.artist + restored.album + restored.uri + restored.duration + restored.artworkUrl + restored.genre + restored.artistId + restored.permalink,
                "remote JSON round trip");
        Track localBack = Track.fromJson(new JSONObject(local.toJson().toString()));
        equal(local.id + local.uri + local.albumId + local.trackNumber + local.dateAdded + local.albumArtist + local.duration,
                localBack.id + localBack.uri + localBack.albumId + localBack.trackNumber + localBack.dateAdded + localBack.albumArtist + localBack.duration, "local JSON round trip");
        check(!localBack.isRemote(), "local stays local");
        equal(null, Track.fromJson(new JSONObject("{\"id\":\"audius:x\"}")), "missing uri rejected");
        equal(null, Track.fromJson(new JSONObject("{\"id\":\"content://x\",\"uri\":\"content://x\",\"source\":\"audius\"}")), "remote source needs a remote id");
        equal(null, Track.fromJson(new JSONObject("{\"id\":\"audius:x\",\"uri\":\"https://h/v1/tracks/x/stream\",\"source\":\"local\"}")), "local source with a remote id rejected");
        Track lenient = Track.fromJson(new JSONObject("{\"id\":\"audius:x\",\"uri\":\"https://h/v1/tracks/x/stream\",\"title\":null,\"artist\":null,\"artworkUrl\":null}"));
        check(lenient != null && lenient.isRemote() && lenient.title.equals("Untitled track") && lenient.artist.equals("Unknown artist") && lenient.artworkUrl == null, "nulls and missing source tolerated");

        Track moved = remote.withStreamUrl("https://gateway.example/v1/tracks/Lb9Ey/stream?app_name=AURA");
        equal("https://gateway.example/v1/tracks/Lb9Ey/stream?app_name=AURA", moved.uri.toString(), "stream URL replaced");
        equal(remote.id + remote.artworkUrl + remote.artistId, moved.id + moved.artworkUrl + moved.artistId, "metadata kept");
        check(remote.withStreamUrl(stream) == remote && local.withStreamUrl("https://x") == local, "no copy when unchanged or local");
        try { Track.remote("audius:x", "t", "a", "b", " ", null, 0, ""); throw new AssertionError("blank stream URL accepted"); } catch (IllegalArgumentException expected) { checks++; }
        System.out.println(checks + " track model tests passed");
    }
}
