package dev.lilt.player.audio;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import java.nio.ByteBuffer;

/**
 * Read-only tap in a player's audio pipeline (via Media3's pass-through {@link TeeAudioProcessor})
 * that measures loudness while a song plays. It never modifies audio and never throws into the
 * playback thread. Only 16-bit PCM is measured, which is what the pipeline carries with float
 * output disabled.
 */
@OptIn(markerClass = UnstableApi.class)
public final class LoudnessTap implements TeeAudioProcessor.AudioBufferSink {
    private final LoudnessMeter meter = new LoudnessMeter();
    private volatile boolean active;

    @Override public void flush(int sampleRateHz, int channelCount, int encoding) {
        try {
            active = encoding == C.ENCODING_PCM_16BIT && sampleRateHz > 0 && channelCount > 0;
            meter.configure(active ? sampleRateHz : 0, active ? channelCount : 0);
        } catch (Throwable ignored) {
            active = false;
        }
    }

    @Override public void handleBuffer(ByteBuffer buffer) {
        if (!active) return;
        try {
            meter.processPcm16(buffer);
        } catch (Throwable ignored) {
            active = false; // measurement is optional; playback must continue untouched
        }
    }

    /** Measurement since the previous call; starts a new one. */
    public LoudnessMeter.Measurement take() { return meter.takeAndReset(); }

    /** Discards anything measured so far (for example after a seek into another song). */
    public void discard() { meter.takeAndReset(); }
}
