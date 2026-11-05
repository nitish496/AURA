package dev.lilt.player.stream;

import android.content.Context;
import dev.lilt.player.Track;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lyrics for any track. Local files: lyrics embedded in the file (ID3 USLT/SYLT, FLAC Vorbis
 * comments) first, then LRCLIB. Streamed tracks: LRCLIB. Results are cached per track id in memory;
 * LRCLIB answers (including "not found") are also cached on disk ({@code cache/lyrics}).
 *
 * <p>Privacy: an LRCLIB lookup sends the song's title, artist, album and duration (seconds) to
 * lrclib.net. Songs with embedded lyrics never leave the device. Call {@link #fetch(Track)} only
 * when the user opens lyrics.
 */
public final class LyricsClient {
    private static volatile LyricsClient instance;

    private final TtlCache<String, Lyrics> byTrack = new TtlCache<>(128, System::currentTimeMillis);
    private volatile LrclibApi lrclib;
    private volatile boolean lrclibHasDisk;

    private LyricsClient() {}

    public static LyricsClient get() {
        LyricsClient client = instance;
        if (client == null) {
            synchronized (LyricsClient.class) {
                client = instance;
                if (client == null) instance = client = new LyricsClient();
            }
        }
        return client;
    }

    /** Same singleton; the context enables the disk cache and embedded lyrics of local files. */
    public static LyricsClient get(Context context) {
        StreamStorage.init(context);
        return get();
    }

    /**
     * Lyrics for {@code track}, never null: {@link Lyrics#NONE} (or an instrumental marker) when
     * nothing is known. Blocking (@WorkerThread): reads the file tag and/or calls LRCLIB.
     * @throws IOException when LRCLIB is needed but unreachable and nothing is cached
     */
    public Lyrics fetch(Track track) throws IOException {
        StreamStorage.requireWorkerThread("LyricsClient.fetch");
        if (track == null) return Lyrics.NONE;
        String key = key(track);
        Lyrics hit = byTrack.get(key);
        if (hit != null) return hit;
        if (!track.isRemote()) {
            Lyrics embedded = embedded(track);
            if (!embedded.isEmpty()) {
                byTrack.put(key, embedded, 6 * 60 * 60_000L);
                return embedded;
            }
        }
        Lyrics online = lrclib().fetch(track.title, track.artist, track.album, track.duration);
        byTrack.put(key, online, online.isEmpty() && !online.instrumental ? 10 * 60_000L : 6 * 60 * 60_000L);
        return online;
    }

    /** Lyrics already fetched this session for {@code track}, or null. Non-blocking. */
    public Lyrics cached(Track track) {
        return track == null ? null : byTrack.get(key(track));
    }

    /** Lyrics embedded in a local file, or {@link Lyrics#NONE}. Blocking (@WorkerThread). */
    public Lyrics embedded(Track track) {
        StreamStorage.requireWorkerThread("LyricsClient.embedded");
        Context context = StreamStorage.context();
        if (context == null || track == null || track.isRemote() || track.uri == null) return Lyrics.NONE;
        try (InputStream in = context.getContentResolver().openInputStream(track.uri)) {
            return in == null ? Lyrics.NONE : EmbeddedLyrics.read(new BufferedInputStream(in, 16 * 1024));
        } catch (IOException | RuntimeException unreadable) {
            // Missing file, revoked permission or a provider error: fall back to LRCLIB.
            return Lyrics.NONE;
        }
    }

    private static String key(Track track) { return track.id + "\u0001" + track.dateAdded + "\u0001" + track.duration; }

    private LrclibApi lrclib() {
        LrclibApi current = lrclib;
        if (current != null && (lrclibHasDisk || StreamStorage.context() == null)) return current;
        synchronized (this) {
            if (lrclib == null || (!lrclibHasDisk && StreamStorage.context() != null)) {
                File dir = StreamStorage.cacheDir("lyrics");
                lrclib = LrclibApi.production(dir);
                lrclibHasDisk = dir != null;
            }
            return lrclib;
        }
    }
}
