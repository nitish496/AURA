package dev.lilt.player.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable lyrics for one track: optional time-synced lines plus a plain-text version.
 * Pure Java so it can be unit-tested off-device.
 */
public final class Lyrics {
    /** Lyrics read from tags inside the local audio file (ID3 USLT/SYLT or FLAC Vorbis comments). */
    public static final String SOURCE_EMBEDDED = "embedded";
    /** Lyrics fetched from lrclib.net. */
    public static final String SOURCE_LRCLIB = "lrclib";
    /** Shared "nothing found" value; {@link #isEmpty()} is true. */
    public static final Lyrics NONE = new Lyrics(Collections.<Line>emptyList(), "", false, "");

    /** One synced line. {@code text} may be "" for an instrumental gap. */
    public static final class Line {
        public final long timeMs;
        public final String text;
        public Line(long timeMs, String text) {
            this.timeMs = Math.max(0, timeMs);
            this.text = text == null ? "" : text;
        }
        @Override public String toString() { return timeMs + ":" + text; }
    }

    /** Synced lines sorted by time; empty when only plain lyrics are known. */
    public final List<Line> lines;
    /** Plain lyrics; derived from the synced lines when the source has no plain text. "" when unknown. */
    public final String plain;
    /** True when the source marks the track as instrumental (no lyrics expected). */
    public final boolean instrumental;
    /** {@link #SOURCE_EMBEDDED}, {@link #SOURCE_LRCLIB} or "" for {@link #NONE}. */
    public final String source;

    public Lyrics(List<Line> lines, String plain) { this(lines, plain, false, ""); }

    public Lyrics(List<Line> lines, String plain, boolean instrumental, String source) {
        List<Line> sorted = new ArrayList<>(lines == null ? Collections.<Line>emptyList() : lines);
        // Stable sort keeps the source order of lines sharing a timestamp.
        Collections.sort(sorted, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        this.lines = Collections.unmodifiableList(sorted);
        String text = plain == null ? "" : plain.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isEmpty() && !sorted.isEmpty()) text = joined(sorted);
        this.plain = text;
        this.instrumental = instrumental;
        this.source = source == null ? "" : source;
    }

    /** True when there are synced lines to follow during playback. */
    public boolean synced() { return !lines.isEmpty(); }

    /** True when neither synced nor plain lyrics are available. */
    public boolean isEmpty() { return lines.isEmpty() && plain.isEmpty(); }

    /**
     * Index of the line active at {@code positionMs}: the last line whose time is at or before the
     * position. Returns -1 before the first line or when the lyrics are not synced. O(log n).
     */
    public int indexAt(long positionMs) {
        int low = 0, high = lines.size() - 1, found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMs <= positionMs) { found = middle; low = middle + 1; }
            else high = middle - 1;
        }
        return found;
    }

    /** Copy carrying a different source label. */
    Lyrics withSource(String newSource) {
        return new Lyrics(lines, plain, instrumental, newSource);
    }

    private static String joined(List<Line> lines) {
        StringBuilder text = new StringBuilder();
        String previous = null;
        for (Line line : lines) {
            String value = line.text.trim();
            // Consecutive blank (instrumental) markers collapse into one paragraph break.
            if (value.isEmpty() && (previous == null || previous.isEmpty())) { previous = value; continue; }
            if (text.length() > 0) text.append('\n');
            text.append(value);
            previous = value;
        }
        return text.toString().trim();
    }
}
