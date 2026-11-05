package dev.lilt.player.stream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An ordered, bounded map of id to immutable value with a timestamp per entry, persisted as one
 * JSON file. Loads lazily (first access), writes are debounced onto a background thread and
 * replace the file atomically. A corrupt file is moved aside instead of crashing. Thread-safe;
 * pure Java (org.json only). Values must be immutable.
 */
final class RecordStore<T> {
    interface Codec<T> {
        JSONObject encode(T value) throws JSONException;
        /** Null when the stored JSON is unusable (the entry is dropped). */
        T decode(JSONObject json);
    }

    enum Overflow { EVICT_OLDEST, REJECT }

    static final int ADDED = 1, UPDATED = 0, REJECTED = -1;
    private static final int VERSION = 1;
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "aura-stream-store");
        thread.setDaemon(true);
        return thread;
    });

    private static final class Entry<T> {
        final T value; final long time;
        Entry(T value, long time) { this.value = value; this.time = time; }
    }

    private final File file;
    private final Codec<T> codec;
    private final int maxEntries, maxFileBytes;
    private final Overflow overflow;
    private final long writeDelayMs;
    private final LongSupplier clock;
    /** Oldest first; the newest entry is last. */
    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<>();
    private final Object writeLock = new Object();
    private boolean loaded, writeScheduled;
    private long revision;
    private List<T> newestFirstCache;
    private long newestFirstRevision = -1;

    RecordStore(File file, Codec<T> codec, int maxEntries, Overflow overflow, int maxFileBytes, long writeDelayMs, LongSupplier clock) {
        this.file = file;
        this.codec = codec;
        this.maxEntries = Math.max(1, maxEntries);
        this.overflow = overflow;
        this.maxFileBytes = maxFileBytes;
        this.writeDelayMs = Math.max(0, writeDelayMs);
        this.clock = clock;
    }

    /** Loads the file once; later calls return immediately. Safe to call from any thread. */
    synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (file == null) return;
        String text;
        try {
            text = JsonFiles.read(file, maxFileBytes);
        } catch (IOException unreadable) {
            quarantine();
            return;
        }
        if (text == null) return;
        try {
            JSONArray items = new JSONObject(text).optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = AudiusParser.text(item, "id");
                T value = id.isEmpty() ? null : codec.decode(item.optJSONObject("value"));
                if (value == null) continue;
                entries.remove(id);
                entries.put(id, new Entry<>(value, AudiusParser.number(item, "t")));
            }
            trim();
        } catch (JSONException | RuntimeException corrupt) {
            entries.clear();
            quarantine();
        }
    }

    /**
     * Inserts or updates {@code id}. New ids become the newest entry. For an existing id,
     * {@code touch} moves it to newest with a fresh timestamp; otherwise only the value changes.
     * @return {@link #ADDED}, {@link #UPDATED} or {@link #REJECTED} (store full with {@link Overflow#REJECT}).
     */
    synchronized int put(String id, T value, boolean touch) {
        ensureLoaded();
        Entry<T> existing = entries.get(id);
        long now = clock.getAsLong();
        int result;
        if (existing != null) {
            if (touch) { entries.remove(id); entries.put(id, new Entry<>(value, now)); }
            else entries.put(id, new Entry<>(value, existing.time));
            result = UPDATED;
        } else {
            if (entries.size() >= maxEntries && overflow == Overflow.REJECT) return REJECTED;
            entries.put(id, new Entry<>(value, now));
            trim();
            result = ADDED;
        }
        changed();
        return result;
    }

    synchronized T get(String id) {
        ensureLoaded();
        Entry<T> entry = entries.get(id);
        return entry == null ? null : entry.value;
    }

    /** Timestamp recorded when the id was added (or last touched); 0 when absent. */
    synchronized long time(String id) {
        ensureLoaded();
        Entry<T> entry = entries.get(id);
        return entry == null ? 0 : entry.time;
    }

    synchronized boolean contains(String id) {
        ensureLoaded();
        return entries.containsKey(id);
    }

    synchronized T remove(String id) {
        ensureLoaded();
        Entry<T> removed = entries.remove(id);
        if (removed != null) changed();
        return removed == null ? null : removed.value;
    }

    /** Immutable snapshot, newest first. Cached until the next change. */
    synchronized List<T> newestFirst() {
        ensureLoaded();
        if (newestFirstCache == null || newestFirstRevision != revision) {
            List<T> values = new ArrayList<>(entries.size());
            for (Entry<T> entry : entries.values()) values.add(entry.value);
            Collections.reverse(values);
            newestFirstCache = Collections.unmodifiableList(values);
            newestFirstRevision = revision;
        }
        return newestFirstCache;
    }

    synchronized int size() {
        ensureLoaded();
        return entries.size();
    }

    synchronized boolean full() {
        ensureLoaded();
        return entries.size() >= maxEntries;
    }

    /** Increments on every change; lets callers cache derived views. */
    synchronized long revision() { return revision; }

    /** Writes the current state now (blocking). Normally writes happen automatically. */
    void flush() throws IOException {
        if (file == null) return;
        // The snapshot is taken under the write lock so an older snapshot can never land after a newer one.
        synchronized (writeLock) {
            List<String> ids;
            List<Entry<T>> values;
            synchronized (this) {
                ensureLoaded();
                writeScheduled = false;
                ids = new ArrayList<>(entries.keySet());
                values = new ArrayList<>(entries.values());
            }
            JSONArray items = new JSONArray();
            for (int i = 0; i < ids.size(); i++) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("id", ids.get(i));
                    item.put("t", values.get(i).time);
                    item.put("value", codec.encode(values.get(i).value));
                    items.put(item);
                } catch (JSONException | RuntimeException skipped) { /* one bad value never blocks the rest */ }
            }
            JSONObject root = new JSONObject();
            try { root.put("v", VERSION).put("items", items); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
            JsonFiles.writeAtomic(file, root.toString());
        }
    }

    private void changed() {
        revision++;
        if (file == null || writeScheduled) return;
        writeScheduled = true;
        try {
            WRITER.schedule(() -> {
                try { flush(); } catch (IOException ignored) { /* retried on the next change */ }
            }, writeDelayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            writeScheduled = false;
        }
    }

    private void trim() {
        Iterator<String> oldest = entries.keySet().iterator();
        while (entries.size() > maxEntries && oldest.hasNext()) { oldest.next(); oldest.remove(); }
    }

    private void quarantine() {
        File aside = new File(file.getPath() + ".corrupt");
        //noinspection ResultOfMethodCallIgnored
        aside.delete();
        //noinspection ResultOfMethodCallIgnored
        file.renameTo(aside);
    }
}
