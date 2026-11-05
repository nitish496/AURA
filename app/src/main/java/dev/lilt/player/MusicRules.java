package dev.lilt.player;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Pure functions shared by library presentation, with host-runnable tests. */
public final class MusicRules {
    private MusicRules() {}
    private static final String SOURCE_SITE="(?i)(?<![\\p{L}\\p{N}])(?:www\\.)?sen\\s*songs\\s*mp3\\s*\\.\\s*co(?:m)?(?![\\p{L}\\p{N}])";
    // Compiled once: these run for every track during a scan and on every albumKey() call.
    private static final Pattern MP3_SUFFIX=Pattern.compile("(?i)\\.mp3$");
    private static final Pattern WRAPPED_SOURCE=Pattern.compile("[\\[(]\\s*"+SOURCE_SITE+"\\s*[\\])]");
    private static final Pattern SEPARATED_SOURCE=Pattern.compile("(?:\\s*(?:::|[-–—|])\\s*)?"+SOURCE_SITE+"(?:\\s*::\\s*)?");
    private static final Pattern WHITESPACE=Pattern.compile("\\s+");
    /** Cleans display metadata only; never renames or edits the source audio. */
    public static String displayTitle(String title) {
        if(title==null||title.trim().isEmpty()||title.trim().equals("<unknown>"))return "Untitled track";
        String trimmed=title.trim();
        // Every cleanup pattern needs "mp3" (ASCII, case-insensitive); most titles skip the regexes.
        if(!containsAsciiIgnoreCase(trimmed,"mp3"))return trimmed;
        String cleaned=MP3_SUFFIX.matcher(trimmed).replaceAll("").trim();
        // Remove only this known source domain, with its immediate wrapper/separators.
        cleaned=WRAPPED_SOURCE.matcher(cleaned).replaceAll("");
        cleaned=SEPARATED_SOURCE.matcher(cleaned).replaceAll(" ");
        cleaned=MP3_SUFFIX.matcher(cleaned.trim()).replaceAll("").trim();
        return cleaned.isEmpty()?"Untitled track":cleaned;
    }
    /** Case-insensitive for ASCII letters only, exactly like a regex (?i) flag without (?u). */
    static boolean containsAsciiIgnoreCase(String text,String lowerNeedle) {
        int n=lowerNeedle.length();
        outer:
        for(int i=0;i+n<=text.length();i++){
            for(int j=0;j<n;j++){
                char c=text.charAt(i+j);
                if(c>='A'&&c<='Z')c=(char)(c+('a'-'A'));
                if(c!=lowerNeedle.charAt(j))continue outer;
            }
            return true;
        }
        return false;
    }
    public static String albumKey(String artist,String album) {
        // Length-prefixing avoids collisions even when metadata contains separators.
        return artist.length()+":"+artist+album.length()+":"+album;
    }
    private static String normalizedAlbum(String album) {
        return album==null?"":WHITESPACE.matcher(java.text.Normalizer.normalize(album,java.text.Normalizer.Form.NFKC).trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
    public static boolean isLooseAlbum(String album) {
        String name=normalizedAlbum(album);
        return name.isEmpty()||name.equals("<unknown>")||name.equals("unknown")||name.equals("unknown album")||name.equals("singles & other songs")||name.equals("other songs")||name.equals("download")||name.equals("downloads");
    }
    public static String displayAlbum(String album) { return isLooseAlbum(album)?"Other songs":album.trim(); }
    public static String collectionKey(String album,String artist,long albumId) {
        if(isLooseAlbum(album))return "loose:other-songs";
        String name=normalizedAlbum(album);
        return "title:"+name.length()+":"+name;
    }
    public static boolean matches(String title,String artist,String album,String query) {
        String q=query.trim().toLowerCase(Locale.ROOT);
        return title.toLowerCase(Locale.ROOT).contains(q) || artist.toLowerCase(Locale.ROOT).contains(q) || album.toLowerCase(Locale.ROOT).contains(q);
    }
    public static List<Integer> queueOrder(int current,int[] successors) {
        List<Integer> order=new ArrayList<>();boolean[] visited=new boolean[successors.length];
        while(current>=0&&current<successors.length&&!visited[current]) {
            visited[current]=true;order.add(current);current=successors[current];
        }
        return order;
    }
    public static String time(long milliseconds) {
        long seconds=Math.max(0,milliseconds)/1000;
        return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
}
