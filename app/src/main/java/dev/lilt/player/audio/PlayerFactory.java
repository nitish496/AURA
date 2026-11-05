package dev.lilt.player.audio;

import android.content.Context;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;

/**
 * Builds AURA's players. Both the main player and the crossfade spare use the same configuration
 * and the shared {@link MediaCache}, so a stream cached by one is read from disk by the other.
 *
 * <p>Audio quality decisions (Media3 1.5.1):
 * <ul>
 *   <li>Decoders output at the file's own sample rate and channel count; AURA never resamples.
 *   <li>Float output stays off. In 1.5.1 {@link DefaultAudioSink} bypasses encoder-delay trimming
 *       (gapless MP3/AAC), channel mapping and Sonic speed control whenever it outputs float, so
 *       enabling it would add gaps between songs of continuous albums and mixes. The catalogue is
 *       MP3, so gapless matters more than 24-bit output here.
 *   <li>Offload and platform speed control stay off (Media3 defaults): both are less reliable.
 *   <li>Decoder fallback is on, so a broken hardware decoder falls back to software.
 *   <li>Constant-bitrate seeking is on so streamed MP3s without a seek table stay seekable.
 * </ul>
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlayerFactory {
    /** Stream buffering: start after 1.5 s, refill to 30 s, buffer up to 2 minutes ahead. */
    static final int MIN_BUFFER_MS = 30_000, MAX_BUFFER_MS = 120_000, BUFFER_FOR_PLAYBACK_MS = 1_500, BUFFER_AFTER_REBUFFER_MS = 3_000;
    /** Network retries before an error surfaces (about 20 s with Media3's back-off). */
    static final int LOAD_RETRIES = 6;

    private PlayerFactory() {}

    public static AudioAttributes musicAttributes() {
        // USAGE_MEDIA lets Android spatialize compatible multichannel content automatically; stereo
        // and pre-spatialized audio are left alone (spatialization behaviour stays AUTO).
        return new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build();
    }

    /**
     * @param tap receives the decoded PCM for loudness measurement (read-only)
     * @param primary the main player owns audio focus; the crossfade spare never requests it
     */
    public static ExoPlayer build(Context context, LoudnessTap tap, boolean primary) {
        Context app = context.getApplicationContext();
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(app) {
            @Override
            protected AudioSink buildAudioSink(Context sinkContext, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
                return new DefaultAudioSink.Builder(sinkContext)
                        .setEnableFloatOutput(false)
                        .setEnableAudioTrackPlaybackParams(false)
                        .setAudioProcessors(new AudioProcessor[]{new TeeAudioProcessor(tap)})
                        .build();
            }
        };
        renderers.setEnableDecoderFallback(true);
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(
                MediaCache.dataSourceFactory(app),
                new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true));
        sources.setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(LOAD_RETRIES));
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_AFTER_REBUFFER_MS)
                .build();
        return new ExoPlayer.Builder(app, renderers, sources)
                .setLoadControl(loadControl)
                .setAudioAttributes(musicAttributes(), primary)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
    }
}
