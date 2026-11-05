package dev.lilt.player;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * SearchIndex now precompiles its regexes and splits words once per entry instead of once per
 * query. Ranking must be unchanged: the original implementation (verbatim below) is the oracle for
 * random libraries and queries in many scripts.
 */
public final class SearchIndexEquivalenceTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    /** Verbatim pre-2.0 implementation (git show ee64b55:.../SearchIndex.java). */
    static final class Legacy {
        private final List<Entry> entries;
        Legacy(List<String[]> metadata) {
            List<Entry> built=new ArrayList<>();
            for(String[] row:metadata) built.add(new Entry(row[0],row[1],row[2],row[3]));
            entries=Collections.unmodifiableList(built);
        }
        static String normalize(String value) {
            if(value==null)return "";
            String decomposed=Normalizer.normalize(value,Normalizer.Form.NFKD);StringBuilder folded=new StringBuilder();boolean latin=false;
            for(int offset=0;offset<decomposed.length();){int cp=decomposed.codePointAt(offset);offset+=Character.charCount(cp);
                int type=Character.getType(cp);boolean mark=type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK;
                if(!mark)latin=Character.UnicodeScript.of(cp)==Character.UnicodeScript.LATIN;
                if(!mark||!latin)folded.appendCodePoint(cp);
            }
            return folded.toString().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\p{M}]+"," ").trim().replaceAll("\\s+"," ");
        }
        List<String> search(String query) {
            String q=normalize(query);List<Hit> hits=new ArrayList<>();
            if(q.isEmpty()){List<String> ids=new ArrayList<>();for(Entry e:entries)ids.add(e.id);return ids;}
            String[] tokens=q.split(" ");
            for(Entry e:entries){int score=score(e,q,tokens);if(score<1000)hits.add(new Hit(e.id,score));}
            hits.sort(Comparator.comparingInt(h->h.score));List<String> ids=new ArrayList<>();for(Hit h:hits)ids.add(h.id);return ids;
        }
        private static int score(Entry e,String q,String[] tokens){
            int title=field(e.title,q,tokens);if(title<1000)return title;
            int artist=field(e.artist,q,tokens);int album=field(e.album,q,tokens);
            if(artist<1000||album<1000)return Math.min(100+artist,200+album);
            if(allTokens(e.all,tokens))return 350;
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

    private static final String[] WORDS = {
        "love", "Love", "LOVE", "lover", "loves", "night", "nights", "Midnight", "radhimma", "Radhimma",
        "radhmma", "café", "Cafe", "CAFÉ", "naïve", "Björk", "bjork", "Beyoncé", "Ñandú", "straße",
        "strasse", "నువ్వే", "నువ్వు", "ప్రేమ", "東京", "東京タワー", "사랑", "любовь", "Любовь", "حب",
        "remix", "Remix", "live", "(Live)", "[Remastered]", "feat.", "ft.", "&", "-", "::", "—", "'",
        "99", "2024", "Ⅳ", "ﬁre", "fire", "éclair", "eclair", "🎵", "tamil", "telugu", "", " ",
    };

    private static String phrase(Random random, int maxWords) {
        int n = random.nextInt(maxWords + 1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append(random.nextInt(5) == 0 ? "  " : " ");
            b.append(WORDS[random.nextInt(WORDS.length)]);
        }
        return b.toString();
    }

    public static void main(String[] args) {
        Random random = new Random(42);
        // normalize() must be identical for every word and phrase.
        for (String w : WORDS) check(Legacy.normalize(w).equals(SearchIndex.normalize(w)), "normalize [" + w + "]");
        check(SearchIndex.normalize(null).equals(""), "null normalizes to empty");
        for (int i = 0; i < 5000; i++) {
            String p = phrase(random, 6);
            check(Legacy.normalize(p).equals(SearchIndex.normalize(p)), "normalize [" + p + "]");
        }
        for (int library = 0; library < 25; library++) {
            List<String[]> rows = new ArrayList<>();
            int size = 1 + random.nextInt(300);
            for (int i = 0; i < size; i++) {
                rows.add(new String[] {"id" + i, phrase(random, 4), phrase(random, 3), phrase(random, 3)});
            }
            Legacy legacy = new Legacy(rows);
            SearchIndex index = new SearchIndex(rows);
            check(legacy.search("").equals(index.search("")), "empty query lists the library");
            for (int q = 0; q < 200; q++) {
                String query = random.nextInt(4) == 0 ? WORDS[random.nextInt(WORDS.length)] : phrase(random, 3);
                List<String> expected = legacy.search(query), actual = index.search(query);
                check(expected.equals(actual), "ranking differs for [" + query + "]: " + expected + " vs " + actual);
            }
        }
        check(new SearchIndex(Arrays.<String[]>asList()).search("love").isEmpty(), "empty library");
        System.out.println("SearchIndexEquivalenceTest: " + checks + " checks passed");
    }
}
