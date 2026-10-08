package dev.lilt.player;
import java.util.*;
public final class AutoplayRulesTest {
    static int count;
    static AutoplayRules.Song song(String id,String album,String artist,String... moods){return new AutoplayRules.Song(id,album,artist,Arrays.asList(moods));}
    static void check(boolean condition,String label){count++;if(!condition)throw new AssertionError(label);}
    static String pick(List<AutoplayRules.Song> library,AutoplayRules.Song seed,List<String> recent,String... upcoming){return AutoplayRules.choose(library,seed,Arrays.asList(upcoming),recent,new Random(42));}
    public static void main(String[] args){
        AutoplayRules.Song seed=song("a","Movie","Singer","Chill");
        AutoplayRules.Song album=song("b"," movie ","Another"),artist=song("c","Different","SINGER"),mood=song("d","Else","Other","Chill"),other=song("e","Elsewhere","Someone");
        List<AutoplayRules.Song> all=Arrays.asList(seed,album,artist,mood,other);
        check(pick(null,seed,null)==null,"null library");
        check(pick(Collections.emptyList(),seed,null)==null,"empty library");
        check(pick(Collections.singletonList(seed),seed,null)==null,"single song never immediate repeats");
        check(pick(all,seed,null).equals("b"),"same movie preferred over singer");
        check(pick(all,seed,null,"b").equals("c"),"upcoming excluded artist next");
        check(pick(all,seed,null,"b","c").equals("d"),"explicit mood next");
        check(pick(all,seed,Arrays.asList("b","c","d")).equals("e"),"fresh before related recent");
        check(pick(all,seed,Arrays.asList("b","c","d","e")).equals("e"),"least recent fallback");
        check(pick(all,seed,Arrays.asList("e","d","c","b")).equals("b"),"history newest first respected");
        check(pick(all,seed,null,"b","c","d","e")==null,"all unavailable");
        check(pick(Arrays.asList(seed,album),seed,Arrays.asList("b")).equals("b"),"two song library can continue");
        check(pick(Arrays.asList(null,song("",null,null),album,album),seed,null).equals("b"),"invalid entries and duplicate IDs");
        check(pick(Collections.singletonList(other),null,null).equals("e"),"missing seed handled");
        List<AutoplayRules.Song> loose=Arrays.asList(song("b","Downloads","Unknown artist"),song("c","Different","Other","Focus"));
        check(pick(loose,song("a","Other songs","Unknown artist","Focus"),null).equals("c"),"generic album and unknown artist not related");
        check(pick(Arrays.asList(song("b","Ｍｏｖｉｅ","Other"),artist),seed,null).equals("b"),"Unicode normalized album");
        List<String> history=new ArrayList<>(Arrays.asList("a","b"));List<AutoplayRules.Song> copy=new ArrayList<>(all);pick(copy,seed,history);
        check(copy.equals(all)&&history.equals(Arrays.asList("a","b")),"inputs unchanged");
        Set<String> choices=new HashSet<>();Random rng=new Random(8);for(int i=0;i<100;i++)choices.add(AutoplayRules.choose(Arrays.asList(mood,song("f","Yet","Someone","Chill")),seed,null,null,rng));
        check(choices.size()==2,"related ties rotate");
        check(seed.moods.equals(Collections.singleton("Chill")),"explicit moods preserved");
        System.out.println(count+" autoplay-rule tests passed");
    }
}
