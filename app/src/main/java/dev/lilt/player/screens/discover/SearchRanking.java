package dev.lilt.player.screens.discover;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges and ranks search results from the library and the Audius catalogue, and picks the top
 * result. Works on lightweight {@link Candidate}s so it stays pure Java; the screen maps them
 * back to tracks, artists and playlists.
 */
public final class SearchRanking {
    private SearchRanking() {}

    public static final int SONG = 0, ARTIST = 1, PLAYLIST = 2, ALBUM = 3;

    /** One result. {@code detail} is the artist (songs, albums) or owner (playlists). */
    public static final class Candidate<T> {
        public final int kind;
        public final String title, detail;
        /** From the user's library (on this phone or saved). */
        public final boolean library;
        /** Followers (artists), 0 when unknown. */
        public final long popularity;
        /** Position in its source list (the API's own relevance order). */
        public final int order;
        public final T item;

        public Candidate(int kind, String title, String detail, boolean library, long popularity, int order, T item) {
            this.kind = kind;
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
            this.library = library;
            this.popularity = Math.max(0, popularity);
            this.order = order;
            this.item = item;
        }
    }

    /** Text relevance of a candidate: titles count most; "title artist" queries also match songs. */
    public static double relevance(String query, Candidate<?> c) {
        double r = SearchText.relevance(query, c.title);
        if (c.kind == SONG || c.kind == ALBUM) {
            r = Math.max(r, 0.95 * SearchText.relevance(query, c.title + " " + c.detail));
            r = Math.max(r, 0.6 * SearchText.relevance(query, c.detail));
        } else if (c.kind == PLAYLIST) {
            r = Math.max(r, 0.5 * SearchText.relevance(query, c.detail));
        }
        return r;
    }

    /** Overall score used to choose the top result. */
    public static double score(String query, Candidate<?> c) {
        double r = relevance(query, c);
        if (r <= 0) return 0;
        double s = r;
        if (c.library) s += 0.06; // the user's own music wins ties
        if (c.kind == ARTIST) {
            s += 0.04 * Math.min(1.0, Math.log10(1 + c.popularity) / 6.0); // up to 1M followers
            if (r >= SearchText.EXACT) s += 0.05; // "field theory" → the artist, as in Apple Music
        }
        s -= Math.min(0.05, c.order * 0.005); // the API's own order breaks remaining ties
        return s;
    }

    /** The best candidate across all lists when it is a convincing match (relevance ≥ 0.7), else null. */
    @SafeVarargs
    public static <T> Candidate<T> top(String query, List<Candidate<T>>... lists) {
        Candidate<T> best = null;
        double bestScore = 0;
        for (List<Candidate<T>> list : lists) {
            if (list == null) continue;
            for (Candidate<T> c : list) {
                if (relevance(query, c) < 0.7) continue;
                double s = score(query, c);
                if (best == null || s > bestScore + 1e-9) { best = c; bestScore = s; }
            }
        }
        return best;
    }

    /** Identity of a song across sources: folded title + artist. */
    public static String songKey(String title, String artist) {
        return SearchText.fold(title).replace(" ", "") + "|" + SearchText.fold(artist).replace(" ", "");
    }

    /**
     * Songs from the library and the catalogue as one list: grouped by relevance bucket (exact,
     * prefix, all words, partial), library songs first within a bucket, then source order.
     * Catalogue songs that duplicate a library song (same id or same title + artist) are dropped.
     * {@code ids} gives each candidate's track id (may be null).
     */
    public static <T> List<Candidate<T>> mergeSongs(String query, List<Candidate<T>> library, List<Candidate<T>> catalogue,
                                                    IdOf<T> ids, int limit) {
        List<Candidate<T>> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<Candidate<T>> list : twoLists(library, catalogue)) {
            for (Candidate<T> c : list) {
                String id = ids == null ? null : ids.id(c.item);
                String key = songKey(c.title, c.detail);
                if ((id != null && !seen.add("id:" + id)) | !seen.add("key:" + key)) continue;
                all.add(c);
            }
        }
        List<Candidate<T>> ranked = new ArrayList<>(all);
        Collections.sort(ranked, (a, b) -> {
            int ba = bucket(relevance(query, a)), bb = bucket(relevance(query, b));
            if (ba != bb) return Integer.compare(bb, ba);
            if (a.library != b.library) return a.library ? -1 : 1;
            return Integer.compare(a.order, b.order);
        });
        return ranked.size() > limit ? new ArrayList<>(ranked.subList(0, Math.max(0, limit))) : ranked;
    }

    /** Track id of an item for de-duplication. */
    public interface IdOf<T> { String id(T item); }

    /** Candidates whose relevance reaches {@code min}, in their original order (library lists). */
    public static <T> List<Candidate<T>> filter(String query, List<Candidate<T>> list, double min, int limit) {
        List<Candidate<T>> out = new ArrayList<>();
        for (Candidate<T> c : list) {
            if (out.size() >= limit) break;
            if (relevance(query, c) >= min) out.add(c);
        }
        return out;
    }

    /** Coarse relevance bucket: 4 exact, 3 prefix, 2 words, 1 contains, 0 weaker. */
    static int bucket(double r) {
        if (r >= SearchText.EXACT - 1e-9) return 4;
        if (r >= SearchText.PREFIX * 0.95 - 1e-9) return 3;
        if (r >= SearchText.WORDS * 0.95 - 1e-9) return 2;
        if (r >= SearchText.CONTAINS * 0.95 - 1e-9) return 1;
        return 0;
    }

    private static <T> List<List<Candidate<T>>> twoLists(List<Candidate<T>> a, List<Candidate<T>> b) {
        List<List<Candidate<T>>> out = new ArrayList<>(2);
        out.add(a == null ? Collections.<Candidate<T>>emptyList() : a);
        out.add(b == null ? Collections.<Candidate<T>>emptyList() : b);
        return out;
    }
}
