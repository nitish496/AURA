package dev.lilt.player.app;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import dev.lilt.player.Track;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * On-disk copy of the last MediaStore scan so the song list appears at once on launch. Compact
 * binary file: header (magic, version, payload length, CRC32) then a de-duplicated string table
 * and fixed-width track rows. Any damage (bad magic/version/length/checksum, truncated or
 * malformed rows) reads as "no cache". Also stores the MediaStore signature the scan was taken
 * at, so an unchanged library needs no rescan. Blocking I/O: call off the main thread only.
 */
final class LibraryCache {
    /** Cached scan: local tracks plus the signature of the store when it was taken. */
    static final class Snapshot {
        final String signature;
        final List<Track> tracks;
        Snapshot(String signature, List<Track> tracks) { this.signature = signature; this.tracks = tracks; }
    }

    private static final int MAGIC = 0x41555243, VERSION = 2, HEADER = 16, MAX_PAYLOAD = 64 << 20;
    private final AtomicFile file;
    private final File legacy;

    LibraryCache(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "library_cache.bin"));
        legacy = new File(context.getFilesDir(), "library_cache.json");
    }

    private static Snapshot empty() { return new Snapshot("", new ArrayList<Track>()); }

    /** Cached local tracks and signature, or an empty snapshot (empty signature). */
    Snapshot read() {
        try {
            return decode(file.readFully());
        } catch (IOException | RuntimeException ignored) {
            return empty();
        }
    }

    void write(List<Track> tracks, String signature) {
        FileOutputStream stream = null;
        try {
            byte[] bytes = encode(tracks, signature);
            stream = file.startWrite();
            stream.write(bytes);
            file.finishWrite(stream);
            if (legacy.exists()) legacy.delete(); // old JSON cache is ignored and removed
        } catch (IOException | RuntimeException failure) {
            if (stream != null) file.failWrite(stream);
        }
    }

    void clear() {
        file.delete();
        if (legacy.exists()) legacy.delete();
    }

    /** A cached scan may stand in for a rescan only when both signatures are known and equal. */
    static boolean unchanged(String cachedSignature, String currentSignature) {
        return cachedSignature != null && currentSignature != null && !currentSignature.isEmpty()
                && cachedSignature.equals(currentSignature);
    }

    // ------------------------------------------------------------------ codec (pure, testable)

    static byte[] encode(List<Track> tracks, String signature) throws IOException {
        Map<String, Integer> index = new HashMap<>();
        List<String> table = new ArrayList<>();
        List<Track> local = new ArrayList<>(tracks.size());
        for (Track t : tracks) if (!t.isRemote()) local.add(t);
        ByteArrayOutputStream body = new ByteArrayOutputStream(local.size() * 40 + 64);
        DataOutputStream rows = new DataOutputStream(body);
        rows.writeInt(local.size());
        for (Track t : local) {
            String uri = t.uri == null ? "" : t.uri.toString();
            boolean same = t.id.equals(uri);
            rows.writeBoolean(same);
            rows.writeInt(intern(index, table, uri));
            if (!same) rows.writeInt(intern(index, table, t.id));
            rows.writeInt(intern(index, table, t.title));
            rows.writeInt(intern(index, table, t.artist));
            rows.writeInt(intern(index, table, t.album));
            rows.writeInt(intern(index, table, t.albumArtist));
            rows.writeLong(t.duration);
            rows.writeLong(t.albumId);
            rows.writeInt(t.trackNumber);
            rows.writeLong(t.dateAdded);
        }
        rows.flush();
        ByteArrayOutputStream payload = new ByteArrayOutputStream(body.size() + table.size() * 24 + 64);
        DataOutputStream out = new DataOutputStream(payload);
        out.writeUTF(signature == null ? "" : signature);
        out.writeInt(table.size());
        for (String s : table) out.writeUTF(s);
        body.writeTo(out);
        out.flush();
        byte[] data = payload.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        ByteArrayOutputStream whole = new ByteArrayOutputStream(data.length + HEADER);
        DataOutputStream head = new DataOutputStream(whole);
        head.writeInt(MAGIC);
        head.writeInt(VERSION);
        head.writeInt(data.length);
        head.writeInt((int) crc.getValue());
        head.write(data);
        head.flush();
        return whole.toByteArray();
    }

    /** Never throws: any problem yields an empty snapshot. */
    static Snapshot decode(byte[] bytes) {
        try {
            if (bytes == null || bytes.length < HEADER) return empty();
            DataInputStream head = new DataInputStream(new ByteArrayInputStream(bytes, 0, HEADER));
            if (head.readInt() != MAGIC || head.readInt() != VERSION) return empty();
            int length = head.readInt(), checksum = head.readInt();
            if (length < 0 || length > MAX_PAYLOAD || length != bytes.length - HEADER) return empty();
            CRC32 crc = new CRC32();
            crc.update(bytes, HEADER, length);
            if ((int) crc.getValue() != checksum) return empty();
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, HEADER, length));
            String signature = in.readUTF();
            int strings = in.readInt();
            if (strings < 0 || strings > length) return empty();
            String[] table = new String[strings];
            for (int i = 0; i < strings; i++) table[i] = in.readUTF();
            int count = in.readInt();
            if (count < 0 || count > length) return empty();
            List<Track> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                boolean idIsUri = in.readBoolean();
                String uri = table[in.readInt()];
                String id = idIsUri ? uri : table[in.readInt()];
                String title = table[in.readInt()], artist = table[in.readInt()], album = table[in.readInt()], albumArtist = table[in.readInt()];
                long duration = in.readLong(), albumId = in.readLong();
                int number = in.readInt();
                long added = in.readLong();
                if (id.isEmpty() || uri.isEmpty()) return empty();
                out.add(new Track(id, title, artist, album, Uri.parse(uri), duration, albumId, number, added, albumArtist));
            }
            if (in.available() != 0) return empty();
            return new Snapshot(signature, out);
        } catch (IOException | RuntimeException malformed) {
            return empty();
        }
    }

    private static int intern(Map<String, Integer> index, List<String> table, String s) {
        String v = s == null ? "" : s;
        Integer at = index.get(v);
        if (at != null) return at;
        index.put(v, table.size());
        table.add(v);
        return table.size() - 1;
    }
}
