package dev.lilt.player.app;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import dev.lilt.player.PlaybackService;
import dev.lilt.player.Track;
import dev.lilt.player.stream.RemoteTrackStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link PlayerHub} over a Media3 {@link MediaController} bound to {@link PlaybackService}.
 *
 * <p>Queue operations are serialised: MediaItems are built on a single background thread (a
 * 2 000-song list never blocks the main thread) and applied on the main thread in the order the
 * calls were made, also when they were issued before the controller connected. Streamed tracks
 * are stored in {@link RemoteTrackStore} before they are queued so their ids resolve later.
 * Listeners run on the main thread.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlayerHubImpl implements PlayerHub {
    /** Largest queue handed to the player (matches what the service persists). */
    static final int MAX_QUEUE = 2000;

    /** Callbacks the shell cares about beyond {@link Listener}. */
    public interface Events {
        void onMessage(String message);
        void onTrackStarted(Track track);
        void onFirstPlayback();
    }

    private final Context context;
    private final LibraryRepoImpl library;
    private final RemoteTrackStore remote;
    private final Events events;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AuraQueueBuild");
        t.setDaemon(true);
        return t;
    });
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private final ListenableFuture<MediaController> future;
    private MediaController controller;
    private boolean released, waitingForNetwork, notifyPosted, startedOnce;
    private List<Track> queueCache;
    private String lastStartedId = "";

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events e) {
            if (e.contains(Player.EVENT_TIMELINE_CHANGED)) queueCache = null;
            if (e.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) trackStarted();
            notifyChanged();
        }
    };

    private final MediaController.Listener controllerListener = new MediaController.Listener() {
        @Override public void onExtrasChanged(MediaController c, Bundle extras) {
            boolean waiting = extras != null && extras.getBoolean(PlaybackService.EXTRA_WAITING_FOR_NETWORK, false);
            if (waiting != waitingForNetwork) { waitingForNetwork = waiting; notifyChanged(); }
        }
        @Override public void onError(MediaController c, SessionError error) {
            if (error != null && error.message != null && !error.message.isEmpty()) events.onMessage(error.message);
        }
        @Override public void onDisconnected(MediaController c) {
            controller = null;
            queueCache = null;
            notifyChanged();
        }
    };

    public PlayerHubImpl(Context context, LibraryRepoImpl library, Events events) {
        this.context = context.getApplicationContext();
        this.library = library;
        this.events = events;
        this.remote = new RemoteTrackStore(this.context);
        SessionToken token = new SessionToken(this.context, new ComponentName(this.context, PlaybackService.class));
        future = new MediaController.Builder(this.context, token).setListener(controllerListener).buildAsync();
        future.addListener(() -> {
            if (released) return;
            try {
                controller = future.get();
            } catch (Exception failure) {
                events.onMessage("Playback couldn't start. Close and reopen AURA.");
                return;
            }
            controller.addListener(playerListener);
            waitingForNetwork = controller.getSessionExtras().getBoolean(PlaybackService.EXTRA_WAITING_FOR_NETWORK, false);
            List<Runnable> queued = new ArrayList<>(pending);
            pending.clear();
            for (Runnable r : queued) r.run();
            notifyChanged();
        }, main::post);
    }

    public void release() {
        released = true;
        builder.shutdownNow();
        main.removeCallbacksAndMessages(null);
        pending.clear();
        listeners.clear();
        if (controller != null) controller.removeListener(playerListener);
        MediaController.releaseFuture(future);
        controller = null;
    }

    /** Runs now when connected, otherwise once the controller connects (in order). */
    private void whenConnected(Runnable r) {
        if (released) return;
        if (controller != null && pending.isEmpty()) r.run();
        else pending.add(r);
    }

    private interface ItemsReady { void apply(MediaController c, List<MediaItem> items); }

    /** Builds MediaItems off the main thread, then applies them in call order once connected. */
    private void withItems(List<Track> tracks, ItemsReady action) {
        if (released || tracks.isEmpty()) return;
        final List<Track> copy = new ArrayList<>(tracks);
        try {
            builder.execute(() -> {
                List<Track> streamed = new ArrayList<>();
                List<MediaItem> items = new ArrayList<>(copy.size());
                for (Track t : copy) {
                    if (t.isRemote()) streamed.add(t);
                    items.add(t.item(library.customCover(t)));
                }
                if (!streamed.isEmpty()) remote.putAll(streamed);
                main.post(() -> whenConnected(() -> { if (controller != null) action.apply(controller, items); }));
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {}
    }

    // ------------------------------------------------------------------ state

    @Override public boolean connected() { return controller != null; }

    @Override public Track current() {
        MediaController c = controller;
        if (c == null) return null;
        MediaItem item = c.getCurrentMediaItem();
        return item == null ? null : toTrack(item);
    }

    private Track toTrack(MediaItem item) {
        Track t = library.find(item.mediaId);
        if (t != null) return t;
        MediaMetadata m = item.mediaMetadata;
        Uri uri = item.localConfiguration != null ? item.localConfiguration.uri
                : item.requestMetadata.mediaUri != null ? item.requestMetadata.mediaUri : Uri.EMPTY;
        String title = m.title == null ? "" : m.title.toString();
        String artist = m.artist == null ? "" : m.artist.toString();
        String album = m.albumTitle == null ? "" : m.albumTitle.toString();
        long duration = m.durationMs == null ? 0 : m.durationMs;
        if (item.mediaId.startsWith("audius:") && !Uri.EMPTY.equals(uri)) {
            String art = m.artworkUri == null ? null : m.artworkUri.toString();
            String genre = m.genre == null ? "" : m.genre.toString();
            try { return Track.remote(item.mediaId, title, artist, album, uri.toString(), art, duration, genre); } catch (IllegalArgumentException ignored) {}
        }
        return new Track(item.mediaId, title, artist, album, uri, duration);
    }

    @Override public List<Track> queue() {
        MediaController c = controller;
        if (c == null) return Collections.emptyList();
        if (queueCache == null) {
            int n = c.getMediaItemCount();
            List<Track> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(toTrack(c.getMediaItemAt(i)));
            queueCache = Collections.unmodifiableList(out);
        }
        return queueCache;
    }

    @Override public int index() { return controller == null ? -1 : controller.getCurrentMediaItemIndex(); }
    @Override public boolean playing() { return controller != null && controller.isPlaying(); }
    @Override public boolean buffering() { return controller != null && controller.getPlaybackState() == Player.STATE_BUFFERING && controller.getPlayWhenReady(); }
    @Override public boolean waitingForNetwork() { return waitingForNetwork; }
    @Override public long position() { return controller == null ? 0 : Math.max(0, controller.getCurrentPosition()); }

    @Override public long duration() {
        if (controller == null) return 0;
        long d = controller.getDuration();
        if (d == C.TIME_UNSET || d <= 0) { Track t = current(); return t == null ? 0 : Math.max(0, t.duration); }
        return d;
    }

    // ------------------------------------------------------------------ queue

    @Override public void play(List<Track> tracks, int start, boolean shuffle) {
        if (tracks == null || tracks.isEmpty()) return;
        int s = Math.max(0, Math.min(start, tracks.size() - 1));
        List<Track> list;
        if (shuffle) {
            // Shuffle here with the chosen song first: a player-side shuffle order would start at a
            // random position of the permutation and skip the songs before it.
            List<Track> rest = new ArrayList<>(tracks);
            Track first = rest.remove(s);
            Collections.shuffle(rest);
            list = new ArrayList<>();
            list.add(first);
            list.addAll(rest);
            if (list.size() > MAX_QUEUE) list = new ArrayList<>(list.subList(0, MAX_QUEUE));
            s = 0;
        } else {
            int from = Math.max(0, s - 200), to = Math.min(tracks.size(), from + MAX_QUEUE);
            list = new ArrayList<>(tracks.subList(from, to));
            s -= from;
        }
        final int startIndex = s;
        withItems(list, (c, items) -> {
            c.setShuffleModeEnabled(false);
            c.setMediaItems(items, startIndex, 0);
            c.prepare();
            c.play();
        });
    }

    @Override public void playNext(Track track) {
        if (track == null) return;
        withItems(Collections.singletonList(track), (c, items) -> {
            if (c.getMediaItemCount() == 0) { c.setMediaItems(items); c.prepare(); c.play(); return; }
            int at = c.getCurrentMediaItemIndex() + 1;
            c.addMediaItem(at, items.get(0));
            if (c.getShuffleModeEnabled()) {
                Bundle args = new Bundle();
                args.putInt("index", at);
                c.sendCustomCommand(PlaybackService.QUEUE_PLAY_NEXT, args);
            }
        });
    }

    @Override public void addToQueue(Track track) {
        if (track == null) return;
        addAllToQueue(Collections.singletonList(track));
    }

    /** Appends several songs (collection "Add to Queue"). */
    public void addAllToQueue(List<Track> tracks) {
        withItems(tracks, (c, items) -> {
            boolean empty = c.getMediaItemCount() == 0;
            c.addMediaItems(items);
            if (empty) { c.prepare(); c.play(); }
        });
    }

    /** Inserts several songs after the current one, in order. */
    public void playAllNext(List<Track> tracks) {
        withItems(tracks, (c, items) -> {
            if (c.getMediaItemCount() == 0) { c.setMediaItems(items); c.prepare(); c.play(); return; }
            int at = c.getCurrentMediaItemIndex() + 1;
            c.addMediaItems(at, items);
            if (c.getShuffleModeEnabled()) {
                for (int i = items.size() - 1; i >= 0; i--) {
                    Bundle args = new Bundle();
                    args.putInt("index", at + i);
                    c.sendCustomCommand(PlaybackService.QUEUE_PLAY_NEXT, args);
                }
            }
        });
    }

    @Override public void toggle() {
        whenConnected(() -> {
            MediaController c = controller;
            if (c.getMediaItemCount() == 0) {
                List<Track> all = library.allTracks();
                if (!all.isEmpty()) play(all, 0, false);
                return;
            }
            if (c.isPlaying()) { c.pause(); return; }
            if (c.getPlaybackState() == Player.STATE_ENDED) c.seekToDefaultPosition();
            if (c.getPlaybackState() == Player.STATE_IDLE) c.prepare();
            c.play();
        });
    }

    @Override public void next() { whenConnected(() -> controller.seekToNext()); }
    /** Restarts the song after 3 s, otherwise goes back (same as the notification and widget). */
    @Override public void previous() { whenConnected(() -> controller.seekToPrevious()); }
    @Override public void seek(long positionMs) { whenConnected(() -> controller.seekTo(Math.max(0, positionMs))); }

    @Override public void moveQueueItem(int from, int to) {
        whenConnected(() -> {
            int n = controller.getMediaItemCount();
            if (from >= 0 && from < n && to >= 0 && to < n && from != to) controller.moveMediaItem(from, to);
        });
    }

    @Override public void removeQueueItem(int index) {
        whenConnected(() -> { if (index >= 0 && index < controller.getMediaItemCount()) controller.removeMediaItem(index); });
    }

    @Override public void playQueueIndex(int index) {
        whenConnected(() -> {
            if (index < 0 || index >= controller.getMediaItemCount()) return;
            controller.seekToDefaultPosition(index);
            if (controller.getPlaybackState() == Player.STATE_IDLE) controller.prepare();
            controller.play();
        });
    }

    @Override public boolean shuffle() { return controller != null && controller.getShuffleModeEnabled(); }
    @Override public void setShuffle(boolean on) { whenConnected(() -> controller.setShuffleModeEnabled(on)); }
    @Override public int repeatMode() { return controller == null ? Player.REPEAT_MODE_OFF : controller.getRepeatMode(); }
    @Override public void setRepeatMode(int mode) { whenConnected(() -> controller.setRepeatMode(mode)); }

    // ------------------------------------------------------------------ session commands

    private void command(SessionCommand command, Bundle args, Callback<Bundle> result) {
        whenConnected(() -> {
            ListenableFuture<SessionResult> f = controller.sendCustomCommand(command, args == null ? Bundle.EMPTY : args);
            if (result == null) return;
            f.addListener(() -> {
                Bundle extras = Bundle.EMPTY;
                try {
                    SessionResult r = f.get();
                    if (r.resultCode == SessionResult.RESULT_SUCCESS && r.extras != null) extras = r.extras;
                } catch (Exception ignored) {}
                if (!released) result.accept(extras);
            }, main::post);
        });
    }

    @Override public void audioEffects(Callback<Bundle> result) { command(PlaybackService.GET_AUDIO_EFFECTS, null, result); }
    @Override public void setAudioEffects(Bundle effects) { command(PlaybackService.SET_AUDIO_EFFECTS, effects, null); }

    @Override public void setSleepTimer(int minutes) {
        Bundle args = new Bundle();
        args.putInt("minutes", Math.max(0, Math.min(PlaybackService.MAX_SLEEP_MINUTES, minutes)));
        command(PlaybackService.SET_SLEEP_TIMER, args, null);
    }

    /** Pause when the current song ends. */
    public void setSleepAtEndOfTrack() {
        Bundle args = new Bundle();
        args.putBoolean("endOfTrack", true);
        command(PlaybackService.SET_SLEEP_TIMER, args, null);
    }

    @Override public void sleepTimerRemaining(Callback<Long> result) {
        command(PlaybackService.GET_SLEEP_TIMER, null, b -> result.accept(b.getLong("remainingMillis", 0)));
    }

    @Override public void playbackOptions(Callback<Bundle> result) { command(PlaybackService.GET_PLAYBACK_OPTIONS, null, result); }
    @Override public void setPlaybackOptions(Bundle options) { command(PlaybackService.SET_PLAYBACK_OPTIONS, options, null); }

    // ------------------------------------------------------------------ listeners

    @Override public void addListener(Listener listener) { if (listener != null && !listeners.contains(listener)) listeners.add(listener); }
    @Override public void removeListener(Listener listener) { listeners.remove(listener); }

    private void notifyChanged() {
        if (notifyPosted || released) return;
        notifyPosted = true;
        main.post(() -> {
            notifyPosted = false;
            if (released) return;
            for (Listener l : listeners) l.onPlaybackChanged();
        });
    }

    private void trackStarted() {
        MediaController c = controller;
        if (c == null || !c.isPlaying()) return;
        MediaItem item = c.getCurrentMediaItem();
        if (item == null || item.mediaId.equals(lastStartedId)) return;
        lastStartedId = item.mediaId;
        events.onTrackStarted(toTrack(item));
        if (!startedOnce) { startedOnce = true; events.onFirstPlayback(); }
    }
}
