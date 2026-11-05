package dev.lilt.player.audio;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Remembers AURA's loudness measurement per media id so normalisation works from a song's second
 * listen onwards (files and streams without ReplayGain tags). Loaded and written off the main
 * thread; lookups are in-memory and return null until the store has loaded.
 */
public final class LoudnessStore {
    /** A stored measurement. */
    public static final class Entry {
        public final double lufs, peak;
        public final long measuredMs;
        Entry(double lufs, double peak, long measuredMs) { this.lufs = lufs; this.peak = peak; this.measuredMs = measuredMs; }
    }

    private static final int MAX_ENTRIES = 6000;
    private final SharedPreferences prefs;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "AuraLoudness"));
    private volatile boolean loaded;
    private Runnable onLoaded;

    public LoudnessStore(Context context, Runnable onLoaded) {
        prefs = context.getApplicationContext().getSharedPreferences("loudness", Context.MODE_PRIVATE);
        this.onLoaded = onLoaded;
        submit(() -> {
            for (Map.Entry<String, ?> stored : prefs.getAll().entrySet()) {
                Entry e = stored.getValue() instanceof String ? parse((String) stored.getValue()) : null;
                if (e != null) entries.put(stored.getKey(), e);
            }
            loaded = true;
            Runnable callback = this.onLoaded;
            if (callback != null) callback.run();
        });
    }

    public boolean loaded() { return loaded; }

    public Entry get(String mediaId) { return mediaId == null ? null : entries.get(mediaId); }

    /** Stores a measurement when it is valid, long enough and covers more than what is stored. */
    public void offer(String mediaId, LoudnessMeter.Measurement m, long durationMs) {
        if (mediaId == null || mediaId.isEmpty() || m == null || !m.valid()) return;
        if (!LoudnessRules.usableMeasurement(m.measuredMs, durationMs)) return;
        Entry existing = entries.get(mediaId);
        if (existing != null && existing.measuredMs >= m.measuredMs) return;
        Entry e = new Entry(m.integratedLufs, m.peak, m.measuredMs);
        entries.put(mediaId, e);
        String value = String.format(Locale.ROOT, "%.2f;%.5f;%d", e.lufs, e.peak, e.measuredMs);
        submit(() -> {
            SharedPreferences.Editor editor = prefs.edit().putString(mediaId, value);
            if (entries.size() > MAX_ENTRIES) {
                // Rarely reached; drop an arbitrary eighth to bound the file.
                int drop = MAX_ENTRIES / 8;
                for (String key : entries.keySet()) { if (drop-- <= 0) break; if (!key.equals(mediaId)) { entries.remove(key); editor.remove(key); } }
            }
            editor.apply();
        });
    }

    public void close() { onLoaded = null; io.shutdown(); }

    private void submit(Runnable task) {
        try { io.execute(task); } catch (RejectedExecutionException closed) { /* service is shutting down */ }
    }

    private static Entry parse(String value) {
        try {
            String[] f = value.split(";");
            if (f.length != 3) return null;
            double lufs = Double.parseDouble(f[0]), peak = Double.parseDouble(f[1]);
            long ms = Long.parseLong(f[2]);
            return Double.isFinite(lufs) && lufs > -70 && lufs < 10 && peak >= 0 && ms > 0 ? new Entry(lufs, peak, ms) : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
