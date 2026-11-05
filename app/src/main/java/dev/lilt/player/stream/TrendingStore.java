package dev.lilt.player.stream;

import android.content.Context;
import android.util.AtomicFile;
import dev.lilt.player.Track;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Last "Trending on Audius" result with its fetch time, so Home can show it at once on launch and
 * refresh in the background. Public catalogue data only. Blocking I/O: off the main thread.
 */
public final class TrendingStore {
    /** Cached list is considered fresh for this long. */
    public static final long FRESH_MS = 15 * 60_000L;
    private static final long BACKOFF_BASE_MS = 60_000L, BACKOFF_MAX_MS = 15 * 60_000L;
    private static final int VERSION = 1, MAX_TRACKS = 50;

    /** Cached tracks and when they were fetched (epoch ms); empty list / 0 when absent. */
    public static final class Snapshot {
        public final List<Track> tracks;
        public final long fetchedAt;
        Snapshot(List<Track> tracks, long fetchedAt) { this.tracks = tracks; this.fetchedAt = fetchedAt; }
    }

    private final AtomicFile file;

    public TrendingStore(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "trending.json")); }

    public Snapshot read() {
        try { return decode(new String(file.readFully(), StandardCharsets.UTF_8)); }
        catch (IOException | RuntimeException ignored) { return new Snapshot(new ArrayList<Track>(), 0); }
    }

    public void write(List<Track> tracks, long fetchedAt) {
        FileOutputStream stream = null;
        try {
            byte[] bytes = encode(tracks, fetchedAt).getBytes(StandardCharsets.UTF_8);
            stream = file.startWrite();
            stream.write(bytes);
            file.finishWrite(stream);
        } catch (IOException | RuntimeException failure) {
            if (stream != null) file.failWrite(stream);
        }
    }

    static String encode(List<Track> tracks, long fetchedAt) {
        try {
            JSONArray rows = new JSONArray();
            for (Track t : tracks) { if (t.isRemote()) rows.put(t.toJson()); if (rows.length() >= MAX_TRACKS) break; }
            return new JSONObject().put("v", VERSION).put("t", fetchedAt).put("tracks", rows).toString();
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Never throws; damaged input gives an empty snapshot. */
    static Snapshot decode(String json) {
        List<Track> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("v") != VERSION) return new Snapshot(out, 0);
            JSONArray rows = root.optJSONArray("tracks");
            if (rows == null) return new Snapshot(out, 0);
            for (int i = 0; i < rows.length(); i++) {
                Track t = Track.fromJson(rows.optJSONObject(i));
                if (t != null && t.isRemote()) out.add(t);
            }
            return new Snapshot(out, out.isEmpty() ? 0 : Math.max(0, root.optLong("t", 0)));
        } catch (JSONException | RuntimeException bad) {
            return new Snapshot(new ArrayList<Track>(), 0);
        }
    }

    /** True when a refresh is due: nothing cached, too old, or the clock went backwards. */
    public static boolean stale(long fetchedAt, long now) {
        return fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= FRESH_MS;
    }

    /** Wait before the next attempt after {@code failures} consecutive failures: 1, 2, 4, ... capped at 15 min. */
    public static long backoffMs(int failures) {
        if (failures <= 0) return 0;
        long wait = BACKOFF_BASE_MS << Math.min(failures - 1, 5);
        return Math.min(wait, BACKOFF_MAX_MS);
    }
}
