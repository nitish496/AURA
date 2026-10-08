package dev.lilt.player;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
}
