package dev.lilt.player.stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.Deflater;

/**
 * Embedded lyrics: real mutagen-written tags (the CI demo-library shape) and synthetic byte
 * fixtures for each ID3v2.2/2.3/2.4 and FLAC code path.
 */
public final class EmbeddedLyricsTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static void equal(Object expected, Object actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected <" + expected + "> but was <" + actual + ">");
        checks++;
    }

    private static final String LRC = "[00:00.80]Salt on the window, morning on the line\n[00:05.20]Counting the ferries as they cross the bay\n"
            + "[00:09.70]\n[00:11.30]Every lantern answers in its own time\n[00:16.05]We keep the small light burning anyway";
    private static final String PLAIN = "Salt on the window, morning on the line\nCounting the ferries as they cross the bay\n\nEvery lantern answers in its own time";

    // ---- byte builders ------------------------------------------------------------------------
    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }
    private static byte[] b(int... values) { byte[] out = new byte[values.length]; for (int i = 0; i < values.length; i++) out[i] = (byte) values[i]; return out; }
    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }
    private static byte[] syncsafe(int n) { return b((n >> 21) & 0x7f, (n >> 14) & 0x7f, (n >> 7) & 0x7f, n & 0x7f); }
    private static byte[] be32(int n) { return b(n >>> 24, (n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff); }
    private static byte[] le32(int n) { return b(n & 0xff, (n >> 8) & 0xff, (n >> 16) & 0xff, n >>> 24); }
    /** Text in an ID3 encoding (1 = UTF-16 with BOM, little-endian as Windows taggers write it). */
    private static byte[] text(String s, int encoding) {
        switch (encoding) {
            case 0: return s.getBytes(StandardCharsets.ISO_8859_1);
            case 1: return cat(b(0xff, 0xfe), s.getBytes(StandardCharsets.UTF_16LE));
            case 2: return s.getBytes(StandardCharsets.UTF_16BE);
            default: return s.getBytes(StandardCharsets.UTF_8);
        }
    }
    private static byte[] term(int encoding) { return encoding == 1 || encoding == 2 ? b(0, 0) : b(0); }
    private static byte[] usltBody(int encoding, String descriptor, String lyrics) {
        return cat(b(encoding), ascii("eng"), text(descriptor, encoding), term(encoding), text(lyrics, encoding));
    }
    private static byte[] syltBody(int encoding, int format, String[] texts, int[] times) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] head = cat(b(encoding), ascii("eng"), b(format, 1), text("", encoding), term(encoding));
        out.write(head, 0, head.length);
        for (int i = 0; i < texts.length; i++) { byte[] entry = cat(text(texts[i], encoding), term(encoding), be32(times[i])); out.write(entry, 0, entry.length); }
        return out.toByteArray();
    }
    private static byte[] frame(int version, String id, byte[] body, int formatFlags) {
        return cat(ascii(id), version == 4 ? syncsafe(body.length) : be32(body.length), b(0, formatFlags), body);
    }
    private static byte[] frame(int version, String id, byte[] body) { return frame(version, id, body, 0); }
    private static byte[] tag(int version, int flags, byte[] content) {
        return cat(ascii("ID3"), b(version, 0, flags), syncsafe(content.length), content);
    }
    private static byte[] withAudio(byte[] tag) { return cat(tag, b(0xff, 0xfb, 0x90, 0x64), new byte[400]); }
    private static byte[] unsync(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            out.write(data[i]);
            if ((data[i] & 0xff) == 0xff && (i + 1 == data.length || (data[i + 1] & 0xff) >= 0xe0 || data[i + 1] == 0)) out.write(0);
        }
        return out.toByteArray();
    }
    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[data.length + 64];
        int length = deflater.deflate(buffer);
        deflater.end();
        byte[] out = new byte[length];
        System.arraycopy(buffer, 0, out, 0, length);
        return out;
    }
    private static Lyrics read(byte[] file) throws IOException { return EmbeddedLyrics.read(new ByteArrayInputStream(file)); }
    private static void synced(Lyrics lyrics, String name) {
        check(lyrics.synced(), name + ": synced");
        equal(5, lyrics.lines.size(), name + ": line count");
        equal(800L, lyrics.lines.get(0).timeMs, name + ": first stamp");
        equal("Salt on the window, morning on the line", lyrics.lines.get(0).text, name + ": first line");
        equal("", lyrics.lines.get(2).text, name + ": instrumental gap");
        equal("We keep the small light burning anyway", lyrics.lines.get(4).text, name + ": last line");
        equal(Lyrics.SOURCE_EMBEDDED, lyrics.source, name + ": source");
    }

    /** Counts bytes actually read (not skipped). */
    static final class Counting extends FilterInputStream {
        long read;
        Counting(InputStream in) { super(in); }
        @Override public int read() throws IOException { int v = super.read(); if (v >= 0) read++; return v; }
        @Override public int read(byte[] b, int off, int len) throws IOException { int n = super.read(b, off, len); if (n > 0) read += n; return n; }
    }

    public static void main(String[] args) throws Exception {
        // Real mutagen output (CI demo shape: ID3v2.4, UTF-8 USLT, lang "eng", empty descriptor, LRC text).
        synced(read(Files.readAllBytes(Paths.get("tests/fixtures/embedded/mutagen_uslt_v24.mp3"))), "mutagen v2.4 USLT");
        Lyrics utf16 = read(Files.readAllBytes(Paths.get("tests/fixtures/embedded/mutagen_uslt_v23_utf16.mp3")));
        check(!utf16.synced(), "plain USLT stays unsynced");
        equal(PLAIN + "\nÉté — déjà vu", utf16.plain, "mutagen v2.3 UTF-16 USLT");
        Lyrics sylt = read(Files.readAllBytes(Paths.get("tests/fixtures/embedded/mutagen_sylt_v24.mp3")));
        equal(3, sylt.lines.size(), "mutagen SYLT lines");
        equal(4_800L, sylt.lines.get(1).timeMs, "SYLT millisecond stamp");
        equal("Engines hum a lower key", sylt.lines.get(1).text, "SYLT text");
        Lyrics flac = read(Files.readAllBytes(Paths.get("tests/fixtures/embedded/mutagen_vorbis.flac")));
        synced(flac, "mutagen FLAC LYRICS");
        equal(PLAIN, flac.plain, "UNSYNCEDLYRICS supplies the plain text");

        // ID3v2.4 synthetic, every encoding.
        for (int encoding = 0; encoding <= 3; encoding++) {
            byte[] file = withAudio(tag(4, 0, cat(frame(4, "TIT2", cat(b(3), ascii("Paper Lanterns"))), frame(4, "USLT", usltBody(encoding, "", LRC)), new byte[64])));
            synced(read(file), "v2.4 encoding " + encoding);
            synced(EmbeddedLyrics.parse(file), "parse(byte[]) encoding " + encoding);
        }
        // UTF-16 strings whose own BOM is missing inherit the byte order of the descriptor's BOM.
        byte[] noBom = cat(b(1), ascii("eng"), b(0xfe, 0xff), b(0, 0), PLAIN.getBytes(StandardCharsets.UTF_16BE));
        equal(PLAIN, read(tag(3, 0, frame(3, "USLT", noBom))).plain, "UTF-16 byte order carried from the descriptor BOM");

        // ID3v2.3 with tag-level unsynchronisation (UTF-16 BOM FF FE forces FF 00 FE).
        byte[] v23frames = cat(frame(3, "TIT2", cat(b(1), text("Glasshouse", 1), b(0, 0))), frame(3, "USLT", usltBody(1, "", PLAIN)));
        byte[] unsynced = unsync(v23frames);
        check(unsynced.length > v23frames.length, "fixture really needed unsynchronisation");
        equal(PLAIN, read(withAudio(tag(3, 0x80, unsynced))).plain, "v2.3 tag-level unsynchronisation");
        // ID3v2.3 extended header (size excludes itself: 6 = flags + padding size).
        byte[] ext23 = cat(be32(6), b(0, 0), be32(0));
        equal(PLAIN, read(tag(3, 0x40, cat(ext23, frame(3, "USLT", usltBody(0, "", PLAIN))))).plain, "v2.3 extended header");
        // ID3v2.4 extended header (syncsafe size includes itself).
        byte[] ext24 = cat(syncsafe(6), b(1, 0));
        synced(read(tag(4, 0x40, cat(ext24, frame(4, "USLT", usltBody(3, "", LRC))))), "v2.4 extended header");
        // ID3v2.4 frame-level unsynchronisation + data length indicator.
        byte[] body16 = usltBody(1, "", PLAIN);
        byte[] flagged = cat(syncsafe(body16.length), unsync(body16));
        equal(PLAIN, read(tag(4, 0, frame(4, "USLT", flagged, 0x03))).plain, "v2.4 frame unsync + data length");
        // Tag-level flag in v2.4 means every frame is unsynchronised.
        equal(PLAIN, read(tag(4, 0x80, frame(4, "USLT", unsync(body16)))).plain, "v2.4 tag-level unsync");
        // Compressed frames: v2.4 (DLI + zlib) and v2.3 (decompressed size + zlib).
        byte[] lrcBody = usltBody(3, "", LRC);
        synced(read(tag(4, 0, frame(4, "USLT", cat(syncsafe(lrcBody.length), deflate(lrcBody)), 0x09))), "v2.4 compressed");
        byte[] lrcBody23 = usltBody(0, "", LRC);
        synced(read(tag(3, 0, frame(3, "USLT", cat(be32(lrcBody23.length), deflate(lrcBody23)), 0x80))), "v2.3 compressed");
        // Grouping byte before the data.
        synced(read(tag(4, 0, frame(4, "USLT", cat(b(7), lrcBody), 0x40))), "v2.4 grouping identity");
        // Encrypted frames cannot be read and are skipped.
        check(read(tag(4, 0, frame(4, "USLT", cat(b(1), lrcBody), 0x04))).isEmpty(), "encrypted frame skipped");

        // Legacy iTunes v2.4 tags with plain (non-syncsafe) frame sizes.
        String longTitle = "Overpass Bloom (Extended Night Mix) " + "x".repeat(170);
        byte[] titleBody = cat(b(3), ascii(longTitle));
        byte[] itunes = cat(ascii("TIT2"), be32(titleBody.length), b(0, 0), titleBody, ascii("USLT"), be32(lrcBody.length), b(0, 0), lrcBody);
        synced(read(tag(4, 0, itunes)), "plain-integer v2.4 frame sizes");
        // 300 = 00 00 01 2C has no high bits, so only walking the frames can tell the two readings apart.
        byte[] title300 = cat(b(3), ascii("y".repeat(299)));
        byte[] itunes300 = cat(ascii("TIT2"), be32(title300.length), b(0, 0), title300, ascii("USLT"), be32(lrcBody.length), b(0, 0), lrcBody);
        synced(read(tag(4, 0, itunes300)), "plain-integer sizes without high bits");

        // SYLT: UTF-16BE lines, karaoke syllables, MPEG-frame stamps ignored, chords ignored.
        Lyrics syltBe = read(tag(4, 0, frame(4, "SYLT", syltBody(2, 2, new String[]{"First light", "Second line", "Third"}, new int[]{1000, 2500, 4000}))));
        equal(3, syltBe.lines.size(), "SYLT UTF-16BE");
        equal("Second line", syltBe.lines.get(1).text, "SYLT UTF-16BE text");
        Lyrics karaoke = read(tag(4, 0, frame(4, "SYLT", syltBody(3, 2, new String[]{"First ", "light", "\nSecond ", "line"}, new int[]{1000, 1400, 2500, 2900}))));
        equal(2, karaoke.lines.size(), "syllables join into lines");
        equal("First light", karaoke.lines.get(0).text, "first karaoke line");
        equal(2_500L, karaoke.lines.get(1).timeMs, "line starts at its first syllable");
        byte[] frames = cat(frame(4, "SYLT", syltBody(3, 1, new String[]{"a", "b"}, new int[]{10, 20})), frame(4, "USLT", usltBody(3, "", PLAIN)));
        Lyrics mpegFrames = read(tag(4, 0, frames));
        check(!mpegFrames.synced() && mpegFrames.plain.equals(PLAIN), "MPEG-frame SYLT ignored, USLT used");
        byte[] chords = syltBody(3, 2, new String[]{"Am", "F"}, new int[]{0, 900});
        chords[5] = 5; // content type 5 = chords
        check(read(tag(4, 0, frame(4, "SYLT", chords))).isEmpty(), "chord SYLT is not lyrics");
        // SYLT wins over USLT; USLT then provides the plain text.
        Lyrics both = read(tag(4, 0, cat(frame(4, "USLT", usltBody(3, "", PLAIN)), frame(4, "SYLT", syltBody(3, 2, new String[]{"one", "two"}, new int[]{100, 200})))));
        check(both.synced() && both.plain.equals(PLAIN), "SYLT lines with USLT plain text");
        // Several USLT frames: an LRC one gives the lines, a plain one the text.
        Lyrics pair = read(tag(4, 0, cat(frame(4, "USLT", usltBody(3, "plain", PLAIN)), frame(4, "USLT", usltBody(3, "lrc", LRC)))));
        check(pair.synced() && pair.plain.equals(PLAIN), "LRC and plain USLT frames combined");

        // ID3v2.2 (three-letter frames).
        byte[] v22 = cat(ascii("TT2"), b(0, 0, 6), b(0), ascii("Song"), b(0), ascii("ULT"), b(0, 0, usltBody(0, "", PLAIN).length), usltBody(0, "", PLAIN));
        equal(PLAIN, read(tag(2, 0, v22)).plain, "ID3v2.2 ULT");

        // ISO-8859-1 text decoding.
        equal("Été déjà vu", read(tag(3, 0, frame(3, "USLT", usltBody(0, "", "Été déjà vu")))).plain, "Latin-1 text");

        // Malformed and unrelated input never throws.
        check(read(new byte[0]).isEmpty() && read(ascii("ID")).isEmpty(), "short input");
        check(read(cat(ascii("ID3"), b(5, 0, 0), syncsafe(10), new byte[10])).isEmpty(), "unknown major version");
        check(read(cat(ascii("ID3"), b(4, 0, 0), b(0x80, 0, 0, 10), new byte[10])).isEmpty(), "size bytes must be syncsafe");
        check(read(cat(ascii("ID3"), b(4, 0, 0), syncsafe(5000), frame(4, "USLT", usltBody(3, "", LRC)))).synced(), "truncated tag keeps complete frames");
        check(read(cat(ascii("ID3"), b(4, 0, 0), syncsafe(5000), ascii("USLT"), syncsafe(4000), b(0, 0), usltBody(3, "", LRC))).isEmpty(), "partial frame dropped");
        check(read(cat(b(0xff, 0xfb, 0x90, 0x64), new byte[100])).isEmpty(), "MP3 without a tag");
        check(read(ascii("OggS and more bytes")).isEmpty(), "other containers");
        byte[] junk = new byte[4096];
        new java.util.Random(4).nextBytes(junk);
        System.arraycopy(ascii("ID3"), 0, junk, 0, 3);
        junk[3] = 4; junk[4] = 0; junk[5] = 0; junk[6] = 0; junk[7] = 0; junk[8] = 0x1f; junk[9] = 0x70;
        EmbeddedLyrics.parse(junk); checks++;

        // Never reads more than the 2 MB cap, even when a tag claims more.
        Counting huge = new Counting(new InputStream() { @Override public int read() { return 0; } });
        byte[] header = cat(ascii("ID3"), b(4, 0, 0), syncsafe(200 * 1024 * 1024));
        Counting capped = new Counting(new java.io.SequenceInputStream(new ByteArrayInputStream(header), huge));
        check(EmbeddedLyrics.read(capped).isEmpty(), "oversized empty tag");
        check(capped.read <= 10 + EmbeddedLyrics.MAX_TAG_BYTES, "read bounded by the cap: " + capped.read);

        // FLAC: picture blocks are skipped (not read), Vorbis comments parsed.
        byte[] streaminfo = new byte[34];
        byte[] picture = new byte[3 * 1024 * 1024];
        // An empty comment and a lower-case key must not derail parsing.
        byte[] comments = cat(le32(6), ascii("tester"), le32(3), le32(16), ascii("TITLE=Glasshouse"), le32(0),
                le32(("unsyncedlyrics=" + PLAIN).getBytes(StandardCharsets.UTF_8).length), ("unsyncedlyrics=" + PLAIN).getBytes(StandardCharsets.UTF_8));
        byte[] flacFile = cat(ascii("fLaC"), b(0, 0, 0, 34), streaminfo, b(6), b((picture.length >> 16) & 0xff, (picture.length >> 8) & 0xff, picture.length & 0xff), picture,
                b(0x84), b((comments.length >> 16) & 0xff, (comments.length >> 8) & 0xff, comments.length & 0xff), comments, new byte[100]);
        Counting flacIn = new Counting(new ByteArrayInputStream(flacFile));
        Lyrics flacPlain = EmbeddedLyrics.read(flacIn);
        equal(PLAIN, flacPlain.plain, "lower-case UNSYNCEDLYRICS key");
        check(!flacPlain.synced(), "plain FLAC lyrics");
        check(flacIn.read < 64 * 1024, "picture skipped, not read: " + flacIn.read);
        byte[] lrcComment = ("LYRICS=" + LRC).getBytes(StandardCharsets.UTF_8);
        byte[] lrcBlock = cat(le32(0), le32(1), le32(lrcComment.length), lrcComment);
        synced(read(cat(ascii("fLaC"), b(0x84), b(0, (lrcBlock.length >> 8) & 0xff, lrcBlock.length & 0xff), lrcBlock)), "FLAC LYRICS with LRC");
        check(read(cat(ascii("fLaC"), b(0x84, 0, 1, 0), new byte[10])).isEmpty(), "truncated FLAC block");
        check(read(cat(ascii("fLaC"), b(0x84, 0, 0, 12), le32(500), new byte[8])).isEmpty(), "bad vendor length");
        System.out.println(checks + " embedded lyrics tests passed");
    }
}
