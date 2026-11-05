package dev.lilt.player.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Listening history for "Replay"-style screens: plays, last played time and listening time per
 * track id, with top tracks, top artists and total minutes for any period. The playback service
 * records into it; a play counts after 30 seconds or half the song ({@link PlayCounter}).
 *
 * <p>Process-wide singleton backed by SharedPreferences ("listening_stats"). Loading and saving
 * happen on a background thread. Query methods wait for the first load (normally a few
 * milliseconds), so call them from a worker thread rather than the main thread. Listeners run on
 * the main thread after changes.
 */
public final class ListeningStats {
    private static final long SAVE_DELAY_MS = 2000;
    private static ListeningStats instance;

    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "AuraStats"); t.setDaemon(true); return t; });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CountDownLatch loaded = new CountDownLatch(1);
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile ListeningLedger ledger = new ListeningLedger();
    private boolean saveScheduled;
    private final Runnable notifyListeners = () -> { for (Runnable listener : listeners) listener.run(); };

    public static synchronized ListeningStats get(Context context) {
        if (instance == null) instance = new ListeningStats(context.getApplicationContext());
        return instance;
    }

    private ListeningStats(Context context) {
        prefs = context.getSharedPreferences("listening_stats", Context.MODE_PRIVATE);
        io.execute(() -> {
            try { ledger = ListeningLedger.decode(prefs.getString("ledger", null)); }
            catch (RuntimeException corrupt) { ledger = new ListeningLedger(); }
            finally { loaded.countDown(); }
        });
    }

    // Recording (any thread; applied in order on the stats thread).

    public void recordPlay(String id, String title, String artist, String album, long atMs) {
        run(() -> { ledger.recordPlay(id, title, artist, album, atMs); changed(); });
    }

    public void addListened(String id, String title, String artist, String album, long ms, long atMs) {
        if (ms <= 0) return;
        run(() -> { ledger.addListened(id, title, artist, album, ms, atMs); changed(); });
    }

    public void clear() { run(() -> { ledger.clear(); changed(); }); }

    /** Writes pending changes now (for example when the playback service ends). */
    public void flush() { run(this::save); }

    private void save() {
        saveScheduled = false;
        try { prefs.edit().putString("ledger", ledger.encode()).apply(); } catch (RuntimeException ignored) {}
    }

    // Queries (blocking until loaded; prefer a worker thread).

    /** Most played tracks since {@code sinceMs} (epoch ms; 0 for all time). */
    public List<ListeningLedger.TrackStat> topTracks(int n, long sinceMs) { awaitLoad(); return ledger.topTracks(n, sinceMs); }

    /** Most played artists since {@code sinceMs} (epoch ms; 0 for all time). */
    public List<ListeningLedger.ArtistStat> topArtists(int n, long sinceMs) { awaitLoad(); return ledger.topArtists(n, sinceMs); }

    /** Whole minutes listened since {@code sinceMs} (epoch ms; 0 for all time). */
    public long totalMinutes(long sinceMs) { awaitLoad(); return ledger.totalMinutes(sinceMs); }

    /** All-time figures for one track id, or null if it was never played. */
    public ListeningLedger.TrackStat track(String id) { awaitLoad(); return ledger.track(id); }

    /** Most recently played tracks, newest first. */
    public List<ListeningLedger.TrackStat> recent(int n) { awaitLoad(); return ledger.recent(n); }

    public void addListener(Runnable listener) { if (listener != null) listeners.addIfAbsent(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    private void awaitLoad() {
        try { loaded.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void run(Runnable task) {
        try { io.execute(() -> { awaitLoad(); task.run(); }); } catch (RejectedExecutionException ignored) {}
    }

    /** Called on the stats thread: coalesces saves and notifies listeners on the main thread. */
    private void changed() {
        main.removeCallbacks(notifyListeners);
        main.postDelayed(notifyListeners, 250);
        if (saveScheduled) return;
        saveScheduled = true;
        main.postDelayed(() -> run(this::save), SAVE_DELAY_MS);
    }
}
