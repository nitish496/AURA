package dev.lilt.player.screens.player;

import java.util.Locale;

/**
 * The small, truthful format line under the Now Playing scrubber, built only from data the
 * player actually reports for the current audio track (codec, bit depth, sample rate, bitrate).
 * Unknown values are left out; with nothing known the line is hidden. Pure Java;
 * tests/player/FormatLabelTest.
 */
public final class FormatLabel {
    private FormatLabel() {}

    /** Shown for catalogue streams. */
    public static final String AUDIUS = "Streaming from Audius";

    /** Codec name for a MIME type, or "" when unknown. */
    public static String codec(String mime) {
        if (mime == null) return "";
        switch (mime.toLowerCase(Locale.ROOT)) {
            case "audio/flac": return "FLAC";
            case "audio/alac": return "ALAC";
            case "audio/mpeg": case "audio/mpeg-l2": case "audio/mpeg-l1": return "MP3";
            case "audio/mp4a-latm": case "audio/aac": return "AAC";
            case "audio/opus": return "Opus";
            case "audio/vorbis": return "Vorbis";
            case "audio/raw": case "audio/wav": case "audio/x-wav": return "PCM";
            case "audio/ac3": return "Dolby Digital";
            case "audio/eac3": return "Dolby Digital Plus";
            case "audio/eac3-joc": return "Dolby Atmos";
            case "audio/ac4": return "Dolby AC-4";
            case "audio/true-hd": return "Dolby TrueHD";
            case "audio/vnd.dts": return "DTS";
            case "audio/vnd.dts.hd": return "DTS-HD";
            case "audio/amr-wb": return "AMR-WB";
            case "audio/3gpp": return "AMR";
            case "audio/midi": return "MIDI";
            default: return "";
        }
    }

    private static boolean lossless(String codec) {
        return codec.equals("FLAC") || codec.equals("ALAC") || codec.equals("PCM") || codec.equals("Dolby TrueHD");
    }

    /**
     * "FLAC · 24-bit · 96 kHz", "AAC · 256 kbps", "MP3 · 320 kbps", "Opus".
     * @param sampleRate   Hz, or ≤ 0 when unknown
     * @param bitsPerSample ≤ 0 when unknown
     * @param bitrate      bits per second, or ≤ 0 when unknown
     */
    public static String describe(String mime, int sampleRate, int bitsPerSample, int bitrate) {
        String codec = codec(mime);
        if (codec.isEmpty()) return "";
        StringBuilder b = new StringBuilder(codec);
        if (lossless(codec)) {
            if (bitsPerSample > 0) b.append(" · ").append(bitsPerSample).append("-bit");
            if (sampleRate > 0) b.append(" · ").append(khz(sampleRate));
        } else if (bitrate > 0) {
            b.append(" · ").append(Math.round(bitrate / 1000f)).append(" kbps");
        } else if (sampleRate > 0 && !codec.startsWith("Dolby")) {
            b.append(" · ").append(khz(sampleRate));
        }
        return b.toString();
    }

    /** "44.1 kHz", "48 kHz", "96 kHz". */
    public static String khz(int sampleRate) {
        float k = sampleRate / 1000f;
        if (Math.abs(k - Math.round(k)) < 0.05f) return Math.round(k) + " kHz";
        return String.format(Locale.ROOT, "%.1f kHz", k);
    }

    /** Bits per sample for an Android/Media3 PCM encoding constant (2 = 16-bit, 3 = 8-bit, 4 = float, 0x15 = 24-bit, 0x16 = 32-bit). */
    public static int bitsForPcmEncoding(int encoding) {
        switch (encoding) {
            case 3: return 8;
            case 2: return 16;
            case 0x15: return 24; // C.ENCODING_PCM_24BIT
            case 0x16: return 32; // C.ENCODING_PCM_32BIT
            case 4: return 32;    // float
            default: return 0;
        }
    }
}
