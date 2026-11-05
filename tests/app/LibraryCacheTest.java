package dev.lilt.player.app;

import android.net.Uri;
import dev.lilt.player.Track;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Binary library cache codec: round trip, damage tolerance and change detection. */
public final class LibraryCacheTest {
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }

    private static Track local(int i) {
        String uri = "content://media/external/audio/media/" + i;
        return new Track(uri, "Song " + i, "Artist " + (i % 7), "Album " + (i % 13), Uri.parse(uri), 180_000L + i, 100 + (i % 13), i % 12, 1_600_000_000L + i, "Album Artist " + (i % 3));
    }

    private static boolean same(Track a, Track b) {
        return a.id.equals(b.id) && a.uri.equals(b.uri) && a.title.equals(b.title) && a.artist.equals(b.artist) && a.album.equals(b.album)
                && a.albumArtist.equals(b.albumArtist) && a.duration == b.duration && a.albumId == b.albumId
                && a.trackNumber == b.trackNumber && a.dateAdded == b.dateAdded && a.albumKey().equals(b.albumKey());
    }

    public static void main(String[] args) throws Exception {
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < 8000; i++) tracks.add(local(i));
        tracks.add(Track.remote("123", "Remote", "R", "", "https://x/y", null, 1000, "", "", "")); // never cached
        byte[] bytes = LibraryCache.encode(tracks, "1#42");
        LibraryCache.Snapshot snap = LibraryCache.decode(bytes);
        check(snap.signature.equals("1#42"), "signature round-trips");
        check(snap.tracks.size() == 8000, "remote tracks skipped, locals kept");
        for (int i = 0; i < 8000; i += 97) check(same(tracks.get(i), snap.tracks.get(i)), "row " + i);
        check(same(tracks.get(7999), snap.tracks.get(7999)), "last row");
        System.out.println("8000 tracks -> " + bytes.length + " bytes");

        Track odd = new Track("custom-id", "T", "A", "B", Uri.parse("content://u/1"), 5, -1, 0, 0, "A");
        LibraryCache.Snapshot o = LibraryCache.decode(LibraryCache.encode(Arrays.asList(odd), ""));
        check(o.tracks.size() == 1 && o.tracks.get(0).id.equals("custom-id") && o.tracks.get(0).uri.toString().equals("content://u/1"), "id != uri");
        check(LibraryCache.decode(LibraryCache.encode(new ArrayList<Track>(), "s")).tracks.isEmpty(), "empty list");

        check(LibraryCache.decode(null).tracks.isEmpty(), "null");
        check(LibraryCache.decode(new byte[0]).tracks.isEmpty(), "zero length");
        check(LibraryCache.decode("{\"v\":1,\"tracks\":[]}".getBytes("UTF-8")).tracks.isEmpty(), "old JSON file ignored");
        check(LibraryCache.decode(Arrays.copyOf(bytes, bytes.length / 2)).tracks.isEmpty(), "truncated");
        check(LibraryCache.decode(Arrays.copyOf(bytes, bytes.length + 3)).tracks.isEmpty(), "trailing garbage");
        for (int at : new int[] {0, 5, 9, 14, 16, 40, bytes.length / 2, bytes.length - 1}) {
            byte[] bad = bytes.clone();
            bad[at] ^= 0x5A;
            check(LibraryCache.decode(bad).tracks.isEmpty(), "bit flip at " + at);
        }
        byte[] wrongVersion = bytes.clone();
        wrongVersion[7] = 99;
        check(LibraryCache.decode(wrongVersion).tracks.isEmpty(), "version mismatch");

        check(LibraryCache.unchanged("1#42", "1#42"), "same generation skips");
        check(!LibraryCache.unchanged("1#42", "1#43"), "new generation rescans");
        check(!LibraryCache.unchanged("1#42", "2#42"), "new version rescans");
        check(!LibraryCache.unchanged("", ""), "API 26-29 (no signature) always rescans");
        check(!LibraryCache.unchanged("1#42", ""), "unknown current rescans");
        check(!LibraryCache.unchanged(null, "1#42"), "no cache rescans");
        System.out.println("LibraryCacheTest ok");
    }
}
