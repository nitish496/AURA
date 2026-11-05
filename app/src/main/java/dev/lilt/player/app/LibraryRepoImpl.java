package dev.lilt.player.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import dev.lilt.player.Library;
import dev.lilt.player.MoodStore;
import dev.lilt.player.SearchIndex;
import dev.lilt.player.Track;
import dev.lilt.player.audio.ListeningLedger;
import dev.lilt.player.audio.ListeningStats;
import dev.lilt.player.stream.RemoteTrackStore;
import dev.lilt.player.stream.SavedCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * {@link LibraryRepo} over MediaStore ({@link Library}), the saved catalogue
 * ({@link SavedCatalog}), {@link RemoteTrackStore} and the "library" preferences (favourites,
 * playlists, history and custom covers keep their 1.x keys, so updates keep the user's data).
 *
 * <p>Scans and every derived structure (albums, artists, search index, merged song list) are built
 * on the shared I/O executor and published as one immutable {@link Model}; reads on the main thread
 * only return references into it. A MediaStore observer marks the library dirty and rescans after
 * 600 ms of quiet while the app is visible.
 */
public final class LibraryRepoImpl implements LibraryRepo {
    /** Immutable result of a scan, retained across configuration changes. */
    public static final class Model {
        final List<Track> local;
        final Map<String, Track> localById;
        final List<Track> all;
        final Map<String, Track> allById;
        final List<AlbumGroup> albums;
        final Map<String, AlbumGroup> albumByKey;
        final List<ArtistGroup> artists;
        final Map<String, ArtistGroup> artistByKey;
        final SearchIndex index;
        final List<Track> recentlyAdded;

        Model(List<Track> local, List<Track> saved, Map<String, Long> savedAt) {
            this.local = Collections.unmodifiableList(local);
            Map<String, Track> byId = new HashMap<>();
            for (Track t : local) byId.put(t.id, t);
            localById = byId;
            List<Track> merged = new ArrayList<>(local.size() + saved.size());
            merged.addAll(local);
            for (Track t : saved) if (!byId.containsKey(t.id)) merged.add(t);
            SectionIndex.sort(merged, t -> t.title);
            all = Collections.unmodifiableList(merged);
            Map<String, Track> allMap = new HashMap<>(byId);
            for (Track t : saved) allMap.put(t.id, t);
            allById = allMap;
            albums = Collections.unmodifiableList(LibraryGrouping.albums(merged));
            Map<String, AlbumGroup> ak = new HashMap<>();
            for (AlbumGroup a : albums) ak.put(a.key, a);
            albumByKey = ak;
            artists = Collections.unmodifiableList(LibraryGrouping.artists(merged, albums));
            Map<String, ArtistGroup> rk = new HashMap<>();
            for (ArtistGroup a : artists) rk.put(LibraryGrouping.key(a.name), a);
            artistByKey = rk;
            List<String[]> rows = new ArrayList<>(merged.size());
            for (Track t : merged) rows.add(new String[] {t.id, t.title, t.artist, t.album});
            index = new SearchIndex(rows);
            List<Track> added = new ArrayList<>(merged);
            added.sort((a, b) -> Long.compare(addedAt(b, savedAt), addedAt(a, savedAt)));
            recentlyAdded = Collections.unmodifiableList(added);
        }

        static Model empty() { return new Model(new ArrayList<>(), new ArrayList<>(), new HashMap<>()); }

        private static long addedAt(Track t, Map<String, Long> savedAt) {
            if (t.isRemote()) { Long s = savedAt.get(t.id); return s == null ? 0 : s; }
            return t.dateAdded * 1000L; // MediaStore date_added is in seconds
        }
    }

    private static final String KEY_FAVOURITES = "favourites", KEY_FAVOURITE_ORDER = "favourite_order",
            KEY_HISTORY = "play_history", KEY_ASKED = "requested";
    private static final int HISTORY_MAX = 50;

    private final Context context;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Library library;
    private final LibraryCache cache;
    private final MoodStore moods;
    private final SavedCatalog saved;
    private final RemoteTrackStore remote;
    private final ListeningStats stats;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> favourites = new HashSet<>();
    private final List<String> favouriteOrder = new ArrayList<>();

    private Model model;
    private boolean loaded, loading, dirty = true, visible, destroyed, scanQueued;
    private long scanGeneration;
    private boolean scanDone;
    /** MediaStore fingerprint of the model on screen; "" when unknown (API < 30). */
    private volatile String storeSignature = "";
    private List<Track> mostPlayed = Collections.emptyList();
    private ContentObserver observer;
    private final Runnable rescanIfDirty = () -> { if (visible && dirty && !loading) refresh(); };
    private final SavedCatalog.Listener savedListener = this::rebuildDerived;
    private final Runnable statsListener = this::refreshMostPlayed;

    public LibraryRepoImpl(Context context, Executor io, Model retained) {
        this.context = context.getApplicationContext();
        this.io = io;
        library = new Library(this.context);
        cache = new LibraryCache(this.context);
        moods = new MoodStore(this.context);
        saved = new SavedCatalog(this.context);
        remote = new RemoteTrackStore(this.context);
        stats = ListeningStats.get(this.context);
        SharedPreferences p = library.prefs();
        favourites.addAll(p.getStringSet(KEY_FAVOURITES, Collections.<String>emptySet()));
        String order = p.getString(KEY_FAVOURITE_ORDER, "");
        if (!order.isEmpty()) for (String id : order.split("\n")) if (favourites.contains(id) && !favouriteOrder.contains(id)) favouriteOrder.add(id);
        for (String id : favourites) if (!favouriteOrder.contains(id)) favouriteOrder.add(id);
        model = retained != null ? retained : Model.empty();
        loaded = retained != null && hasPermission();
        dirty = !loaded;
        saved.addListener(savedListener);
        stats.addListener(statsListener);
        observer = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) {
                dirty = true;
                main.removeCallbacks(rescanIfDirty);
                main.postDelayed(rescanIfDirty, 600);
            }
        };
        try {
            this.context.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
        } catch (RuntimeException ignored) { observer = null; }
        refreshMostPlayed();
    }

    /** The current model, for {@code onRetainNonConfigurationInstance}. Null before the first scan. */
    public Model retain() { return loaded ? model : null; }

    /** True once the first scan finished (or there is no permission and nothing to scan). */
    public boolean ready() { return loaded || !hasPermission(); }

    public void setVisible(boolean nowVisible) {
        visible = nowVisible;
        if (visible && (dirty || (!loaded && hasPermission()))) refresh();
    }

    public void destroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        saved.removeListener(savedListener);
        stats.removeListener(statsListener);
        if (observer != null) context.getContentResolver().unregisterContentObserver(observer);
        listeners.clear();
    }

    // ------------------------------------------------------------------ scanning

    @Override public boolean loading() { return loading && !loaded; }

    @Override public boolean hasPermission() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    /** True after the permission was requested once (to detect permanent denial). */
    public boolean permissionAsked() { return library.prefs().getBoolean(KEY_ASKED, false); }
    public void markPermissionAsked() { library.prefs().edit().putBoolean(KEY_ASKED, true).apply(); }

    @Override public void refresh() {
        if (destroyed) return;
        if (loading) { scanQueued = true; return; }
        loading = true;
        scanQueued = false;
        dirty = false;
        final long generation = ++scanGeneration;
        scanDone = false;
        final boolean permitted = hasPermission();
        notifyChanged();
        final boolean needsCache = permitted && !loaded && model.local.isEmpty();
        final boolean wasLoaded = loaded;
        final List<Track> current = model.local;
        io.execute(() -> {
            // Fingerprint first: edits made while we scan then show up as a mismatch next time.
            final String signature = permitted ? library.storeSignature() : "";
            List<Track> baseline = current;
            String cachedSignature = storeSignature;
            Model early = null;
            if (needsCache) {
                // Show the last scan straight away; the real scan below (if any) replaces it.
                final LibraryCache.Snapshot snapshot = cache.read();
                if (!snapshot.tracks.isEmpty()) {
                    baseline = snapshot.tracks;
                    cachedSignature = snapshot.signature;
                    final Model shown = build(snapshot.tracks);
                    early = shown;
                    main.post(() -> {
                        if (destroyed || generation != scanGeneration || scanDone) return;
                        model = shown;
                        loaded = true;
                        notifyChanged();
                    });
                }
            }
            final boolean haveBaseline = wasLoaded || early != null;
            String publishSig = signature;
            Model result = null; // null: keep the current model
            if (permitted && haveBaseline && LibraryCache.unchanged(cachedSignature, signature)) {
                result = early; // MediaStore generation unchanged: no query, no rebuild
            } else {
                List<Track> found = new ArrayList<>();
                boolean scanned = false;
                if (permitted) {
                    try { found = library.scan(); scanned = true; } catch (RuntimeException failure) { found = baseline; publishSig = ""; /* retry next time */ }
                }
                final boolean same = scanned && sameTracks(found, baseline);
                if (scanned && (!same || !signature.equals(cachedSignature))) cache.write(found, signature);
                else if (!permitted) cache.clear();
                if (!same) result = build(found);
                else if (early != null) result = early;
                else if (!wasLoaded) result = build(baseline);
            }
            final Model published = result;
            final String publishedSignature = publishSig;
            final Model shownEarly = early;
            main.post(() -> {
                scanDone = true;
                if (destroyed || generation != scanGeneration) return;
                storeSignature = publishedSignature;
                if (published != null) {
                    boolean already = published == shownEarly && loaded;
                    model = published;
                    loaded = true;
                    loading = false;
                    if (!already) notifyChanged();
                } else {
                    loaded = true;
                    loading = false; // nothing changed: keep the model, no screen rebuilds
                }
                if (scanQueued || dirty) main.postDelayed(rescanIfDirty, 600);
                // Normalise search fields after first paint so the first keystroke is instant.
                final SearchIndex index = model.index;
                io.execute(index::warm);
            });
        });
    }

    /** Same songs (id, title, length, date added) in the same order. */
    private static boolean sameTracks(List<Track> a, List<Track> b) {
        if (a.size() != b.size() || a.isEmpty()) return false;
        for (int i = 0; i < a.size(); i++) {
            Track x = a.get(i), y = b.get(i);
            if (!x.id.equals(y.id) || x.duration != y.duration || x.dateAdded != y.dateAdded
                    || !java.util.Objects.equals(x.title, y.title) || !java.util.Objects.equals(x.artist, y.artist)
                    || !java.util.Objects.equals(x.album, y.album)) return false;
        }
        return true;
    }

    private Model build(List<Track> local) {
        List<Track> savedTracks = saved.tracks();
        Map<String, Long> savedAt = new HashMap<>();
        for (Track t : savedTracks) savedAt.put(t.id, saved.savedAt(t.id));
        return new Model(new ArrayList<>(local), savedTracks, savedAt);
    }

    /** Saved catalogue changed: rebuild merged structures in the background (no rescan). */
    private void rebuildDerived() {
        if (destroyed) return;
        final List<Track> local = model.local;
        final long generation = scanGeneration;
        io.execute(() -> {
            Model built = build(local);
            main.post(() -> {
                if (destroyed || generation != scanGeneration) return;
                model = built;
                notifyChanged();
            });
        });
    }

    private void refreshMostPlayed() {
        if (destroyed) return;
        io.execute(() -> {
            List<ListeningLedger.TrackStat> top = stats.topTracks(100, 0);
            main.post(() -> {
                if (destroyed) return;
                List<Track> out = new ArrayList<>();
                for (ListeningLedger.TrackStat s : top) { Track t = find(s.id); if (t != null) out.add(t); }
                mostPlayed = Collections.unmodifiableList(out);
                notifyChanged();
            });
        });
    }

    /** Stats for mix building, queried off the main thread. */
    public ListeningStats stats() { return stats; }

    // ------------------------------------------------------------------ reads

    @Override public List<Track> localTracks() { return model.local; }
    @Override public List<Track> allTracks() { return model.all; }
    @Override public List<AlbumGroup> albums() { return model.albums; }
    @Override public List<ArtistGroup> artists() { return model.artists; }
    @Override public AlbumGroup album(String key) { return key == null ? null : model.albumByKey.get(key); }
    @Override public ArtistGroup artist(String name) { return name == null ? null : model.artistByKey.get(LibraryGrouping.key(name)); }

    @Override public Track find(String id) {
        if (id == null || id.isEmpty()) return null;
        Track t = model.allById.get(id);
        if (t != null) return t;
        if (id.startsWith("audius:")) {
            t = saved.get(id);
            if (t == null) t = remote.get(id);
        }
        return t;
    }

    /** Ids resolved in order; unknown ids are skipped. */
    public List<Track> resolve(Collection<String> ids) {
        List<Track> out = new ArrayList<>(ids.size());
        for (String id : ids) { Track t = find(id); if (t != null) out.add(t); }
        return out;
    }

    // ------------------------------------------------------------------ favourites

    @Override public boolean isFavourite(String id) { return id != null && favourites.contains(id); }

    @Override public void setFavourite(Track track, boolean favourite) {
        if (track == null) return;
        if (favourite == favourites.contains(track.id)) return;
        if (favourite) {
            if (track.isRemote()) remote.put(track);
            favourites.add(track.id);
            favouriteOrder.add(0, track.id);
        } else {
            favourites.remove(track.id);
            favouriteOrder.remove(track.id);
        }
        library.prefs().edit().putStringSet(KEY_FAVOURITES, new HashSet<>(favourites))
                .putString(KEY_FAVOURITE_ORDER, TextUtils.join("\n", favouriteOrder)).apply();
        notifyChanged();
    }

    /** Favourites, most recently added first. */
    @Override public List<Track> favourites() { return resolve(favouriteOrder); }

    // ------------------------------------------------------------------ playlists

    @Override public Map<String, List<String>> playlists() { return library.playlists(); }

    @Override public void createPlaylist(String name) {
        String n = cleanName(name);
        if (n == null) return;
        Map<String, List<String>> lists = library.playlists();
        if (lists.containsKey(n)) return;
        lists.put(n, new ArrayList<>());
        library.savePlaylists(lists);
        notifyChanged();
    }

    /** Null when the name is blank or too long. */
    public static String cleanName(String name) {
        if (name == null) return null;
        String n = name.trim();
        return n.isEmpty() || n.length() > 80 ? null : n;
    }

    @Override public void addToPlaylist(String name, Track track) {
        if (track == null) return;
        addAllToPlaylist(name, Collections.singletonList(track));
    }

    @Override public void addAllToPlaylist(String name, List<Track> tracks) {
        Map<String, List<String>> lists = library.playlists();
        List<String> ids = lists.get(name);
        if (ids == null || tracks == null || tracks.isEmpty()) return;
        List<Track> streamed = new ArrayList<>();
        for (Track t : tracks) { ids.add(t.id); if (t.isRemote()) streamed.add(t); }
        remote.putAll(streamed);
        library.savePlaylists(lists);
        notifyChanged();
    }

    @Override public void removeFromPlaylist(String name, String trackId) {
        Map<String, List<String>> lists = library.playlists();
        List<String> ids = lists.get(name);
        if (ids == null || !ids.remove(trackId)) return;
        library.savePlaylists(lists);
        notifyChanged();
    }

    /** Removes the entry at {@code index} (duplicates in a playlist are separate entries). */
    public void removePlaylistEntry(String name, int index) {
        Map<String, List<String>> lists = library.playlists();
        List<String> ids = lists.get(name);
        if (ids == null || index < 0 || index >= ids.size()) return;
        ids.remove(index);
        library.savePlaylists(lists);
        notifyChanged();
    }

    @Override public void movePlaylistTrack(String name, int from, int to) {
        Map<String, List<String>> lists = library.playlists();
        List<String> ids = lists.get(name);
        if (ids == null || from < 0 || from >= ids.size() || to < 0 || to >= ids.size() || from == to) return;
        ids.add(to, ids.remove(from));
        library.savePlaylists(lists);
        notifyChanged();
    }

    @Override public boolean renamePlaylist(String oldName, String newName) {
        String n = cleanName(newName);
        if (n == null) return false;
        Map<String, List<String>> lists = library.playlists();
        if (!lists.containsKey(oldName)) return false;
        if (n.equals(oldName)) return true;
        if (lists.containsKey(n)) return false;
        Map<String, List<String>> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : lists.entrySet()) renamed.put(e.getKey().equals(oldName) ? n : e.getKey(), e.getValue());
        library.savePlaylists(renamed);
        notifyChanged();
        return true;
    }

    @Override public void deletePlaylist(String name) {
        Map<String, List<String>> lists = library.playlists();
        if (lists.remove(name) == null) return;
        library.savePlaylists(lists);
        notifyChanged();
    }

    /** Tracks of a playlist; entries that no longer resolve (deleted files, malformed ids) are skipped. */
    @Override public List<Track> playlistTracks(String name) {
        List<String> ids = library.playlists().get(name);
        return ids == null ? Collections.<Track>emptyList() : resolve(ids);
    }

    // ------------------------------------------------------------------ saved catalogue

    @Override public SavedCatalog saved() { return saved; }
    @Override public boolean isSaved(String id) { return saved.contains(id); }

    @Override public void setSaved(Track track, boolean save) {
        if (track == null || !track.isRemote()) return;
        if (save) { remote.put(track); saved.add(track); } else saved.remove(track.id);
    }

    // ------------------------------------------------------------------ history and stats

    /** Records the start of a song (newest first, at most 50, no duplicates). */
    public void recordPlayed(Track track) {
        if (track == null) return;
        if (track.isRemote()) remote.put(track);
        List<String> ids = history();
        if (!ids.isEmpty() && ids.get(0).equals(track.id)) return;
        ids.remove(track.id);
        ids.add(0, track.id);
        while (ids.size() > HISTORY_MAX) ids.remove(ids.size() - 1);
        library.prefs().edit().putString(KEY_HISTORY, TextUtils.join("\n", ids)).apply();
        notifyChanged();
    }

    private String historyRaw;
    private List<String> historyParsed = new ArrayList<>();

    /** Parsed once per stored value; returns a mutable copy. */
    private synchronized List<String> history() {
        String stored = library.prefs().getString(KEY_HISTORY, "");
        if (!stored.equals(historyRaw)) {
            List<String> ids = new ArrayList<>();
            if (!stored.isEmpty()) for (String id : stored.split("\n")) if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
            historyRaw = stored;
            historyParsed = ids;
        }
        return new ArrayList<>(historyParsed);
    }

    @Override public List<Track> recentlyPlayed(int limit) {
        List<Track> out = new ArrayList<>();
        for (String id : history()) {
            Track t = find(id);
            if (t != null) out.add(t);
            if (out.size() >= limit) break;
        }
        return out;
    }

    @Override public List<Track> recentlyAdded(int limit) {
        List<Track> all = model.recentlyAdded;
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    @Override public List<Track> mostPlayed(int limit) {
        return mostPlayed.size() <= limit ? mostPlayed : mostPlayed.subList(0, limit);
    }

    @Override public void clearHistory() {
        library.prefs().edit().remove(KEY_HISTORY).apply();
        notifyChanged();
    }

    // ------------------------------------------------------------------ search and moods

    @Override public List<Track> searchLocal(String query, int limit) {
        Model m = model;
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        List<String> ids = m.index.search(query);
        List<Track> out = new ArrayList<>(Math.min(limit, ids.size()));
        for (String id : ids) {
            Track t = m.allById.get(id);
            if (t != null) out.add(t);
            if (out.size() >= limit) break;
        }
        return out;
    }

    @Override public Set<String> moods(String trackId) { return moods.moodsFor(trackId); }

    @Override public void setMoods(String trackId, Collection<String> values) {
        moods.setMoods(trackId, values);
        notifyChanged();
    }

    /** Gives {@code mood} to exactly {@code selected} among all library songs (one write). */
    public void assignMood(String mood, Collection<String> selected) {
        List<String> ids = new ArrayList<>(model.all.size());
        for (Track t : model.all) ids.add(t.id);
        moods.assign(mood, ids, selected);
        notifyChanged();
    }

    @Override public List<Track> moodTracks(String mood) { return moods.tracksFor(model.all, mood); }

    public static List<String> moodLabels() { return MoodStore.labels(); }

    // ------------------------------------------------------------------ custom covers (local albums)

    /** Remembers a user-picked cover (persisted content URI) for a local album; null resets it. */
    public void setCustomCover(Track track, Uri cover) {
        if (track == null || track.isRemote()) return;
        SharedPreferences.Editor e = library.prefs().edit();
        String key = "cover:" + track.albumKey();
        if (cover == null) {
            e.remove(key);
            if (track.albumId > 0) e.remove("cover:album:" + track.albumId);
        } else e.putString(key, cover.toString());
        e.apply();
        notifyChanged();
    }

    public boolean hasCustomCover(Track track) { return track != null && !track.isRemote() && library.artwork(track) != null; }

    /** Custom cover for MediaItem artwork (null = embedded art). */
    public Uri customCover(Track track) { return track == null || track.isRemote() ? null : library.artwork(track); }

    public Library library() { return library; }

    // ------------------------------------------------------------------ listeners

    @Override public void addListener(Listener listener) { if (listener != null && !listeners.contains(listener)) listeners.add(listener); }
    @Override public void removeListener(Listener listener) { listeners.remove(listener); }

    private boolean notifyPosted;

    /** Coalesces bursts of changes into one callback on the next main-loop turn. */
    private void notifyChanged() {
        if (notifyPosted || destroyed) return;
        notifyPosted = true;
        main.post(() -> {
            notifyPosted = false;
            if (destroyed) return;
            for (Listener l : listeners) l.onLibraryChanged();
        });
    }

    static List<String> split(String s) { return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split("\n"))); }
    static <T> Set<T> set(Collection<T> c) { return new LinkedHashSet<>(c); }
}
