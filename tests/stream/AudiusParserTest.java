package dev.lilt.player.stream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

/** Audius v1 response parsing against fixtures shaped like the public API. */
public final class AudiusParserTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static void equal(Object expected, Object actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected <" + expected + "> but was <" + actual + ">");
        checks++;
    }
    private static String fixture(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("tests/fixtures/audius/" + name)), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        // Host discovery: https origins only, normalised, de-duplicated, order kept.
        List<String> hosts = AudiusParser.hosts(fixture("hosts.json"), false);
        equal(Arrays.asList("https://discoveryprovider.audius.co", "https://discoveryprovider2.audius.co", "https://discoveryprovider3.audius.co",
                "https://audius-discovery-1.altego.net", "https://dn1.monophonic.digital", "https://audius-dp.singapore.creatorseed.com"), hosts, "host list");
        equal(null, AudiusParser.origin("http://127.0.0.1:8080", false), "local hosts only in tests");
        equal("http://127.0.0.1:8080", AudiusParser.origin("http://127.0.0.1:8080/", true), "local test origin");
        equal(null, AudiusParser.origin("https://evil.example/@x", false), "no paths or userinfo");

        // Trending: unplayable tracks dropped, duplicates removed, order kept.
        List<AudiusTrack> trending = AudiusParser.tracks(fixture("trending.json"), null);
        StringBuilder ids = new StringBuilder();
        for (AudiusTrack t : trending) ids.append(t.id).append(' ');
        equal("Lb9Ey 7eP5n Qx2Wm Gh6Lk ", ids.toString(), "skips unstreamable, deleted, gated, deactivated-owner and duplicate tracks");
        AudiusTrack low = trending.get(0);
        equal("audius:Lb9Ey", low.trackId(), "app id");
        equal("Low Tide Signals", low.title, "title");
        equal("Marlow Fields", low.artist, "artist is user.name");
        equal("nlGNe", low.artistId, "artist id");
        equal("marlowfields", low.artistHandle, "handle");
        equal(213_000L, low.durationMs, "duration seconds to ms");
        equal("Electronic", low.genre, "genre");
        equal("Single", low.album, "no album -> Single");
        equal("https://creatornode2.audius.co/content/bafyartlb9ey/1000x1000.jpg", low.artworkUrl, "largest artwork");
        equal("/marlowfields/low-tide-signals", low.permalink, "permalink");
        equal(48_211L, low.playCount, "play count");
        AudiusTrack paper = trending.get(1);
        equal("https://creatornode2.audius.co/content/bafyart7ep5n/480x480.jpg", paper.artworkUrl, "480 when 1000 is missing");
        equal("Night Ferries", paper.album, "album_backlink names the album");
        AudiusTrack north = trending.get(2);
        equal(187_400L, north.durationMs, "fractional seconds");
        equal("", north.genre, "JSON null genre is empty, never \"null\"");
        equal(null, north.artworkUrl, "no artwork object");
        equal("Energizing", north.mood, "mood");

        // Album context labels every track; it wins over backlinks.
        List<AudiusTrack> album = AudiusParser.tracks(fixture("playlist_tracks.json"), "Harbour Hours");
        equal(3, album.size(), "playlist tracks");
        for (AudiusTrack t : album) equal("Harbour Hours", t.album, "album context " + t.id);
        equal("Night Ferries", AudiusParser.tracks(fixture("playlist_tracks.json"), null).get(0).album, "backlink without context");

        // Single track responses.
        equal("7eP5n", AudiusParser.singleTrack(fixture("track.json")).id, "single track");
        equal(null, AudiusParser.singleTrack(fixture("track_gated.json")), "purchase-gated single track is unplayable");
        equal(null, AudiusParser.singleTrack("{\"data\":null}"), "null data");
        equal(0, AudiusParser.tracks("{\"data\":null}", null).size(), "null data list");
        equal(0, AudiusParser.tracks("{}", null).size(), "missing data list");

        // Search tracks.
        List<AudiusTrack> found = AudiusParser.tracks(fixture("search_tracks.json"), null);
        equal(2, found.size(), "search drops unstreamable");
        equal("Glasshouse", found.get(0).title, "search order kept");

        // Users.
        List<CatalogArtist> users = AudiusParser.users(fixture("users_search.json"));
        equal(3, users.size(), "deactivated user skipped");
        CatalogArtist marlow = users.get(0);
        equal("nlGNe", marlow.id, "user id");
        equal("Marlow Fields", marlow.name, "user name");
        equal("marlowfields", marlow.handle, "user handle");
        equal(11141, marlow.followers, "followers");
        check(marlow.verified, "verified");
        equal(14, marlow.trackCount, "track count");
        equal("https://creatornode2.audius.co/content/bafypicnlgne/1000x1000.jpg", marlow.pictureUrl, "largest picture");
        equal("https://creatornode2.audius.co/content/bafypictz1qa/150x150.jpg", users.get(1).pictureUrl, "only 150 available");
        check(!users.get(1).verified, "unverified");
        equal(null, users.get(2).pictureUrl, "no picture");

        // Playlists and albums.
        List<CatalogPlaylist> playlists = AudiusParser.playlists(fixture("playlists_search.json"));
        equal(2, playlists.size(), "empty, deleted, private and purchase-gated collections skipped");
        CatalogPlaylist nightFerries = playlists.get(0);
        equal("DOPRl", nightFerries.id, "playlist id");
        equal("Night Ferries", nightFerries.name, "playlist_name");
        equal("Juniper Static", nightFerries.owner, "owner name");
        equal("eAZl3", nightFerries.ownerId, "owner id");
        check(nightFerries.isAlbum, "album flag");
        equal(3, nightFerries.trackCount, "track count");
        equal("Late trains and harbour lights.", nightFerries.description, "description");
        equal("https://creatornode2.audius.co/content/bafypldoprl/1000x1000.jpg", nightFerries.artworkUrl, "playlist artwork");
        check(!playlists.get(1).isAlbum, "plain playlist");
        equal(2, AudiusParser.playlists(fixture("playlists_trending.json")).size(), "trending playlists");

        // Saved-playlist persistence round trip.
        CatalogPlaylist restored = CatalogPlaylist.fromJson(new JSONObject(nightFerries.toJson().toString()));
        equal(nightFerries.id + nightFerries.name + nightFerries.owner + nightFerries.artworkUrl + nightFerries.description + nightFerries.trackCount
                        + nightFerries.ownerId + nightFerries.isAlbum + nightFerries.permalink,
                restored.id + restored.name + restored.owner + restored.artworkUrl + restored.description + restored.trackCount
                        + restored.ownerId + restored.isAlbum + restored.permalink, "playlist JSON round trip");
        equal(null, CatalogPlaylist.fromJson(new JSONObject("{\"id\":\"../x\"}")), "invalid playlist id rejected");

        // Null-safe field helpers (Android's optString would return "null").
        JSONObject odd = new JSONObject("{\"a\":null,\"b\":12.0,\"c\":\" x \",\"d\":{},\"e\":\"true\",\"f\":\"NaN\",\"g\":1}");
        equal("", AudiusParser.text(odd, "a"), "null text");
        equal("12", AudiusParser.text(odd, "b"), "integral double text");
        equal("x", AudiusParser.text(odd, "c"), "trimmed text");
        equal("", AudiusParser.text(odd, "d"), "object is not text");
        check(AudiusParser.flag(odd, "e", false) && AudiusParser.flag(odd, "g", false) && AudiusParser.flag(odd, "missing", true), "flags");
        equal(0.0, AudiusParser.decimal(odd, "f"), "NaN is zero");

        // Ids, stream URLs and artwork variants.
        check(AudiusParser.isId("D7KyD") && !AudiusParser.isId("a/b") && !AudiusParser.isId("") && !AudiusParser.isId(null), "id validation");
        equal("D7KyD", AudiusApi.rawId("audius:D7KyD"), "strip prefix");
        equal("https://h.example/v1/tracks/D7KyD/stream?app_name=AURA", AudiusApi.streamUrlOn("https://h.example", "audius:D7KyD"), "stream URL");
        equal("audius:D7KyD", AudiusApi.trackIdFromStreamUrl("https://h.example/v1/tracks/D7KyD/stream?app_name=AURA"), "id from stream URL");
        equal(null, AudiusApi.trackIdFromStreamUrl("https://h.example/content/x/480x480.jpg"), "artwork is not a stream");
        equal("https://h.example:8443", AudiusApi.hostOf("https://H.example:8443/v1/tracks/x/stream"), "host of URL");
        equal("allTime", AudiusApi.normalizeTime("allTime"), "time allTime");
        equal("week", AudiusApi.normalizeTime("fortnight"), "unknown time is week");
        String big = "https://creatornode2.audius.co/content/bafyartlb9ey/1000x1000.jpg";
        equal("https://creatornode2.audius.co/content/bafyartlb9ey/480x480.jpg", StreamArt.thumb(big), "thumb");
        equal("https://creatornode2.audius.co/content/bafyartlb9ey/150x150.jpg", StreamArt.forSize(big, 128), "small bucket");
        equal("https://creatornode2.audius.co/content/bafyartlb9ey/480x480.jpg", StreamArt.forSize(big, 384), "medium bucket");
        equal(big, StreamArt.forSize(big, 900), "player size keeps the original");
        equal("https://x.example/a/480x480.jpg?sig=1", StreamArt.thumb("https://x.example/a/1000x1000.jpg?sig=1"), "query kept");
        equal("https://x.example/cover.png", StreamArt.thumb("https://x.example/cover.png"), "other URLs unchanged");
        equal(null, StreamArt.thumb(null), "null safe");
        check(StreamArt.isRemote("HTTPS://x") && !StreamArt.isRemote("content://media/1") && !StreamArt.isRemote(null), "remote art scheme");
        System.out.println(checks + " Audius parsing tests passed");
    }
}
