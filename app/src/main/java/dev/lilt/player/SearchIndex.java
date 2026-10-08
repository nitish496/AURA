package dev.lilt.player;

import java.text.Normalizer;
import java.util.*;

/** Immutable local metadata index. Scores never use playback state or network data. */
public final class SearchIndex {
    private final List<Entry> entries;
    public SearchIndex(List<String[]> metadata) {
        List<Entry> built=new ArrayList<>();
        for(String[] row:metadata) built.add(new Entry(row[0],row[1],row[2],row[3]));
        entries=Collections.unmodifiableList(built);
    }
    public static String normalize(String value) {
        if(value==null)return "";
        String decomposed=Normalizer.normalize(value,Normalizer.Form.NFKD);StringBuilder folded=new StringBuilder();boolean latin=false;
        for(int offset=0;offset<decomposed.length();){int cp=decomposed.codePointAt(offset);offset+=Character.charCount(cp);
            int type=Character.getType(cp);boolean mark=type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK;
            if(!mark)latin=Character.UnicodeScript.of(cp)==Character.UnicodeScript.LATIN;
            if(!mark||!latin)folded.appendCodePoint(cp);
        }
        return folded.toString().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\p{M}]+"," ").trim().replaceAll("\\s+"," ");
    }
    public List<String> search(String query) {
        String q=normalize(query);List<Hit> hits=new ArrayList<>();
        if(q.isEmpty()){List<String> ids=new ArrayList<>();for(Entry e:entries)ids.add(e.id);return ids;}
        String[] tokens=q.split(" ");
        for(Entry e:entries){int score=score(e,q,tokens);if(score<1000)hits.add(new Hit(e.id,score));}
        // Stable sort preserves library order for equally relevant songs.
        hits.sort(Comparator.comparingInt(h->h.score));List<String> ids=new ArrayList<>();for(Hit h:hits)ids.add(h.id);return ids;
    }
    private static int score(Entry e,String q,String[] tokens){
        int title=field(e.title,q,tokens);if(title<1000)return title;
        int artist=field(e.artist,q,tokens);int album=field(e.album,q,tokens);
        if(artist<1000||album<1000)return Math.min(100+artist,200+album);
        if(allTokens(e.all,tokens))return 350;
        // One edit only, words of at least five characters, and only single-word queries.
        // Exact metadata always outranks this fallback; short vague queries stay literal.
        if(tokens.length==1&&q.length()>=5&&q.length()<=40){
            for(String word:e.titleWords)if(oneEdit(q,word))return 500;
        }
        return 1000;
    }
    private static int field(String value,String q,String[] tokens){
        if(value.equals(q))return 0;if(value.startsWith(q))return 10;
        if(allTokens(value,tokens))return 20;if(value.contains(q))return 30;return 1000;
    }
    private static boolean allTokens(String value,String[] tokens){
        String[] words=value.split(" ");for(String token:tokens){boolean found=false;
            for(String word:words)if(word.startsWith(token)){found=true;break;}if(!found)return false;
        }return true;
    }
    private static boolean oneEdit(String a,String b){
        if(b.length()<5||Math.abs(a.length()-b.length())>1)return false;
        int i=0,j=0,edits=0;while(i<a.length()&&j<b.length()){
            if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}if(++edits>1)return false;
            if(a.length()>=b.length())i++;if(b.length()>=a.length())j++;
        }return edits+(i<a.length()||j<b.length()?1:0)<=1;
    }
    private static final class Entry {
        final String id,title,artist,album,all;final String[] titleWords;
        Entry(String id,String title,String artist,String album){this.id=id;this.title=normalize(title);this.artist=normalize(artist);this.album=normalize(album);all=this.title+" "+this.artist+" "+this.album;titleWords=this.title.split(" ");}
    }
    private static final class Hit {final String id;final int score;Hit(String id,int score){this.id=id;this.score=score;}}
}
