package dev.lilt.player;
import java.util.*;
/** Round-robin albums so home shelves mix the library without dropping tracks. */
public final class HomeMixRules {
 private HomeMixRules() {}
 public static List<Integer> order(List<String> keys){Map<String,ArrayDeque<Integer>> albums=new LinkedHashMap<>();for(int i=0;i<keys.size();i++)albums.computeIfAbsent(keys.get(i),k->new ArrayDeque<>()).add(i);List<Integer> result=new ArrayList<>();boolean remaining=true;while(remaining){remaining=false;for(ArrayDeque<Integer> album:albums.values())if(!album.isEmpty()){result.add(album.removeFirst());remaining=true;}}return result;}
}
