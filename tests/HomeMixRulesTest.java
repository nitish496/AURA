package dev.lilt.player;
import java.util.*;
public class HomeMixRulesTest {public static void main(String[] args){
 if(!HomeMixRules.order(Arrays.asList("a","a","b","b","c")).equals(Arrays.asList(0,2,4,1,3)))throw new AssertionError("interleaved albums");
 if(!HomeMixRules.order(Arrays.asList("a","a","a")).equals(Arrays.asList(0,1,2)))throw new AssertionError("single album retains all");
 if(!HomeMixRules.order(Collections.emptyList()).isEmpty())throw new AssertionError("empty");
 List<String> keys=new ArrayList<>();for(int i=0;i<303;i++)keys.add("album"+(i%7));List<Integer> result=HomeMixRules.order(keys);if(result.size()!=303||new HashSet<>(result).size()!=303)throw new AssertionError("all songs exactly once");
 System.out.println("4 home-mix tests passed");}}
