package dev.lilt.player.audio;

import android.os.SystemClock;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ShuffleOrder;

/** Reads and writes a player's shuffle play order (so it survives crossfades and restarts). */
@OptIn(markerClass = UnstableApi.class)
public final class QueueOrders {
    private QueueOrders() {}

    /** The shuffled play order of the player's queue, or null if it cannot be read. */
    public static int[] shuffleOrder(Player player) {
        Timeline timeline = player.getCurrentTimeline();
        int count = timeline.getWindowCount();
        if (count == 0) return null;
        int[] order = new int[count];
        int index = timeline.getFirstWindowIndex(true), k = 0;
        while (index != C.INDEX_UNSET && k < count) {
            order[k++] = index;
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true);
        }
        return k == count ? order : null;
    }

    /** Applies a play order of the right length; ignores anything else. */
    public static boolean apply(ExoPlayer player, int[] order) {
        if (order == null || order.length != player.getMediaItemCount() || order.length == 0) return false;
        boolean[] seen = new boolean[order.length];
        for (int value : order) { if (value < 0 || value >= order.length || seen[value]) return false; seen[value] = true; }
        player.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(order.clone(), SystemClock.elapsedRealtime()));
        return true;
    }
}
