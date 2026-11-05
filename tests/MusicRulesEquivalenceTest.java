package dev.lilt.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * MusicRules now uses precompiled patterns and an "mp3" fast path. It must behave exactly like
 * the original String.replaceAll implementation (copied verbatim below from the pre-2.0 baseline),
 * so the comparison runs over crafted cases plus thousands of random titles and album names.
 */
public final class MusicRulesEquivalenceTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    /** Verbatim pre-2.0 implementation (git show ee64b55:.../MusicRules.java). */
    static final class Legacy {
        private static final String SOURCE_SITE="(?i)(?<![\\p{L}\\p{N}])(?:www\\.)?sen\\s*songs\\s*mp3\\s*\\.\\s*co(?:m)?(?![\\p{L}\\p{N}])";
        static String displayTitle(String title) {
            if(title==null||title.trim().isEmpty()||title.trim().equals("<unknown>"))return "Untitled track";
            String cleaned=title.trim().replaceAll("(?i)\\.mp3$","").trim();
            cleaned=cleaned.replaceAll("[\\[(]\\s*"+SOURCE_SITE+"\\s*[\\])]", "");
            cleaned=cleaned.replaceAll("(?:\\s*(?:::|[-–—|])\\s*)?"+SOURCE_SITE+"(?:\\s*::\\s*)?", " ");
            cleaned=cleaned.trim().replaceAll("(?i)\\.mp3$", "").trim();
            return cleaned.isEmpty()?"Untitled track":cleaned;
        }
        private static String normalizedAlbum(String album) {
            return album==null?"":java.text.Normalizer.normalize(album,java.text.Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        }
        static boolean isLooseAlbum(String album) {
            String name=normalizedAlbum(album);
            return name.isEmpty()||name.equals("<unknown>")||name.equals("unknown")||name.equals("unknown album")||name.equals("singles & other songs")||name.equals("other songs")||name.equals("download")||name.equals("downloads");
        }
        static String displayAlbum(String album) { return isLooseAlbum(album)?"Other songs":album.trim(); }
        static String collectionKey(String album,String artist,long albumId) {
            if(isLooseAlbum(album))return "loose:other-songs";
            String name=normalizedAlbum(album);
            return "title:"+name.length()+":"+name;
        }
    }

    private static final String[] FRAGMENTS = {
        "Song", "Radhimma", " ", "  ", "\t", " ", " ", "mp3", ".mp3", ".MP3", ".Mp3", "mP3", "MP", "3",
        ".", "..", "SenSongsMp3.Co", "SenSongsMp3.Com", "www.SenSongsMp3.Com", "sen songs mp3 . co",
        "Sen Songs Mp3 .Co", "sensongsmp3.co", "::", " :: ", " - ", "-", "–", "—", "|", " | ", "(", ")",
        "[", "]", "Live", "Remix", "Part 2", "నువ్వే", "Björk", "🎵", "<unknown>", "ＭＰ３", "Kmp3",
        "İ", "ß", "x", "My", "Com", "co", "m", "unknown", "Unknown Album", "Downloads", "download",
        "Singles & Other Songs", "other songs", "Ｄｏｗｎｌｏａｄ", "é", "Ⅳ", "ﬁ",
    };

    private static String randomText(Random random) {
        StringBuilder b = new StringBuilder();
        int parts = 1 + random.nextInt(7);
        for (int i = 0; i < parts; i++) b.append(FRAGMENTS[random.nextInt(FRAGMENTS.length)]);
        return b.toString();
    }

    public static void main(String[] args) {
        List<String> corpus = new ArrayList<>(Arrays.asList(
            null, "", " ", "<unknown>", " <unknown> ", "Song", "Song.mp3", "Song.MP3 ", "Song.mp3.mp3",
            "Radhimma :: SenSongsMp3.Co", "Radhimma-Sen SongsMp3.Co.mp3", "Song [SenSongsMp3.Co]",
            "Song (www.SenSongsMp3.Com).mp3", "SenSongsMp3.Com :: Song", "Song :: SenSongsMp3.Co :: Live",
            "MySenSongsMp3.ComSong", "Song.mp3 Remix", "SenSongsMp3.Co.mp3", ".mp3", "mp3", "MP3 Love",
            "ＭＰ３", "Song.ＭＰ３", "నువ్వే - నువ్వే.mp3", "Kp3"));
        Random random = new Random(20261105L);
        for (int i = 0; i < 20000; i++) corpus.add(randomText(random));

        for (String s : corpus) {
            check(Legacy.displayTitle(s).equals(MusicRules.displayTitle(s)),
                "displayTitle differs for [" + s + "]: legacy=[" + Legacy.displayTitle(s) + "] new=[" + MusicRules.displayTitle(s) + "]");
            check(Legacy.isLooseAlbum(s) == MusicRules.isLooseAlbum(s), "isLooseAlbum differs for [" + s + "]");
            check(Legacy.collectionKey(s, "Artist", 7).equals(MusicRules.collectionKey(s, "Artist", 7)),
                "collectionKey differs for [" + s + "]");
            if (s != null) check(Legacy.displayAlbum(s).equals(MusicRules.displayAlbum(s)), "displayAlbum differs for [" + s + "]");
        }

        check(MusicRules.containsAsciiIgnoreCase("xMP3y", "mp3"), "upper-case ASCII matches");
        check(MusicRules.containsAsciiIgnoreCase("mp3", "mp3"), "whole string matches");
        check(!MusicRules.containsAsciiIgnoreCase("mp", "mp3"), "shorter text never matches");
        check(!MusicRules.containsAsciiIgnoreCase("", "mp3"), "empty text never matches");
        check(!MusicRules.containsAsciiIgnoreCase("ＭＰ３", "mp3"), "full-width letters are not ASCII (regex (?i) agrees)");
        check(!MusicRules.containsAsciiIgnoreCase("m p3", "mp3"), "needle must be contiguous");

        // Turkish default locale must not change normalisation (toLowerCase(Locale.ROOT)).
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            check(MusicRules.collectionKey("INDIGO NIGHTS", "A", 1).equals(Legacy.collectionKey("INDIGO NIGHTS", "A", 1)),
                "locale-independent album keys");
        } finally {
            Locale.setDefault(old);
        }
        System.out.println("MusicRulesEquivalenceTest: " + checks + " checks passed");
    }
}
