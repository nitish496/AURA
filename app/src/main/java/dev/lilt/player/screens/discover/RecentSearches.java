package dev.lilt.player.screens.discover;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Recently searched items, newest first: typed queries and the songs, artists, playlists and
 * albums the user opened from results. Bounded, de-duplicated, with a compact line-based text
 * encoding for SharedPreferences. Not thread-safe (main thread). Pure Java.
 */
public final class RecentSearches {
    public static final int MAX = 12;
    public static final String QUERY = "query", SONG = "song", ARTIST = "artist", PLAYLIST = "playlist";
    private static final String HEADER = "aura-recents-v1";

    /** One entry. {@code payload} is opaque data the screen needs to reopen it (e.g. JSON). */
    public static final class Entry {
        public final String kind, key, title, subtitle, artworkUrl, payload;
        public Entry(String kind, String key, String title, String subtitle, String artworkUrl, String payload) {
            this.kind = kind == null ? QUERY : kind;
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.artworkUrl = artworkUrl == null || artworkUrl.isEmpty() ? null : artworkUrl;
            this.payload = payload == null ? "" : payload;
        }
        public static Entry query(String text) { return new Entry(QUERY, text, text, "", null, ""); }
        boolean same(String otherKind, String otherKey) {
            return kind.equals(otherKind) && (QUERY.equals(kind) ? key.toLowerCase(Locale.ROOT).equals(otherKey.toLowerCase(Locale.ROOT)) : key.equals(otherKey));
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() { return Collections.unmodifiableList(new ArrayList<>(entries)); }
    public int size() { return entries.size(); }
    public boolean isEmpty() { return entries.isEmpty(); }

    /** Adds or moves {@code e} to the front; blank queries are ignored. */
    public void add(Entry e) {
        if (e == null || e.key.trim().isEmpty()) return;
        remove(e.kind, e.key);
        entries.add(0, e);
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
    }

    public boolean remove(String kind, String key) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).same(kind, key == null ? "" : key)) { entries.remove(i); return true; }
        return false;
    }

    public void clear() { entries.clear(); }

    public String encode() {
        StringBuilder b = new StringBuilder(HEADER);
        for (Entry e : entries) {
            b.append('\n').append(esc(e.kind)).append('\t').append(esc(e.key)).append('\t').append(esc(e.title)).append('\t')
                    .append(esc(e.subtitle)).append('\t').append(esc(e.artworkUrl == null ? "" : e.artworkUrl)).append('\t').append(esc(e.payload));
        }
        return b.toString();
    }

    /** Restores {@link #encode()} output; unknown or damaged input yields an empty list, never an exception. */
    public static RecentSearches decode(String text) {
        RecentSearches r = new RecentSearches();
        if (text == null) return r;
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) return r;
        for (int i = 1; i < lines.length && r.entries.size() < MAX; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length != 6) continue;
            Entry e = new Entry(unesc(f[0]), unesc(f[1]), unesc(f[2]), unesc(f[3]), unesc(f[4]), unesc(f[5]));
            if (e.key.trim().isEmpty()) continue;
            boolean dup = false;
            for (Entry existing : r.entries) if (existing.same(e.kind, e.key)) { dup = true; break; }
            if (!dup) r.entries.add(e);
        }
        return r;
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\t') b.append("\\t");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else b.append(c);
        }
        return b.toString();
    }

    private static String unesc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { b.append(c); continue; }
            char n = s.charAt(++i);
            b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
        }
        return b.toString();
    }
}
