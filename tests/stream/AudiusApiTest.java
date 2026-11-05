package dev.lilt.player.stream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * AudiusApi end to end against loopback servers: discovery, parallel probe (fastest healthy host
 * wins), failover on 5xx to the next host and then the gateway, 4xx without failover, in-memory
 * TTL cache, gzip, app_name on every call, host memory. Plus HostPool policy with a fake clock.
 */
public final class AudiusApiTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static void equal(Object expected, Object actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected <" + expected + "> but was <" + actual + ">");
        checks++;
    }
    private static byte[] fixture(String name) throws IOException { return Files.readAllBytes(Paths.get("tests/fixtures/audius/" + name)); }

    /** Minimal HTTP/1.1 server on 127.0.0.1 (java.base only). One request per connection. */
    static final class LocalServer implements AutoCloseable {
        interface Handler { Reply handle(String target, String userAgent) throws Exception; }
        static final class Reply {
            final int code; final byte[] body; final String type; boolean gzip; long delayMs;
            Reply(int code, byte[] body, String type) { this.code = code; this.body = body; this.type = type; }
            static Reply json(byte[] body) { return new Reply(200, body, "application/json"); }
            static Reply status(int code) { return new Reply(code, ("{\"error\":" + code + "}").getBytes(StandardCharsets.UTF_8), "application/json"); }
            Reply gzipped() { gzip = true; return this; }
            Reply delayed(long ms) { delayMs = ms; return this; }
        }
        private final ServerSocket socket;
        private final ExecutorService workers = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "test-http"); t.setDaemon(true); return t; });
        final List<String> requests = Collections.synchronizedList(new ArrayList<>());
        final List<String> agents = Collections.synchronizedList(new ArrayList<>());
        volatile Handler handler;

        LocalServer(Handler handler) throws IOException {
            this.handler = handler;
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(() -> {
                while (!socket.isClosed()) {
                    try { Socket client = socket.accept(); workers.execute(() -> serve(client)); } catch (IOException closed) { return; }
                }
            }, "test-http-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String origin() { return "http://127.0.0.1:" + socket.getLocalPort(); }

        private void serve(Socket client) {
            try (Socket s = client) {
                s.setSoTimeout(5000);
                InputStream in = s.getInputStream();
                ByteArrayOutputStream head = new ByteArrayOutputStream();
                int b, matched = 0;
                while (matched < 4 && (b = in.read()) != -1) {
                    head.write(b);
                    matched = (b == '\r' || b == '\n') ? matched + 1 : 0;
                }
                String[] lines = head.toString("ISO-8859-1").split("\r\n");
                String target = lines[0].split(" ")[1];
                String agent = "";
                for (String line : lines) if (line.toLowerCase().startsWith("user-agent:")) agent = line.substring(11).trim();
                requests.add(target);
                agents.add(agent);
                Reply reply;
                try { reply = handler.handle(target, agent); } catch (Exception e) { reply = Reply.status(500); }
                if (reply.delayMs > 0) Thread.sleep(reply.delayMs);
                byte[] body = reply.body;
                if (reply.gzip) {
                    ByteArrayOutputStream zipped = new ByteArrayOutputStream();
                    try (GZIPOutputStream gz = new GZIPOutputStream(zipped)) { gz.write(body); }
                    body = zipped.toByteArray();
                }
                OutputStream out = s.getOutputStream();
                String headers = "HTTP/1.1 " + reply.code + " X\r\nContent-Type: " + reply.type + "\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n" + (reply.gzip ? "Content-Encoding: gzip\r\n" : "") + "\r\n";
                out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
            } catch (Exception ignored) {
                // Client gave up (timeouts are part of the tests).
            }
        }

        @Override public void close() throws IOException { socket.close(); workers.shutdownNow(); }
    }

    /** Serves the catalogue fixtures for the v1 paths the client uses. */
    static LocalServer.Reply catalogue(String target) throws IOException {
        String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
        if (target.startsWith("/v1/tracks/trending") && target.contains("genre=")) return LocalServer.Reply.json(fixture("genre_trending.json"));
        if (path.equals("/v1/tracks/trending")) return LocalServer.Reply.json(fixture("trending.json"));
        if (path.equals("/v1/tracks/search")) return LocalServer.Reply.json(fixture("search_tracks.json"));
        if (path.equals("/v1/users/search")) return LocalServer.Reply.json(fixture("users_search.json"));
        if (path.equals("/v1/playlists/search")) return LocalServer.Reply.json(fixture("playlists_search.json")).gzipped();
        if (path.equals("/v1/playlists/trending")) return LocalServer.Reply.json(fixture("playlists_trending.json"));
        if (path.equals("/v1/playlists/DOPRl/tracks")) return LocalServer.Reply.json(fixture("playlist_tracks.json"));
        if (path.equals("/v1/users/nlGNe/tracks")) return LocalServer.Reply.json(fixture("user_tracks.json"));
        if (path.equals("/v1/tracks/7eP5n")) return LocalServer.Reply.json(fixture("track.json"));
        return LocalServer.Reply.status(404);
    }

    public static void main(String[] args) throws Exception {
        hostPoolPolicy();
        firstHealthyIsParallel();
        mixRelated();
        storedStreamUrls();
        endToEnd();
        System.out.println(checks + " Audius client tests passed");
    }

    private static void hostPoolPolicy() {
        HostPool pool = new HostPool("https://gateway.example");
        long t = 1_000_000;
        check(pool.current(t) == null && pool.discoveryDue(t), "empty pool needs discovery");
        pool.discoveryAttempted(t);
        check(!pool.discoveryDue(t + 1_000), "discovery retries are rate limited");
        pool.setCandidates(Arrays.asList("https://a.example", "https://b.example", "https://gateway.example", "https://a.example"), t);
        equal(Arrays.asList("https://a.example", "https://b.example"), pool.candidates(), "candidates de-duplicated, gateway excluded");
        check(!pool.discoveryDue(t + HostPool.DISCOVERY_RETRY_MS), "fresh list needs no discovery");
        pool.use("https://a.example", t);
        equal("https://a.example", pool.current(t + 10), "selected host sticks");
        pool.markFailed("https://a.example", t + 20);
        equal(null, pool.current(t + 21), "failed host is dropped");
        equal(Collections.singletonList("https://b.example"), pool.probeOrder(t + 21, 4, new Random(1)), "cooling host not probed");
        // Time only moves forward below: cooldowns are evaluated against the time asked about.
        pool.markFailed("https://b.example", t + 30);
        long gatewayAt = t + 30 + HostPool.DISCOVERY_RETRY_MS;
        check(pool.discoveryDue(gatewayAt), "all candidates cooling: rediscover");
        check(pool.probeOrder(gatewayAt, 4, new Random(1)).isEmpty(), "nothing usable while cooling");
        pool.use("https://gateway.example", gatewayAt);
        equal("https://gateway.example", pool.current(gatewayAt + 1), "gateway fallback in use");
        long later = gatewayAt + Math.max(HostPool.GATEWAY_STICKY_MS, HostPool.FAILURE_COOLDOWN_MS);
        equal(null, pool.current(later), "gateway is temporary; hosts retried later");
        equal(2, pool.probeOrder(later, 4, new Random(1)).size(), "cooldowns expire");
        equal(1, pool.probeOrder(later, 1, new Random(1)).size(), "fan-out limit");
        check(pool.discoveryDue(t + HostPool.DISCOVERY_MAX_AGE_MS + 1), "old host list refreshed");
    }

    private static void firstHealthyIsParallel() {
        ExecutorService executor = Executors.newCachedThreadPool(r -> { Thread th = new Thread(r); th.setDaemon(true); return th; });
        HostPool.Probe probe = host -> {
            switch (host) {
                case "fast-bad": Thread.sleep(10); return false;
                case "throws": throw new IOException("refused");
                case "fast-good": Thread.sleep(80); return true;
                case "slow-good": Thread.sleep(600); return true;
                default: Thread.sleep(2_000); return true;
            }
        };
        long start = System.nanoTime();
        equal("fast-good", HostPool.firstHealthy(Arrays.asList("slow-good", "fast-bad", "throws", "fast-good"), probe, executor, 1_000), "fastest healthy host wins");
        check((System.nanoTime() - start) / 1_000_000 < 500, "probes run in parallel");
        equal(null, HostPool.firstHealthy(Arrays.asList("fast-bad", "throws"), probe, executor, 1_000), "no healthy host");
        start = System.nanoTime();
        equal(null, HostPool.firstHealthy(Collections.singletonList("hangs"), probe, executor, 150), "probe timeout");
        check((System.nanoTime() - start) / 1_000_000 < 1_000, "timeout bounds the wait");
        equal(null, HostPool.firstHealthy(Collections.<String>emptyList(), probe, executor, 100), "nothing to probe");
        executor.shutdownNow();
    }

    private static void storedStreamUrls() {
        AtomicLong clock = new AtomicLong(5_000_000L);
        Http unused = new Http("AURA-test", 100, 100, 1024);
        AudiusApi api = new AudiusApi("http://127.0.0.1:9", "https://api.example", unused, unused, 100, clock::get, new Random(1), true);
        String stored = "https://old.example/v1/tracks/Lb9Ey/stream?app_name=AURA";
        equal(stored, api.freshStreamUrl("audius:Lb9Ey", stored), "no host known yet: a stored URL is kept");
        equal("https://api.example/v1/tracks/Lb9Ey/stream?app_name=AURA", api.freshStreamUrl("audius:Lb9Ey", "https://old.example/v1/tracks/Other/stream"), "mismatched stored URL replaced");
        equal("https://api.example/v1/tracks/Lb9Ey/stream?app_name=AURA", api.freshStreamUrl("audius:Lb9Ey", "content://media/1"), "non-stream URL replaced");
        api.hostFailed(stored);
        equal("https://api.example/v1/tracks/Lb9Ey/stream?app_name=AURA", api.freshStreamUrl("audius:Lb9Ey", stored), "failed stored host moves to the gateway");
        api.restore("https://dn2.example", Collections.singletonList("https://dn2.example"), clock.get());
        equal("https://dn2.example/v1/tracks/Lb9Ey/stream?app_name=AURA", api.freshStreamUrl("audius:Lb9Ey", stored), "known healthy host wins");
    }

    private static AudiusTrack t(String id, String artist) {
        return new AudiusTrack(id, "Song " + id, artist, "", "", null, null, "", "", "", 1000, 0);
    }

    private static void mixRelated() {
        List<AudiusTrack> byArtist = Arrays.asList(t("seed", "A"), t("a1", "A"), t("a2", "A"));
        List<AudiusTrack> byGenre = Arrays.asList(t("g1", "G"), t("a1", "A"), t("g2", "G"), t("g3", "G"));
        StringBuilder ids = new StringBuilder();
        for (AudiusTrack x : AudiusApi.mixRelated(byArtist, byGenre, "seed", 10)) ids.append(x.id).append(' ');
        equal("g1 a1 a2 g2 g3 ", ids.toString(), "interleaved, seed and duplicates removed");
        equal(2, AudiusApi.mixRelated(byArtist, byGenre, "seed", 2).size(), "limit");
        equal(0, AudiusApi.mixRelated(Collections.<AudiusTrack>emptyList(), Collections.<AudiusTrack>emptyList(), null, 5).size(), "empty");
    }

    private static void endToEnd() throws Exception {
        AtomicLong clock = new AtomicLong(1_700_000_000_000L);
        try (LocalServer bad = new LocalServer((target, agent) -> LocalServer.Reply.status(500));
             LocalServer slow = new LocalServer((target, agent) -> catalogue(target).delayed(1_500));
             LocalServer good = new LocalServer((target, agent) -> catalogue(target));
             LocalServer gateway = new LocalServer((target, agent) -> catalogue(target));
             LocalServer discovery = new LocalServer((target, agent) -> LocalServer.Reply.status(503))) {
            String hostList = "{\"data\":[\"" + bad.origin() + "\",\"" + slow.origin() + "\",\"" + good.origin() + "/\",\"ftp://nope\"]}";
            discovery.handler = (target, agent) -> LocalServer.Reply.json(hostList.getBytes(StandardCharsets.UTF_8));
            Http http = new Http("AURA-test", 1_000, 2_500, 8 << 20);
            Http probeHttp = new Http("AURA-test", 500, 1_000, 1 << 20);
            AudiusApi api = new AudiusApi(discovery.origin(), gateway.origin(), http, probeHttp, 1_000, clock::get, new Random(3), true);
            List<String> selected = new ArrayList<>();
            api.setHostListener((host, candidates) -> selected.add(host + " of " + candidates.size()));

            equal(null, api.host(), "no host before the first call");
            check(api.streamUrl("audius:Lb9Ey").startsWith(gateway.origin() + "/v1/tracks/Lb9Ey/stream?app_name=AURA"), "stream URL falls back to the gateway");

            List<AudiusTrack> trending = api.trending(null, "week", 10);
            equal(4, trending.size(), "trending parsed and filtered");
            equal(good.origin(), api.host(), "fastest healthy host selected (500 and slow hosts lose)");
            equal(Collections.singletonList(good.origin() + " of 3"), selected, "host listener notified once with candidates");
            equal(1, discovery.requests.size(), "one discovery call");
            check(good.requests.contains("/v1/tracks/trending?time=week&limit=10&app_name=AURA"), "trending request with app_name: " + good.requests);
            check(good.requests.contains(AudiusApi.PROBE_PATH + "&app_name=AURA"), "probe carries app_name");
            equal("AURA-test", good.agents.get(0), "User-Agent sent");
            equal(0, gateway.requests.size(), "gateway untouched while a host is healthy");
            check(api.streamUrl("Lb9Ey").startsWith(good.origin() + "/v1/tracks/Lb9Ey/stream"), "stream URL on the selected host");

            int before = good.requests.size();
            api.trending(null, "week", 10);
            equal(before, good.requests.size(), "trending served from cache");
            clock.addAndGet(AudiusApi.TRENDING_TTL_MS + 1);
            api.trending(null, "week", 10);
            equal(before + 1, good.requests.size(), "cache expires after its TTL");

            List<AudiusTrack> search = api.searchTracks("  low tide & co ", 5);
            equal(2, search.size(), "search results");
            check(good.requests.contains("/v1/tracks/search?query=low+tide+%26+co&limit=5&app_name=AURA"), "query trimmed and encoded: " + good.requests);
            equal(0, api.searchTracks("   ", 5).size(), "blank search makes no request");

            List<CatalogPlaylist> playlists = api.searchPlaylists("night", 10);
            equal(2, playlists.size(), "gzip-encoded playlist search");
            List<AudiusTrack> albumTracks = api.playlistTracks("audius:DOPRl");
            equal("Night Ferries", albumTracks.get(1).album, "album tracks named after a known album");

            equal("Paper Lanterns", api.track("7eP5n").title, "track by id");
            int badBefore = bad.requests.size();
            equal(null, api.track("Zz9Qq"), "404 is null, not an error");
            equal(good.origin(), api.host(), "404 does not fail the host over");
            equal(badBefore, bad.requests.size(), "404 does not touch other hosts");
            try { api.artistTracks("../etc", 3); throw new AssertionError("invalid id accepted"); } catch (IOException expected) { checks++; }

            List<AudiusTrack> related = api.related("audius:Lb9Ey", "nlGNe", "Electronic", "Marlow Fields", 10);
            StringBuilder ids = new StringBuilder();
            for (AudiusTrack x : related) ids.append(x.id).append(' ');
            equal("Gh6Lk Qx2Wm Ov8Bm 7eP5n ", ids.toString(), "related: artist and genre interleaved without the seed");
            check(good.requests.contains("/v1/users/nlGNe/tracks?sort=plays&limit=10&app_name=AURA"), "artist tracks sorted by plays");
            check(good.requests.contains("/v1/tracks/trending?time=month&limit=10&genre=Electronic&app_name=AURA"), "genre trending: " + good.requests);
            List<AudiusTrack> lookedUp = api.related("audius:7eP5n", "", "", "Juniper Static", 3);
            check(good.requests.contains("/v1/tracks/7eP5n?app_name=AURA") && lookedUp.size() <= 3, "seed looked up for missing artist/genre");

            // Failover: the selected host starts failing -> other candidates probed -> gateway.
            good.handler = (target, agent) -> LocalServer.Reply.status(503);
            List<CatalogArtist> artists = api.searchArtists("marlow", 5);
            equal(3, artists.size(), "request survives a failing host");
            equal(gateway.origin(), api.host(), "gateway used when no candidate is healthy");
            check(gateway.requests.contains("/v1/users/search?query=marlow&limit=5&app_name=AURA"), "retried on the gateway");
            api.hostFailed(api.streamUrl("x1"));
            check(api.streamUrl("x1").startsWith(gateway.origin()), "stream URL after a stream failure still resolves");

            // Hosts recover once cooldowns and the gateway stickiness expire.
            good.handler = (target, agent) -> catalogue(target);
            clock.addAndGet(HostPool.FAILURE_COOLDOWN_MS + HostPool.GATEWAY_STICKY_MS + 1);
            api.trendingPlaylists(5);
            equal(good.origin(), api.host(), "healthy host selected again after cooldown");

            // Discovery unreachable: the gateway serves directly.
            LocalServer dead = new LocalServer((target, agent) -> LocalServer.Reply.status(500));
            String deadOrigin = dead.origin();
            dead.close();
            AudiusApi offline = new AudiusApi(deadOrigin, gateway.origin(), http, probeHttp, 500, clock::get, new Random(5), true);
            equal(4, offline.trending("", "month", 20).size(), "gateway works without discovery");
            equal(gateway.origin(), offline.host(), "gateway selected");

            // Remembered host from an earlier launch: no discovery, no probe.
            int discoveries = discovery.requests.size();
            AudiusApi remembered = new AudiusApi(discovery.origin(), gateway.origin(), http, probeHttp, 1_000, clock::get, new Random(9), true);
            remembered.restore(good.origin(), Arrays.asList(bad.origin(), good.origin()), clock.get() - 60_000);
            equal(good.origin(), remembered.host(), "remembered host restored");
            int goodBefore = good.requests.size();
            remembered.searchTracks("glass", 3);
            equal(discoveries, discovery.requests.size(), "no discovery with a remembered host");
            equal(goodBefore + 1, good.requests.size(), "remembered host serves the first call directly");
            AudiusApi stale = new AudiusApi(discovery.origin(), gateway.origin(), http, probeHttp, 1_000, clock::get, new Random(9), true);
            stale.restore(good.origin(), Collections.<String>emptyList(), clock.get() - 2 * 24 * 60 * 60_000L);
            equal(null, stale.host(), "memory older than a day ignored");

            // Malformed JSON from a host counts as a host failure.
            good.handler = (target, agent) -> new LocalServer.Reply(200, "<html>maintenance</html>".getBytes(StandardCharsets.UTF_8), "text/html");
            equal(3, remembered.searchArtists("tidal", 5).size(), "HTML error page fails over");
            check(!good.origin().equals(remembered.host()), "host serving HTML is dropped");
        }
    }
}
