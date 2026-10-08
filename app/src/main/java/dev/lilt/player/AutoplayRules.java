package dev.lilt.player;

import java.text.Normalizer;
import java.util.*;

/** Local metadata recommendations. No network, audio inference or AI claims. */
public final class AutoplayRules {
    private AutoplayRules() {}
    public static final class Song {
        public final String id, album, artist;
        public final Set<String> moods;
        public Song(String id,String album,String artist,Collection<String> moods) {
            this.id=id==null?"":id;
            this.album=normalize(album);this.artist=normalize(artist);
            this.moods=Collections.unmodifiableSet(MoodRules.normalize(moods));
        }
    }
    private static String normalize(String text) {
        return text==null?"":Normalizer.normalize(text,Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
    private static int relation(Song song,Song seed) {
        if(seed==null)return 0;
        int score=0;
        if(!MusicRules.isLooseAlbum(song.album)&&song.album.equals(seed.album))score+=8;
        if(!song.artist.isEmpty()&&!song.artist.equals("unknown artist")&&!song.artist.equals("<unknown>")&&song.artist.equals(seed.artist))score+=4;
        for(String mood:song.moods)if(seed.moods.contains(mood)){score+=2;break;}
        return score;
    }
    /**
     * recentNewestFirst is an actual bounded playback history. upcomingIds contains only
     * unplayed queue items. Current/upcoming songs are never selected. Recent tracks are
     * excluded while fresh choices exist; otherwise the least recent eligible song wins.
     * Ties use the caller's Random so related tracks rotate rather than alphabetical loops.
     */
    public static String choose(List<Song> library,Song seed,Collection<String> upcomingIds,
                                List<String> recentNewestFirst,Random random) {
        if(library==null||library.isEmpty())return null;
        Set<String> excluded=new HashSet<>();
        if(upcomingIds!=null)excluded.addAll(upcomingIds);
        if(seed!=null)excluded.add(seed.id);
        Map<String,Integer> recency=new HashMap<>();
        if(recentNewestFirst!=null)for(int i=0;i<recentNewestFirst.size();i++)
            recency.putIfAbsent(recentNewestFirst.get(i),i);
        LinkedHashMap<String,Song> eligible=new LinkedHashMap<>();
        for(Song song:library)if(song!=null&&!song.id.isEmpty()&&!excluded.contains(song.id))eligible.putIfAbsent(song.id,song);
        boolean fresh=false;for(String id:eligible.keySet())if(!recency.containsKey(id)){fresh=true;break;}
        String chosen=null;int bestRecent=-1,bestScore=-1,ties=0;
        Random rng=random==null?new Random():random;
        for(Song song:eligible.values()) {
            Integer seen=recency.get(song.id);
            if(fresh&&seen!=null)continue;
            int age=seen==null?Integer.MAX_VALUE:seen;
            int score=relation(song,seed);
            boolean better=chosen==null||(!fresh&&age>bestRecent)||((fresh||age==bestRecent)&&score>bestScore);
            if(better){chosen=song.id;bestRecent=age;bestScore=score;ties=1;}
            else if((fresh||age==bestRecent)&&score==bestScore&&rng.nextInt(++ties)==0)chosen=song.id;
        }
        return chosen;
    }
}
