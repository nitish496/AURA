package dev.lilt.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Local per-song mood assignments; mixes only contain songs in the current library. */
public final class MoodStore {
    private final SharedPreferences preferences;
    public MoodStore(Context context) {
        preferences=context.getApplicationContext().getSharedPreferences("song_moods",Context.MODE_PRIVATE);
    }
    public static List<String> labels() { return MoodRules.LABELS; }
    public Set<String> moodsFor(String trackId) {
        if(trackId==null)return MoodRules.normalize(null);
        return MoodRules.normalize(preferences.getStringSet("song:"+trackId,Collections.emptySet()));
    }
    public void setMoods(String trackId,Collection<String> moods) {
        if(trackId==null||trackId.isEmpty())return;
        Set<String> values=MoodRules.normalize(moods);
        SharedPreferences.Editor edit=preferences.edit();
        if(values.isEmpty())edit.remove("song:"+trackId);
        else edit.putStringSet("song:"+trackId,values);
        edit.apply();
    }
    public void toggle(String trackId,String mood) { setMoods(trackId,MoodRules.toggle(moodsFor(trackId),mood)); }
    public List<Track> tracksFor(List<Track> library,String mood) {
        List<Track> result=new ArrayList<>();
        if(library!=null&&MoodRules.LABELS.contains(mood))for(Track track:library)
            if(track!=null&&MoodRules.matches(moodsFor(track.id),mood))result.add(track);
        return result;
    }
}
