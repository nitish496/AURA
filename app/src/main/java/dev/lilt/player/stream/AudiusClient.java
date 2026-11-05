package dev.lilt.player.stream;

import android.content.Context;
import dev.lilt.player.Track;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * App-facing Audius catalogue client: free, legally streamable full tracks from the public Audius
 * API (no account, no key). Thread-safe singleton.
 *
 * <p><b>Threading:</b> every method that returns catalogue data blocks on the network (unless the
 * answer is in the in-memory cache) and throws {@link IllegalStateException} on the main thread;
 * call them from a worker thread ({@code @WorkerThread}). {@link #streamUrl(String)},
 * {@link #refresh(Track)}, {@link #failover(Track)}, {@link #host()} and {@link #genres()} never
 * block.
 *
 * <p>Returned tracks have ids {@code "audius:<id>"}, {@code uri} = stream URL on the current
 * healthy host (302 to the audio), the best artwork in {@code artworkUrl}, album = album name when
 * known else "Single". Errors surface as {@link IOException}; {@link Http.StatusException} carries
 * the HTTP status for 4xx answers.
 */
public final class AudiusClient {
    public static final String USER_AGENT = "AURA/2.0 (Android; https://github.com/nitish496/AURA)";
    private static volatile AudiusClient instance;

    private final AudiusApi api;
    private final AtomicBoolean restoring = new AtomicBoolean();

    private AudiusClient() {
        api = AudiusApi.production(USER_AGENT);
        api.setHostListener(this::rememberHost);
    }

    public static AudiusClient get() {
        AudiusClient client = instance;
        if (client == null) {
            synchronized (AudiusClient.class) {
                client = instance;
                if (client == null) instance = client = new AudiusClient();
            }
        }
        return client;
    }

    /** Same singleton; also enables remembering the chosen host across launches. */
    public static AudiusClient get(Context context) {
        AudiusClient client = get();
        StreamStorage.init(context);
        return client;
    }

    /** Curated Audius genre names (exact API spelling) for Browse. */
    public static List<String> genres() { return AudiusApi.genres(); }

    /** Trending tracks. {@code genre} null = all; {@code time} "week" | "month" | "year" | "allTime". @WorkerThread */
    public List<Track> trending(String genre, String time, int limit) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.trending");
        return tracks(api.trending(genre, time, limit));
    }

    /** @WorkerThread */
    public List<Track> searchTracks(String query, int limit) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.searchTracks");
        return tracks(api.searchTracks(query, limit));
    }

    /** @WorkerThread */
    public List<CatalogArtist> searchArtists(String query, int limit) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.searchArtists");
        return api.searchArtists(query, limit);
    }

    /** @WorkerThread */
    public List<CatalogPlaylist> searchPlaylists(String query, int limit) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.searchPlaylists");
        return api.searchPlaylists(query, limit);
    }

    /** @WorkerThread */
    public List<CatalogPlaylist> trendingPlaylists(int limit) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.trendingPlaylists");
        return api.trendingPlaylists(limit);
    }

    /** Tracks of a playlist or album (raw or {@code audius:}-prefixed id). @WorkerThread */
    public List<Track> playlistTracks(String playlistId) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.playlistTracks");
        return tracks(api.playlistTracks(playlistId));
    }

    /** Prefer this overload when the playlist object is at hand: album tracks get the album name. @WorkerThread */
    public List<Track> playlistTracks(CatalogPlaylist playlist) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.playlistTracks");
        return tracks(api.playlistTracks(playlist));
    }

    /** An artist's tracks, most played first. @WorkerThread */
    public List<Track> artistTracks(String userId, int limit) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.artistTracks");
        return tracks(api.artistTracks(userId, limit));
    }

    /**
     * Songs to follow {@code seed}: the same artist's most played tracks interleaved with trending
     * tracks of the same genre, excluding the seed. Works for local seeds too (exact artist-name
     * matches only, since local files carry no Audius ids). @WorkerThread
     */
    public List<Track> related(Track seed, int limit) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.related");
        if (seed == null) return Collections.emptyList();
        return tracks(api.related(seed.isRemote() ? seed.id : null, seed.artistId, seed.genre, seed.artist, limit));
    }

    /** One track by id (raw or {@code audius:}-prefixed); null when missing or not streamable. @WorkerThread */
    public Track track(String id) throws IOException {
        StreamStorage.requireWorkerThread("AudiusClient.track");
        AudiusTrack found = api.track(id);
        return found == null ? null : toTrack(found);
    }

    /** Stream URL for a track id on the current healthy host. Non-blocking. */
    public String streamUrl(String trackId) { return api.streamUrl(trackId); }

    /**
     * {@code track} with its stream URL on the current healthy host; a stored URL is kept while no
     * host is known yet and its own host has not failed (same instance when nothing changes or the
     * track is local). Non-blocking; {@link RemoteTrackStore} and {@link SavedCatalog} apply it to
     * everything they return.
     */
    public Track refresh(Track track) {
        if (track == null || !track.isRemote()) return track;
        String fresh = api.freshStreamUrl(track.id, track.uri.toString());
        return fresh.equals(track.uri.toString()) ? track : track.withStreamUrl(fresh);
    }

    /**
     * After a stream fails to open: marks its host unhealthy and returns the track pointed at the
     * next host (or the gateway). Non-blocking; retry playback once with the result.
     */
    public Track failover(Track track) {
        if (track == null || !track.isRemote()) return track;
        api.hostFailed(track.uri.toString());
        return refresh(track);
    }

    /** Picks a healthy host in the background so the first screen loads faster. */
    public void warmUp() {
        StreamStorage.io(() -> {
            restoreHostNow();
            api.selectHost();
        });
    }

    /** Current API host, or null before the first request. */
    public String host() { return api.host(); }

    static Track toTrack(AudiusTrack a, String streamUrl) {
        return Track.remote(a.trackId(), a.title, a.artist, a.album, streamUrl, a.artworkUrl, a.durationMs, a.genre, a.artistId, a.permalink);
    }

    private Track toTrack(AudiusTrack a) { return toTrack(a, api.streamUrl(a.id)); }

    private List<Track> tracks(List<AudiusTrack> found) {
        List<Track> tracks = new ArrayList<>(found.size());
        for (AudiusTrack a : found) tracks.add(toTrack(a));
        return Collections.unmodifiableList(tracks);
    }

    // ---- Host memory (files/stream/audius_host.json) ------------------------------------------

    void restoreHostAsync() {
        if (restoring.get()) return;
        StreamStorage.io(this::restoreHostNow);
    }

    private void restoreHostNow() {
        if (!restoring.compareAndSet(false, true)) return;
        File dir = StreamStorage.filesDir();
        if (dir == null) { restoring.set(false); return; }
        try {
            String text = JsonFiles.read(new File(dir, "audius_host.json"), 64 * 1024);
            if (text == null) return;
            JSONObject o = new JSONObject(text);
            List<String> candidates = new ArrayList<>();
            JSONArray list = o.optJSONArray("candidates");
            if (list != null) for (int i = 0; i < list.length(); i++) {
                Object value = list.opt(i);
                if (value instanceof String) candidates.add((String) value);
            }
            api.restore(AudiusParser.text(o, "host"), candidates, AudiusParser.number(o, "at"));
        } catch (IOException | JSONException | RuntimeException ignored) {
            // A missing or damaged record only costs one host probe.
        }
    }

    private void rememberHost(String host, List<String> candidates) {
        File dir = StreamStorage.filesDir();
        if (dir == null) return;
        StreamStorage.io(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("host", host).put("at", System.currentTimeMillis()).put("candidates", new JSONArray(candidates));
                JsonFiles.writeAtomic(new File(dir, "audius_host.json"), o.toString());
            } catch (IOException | JSONException ignored) {
                // Optional optimisation.
            }
        });
    }
}
