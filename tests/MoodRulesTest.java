package dev.lilt.player;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
public final class MoodRulesTest {
    private static int count;
    private static void check(boolean value,String message) { count++;if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        check(MoodRules.normalize(null).isEmpty(),"null assignments");
        check(MoodRules.normalize(Arrays.asList("Happy","unknown","Chill","Happy")).equals(
                new LinkedHashSet<>(Arrays.asList("Chill","Happy"))),"known unique moods");
        check(MoodRules.normalize(Arrays.asList("chill"," Chill ")).isEmpty(),"only explicit supported labels");
        check(MoodRules.matches(Arrays.asList("Focus","Chill"),"Chill"),"multiple moods per song");
        check(!MoodRules.matches(null,"Focus"),"untagged song excluded");
        check(!MoodRules.matches(Arrays.asList("made up"),"made up"),"unknown mood excluded");
        check(!MoodRules.matches(Arrays.asList("Chill"),"Workout"),"no unrelated songs");
        check(MoodRules.toggle(Collections.emptySet(),"Sad").contains("Sad"),"assign mood");
        check(MoodRules.toggle(Arrays.asList("Sad","Focus"),"Sad").equals(Collections.singleton("Focus")),"remove one mood");
        check(MoodRules.toggle(Arrays.asList("Chill"),"bad").equals(Collections.singleton("Chill")),"invalid toggle harmless");
        Set<String> input=new LinkedHashSet<>(Arrays.asList("Happy"));MoodRules.toggle(input,"Happy");
        check(input.contains("Happy"),"toggle does not mutate source");
        Set<String> copy=MoodRules.normalize(input);copy.clear();check(input.contains("Happy"),"normalization defensive copy");
        boolean immutable=false;try {MoodRules.LABELS.add("bad");}catch(UnsupportedOperationException e){immutable=true;}
        check(immutable,"labels immutable");
        System.out.println(count+" mood-rule tests passed");
    }
}
