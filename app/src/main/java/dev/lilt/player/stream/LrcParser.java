package dev.lilt.player.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for LRC synced lyrics ({@code [mm:ss.xx] text}). Handles several timestamps on one line,
 * {@code [mm:ss]}, {@code [mm:ss.x]}, {@code [mm:ss.xxx]} and {@code [mm:ss:xx]} stamps, the
 * {@code [offset:±ms]} tag, metadata tags ({@code [ar:]}, {@code [ti:]}...), blank timed lines
 * (kept as instrumental gaps), enhanced word stamps ({@code <mm:ss.xx>}, removed), a UTF-8 BOM and
 * CR/LF endings. Pure Java.
 */
public final class LrcParser {
    private LrcParser() {}

    private static final Pattern TIME = Pattern.compile("(\\d{1,3}):(\\d{1,2})(?:[.:,](\\d{1,3}))?");
    private static final Pattern WORD_STAMP = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:,]\\d{1,3})?>");
    private static final Pattern OFFSET = Pattern.compile("(?i)offset\\s*:\\s*([+-]?\\d{1,7})\\s*");
    private static final Set<String> METADATA_KEYS = new HashSet<>(Arrays.asList(
            "ar", "al", "ti", "au", "by", "offset", "length", "re", "ve", "tool", "la", "lang", "id", "lr", "kana"));

    /** Synced lines sorted by time; empty when the text has no timestamps. */
    public static List<Lyrics.Line> parse(String lrc) {
        return scan(lrc).lines;
    }

    /**
     * True when the text is meaningfully time-synced: at least two timed text lines, and timed lines
     * are not outnumbered by untimed text lines (plain lyrics with a stray stamp stay plain).
     */
    public static boolean looksSynced(String text) {
        Scan scan = scan(text);
        return scan.timedTextLines >= 2 && scan.timedTextLines >= scan.untimedTextLines;
    }

    /** The lyrics text with stamps and metadata tags removed; runs of blank lines collapse to one. */
    public static String plainText(String lrc) {
        if (lrc == null || lrc.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        for (String raw : lines(lrc)) {
            Parsed parsed = parseLine(raw);
            if (parsed.metadata) continue;
            if (parsed.text.isEmpty()) {
                if (!out.isEmpty() && !out.get(out.size() - 1).isEmpty()) out.add("");
            } else out.add(parsed.text);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return String.join("\n", out);
    }

    /** Parses one bracket body such as {@code 01:02.34}; returns -1 when it is not a timestamp. */
    static long time(String tag) {
        Matcher m = TIME.matcher(tag.trim());
        if (!m.matches()) return -1;
        long minutes = Long.parseLong(m.group(1));
        long seconds = Long.parseLong(m.group(2));
        if (seconds >= 60) return -1;
        long fraction = 0;
        String digits = m.group(3);
        if (digits != null) {
            fraction = Long.parseLong(digits);
            if (digits.length() == 1) fraction *= 100;
            else if (digits.length() == 2) fraction *= 10;
        }
        return (minutes * 60 + seconds) * 1000 + fraction;
    }

    private static final class Parsed {
        final List<Long> times = new ArrayList<>();
        boolean metadata, hasOffset;
        long offset;
        String text = "";
    }

    private static final class Scan {
        final List<Lyrics.Line> lines = new ArrayList<>();
        int timedTextLines, untimedTextLines;
    }

    private static Scan scan(String lrc) {
        Scan scan = new Scan();
        if (lrc == null || lrc.isEmpty()) return scan;
        long offset = 0;
        List<Long> stamps = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (String raw : lines(lrc)) {
            Parsed parsed = parseLine(raw);
            if (parsed.hasOffset) offset = parsed.offset;
            if (parsed.metadata) continue;
            if (parsed.times.isEmpty()) {
                if (!parsed.text.isEmpty()) scan.untimedTextLines++;
                continue;
            }
            if (!parsed.text.isEmpty()) scan.timedTextLines++;
            for (long t : parsed.times) { stamps.add(t); texts.add(parsed.text); }
        }
        // A positive offset shows lyrics earlier (LRC convention), so it is subtracted.
        for (int k = 0; k < stamps.size(); k++) scan.lines.add(new Lyrics.Line(Math.max(0, stamps.get(k) - offset), texts.get(k)));
        Collections.sort(scan.lines, (a, b) -> Long.compare(a.timeMs, b.timeMs)); // stable for equal stamps
        return scan;
    }

    private static Parsed parseLine(String line) {
        Parsed parsed = new Parsed();
        int i = 0, length = line.length();
        while (i < length && Character.isWhitespace(line.charAt(i))) i++;
        while (i < length && line.charAt(i) == '[') {
            int close = line.indexOf(']', i + 1);
            if (close < 0) break;
            String tag = line.substring(i + 1, close);
            long t = time(tag);
            if (t >= 0) {
                parsed.times.add(t);
                i = close + 1;
                // Tolerate "[00:01.00] [00:05.00] text".
                int next = i;
                while (next < length && line.charAt(next) == ' ') next++;
                if (next < length && line.charAt(next) == '[' && time(tagAt(line, next)) >= 0) i = next;
                continue;
            }
            if (parsed.times.isEmpty() && isMetadataTag(tag)) {
                parsed.metadata = true;
                Matcher offset = OFFSET.matcher(tag.trim());
                if (offset.matches()) {
                    parsed.hasOffset = true;
                    parsed.offset = Long.parseLong(offset.group(1).replace("+", ""));
                }
                return parsed;
            }
            break;
        }
        String text = WORD_STAMP.matcher(line.substring(Math.min(i, length))).replaceAll("");
        parsed.text = text.replace(' ', ' ').trim().replaceAll("\\s{2,}", " ");
        return parsed;
    }

    private static String tagAt(String line, int open) {
        int close = line.indexOf(']', open + 1);
        return close < 0 ? "" : line.substring(open + 1, close);
    }

    /** Known LRC header tags ({@code [ar:...]}, {@code [offset:...]}) and {@code [#...]} comments. */
    private static boolean isMetadataTag(String tag) {
        String trimmed = tag.trim();
        if (trimmed.startsWith("#")) return true;
        int colon = trimmed.indexOf(':');
        return colon > 0 && METADATA_KEYS.contains(trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT));
    }

    private static String[] lines(String text) {
        String normalized = text;
        if (!normalized.isEmpty() && normalized.charAt(0) == '﻿') normalized = normalized.substring(1);
        return normalized.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }
}
