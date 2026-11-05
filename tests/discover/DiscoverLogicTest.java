package dev.lilt.player.screens.discover;

import dev.lilt.player.screens.discover.SearchRanking.Candidate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Formatting, matching, ranking, generations, recents and top artists of the discovery screens. */
public final class DiscoverLogicTest {
    public static void main(String[] args) {
        formatting();
        folding();
        relevance();
        highlight();
        ranking();
        gate();
        recents();
        topArtists();
        System.out.println("DiscoverLogicTest OK");
    }

    static void formatting() {
        eq("0", DiscoverText.compact(0));
        eq("950", DiscoverText.compact(950));
        eq("1K", DiscoverText.compact(1000));
        eq("1.2K", DiscoverText.compact(1299));
        eq("12.4K", DiscoverText.compact(12_480));
        eq("99.9K", DiscoverText.compact(99_999));
        eq("124K", DiscoverText.compact(124_999));
        eq("999K", DiscoverText.compact(999_999));
        eq("1M", DiscoverText.compact(1_000_000));
        eq("1.3M", DiscoverText.compact(1_349_000));
        eq("12.4M", DiscoverText.compact(12_480_000));
        eq("2.1B", DiscoverText.compact(2_150_000_000L));
        eq("", DiscoverText.followers(0));
        eq("1 follower", DiscoverText.followers(1));
        eq("12.4K followers", DiscoverText.followers(12_400));
        eq("48K followers on Audius", DiscoverText.followersOnAudius(48_000));
        eq("", DiscoverText.collection(0, 0));
        eq("Hip-Hop & Rap", DiscoverText.genre("Hip-Hop/Rap"));
        eq("R&B/Soul", DiscoverText.genre("R&B/Soul"));
        eq("Top Songs This Week", DiscoverText.chartTitle("week"));
        eq("Top Songs All Time", DiscoverText.chartTitle("allTime"));
        eq("Song · Kofi Mensah", DiscoverText.kind("Song", "Kofi Mensah"));
        eq("Artist", DiscoverText.kind("Artist", " "));
        eq("A · B", DiscoverText.joined("A", "", null, "B"));
    }

    static void folding() {
        eq("bjork", SearchText.fold("Björk"));
        eq("drum and bass", SearchText.fold("Drum & Bass"));
        eq("hip hop rap", SearchText.fold("  Hip-Hop/Rap! "));
        eq("dont stop", SearchText.fold("Don’t Stop"));
        eq("strasse", SearchText.fold("Straße"));
        eq(Arrays.asList("low", "tide"), SearchText.tokens("Low, Tide"));
    }

    static void relevance() {
        check(SearchText.relevance("low tide", "Low Tide") == SearchText.EXACT, "exact");
        check(SearchText.relevance("LOW TIDE!", "low-tide") == SearchText.EXACT, "exact ignores case/punct");
        check(SearchText.relevance("low ti", "Low Tide Signals") == SearchText.PREFIX, "prefix");
        check(SearchText.relevance("tide sig", "Low Tide Signals") == SearchText.WORDS_IN_ORDER, "words in order");
        check(SearchText.relevance("signals low", "Low Tide Signals") == SearchText.WORDS, "words any order");
        check(SearchText.relevance("ide", "Low Tide") == SearchText.CONTAINS, "substring");
        check(SearchText.relevance("bjork", "Björk") == SearchText.EXACT, "accents");
        check(SearchText.relevance("zzz", "Low Tide") == 0, "no match");
        check(SearchText.relevance("", "Low Tide") == 0, "empty query");
        double partial = SearchText.relevance("low zzz", "Low Tide");
        check(partial > 0 && partial < SearchText.CONTAINS, "partial " + partial);
    }

    static void highlight() {
        eqArr(new int[] {0, 5, 6, 9}, SearchText.highlight("Night Bus Home", "night bus"));
        eqArr(new int[] {0, 5}, SearchText.highlight("Björk", "bjork"));
        // Word start preferred over an earlier substring: "tide" in "Low Tide", not inside "Atidex".
        eqArr(new int[] {4, 8}, SearchText.highlight("Low Tide", "tide"));
        eqArr(new int[] {5, 6}, SearchText.highlight("Drum & Bass", "and"));
        eqArr(new int[0], SearchText.highlight("Low Tide", "zzz"));
        eqArr(new int[] {0, 3}, SearchText.highlight("Lowlands", "low lo")); // overlapping ranges merge
    }

    static void ranking() {
        Candidate<String> artist = new Candidate<>(SearchRanking.ARTIST, "Field Theory", "", false, 48_000, 0, "a1");
        Candidate<String> song = new Candidate<>(SearchRanking.SONG, "Field Theory Blues", "Someone", false, 0, 0, "s1");
        Candidate<String> local = new Candidate<>(SearchRanking.SONG, "Fields", "Field Theory", true, 0, 0, "l1");
        Candidate<String> top = SearchRanking.top("field theory", list(local), list(song), list(artist));
        eq("a1", top.item);
        // Exact song title in the library beats a catalogue prefix match.
        Candidate<String> lib = new Candidate<>(SearchRanking.SONG, "Low Tide", "Field Theory", true, 0, 0, "lib");
        Candidate<String> cat = new Candidate<>(SearchRanking.SONG, "Low Tide (Remix)", "DJ", false, 0, 0, "cat");
        eq("lib", SearchRanking.top("low tide", list(lib), list(cat)).item);
        // "title artist" query finds the song.
        eq("lib", SearchRanking.top("low tide field theory", list(cat), list(lib)).item);
        // Weak matches produce no top result.
        check(SearchRanking.top("zz", list(lib), list(cat)) == null, "no weak top");

        List<Candidate<String>> library = list(
                new Candidate<>(SearchRanking.SONG, "Night Bus Home", "The Quiet Hours", true, 0, 0, "local:1"),
                new Candidate<>(SearchRanking.SONG, "Late Night Static", "Wire & Wool", true, 0, 1, "audius:7"));
        List<Candidate<String>> catalogue = list(
                new Candidate<>(SearchRanking.SONG, "Nightswimming", "Tunde Ade", false, 0, 0, "audius:1"),
                new Candidate<>(SearchRanking.SONG, "Late Night Static", "Wire & Wool", false, 0, 1, "audius:7"), // saved: dup id
                new Candidate<>(SearchRanking.SONG, "NIGHT BUS HOME", "the quiet hours", false, 0, 2, "audius:9"), // dup title+artist
                new Candidate<>(SearchRanking.SONG, "Night", "Low Ceiling", false, 0, 3, "audius:3"));
        List<Candidate<String>> merged = SearchRanking.mergeSongs("night", library, catalogue, s -> s, 10);
        List<String> ids = new ArrayList<>();
        for (Candidate<String> c : merged) ids.add(c.item);
        // exact "Night" first, then prefix matches (library first), then word matches.
        eq(Arrays.asList("audius:3", "local:1", "audius:1", "audius:7"), ids);
        eq(2, SearchRanking.mergeSongs("night", library, catalogue, s -> s, 2).size());
        eq(1, SearchRanking.filter("bus", library, 0.7, 5).size());
    }

    static void gate() {
        QueryGate g = new QueryGate();
        int a = g.begin("low");
        int b = g.begin("low tide");
        check(!g.isCurrent(a) && g.isCurrent(b), "newest wins");
        eq("low tide", g.query());
        check(!g.changes("Low  Tide".replaceAll("\\s+", " ")), "case-only change is not a new query");
        check(g.changes("low tides"), "real change");
        g.cancel();
        check(!g.isCurrent(b), "cancel drops outstanding");
        eq("", g.query());
        eq("a b", QueryGate.normalize("  a \t  b "));
        eq("", QueryGate.normalize(null));
    }

    static void recents() {
        RecentSearches r = new RecentSearches();
        r.add(RecentSearches.Entry.query("monsoon letters"));
        r.add(new RecentSearches.Entry(RecentSearches.ARTIST, "nlGNe", "Marlow Fields", "Artist", "https://x/150x150.jpg", ""));
        r.add(RecentSearches.Entry.query("Monsoon Letters")); // case-insensitive duplicate moves to front
        eq(2, r.size());
        eq("Monsoon Letters", r.entries().get(0).title);
        r.add(RecentSearches.Entry.query("   "));
        eq(2, r.size());
        for (int i = 0; i < 20; i++) r.add(RecentSearches.Entry.query("q" + i));
        eq(RecentSearches.MAX, r.size());
        eq("q19", r.entries().get(0).key);
        r.clear();
        r.add(new RecentSearches.Entry(RecentSearches.SONG, "audius:1", "Tab\tTitle", "Line\nTwo \\ end", null, "{\"a\":1}"));
        r.add(RecentSearches.Entry.query("x"));
        RecentSearches back = RecentSearches.decode(r.encode());
        eq(2, back.size());
        RecentSearches.Entry song = back.entries().get(1);
        eq("Tab\tTitle", song.title);
        eq("Line\nTwo \\ end", song.subtitle);
        eq("{\"a\":1}", song.payload);
        check(song.artworkUrl == null, "null art round-trips");
        check(back.remove(RecentSearches.SONG, "audius:1") && back.size() == 1, "remove");
        eq(0, RecentSearches.decode("garbage\nquery\tx").size());
        eq(0, RecentSearches.decode(null).size());
    }

    static void topArtists() {
        List<String> ids = Arrays.asList("a", "b", "", "b", "c", "b", "");
        List<String> names = Arrays.asList("Ana", "Bo", "Cy", "Bo", "Cee", "Bo", "cy");
        List<TopArtists.Entry> top = TopArtists.fromChart(ids, names, 3);
        eq(3, top.size());
        eq("Bo", top.get(0).name);   // three charting songs
        eq(3, top.get(0).songs);
        eq(1, top.get(0).bestIndex);
        eq("Ana", top.get(1).name);  // rank 1
        eq("Cy", top.get(2).name);   // two entries without id merged by name
        eq(2, top.get(2).songs);
        eq(0, TopArtists.fromChart(Arrays.asList("x"), Arrays.asList("Unknown artist"), 5).size());
    }

    @SafeVarargs static <T> List<T> list(T... items) { return new ArrayList<>(Arrays.asList(items)); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }
    static void eqArr(int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) throw new AssertionError("expected " + Arrays.toString(expected) + " but was " + Arrays.toString(actual));
    }
}
