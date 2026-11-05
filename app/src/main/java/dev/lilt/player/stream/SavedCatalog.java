package dev.lilt.player.stream;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import dev.lilt.player.Track;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The user's library of streamed music, kept without downloading audio: saved Audius songs and
 * saved Audius playlists/albums (with a snapshot of their tracks so they open offline). Ordered
 * newest first with the time each item was saved.
 *
 * <p>Persisted in {@code files/stream/saved_tracks.json} and {@code saved_playlists.json} (atomic
 * replace, debounced background writes). All instances share one process-wide state, so a change
 * made anywhere (activity, service, widget) reaches every listener. Listeners are called on the
 * main thread, coalesced per burst of changes; remove them in {@code onDestroy}. Reads are
 * in-memory and safe on the main thread once loaded (loading starts with the first instance).
 */
public final class SavedCatalog {
    /** Called on the main thread after saved songs or playlists change. */
    public interface Listener { void onSavedCatalogChanged(); }

    static final int MAX_TRACKS = 20_000, MAX_PLAYLISTS = 1_000, MAX_PLAYLIST_TRACKS = 2_000;

    /** A saved playlist and the snapshot of its tracks. Immutable. */
    static final class SavedPlaylist {
        final CatalogPlaylist playlist;
        final List<Track> tracks;
        SavedPlaylist(CatalogPlaylist playlist, List<Track> tracks) {
            this.playlist = playlist;
            List<Track> remote = new ArrayList<>();
            if (tracks != null) for (Track t : tracks) if (t != null && t.isRemote() && remote.size() < MAX_PLAYLIST_TRACKS) remote.add(t);
            this.tracks = Collections.unmodifiableList(remote);
        }
    }

    private static final RecordStore.Codec<SavedPlaylist> PLAYLIST_CODEC = new RecordStore.Codec<SavedPlaylist>() {
        @Override public JSONObject encode(SavedPlaylist value) throws JSONException {
            JSONArray tracks = new JSONArray();
            for (Track t : value.tracks) tracks.put(t.toJson());
            return new JSONObject().put("playlist", value.playlist.toJson()).put("tracks", tracks);
        }
        @Override public SavedPlaylist decode(JSONObject json) {
            if (json == null) return null;
            CatalogPlaylist playlist = CatalogPlaylist.fromJson(json.optJSONObject("playlist"));
            if (playlist == null) return null;
            List<Track> tracks = new ArrayList<>();
            JSONArray stored = json.optJSONArray("tracks");
            if (stored != null) for (int i = 0; i < stored.length(); i++) {
                Track t = RemoteTrackStore.CODEC.decode(stored.optJSONObject(i));
                if (t != null) tracks.add(t);
            }
            return new SavedPlaylist(playlist, tracks);
        }
    };

    private static final class Backing {
        final RecordStore<Track> tracks;
        final RecordStore<SavedPlaylist> playlists;
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean notifyPosted = new AtomicBoolean();
        private Map<String, Track> snapshotIndex = Collections.emptyMap();
        private long snapshotRevision = -1;

        Backing(File dir) {
            tracks = new RecordStore<>(new File(dir, "saved_tracks.json"), RemoteTrackStore.CODEC, MAX_TRACKS,
                    RecordStore.Overflow.REJECT, 32 << 20, 400, System::currentTimeMillis);
            playlists = new RecordStore<>(new File(dir, "saved_playlists.json"), PLAYLIST_CODEC, MAX_PLAYLISTS,
                    RecordStore.Overflow.REJECT, 64 << 20, 400, System::currentTimeMillis);
        }

        void changed() {
            if (!notifyPosted.compareAndSet(false, true)) return;
            main.post(() -> {
                notifyPosted.set(false);
                for (Listener listener : listeners) listener.onSavedCatalogChanged();
            });
        }

        /** Track from any saved playlist snapshot (index rebuilt only when playlists change). */
        synchronized Track snapshotTrack(String id) {
            long revision = playlists.revision();
            if (revision != snapshotRevision) {
                Map<String, Track> index = new HashMap<>();
                for (SavedPlaylist saved : playlists.newestFirst()) for (Track t : saved.tracks) if (!index.containsKey(t.id)) index.put(t.id, t);
                snapshotIndex = index;
                snapshotRevision = revision;
            }
            return snapshotIndex.get(id);
        }
    }

    private static volatile Backing shared;
    private final Backing backing;

    public SavedCatalog(Context context) {
        StreamStorage.init(context);
        Backing existing = shared;
        if (existing == null) {
            synchronized (SavedCatalog.class) {
                existing = shared;
                if (existing == null) {
                    existing = new Backing(new File(context.getApplicationContext().getFilesDir(), "stream"));
                    shared = existing;
                    Backing loading = existing;
                    StreamStorage.io(() -> { loading.tracks.ensureLoaded(); loading.playlists.ensureLoaded(); });
                }
            }
        }
        backing = existing;
    }

    /** Saved song or saved-playlist track for {@link RemoteTrackStore#get(String)}; null when unknown or not loaded. */
    static Track lookup(String id) {
        Backing backing = shared;
        if (backing == null) return null;
        Track saved = backing.tracks.get(id);
        return saved != null ? saved : backing.snapshotTrack(id);
    }

    // ---- Songs --------------------------------------------------------------------------------

    /**
     * Saves a remote song to the library (newest first). Re-saving refreshes its metadata without
     * moving it. Returns true when newly saved; false for local tracks, already-saved songs, or a
     * full library ({@link #isFull()}).
     */
    public boolean add(Track track) {
        if (track == null || !track.isRemote()) return false;
        int result = backing.tracks.put(track.id, track, false);
        if (result != RecordStore.REJECTED) backing.changed();
        return result == RecordStore.ADDED;
    }

    /** Saves several songs; returns how many were newly saved. */
    public int addAll(Collection<Track> tracks) {
        if (tracks == null) return 0;
        int added = 0;
        boolean changed = false;
        for (Track track : tracks) {
            if (track == null || !track.isRemote()) continue;
            int result = backing.tracks.put(track.id, track, false);
            if (result == RecordStore.ADDED) added++;
            if (result != RecordStore.REJECTED) changed = true;
        }
        if (changed) backing.changed();
        return added;
    }

    public boolean remove(String trackId) {
        if (trackId == null || backing.tracks.remove(trackId) == null) return false;
        backing.changed();
        return true;
    }

    public boolean contains(String trackId) { return trackId != null && backing.tracks.contains(trackId); }

    /** When the song was saved (epoch ms); 0 when not saved. */
    public long savedAt(String trackId) { return trackId == null ? 0 : backing.tracks.time(trackId); }

    public Track get(String trackId) { return trackId == null ? null : AudiusClient.get().refresh(backing.tracks.get(trackId)); }

    /** Saved songs, most recently saved first. */
    public List<Track> tracks() { return refreshed(backing.tracks.newestFirst()); }

    public int size() { return backing.tracks.size(); }

    public boolean isFull() { return backing.tracks.full(); }

    // ---- Playlists ----------------------------------------------------------------------------

    /**
     * Saves a playlist/album with an optional snapshot of its tracks (pass the result of
     * {@link AudiusClient#playlistTracks(CatalogPlaylist)} so it opens offline). Re-saving updates
     * metadata and snapshot without moving it. Returns true when newly saved.
     */
    public boolean addPlaylist(CatalogPlaylist playlist, List<Track> tracks) {
        if (playlist == null) return false;
        SavedPlaylist existing = backing.playlists.get(playlist.id);
        List<Track> snapshot = tracks != null ? tracks : existing != null ? existing.tracks : null;
        int result = backing.playlists.put(playlist.id, new SavedPlaylist(playlist, snapshot), false);
        if (result != RecordStore.REJECTED) backing.changed();
        return result == RecordStore.ADDED;
    }

    /** Replaces the offline snapshot of a saved playlist; false when it is not saved. */
    public boolean updatePlaylistTracks(String playlistId, List<Track> tracks) {
        String id = AudiusApi.rawId(playlistId);
        SavedPlaylist existing = backing.playlists.get(id);
        if (existing == null) return false;
        backing.playlists.put(id, new SavedPlaylist(existing.playlist, tracks), false);
        backing.changed();
        return true;
    }

    public boolean removePlaylist(String playlistId) {
        if (backing.playlists.remove(AudiusApi.rawId(playlistId)) == null) return false;
        backing.changed();
        return true;
    }

    public boolean containsPlaylist(String playlistId) { return backing.playlists.contains(AudiusApi.rawId(playlistId)); }

    public long playlistSavedAt(String playlistId) { return backing.playlists.time(AudiusApi.rawId(playlistId)); }

    public CatalogPlaylist playlist(String playlistId) {
        SavedPlaylist saved = backing.playlists.get(AudiusApi.rawId(playlistId));
        return saved == null ? null : saved.playlist;
    }

    /** Saved playlists and albums, most recently saved first. */
    public List<CatalogPlaylist> playlists() {
        List<SavedPlaylist> saved = backing.playlists.newestFirst();
        List<CatalogPlaylist> playlists = new ArrayList<>(saved.size());
        for (SavedPlaylist entry : saved) playlists.add(entry.playlist);
        return playlists;
    }

    /** Offline snapshot of a saved playlist's tracks; empty when unknown. */
    public List<Track> playlistTracks(String playlistId) {
        SavedPlaylist saved = backing.playlists.get(AudiusApi.rawId(playlistId));
        return saved == null ? Collections.<Track>emptyList() : refreshed(saved.tracks);
    }

    // ---- Listeners and persistence ------------------------------------------------------------

    public void addListener(Listener listener) { if (listener != null && !backing.listeners.contains(listener)) backing.listeners.add(listener); }

    public void removeListener(Listener listener) { backing.listeners.remove(listener); }

    /** Writes pending changes now (blocking). Writes otherwise happen automatically within ~0.5 s. */
    public void flush() {
        StreamStorage.requireWorkerThread("SavedCatalog.flush");
        try { backing.tracks.flush(); backing.playlists.flush(); } catch (IOException ignored) { /* retried on the next change */ }
    }

    private static List<Track> refreshed(List<Track> stored) {
        AudiusClient client = AudiusClient.get();
        List<Track> tracks = new ArrayList<>(stored.size());
        for (Track track : stored) tracks.add(client.refresh(track));
        return Collections.unmodifiableList(tracks);
    }
}
