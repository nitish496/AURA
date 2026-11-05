package dev.lilt.player.screens.player;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Playing Next" list as positions in the player's queue (timeline indices), in the order the
 * songs will actually play: after the current item in queue order, or in the shuffled order
 * when shuffle is on. Reordering is only offered in queue order (a shuffled order is the
 * player's own and moving an item would re-randomise it). Pure Java; tests/player/QueueModelTest.
 */
public final class QueueModel {
    private final int[] upcoming;
    public final boolean shuffled;

    private QueueModel(int[] upcoming, boolean shuffled) { this.upcoming = upcoming; this.shuffled = shuffled; }

    /**
     * @param size     queue length
     * @param current  index of the playing item (-1 for none)
     * @param shuffleOrder full play order when shuffle is on (each queue index once), else null
     */
    public static QueueModel of(int size, int current, int[] shuffleOrder) {
        if (size <= 0) return new QueueModel(new int[0], shuffleOrder != null);
        int cur = Math.max(-1, Math.min(size - 1, current));
        if (shuffleOrder != null && shuffleOrder.length == size) {
            int at = -1;
            for (int i = 0; i < shuffleOrder.length; i++) if (shuffleOrder[i] == cur) { at = i; break; }
            List<Integer> out = new ArrayList<>();
            for (int i = at + 1; i < shuffleOrder.length; i++) {
                int idx = shuffleOrder[i];
                if (idx >= 0 && idx < size && idx != cur) out.add(idx);
            }
            int[] a = new int[out.size()];
            for (int i = 0; i < a.length; i++) a[i] = out.get(i);
            return new QueueModel(a, true);
        }
        int n = Math.max(0, size - cur - 1);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = cur + 1 + i;
        return new QueueModel(a, false);
    }

    public int size() { return upcoming.length; }

    /** Queue (timeline) index of row {@code position}. */
    public int queueIndex(int position) { return upcoming[position]; }

    public boolean canReorder() { return !shuffled && upcoming.length > 1; }

    /**
     * Player move for dragging row {@code from} to row {@code to} (queue order only): returns
     * {fromIndex, toIndex} for {@code moveQueueItem}, or null when nothing moves.
     */
    public int[] move(int from, int to) {
        if (shuffled || from == to || from < 0 || to < 0 || from >= upcoming.length || to >= upcoming.length) return null;
        return new int[] {upcoming[from], upcoming[to]};
    }

    /** Applies a row move to a list in place (the optimistic UI copy). */
    public static <T> void moveInList(List<T> list, int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= list.size() || to >= list.size()) return;
        list.add(to, list.remove(from));
    }
}
