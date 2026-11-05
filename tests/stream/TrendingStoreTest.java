package dev.lilt.player.stream;

import dev.lilt.player.Track;
import java.util.ArrayList;
import java.util.List;

/** Trending persistence format, freshness window and failure back-off. */
public final class TrendingStoreTest {
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }

    public static void main(String[] args) {
        List<Track> in = new ArrayList<>();
        for (int i = 0; i < 20; i++) in.add(Track.remote("t" + i, "Title " + i, "Artist", "Album", "https://h/" + i, "https://a/" + i + "/1000x1000.jpg", 200_000, "Pop", "u" + i, "/a/s" + i));
        TrendingStore.Snapshot s = TrendingStore.decode(TrendingStore.encode(in, 1_700_000_000_000L));
        check(s.tracks.size() == 20 && s.fetchedAt == 1_700_000_000_000L, "round trip");
        check(s.tracks.get(3).id.equals("audius:t3") && s.tracks.get(3).artworkUrl != null && s.tracks.get(3).genre.equals("Pop"), "fields kept");
        check(TrendingStore.decode("not json").tracks.isEmpty(), "garbage");
        check(TrendingStore.decode("{\"v\":9,\"t\":5,\"tracks\":[]}").fetchedAt == 0, "version mismatch");
        check(TrendingStore.decode(null).tracks.isEmpty(), "null");

        long t = 1_000_000_000L;
        check(!TrendingStore.stale(t, t + 14 * 60_000L), "fresh under 15 min");
        check(TrendingStore.stale(t, t + 15 * 60_000L), "stale at 15 min");
        check(TrendingStore.stale(0, t), "nothing cached");
        check(TrendingStore.stale(t + 5, t), "clock went back");

        check(TrendingStore.backoffMs(0) == 0, "no failures no wait");
        check(TrendingStore.backoffMs(1) == 60_000L && TrendingStore.backoffMs(2) == 120_000L, "doubles");
        check(TrendingStore.backoffMs(50) == 15 * 60_000L, "capped");
        System.out.println("TrendingStoreTest ok");
    }
}
