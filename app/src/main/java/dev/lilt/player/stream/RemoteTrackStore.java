package dev.lilt.player.stream;

import android.content.Context;
import dev.lilt.player.Track;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/**
 * Persistent metadata for remote (Audius) tracks the app must be able to resolve later: queued,
 * favourited, playlisted or recently played streams. Lets ids such as {@code "audius:D7KyD"}
 * resolve offline and after a restart. Songs saved through {@link SavedCatalog} always resolve
 * here too, whatever the bound.
 *
 * <p>Stored as JSON in {@code files/stream/remote_tracks.json} (atomic replace, writes debounced
 * off the calling thread), bounded to {@value #MAX_TRACKS} tracks evicting the least recently put.
 * All instances share one process-wide store. Reads are in-memory; the file loads in the
 * background as soon as the first instance is created (an access before that waits briefly).
 * Returned tracks carry a stream URL on the current healthy Audius host.
 */
public final class RemoteTrackStore {
    static final int MAX_TRACKS = 5000;
    private static volatile RecordStore<Track> shared;

    static final RecordStore.Codec<Track> CODEC = new RecordStore.Codec<Track>() {
        @Override public JSONObject encode(Track value) { return value.toJson(); }
        @Override public Track decode(JSONObject json) {
            Track track = json == null ? null : Track.fromJson(json);
            return track != null && track.isRemote() ? track : null;
        }
    };

    private final RecordStore<Track> store;

    public RemoteTrackStore(Context context) {
        StreamStorage.init(context);
        store = shared(context);
    }

    private static RecordStore<Track> shared(Context context) {
        RecordStore<Track> store = shared;
        if (store != null) return store;
        synchronized (RemoteTrackStore.class) {
            if (shared == null) {
                File file = new File(new File(context.getApplicationContext().getFilesDir(), "stream"), "remote_tracks.json");
                shared = new RecordStore<>(file, CODEC, MAX_TRACKS, RecordStore.Overflow.EVICT_OLDEST, 16 << 20, 500, System::currentTimeMillis);
                StreamStorage.io(shared::ensureLoaded);
            }
            return shared;
        }
    }

    /** Remembers a remote track (local tracks are ignored); refreshes its recency. */
    public void put(Track track) {
        if (track != null && track.isRemote()) store.put(track.id, track, true);
    }

    public void putAll(Collection<Track> tracks) {
        if (tracks == null) return;
        for (Track track : tracks) put(track);
    }

    /** The remembered track, a saved song or a saved-playlist track; null when unknown. */
    public Track get(String id) {
        if (id == null || !id.startsWith(AudiusApi.ID_PREFIX)) return null;
        Track track = store.get(id);
        if (track == null) track = SavedCatalog.lookup(id);
        return AudiusClient.get().refresh(track);
    }

    /** Ids resolved in order; unknown ids are skipped. */
    public List<Track> resolve(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Track> tracks = new ArrayList<>(ids.size());
        for (String id : ids) {
            Track track = get(id);
            if (track != null) tracks.add(track);
        }
        return tracks;
    }

    /** All remembered tracks, most recently put first. */
    public List<Track> all() {
        List<Track> stored = store.newestFirst();
        List<Track> tracks = new ArrayList<>(stored.size());
        AudiusClient client = AudiusClient.get();
        for (Track track : stored) tracks.add(client.refresh(track));
        return tracks;
    }

    public void remove(String id) { if (id != null) store.remove(id); }

    public boolean contains(String id) { return id != null && (store.contains(id) || SavedCatalog.lookup(id) != null); }

    public int size() { return store.size(); }

    /** Writes pending changes now (blocking). Writes otherwise happen automatically within ~0.5 s. */
    public void flush() {
        StreamStorage.requireWorkerThread("RemoteTrackStore.flush");
        try { store.flush(); } catch (IOException ignored) { /* retried on the next change */ }
    }
}
