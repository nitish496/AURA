package dev.lilt.player.stream;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Small thread-safe LRU cache whose entries expire after a per-entry time to live. Pure Java. */
final class TtlCache<K, V> {
    private static final class Entry<V> {
        final V value; final long expiresAt;
        Entry(V value, long expiresAt) { this.value = value; this.expiresAt = expiresAt; }
    }

    private final int maxEntries;
    private final LongSupplier clock;
    private final LinkedHashMap<K, Entry<V>> map = new LinkedHashMap<>(16, 0.75f, true);

    TtlCache(int maxEntries, LongSupplier clock) {
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    synchronized V get(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null) return null;
        if (clock.getAsLong() >= entry.expiresAt) { map.remove(key); return null; }
        return entry.value;
    }

    synchronized void put(K key, V value, long ttlMs) {
        if (value == null || ttlMs <= 0) return;
        map.put(key, new Entry<>(value, clock.getAsLong() + ttlMs));
        Iterator<Map.Entry<K, Entry<V>>> eldest = map.entrySet().iterator();
        while (map.size() > maxEntries && eldest.hasNext()) { eldest.next(); eldest.remove(); }
    }

    synchronized void remove(K key) { map.remove(key); }

    synchronized void clear() { map.clear(); }

    synchronized int size() { return map.size(); }
}
