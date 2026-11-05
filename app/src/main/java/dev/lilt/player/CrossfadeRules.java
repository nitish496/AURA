package dev.lilt.player;

/** Pure timing and gain rules for two-player transitions. */
final class CrossfadeRules {
    /** Lead time before the fade window at which the next song is prepared on the spare player. */
    static final long LOCAL_STAGE_LEAD_MS = 3000, REMOTE_STAGE_LEAD_MS = 10_000;
    /** How long the spare player may take to become ready before the transition falls back to a cut. */
    static final long LOCAL_PREPARE_TIMEOUT_MS = 6000, REMOTE_PREPARE_TIMEOUT_MS = 12_000;

    static boolean validSeconds(int seconds) { return seconds >= 0 && seconds <= 12; }
    static long durationMillis(int seconds, long outgoingDuration, long incomingDuration) {
        if (!validSeconds(seconds) || seconds == 0 || outgoingDuration <= 0 || incomingDuration <= 0) return 0;
        // Short clips must still have an audible solo section.
        return Math.min(seconds * 1000L, Math.min(outgoingDuration / 2, incomingDuration / 2));
    }
    static long safeFadeMillis(int seconds,long outgoingDuration,long incomingDuration,long remaining) {
        // Leave time for the main looper to promote before ExoPlayer advances naturally.
        return Math.min(durationMillis(seconds,outgoingDuration,incomingDuration),Math.max(0,remaining-350));
    }
    static float incomingGain(long elapsed, long duration) {
        return duration <= 0 ? 1f : Math.max(0f, Math.min(1f, elapsed / (float) duration));
    }
    static float outgoingGain(long elapsed, long duration) { return 1f - incomingGain(elapsed, duration); }

    /** Streams need longer to buffer the incoming song than files on the phone. */
    static long stageLeadMillis(boolean remoteDestination) { return remoteDestination ? REMOTE_STAGE_LEAD_MS : LOCAL_STAGE_LEAD_MS; }
    static long prepareTimeoutMillis(boolean remoteDestination) { return remoteDestination ? REMOTE_PREPARE_TIMEOUT_MS : LOCAL_PREPARE_TIMEOUT_MS; }

    /** Whether to stage the next song now: inside the fade window plus the lead time. */
    static boolean shouldStage(int seconds, long duration, long position, boolean remoteDestination) {
        if (!validSeconds(seconds) || seconds == 0 || duration <= 0) return false;
        long window = Math.min(seconds * 1000L, duration / 2);
        return window >= 300 && duration - position <= window + stageLeadMillis(remoteDestination);
    }

    /** Next engine tick: fast near and during a transition, slow otherwise, none when idle. */
    static long tickDelayMillis(boolean overlapping, boolean playing, int seconds, long duration, long position) {
        if (overlapping) return 100;
        if (!playing || seconds == 0) return -1;
        if (duration <= 0) return 1000;
        long window = Math.min(seconds * 1000L, duration / 2);
        long untilStage = duration - position - window - REMOTE_STAGE_LEAD_MS;
        return untilStage > 1500 ? Math.min(untilStage - 1000, 5000) : 100;
    }

    /** Sleep-timer fade: full volume until the last fadeMs, then a linear ramp to silence. */
    static float sleepFadeGain(long remainingMs, long fadeMs) {
        if (fadeMs <= 0 || remainingMs >= fadeMs) return 1f;
        return Math.max(0f, Math.min(1f, remainingMs / (float) fadeMs));
    }
}
