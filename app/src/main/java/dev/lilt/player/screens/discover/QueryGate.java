package dev.lilt.player.screens.discover;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generation counter for asynchronous loads: every new request bumps the generation, and a
 * result is applied only while its generation is still current, so a slow answer to an old query
 * never replaces a newer one. {@link #isCurrent} is safe from worker threads (skip work early).
 * Pure Java.
 */
public final class QueryGate {
    /** Debounce before a typed query reaches the network. */
    public static final long DEBOUNCE_MS = 250;

    private final AtomicInteger generation = new AtomicInteger();
    private volatile String query = "";

    /** Starts a new request for {@code normalizedQuery}; returns its generation. */
    public int begin(String normalizedQuery) {
        query = normalizedQuery == null ? "" : normalizedQuery;
        return generation.incrementAndGet();
    }

    /** Drops every outstanding request (screen destroyed, query cleared). */
    public void cancel() { query = ""; generation.incrementAndGet(); }

    public boolean isCurrent(int gen) { return generation.get() == gen; }

    /** Query of the current request ("" after {@link #cancel}). */
    public String query() { return query; }

    /** Trimmed with inner whitespace collapsed; "" for null. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    /** True when {@code normalized} differs from the current request in a way the network would notice. */
    public boolean changes(String normalized) {
        return !SearchText.fold(normalized).equals(SearchText.fold(query));
    }
}
