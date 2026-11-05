package dev.lilt.player;

import android.app.Application;
import android.content.Context;
import dev.lilt.player.audio.ListeningStats;

/**
 * Starts the slow, independent parts of start-up on a background thread while the first activity
 * is still being created: SharedPreferences files are read from disk on first use (blocking the
 * main thread inside {@code Shell}), and the listening-stats store parses its ledger. Warming them
 * here means the main thread finds them already in memory. Pure warm-up: failures are ignored and
 * nothing here is required for correctness.
 */
public final class AuraApp extends Application {
    private static final String[] PREFS = {"library", "aura_shell", "aura_ui", "song_moods", "playback", "audio_effects"};

    @Override public void onCreate() {
        super.onCreate();
        final Context app = this;
        Thread warm = new Thread(() -> {
            for (String name : PREFS) {
                try { app.getSharedPreferences(name, Context.MODE_PRIVATE).getAll(); } catch (RuntimeException ignored) { }
            }
            try { ListeningStats.get(app); } catch (RuntimeException ignored) { }
        }, "AuraWarmUp");
        warm.setDaemon(true);
        warm.setPriority(Thread.NORM_PRIORITY - 1);
        warm.start();
    }
}
