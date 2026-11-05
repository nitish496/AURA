package dev.lilt.player.audio;

import java.util.Locale;

/**
 * Stable cache keys for streamed audio and the media-cache size budget.
 *
 * <p>The catalogue API can fail over between hosts, so the same Audius track may be served from
 * different URLs on different days. Keying the cache on the track id rather than the full URL keeps
 * repeat listens offline after a host change. Anything that is not a recognised stream URL keeps
 * its full URI as the key (Media3's default behaviour).
 */
public final class CacheKeys {
    public static final long MAX_CACHE_BYTES = 1024L * 1024 * 1024;
    public static final long MIN_CACHE_BYTES = 128L * 1024 * 1024;
    private static final String TRACKS = "/v1/tracks/";
    private CacheKeys() {}

    /** Returns the cache key for a stream or artwork URI; never null. */
    public static String forUri(String uri) {
        if (uri == null) return "";
        String lower = uri.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return uri;
        int marker = lower.indexOf(TRACKS);
        if (marker < 0) return uri;
        int start = marker + TRACKS.length(), end = start;
        while (end < uri.length() && isIdChar(uri.charAt(end))) end++;
        if (end == start || end - start > 64) return uri;
        // Only the audio endpoint; other track endpoints (e.g. JSON metadata) keep their own key.
        if (!lower.startsWith("/stream", end)) return uri;
        int after = end + "/stream".length();
        if (after < uri.length() && "?#/".indexOf(uri.charAt(after)) < 0) return uri;
        return "audius-stream:" + uri.substring(start, end);
    }

    private static boolean isIdChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    /**
     * LRU budget for the stream cache: about 1 GB, but never more than a fifth of the free space so
     * the cache cannot crowd out the user's photos and apps on a nearly full phone.
     */
    public static long budgetBytes(long usableBytes) {
        if (usableBytes <= 0) return MIN_CACHE_BYTES;
        return Math.max(MIN_CACHE_BYTES, Math.min(MAX_CACHE_BYTES, usableBytes / 5));
    }
}
