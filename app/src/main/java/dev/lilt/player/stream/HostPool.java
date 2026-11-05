package dev.lilt.player.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Host selection state for the Audius API: discovered candidate hosts, the host in use, per-host
 * failure cooldowns and the always-available gateway fallback. All times come from the caller so
 * the policy is deterministic under test. Thread-safe; pure Java.
 */
final class HostPool {
    /** Health check run against one host during a selection round. */
    interface Probe { boolean healthy(String host) throws Exception; }

    /** A failed host is skipped for this long. */
    static final long FAILURE_COOLDOWN_MS = 5 * 60_000L;
    /** After falling back to the gateway, discovered hosts are retried after this long. */
    static final long GATEWAY_STICKY_MS = 5 * 60_000L;
    /** Discovered host lists are refreshed after this long. */
    static final long DISCOVERY_MAX_AGE_MS = 6 * 60 * 60_000L;
    /** Minimum spacing between discovery attempts (keeps offline retries cheap). */
    static final long DISCOVERY_RETRY_MS = 30_000L;

    private final String gateway;
    private final List<String> candidates = new ArrayList<>();
    private final Map<String, Long> coolUntil = new HashMap<>();
    private String current;
    private long gatewayUntil;
    private long discoveredAt = Long.MIN_VALUE / 4, discoveryAttemptAt = Long.MIN_VALUE / 4;

    HostPool(String gateway) { this.gateway = gateway; }

    String gateway() { return gateway; }

    /** The host to use now, or null when a new selection round is needed. */
    synchronized String current(long now) {
        if (current == null) return null;
        if (cooling(current, now) || (current.equals(gateway) && now >= gatewayUntil)) { current = null; return null; }
        return current;
    }

    /** Last selected host without expiry checks (may be null); for building stream URLs. */
    synchronized String last() { return current; }

    synchronized void use(String host, long now) {
        current = host;
        coolUntil.remove(host);
        if (host.equals(gateway)) gatewayUntil = now + GATEWAY_STICKY_MS;
    }

    synchronized void markFailed(String host, long now) {
        if (host == null) return;
        coolUntil.put(host, now + FAILURE_COOLDOWN_MS);
        if (host.equals(current)) current = null;
    }

    synchronized boolean cooling(String host, long now) {
        Long until = coolUntil.get(host);
        if (until == null) return false;
        if (now >= until) { coolUntil.remove(host); return false; }
        return true;
    }

    synchronized void setCandidates(Collection<String> hosts, long now) {
        candidates.clear();
        for (String host : new LinkedHashSet<>(hosts)) if (host != null && !host.equals(gateway)) candidates.add(host);
        discoveredAt = now;
    }

    synchronized List<String> candidates() { return new ArrayList<>(candidates); }

    synchronized long discoveredAt() { return discoveredAt; }

    synchronized void discoveryAttempted(long now) { discoveryAttemptAt = now; }

    /** Discovery is due when no candidate is usable or the list is stale, rate-limited by {@link #DISCOVERY_RETRY_MS}. */
    synchronized boolean discoveryDue(long now) {
        if (now - discoveryAttemptAt < DISCOVERY_RETRY_MS) return false;
        return now - discoveredAt >= DISCOVERY_MAX_AGE_MS || usable(now).isEmpty();
    }

    /** Up to {@code max} usable candidates in random order (spreads load across the network). */
    synchronized List<String> probeOrder(long now, int max, Random random) {
        List<String> usable = usable(now);
        Collections.shuffle(usable, random);
        return new ArrayList<>(usable.subList(0, Math.min(Math.max(0, max), usable.size())));
    }

    private List<String> usable(long now) {
        List<String> usable = new ArrayList<>();
        for (String host : candidates) if (!cooling(host, now)) usable.add(host);
        return usable;
    }

    /**
     * Probes all {@code hosts} in parallel and returns the first one reporting healthy within
     * {@code timeoutMs}, or null. Slower probes are cancelled (best effort).
     */
    static String firstHealthy(List<String> hosts, Probe probe, ExecutorService executor, long timeoutMs) {
        if (hosts.isEmpty()) return null;
        CompletionService<String> completion = new ExecutorCompletionService<>(executor);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (String host : hosts) futures.add(completion.submit(() -> probe.healthy(host) ? host : null));
        } catch (RejectedExecutionException stopped) {
            for (Future<String> future : futures) future.cancel(true);
            return null;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            for (int received = 0; received < futures.size(); received++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                Future<String> done = completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (done == null) break;
                try {
                    String winner = done.get();
                    if (winner != null) return winner;
                } catch (ExecutionException failed) { /* unhealthy host */ }
            }
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            for (Future<String> future : futures) future.cancel(true);
        }
    }
}
