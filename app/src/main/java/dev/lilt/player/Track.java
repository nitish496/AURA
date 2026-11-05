package dev.lilt.player;

import android.net.Uri;
import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import dev.lilt.player.stream.StreamArt;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One playable song: a MediaStore file ({@link #SOURCE_LOCAL}) or a streamed Audius track
 * ({@link #SOURCE_AUDIUS}, id {@code "audius:<trackId>"}, {@link #uri} = stream URL). Immutable.
 */
public final class Track {
    public static final String SOURCE_LOCAL = "local", SOURCE_AUDIUS = "audius";
    /** Album label of remote tracks that are not part of a known album. */
    public static final String REMOTE_SINGLE = "Single";
    /** {@link MediaMetadata#extras} keys set on remote items. */
    public static final String EXTRA_SOURCE = "source", EXTRA_ARTIST_ID = "artistId", EXTRA_PERMALINK = "permalink", EXTRA_DURATION_MS = "durationMs";
    private static final String REMOTE_PREFIX = "audius:";

    public final String id, title, artist, album;
    public final Uri uri;
    public final long duration,albumId,dateAdded;
    public final int trackNumber; public final String albumArtist;
    /** {@link #SOURCE_LOCAL} or {@link #SOURCE_AUDIUS}. */
    public final String source;
    /** Best https artwork for remote tracks (1000x1000 when available); null for local tracks. */
    public final String artworkUrl;
    /** Catalogue genre; "" when unknown and for local tracks. */
    public final String genre;
    /** Audius user id of the artist; "" when unknown and for local tracks. */
    public final String artistId;
    /** Audius permalink path such as {@code /handle/track-slug}; "" when unknown and for local tracks. */
    public final String permalink;

    public Track(String id, String title, String artist, String album, Uri uri, long duration) {
        this(id,title,artist,album,uri,duration,-1,0,0,artist);
    }
    public Track(String id,String title,String artist,String album,Uri uri,long duration,long albumId,int trackNumber,long dateAdded,String albumArtist) {
        this(id,title,artist,album,uri,duration,albumId,trackNumber,dateAdded,albumArtist,SOURCE_LOCAL,null,"","","");
    }
    private Track(String id,String title,String artist,String album,Uri uri,long duration,long albumId,int trackNumber,long dateAdded,String albumArtist,
                  String source,String artworkUrl,String genre,String artistId,String permalink) {
        this.albumId=albumId;this.trackNumber=trackNumber;this.dateAdded=dateAdded;this.albumArtist=clean(albumArtist,clean(artist,"Unknown artist"));
        this.id=id; this.title=MusicRules.displayTitle(title); this.artist=clean(artist,"Unknown artist");
        this.album=MusicRules.displayAlbum(album); this.uri=uri; this.duration=duration;
        this.source=source; this.artworkUrl=artworkUrl; this.genre=genre==null?"":genre.trim();
        this.artistId=artistId==null?"":artistId; this.permalink=permalink==null?"":permalink;
    }

    /**
     * A streamed Audius track. {@code id} is {@code "audius:<trackId>"} (the prefix is added when
     * missing); {@code streamUrl} becomes {@link #uri}; a blank album becomes {@link #REMOTE_SINGLE}.
     */
    public static Track remote(String id, String title, String artist, String album, String streamUrl, String artworkUrl, long durationMs, String genre) {
        return remote(id,title,artist,album,streamUrl,artworkUrl,durationMs,genre,"","");
    }

    /** {@link #remote(String, String, String, String, String, String, long, String)} plus artist id and permalink. */
    public static Track remote(String id, String title, String artist, String album, String streamUrl, String artworkUrl, long durationMs, String genre,
                               String artistId, String permalink) {
        if(id==null||id.trim().isEmpty())throw new IllegalArgumentException("Remote track needs an id");
        if(streamUrl==null||streamUrl.trim().isEmpty())throw new IllegalArgumentException("Remote track needs a stream URL");
        String trackId=id.startsWith(REMOTE_PREFIX)?id:REMOTE_PREFIX+id;
        String albumName=album==null||album.trim().isEmpty()?REMOTE_SINGLE:album.trim();
        String art=artworkUrl==null||artworkUrl.trim().isEmpty()?null:artworkUrl.trim();
        title=clean(title);artist=clean(artist);albumName=clean(albumName);
        return new Track(trackId,title,artist,albumName,Uri.parse(streamUrl.trim()),Math.max(0,durationMs),-1,0,0,artist,SOURCE_AUDIUS,art,genre,artistId,permalink);
    }

    /** The same remote track streamed from another URL (host failover); local tracks are returned unchanged. */
    public Track withStreamUrl(String streamUrl) {
        if(!isRemote()||streamUrl==null||streamUrl.equals(uri.toString()))return this;
        return new Track(id,title,artist,album,Uri.parse(streamUrl),duration,albumId,trackNumber,dateAdded,albumArtist,source,artworkUrl,genre,artistId,permalink);
    }

    public boolean isRemote() { return !SOURCE_LOCAL.equals(source); }

    private static String clean(String value, String fallback) {
        return value==null || value.trim().isEmpty() || value.equals("<unknown>") ? fallback : value;
    }

    /**
     * Grouping key for albums. Local: album title (unchanged). Remote: never merges with local
     * albums; a "Single" groups only itself, a named album groups per artist.
     */
    public String albumKey() {
        if(!isRemote())return MusicRules.collectionKey(album,albumArtist,albumId);
        if(REMOTE_SINGLE.equals(album))return "remote-single:"+id;
        return "remote-album:"+MusicRules.collectionKey(album,albumArtist,albumId)+":"+albumArtist.toLowerCase(Locale.ROOT);
    }

    /** Remote artwork sized for {@code pixels} (150/480/1000 variant); null for local tracks or no artwork. */
    public String artworkUrl(int pixels) { return artworkUrl==null?null:StreamArt.forSize(artworkUrl,pixels); }

    /** Public web page of a remote track (for sharing), or null. */
    public String webUrl() { return isRemote()&&permalink.startsWith("/")?"https://audius.co"+permalink:null; }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    public MediaItem item(Uri artwork) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album);
        if(!isRemote()){
            metadata.setArtworkUri(artwork==null?uri:artwork);
            return new MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata.build()).build();
        }
        // Remote: artwork is the https cover (custom covers are a local-file feature). The audio
        // engine keys its stream cache by track id from the URL (audio.CacheKeys), so cached songs
        // survive host changes and play offline.
        Bundle extras=new Bundle();
        extras.putString(EXTRA_SOURCE,source);
        extras.putString(EXTRA_ARTIST_ID,artistId);
        extras.putString(EXTRA_PERMALINK,permalink);
        extras.putLong(EXTRA_DURATION_MS,duration);
        metadata.setArtworkUri(artworkUrl==null?null:Uri.parse(artworkUrl)).setAlbumArtist(albumArtist).setExtras(extras);
        if(!genre.isEmpty())metadata.setGenre(genre);
        if(duration>0)metadata.setDurationMs(duration);
        return new MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata.build()).build();
    }

    /** JSON record for persistence (remote metadata; local tracks round-trip too). */
    public JSONObject toJson() {
        JSONObject o=new JSONObject();
        try {
            o.put("v",1).put("id",id).put("source",source).put("title",title).put("artist",artist).put("album",album)
                    .put("uri",uri==null?"":uri.toString()).put("durationMs",duration);
            if(isRemote()){
                if(artworkUrl!=null)o.put("artworkUrl",artworkUrl);
                if(!genre.isEmpty())o.put("genre",genre);
                if(!artistId.isEmpty())o.put("artistId",artistId);
                if(!permalink.isEmpty())o.put("permalink",permalink);
            } else {
                o.put("albumId",albumId).put("trackNumber",trackNumber).put("dateAdded",dateAdded).put("albumArtist",albumArtist);
            }
        } catch(JSONException impossible) { throw new IllegalStateException(impossible); }
        return o;
    }

    /** Restores {@link #toJson()} output; null when the record is unusable (missing id/uri, bad remote id). */
    public static Track fromJson(JSONObject o) {
        if(o==null)return null;
        String id=string(o,"id"),uri=string(o,"uri");
        if(id.isEmpty()||uri.isEmpty())return null;
        long duration=Math.max(0,o.optLong("durationMs",0));
        boolean remote=id.startsWith(REMOTE_PREFIX);
        String source=string(o,"source");
        if(source.isEmpty())source=remote?SOURCE_AUDIUS:SOURCE_LOCAL;
        if(SOURCE_LOCAL.equals(source)){
            if(remote)return null;
            return new Track(id,string(o,"title"),string(o,"artist"),string(o,"album"),Uri.parse(uri),duration,o.optLong("albumId",-1),o.optInt("trackNumber",0),o.optLong("dateAdded",0),string(o,"albumArtist"));
        }
        if(!remote||!SOURCE_AUDIUS.equals(source))return null;
        String art=string(o,"artworkUrl");
        return remote(id,string(o,"title"),string(o,"artist"),string(o,"album"),uri,art.isEmpty()?null:art,duration,string(o,"genre"),string(o,"artistId"),string(o,"permalink"));
    }

    /** Catalogue text without emoji (the design bans them); unchanged when nothing else would be left. */
    private static String clean(String s) {
        if(s==null)return s;
        String stripped=dev.lilt.player.stream.RemoteText.stripEmoji(s);
        return stripped==null||stripped.trim().isEmpty()?s:stripped;
    }

    /** Null-safe string read (Android's optString turns JSON null into "null"). */
    private static String string(JSONObject o,String key) {
        Object value=o.opt(key);
        return value==null||JSONObject.NULL.equals(value)?"":String.valueOf(value);
    }
}
