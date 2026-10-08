package dev.lilt.player;
import java.util.*;
public final class SearchIndexTest {
 private static int count;
 private static void check(boolean ok,String label){count++;if(!ok)throw new AssertionError(label);}
 private static SearchIndex index(String[]...rows){return new SearchIndex(Arrays.asList(rows));}
 public static void main(String[] args){
 SearchIndex s=index(new String[]{"artist","Something","Radhimma","Other"},new String[]{"partial","My Radhimma Remix","Singer","Movie"},new String[]{"prefix","Radhimma Live","Singer","Movie"},new String[]{"exact","Radhimma","Singer","Movie"},new String[]{"album","Elsewhere","Nobody","Radhimma"});
 check(s.search("RADHIMMA").equals(Arrays.asList("exact","prefix","partial","artist","album")),"title relevance before artist and album");
 check(s.search("radhmma").equals(Arrays.asList("partial","prefix","exact")),"one deleted letter title fallback stable");
 check(s.search("rad").equals(Arrays.asList("prefix","exact","partial","artist","album")),"short prefix literal");
 check(s.search("xyz").isEmpty(),"unrelated rejected");
 check(s.search("radhxxx").isEmpty(),"several typos rejected");
 check(s.search("").equals(Arrays.asList("artist","partial","prefix","exact","album")),"empty library order");
 check(s.search("  ").equals(s.search("")),"blank library order");
 check(s.search(null).equals(s.search("")),"null harmless");
 check(SearchIndex.normalize("  CAFÉ—Song  ").equals("cafe song"),"diacritics punctuation spaces");
 check(SearchIndex.normalize("ＡＢＣ").equals("abc"),"compatibility Unicode");
 SearchIndex u=index(new String[]{"a","Café Song","A","M"},new String[]{"b","నువ్వే నువ్వే","B","N"});
 check(u.search("cafe song").equals(Arrays.asList("a")),"accent match");
 check(u.search("నువ్వే").equals(Arrays.asList("b")),"native script match");
 check(!SearchIndex.normalize("నువ్వే").equals(SearchIndex.normalize("నువ్వు")),"native vowel distinctions retained");
 check(u.search("song cafe").equals(Arrays.asList("a")),"unordered title tokens");
 check(u.search("cafe a").equals(Arrays.asList("a")),"combined metadata tokens");
 check(s.search("sing movie").equals(Arrays.asList("partial","prefix","exact")),"artist album tokens");
 check(s.search("live radh").equals(Arrays.asList("prefix")),"token prefix order");
 check(index(new String[]{"a","Ocean","",""}).search("Oceam").equals(Arrays.asList("a")),"single substitution");
 check(index(new String[]{"a","Ocean","",""}).search("Oceann").equals(Arrays.asList("a")),"single insertion");
 check(index(new String[]{"a","Blue","",""}).search("Blud").isEmpty(),"short fuzzy disabled");
 check(index(new String[]{"a","Ocean","",""}).search("Oecan").isEmpty(),"transposition not overmatched");
 check(index(new String[]{"a","Nothing","Ocean",""}).search("Oceam").isEmpty(),"fuzzy title only");
 List<String[]> rows=new ArrayList<>();for(int i=0;i<10000;i++)rows.add(new String[]{""+i,"Song "+i,"Artist","Album"});SearchIndex large=new SearchIndex(rows);
 check(large.search("song 9999").get(0).equals("9999"),"large library exact first");
 check(index().search("abc").isEmpty(),"empty index");
 System.out.println(count+" search-index tests passed");
 }
}
