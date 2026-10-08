package dev.lilt.player;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import org.json.*;
import java.util.*;

public final class Library {
    private final Context context;
    public Library(Context context) { this.context=context.getApplicationContext(); }
    public List<Track> scan() {
        List<Track> tracks=new ArrayList<>();
        Uri collection=Build.VERSION.SDK_INT>=29 ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] fields=Build.VERSION.SDK_INT>=30?new String[]{"_id","title","artist","album","duration","album_id","track","date_added","album_artist"}:new String[]{"_id","title","artist","album","duration","album_id","track","date_added"};
        try(Cursor c=context.getContentResolver().query(collection,fields,"duration > 0",null,"title COLLATE NOCASE ASC")) {
            if(c!=null) while(c.moveToNext()) {
                Uri uri=ContentUris.withAppendedId(collection,c.getLong(0));
                tracks.add(new Track(uri.toString(),c.getString(1),c.getString(2),c.getString(3),uri,c.getLong(4),c.getLong(5),c.getInt(6),c.getLong(7),Build.VERSION.SDK_INT>=30?c.getString(8):c.getString(2)));
            }
        }
        return tracks;
    }
    public SharedPreferences prefs() { return context.getSharedPreferences("library",Context.MODE_PRIVATE); }
    public Map<String,List<String>> playlists() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        try {
            JSONObject json=new JSONObject(prefs().getString("playlists","{}"));
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
