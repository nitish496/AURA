package dev.lilt.player.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** BS.1770 meter accuracy and gating, ReplayGain/R128 parsing and the normalisation gain rules. */
public final class LoudnessTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static boolean near(double a, double b, double tolerance) { return Math.abs(a - b) <= tolerance; }

    /** Interleaved 16-bit sine, same signal on every channel. */
    static short[] sine(int rate, int channels, double seconds, double amplitude, double hz) {
        int frames = (int) Math.round(rate * seconds);
        short[] pcm = new short[frames * channels];
        for (int f = 0; f < frames; f++) {
            short v = (short) Math.round(amplitude * 32767 * Math.sin(2 * Math.PI * hz * f / rate));
            for (int c = 0; c < channels; c++) pcm[f * channels + c] = v;
        }
        return pcm;
    }

    static LoudnessMeter.Measurement measure(int rate, int channels, double seconds, double amplitude) {
        LoudnessMeter meter = new LoudnessMeter();
        meter.configure(rate, channels);
        short[] pcm = sine(rate, channels, seconds, amplitude, 997);
        meter.processSamples(pcm, 0, pcm.length / channels);
        return meter.snapshot();
    }

    public static void main(String[] args) {
        meter();
        rules();
        System.out.println("LoudnessTest: " + checks + " checks passed");
    }

    static void meter() {
        // EBU/ITU reference: a 997 Hz sine at -20 dBFS on both stereo channels reads -20 LUFS.
        LoudnessMeter.Measurement stereo = measure(48000, 2, 10, 0.1);
        check(stereo.valid() && near(stereo.integratedLufs, -20.0, 0.15), "stereo -20 dBFS sine: " + stereo.integratedLufs);
        check(near(stereo.measuredMs, 10_000, 5) && near(stereo.peak, 0.1, 0.001), "duration and peak");
        check(near(measure(44100, 2, 10, 0.5).integratedLufs, -6.02, 0.15), "44.1 kHz, -6 dBFS");
        // Mono counts as dual mono: the same level as the equivalent stereo signal.
        check(near(measure(44100, 1, 10, 0.1).integratedLufs, -20.0, 0.15), "mono measured as dual mono");
        check(!measure(48000, 2, 10, 0).valid(), "silence has no loudness");
        check(!measure(48000, 2, 0.3, 0.1).valid(), "shorter than one 400 ms block");

        // Relative gate: a long quiet passage 26 dB down does not drag the result down.
        LoudnessMeter gated = new LoudnessMeter();
        gated.configure(48000, 2);
        short[] loud = sine(48000, 2, 10, 0.1, 997), quiet = sine(48000, 2, 10, 0.005, 997);
        gated.processSamples(loud, 0, loud.length / 2);
        gated.processSamples(quiet, 0, quiet.length / 2);
        check(near(gated.snapshot().integratedLufs, -20.0, 0.3), "relative gate: " + gated.snapshot().integratedLufs);

        // A format change mid-song (flush) keeps what was measured.
        LoudnessMeter split = new LoudnessMeter();
        split.configure(48000, 2);
        short[] a = sine(48000, 2, 5, 0.1, 997);
        split.processSamples(a, 0, a.length / 2);
        split.configure(44100, 2);
        short[] b = sine(44100, 2, 5, 0.1, 997);
        split.processSamples(b, 0, b.length / 2);
        LoudnessMeter.Measurement both = split.snapshot();
        check(near(both.integratedLufs, -20.0, 0.2) && near(both.measuredMs, 10_000, 10), "measurement survives reconfigure");
        LoudnessMeter.Measurement taken = split.takeAndReset();
        check(taken.valid() && !split.snapshot().valid() && split.snapshot().measuredMs == 0, "take resets");

        // ByteBuffer input (what the playback tap sees) matches sample input and leaves position alone.
        short[] pcm = sine(48000, 2, 3, 0.25, 997);
        ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.nativeOrder());
        for (short s : pcm) buffer.putShort(s);
        buffer.flip();
        LoudnessMeter fromBytes = new LoudnessMeter();
        fromBytes.configure(48000, 2);
        fromBytes.processPcm16(buffer);
        check(buffer.position() == 0, "buffer position untouched");
        LoudnessMeter fromSamples = new LoudnessMeter();
        fromSamples.configure(48000, 2);
        fromSamples.processSamples(pcm, 0, pcm.length / 2);
        check(near(fromBytes.snapshot().integratedLufs, fromSamples.snapshot().integratedLufs, 1e-9), "byte and sample paths agree");
        LoudnessMeter unconfigured = new LoudnessMeter();
        unconfigured.processPcm16(buffer);
        check(!unconfigured.snapshot().valid(), "unconfigured meter ignores audio");
    }

    static void rules() {
        check(LoudnessRules.parseGainDb("-7.89 dB") == -7.89 && LoudnessRules.parseGainDb("+3.20dB") == 3.2, "gain tags");
        check(LoudnessRules.parseGainDb(" 6 ") == 6 && LoudnessRules.parseGainDb("1,5 dB") == 1.5, "plain and comma gains");
        check(Double.isNaN(LoudnessRules.parseGainDb("loud")) && Double.isNaN(LoudnessRules.parseGainDb(null)) && Double.isNaN(LoudnessRules.parseGainDb("-99 dB")), "invalid gains");
        check(LoudnessRules.parsePeak("0.988831") == 0.988831 && Double.isNaN(LoudnessRules.parsePeak("0")) && Double.isNaN(LoudnessRules.parsePeak("-1")), "peaks");
        check(LoudnessRules.r128ToReplayGainDb("-1280") == 0.0 && LoudnessRules.r128ToReplayGainDb("256") == 6.0, "R128 Q7.8 to ReplayGain");
        check(Double.isNaN(LoudnessRules.r128ToReplayGainDb("x")), "invalid R128");

        double[] tags = {Double.NaN, Double.NaN};
        check(LoudnessRules.readTag("R128_TRACK_GAIN", "-1280", tags) && tags[0] == 0.0, "R128 read");
        check(LoudnessRules.readTag("replaygain_track_gain", "-6.5 dB", tags) && tags[0] == -6.5, "ReplayGain overrides earlier R128");
        check(LoudnessRules.readTag("REPLAYGAIN_TRACK_PEAK", "0.9", tags) && tags[1] == 0.9, "peak read");
        check(LoudnessRules.readTag("R128_TRACK_GAIN", "256", tags) && tags[0] == -6.5, "R128 never overrides ReplayGain");
        check(!LoudnessRules.readTag("ARTIST", "x", tags) && !LoudnessRules.readTag(null, "x", tags), "other tags ignored");

        double nan = Double.NaN;
        check(near(LoudnessRules.gainDb(-7.89, 0.98, nan, nan), -3.89, 1e-9), "ReplayGain re-referenced to -14 LUFS");
        check(near(LoudnessRules.gainDb(nan, nan, -9, 1.0), -5, 1e-9), "loud master turned down");
        check(near(LoudnessRules.gainDb(-2, 0.5, -30, 1.0), 2.0, 1e-9), "tags win over measurement");
        check(near(LoudnessRules.gainDb(nan, nan, -20, 0.5), -20 * Math.log10(0.5) - 1, 1e-9), "boost limited by peak headroom");
        check(LoudnessRules.gainDb(nan, nan, -20, nan) == 3.0, "boost without peak capped at 3 dB");
        check(LoudnessRules.gainDb(nan, nan, -30, 0.01) == 6.0, "boost capped at 6 dB");
        check(LoudnessRules.gainDb(nan, nan, -20, 1.0) == 0.0, "full-scale peak: no boost");
        check(LoudnessRules.gainDb(-30, nan, nan, nan) == -15.0, "cut capped at 15 dB");
        check(LoudnessRules.gainDb(nan, nan, nan, nan) == 0.0, "nothing known: unchanged");
        check(near(LoudnessRules.volume(-6.0206), 0.5, 1e-4) && LoudnessRules.volume(3) == 1f && LoudnessRules.volume(nan) == 1f, "cut as volume");
        check(LoudnessRules.enhancerMillibels(2.5) == 250 && LoudnessRules.enhancerMillibels(-3) == 0 && LoudnessRules.enhancerMillibels(9) == 600, "boost as enhancer gain");
        check(LoudnessRules.usableMeasurement(60_000, 300_000) && !LoudnessRules.usableMeasurement(59_000, 300_000), "a minute of a long song");
        check(LoudnessRules.usableMeasurement(15_000, 30_000) && !LoudnessRules.usableMeasurement(9_000, 10_000), "half of a short song, at least 10 s");
        check(LoudnessRules.usableMeasurement(60_000, 0) && !LoudnessRules.usableMeasurement(59_999, 0), "unknown duration needs a minute");
    }
}
