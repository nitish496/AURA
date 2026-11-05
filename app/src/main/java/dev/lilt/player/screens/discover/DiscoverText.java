package dev.lilt.player.screens.discover;

import dev.lilt.player.ui.TimeText;
import java.util.Locale;

/**
 * Copy and number formatting for the discovery screens (Browse, Search, catalogue pages).
 * Counts never overstate: 12,480 followers is "12.4K", not "12.5K". Pure Java.
 */
public final class DiscoverText {
    private DiscoverText() {}

    /** Compact count: 950, "1.2K", "12.4K", "124K", "1.3M", "12.4M", "124M", "1.2B". Floors. */
    public static String compact(long n) {
        if (n < 0) n = 0;
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return scaled(n, 1000, "K");
        if (n < 1_000_000_000) return scaled(n, 1_000_000, "M");
        return scaled(n, 1_000_000_000, "B");
    }

    private static String scaled(long n, long unit, String suffix) {
        long whole = n / unit;
        if (whole >= 100) return whole + suffix;
        long tenths = (n % unit) * 10 / unit;
        return tenths == 0 ? whole + suffix : whole + "." + tenths + suffix;
    }

    /** "1 follower", "950 followers", "12.4K followers"; "" when there are none. */
    public static String followers(long n) {
        if (n <= 0) return "";
        return compact(n) + (n == 1 ? " follower" : " followers");
    }

    /** "12.4K followers on Audius" for artist headers; "" when unknown. */
    public static String followersOnAudius(long n) {
        String f = followers(n);
        return f.isEmpty() ? "" : f + " on Audius";
    }

    /** "32 songs · 1 hr 54 min", or just "32 songs" while the duration is unknown; "" for 0 songs. */
    public static String collection(int songs, long durationMs) {
        if (songs <= 0) return "";
        return durationMs > 0 ? TimeText.summary(songs, durationMs, Locale.getDefault()) : TimeText.songs(songs, Locale.getDefault());
    }

    /** Display name for an Audius genre ("Hip-Hop/Rap" → "Hip-Hop & Rap"). */
    public static String genre(String apiGenre) {
        if (apiGenre == null) return "";
        if (apiGenre.equals("Hip-Hop/Rap")) return "Hip-Hop & Rap";
        return apiGenre;
    }

    /** Trending windows, in segmented-control order. */
    public static final String[] WINDOWS = {"week", "month", "allTime"};
    public static final String[] WINDOW_LABELS = {"This Week", "This Month", "All Time"};

    /** Chart header for a window: "Top Songs This Week". */
    public static String chartTitle(String window) {
        for (int i = 0; i < WINDOWS.length; i++) if (WINDOWS[i].equals(window)) return "Top Songs " + WINDOW_LABELS[i];
        return "Top Songs";
    }

    /** "Song · Kofi Mensah", "Album · The Quiet Hours"; just the kind when {@code detail} is blank. */
    public static String kind(String kind, String detail) {
        return detail == null || detail.trim().isEmpty() ? kind : kind + TimeText.DOT + detail.trim();
    }

    /** Subtitle of a remote song row: the album unless it is a single, else the artist. */
    public static String joined(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(TimeText.DOT);
            b.append(p.trim());
        }
        return b.toString();
    }
}
