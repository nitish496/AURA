package dev.lilt.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        if(values.equals(moodsFor(trackId)))return; // unchanged: no prefs copy, no disk write
        SharedPreferences.Editor edit=preferences.edit();
        if(values.isEmpty())edit.remove("song:"+trackId);
        else edit.putStringSet("song:"+trackId,values);
        edit.apply();
    }
    public void toggle(String trackId,String mood) { setMoods(trackId,MoodRules.toggle(moodsFor(trackId),mood)); }
    /**
     * Gives {@code mood} to exactly the {@code selected} songs among {@code trackIds} with a single
     * SharedPreferences write. Use this for "choose songs for a mood": calling setMoods per song
     * queues one apply() per library track and can stall the main thread in onPause.
     * @return how many songs changed
     */
    public int assign(String mood,Collection<String> trackIds,Collection<String> selected) {
        if(trackIds==null)return 0;
        Map<String,Set<String>> current=new HashMap<>();
        for(String id:trackIds)if(id!=null&&!id.isEmpty())current.put(id,moodsFor(id));
        Map<String,Set<String>> changes=MoodRules.assignment(current,trackIds,selected,mood);
        if(changes.isEmpty())return 0;
        SharedPreferences.Editor edit=preferences.edit();
        for(Map.Entry<String,Set<String>> change:changes.entrySet()) {
            if(change.getValue().isEmpty())edit.remove("song:"+change.getKey());
            else edit.putStringSet("song:"+change.getKey(),change.getValue());
        }
        edit.apply();
        return changes.size();
    }
    public List<Track> tracksFor(List<Track> library,String mood) {
        List<Track> result=new ArrayList<>();
        if(library!=null&&MoodRules.LABELS.contains(mood))for(Track track:library)
            if(track!=null&&MoodRules.matches(moodsFor(track.id),mood))result.add(track);
        return result;
    }
}
