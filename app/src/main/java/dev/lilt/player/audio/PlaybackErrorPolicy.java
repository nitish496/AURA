package dev.lilt.player.audio;

/**
 * Pure decision table for playback errors (Media3 PlaybackException codes, duplicated as plain
 * ints so this class stays testable without Android).
 *
 * <ul>
 *   <li>Errors while the user has not asked to play (for example a queue restored at start-up)
 *       are ignored: pressing play prepares the item again.
 *   <li>Streams: when the phone is offline playback pauses and resumes automatically once the
 *       network returns. Online, a stream gets one retry from the same position and is then
 *       skipped, at most {@link #MAX_CONSECUTIVE_SKIPS} times in a row before playback pauses.
 *   <li>Device files keep AURA's established rule: one retry for unspecified I/O, otherwise pause
 *       with a message, never silently skipping the user's own queue.
 * </ul>
 */
public final class PlaybackErrorPolicy {
    public enum Action { IGNORE, RETRY, WAIT_FOR_NETWORK, SKIP, PAUSE }

    public static final int MAX_CONSECUTIVE_SKIPS = 3;
    public static final long RETRY_DELAY_MS = 1500;

    // androidx.media3.common.PlaybackException codes.
    static final int TIMEOUT = 1003;
    static final int IO_UNSPECIFIED = 2000, IO_NETWORK_CONNECTION_FAILED = 2001, IO_NETWORK_CONNECTION_TIMEOUT = 2002,
            IO_INVALID_HTTP_CONTENT_TYPE = 2003, IO_BAD_HTTP_STATUS = 2004, IO_FILE_NOT_FOUND = 2005,
            IO_NO_PERMISSION = 2006, IO_READ_POSITION_OUT_OF_RANGE = 2008;

    private PlaybackErrorPolicy() {}

    /** Errors that a working connection can fix. */
    public static boolean isNetworkError(int code) {
        return code == IO_UNSPECIFIED || code == IO_NETWORK_CONNECTION_FAILED || code == IO_NETWORK_CONNECTION_TIMEOUT
                || code == IO_BAD_HTTP_STATUS || code == IO_INVALID_HTTP_CONTENT_TYPE
                || code == IO_READ_POSITION_OUT_OF_RANGE || code == TIMEOUT;
    }

    public static Action decide(int code, boolean remote, boolean online, boolean wantedPlaying,
                                boolean alreadyRetried, int consecutiveSkips, boolean hasNext) {
        if (!wantedPlaying) return Action.IGNORE;
        if (remote) {
            if (!online && isNetworkError(code)) return Action.WAIT_FOR_NETWORK;
            if (!alreadyRetried && isNetworkError(code)) return Action.RETRY;
            return hasNext && consecutiveSkips < MAX_CONSECUTIVE_SKIPS ? Action.SKIP : Action.PAUSE;
        }
        if (code == IO_UNSPECIFIED && !alreadyRetried) return Action.RETRY;
        return Action.PAUSE;
    }

    /** User-facing text for an action; null when nothing should be shown. */
    public static String message(Action action, boolean remote, String title, int code) {
        String name = title == null || title.trim().isEmpty() ? "this song" : "“" + title.trim() + "”";
        switch (action) {
            case WAIT_FOR_NETWORK: return "You're offline. Playback will continue when you're connected.";
            case SKIP: return "Couldn't stream " + name + ". Playing the next song.";
            case PAUSE:
                if (remote) return code == IO_BAD_HTTP_STATUS || code == IO_FILE_NOT_FOUND
                        ? "Couldn't stream " + name + ". It may no longer be available."
                        : "Couldn't stream " + name + ". Check your connection and try again.";
                if (code == IO_FILE_NOT_FOUND || code == IO_NO_PERMISSION)
                    return "Couldn't open " + name + ". The file may have moved or been deleted.";
                return "Couldn't play " + name + ". The file may be damaged or use an unsupported format.";
            default: return null;
        }
    }
}
