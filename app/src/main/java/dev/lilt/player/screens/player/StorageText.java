package dev.lilt.player.screens.player;

import java.util.Locale;

/** Byte counts for the stream cache rows ("412 MB of 1 GB"). Pure Java; tests/player/StorageTextTest. */
public final class StorageText {
    private StorageText() {}

    /** "0 KB", "820 KB", "412 MB", "1.2 GB" (decimal units, like Android's storage settings). */
    public static String bytes(long n) {
        long b = Math.max(0, n);
        if (b < 1_000_000L) return Math.round(b / 1000.0) + " KB";
        if (b < 1_000_000_000L) return Math.round(b / 1_000_000.0) + " MB";
        double g = b / 1_000_000_000.0;
        return (g < 10 ? trim(String.format(Locale.ROOT, "%.1f", g)) : Long.toString(Math.round(g))) + " GB";
    }

    /** "412 MB of 1 GB used", or just the usage when the limit is unknown. */
    public static String usage(long used, long limit) {
        if (limit <= 0) return bytes(used) + " used";
        return bytes(used) + " of " + bytes(limit) + " used";
    }

    /** 0..1 share of the limit, for a usage bar. */
    public static float share(long used, long limit) {
        if (limit <= 0 || used <= 0) return 0;
        return (float) Math.min(1.0, used / (double) limit);
    }

    private static String trim(String s) { return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s; }
}
