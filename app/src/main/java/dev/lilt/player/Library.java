package dev.lilt.player;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import org.json.*;
import java.util.*;

public final class Library {
    /**
     * Playable audio that is not a system sound: MediaStore flags files under Alarms/,
     * Notifications/ and Ringtones/, which are not songs. NULL flags (very old rows) still count.
     */
    static final String AUDIO_SELECTION="duration > 0"
            +" AND (is_alarm IS NULL OR is_alarm = 0)"
            +" AND (is_notification IS NULL OR is_notification = 0)"
            +" AND (is_ringtone IS NULL OR is_ringtone = 0)";
    private final Context context;
    public Library(Context context) { this.context=context.getApplicationContext(); }
    public List<Track> scan() {
        List<Track> tracks=new ArrayList<>();
        Uri collection=Build.VERSION.SDK_INT>=29 ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] fields=Build.VERSION.SDK_INT>=30?new String[]{"_id","title","artist","album","duration","album_id","track","date_added","album_artist"}:new String[]{"_id","title","artist","album","duration","album_id","track","date_added"};
        try(Cursor c=context.getContentResolver().query(collection,fields,AUDIO_SELECTION,null,"title COLLATE NOCASE ASC")) {
            if(c!=null) while(c.moveToNext()) {
                Uri uri=ContentUris.withAppendedId(collection,c.getLong(0));
                tracks.add(new Track(uri.toString(),c.getString(1),c.getString(2),c.getString(3),uri,c.getLong(4),c.getLong(5),c.getInt(6),c.getLong(7),Build.VERSION.SDK_INT>=30?c.getString(8):c.getString(2)));
            }
        }
        return tracks;
    }
    /**
     * Cheap fingerprint of the audio store (MediaStore version + volume generation, API 30+); equal
     * fingerprints mean nothing was added, removed or edited. Empty when unavailable (API 26-29),
     * which callers treat as "always rescan". Take it before scanning so edits made during the
     * scan show up as a mismatch next time.
     */
    public String storeSignature() {
        if(Build.VERSION.SDK_INT<30)return "";
        try {
            StringBuilder sig=new StringBuilder();
            java.util.TreeSet<String> volumes=new java.util.TreeSet<>(MediaStore.getExternalVolumeNames(context));
            if(volumes.isEmpty())volumes.add(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            for(String volume:volumes)sig.append(volume).append(':').append(MediaStore.getVersion(context,volume)).append('#').append(MediaStore.getGeneration(context,volume)).append(';');
            return sig.toString();
        } catch(RuntimeException unavailable) { return ""; }
    }
    public SharedPreferences prefs() { return context.getSharedPreferences("library",Context.MODE_PRIVATE); }
    private String playlistsRaw; private Map<String,List<String>> playlistsParsed;
    /** Parsed once per stored value; every call returns a fresh mutable copy. */
    public synchronized Map<String,List<String>> playlists() {
        String raw=prefs().getString("playlists","{}");
        if(raw.equals(playlistsRaw)&&playlistsParsed!=null)return copy(playlistsParsed);
        Map<String,List<String>> parsed=parsePlaylists(raw);
        playlistsRaw=raw;playlistsParsed=parsed;
        return copy(parsed);
    }
    private static Map<String,List<String>> copy(Map<String,List<String>> src) {
        Map<String,List<String>> out=new LinkedHashMap<>();
        for(Map.Entry<String,List<String>> e:src.entrySet())out.put(e.getKey(),new ArrayList<>(e.getValue()));
        return out;
    }
    private static Map<String,List<String>> parsePlaylists(String raw) {
        Map<String,List<String>> result=new LinkedHashMap<>();
        try {
            JSONObject json=new JSONObject(raw);
            Iterator<String> keys=json.keys();
            while(keys.hasNext()) { String key=keys.next(); JSONArray a=json.optJSONArray(key);if(a==null)continue;List<String> ids=new ArrayList<>();for(int i=0;i<a.length();i++){Object value=a.opt(i);if(value instanceof String&&!((String)value).isEmpty())ids.add((String)value);}result.put(key,ids); }
        } catch(JSONException ignored) {}
        return result;
    }
    public void savePlaylists(Map<String,List<String>> lists) {
        JSONObject json=new JSONObject();
        try { for(Map.Entry<String,List<String>> entry:lists.entrySet()) json.put(entry.getKey(),new JSONArray(entry.getValue())); }
        catch(JSONException e) { throw new IllegalStateException(e); }
        prefs().edit().putString("playlists",json.toString()).apply();
    }
    public Uri artwork(Track track) {
        String path=prefs().getString("cover:"+track.albumKey(),null);
        if(path==null&&track.albumId>0)path=prefs().getString("cover:album:"+track.albumId,null);
        if(path==null)path=prefs().getString("cover:"+MusicRules.albumKey(track.artist,track.album),null);
        return path==null ? null : Uri.parse(path);
    }
}
