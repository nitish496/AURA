package dev.lilt.player.stream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.json.JSONArray;

/**
 * LrclibApi against a loopback server: exact-signature lookup, search fallback choosing the
 * closest duration, sync dropped for loose matches, cleaned-title retry, instrumental marker,
 * memory and disk caching (including "not found" with a shorter TTL), stale-if-error, User-Agent.
 */
public final class LrclibApiTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static void equal(Object expected, Object actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected <" + expected + "> but was <" + actual + ">");
        checks++;
    }
    private static byte[] fixture(String name) throws IOException { return Files.readAllBytes(Paths.get("tests/fixtures/lrclib/" + name)); }

    /** Minimal HTTP/1.1 loopback server (java.base only), one request per connection. */
    static final class LocalServer implements AutoCloseable {
        interface Handler { Object[] handle(String target) throws Exception; } // {status, body}
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
                while (matched < 4 && (b = in.read()) != -1) { head.write(b); matched = (b == '\r' || b == '\n') ? matched + 1 : 0; }
                String[] lines = head.toString("ISO-8859-1").split("\r\n");
                String target = lines[0].split(" ")[1];
                for (String line : lines) if (line.toLowerCase().startsWith("user-agent:")) agents.add(line.substring(11).trim());
                requests.add(target);
                Object[] reply;
                try { reply = handler.handle(target); } catch (Exception e) { reply = new Object[]{500, "{}".getBytes(StandardCharsets.UTF_8)}; }
                byte[] body = (byte[]) reply[1];
                OutputStream out = s.getOutputStream();
                out.write(("HTTP/1.1 " + reply[0] + " X\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
            } catch (Exception ignored) { }
        }
        @Override public void close() throws IOException { socket.close(); workers.shutdownNow(); }
    }

    static String param(String target, String name) throws Exception {
        int q = target.indexOf('?');
        if (q < 0) return null;
        for (String pair : target.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
        }
        return null;
    }

    static Object[] lrclib(String target) throws Exception {
        String title = param(target, "track_name");
        if (target.startsWith("/api/get")) {
            if ("Paper Lanterns".equals(title) && "Night Ferries".equals(param(target, "album_name")) && "201".equals(param(target, "duration"))) return new Object[]{200, fixture("get.json")};
            if ("Tide Tables".equals(title)) return new Object[]{200, fixture("instrumental.json")};
            return new Object[]{404, fixture("not_found.json")};
        }
        if (target.startsWith("/api/search")) {
            if ("Paper Lanterns".equals(title)) return new Object[]{200, fixture("search.json")};
            if ("Harbour Clock".equals(title)) return new Object[]{200, fixture("search_far.json")};
            return new Object[]{200, fixture("empty_search.json")};
        }
        return new Object[]{404, fixture("not_found.json")};
    }

    public static void main(String[] args) throws Exception {
        units();
        Path cache = Files.createTempDirectory("aura-lyrics-test");
        try (LocalServer server = new LocalServer(LrclibApiTest::lrclib)) {
            AtomicLong clock = new AtomicLong(1_700_000_000_000L);
            Http http = new Http(LrclibApi.USER_AGENT, 1_000, 2_000, 2 << 20);
            LrclibApi api = new LrclibApi(server.origin(), http, cache.toFile(), clock::get);

            Lyrics exact = api.fetch("Paper Lanterns", "Juniper Static", "Night Ferries", 201_000);
            check(exact.synced(), "exact signature returns synced lyrics");
            equal(6, exact.lines.size(), "all synced lines, blank gaps included");
            equal(12_400L, exact.lines.get(0).timeMs, "first stamp");
            equal("Salt on the window, morning on the line", exact.lines.get(0).text, "first line text");
            check(exact.plain.startsWith("Salt on the window") && exact.plain.contains("\n\n"), "plain lyrics from plainLyrics");
            equal(Lyrics.SOURCE_LRCLIB, exact.source, "source label");
            equal("/api/get?track_name=Paper+Lanterns&artist_name=Juniper+Static&album_name=Night+Ferries&duration=201", server.requests.get(0), "get signature in seconds");
            equal("AURA/2.0 (https://github.com/nitish496/AURA)", server.agents.get(0), "descriptive User-Agent");

            int count = server.requests.size();
            check(api.fetch("Paper Lanterns", "Juniper Static", "Night Ferries", 201_000) == exact, "memory cache returns the same lyrics");
            LrclibApi reopened = new LrclibApi(server.origin(), http, cache.toFile(), clock::get);
            Lyrics fromDisk = reopened.fetch("Paper Lanterns", "Juniper Static", "Night Ferries", 201_400);
            equal(count, server.requests.size(), "disk cache hit after restart (duration rounds to the same second)");
            equal(exact.lines.size(), fromDisk.lines.size(), "disk round trip keeps lines");
            equal(exact.lines.get(3).text, fromDisk.lines.get(3).text, "disk round trip keeps text");
            equal(exact.plain, fromDisk.plain, "disk round trip keeps plain lyrics");
            check(reopened.cached("Paper Lanterns", "Juniper Static", "Night Ferries", 201_000) != null, "cached() without network");

            // Signature miss (different duration) -> search -> same artist, closest duration (201 s, synced).
            server.requests.clear();
            Lyrics searched = api.fetch("Paper Lanterns", "Juniper Static", "Night Ferries", 202_300);
            equal("/api/get", server.requests.get(0).substring(0, 8), "exact lookup first");
            check(server.requests.get(1).startsWith("/api/search?track_name=Paper+Lanterns&artist_name=Juniper+Static"), "then search: " + server.requests);
            check(searched.synced() && searched.lines.get(0).timeMs == 12_400, "closest duration from the same artist wins");

            // Loose album: no exact lookup; radio edit matches its own duration exactly.
            server.requests.clear();
            Lyrics radio = api.fetch("Paper Lanterns", "Juniper Static", "Single", 214_000);
            check(server.requests.get(0).startsWith("/api/search"), "placeholder album skips /api/get");
            equal(5_100L, radio.lines.get(0).timeMs, "radio edit timing");

            // Duration within 10 s but beyond 3 s: lyrics kept, sync dropped (timings would be wrong).
            Lyrics loose = api.fetch("Harbour Clock", "Juniper Static", "Night Ferries", 189_000);
            check(!loose.synced() && loose.plain.startsWith("Salt on the window"), "loose match is plain only");

            // Featured-artist suffixes are retried without them.
            server.requests.clear();
            Lyrics cleaned = api.fetch("Paper Lanterns (feat. Ada North) [Live]", "Juniper Static", "", 201_000);
            equal(2, server.requests.size(), "two searches (full title, cleaned title): " + server.requests);
            equal("Paper Lanterns", param(server.requests.get(1), "track_name"), "cleaned title");
            check(cleaned.synced(), "cleaned-title match");

            // Unknown artist: search by title only, best duration overall.
            server.requests.clear();
            Lyrics anyArtist = api.fetch("Paper Lanterns", "Unknown artist", "Night Ferries", 201_000);
            equal(1, server.requests.size(), "no exact lookup without an artist");
            equal(null, param(server.requests.get(0), "artist_name"), "no artist filter");
            equal(12_400L, anyArtist.lines.get(0).timeMs, "exact duration among all artists");

            // Instrumental marker is a result, cached like lyrics.
            Lyrics instrumental = api.fetch("Tide Tables", "Marlow Fields", "Low Water", 182_000);
            check(instrumental.instrumental && instrumental.isEmpty(), "instrumental flag");
            count = server.requests.size();
            check(new LrclibApi(server.origin(), http, cache.toFile(), clock::get).fetch("Tide Tables", "Marlow Fields", "Low Water", 182_000).instrumental, "instrumental cached on disk");
            equal(count, server.requests.size(), "no request for a cached instrumental");

            // Not found: cached (memory + disk) for MISSING_TTL, then retried.
            server.requests.clear();
            Lyrics missing = api.fetch("Unwritten Song", "Nobody Known", "", 100_000);
            check(missing.isEmpty() && !missing.instrumental, "not found");
            equal(1, server.requests.size(), "one search for a missing song");
            LrclibApi third = new LrclibApi(server.origin(), http, cache.toFile(), clock::get);
            check(third.fetch("Unwritten Song", "Nobody Known", "", 100_000).isEmpty(), "not-found cached on disk");
            equal(1, server.requests.size(), "no request while not-found is fresh");
            clock.addAndGet(LrclibApi.MISSING_TTL_MS + 1);
            new LrclibApi(server.origin(), http, cache.toFile(), clock::get).fetch("Unwritten Song", "Nobody Known", "", 100_000);
            equal(2, server.requests.size(), "not-found expires sooner than found lyrics");
            count = server.requests.size();
            new LrclibApi(server.origin(), http, cache.toFile(), clock::get).fetch("Paper Lanterns", "Juniper Static", "Night Ferries", 201_000);
            equal(count, server.requests.size(), "found lyrics still fresh after two days");

            // Errors are not cached; expired lyrics are served when the refresh fails.
            server.handler = target -> new Object[]{503, "{}".getBytes(StandardCharsets.UTF_8)};
            try { api.fetch("Brand New", "Someone", "", 120_000); throw new AssertionError("5xx must surface"); } catch (IOException expected) { checks++; }
            clock.addAndGet(LrclibApi.FOUND_TTL_MS + 1);
            Lyrics stale = new LrclibApi(server.origin(), http, cache.toFile(), clock::get).fetch("Paper Lanterns", "Juniper Static", "Night Ferries", 201_000);
            check(stale.synced(), "stale lyrics served when LRCLIB is down");
            server.handler = LrclibApiTest::lrclib;
            check(new LrclibApi(server.origin(), http, null, clock::get).fetch("Brand New", "Someone", "", 120_000).isEmpty(), "errors were not cached; memory-only mode works");
            check(api.fetch("  ", "x", "y", 1).isEmpty() && api.fetch("Untitled track", "x", "y", 1).isEmpty(), "no lookup without a title");
        } finally {
            try (Stream<Path> files = Files.walk(cache)) { files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete); }
        }
        System.out.println(checks + " LRCLIB client tests passed");
    }

    private static void units() throws Exception {
        equal("Paper Lanterns", LrclibApi.cleanTitle("Paper Lanterns (feat. Ada North)"), "feat in brackets");
        equal("Paper Lanterns", LrclibApi.cleanTitle("Paper Lanterns ft. Ada North"), "ft suffix");
        equal("Paper Lanterns", LrclibApi.cleanTitle("Paper Lanterns - 2024 Remaster"), "dash suffix");
        equal("Paper Lanterns", LrclibApi.cleanTitle("Paper Lanterns [Free DL]"), "square brackets");
        check(!LrclibApi.usableAlbum("Other songs") && !LrclibApi.usableAlbum(" single ") && !LrclibApi.usableAlbum(null) && LrclibApi.usableAlbum("Night Ferries"), "placeholder albums");
        JSONArray results = new JSONArray(new String(fixture("search.json"), StandardCharsets.UTF_8));
        equal(5_100L, LrclibApi.best(results, 214, "Juniper Static").lines.get(0).timeMs, "best by duration");
        check(LrclibApi.best(results, 0, "Juniper Static").synced(), "unknown duration prefers synced");
        equal(null, LrclibApi.best(results, 400, "Juniper Static"), "nothing within tolerance");
        equal(null, LrclibApi.best(new JSONArray("[{\"duration\":200,\"syncedLyrics\":null,\"plainLyrics\":null,\"instrumental\":false}]"), 200, null), "records without lyrics ignored");
        check(!LrclibApi.key("Song", "A", "", 200).equals(LrclibApi.key("Song", "A", "", 201)), "duration in key");
        equal(LrclibApi.key("Ｓｏｎｇ ", "a", "", 1), LrclibApi.key("song", "A", "", 1), "key folds width and case");
    }
}
