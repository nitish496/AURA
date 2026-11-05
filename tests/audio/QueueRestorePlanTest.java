package dev.lilt.player.audio;

import dev.lilt.player.QueueSnapshot;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saved queues that mix device files and catalogue streams: the snapshot keeps remote entries
 * (https stream + artwork), restore drops what cannot be resolved without losing the place, and
 * the shuffled play order is remapped. Mirrors the keep rules of PlaybackStore.load().
 */
public final class QueueRestorePlanTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }

    static boolean[] keep(List<QueueSnapshot.Entry> entries, boolean mediaPermission, Set<String> remoteStore) {
        boolean[] keep = new boolean[entries.size()];
        for (int i = 0; i < keep.length; i++) {
            QueueSnapshot.Entry e = entries.get(i);
            if (MediaIds.isRemote(e.id, e.uri)) keep[i] = remoteStore.contains(e.id) || MediaIds.isHttp(e.uri);
            else keep[i] = mediaPermission && MediaIds.isLocalMedia(e.uri);
        }
        return keep;
    }

    public static void main(String[] args) throws Exception {
        List<QueueSnapshot.Entry> entries = Arrays.asList(
                new QueueSnapshot.Entry("content://media/external/audio/media/1", "content://media/external/audio/media/1", "Local One", "A", "Al", ""),
                new QueueSnapshot.Entry("audius:D7KyD", "https://api.audius.co/v1/tracks/D7KyD/stream?app_name=AURA", "Stream One", "B", "Single", "https://img.example/1000x1000.jpg"),
                new QueueSnapshot.Entry("audius:gone", "", "Lost Stream", "C", "Single", ""),
                new QueueSnapshot.Entry("audius:Q9", "https://api.audius.co/v1/tracks/Q9/stream", "Stream Two", "D", "Single", ""),
                new QueueSnapshot.Entry("file:///sdcard/x.mp3", "file:///sdcard/x.mp3", "Unsupported", "E", "", ""));
        QueueSnapshot saved = QueueSnapshot.decode(new QueueSnapshot(entries, 2, 45_000, true, 2).encode());
        QueueSnapshot.Entry stream = saved.entries.get(1);
        check(stream.id.equals("audius:D7KyD") && stream.uri.startsWith("https://") && stream.artwork.startsWith("https://"), "remote entry round trip");
        check(MediaIds.isRemote(stream.id, stream.uri) && MediaIds.isRemote("audius:x", null) && MediaIds.isRemote("x", "HTTPS://h/a"), "remote detection");
        check(!MediaIds.isRemote(saved.entries.get(0).id, saved.entries.get(0).uri) && MediaIds.isLocalMedia(saved.entries.get(0).uri), "local detection");

        Set<String> store = new HashSet<>();   // "audius:gone" is unknown and has no stream URL
        boolean[] keep = keep(saved.entries, true, store);
        check(Arrays.equals(keep, new boolean[]{true, true, false, true, false}), "unresolvable entries dropped");

        // The saved current song (index 2) was dropped: continue with the next playable one, from 0.
        QueueRestorePlan plan = QueueRestorePlan.plan(keep, saved.index, saved.position, new int[]{3, 0, 2, 1, 4});
        check(Arrays.equals(plan.kept, new int[]{0, 1, 3}) && plan.index == 2 && plan.position == 0, "dropped current moves forward");
        check(Arrays.equals(plan.shuffleOrder, new int[]{2, 0, 1}), "shuffle order remapped: " + Arrays.toString(plan.shuffleOrder));

        // Current song kept: same song, same position.
        QueueRestorePlan same = QueueRestorePlan.plan(keep, 3, 45_000, null);
        check(same.index == 2 && same.position == 45_000 && same.shuffleOrder == null, "kept current keeps its place");
        // Dropped last item: fall back to the closest earlier one.
        QueueRestorePlan back = QueueRestorePlan.plan(new boolean[]{true, true, false}, 2, 9_000, null);
        check(back.index == 1 && back.position == 0, "dropped last item falls back");
        // Without the media permission only streams survive.
        QueueRestorePlan streamsOnly = QueueRestorePlan.plan(keep(saved.entries, false, store), 0, 10_000, null);
        check(Arrays.equals(streamsOnly.kept, new int[]{1, 3}) && streamsOnly.index == 0 && streamsOnly.position == 0, "no permission: streams only");
        // A remote id known to the RemoteTrackStore resolves even without a usable snapshot URL.
        store.add("audius:gone");
        check(keep(saved.entries, true, store)[2], "remote store resolves the id");
        check(QueueRestorePlan.plan(new boolean[]{false, false}, 0, 5, null).isEmpty(), "nothing playable");
        check(QueueRestorePlan.plan(new boolean[0], 0, 0, null).isEmpty(), "empty queue");
        check(QueueRestorePlan.plan(new boolean[]{true, true}, 7, 5, null).index == 1, "out-of-range index clamped");

        // Shuffle orders: invalid permutations are discarded, not trusted.
        check(QueueRestorePlan.plan(keep, 0, 0, new int[]{0, 0, 1, 2, 3}).shuffleOrder == null, "duplicate in order");
        check(QueueRestorePlan.plan(keep, 0, 0, new int[]{0, 1}).shuffleOrder == null, "wrong length");
        check(QueueRestorePlan.plan(keep, 0, 0, new int[]{0, 1, 2, 3, 9}).shuffleOrder == null, "out of range");
        check(Arrays.equals(QueueRestorePlan.parseOrder(QueueRestorePlan.formatOrder(new int[]{4, 0, 3})), new int[]{4, 0, 3}), "order text round trip");
        check(QueueRestorePlan.parseOrder("1,x") == null && QueueRestorePlan.parseOrder("") == null && QueueRestorePlan.formatOrder(null).isEmpty(), "order text errors");

        // "Play next" while shuffled: the target moves right after the current item.
        check(Arrays.equals(QueueRestorePlan.playNext(new int[]{0, 3, 1, 2}, 3, 2), new int[]{0, 3, 2, 1}), "play next after current");
        check(Arrays.equals(QueueRestorePlan.playNext(new int[]{2, 0, 1}, 1, 2), new int[]{0, 1, 2}), "target earlier in the order");
        check(Arrays.equals(QueueRestorePlan.playNext(new int[]{1, 0}, 1, 1), new int[]{1, 0}), "target is current");
        check(Arrays.equals(QueueRestorePlan.playNext(new int[]{1, 0}, 1, 7), new int[]{1, 0}), "unknown target");
        System.out.println("QueueRestorePlanTest: " + checks + " checks passed");
    }
}
