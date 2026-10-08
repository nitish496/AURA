package dev.lilt.player;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

public final class Track {
    public final String id, title, artist, album;
    public final Uri uri;
    public final long duration,albumId,dateAdded;
    public final int trackNumber; public final String albumArtist;
    public Track(String id, String title, String artist, String album, Uri uri, long duration) {
        this(id,title,artist,album,uri,duration,-1,0,0,artist);
    }
    public Track(String id,String title,String artist,String album,Uri uri,long duration,long albumId,int trackNumber,long dateAdded,String albumArtist) {
        this.albumId=albumId;this.trackNumber=trackNumber;this.dateAdded=dateAdded;this.albumArtist=clean(albumArtist,clean(artist,"Unknown artist"));
        this.id=id; this.title=MusicRules.displayTitle(title); this.artist=clean(artist,"Unknown artist");
        this.album=MusicRules.displayAlbum(album); this.uri=uri; this.duration=duration;
    }
    private static String clean(String value, String fallback) {
        return value==null || value.trim().isEmpty() || value.equals("<unknown>") ? fallback : value;
    }
    public String albumKey() { return MusicRules.collectionKey(album,albumArtist,albumId); }
    public MediaItem item(Uri artwork) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album);
        metadata.setArtworkUri(artwork==null?uri:artwork);
        return new MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata.build()).build();
    }
}
