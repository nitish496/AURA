package dev.lilt.player.screens.player;

import android.graphics.Bitmap;
import android.os.Handler;
import dev.lilt.player.ArtworkTheme;
import dev.lilt.player.ui.ArtworkPalette;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Artwork palettes shared by Now Playing, Lyrics and Queue, so moving between them keeps the
 * exact same colour (no flash). Keyed by track id; sampled from the artwork bitmap off the main
 * thread (24 x 24 points). Main thread only, except the sampling task.
 */
public final class PlayerColors {
    private PlayerColors() {}

    public interface Callback { void onPalette(ArtworkPalette palette); }

    private static final int SIZE = 24;
    private static final Map<String, ArtworkPalette> cache = new LinkedHashMap<String, ArtworkPalette>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ArtworkPalette> e) { return size() > 40; }
    };

    /** Cached palette for a track id, or null. */
    public static ArtworkPalette cached(String trackId) {
        return trackId == null ? null : cache.get(trackId);
    }

    /** Remembers a palette (e.g. NEUTRAL for a song with no artwork). */
    public static void put(String trackId, ArtworkPalette palette) {
        if (trackId != null && palette != null) cache.put(trackId, palette);
    }

    /**
     * Samples {@code bitmap} on {@code io} and delivers the palette on {@code main}; the result
     * is cached for {@code trackId}. A null bitmap gives {@link ArtworkPalette#NEUTRAL}.
     */
    public static void sample(String trackId, Bitmap bitmap, Executor io, Handler main, Callback callback) {
        if (bitmap == null) { put(trackId, ArtworkPalette.NEUTRAL); callback.onPalette(ArtworkPalette.NEUTRAL); return; }
        try {
            io.execute(() -> {
                ArtworkPalette p;
                try {
                    if (bitmap.isRecycled() || bitmap.getConfig() == Bitmap.Config.HARDWARE) p = ArtworkPalette.NEUTRAL;
                    else {
                        int w = bitmap.getWidth(), h = bitmap.getHeight();
                        int[] px = new int[SIZE * SIZE];
                        for (int y = 0; y < SIZE; y++)
                            for (int x = 0; x < SIZE; x++)
                                px[y * SIZE + x] = bitmap.getPixel(Math.min(w - 1, (x * w + w / 2) / SIZE), Math.min(h - 1, (y * h + h / 2) / SIZE));
                        p = ArtworkTheme.palette(px);
                    }
                } catch (RuntimeException failed) {
                    p = ArtworkPalette.NEUTRAL;
                }
                final ArtworkPalette result = p;
                main.post(() -> { put(trackId, result); callback.onPalette(result); });
            });
        } catch (RejectedExecutionException shutDown) {
            callback.onPalette(ArtworkPalette.NEUTRAL);
        }
    }
}
