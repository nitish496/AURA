package dev.lilt.player.screens.player;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import dev.lilt.player.PlaybackService;
import dev.lilt.player.app.PlayerHub;
import java.util.ArrayList;
import java.util.List;

/**
 * A small, shared extra connection to {@link PlaybackService} for the player screens: the session
 * commands {@link PlayerHub} does not wrap (stream cache info / clear, "end of song" sleep timer
 * and its state), the decoded format of the current song (for the truthful format line) and the
 * shuffled play order (for Queue). Reference-counted: screens {@link #acquire} it while visible
 * and {@link #release} it after; the controller disconnects 15 s after the last release.
 * Main thread only.
 */
@OptIn(markerClass = UnstableApi.class)
public final class SessionLink {
    /** Something the link reports changed (format, timeline / shuffle order). */
    public interface Listener { void onLinkChanged(); }

    private static SessionLink instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>(3);
    private final List<Runnable> pending = new ArrayList<>(4);
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private int users;
    private final Runnable disconnect = this::disconnectNow;
    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onTracksChanged(Tracks tracks) { notifyChanged(); }
        @Override public void onTimelineChanged(Timeline timeline, int reason) { notifyChanged(); }
        @Override public void onShuffleModeEnabledChanged(boolean enabled) { notifyChanged(); }
        @Override public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) { notifyChanged(); }
    };

    private SessionLink(Context context) { app = context.getApplicationContext(); }

    public static SessionLink get(Context context) {
        if (instance == null) instance = new SessionLink(context);
        return instance;
    }

    public void acquire() {
        users++;
        main.removeCallbacks(disconnect);
        connect();
    }

    public void release() {
        if (users > 0) users--;
        if (users == 0) { main.removeCallbacks(disconnect); main.postDelayed(disconnect, 15_000); }
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public boolean connected() { return controller != null && controller.isConnected(); }

    private void connect() {
        if (future != null) return;
        try {
            future = new MediaController.Builder(app, new SessionToken(app, new ComponentName(app, PlaybackService.class))).buildAsync();
        } catch (RuntimeException failed) {
            future = null;
            return;
        }
        final ListenableFuture<MediaController> started = future;
        started.addListener(() -> {
            if (future != started) return;
            try {
                controller = started.get();
                controller.addListener(playerListener);
                List<Runnable> run = new ArrayList<>(pending);
                pending.clear();
                for (Runnable r : run) r.run();
                notifyChanged();
            } catch (Exception failed) {
                future = null;
                controller = null;
                pending.clear();
            }
        }, main::post);
    }

    private void disconnectNow() {
        if (users > 0) return;
        if (controller != null) controller.removeListener(playerListener);
        if (future != null) MediaController.releaseFuture(future);
        future = null;
        controller = null;
        pending.clear();
    }

    private void notifyChanged() {
        for (int i = 0; i < listeners.size(); i++) listeners.get(i).onLinkChanged();
    }

    /** Runs {@code command}; {@code result} gets the result extras, or null on failure. */
    public void send(SessionCommand command, Bundle args, PlayerHub.Callback<Bundle> result) {
        Runnable task = () -> {
            MediaController c = controller;
            if (c == null) { if (result != null) result.accept(null); return; }
            ListenableFuture<SessionResult> f = c.sendCustomCommand(command, args == null ? Bundle.EMPTY : args);
            f.addListener(() -> {
                Bundle extras = null;
                try {
                    SessionResult r = f.get();
                    if (r.resultCode == SessionResult.RESULT_SUCCESS) extras = r.extras;
                } catch (Exception ignored) { /* reported as null */ }
                if (result != null) result.accept(extras);
            }, main::post);
        };
        if (controller != null) task.run();
        else { pending.add(task); connect(); }
    }

    /** Format line for the current local song, "" when the player has not reported one. */
    public String formatLabel() {
        MediaController c = controller;
        if (c == null) return "";
        try {
            Tracks tracks = c.getCurrentTracks();
            for (Tracks.Group g : tracks.getGroups()) {
                if (g.getType() != C.TRACK_TYPE_AUDIO || !g.isSelected()) continue;
                for (int i = 0; i < g.length; i++) {
                    if (!g.isTrackSelected(i)) continue;
                    Format f = g.getTrackFormat(i);
                    int bitrate = f.averageBitrate > 0 ? f.averageBitrate : f.bitrate;
                    int bits = f.pcmEncoding == Format.NO_VALUE ? 0 : FormatLabel.bitsForPcmEncoding(f.pcmEncoding);
                    return FormatLabel.describe(f.sampleMimeType, f.sampleRate, bits, bitrate);
                }
            }
        } catch (RuntimeException unavailable) { /* not connected yet */ }
        return "";
    }

    /** The full shuffled play order (queue indices) when shuffle is on, else null. */
    public int[] shuffleOrder() {
        MediaController c = controller;
        if (c == null || !c.getShuffleModeEnabled()) return null;
        try {
            Timeline t = c.getCurrentTimeline();
            int n = t.getWindowCount();
            if (n == 0) return null;
            int[] order = new int[n];
            int w = t.getFirstWindowIndex(true), k = 0;
            while (w != C.INDEX_UNSET && k < n) { order[k++] = w; w = t.getNextWindowIndex(w, Player.REPEAT_MODE_OFF, true); }
            return k == n ? order : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
