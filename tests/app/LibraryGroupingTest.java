package app;

import android.net.Uri;
import dev.lilt.player.Track;
import dev.lilt.player.app.AlbumGroup;
import dev.lilt.player.app.ArtistGroup;
import dev.lilt.player.app.LibraryGrouping;
import java.util.ArrayList;
import java.util.List;

public final class LibraryGroupingTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static Track local(String id, String title, String artist, String album, int number, String albumArtist) {
        return new Track(id, title, artist, album, Uri.parse("content://media/" + id), 1000, 1, number, 0, albumArtist);
    }

    public static void main(String[] args) {
        List<Track> tracks = new ArrayList<>();
        tracks.add(local("1", "Second", "Field Theory", "Low Tide", 2, "Field Theory"));
        tracks.add(local("2", "First", "Field Theory", "Low Tide", 1, "Field Theory"));
        tracks.add(local("3", "Untagged", "field theory", "", 0, null));
        tracks.add(local("4", "Duet", "Guest", "Mixtape", 1, "Guest"));
        tracks.add(local("5", "Solo", "Host", "Mixtape", 2, "Host"));
        tracks.add(local("6", "Disc two", "Field Theory", "Low Tide", 1001, "Field Theory"));
        tracks.add(Track.remote("audius:9", "Stream", "Field Theory", "", "https://x/stream", null, 1000, ""));
        List<AlbumGroup> albums = LibraryGrouping.albums(tracks);
        check(albums.size() == 3, "remote single is not an album: " + albums.size());
        AlbumGroup low = null, mix = null, other = null;
        for (AlbumGroup a : albums) { if (a.title.equals("Low Tide")) low = a; if (a.title.equals("Mixtape")) mix = a; if (a.title.equals("Other songs")) other = a; }
        check(low != null && low.tracks.get(0).title.equals("First") && low.tracks.get(1).title.equals("Second")
                && low.tracks.get(2).title.equals("Disc two"), "track number then disc order");
        check(mix != null && mix.artist.equals(LibraryGrouping.VARIOUS), "no majority -> Various Artists");
        check(other != null && other.tracks.size() == 1, "loose files collect in Other songs");
        check(albums.get(0).title.equals("Low Tide"), "albums sorted by title");

        List<ArtistGroup> artists = LibraryGrouping.artists(tracks, albums);
        ArtistGroup ft = null;
        for (ArtistGroup a : artists) if (a.name.equalsIgnoreCase("Field Theory")) ft = a;
        check(ft != null && ft.tracks.size() == 5, "case-insensitive artist with remote track: " + (ft == null ? 0 : ft.tracks.size()));
        check(ft.albums.size() == 2, "artist albums: low tide + other songs");
        System.out.println("LibraryGroupingTest OK");
    }
}
