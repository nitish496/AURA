package dev.lilt.player.screens.discover;

import android.widget.ImageView;
import dev.lilt.player.Track;
import dev.lilt.player.app.AppHost;
import dev.lilt.player.ui.PlaceholderDrawable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Artwork for discovery rows and cards through the shell's shared {@link dev.lilt.player.Artwork}
 * loader (memory + disk cache, sized variants, coalescing, recycled views drop stale results).
 * Playlist covers and artist pictures are plain URLs; they load through a stand-in remote track
 * so they share the same caches. Missing artwork gets the typographic graphite placeholder.
 * Main thread only.
 */
public final class Art {
    private Art() {}

    private static final Map<String, Track> STAND_INS = new LinkedHashMap<String, Track>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Track> eldest) { return size() > 300; }
    };

    public static void track(AppHost host, ImageView view, Track track) {
        if (track == null) { placeholder(view, "", false); return; }
        if (track.isRemote() && track.artworkUrl == null) { placeholder(view, track.title, false); return; }
        host.artwork().load(view, track);
    }

    /** {@code url} (https, Audius-sized variants honoured) or the placeholder for {@code label}. */
    public static void url(AppHost host, ImageView view, String key, String label, String url, boolean circle) {
        if (url == null || url.isEmpty()) { placeholder(view, label, circle); return; }
        String id = "audius:art-" + key;
        Track stand = STAND_INS.get(id);
        if (stand == null || !url.equals(stand.artworkUrl)) {
            stand = Track.remote(id, label, label, "", "https://audius.co/", url, 0, "");
            STAND_INS.put(id, stand);
        }
        host.artwork().load(view, stand);
    }

    /** Shows the placeholder and detaches the view from any pending load (it ignores the result). */
    public static void placeholder(ImageView view, String label, boolean circle) {
        view.setTag(null);
        view.setImageDrawable(new PlaceholderDrawable(view.getContext(), label == null ? "" : label, circle));
    }
}
