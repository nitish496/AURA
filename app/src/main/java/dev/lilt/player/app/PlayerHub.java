package dev.lilt.player.app;

import android.os.Bundle;
import dev.lilt.player.Track;
import java.util.List;

/**
 * UI-facing playback API wrapping the Media3 MediaController connected to PlaybackService.
 * Main thread only. Safe to call before the controller connects (calls are queued or ignored).
 */
public interface PlayerHub {
    interface Listener { void onPlaybackChanged(); }

    boolean connected();
    Track current();
    List<Track> queue();
    int index();
    boolean playing();
    boolean buffering();
    long position();
    long duration();

    /** Replace the queue and start at {@code start}; shuffle=true shuffles the rest. */
    void play(List<Track> tracks, int start, boolean shuffle);
    void playNext(Track track);
    void addToQueue(Track track);
    void toggle();
    void next();
    void previous();
    void seek(long positionMs);
    void moveQueueItem(int from, int to);
    void removeQueueItem(int index);
    void playQueueIndex(int index);

    boolean shuffle();
    void setShuffle(boolean on);
    /** Player.REPEAT_MODE_OFF / ONE / ALL. */
    int repeatMode();
    void setRepeatMode(int mode);

    /** Audio effects Bundle (keys documented in docs/ARCHITECTURE.md). Callback on main thread. */
    void audioEffects(Callback<Bundle> result);
    void setAudioEffects(Bundle effects);
    /** Sleep timer: minutes 0 cancels; remaining ms via callback (0 = off). */
    void setSleepTimer(int minutes);
    void sleepTimerRemaining(Callback<Long> result);
    /** Crossfade seconds (0 = gapless) and autoplay toggle. */
    void playbackOptions(Callback<Bundle> result);
    void setPlaybackOptions(Bundle options);

    void addListener(Listener listener);
    void removeListener(Listener listener);

    interface Callback<T> { void accept(T value); }
}
