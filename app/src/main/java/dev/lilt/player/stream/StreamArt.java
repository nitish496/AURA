package dev.lilt.player.stream;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audius artwork URL helpers. Audius serves square artwork as {@code .../150x150.jpg},
 * {@code .../480x480.jpg} and {@code .../1000x1000.jpg}; tracks store the largest, and lists ask
 * for a smaller variant here to save bandwidth and memory. URLs that do not follow the pattern are
 * returned unchanged. Pure Java.
 */
public final class StreamArt {
    private StreamArt() {}

    public static final int SMALL = 150, MEDIUM = 480, LARGE = 1000;
    private static final Pattern SIZED = Pattern.compile("^(.*/)(?:150x150|480x480|1000x1000)(\\.[A-Za-z]{3,4})?(\\?.*)?$");

    /** The 480x480 variant: list rows, grid tiles and notification thumbnails. */
    public static String thumb(String url) { return variant(url, MEDIUM); }

    /** The smallest variant fitting {@code pixels} on screen (150, 480 or 1000). */
    public static String forSize(String url, int pixels) {
        return variant(url, pixels <= SMALL ? SMALL : pixels <= MEDIUM ? MEDIUM : LARGE);
    }

    /** {@code url} rewritten to the {@code size}x{@code size} variant when it follows the Audius pattern. */
    public static String variant(String url, int size) {
        if (url == null) return null;
        if (size != SMALL && size != MEDIUM && size != LARGE) throw new IllegalArgumentException("Unsupported size " + size);
        Matcher m = SIZED.matcher(url);
        if (!m.matches()) return url;
        String extension = m.group(2) == null ? "" : m.group(2);
        String query = m.group(3) == null ? "" : m.group(3);
        return m.group(1) + size + "x" + size + extension + query;
    }

    /** True for https URLs (the only artwork scheme the app loads). */
    public static boolean isRemote(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8);
    }
}
