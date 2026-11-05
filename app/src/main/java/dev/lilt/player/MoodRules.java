package dev.lilt.player;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit user preferences, not inferred audio or AI classifications. */
public final class MoodRules {
    public static final List<String> LABELS=Collections.unmodifiableList(
            Arrays.asList("Chill","Focus","Workout","Happy","Sad"));
    private MoodRules() {}
    public static Set<String> normalize(Collection<String> values) {
        Set<String> result=new LinkedHashSet<>();
        if(values!=null) for(String label:LABELS) if(values.contains(label)) result.add(label);
        return result;
    }
    public static boolean matches(Collection<String> values,String mood) {
        return LABELS.contains(mood)&&values!=null&&values.contains(mood);
    }
    public static Set<String> toggle(Collection<String> values,String mood) {
        Set<String> result=normalize(values);
        if(LABELS.contains(mood)&&!result.remove(mood))result.add(mood);
        return normalize(result);
    }
    /**
     * Assigning {@code mood} to exactly the {@code selected} songs among {@code trackIds}: returns
     * only the songs whose mood set changes, mapped to their new set (empty = clear the song).
     * Lets callers persist a whole "choose songs for this mood" edit in one write.
     */
    public static Map<String,Set<String>> assignment(Map<String,? extends Collection<String>> current,
                                                     Collection<String> trackIds,Collection<String> selected,String mood) {
        Map<String,Set<String>> changes=new LinkedHashMap<>();
        if(!LABELS.contains(mood)||trackIds==null)return changes;
        Set<String> chosen=selected==null?Collections.emptySet():new java.util.HashSet<>(selected);
        for(String id:trackIds) {
            if(id==null||id.isEmpty()||changes.containsKey(id))continue;
            Set<String> before=normalize(current==null?null:current.get(id));
            Set<String> after=new LinkedHashSet<>(before);
            if(chosen.contains(id))after.add(mood);else after.remove(mood);
            after=normalize(after);
            if(!after.equals(before))changes.put(id,after);
        }
        return changes;
    }
}
