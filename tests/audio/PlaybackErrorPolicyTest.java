package dev.lilt.player.audio;

import dev.lilt.player.audio.PlaybackErrorPolicy.Action;

/** Stream retry/skip/offline-wait decisions and the device-file rule (never skip silently). */
public final class PlaybackErrorPolicyTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    static final int NETWORK = 2001, TIMEOUT = 2002, HTTP = 2004, IO = 2000, NOT_FOUND = 2005, PARSE = 3001, DECODER = 4001;

    public static void main(String[] args) {
        check(PlaybackErrorPolicy.decide(NETWORK, true, false, false, false, 0, true) == Action.IGNORE, "not playing: ignore");
        check(PlaybackErrorPolicy.decide(NETWORK, true, false, true, false, 0, true) == Action.WAIT_FOR_NETWORK, "offline stream waits");
        check(PlaybackErrorPolicy.decide(TIMEOUT, true, false, true, true, 2, true) == Action.WAIT_FOR_NETWORK, "offline waits even after a retry");
        check(PlaybackErrorPolicy.decide(NETWORK, true, true, true, false, 0, true) == Action.RETRY, "online: one retry");
        check(PlaybackErrorPolicy.decide(HTTP, true, true, true, false, 0, true) == Action.RETRY, "bad status retried (host failover)");
        check(PlaybackErrorPolicy.decide(NETWORK, true, true, true, true, 0, true) == Action.SKIP, "then skip");
        check(PlaybackErrorPolicy.decide(NETWORK, true, true, true, true, 0, false) == Action.PAUSE, "nothing to skip to");
        check(PlaybackErrorPolicy.decide(NETWORK, true, true, true, true, PlaybackErrorPolicy.MAX_CONSECUTIVE_SKIPS, true) == Action.PAUSE, "skip limit");
        check(PlaybackErrorPolicy.decide(PARSE, true, true, true, false, 0, true) == Action.SKIP, "broken stream skipped without retry");
        check(PlaybackErrorPolicy.decide(DECODER, true, false, true, false, 0, true) == Action.SKIP, "offline but not a network error");

        check(PlaybackErrorPolicy.decide(IO, false, true, true, false, 0, true) == Action.RETRY, "local I/O retried once");
        check(PlaybackErrorPolicy.decide(IO, false, true, true, true, 0, true) == Action.PAUSE, "then pause");
        check(PlaybackErrorPolicy.decide(NOT_FOUND, false, false, true, false, 0, true) == Action.PAUSE, "missing file pauses, never skips");
        check(PlaybackErrorPolicy.decide(DECODER, false, true, true, false, 0, true) == Action.PAUSE, "undecodable file pauses");

        check(PlaybackErrorPolicy.isNetworkError(NETWORK) && PlaybackErrorPolicy.isNetworkError(HTTP) && !PlaybackErrorPolicy.isNetworkError(DECODER), "network errors");
        String skip = PlaybackErrorPolicy.message(Action.SKIP, true, "Midnight", NETWORK);
        check(skip.contains("“Midnight”") && skip.contains("next"), "skip message names the song");
        check(PlaybackErrorPolicy.message(Action.WAIT_FOR_NETWORK, true, "x", NETWORK).contains("offline"), "offline message");
        check(PlaybackErrorPolicy.message(Action.PAUSE, false, null, NOT_FOUND).contains("this song"), "untitled song");
        check(PlaybackErrorPolicy.message(Action.PAUSE, true, "x", HTTP).contains("no longer be available"), "unavailable stream");
        check(PlaybackErrorPolicy.message(Action.RETRY, true, "x", NETWORK) == null && PlaybackErrorPolicy.message(Action.IGNORE, true, "x", 0) == null, "silent actions");
        System.out.println("PlaybackErrorPolicyTest: " + checks + " checks passed");
    }
}
