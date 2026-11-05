package dev.lilt.player.stream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** LRC parsing and Lyrics line lookup (pure Java). */
public final class LrcParserTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static void equal(Object expected, Object actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected <" + expected + "> but was <" + actual + ">");
        checks++;
    }

    public static void main(String[] args) throws Exception {
        String tricky = new String(Files.readAllBytes(Paths.get("tests/fixtures/lrclib/tricky.lrc")), StandardCharsets.UTF_8);
        List<Lyrics.Line> lines = LrcParser.parse(tricky);
        // offset:+250 shifts every stamp 250 ms earlier; [00:00.00] clamps at zero.
        long[] times = new long[lines.size()];
        for (int i = 0; i < times.length; i++) times[i] = lines.get(i).timeMs;
        equal(Arrays.toString(new long[]{0, 12150, 16800, 21650, 23050, 28500, 33750, 39750, 62150}), Arrays.toString(times), "stamps, offset, multi-stamp lines, sorting");
        equal("Salt on the window, morning on the line", lines.get(1).text, "word stamps removed, BOM ignored");
        equal(lines.get(1).text, lines.get(8).text, "second stamp of a line repeats its text");
        equal("", lines.get(3).text, "blank timed line kept as a gap");
        equal("Every lantern answers in its own time", lines.get(4).text, "three-digit milliseconds");
        equal(28500L, lines.get(5).timeMs, "[mm:ss:cc] colon fraction");
        equal("Hold on", lines.get(6).text, "stamps separated by a space");
        equal("Hold on", lines.get(7).text, "[mm:ss] without fraction");
        check(LrcParser.looksSynced(tricky), "fixture is synced");
        String plain = LrcParser.plainText(tricky);
        check(plain.startsWith("Salt on the window, morning on the line\nCounting the ferries"), "plain text drops metadata: " + plain);
        check(plain.contains("bay\n\nEvery lantern"), "blank stamp becomes one paragraph break");
        check(!plain.contains("[ti:") && !plain.contains("offset") && !plain.contains("<00:"), "plain text has no tags");
        check(plain.contains("[Chorus]") && plain.contains("[99:99.99] invalid stamp stays text"), "non-stamp brackets are text");

        equal(-1L, LrcParser.time("99:99.99"), "seconds over 59 rejected");
        equal(83_450L, LrcParser.time("1:23.45"), "single-digit minutes");
        equal(83_400L, LrcParser.time("01:23.4"), "deciseconds");
        equal(83_456L, LrcParser.time("01:23.456"), "milliseconds");
        equal(6_000_000L, LrcParser.time("100:00"), "long tracks");
        equal(-1L, LrcParser.time("ar:Someone"), "metadata is not a time");

        // Negative offsets delay lines.
        List<Lyrics.Line> later = LrcParser.parse("[offset:-500]\n[00:01.00]one\n[00:02.00]two");
        equal(1500L, later.get(0).timeMs, "negative offset delays");
        // Plain lyrics with Genius-style section headers are not synced.
        check(!LrcParser.looksSynced("[Verse 1]\nLine one\nLine two\n[Chorus: Ada North]\nLine three"), "section headers are not stamps");
        check(!LrcParser.looksSynced("[00:01.00]Only one stamp\nthen\nplain\nlines"), "one stray stamp is not synced");
        check(LrcParser.looksSynced("[00:01.00]a\n[00:02.00]b"), "two stamped lines are synced");
        check(LrcParser.parse(null).isEmpty() && LrcParser.parse("").isEmpty() && LrcParser.plainText(null).isEmpty(), "null and empty input");
        check(LrcParser.parse("no stamps here\nat all").isEmpty(), "untimed text has no synced lines");
        equal("[Chorus: Ada North]\nLine", LrcParser.plainText("[Chorus: Ada North]\nLine"), "colon headers outside the LRC key set are kept");
        List<Lyrics.Line> stable = LrcParser.parse("[00:05.00]first\n[00:05.00]second\n[00:01.00]zero");
        equal("zero,first,second", stable.get(0).text + "," + stable.get(1).text + "," + stable.get(2).text, "equal stamps keep file order");

        // Lyrics: binary-search line lookup and derived plain text.
        Lyrics lyrics = new Lyrics(lines, "");
        check(lyrics.synced() && !lyrics.isEmpty(), "synced lyrics");
        equal(-1, new Lyrics(LrcParser.parse("[00:01.00]a\n[00:02.00]b"), "").indexAt(999), "before the first line");
        equal(0, lyrics.indexAt(0), "first line at zero");
        equal(1, lyrics.indexAt(12_150), "exact stamp is active");
        equal(1, lyrics.indexAt(16_799), "just before the next stamp");
        equal(2, lyrics.indexAt(16_800), "next stamp");
        equal(8, lyrics.indexAt(10_000_000), "after the last line");
        check(lyrics.plain.startsWith("Salt on the window"), "plain derived from lines when missing");
        check(!lyrics.plain.startsWith("\n"), "leading gap does not create a blank first line");
        Lyrics unsynced = new Lyrics(null, "  just words \r\n here ");
        check(!unsynced.synced() && unsynced.indexAt(5000) == -1 && unsynced.plain.equals("just words \n here"), "plain-only lyrics");
        check(Lyrics.NONE.isEmpty() && !Lyrics.NONE.synced() && Lyrics.NONE.source.isEmpty(), "NONE");
        try { lyrics.lines.clear(); throw new AssertionError("lines must be immutable"); } catch (UnsupportedOperationException expected) { checks++; }
        System.out.println(checks + " LRC parsing tests passed");
    }
}
