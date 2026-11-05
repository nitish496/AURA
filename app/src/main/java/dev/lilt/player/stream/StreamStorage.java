package dev.lilt.player.stream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Process-wide application context, storage locations and a background I/O thread for the stream
 * package. Any stream entry point that receives a {@link Context} records the application context
 * here, so the context-free singletons ({@link AudiusClient#get()}, {@link LyricsClient#get()})
 * gain disk caching and embedded-lyrics access as soon as the app has touched one of them.
 */
final class StreamStorage {
    private StreamStorage() {}

    // Only ever the Application context, which lives exactly as long as the process.
    @SuppressLint("StaticFieldLeak")
    private static volatile Context app;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "aura-stream-io");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    static void init(Context context) {
        if (app != null || context == null) return;
        Context application = context.getApplicationContext();
        if (application == null) return;
        synchronized (StreamStorage.class) {
            if (app != null) return;
            app = application;
        }
        AudiusClient.get().restoreHostAsync();
    }

    /** Application context, or null before any stream class received a Context. */
    static Context context() { return app; }

    /** {@code files/stream}, or null before {@link #init(Context)}. */
    static File filesDir() {
        Context context = app;
        return context == null ? null : new File(context.getFilesDir(), "stream");
    }

    /** {@code cache/<name>}, or null before {@link #init(Context)}. */
    static File cacheDir(String name) {
        Context context = app;
        return context == null ? null : new File(context.getCacheDir(), name);
    }

    /** Single low-priority thread for small persistence jobs. */
    static void io(Runnable task) {
        try { IO.execute(task); } catch (RuntimeException rejected) { /* process is shutting down */ }
    }

    /** Throws when called on the main thread: the operation blocks on network or disk. */
    static void requireWorkerThread(String operation) {
        if (Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException(operation + " blocks on network/disk I/O; call it from a background thread");
        }
    }
}
