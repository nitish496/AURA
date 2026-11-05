package dev.lilt.player.audio;

import java.util.Arrays;

/**
 * Decides what survives when a saved queue is restored. Entries can disappear between sessions (a
 * deleted file, a revoked media permission, a catalogue track whose metadata can no longer be
 * resolved). Dropped entries are skipped gracefully: the current song falls back to the next
 * playable song after it (or the closest one before it) and starts from the beginning, and the
 * saved shuffle order is remapped so "Up next" stays the same for everything that remains.
 */
public final class QueueRestorePlan {
    /** Original indices that are restored, in queue order. */
    public final int[] kept;
    /** Current item, as an index into {@link #kept}. 0 when nothing is kept. */
    public final int index;
    /** Start position in the current item; 0 when the saved current item was dropped. */
    public final long position;
    /** Shuffle play order over the kept items, or null when the saved order is unusable. */
    public final int[] shuffleOrder;

    private QueueRestorePlan(int[] kept, int index, long position, int[] shuffleOrder) {
        this.kept = kept; this.index = index; this.position = position; this.shuffleOrder = shuffleOrder;
    }

    public boolean isEmpty() { return kept.length == 0; }

    public static QueueRestorePlan plan(boolean[] keep, int savedIndex, long savedPosition, int[] savedShuffleOrder) {
        int n = keep == null ? 0 : keep.length;
        int[] newIndex = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) newIndex[i] = keep[i] ? count++ : -1;
        int[] kept = new int[count];
        for (int i = 0, k = 0; i < n; i++) if (keep[i]) kept[k++] = i;
        if (count == 0) return new QueueRestorePlan(kept, 0, 0, null);
        int saved = Math.max(0, Math.min(savedIndex, n - 1));
        int index;
        long position;
        if (keep[saved]) {
            index = newIndex[saved];
            position = Math.max(0, savedPosition);
        } else {
            int choice = -1;
            for (int i = saved + 1; i < n && choice < 0; i++) if (keep[i]) choice = newIndex[i];
            for (int i = saved - 1; i >= 0 && choice < 0; i--) if (keep[i]) choice = newIndex[i];
            index = Math.max(0, choice);
            position = 0;
        }
        return new QueueRestorePlan(kept, index, position, remapOrder(savedShuffleOrder, newIndex, count));
    }

    /** Removes dropped entries from a saved permutation and renumbers the rest; null if invalid. */
    static int[] remapOrder(int[] order, int[] newIndex, int count) {
        if (order == null || order.length != newIndex.length) return null;
        boolean[] seen = new boolean[order.length];
        for (int value : order) {
            if (value < 0 || value >= order.length || seen[value]) return null;
            seen[value] = true;
        }
        int[] result = new int[count];
        int k = 0;
        for (int value : order) if (newIndex[value] >= 0) result[k++] = newIndex[value];
        return k == count ? result : null;
    }

    /** Parses "3,0,2,1"; returns null for empty or malformed input. */
    public static int[] parseOrder(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] parts = text.split(",");
        int[] order = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) order[i] = Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException malformed) {
            return null;
        }
        return order;
    }

    public static String formatOrder(int[] order) {
        if (order == null || order.length == 0) return "";
        StringBuilder text = new StringBuilder(order.length * 4);
        for (int i = 0; i < order.length; i++) { if (i > 0) text.append(','); text.append(order[i]); }
        return text.toString();
    }

    /**
     * Returns a play order in which {@code target} plays immediately after {@code current}. Used for
     * "Play next" while shuffle is on, where a plain insert would land at a random shuffled spot.
     */
    public static int[] playNext(int[] order, int current, int target) {
        if (order == null) return null;
        int[] copy = Arrays.copyOf(order, order.length);
        if (current == target) return copy;
        int targetAt = -1, currentAt = -1;
        for (int i = 0; i < copy.length; i++) { if (copy[i] == target) targetAt = i; if (copy[i] == current) currentAt = i; }
        if (targetAt < 0 || currentAt < 0) return copy;
        int[] result = new int[copy.length];
        int k = 0;
        for (int i = 0; i < copy.length; i++) {
            if (i == targetAt) continue;
            result[k++] = copy[i];
            if (i == currentAt) result[k++] = target;
        }
        return result;
    }
}
