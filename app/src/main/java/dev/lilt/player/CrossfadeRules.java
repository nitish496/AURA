package dev.lilt.player;

/** Pure timing and gain rules for local two-player transitions. */
final class CrossfadeRules {
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
}
