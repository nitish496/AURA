package dev.lilt.player.audio;

/**
 * Tracks one listening session (one song from start to transition) and decides when it counts as
 * a play: after 30 seconds of listening or half of the song, whichever comes first. Listening time
 * is content time, so it follows the playback speed. Time comes from the caller (elapsed-realtime
 * milliseconds) so the rule is deterministic in tests.
 */
public final class PlayCounter {
    public static final long PLAY_THRESHOLD_MS = 30_000;

    private String id = "";
    private long durationMs, listenedMs, undrainedMs, playingSince = -1;
    private float speed = 1f;
    private boolean counted;

    /** Whether a session of this length counts as a play. */
    public static boolean qualifies(long listenedMs, long durationMs) {
        if (listenedMs <= 0) return false;
        return listenedMs >= PLAY_THRESHOLD_MS || (durationMs > 0 && listenedMs * 2 >= durationMs);
    }

    /** Starts a new session; the previous one must have been ended by the caller. */
    public void begin(String mediaId, long durationMs, boolean playing, float speed, long now) {
        id = mediaId == null ? "" : mediaId;
        this.durationMs = durationMs > 0 ? durationMs : 0;
        listenedMs = undrainedMs = 0;
        counted = false;
        this.speed = speed > 0 ? speed : 1f;
        playingSince = playing ? now : -1;
    }

    public String id() { return id; }
    public boolean active() { return !id.isEmpty(); }
    public boolean counted() { return counted; }

    /** Durations of streams are often only known once playback has started. */
    public void setDuration(long durationMs) { if (durationMs > 0) this.durationMs = durationMs; }

    public void setPlaying(boolean playing, long now) {
        if (playing && playingSince < 0) playingSince = now;
        else if (!playing && playingSince >= 0) { accumulate(now); playingSince = -1; }
    }

    public void setSpeed(float speed, long now) {
        if (playingSince >= 0) { accumulate(now); playingSince = now; }
        this.speed = speed > 0 ? speed : 1f;
    }

    public long listenedMs(long now) {
        if (playingSince < 0) return listenedMs;
        return listenedMs + Math.max(0, Math.round((now - playingSince) * (double) speed));
    }

    /** True exactly once per session, when the play threshold is crossed. */
    public boolean shouldCount(long now) {
        if (counted || id.isEmpty() || !qualifies(listenedMs(now), durationMs)) return false;
        counted = true;
        return true;
    }

    /** Listening time accumulated since the previous drain, for incremental persistence. */
    public long drainListened(long now) {
        if (playingSince >= 0) { accumulate(now); playingSince = now; }
        long result = undrainedMs;
        undrainedMs = 0;
        return result;
    }

    /** Ends the session and returns its undrained listening time; the counter becomes inactive. */
    public long end(long now) {
        long rest = drainListened(now);
        id = "";
        playingSince = -1;
        return rest;
    }

    private void accumulate(long now) {
        long delta = Math.max(0, Math.round((now - playingSince) * (double) speed));
        listenedMs += delta;
        undrainedMs += delta;
    }
}
