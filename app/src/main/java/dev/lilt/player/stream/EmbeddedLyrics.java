package dev.lilt.player.stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads lyrics embedded in local audio files. Pure Java (no Android types); the caller opens the
 * stream ({@code ContentResolver.openInputStream}) on a worker thread.
 *
 * <ul>
 *   <li>MP3: ID3v2.2/2.3/2.4 at the start of the file. {@code USLT}/{@code ULT} (unsynced text; a
 *   text holding LRC {@code [mm:ss.xx]} stamps is treated as synced) and {@code SYLT}/{@code SLT}
 *   (synced, millisecond timestamps). Text encodings 0–3, tag- and frame-level unsynchronisation,
 *   extended headers, syncsafe (and legacy iTunes plain) v2.4 frame sizes, zlib-compressed frames.</li>
 *   <li>FLAC: Vorbis comments {@code LYRICS} / {@code UNSYNCEDLYRICS} (LRC text is treated as synced).</li>
 * </ul>
 *
 * Only the tag is read, never more than {@link #MAX_TAG_BYTES}; FLAC picture blocks are skipped
 * without being read. Malformed data yields {@link Lyrics#NONE} rather than an exception.
 */
public final class EmbeddedLyrics {
    private EmbeddedLyrics() {}

    /** Upper bound on tag bytes held in memory. */
    public static final int MAX_TAG_BYTES = 2 * 1024 * 1024;
    private static final long MAX_FLAC_SKIP = 64L * 1024 * 1024;
    private static final Charset UTF_16LE = StandardCharsets.UTF_16LE, UTF_16BE = StandardCharsets.UTF_16BE;

    /** Lyrics from the start of an audio stream, or {@link Lyrics#NONE}. Does not close the stream. */
    public static Lyrics read(InputStream in) throws IOException {
        byte[] magic = readUpTo(in, 4);
        if (magic.length < 4) return Lyrics.NONE;
        if (magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') return id3(in, magic);
        if (magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C') return flac(in);
        return Lyrics.NONE;
    }

    /** {@link #read(InputStream)} over an in-memory file prefix. */
    public static Lyrics parse(byte[] data) {
        try { return read(new ByteArrayInputStream(data)); }
        catch (IOException | RuntimeException malformed) { return Lyrics.NONE; }
    }

    // ---- ID3v2 --------------------------------------------------------------------------------

    private static Lyrics id3(InputStream in, byte[] magic) throws IOException {
        byte[] rest = readUpTo(in, 6);
        if (rest.length < 6) return Lyrics.NONE;
        int major = magic[3] & 0xFF, flags = rest[1] & 0xFF;
        if (major < 2 || major > 4 || (rest[2] & 0x80) != 0 || (rest[3] & 0x80) != 0 || (rest[4] & 0x80) != 0 || (rest[5] & 0x80) != 0) {
            return Lyrics.NONE;
        }
        int size = syncsafe(rest, 2);
        byte[] tag = readUpTo(in, Math.min(size, MAX_TAG_BYTES));
        boolean tagUnsync = (flags & 0x80) != 0;
        List<byte[]> plain = new ArrayList<>(), synced = new ArrayList<>();
        if (major == 2) {
            if ((flags & 0x40) != 0) return Lyrics.NONE; // v2.2 compression was never defined
            if (tagUnsync) tag = deunsync(tag);
            frames22(tag, plain, synced);
        } else if (major == 3) {
            if (tagUnsync) tag = deunsync(tag);
            int offset = 0;
            if ((flags & 0x40) != 0 && tag.length >= 4) offset = 4 + int32(tag, 0); // v2.3 size excludes itself
            frames(tag, offset, 3, false, plain, synced);
        } else {
            int offset = 0;
            if ((flags & 0x40) != 0 && tag.length >= 4) {
                int ext = syncsafe(tag, 0); // v2.4 size includes itself
                offset = validFrameAt(tag, ext) || !validFrameAt(tag, 4 + ext) ? ext : 4 + ext;
            }
            frames(tag, offset, 4, tagUnsync, plain, synced);
        }
        return choose(plain, synced);
    }

    private static void frames(byte[] tag, int offset, int major, boolean tagUnsync, List<byte[]> plain, List<byte[]> synced) {
        boolean syncsafeSizes = major == 4 && countFrames(tag, offset, true) >= countFrames(tag, offset, false);
        int pos = Math.max(0, offset);
        while (pos + 10 <= tag.length && validFrameAt(tag, pos)) {
            int size = syncsafeSizes ? syncsafe(tag, pos + 4) : int32(tag, pos + 4);
            int start = pos + 10;
            if (size < 0 || size > tag.length - start) break; // truncated (capped) tag: drop the partial frame
            String id = new String(tag, pos, 4, StandardCharsets.ISO_8859_1);
            if (id.equals("USLT") || id.equals("SYLT")) {
                byte[] body = major == 4 ? body24(tag, start, size, tag[pos + 9] & 0xFF, tagUnsync) : body23(tag, start, size, tag[pos + 9] & 0xFF);
                if (body != null && body.length > 0) (id.equals("USLT") ? plain : synced).add(body);
            }
            pos = start + size;
        }
    }

    private static void frames22(byte[] tag, List<byte[]> plain, List<byte[]> synced) {
        int pos = 0;
        while (pos + 6 <= tag.length) {
            if (!idChar(tag[pos]) || !idChar(tag[pos + 1]) || !idChar(tag[pos + 2])) break;
            int size = ((tag[pos + 3] & 0xFF) << 16) | ((tag[pos + 4] & 0xFF) << 8) | (tag[pos + 5] & 0xFF);
            int start = pos + 6;
            if (size > tag.length - start) break;
            String id = new String(tag, pos, 3, StandardCharsets.ISO_8859_1);
            if (id.equals("ULT")) plain.add(copy(tag, start, start + size));
            else if (id.equals("SLT")) synced.add(copy(tag, start, start + size));
            pos = start + size;
        }
    }

    /** v2.3 frame body: flags %ijk00000 = compression (+4-byte size), encryption (+1), grouping (+1). */
    private static byte[] body23(byte[] tag, int start, int size, int format) {
        boolean compressed = (format & 0x80) != 0, encrypted = (format & 0x40) != 0, grouped = (format & 0x20) != 0;
        if (encrypted) return null;
        int p = start, end = start + size, expected = -1;
        if (compressed) { if (p + 4 > end) return null; expected = int32(tag, p); p += 4; }
        if (grouped) p += 1;
        if (p > end) return null;
        byte[] data = copy(tag, p, end);
        return compressed ? inflate(data, expected) : data;
    }

    /** v2.4 frame body: flags %0h00kmnp = grouping (+1), compression, encryption (+1), unsync, data length (+4). */
    private static byte[] body24(byte[] tag, int start, int size, int format, boolean tagUnsync) {
        boolean grouped = (format & 0x40) != 0, compressed = (format & 0x08) != 0, encrypted = (format & 0x04) != 0;
        boolean unsync = (format & 0x02) != 0 || tagUnsync, lengthIndicator = (format & 0x01) != 0;
        if (encrypted) return null;
        int p = start, end = start + size, expected = -1;
        if (grouped) p += 1;
        if (lengthIndicator) { if (p + 4 > end) return null; expected = syncsafe(tag, p); p += 4; }
        if (p > end) return null;
        byte[] data = copy(tag, p, end);
        if (unsync) data = deunsync(data);
        return compressed ? inflate(data, expected) : data;
    }

    /**
     * Frames reachable from {@code offset} when sizes are read as syncsafe or as plain integers.
     * Old iTunes versions wrote plain sizes in v2.4 tags; the interpretation that walks more valid
     * frames wins (ties go to the standard syncsafe form).
     */
    private static int countFrames(byte[] tag, int offset, boolean syncsafe) {
        int pos = Math.max(0, offset), frames = 0;
        while (pos + 10 <= tag.length && validFrameAt(tag, pos)) {
            if (syncsafe && ((tag[pos + 4] | tag[pos + 5] | tag[pos + 6] | tag[pos + 7]) & 0x80) != 0) break;
            int size = syncsafe ? syncsafe(tag, pos + 4) : int32(tag, pos + 4);
            if (size < 0 || size > tag.length - pos - 10) break;
            frames++;
            pos += 10 + size;
        }
        return frames;
    }

    private static boolean validFrameAt(byte[] tag, int pos) {
        if (pos < 0 || pos + 10 > tag.length) return false;
        for (int i = 0; i < 4; i++) if (!idChar(tag[pos + i])) return false;
        return true;
    }

    private static boolean idChar(byte b) { return (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9'); }

    // ---- Lyrics frame bodies -------------------------------------------------------------------

    /** USLT: encoding, language[3], descriptor (terminated), text. */
    static String uslt(byte[] data) {
        if (data.length < 4) return "";
        int encoding = data[0] & 0xFF;
        if (encoding > 3) return "";
        int[] endian = {0};
        int descriptorEnd = terminator(data, 4, encoding);
        if (descriptorEnd < 0) return clean(decode(data, 4, data.length, encoding, endian)); // no descriptor terminator
        decode(data, 4, descriptorEnd, encoding, endian);
        int textStart = descriptorEnd + terminatorLength(encoding);
        return clean(decode(data, Math.min(textStart, data.length), data.length, encoding, endian));
    }

    /** SYLT: encoding, language[3], timestamp format, content type, descriptor, then (text, uint32 time)*. */
    static List<Lyrics.Line> sylt(byte[] data) {
        if (data.length < 7) return Collections.emptyList();
        int encoding = data[0] & 0xFF, format = data[4] & 0xFF, contentType = data[5] & 0xFF;
        // Only millisecond stamps can be shown; content types above 2 are chords, events, trivia...
        if (encoding > 3 || format != 2 || contentType > 2) return Collections.emptyList();
        int[] endian = {0};
        int pos = terminator(data, 6, encoding);
        if (pos < 0) return Collections.emptyList();
        pos += terminatorLength(encoding);
        List<String> texts = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        while (pos < data.length) {
            int end = terminator(data, pos, encoding);
            if (end < 0 || end + terminatorLength(encoding) + 4 > data.length) break;
            texts.add(decode(data, pos, end, encoding, endian));
            pos = end + terminatorLength(encoding);
            times.add(((long) (data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16) | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF));
            pos += 4;
        }
        boolean syllables = false;
        for (int i = 1; i < texts.size(); i++) if (startsWithBreak(texts.get(i))) { syllables = true; break; }
        List<Lyrics.Line> lines = new ArrayList<>();
        if (!syllables) {
            for (int i = 0; i < texts.size(); i++) lines.add(new Lyrics.Line(times.get(i), clean(texts.get(i)).replace('\n', ' ')));
            return lines;
        }
        // Karaoke-style SYLT: syllables join into a line until an entry starts with a line break.
        StringBuilder line = null;
        long lineTime = 0;
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (line == null || startsWithBreak(text)) {
                if (line != null) lines.add(new Lyrics.Line(lineTime, clean(line.toString())));
                line = new StringBuilder();
                lineTime = times.get(i);
            }
            line.append(text.replace("\r", "").replace("\n", ""));
        }
        if (line != null) lines.add(new Lyrics.Line(lineTime, clean(line.toString())));
        return lines;
    }

    private static boolean startsWithBreak(String text) { return !text.isEmpty() && (text.charAt(0) == '\n' || text.charAt(0) == '\r'); }

    private static Lyrics choose(List<byte[]> usltBodies, List<byte[]> syltBodies) {
        String plainText = "";
        String lrcText = null;
        for (byte[] body : usltBodies) {
            String text = uslt(body);
            if (text.isEmpty()) continue;
            if (lrcText == null && LrcParser.looksSynced(text)) lrcText = text;
            else if (plainText.isEmpty() && !LrcParser.looksSynced(text)) plainText = text;
        }
        for (byte[] body : syltBodies) {
            List<Lyrics.Line> lines = sylt(body);
            int textLines = 0;
            for (Lyrics.Line line : lines) if (!line.text.isEmpty()) textLines++;
            if (textLines >= 2) return new Lyrics(lines, plainText, false, Lyrics.SOURCE_EMBEDDED);
        }
        if (lrcText != null) return fromText(lrcText, plainText);
        return plainText.isEmpty() ? Lyrics.NONE : new Lyrics(Collections.<Lyrics.Line>emptyList(), plainText, false, Lyrics.SOURCE_EMBEDDED);
    }

    /** Lyrics from free text: LRC-stamped text becomes synced lines, anything else stays plain. */
    static Lyrics fromText(String text, String plainFallback) {
        String cleaned = clean(text);
        if (cleaned.isEmpty()) return Lyrics.NONE;
        if (LrcParser.looksSynced(cleaned)) {
            String plain = plainFallback == null || plainFallback.isEmpty() ? LrcParser.plainText(cleaned) : plainFallback;
            return new Lyrics(LrcParser.parse(cleaned), plain, false, Lyrics.SOURCE_EMBEDDED);
        }
        return new Lyrics(Collections.<Lyrics.Line>emptyList(), cleaned, false, Lyrics.SOURCE_EMBEDDED);
    }

    // ---- FLAC -------------------------------------------------------------------------------------

    private static Lyrics flac(InputStream in) throws IOException {
        long skipped = 0;
        for (int blocks = 0; blocks < 256; blocks++) {
            byte[] header = readUpTo(in, 4);
            if (header.length < 4) return Lyrics.NONE;
            boolean last = (header[0] & 0x80) != 0;
            int type = header[0] & 0x7F;
            int length = ((header[1] & 0xFF) << 16) | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            if (type == 127) return Lyrics.NONE;
            if (type == 4 && length <= MAX_TAG_BYTES) {
                byte[] block = readUpTo(in, length);
                return block.length < length ? Lyrics.NONE : vorbis(block);
            }
            skipped += length;
            if (skipped > MAX_FLAC_SKIP || !skipFully(in, length)) return Lyrics.NONE;
            if (last) return Lyrics.NONE;
        }
        return Lyrics.NONE;
    }

    /** Vorbis comment block: little-endian vendor length + vendor, count, then (length, "KEY=value")*. */
    static Lyrics vorbis(byte[] block) {
        int pos = 0;
        if (block.length < 8) return Lyrics.NONE;
        long vendor = uint32le(block, pos);
        if (vendor > block.length - 8) return Lyrics.NONE;
        pos += 4 + (int) vendor;
        long count = uint32le(block, pos);
        pos += 4;
        String lyrics = null, unsynced = null;
        for (long i = 0; i < count && pos + 4 <= block.length; i++) {
            long length = uint32le(block, pos);
            pos += 4;
            if (length > block.length - pos) break;
            String comment = new String(block, pos, (int) length, StandardCharsets.UTF_8);
            pos += (int) length;
            int equals = comment.indexOf('=');
            if (equals <= 0) continue;
            String key = comment.substring(0, equals).trim().toUpperCase(Locale.ROOT);
            String value = comment.substring(equals + 1);
            if (key.equals("LYRICS") && lyrics == null && !clean(value).isEmpty()) lyrics = value;
            else if (key.equals("UNSYNCEDLYRICS") && unsynced == null && !clean(value).isEmpty()) unsynced = value;
        }
        boolean lyricsSynced = lyrics != null && LrcParser.looksSynced(lyrics);
        boolean unsyncedSynced = unsynced != null && LrcParser.looksSynced(unsynced);
        String synced = lyricsSynced ? lyrics : unsyncedSynced ? unsynced : null;
        String plain = lyrics != null && !lyricsSynced ? clean(lyrics) : unsynced != null && !unsyncedSynced ? clean(unsynced) : "";
        if (synced != null) return fromText(synced, plain);
        return plain.isEmpty() ? Lyrics.NONE : new Lyrics(Collections.<Lyrics.Line>emptyList(), plain, false, Lyrics.SOURCE_EMBEDDED);
    }

    // ---- Text and byte helpers ------------------------------------------------------------------

    /** Index of the string terminator at or after {@code from}: 0x00 (enc 0/3) or an aligned 0x00 0x00 (enc 1/2). */
    private static int terminator(byte[] data, int from, int encoding) {
        if (encoding == 0 || encoding == 3) {
            for (int i = from; i < data.length; i++) if (data[i] == 0) return i;
            return -1;
        }
        for (int i = from; i + 1 < data.length; i += 2) if (data[i] == 0 && data[i + 1] == 0) return i;
        return -1;
    }

    private static int terminatorLength(int encoding) { return encoding == 1 || encoding == 2 ? 2 : 1; }

    /**
     * Decodes [from, to). For UTF-16 with BOM the byte order comes from the string's own BOM, else
     * from the last BOM seen in the frame ({@code endian[0]}: 0 unknown, 1 little, 2 big; default little).
     */
    private static String decode(byte[] data, int from, int to, int encoding, int[] endian) {
        if (from >= to) return "";
        switch (encoding) {
            case 0: return new String(data, from, to - from, StandardCharsets.ISO_8859_1);
            case 3: return new String(data, from, to - from, StandardCharsets.UTF_8);
            case 2: return new String(data, from, to - from, UTF_16BE);
            default: {
                int start = from;
                if (to - from >= 2) {
                    int b0 = data[from] & 0xFF, b1 = data[from + 1] & 0xFF;
                    if (b0 == 0xFF && b1 == 0xFE) { endian[0] = 1; start += 2; }
                    else if (b0 == 0xFE && b1 == 0xFF) { endian[0] = 2; start += 2; }
                }
                if (start >= to) return "";
                return new String(data, start, to - start, endian[0] == 2 ? UTF_16BE : UTF_16LE);
            }
        }
    }

    /** Trims stray NULs/BOMs, normalises line endings. */
    static String clean(String text) {
        if (text == null) return "";
        String value = text.replace("\u0000", "").replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        return value.trim();
    }

    static byte[] deunsync(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        for (int i = 0; i < data.length; i++) {
            out.write(data[i]);
            if ((data[i] & 0xFF) == 0xFF && i + 1 < data.length && data[i + 1] == 0) i++;
        }
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] data, int expected) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(expected > 0 && expected <= MAX_TAG_BYTES ? expected : 4096);
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buffer, 0, count);
                if (out.size() > MAX_TAG_BYTES) return null;
            }
            return out.toByteArray();
        } catch (DataFormatException corrupt) {
            return null;
        } finally {
            inflater.end();
        }
    }

    static int syncsafe(byte[] b, int at) {
        return ((b[at] & 0x7F) << 21) | ((b[at + 1] & 0x7F) << 14) | ((b[at + 2] & 0x7F) << 7) | (b[at + 3] & 0x7F);
    }

    private static int int32(byte[] b, int at) {
        if (at + 4 > b.length) return -1;
        long value = ((long) (b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    private static long uint32le(byte[] b, int at) {
        if (at + 4 > b.length) return Long.MAX_VALUE;
        return (b[at] & 0xFFL) | ((b[at + 1] & 0xFFL) << 8) | ((b[at + 2] & 0xFFL) << 16) | ((b[at + 3] & 0xFFL) << 24);
    }

    private static byte[] copy(byte[] data, int from, int to) {
        byte[] out = new byte[Math.max(0, to - from)];
        System.arraycopy(data, from, out, 0, out.length);
        return out;
    }

    /** Reads up to {@code count} bytes (fewer only at end of stream). */
    static byte[] readUpTo(InputStream in, int count) throws IOException {
        byte[] buffer = new byte[Math.max(0, count)];
        int filled = 0;
        while (filled < buffer.length) {
            int read = in.read(buffer, filled, buffer.length - filled);
            if (read < 0) break;
            filled += read;
        }
        if (filled == buffer.length) return buffer;
        byte[] shorter = new byte[filled];
        System.arraycopy(buffer, 0, shorter, 0, filled);
        return shorter;
    }

    private static boolean skipFully(InputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) { remaining -= skipped; continue; }
            if (in.read() < 0) return false;
            remaining--;
        }
        return true;
    }
}
