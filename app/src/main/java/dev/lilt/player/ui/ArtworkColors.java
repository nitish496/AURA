package dev.lilt.player.ui;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Bridges Android bitmaps to {@link ArtworkPalette}: samples a 24 x 24 grid (hardware bitmaps
 * are copied first) and builds the palette. Sampling is quick but not free: use
 * {@link #paletteAsync} from UI code.
 */
public final class ArtworkColors {
    private ArtworkColors() {}

    public static final int GRID = 24;

    /** {@code GRID x GRID} ARGB samples of {@code bitmap} (empty for null / recycled bitmaps). */
    public static int[] sample(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) return new int[0];
        Bitmap src = bitmap;
        boolean copied = false;
        if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            src = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (src == null) return new int[0];
            copied = true;
        }
        int[] out = new int[GRID * GRID];
        int w = src.getWidth(), h = src.getHeight();
        for (int y = 0; y < GRID; y++)
            for (int x = 0; x < GRID; x++)
                out[y * GRID + x] = src.getPixel(Math.min(w - 1, (x * w + w / 2) / GRID), Math.min(h - 1, (y * h + h / 2) / GRID));
        if (copied) src.recycle();
        return out;
    }

    public static ArtworkPalette palette(Bitmap bitmap) { return ArtworkPalette.from(sample(bitmap)); }

    /** Builds the palette on {@code background} and delivers it on the main thread. */
    public static void paletteAsync(Bitmap bitmap, Executor background, Consumer<ArtworkPalette> callback) {
        Handler main = new Handler(Looper.getMainLooper());
        background.execute(() -> {
            ArtworkPalette p = palette(bitmap);
            main.post(() -> callback.accept(p));
        });
    }
}
