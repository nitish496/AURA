package dev.lilt.player.audio;

import java.util.Locale;

/**
 * Loudness normalisation arithmetic. Songs are brought towards {@link #TARGET_LUFS} (the level
 * most streaming services use) using, in order of preference: ReplayGain / R128 tags from the
 * file, AURA's own BS.1770 measurement from an earlier listen, or nothing (0 dB).
 *
 * <p>Cuts are unlimited down to {@link #MAX_CUT_DB}. Boosts are capped at {@link #MAX_BOOST_DB}
 * and by the song's peak so a quiet track is never pushed into clipping; with no peak
 * information boosts stop at {@link #UNKNOWN_PEAK_BOOST_DB}.
 */
public final class LoudnessRules {
    public static final double TARGET_LUFS = -14.0;
    /** ReplayGain 2 tags are computed for -18 LUFS. */
    public static final double REPLAYGAIN_REFERENCE_LUFS = -18.0;
    /** Opus R128 gains are relative to -23 LUFS. */
    public static final double R128_REFERENCE_LUFS = -23.0;
    public static final double MAX_CUT_DB = -15.0, MAX_BOOST_DB = 6.0, UNKNOWN_PEAK_BOOST_DB = 3.0, PEAK_HEADROOM_DB = 1.0;

    private LoudnessRules() {}

    /** Parses "-7.89 dB", "+3.20dB" or "-7.89"; NaN when invalid or implausible. */
    public static double parseGainDb(String text) {
        if (text == null) return Double.NaN;
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("db")) value = value.substring(0, value.length() - 2).trim();
        if (value.startsWith("+")) value = value.substring(1);
        try {
            double gain = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(gain) && Math.abs(gain) <= 64 ? gain : Double.NaN;
        } catch (NumberFormatException invalid) {
            return Double.NaN;
        }
    }

    /** Parses a linear ReplayGain peak such as "0.988831"; NaN when invalid. */
    public static double parsePeak(String text) {
        if (text == null) return Double.NaN;
        try {
            double peak = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(peak) && peak > 0 && peak <= 16 ? peak : Double.NaN;
        } catch (NumberFormatException invalid) {
            return Double.NaN;
        }
    }

    /** Opus R128_TRACK_GAIN (Q7.8 integer, relative to -23 LUFS) expressed as a ReplayGain gain. */
    public static double r128ToReplayGainDb(String text) {
        if (text == null) return Double.NaN;
        try {
            int q78 = Integer.parseInt(text.trim());
            if (q78 < -32768 || q78 > 32767) return Double.NaN;
            return q78 / 256.0 + (REPLAYGAIN_REFERENCE_LUFS - R128_REFERENCE_LUFS);
        } catch (NumberFormatException invalid) {
            return Double.NaN;
        }
    }

    /**
     * Reads one metadata tag (ID3 TXXX description, Vorbis comment key or MP4 "----" name) into
     * {@code gainAndPeak} = {track gain dB, track peak}. ReplayGain wins over Opus R128 whatever the
     * tag order. Returns whether the tag was a loudness tag.
     */
    public static boolean readTag(String key, String value, double[] gainAndPeak) {
        if (key == null || value == null || gainAndPeak == null || gainAndPeak.length < 2) return false;
        switch (key.trim().toUpperCase(Locale.ROOT)) {
            case "REPLAYGAIN_TRACK_GAIN": {
                double gain = parseGainDb(value);
                if (Double.isFinite(gain)) gainAndPeak[0] = gain;
                return true;
            }
            case "REPLAYGAIN_TRACK_PEAK": {
                double peak = parsePeak(value);
                if (Double.isFinite(peak)) gainAndPeak[1] = peak;
                return true;
            }
            case "R128_TRACK_GAIN": {
                if (!Double.isFinite(gainAndPeak[0])) {
                    double gain = r128ToReplayGainDb(value);
                    if (Double.isFinite(gain)) gainAndPeak[0] = gain;
                }
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Gain in dB to apply to a song. Pass NaN for anything unknown.
     *
     * @param replayGainDb ReplayGain track gain (relative to -18 LUFS)
     * @param replayGainPeak linear peak from the same tags
     * @param measuredLufs AURA's integrated measurement
     * @param measuredPeak linear sample peak from the same measurement
     */
    public static double gainDb(double replayGainDb, double replayGainPeak, double measuredLufs, double measuredPeak) {
        double gain, peak;
        if (Double.isFinite(replayGainDb)) {
            gain = replayGainDb + (TARGET_LUFS - REPLAYGAIN_REFERENCE_LUFS);
            peak = replayGainPeak;
        } else if (Double.isFinite(measuredLufs)) {
            gain = TARGET_LUFS - measuredLufs;
            peak = measuredPeak;
        } else {
            return 0;
        }
        if (gain <= 0) return Math.max(MAX_CUT_DB, gain);
        double limit = MAX_BOOST_DB;
        if (Double.isFinite(peak) && peak > 0) limit = Math.min(limit, -20.0 * Math.log10(peak) - PEAK_HEADROOM_DB);
        else limit = Math.min(limit, UNKNOWN_PEAK_BOOST_DB);
        return Math.max(0, Math.min(gain, limit));
    }

    /** Player volume for a gain: cuts are applied as volume, boosts leave the volume at 1. */
    public static float volume(double gainDb) {
        if (!Double.isFinite(gainDb) || gainDb >= 0) return 1f;
        return (float) Math.pow(10.0, Math.max(MAX_CUT_DB, gainDb) / 20.0);
    }

    /** LoudnessEnhancer target gain (millibels) for a boost; 0 for cuts. */
    public static int enhancerMillibels(double gainDb) {
        if (!Double.isFinite(gainDb) || gainDb <= 0) return 0;
        return (int) Math.round(Math.min(MAX_BOOST_DB, gainDb) * 100);
    }

    /**
     * Whether a measurement covers enough of a song to trust: at least a minute, or half of a
     * shorter song (never less than 10 s).
     */
    public static boolean usableMeasurement(long measuredMs, long durationMs) {
        long needed = durationMs > 0 ? Math.min(60_000, Math.max(10_000, durationMs / 2)) : 60_000;
        return measuredMs >= needed;
    }
}
