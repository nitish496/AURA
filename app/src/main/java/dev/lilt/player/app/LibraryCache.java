package dev.lilt.player.app;

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
 * On-disk copy of the last MediaStore scan so the song list appears at once on launch while a
 * fresh scan runs in the background. Blocking I/O: call off the main thread only. A damaged or
 * missing file reads as "no cache"; writes are atomic.
 */
final class LibraryCache {
    private static final int VERSION = 1;
    private final AtomicFile file;

    LibraryCache(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "library_cache.json")); }

    /** Cached local tracks, or an empty list. */
    List<Track> read() {
        List<Track> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            if (root.optInt("v") != VERSION) return out;
            JSONArray rows = root.optJSONArray("tracks");
            if (rows == null) return out;
            for (int i = 0; i < rows.length(); i++) {
                Track t = Track.fromJson(rows.optJSONObject(i));
                if (t != null && !t.isRemote()) out.add(t);
            }
        } catch (IOException | JSONException | RuntimeException ignored) {
            out.clear();
        }
        return out;
    }

    void write(List<Track> tracks) {
        FileOutputStream stream = null;
        try {
            JSONArray rows = new JSONArray();
            for (Track t : tracks) if (!t.isRemote()) rows.put(t.toJson());
            byte[] bytes = new JSONObject().put("v", VERSION).put("tracks", rows).toString().getBytes(StandardCharsets.UTF_8);
            stream = file.startWrite();
            stream.write(bytes);
            file.finishWrite(stream);
        } catch (IOException | JSONException | RuntimeException failure) {
            if (stream != null) file.failWrite(stream);
        }
    }

    void clear() { file.delete(); }
}
