package dev.lilt.player.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Integrated loudness meter following ITU-R BS.1770-4 / EBU R128: K-weighting (high shelf plus
 * high pass, coefficients derived for any sample rate), 400 ms blocks with 75% overlap, an
 * absolute gate at -70 LUFS and a relative gate 10 LU below the ungated level. Gating uses a
 * 0.1 LU histogram, so memory stays constant for tracks of any length.
 *
 * <p>Mono is measured as dual mono (+3 dB), because Android plays a mono file on both speakers
 * and listeners hear it at the level of the equivalent stereo signal.
 *
 * <p>Thread-safe: audio is fed on the playback thread, results are taken on the main thread.
 */
public final class LoudnessMeter {
    /** Result of a measurement. */
    public static final class Measurement {
        /** Gated integrated loudness in LUFS; NaN when the audio was silent or too short. */
        public final double integratedLufs;
        /** Highest absolute sample value, 0..1 (sample peak, not true peak). */
        public final double peak;
        /** Duration of audio measured, in milliseconds. */
        public final long measuredMs;
        Measurement(double integratedLufs, double peak, long measuredMs) {
            this.integratedLufs = integratedLufs; this.peak = peak; this.measuredMs = measuredMs;
        }
        public boolean valid() { return !Double.isNaN(integratedLufs); }
    }

    private static final double HISTOGRAM_MIN = -70, HISTOGRAM_STEP = 0.1;
    private static final int BINS = 800; // -70 .. +10 LUFS
    private final long[] counts = new long[BINS];
    private final double[] energies = new double[BINS];

    private int sampleRate, channels, subBlockFrames, framesInSubBlock, subBlocksSeen;
    private double[] weights = new double[0], accumulators = new double[0];
    // Per-channel biquad state: shelf (s1, s2) and high-pass (h1, h2), transposed direct form II.
    private double[] s1 = new double[0], s2 = new double[0], h1 = new double[0], h2 = new double[0];
    private double pb0, pb1, pb2, pa1, pa2, ra1, ra2;
    private final double[] recentSubBlocks = new double[4];
    private double peak, measuredSeconds;
    private long framesThisSegment;

    /** Starts a new audio segment (format change, seek or flush); keeps accumulated loudness. */
    public synchronized void configure(int sampleRate, int channels) {
        flushSegment();
        if (sampleRate <= 0 || channels <= 0 || channels > 16) { this.sampleRate = this.channels = 0; return; }
        this.sampleRate = sampleRate; this.channels = channels;
        subBlockFrames = Math.max(1, Math.round(sampleRate / 10f));
        framesInSubBlock = 0; subBlocksSeen = 0;
        weights = new double[channels];
        for (int c = 0; c < channels; c++) weights[c] = weight(c, channels);
        accumulators = new double[channels];
        s1 = new double[channels]; s2 = new double[channels]; h1 = new double[channels]; h2 = new double[channels];
        // High shelf (head acoustics), then RLB high pass; derivation as in libebur128.
        double f0 = 1681.974450955533, gain = 3.999843853973347, q = 0.7071752369554196;
        double k = Math.tan(Math.PI * f0 / sampleRate);
        double vh = Math.pow(10.0, gain / 20.0), vb = Math.pow(vh, 0.4996667741545416);
        double a0 = 1.0 + k / q + k * k;
        pb0 = (vh + vb * k / q + k * k) / a0; pb1 = 2.0 * (k * k - vh) / a0; pb2 = (vh - vb * k / q + k * k) / a0;
        pa1 = 2.0 * (k * k - 1.0) / a0; pa2 = (1.0 - k / q + k * k) / a0;
        f0 = 38.13547087602444; q = 0.5003270373238773;
        k = Math.tan(Math.PI * f0 / sampleRate);
        double ra0 = 1.0 + k / q + k * k;
        ra1 = 2.0 * (k * k - 1.0) / ra0; ra2 = (1.0 - k / q + k * k) / ra0;
    }

    private static double weight(int channel, int channels) {
        if (channels == 1) return 2.0;          // dual mono
        if (channels <= 3) return 1.0;          // L, R (, C)
        if (channels >= 6 && channel == 3) return 0.0;  // LFE in Android's 5.1/7.1 order
        return channel < 3 ? 1.0 : 1.41;        // surrounds
    }

    /** Feeds interleaved 16-bit PCM in native byte order. Does not move the buffer's position. */
    public synchronized void processPcm16(ByteBuffer buffer) {
        if (channels == 0 || buffer == null) return;
        ByteBuffer data = buffer.duplicate().order(ByteOrder.nativeOrder());
        int frames = data.remaining() / (2 * channels);
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) sample(c, data.getShort() / 32768.0);
            endFrame();
        }
    }

    /** Feeds interleaved 16-bit samples (tests and non-buffer callers). */
    public synchronized void processSamples(short[] interleaved, int offset, int frames) {
        if (channels == 0 || interleaved == null) return;
        int index = offset;
        for (int f = 0; f < frames && index + channels <= interleaved.length; f++) {
            for (int c = 0; c < channels; c++) sample(c, interleaved[index++] / 32768.0);
            endFrame();
        }
    }

    private void sample(int c, double x) {
        double ax = Math.abs(x);
        if (ax > peak) peak = ax;
        double y = pb0 * x + s1[c];
        s1[c] = pb1 * x - pa1 * y + s2[c];
        s2[c] = pb2 * x - pa2 * y;
        double z = y + h1[c];                    // high pass numerator is 1, -2, 1
        h1[c] = -2.0 * y - ra1 * z + h2[c];
        h2[c] = y - ra2 * z;
        accumulators[c] += z * z;
    }

    private void endFrame() {
        framesThisSegment++;
        if (++framesInSubBlock < subBlockFrames) return;
        double energy = 0;
        for (int c = 0; c < channels; c++) { energy += weights[c] * accumulators[c] / subBlockFrames; accumulators[c] = 0; }
        framesInSubBlock = 0;
        recentSubBlocks[subBlocksSeen % 4] = energy;
        if (++subBlocksSeen < 4) return;
        double block = (recentSubBlocks[0] + recentSubBlocks[1] + recentSubBlocks[2] + recentSubBlocks[3]) / 4.0;
        if (block <= 0) return;
        double loudness = -0.691 + 10.0 * Math.log10(block);
        if (loudness <= HISTOGRAM_MIN) return;  // absolute gate
        int bin = (int) Math.floor((loudness - HISTOGRAM_MIN) / HISTOGRAM_STEP);
        bin = Math.max(0, Math.min(BINS - 1, bin));
        counts[bin]++; energies[bin] += block;
    }

    private void flushSegment() {
        if (sampleRate > 0) measuredSeconds += framesThisSegment / (double) sampleRate;
        framesThisSegment = 0;
    }

    /** Current measurement without resetting. */
    public synchronized Measurement snapshot() {
        double seconds = measuredSeconds + (sampleRate > 0 ? framesThisSegment / (double) sampleRate : 0);
        return new Measurement(integrated(), peak, Math.round(seconds * 1000));
    }

    /** Returns the measurement so far and starts a fresh one, keeping the audio format. */
    public synchronized Measurement takeAndReset() {
        Measurement result = snapshot();
        java.util.Arrays.fill(counts, 0); java.util.Arrays.fill(energies, 0);
        peak = 0; measuredSeconds = 0; framesThisSegment = 0;
        return result;
    }

    private double integrated() {
        long n = 0; double sum = 0;
        for (int b = 0; b < BINS; b++) { n += counts[b]; sum += energies[b]; }
        if (n == 0) return Double.NaN;
        double relativeGate = -0.691 + 10.0 * Math.log10(sum / n) - 10.0;
        long gatedCount = 0; double gatedSum = 0;
        for (int b = 0; b < BINS; b++) {
            double centre = HISTOGRAM_MIN + (b + 0.5) * HISTOGRAM_STEP;
            if (centre > relativeGate) { gatedCount += counts[b]; gatedSum += energies[b]; }
        }
        return gatedCount == 0 ? Double.NaN : -0.691 + 10.0 * Math.log10(gatedSum / gatedCount);
    }
}
