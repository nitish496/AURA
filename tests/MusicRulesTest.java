package dev.lilt.player;
import java.util.Locale;
import java.util.Arrays;
public class MusicRulesTest {
    private static int count;
    private static void check(boolean value,String message) { count++;if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        check(MusicRules.matches("Evening","Aster","Night"," eve "),"trimmed title search");
        check(MusicRules.matches("Evening","Björk","Night","BJÖRK"),"Unicode artist search");
        check(MusicRules.matches("Evening","Aster","Night","night"),"album search");
        check(!MusicRules.matches("One","Two","Three","missing"),"no result");
        check(MusicRules.matches("One","Two","Three",""),"empty query");
        Locale old=Locale.getDefault();Locale.setDefault(new Locale("tr","TR"));
        check(MusicRules.matches("INDIGO","Artist","Album","indigo"),"search independent of Turkish locale");Locale.setDefault(old);
        check(!MusicRules.albumKey("A\u001fB","C").equals(MusicRules.albumKey("A","B\u001fC")),"metadata separator collision");
        check(!MusicRules.albumKey("Artist A","Same album").equals(MusicRules.albumKey("Artist B","Same album")),"distinct artists");
        check(MusicRules.time(-9223372036854775807L).equals("0:00"),"unknown duration");
        check(MusicRules.time(185999).equals("3:05"),"time rounding");
        check(MusicRules.time(3600000).equals("60:00"),"long track");
        check(MusicRules.queueOrder(0,new int[]{1,2,-1}).equals(Arrays.asList(0,1,2)),"sequential upcoming order");
        check(MusicRules.queueOrder(2,new int[]{1,-1,0}).equals(Arrays.asList(2,0,1)),"shuffled upcoming order");
        check(MusicRules.queueOrder(1,new int[]{1,2,0}).equals(Arrays.asList(1,2,0)),"repeat-all stops after one cycle");
        check(MusicRules.queueOrder(0,new int[]{0}).equals(Arrays.asList(0)),"repeat-one cannot loop forever");
        check(MusicRules.queueOrder(4,new int[]{1,-1}).isEmpty(),"invalid current index");
        check(MusicRules.queueOrder(0,new int[]{99}).equals(Arrays.asList(0)),"invalid successor terminates");
        check(MusicRules.collectionKey("Movie","Singer A",1).equals(MusicRules.collectionKey("Movie","Singer B",2)),"same movie different singers and MediaStore IDs");
        check(MusicRules.collectionKey("  MOVIE   Name ","A",1).equals(MusicRules.collectionKey("movie name","B",2)),"normalize movie whitespace and case");
        check(!MusicRules.collectionKey("Movie A","Singer",1).equals(MusicRules.collectionKey("Movie B","Singer",1)),"different movie names remain separate");
        check(MusicRules.collectionKey("Unknown album","A",1).equals(MusicRules.collectionKey("Unknown album","B",2)),"unknown albums share Other songs");
        check(MusicRules.collectionKey(null,"A",0).equals(MusicRules.collectionKey(null,"B",0)),"missing albums share Other songs");
        check(!MusicRules.collectionKey("Movie (Telugu)","A",1).equals(MusicRules.collectionKey("Movie (Tamil)","B",2)),"language editions retained");
        check(MusicRules.collectionKey("Downloads","Unknown artist",3).equals(MusicRules.collectionKey(null,"Singer",4)),"download folder merged with loose songs");
        check(MusicRules.displayAlbum(" DOWNLOAD ").equals("Other songs"),"download label normalized");
        check(MusicRules.displayAlbum(null).equals("Other songs"),"missing label");
        check(MusicRules.displayAlbum("Movie Name").equals("Movie Name"),"real album retained");
        check(!MusicRules.collectionKey("Downloads","Singer",3).equals(MusicRules.collectionKey("Movie Name","Singer",4)),"real movie not merged with downloads");
        check(MusicRules.displayTitle("Radhimma :: SenSongsMp3.Co").equals("Radhimma"),"domain suffix delimiter");
        check(MusicRules.displayTitle("Radhimma-Sen SongsMp3.Co.mp3").equals("Radhimma"),"spaced suffix and extension");
        check(MusicRules.displayTitle("Song.MP3").equals("Song"),"extension case folding");
        check(MusicRules.displayTitle("Song.mp3 :: SenSongsMp3.Com").equals("Song"),"extension before source");
        check(MusicRules.displayTitle("Song [SenSongsMp3.Co]").equals("Song"),"bracketed source");
        check(MusicRules.displayTitle("Song (www.SenSongsMp3.Com).mp3").equals("Song"),"wrapped www variant");
        check(MusicRules.displayTitle("SenSongsMp3.Com :: Song").equals("Song"),"source prefix");
        check(MusicRules.displayTitle("Song :: SenSongsMp3.Co :: Live").equals("Song Live"),"embedded source keeps other metadata");
        check(MusicRules.displayTitle("నువ్వే - నువ్వే.mp3").equals("నువ్వే - నువ్వే"),"native title punctuation retained");
        check(MusicRules.displayTitle("Love - Story (Live) :: Part 2").equals("Love - Story (Live) :: Part 2"),"legitimate separators preserved");
        check(MusicRules.displayTitle("MySenSongsMp3.ComSong").equals("MySenSongsMp3.ComSong"),"embedded legitimate word preserved");
        check(MusicRules.displayTitle("Song.mp3 Remix").equals("Song.mp3 Remix"),"nonterminal extension text preserved");
        check(MusicRules.displayTitle(null).equals("Untitled track"),"missing title fallback");
        check(MusicRules.displayTitle("SenSongsMp3.Co.mp3").equals("Untitled track"),"source-only fallback");
        check(MusicRules.displayTitle(MusicRules.displayTitle("Song :: SenSongsMp3.Co.mp3")).equals("Song"),"cleanup idempotent");
        System.out.println(count+" music-rule tests passed");
    }
}
