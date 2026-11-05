package dev.lilt.player.audio;

import java.util.List;

/** Play counting (30 s or half the song) and the listening ledger behind ListeningStats. */
public final class ListeningStatsTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static final long DAY = ListeningLedger.DAY_MS;

    public static void main(String[] args) {
        counter();
        ledger();
        System.out.println("ListeningStatsTest: " + checks + " checks passed");
    }

    static void counter() {
        PlayCounter c = new PlayCounter();
        c.begin("a", 200_000, true, 1f, 0);
        check(c.active() && c.id().equals("a"), "session started");
        check(!c.shouldCount(29_999), "not yet at 29.999 s");
        check(c.shouldCount(30_000), "counted at 30 s");
        check(!c.shouldCount(90_000), "counted once");

        c.begin("short", 40_000, true, 1f, 0);
        check(!c.shouldCount(19_999) && c.shouldCount(20_000), "half of a 40 s song");

        c.begin("p", 300_000, true, 1f, 0);
        c.setPlaying(false, 10_000);
        c.setPlaying(true, 50_000);
        check(c.listenedMs(60_000) == 20_000 && !c.shouldCount(60_000), "paused time does not count");
        check(c.shouldCount(70_000), "counted after 30 s of listening");

        c.begin("fast", 300_000, true, 2f, 0);
        check(c.shouldCount(15_000), "2x speed: 15 s of wall time is 30 s of music");
        c.begin("speedchange", 300_000, true, 1f, 0);
        c.setSpeed(2f, 10_000);
        check(c.listenedMs(20_000) == 30_000, "speed change mid-song");

        c.begin("stream", 0, true, 1f, 0);
        check(!c.shouldCount(20_000), "unknown duration: only the 30 s rule");
        c.setDuration(30_000);
        check(c.shouldCount(20_000), "duration learned later");

        c.begin("d", 200_000, true, 1f, 0);
        check(c.drainListened(5_000) == 5_000 && c.drainListened(8_000) == 3_000, "incremental drains");
        c.setPlaying(false, 9_000);
        check(c.end(12_000) == 1_000 && !c.active(), "end returns the rest");
        check(!c.shouldCount(100_000), "inactive counter never counts");
        check(!PlayCounter.qualifies(0, 0) && PlayCounter.qualifies(30_000, 0) && PlayCounter.qualifies(5_000, 10_000), "qualifies");
    }

    static void ledger() {
        ListeningLedger l = new ListeningLedger();
        long t0 = 10 * DAY;
        l.recordPlay("s1", "Song One", "Artist A", "Album", t0);
        l.recordPlay("s1", "Song One", "Artist A", "Album", t0 + 1000);
        l.addListened("s1", "Song One", "Artist A", "Album", 400_000, t0 + 1000);
        l.recordPlay("s2", "Song Two", "artist a ", "Album", t0 + 2000);
        l.addListened("s2", "Song Two", "artist a ", "Album", 200_000, t0 + 2000);
        l.recordPlay("s3", "Tab\tand\nnewline", "Artist B", "", t0 + 3000);
        l.addListened("s3", "Tab\tand\nnewline", "Artist B", "", 300_000, t0 + 3000);
        l.recordPlay("u", "Untagged", "Unknown artist", "", t0 + 4000);
        l.addListened("skipped", "Skipped", "Artist C", "", 10_000, t0 + 5000); // listened, never counted

        List<ListeningLedger.TrackStat> top = l.topTracks(10, 0);
        check(top.size() == 4 && top.get(0).id.equals("s1") && top.get(0).plays == 2, "most played first");
        check(top.get(1).id.equals("s3") && top.get(2).id.equals("s2"), "ties broken by listening time");
        check(l.topTracks(1, 0).size() == 1 && l.topTracks(0, 0).isEmpty(), "limit respected");
        ListeningLedger.TrackStat s1 = l.track("s1");
        check(s1.plays == 2 && s1.msListened == 400_000 && s1.lastPlayedAt == t0 + 1000 && s1.title.equals("Song One"), "per-track figures");
        check(l.track("skipped").plays == 0 && l.track("nobody") == null, "listened-only and unknown tracks");

        List<ListeningLedger.ArtistStat> artists = l.topArtists(10, 0);
        check(artists.size() == 2 && artists.get(0).plays == 3 && artists.get(0).tracks == 2, "artist names merged case-insensitively");
        check(artists.get(0).name.equals("artist a "), "latest spelling shown");
        check(artists.get(1).name.equals("Artist B"), "unknown artist excluded, unplayed artist excluded");
        check(l.totalMinutes(0) == (400_000 + 200_000 + 300_000 + 10_000) / 60_000, "total minutes");

        // Periods: plays and minutes from day buckets.
        l.recordPlay("s2", "Song Two", "Artist A", "Album", t0 + 5 * DAY);
        l.addListened("s2", "Song Two", "Artist A", "Album", 180_000, t0 + 5 * DAY);
        List<ListeningLedger.TrackStat> week = l.topTracks(10, t0 + 3 * DAY);
        check(week.size() == 1 && week.get(0).id.equals("s2") && week.get(0).plays == 1 && week.get(0).msListened == 180_000, "since filters older days");
        check(l.totalMinutes(t0 + 3 * DAY) == 3 && l.topArtists(5, t0 + 3 * DAY).get(0).plays == 1, "period minutes and artists");
        check(l.recent(2).get(0).id.equals("s2") && l.recent(2).size() == 2, "recent newest first");

        // Persistence round trip, including tabs and newlines in titles.
        ListeningLedger back = ListeningLedger.decode(l.encode());
        check(back.track("s3").title.equals("Tab\tand\nnewline"), "escaped text survives");
        check(back.topTracks(10, 0).size() == l.topTracks(10, 0).size() && back.track("s1").msListened == 400_000, "totals survive");
        check(back.topTracks(10, t0 + 3 * DAY).get(0).msListened == 180_000, "day buckets survive");
        check(ListeningLedger.decode("nonsense").isEmpty() && ListeningLedger.decode(null).isEmpty(), "unknown data is ignored");
        check(ListeningLedger.decode("L1\nT\tbroken\nD\t1\t99\t1\t1\n").isEmpty(), "malformed lines skipped");

        // Bounded: the least recently touched track is evicted.
        ListeningLedger big = new ListeningLedger();
        for (int i = 0; i <= ListeningLedger.MAX_TRACKS; i++) big.recordPlay("t" + i, "T", "A", "", 1000L + i);
        check(big.track("t0") == null && big.track("t1") != null && big.track("t" + ListeningLedger.MAX_TRACKS) != null, "oldest track evicted");
        big.clear();
        check(big.isEmpty() && big.totalMinutes(0) == 0, "clear");
    }
}
