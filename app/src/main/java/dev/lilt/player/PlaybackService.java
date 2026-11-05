package dev.lilt.player;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.extractor.metadata.id3.InternalFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.extractor.metadata.vorbis.VorbisComment;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import dev.lilt.player.audio.AudioEffectSettings;
import dev.lilt.player.audio.AudioEffects;
import dev.lilt.player.audio.ListeningStats;
import dev.lilt.player.audio.LoudnessMeter;
import dev.lilt.player.audio.LoudnessRules;
import dev.lilt.player.audio.LoudnessStore;
import dev.lilt.player.audio.LoudnessTap;
import dev.lilt.player.audio.MediaCache;
import dev.lilt.player.audio.MediaIds;
import dev.lilt.player.audio.NetworkMonitor;
import dev.lilt.player.audio.PlayCounter;
import dev.lilt.player.audio.PlaybackErrorPolicy;
import dev.lilt.player.audio.PlayerFactory;
import dev.lilt.player.audio.QueueOrders;
import dev.lilt.player.audio.QueueRestorePlan;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.stream.RemoteTrackStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * AURA's playback service: one Media3 session over two ExoPlayers (the active player and a spare
 * used for crossfades), both built by {@link PlayerFactory} on the shared stream cache.
 *
 * <p>Owns: queue persistence/restore ({@link PlaybackStore}), crossfade ({@link CrossfadeEngine}),
 * equaliser and normalisation ({@link AudioEffects}, {@link LoudnessStore}), listening statistics
 * ({@link ListeningStats}), stream error recovery and autoplay. The controller API (custom
 * commands and Bundle keys) is documented in docs/ARCHITECTURE.md (Audio).
 */
@androidx.media3.common.util.UnstableApi
public final class PlaybackService extends MediaSessionService {
    public static final SessionCommand SET_SLEEP_TIMER = new SessionCommand("dev.lilt.player.SET_SLEEP_TIMER", Bundle.EMPTY);
    public static final SessionCommand GET_SLEEP_TIMER = new SessionCommand("dev.lilt.player.GET_SLEEP_TIMER", Bundle.EMPTY);
    public static final SessionCommand SET_PLAYBACK_OPTIONS = new SessionCommand("dev.lilt.player.SET_PLAYBACK_OPTIONS", Bundle.EMPTY);
    public static final SessionCommand GET_PLAYBACK_OPTIONS = new SessionCommand("dev.lilt.player.GET_PLAYBACK_OPTIONS", Bundle.EMPTY);
    public static final SessionCommand SET_AUDIO_EFFECTS = new SessionCommand("dev.lilt.player.SET_AUDIO_EFFECTS", Bundle.EMPTY);
    public static final SessionCommand GET_AUDIO_EFFECTS = new SessionCommand("dev.lilt.player.GET_AUDIO_EFFECTS", Bundle.EMPTY);
    /** Makes the item at Bundle int "index" play right after the current one, also while shuffled. */
    public static final SessionCommand QUEUE_PLAY_NEXT = new SessionCommand("dev.lilt.player.QUEUE_PLAY_NEXT", Bundle.EMPTY);
    /** Returns "cacheBytes" and "cacheLimitBytes" (long) for the offline stream cache. */
    public static final SessionCommand GET_CACHE_INFO = new SessionCommand("dev.lilt.player.GET_CACHE_INFO", Bundle.EMPTY);
    /** Deletes cached streams; returns the same keys as GET_CACHE_INFO. */
    public static final SessionCommand CLEAR_CACHE = new SessionCommand("dev.lilt.player.CLEAR_CACHE", Bundle.EMPTY);

    /** Session extras (MediaController#getSessionExtras): playback paused until the network returns. */
    public static final String EXTRA_WAITING_FOR_NETWORK = "waitingForNetwork";
    /** Extra result keys of the audio-effects commands. */
    public static final String KEY_NORMALIZE_GAIN_DB = "normalizeGainDb", KEY_NORMALIZE_SOURCE = "normalizeSource";
    public static final int MAX_SLEEP_MINUTES = 720;

    private static final String TAG = "AuraPlayback";
    private static final long SLEEP_FADE_MS = 8000, AUTOPLAY_LEAD_MS = 20_000;

    /** A player plus its loudness tap and the song that tap is currently measuring. */
    private static final class Channel {
        final ExoPlayer player;
        final LoudnessTap tap;
        int sessionId;
        String measuringId = "";
        long measuringDuration;
        Channel(ExoPlayer player, LoudnessTap tap) { this.player = player; this.tap = tap; sessionId = player.getAudioSessionId(); }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "AuraPlaybackWorker"));
    private android.content.SharedPreferences options;
    private boolean debug, destroyed;
    private ExoPlayer player, spare;
    private Channel first, second;
    private MediaSession session;
    private LocalBitmapLoader bitmapLoader;
    private PlaybackStore store;
    private CrossfadeEngine crossfade;
    private AudioEffects effects;
    private LoudnessStore loudness;
    private ListeningStats stats;
    private NetworkMonitor network;
    private SettableFuture<PlaybackStore.Restored> initialRestore;

    private int crossfadeSeconds;
    private boolean autoplayEnabled, stopOnTaskRemoved, normalizeEnabled;
    private int wakeMode = C.WAKE_MODE_LOCAL;

    // Normalisation of the active song.
    private float trackVolume = 1f, sleepScale = 1f;
    private double currentGainDb;
    private String currentGainSource = "none";

    // Autoplay.
    private boolean recommendationPending;
    private long lastRecommendationAttempt;
    private String recentId = "";
    private final ArrayList<String> recent = new ArrayList<>();

    // Error recovery.
    private String retriedErrorId = "", waitingId = "";
    private int consecutiveSkips, playingSeconds;

    // Listening statistics.
    private final PlayCounter counter = new PlayCounter();
    private String statsTitle = "", statsArtist = "", statsAlbum = "";
    private int lastTransitionReason = -1;

    // Sleep timer.
    private SleepTimer sleepTimer;
    private boolean sleepAtEndOfTrack;

    private boolean ticking;
    private int tickCount;
    private final Runnable playingTick = new Runnable() {
        @Override public void run() {
            ticking = false;
            if (destroyed) return;
            onPlayingTick();
            if (player.isPlaying()) { ticking = true; handler.postDelayed(this, 1000); }
        }
    };
    private final Runnable sleepFade = new Runnable() {
        @Override public void run() {
            if (destroyed || !sleepTimer.isActive()) return;
            long remaining = sleepTimer.remainingMillis();
            if (crossfade.overlapping()) crossfade.cancel();
            sleepScale = CrossfadeRules.sleepFadeGain(remaining, SLEEP_FADE_MS);
            player.setVolume(trackVolume * sleepScale);
            if (remaining > 0) handler.postDelayed(this, 100);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        debug = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        options = getSharedPreferences("playback_options", MODE_PRIVATE);
        crossfadeSeconds = options.getInt("crossfadeSeconds", 0);
        if (!CrossfadeRules.validSeconds(crossfadeSeconds)) crossfadeSeconds = 0;
        autoplayEnabled = options.getBoolean("autoplayEnabled", true);
        // The owner's established preference: closing AURA from Recents ends playback.
        stopOnTaskRemoved = options.getBoolean("stopOnTaskRemoved", true);
        String savedRecent = options.getString("recent", "");
        if (!savedRecent.isEmpty()) Collections.addAll(recent, savedRecent.split("\n"));

        effects = new AudioEffects(this);
        AudioEffectSettings fx = effects.settings();
        normalizeEnabled = fx.normalize;
        stats = ListeningStats.get(this);
        loudness = new LoudnessStore(this, () -> handler.post(() -> { if (!destroyed) applyTrackGain(player); }));

        LoudnessTap firstTap = new LoudnessTap(), secondTap = new LoudnessTap();
        first = new Channel(PlayerFactory.build(this, firstTap, true), firstTap);
        second = new Channel(PlayerFactory.build(this, secondTap, false), secondTap);
        player = first.player;
        spare = second.player;
        player.setPlaybackParameters(new PlaybackParameters(fx.speed));
        effects.attach(first.sessionId);
        effects.attach(second.sessionId);

        store = new PlaybackStore(this);
        PlayerWidget.publish(this, player);
        sleepTimer = new SleepTimer(this::sleepExpired);

        Player.Listener events = new Player.Listener() {
            @Override public void onEvents(Player p, Player.Events e) { if (p == player) onActiveEvents(e); }
        };
        player.addListener(events); spare.addListener(events);
        attachPlayerListener(player); attachPlayerListener(spare);

        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        bitmapLoader = new LocalBitmapLoader(this);
        session = new MediaSession.Builder(this, player).setBitmapLoader(bitmapLoader).setSessionActivity(open).setCallback(new SessionCallback()).build();

        crossfade = new CrossfadeEngine(new CrossfadeEngine.Host() {
            public boolean debugEnabled() { return debug; }
            public ExoPlayer active() { return player; }
            public ExoPlayer spare() { return spare; }
            public boolean allowCrossfade() { return !sleepAtEndOfTrack && !(sleepTimer.isActive() && sleepTimer.remainingMillis() <= SLEEP_FADE_MS + 15_000); }
            public boolean isRemote(MediaItem item) { return PlaybackService.isRemote(item); }
            public void staging(ExoPlayer incoming, MediaItem destination) {
                // Drop whatever a cancelled earlier overlap left in this player's loudness tap.
                Channel channel = channelOf(incoming);
                if (channel == null) return;
                channel.tap.discard();
                channel.measuringId = destination.mediaId;
                channel.measuringDuration = 0;
            }
            public float prepareVolume(ExoPlayer incoming) { return applyTrackGain(incoming); }
            public void promote(ExoPlayer incoming, ExoPlayer outgoing) { promoteSpare(incoming, outgoing); }
        }, handler);
        crossfade.setSeconds(crossfadeSeconds);

        network = new NetworkMonitor(this, handler, this::onOnline);
        network.start();

        initialRestore = SettableFuture.create();
        store.restoreAsync(handler, restored -> {
            initialRestore.set(restored);
            if (destroyed || restored == null || player.getMediaItemCount() > 0) return;
            store.apply(player, restored);
            PlayerWidget.publish(this, player);
        });
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo info) {
        // Only this app and trusted system clients may control the session.
        return trusted(info) ? session : null;
    }

    private boolean trusted(MediaSession.ControllerInfo info) { return info.getPackageName().equals(getPackageName()) || info.isTrusted(); }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Optionally keep playing in the foreground, like other music apps. A paused (or not
        // foreground) service must stop here, or Android would kill and restart it.
        if (!stopOnTaskRemoved && isPlaybackOngoing()) return;
        Log.i(TAG, "Task removed: stopping playback");
        crossfade.cancel();
        player.pause(); spare.pause();
        store.save(player);
        player.stop(); spare.stop();
        stopTicking();
        PlayerWidget.publish(this, player);
        stopSelf();
    }

    @Override public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        sleepTimer.cancel();
        endStatsSession(SystemClock.elapsedRealtime());
        stats.flush();
        offerMeasurement(channelOf(player));
        crossfade.close();
        network.stop();
        store.save(player);
        store.close();
        worker.shutdownNow();
        effects.release();
        loudness.close();
        if (bitmapLoader != null) bitmapLoader.close();
        session.release();
        player.release();
        spare.release();
        PlayerWidget.prefs(this).edit().putBoolean("playing", false).apply();
        PlayerWidget.refresh(this);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- player events

    private void onActiveEvents(Player.Events events) {
        if (events.size() == 1 && events.contains(Player.EVENT_VOLUME_CHANGED)) return;
        if (debug && (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) || events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)))
            Log.d(TAG, "Intent playing=" + player.getPlayWhenReady() + " suppression=" + player.getPlaybackSuppressionReason());
        crossfade.activeEvent(events);
        long now = SystemClock.elapsedRealtime();
        boolean transition = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION);
        boolean mediaChanged = transition || events.contains(Player.EVENT_MEDIA_METADATA_CHANGED);
        boolean stateChanged = events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) || events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED);
        if (transition) onTransition(now);
        if (transition || events.contains(Player.EVENT_TRACKS_CHANGED)) applyTrackGain(player);
        if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_TRACKS_CHANGED)) refreshMeasuringDuration();
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
            counter.setPlaying(player.isPlaying(), now);
            if (!player.isPlaying()) { maybeCountPlay(now); drainStats(now); }
        }
        if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) counter.setSpeed(player.getPlaybackParameters().speed, now);
        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && player.getPlayWhenReady()) setWaitingForNetwork("");
        if (mediaChanged || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) rememberPlaying();
        if (events.contains(Player.EVENT_TIMELINE_CHANGED) || transition) updateWakeMode();
        // Decoder/loading/device events do not change the saved queue or widget content.
        if (mediaChanged || stateChanged || events.contains(Player.EVENT_TIMELINE_CHANGED)
                || events.contains(Player.EVENT_POSITION_DISCONTINUITY) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                || events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) store.save(player);
        if (mediaChanged || stateChanged || events.contains(Player.EVENT_TIMELINE_CHANGED)) PlayerWidget.publish(this, player);
        if (stateChanged) {
            if (player.isPlaying()) startTicking(); else stopTicking();
            if (player.getPlaybackState() == Player.STATE_ENDED) maybeAutoplay();
        }
    }

    private void attachPlayerListener(ExoPlayer source) {
        source.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) { if (source == player) lastTransitionReason = reason; }
            @Override public void onPlayWhenReadyChanged(boolean wanted, int reason) {
                if (source != player) return;
                if (debug) Log.d(TAG, "Play intent=" + wanted + " changeReason=" + reason + " suppression=" + source.getPlaybackSuppressionReason());
                if (!wanted && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAtEndOfTrack) clearEndOfTrack();
            }
            @Override public void onAudioSessionIdChanged(int audioSessionId) {
                // Media3 keeps one session per player, but rebind defensively if it ever changes.
                Channel channel = channelOf(source);
                if (channel == null || channel.sessionId == audioSessionId) return;
                effects.detach(channel.sessionId);
                channel.sessionId = audioSessionId;
                effects.attach(audioSessionId);
                applyTrackGain(source);
            }
            @Override public void onPlayerError(PlaybackException error) { handlePlayerError(source, error); }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                if (source == player) crossfade.positionDiscontinuity(reason);
            }
        });
    }

    private void onTransition(long now) {
        MediaItem item = player.getCurrentMediaItem();
        String id = item == null ? "" : item.mediaId;
        // Replacing the current item (stream host failover, metadata refresh) keeps the session.
        boolean sameSong = lastTransitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && id.equals(counter.id());
        lastTransitionReason = -1;
        playingSeconds = 0;
        if (sameSong) return;
        endStatsSession(now);
        Channel channel = channelOf(player);
        offerMeasurement(channel);
        channel.measuringId = id;
        channel.measuringDuration = positive(player.getDuration());
        beginStatsSession(now);
        if (!id.equals(waitingId)) setWaitingForNetwork("");
    }

    private void promoteSpare(ExoPlayer incoming, ExoPlayer outgoing) {
        long now = SystemClock.elapsedRealtime();
        endStatsSession(now);
        offerMeasurement(channelOf(outgoing));
        outgoing.setAudioAttributes(outgoing.getAudioAttributes(), false);
        player = incoming; spare = outgoing;
        incoming.setAudioAttributes(incoming.getAudioAttributes(), true);
        outgoing.setPauseAtEndOfMediaItems(false);
        effects.setBoost(outgoing.getAudioSessionId(), 0);
        trackVolume = incoming.getVolume();
        session.setPlayer(incoming);
        Channel channel = channelOf(incoming);
        MediaItem item = incoming.getCurrentMediaItem();
        channel.measuringId = item == null ? "" : item.mediaId;
        channel.measuringDuration = positive(incoming.getDuration());
        beginStatsSession(now);
        applyTrackGain(incoming);
        rememberPlaying(); store.save(incoming); PlayerWidget.publish(this, incoming);
        if (incoming.isPlaying()) startTicking();
    }

    private void startTicking() {
        if (ticking || destroyed) return;
        ticking = true;
        handler.postDelayed(playingTick, 1000);
    }

    private void stopTicking() {
        handler.removeCallbacks(playingTick);
        ticking = false;
    }

    private void onPlayingTick() {
        long now = SystemClock.elapsedRealtime();
        tickCount++;
        maybeAutoplay();
        counter.setDuration(positive(player.getDuration()));
        maybeCountPlay(now);
        if (tickCount % 30 == 0) drainStats(now);
        if (tickCount % 5 == 0) store.save(player);
        if (player.isPlaying() && ++playingSeconds >= 5) { consecutiveSkips = 0; retriedErrorId = ""; }
    }

    // ---------------------------------------------------------------- normalisation

    /** Applies the normalisation for a player's current song; returns the volume part (cut). */
    private float applyTrackGain(ExoPlayer p) {
        if (destroyed) return 1f;
        MediaItem item = p.getCurrentMediaItem();
        double gain = 0;
        String source = "none";
        if (normalizeEnabled && item != null) {
            double[] tags = replayGain(p);
            LoudnessStore.Entry measured = loudness.get(item.mediaId);
            gain = LoudnessRules.gainDb(tags[0], tags[1], measured == null ? Double.NaN : measured.lufs, measured == null ? Double.NaN : measured.peak);
            source = Double.isFinite(tags[0]) ? "replaygain" : measured != null ? "measured" : "none";
        }
        effects.setBoost(p.getAudioSessionId(), LoudnessRules.enhancerMillibels(gain));
        float volume = LoudnessRules.volume(gain);
        if (p == player) {
            currentGainDb = gain; currentGainSource = source; trackVolume = volume;
            if (!crossfade.overlapping()) p.setVolume(volume * sleepScale);
        }
        return volume;
    }

    /** ReplayGain/R128 track gain and peak from the selected audio format's tags; NaN when absent. */
    private static double[] replayGain(Player p) {
        double[] gainAndPeak = {Double.NaN, Double.NaN};
        try {
            for (Tracks.Group group : p.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
                for (int i = 0; i < group.length; i++) {
                    if (!group.isTrackSelected(i)) continue;
                    Metadata metadata = group.getTrackFormat(i).metadata;
                    if (metadata == null) continue;
                    for (int e = 0; e < metadata.length(); e++) {
                        Metadata.Entry entry = metadata.get(e);
                        if (entry instanceof TextInformationFrame) {
                            TextInformationFrame frame = (TextInformationFrame) entry;
                            if ("TXXX".equals(frame.id) && !frame.values.isEmpty()) LoudnessRules.readTag(frame.description, frame.values.get(0), gainAndPeak);
                        } else if (entry instanceof VorbisComment) {
                            VorbisComment comment = (VorbisComment) entry;
                            LoudnessRules.readTag(comment.key, comment.value, gainAndPeak);
                        } else if (entry instanceof InternalFrame) {
                            InternalFrame frame = (InternalFrame) entry;
                            LoudnessRules.readTag(frame.description, frame.text, gainAndPeak);
                        }
                    }
                }
            }
        } catch (RuntimeException unreadable) { /* tags are optional */ }
        return gainAndPeak;
    }

    private void offerMeasurement(Channel channel) {
        if (channel == null) return;
        LoudnessMeter.Measurement m = channel.tap.take();
        if (!channel.measuringId.isEmpty()) loudness.offer(channel.measuringId, m, channel.measuringDuration);
    }

    private void refreshMeasuringDuration() {
        Channel channel = channelOf(player);
        MediaItem item = player.getCurrentMediaItem();
        if (channel != null && item != null && item.mediaId.equals(channel.measuringId) && player.getDuration() > 0)
            channel.measuringDuration = player.getDuration();
    }

    private Channel channelOf(ExoPlayer p) { return p == first.player ? first : p == second.player ? second : null; }

    // ---------------------------------------------------------------- statistics

    private void beginStatsSession(long now) {
        MediaItem item = player.getCurrentMediaItem();
        if (item == null) return;
        MediaMetadata meta = item.mediaMetadata;
        statsTitle = text(meta.title); statsArtist = text(meta.artist); statsAlbum = text(meta.albumTitle);
        counter.begin(item.mediaId, positive(player.getDuration()), player.isPlaying(), player.getPlaybackParameters().speed, now);
    }

    private void endStatsSession(long now) {
        if (!counter.active()) return;
        maybeCountPlay(now);
        String id = counter.id();
        long ms = counter.end(now);
        if (ms > 0) stats.addListened(id, statsTitle, statsArtist, statsAlbum, ms, System.currentTimeMillis());
    }

    private void maybeCountPlay(long now) {
        if (counter.shouldCount(now)) stats.recordPlay(counter.id(), statsTitle, statsArtist, statsAlbum, System.currentTimeMillis());
    }

    private void drainStats(long now) {
        if (!counter.active()) return;
        long ms = counter.drainListened(now);
        if (ms > 0) stats.addListened(counter.id(), statsTitle, statsArtist, statsAlbum, ms, System.currentTimeMillis());
    }

    // ---------------------------------------------------------------- errors and connectivity

    private void handlePlayerError(ExoPlayer source, PlaybackException error) {
        // Error messages/cause stacks may contain private paths; record codes only.
        Log.w(TAG, "Playback error code=" + error.errorCode + " active=" + (source == player));
        options.edit().putInt("lastErrorCode", error.errorCode).putLong("lastErrorTime", System.currentTimeMillis()).apply();
        crossfade.playerError(source, error.errorCode);
        if (source != player || destroyed) return;
        playingSeconds = 0;
        MediaItem item = source.getCurrentMediaItem();
        String id = item == null ? "" : item.mediaId;
        boolean remote = item != null && isRemote(item);
        String title = item == null ? "" : text(item.mediaMetadata.title);
        long position = source.getCurrentPosition();
        PlaybackErrorPolicy.Action action = PlaybackErrorPolicy.decide(error.errorCode, remote, network.online(),
                source.getPlayWhenReady() && !id.isEmpty(), id.equals(retriedErrorId), consecutiveSkips, source.hasNextMediaItem());
        String message = PlaybackErrorPolicy.message(action, remote, title, error.errorCode);
        switch (action) {
            case IGNORE:
                return;
            case RETRY: {
                retriedErrorId = id;
                MediaItem replacement = remote ? failoverItem(item) : null;
                handler.postDelayed(() -> {
                    MediaItem now = source.getCurrentMediaItem();
                    if (destroyed || source != player || !source.getPlayWhenReady() || source.getPlayerError() == null
                            || now == null || !id.equals(now.mediaId)) return;
                    int index = source.getCurrentMediaItemIndex();
                    if (replacement != null) source.replaceMediaItem(index, replacement);
                    source.seekTo(index, position);
                    source.prepare();
                }, PlaybackErrorPolicy.RETRY_DELAY_MS);
                return;
            }
            case WAIT_FOR_NETWORK:
                source.pause();
                setWaitingForNetwork(id);
                notifyUser(message);
                store.save(source);
                return;
            case SKIP:
                consecutiveSkips++;
                retriedErrorId = "";
                notifyUser(message);
                // Let controllers see the error before the next song replaces it.
                handler.postDelayed(() -> {
                    MediaItem now = source.getCurrentMediaItem();
                    if (destroyed || source != player || now == null || !id.equals(now.mediaId) || !source.hasNextMediaItem()) return;
                    source.seekToNextMediaItem();
                    source.prepare();
                }, 400);
                return;
            case PAUSE:
            default:
                source.pause();
                store.save(source);
                notifyUser(message);
        }
    }

    /** The same catalogue track on the next healthy host, or null to retry the same URL. */
    private static MediaItem failoverItem(MediaItem item) {
        try {
            Track track = remoteTrack(item);
            Track moved = AudiusClient.get().failover(track);
            return moved == null || moved == track || moved.uri.equals(track.uri) ? null : moved.item(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private void onOnline() {
        if (destroyed || waitingId.isEmpty()) return;
        MediaItem item = player.getCurrentMediaItem();
        String id = waitingId;
        setWaitingForNetwork("");
        if (item == null || !id.equals(item.mediaId) || player.getPlayWhenReady()) return;
        retriedErrorId = "";
        if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
        player.play();
    }

    private void setWaitingForNetwork(String id) {
        if (id.equals(waitingId)) return;
        waitingId = id;
        if (session == null) return;
        Bundle extras = new Bundle(session.getSessionExtras());
        extras.putBoolean(EXTRA_WAITING_FOR_NETWORK, !id.isEmpty());
        session.setSessionExtras(extras);
    }

    /** Sends a non-fatal SessionError to controllers; toasts only when AURA's own UI is not connected. */
    private void notifyUser(String message) {
        if (message == null || session == null) return;
        try { session.sendError(new SessionError(SessionError.ERROR_IO, message)); } catch (RuntimeException ignored) {}
        boolean appConnected = false;
        for (MediaSession.ControllerInfo controller : session.getConnectedControllers())
            if (controller.getPackageName().equals(getPackageName()) && !session.isMediaNotificationController(controller)) { appConnected = true; break; }
        if (!appConnected) android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- queue helpers

    static boolean isRemote(MediaItem item) {
        return item != null && MediaIds.isRemote(item.mediaId, item.localConfiguration == null ? null : item.localConfiguration.uri.toString());
    }

    /** Rebuilds a catalogue Track from a queued MediaItem (Track.item() wrote these fields). */
    static Track remoteTrack(MediaItem item) {
        MediaMetadata meta = item.mediaMetadata;
        Bundle extras = meta.extras;
        String uri = item.localConfiguration == null ? "" : item.localConfiguration.uri.toString();
        return Track.remote(item.mediaId, text(meta.title), text(meta.artist), text(meta.albumTitle), uri,
                meta.artworkUri == null ? null : meta.artworkUri.toString(),
                extras == null ? 0 : extras.getLong(Track.EXTRA_DURATION_MS, 0), text(meta.genre),
                extras == null ? "" : extras.getString(Track.EXTRA_ARTIST_ID, ""), extras == null ? "" : extras.getString(Track.EXTRA_PERMALINK, ""));
    }

    /** Streams need a Wi-Fi lock while playing with the screen off; device files do not. */
    private void updateWakeMode() {
        boolean remote = false;
        for (int i = 0, n = Math.min(player.getMediaItemCount(), 2000); i < n && !remote; i++) remote = isRemote(player.getMediaItemAt(i));
        int mode = remote ? C.WAKE_MODE_NETWORK : C.WAKE_MODE_LOCAL;
        if (mode == wakeMode) return;
        wakeMode = mode;
        player.setWakeMode(mode); spare.setWakeMode(mode);
    }

    private void rememberPlaying() {
        MediaItem item = player.getCurrentMediaItem();
        if (!player.isPlaying() || item == null || item.mediaId.equals(recentId)) return;
        recentId = item.mediaId; recent.remove(recentId); recent.add(0, recentId);
        while (recent.size() > 50) recent.remove(recent.size() - 1);
        options.edit().putString("recent", android.text.TextUtils.join("\n", recent)).apply();
    }

    // ---------------------------------------------------------------- autoplay

    private void maybeAutoplay() {
        if (destroyed || recommendationPending || SystemClock.elapsedRealtime() - lastRecommendationAttempt < 5000 || !autoplayEnabled || crossfade.overlapping()
                || sleepAtEndOfTrack || player.getRepeatMode() != Player.REPEAT_MODE_OFF || !player.getPlayWhenReady()
                || player.hasNextMediaItem() || player.getMediaItemCount() == 0 || player.getMediaItemCount() >= 2000) return;
        long duration = player.getDuration();
        if (player.getPlaybackState() != Player.STATE_ENDED && (duration <= 0 || duration - player.getCurrentPosition() > AUTOPLAY_LEAD_MS)) return;
        MediaItem seedItem = player.getCurrentMediaItem();
        if (seedItem == null) return;
        final String seedId = seedItem.mediaId;
        final ExoPlayer requestedPlayer = player;
        final int count = player.getMediaItemCount(), index = player.getCurrentMediaItemIndex();
        final ArrayList<String> history = new ArrayList<>(recent);
        final boolean remoteSeed = isRemote(seedItem);
        final Set<String> queued = new HashSet<>();
        for (int i = 0; i < count; i++) queued.add(player.getMediaItemAt(i).mediaId);
        recommendationPending = true; lastRecommendationAttempt = SystemClock.elapsedRealtime();
        try {
            worker.execute(() -> {
                MediaItem next = null;
                try {
                    next = remoteSeed ? remoteRecommendation(seedItem, queued, history) : localRecommendation(seedItem, history);
                } catch (RuntimeException unavailable) { /* Revoked permission, provider or network failure: keep the queue. */ }
                final MediaItem chosen = next;
                handler.post(() -> {
                    recommendationPending = false;
                    if (destroyed || chosen == null || !autoplayEnabled || player != requestedPlayer || crossfade.overlapping()
                            || player.getRepeatMode() != Player.REPEAT_MODE_OFF || !player.getPlayWhenReady()
                            || player.getMediaItemCount() != count || player.getCurrentMediaItemIndex() != index
                            || player.getCurrentMediaItem() == null || !seedId.equals(player.getCurrentMediaItem().mediaId) || player.hasNextMediaItem()) return;
                    boolean ended = player.getPlaybackState() == Player.STATE_ENDED;
                    player.addMediaItem(chosen);
                    if (ended) { player.seekTo(count, 0); player.prepare(); player.play(); }
                });
            });
        } catch (RejectedExecutionException stopped) {
            recommendationPending = false;
        }
    }

    /** Worker thread: a related catalogue song (same artist / genre), avoiding the queue and recent plays. */
    private MediaItem remoteRecommendation(MediaItem seedItem, Set<String> queued, List<String> history) {
        RemoteTrackStore remote = new RemoteTrackStore(this);
        Track seed = remote.get(seedItem.mediaId);
        if (seed == null) seed = remoteTrack(seedItem);
        List<Track> related;
        try { related = AudiusClient.get(this).related(seed, 25); }
        catch (java.io.IOException offline) { return null; }
        List<AutoplayRules.Song> songs = new ArrayList<>();
        for (Track track : related) songs.add(new AutoplayRules.Song(track.id, track.album, track.artist, Collections.emptySet()));
        AutoplayRules.Song seedSong = new AutoplayRules.Song(seed.id, seed.album, seed.artist, Collections.emptySet());
        String choice = AutoplayRules.choose(songs, seedSong, queued, history, new Random());
        if (choice == null) return null;
        for (Track track : related) if (track.id.equals(choice)) { remote.put(track); return track.item(null); }
        return null;
    }

    /** Worker thread: AURA's local rules (album, artist, mood), avoiding recent plays. */
    private MediaItem localRecommendation(MediaItem seedItem, List<String> history) {
        Library library = new Library(this);
        MoodStore moods = new MoodStore(this);
        List<Track> tracks = library.scan();
        ArrayList<AutoplayRules.Song> songs = new ArrayList<>();
        AutoplayRules.Song seed = null;
        String seedId = seedItem.mediaId;
        for (Track track : tracks) {
            AutoplayRules.Song song = new AutoplayRules.Song(track.id, track.album, track.artist, moods.moodsFor(track.id));
            songs.add(song);
            if (track.id.equals(seedId)) seed = song;
        }
        // A deleted seed may still have playable metadata, without granting it a mood.
        if (seed == null) seed = new AutoplayRules.Song(seedId, String.valueOf(seedItem.mediaMetadata.albumTitle), String.valueOf(seedItem.mediaMetadata.artist), Collections.emptySet());
        String choice = AutoplayRules.choose(songs, seed, Collections.emptySet(), history, new Random());
        if (choice != null) for (Track track : tracks) if (track.id.equals(choice)) return track.item(library.artwork(track));
        return null;
    }

    // ---------------------------------------------------------------- sleep timer

    private void scheduleSleepFade() {
        handler.removeCallbacks(sleepFade);
        if (!sleepTimer.isActive()) return;
        handler.postDelayed(sleepFade, Math.max(0, sleepTimer.remainingMillis() - SLEEP_FADE_MS));
    }

    private void cancelSleepTimer() {
        sleepTimer.cancel();
        handler.removeCallbacks(sleepFade);
        restoreSleepVolume();
    }

    private void restoreSleepVolume() {
        if (sleepScale == 1f) return;
        sleepScale = 1f;
        if (!crossfade.overlapping()) player.setVolume(trackVolume);
    }

    private void sleepExpired() {
        handler.removeCallbacks(sleepFade);
        crossfade.cancel();
        player.pause();
        store.save(player);
        restoreSleepVolume();
    }

    private void clearEndOfTrack() {
        sleepAtEndOfTrack = false;
        player.setPauseAtEndOfMediaItems(false);
        spare.setPauseAtEndOfMediaItems(false);
    }

    private Bundle sleepState() {
        Bundle result = new Bundle();
        result.putLong("remainingMillis", sleepTimer.remainingMillis());
        result.putBoolean("endOfTrack", sleepAtEndOfTrack);
        return result;
    }

    // ---------------------------------------------------------------- session callback

    private Bundle effectsState() {
        Bundle state = effects.describe();
        state.putFloat(KEY_NORMALIZE_GAIN_DB, (float) currentGainDb);
        state.putString(KEY_NORMALIZE_SOURCE, currentGainSource);
        return state;
    }

    private void applyEffectSettings() {
        AudioEffectSettings fx = effects.settings();
        crossfade.cancel();
        normalizeEnabled = fx.normalize;
        PlaybackParameters speed = new PlaybackParameters(fx.speed);
        if (!speed.equals(player.getPlaybackParameters())) player.setPlaybackParameters(speed);
        applyTrackGain(player);
    }

    private Bundle playbackOptions() {
        Bundle result = new Bundle();
        result.putInt("crossfadeSeconds", crossfadeSeconds);
        result.putBoolean("autoplayEnabled", autoplayEnabled);
        result.putBoolean("stopOnTaskRemoved", stopOnTaskRemoved);
        return result;
    }

    private ListenableFuture<SessionResult> cacheInfo(boolean clear) {
        SettableFuture<SessionResult> future = SettableFuture.create();
        try {
            worker.execute(() -> {
                try {
                    if (clear) MediaCache.clear(this);
                    Bundle result = new Bundle();
                    result.putLong("cacheBytes", MediaCache.usedBytes(this));
                    result.putLong("cacheLimitBytes", MediaCache.limitBytes(this));
                    future.set(new SessionResult(SessionResult.RESULT_SUCCESS, result));
                } catch (RuntimeException failed) {
                    future.set(new SessionResult(SessionError.ERROR_IO));
                }
            });
        } catch (RejectedExecutionException stopped) {
            future.set(new SessionResult(SessionError.ERROR_INVALID_STATE));
        }
        return future;
    }

    private static ListenableFuture<SessionResult> ok(Bundle extras) { return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS, extras)); }
    private static ListenableFuture<SessionResult> error(int code) { return Futures.immediateFuture(new SessionResult(code)); }

    private final class SessionCallback implements MediaSession.Callback {
        @Override public MediaSession.ConnectionResult onConnect(MediaSession mediaSession, MediaSession.ControllerInfo controller) {
            if (!trusted(controller)) return MediaSession.ConnectionResult.reject();
            MediaSession.ConnectionResult base = MediaSession.Callback.super.onConnect(mediaSession, controller);
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                    .setAvailableSessionCommands(base.availableSessionCommands.buildUpon()
                            .add(SET_SLEEP_TIMER).add(GET_SLEEP_TIMER).add(SET_PLAYBACK_OPTIONS).add(GET_PLAYBACK_OPTIONS)
                            .add(SET_AUDIO_EFFECTS).add(GET_AUDIO_EFFECTS).add(QUEUE_PLAY_NEXT).add(GET_CACHE_INFO).add(CLEAR_CACHE).build())
                    .setAvailablePlayerCommands(base.availablePlayerCommands).build();
        }

        @Override public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(MediaSession mediaSession, MediaSession.ControllerInfo controller) {
            // Play pressed (headset, widget, system controls) while the queue is empty: resume the saved queue.
            SettableFuture<MediaSession.MediaItemsWithStartPosition> result = SettableFuture.create();
            java.util.function.Consumer<PlaybackStore.Restored> deliver = restored -> {
                if (restored == null) result.setException(new UnsupportedOperationException("No saved queue"));
                else result.set(new MediaSession.MediaItemsWithStartPosition(restored.items, restored.index, restored.position));
            };
            if (!initialRestore.isDone()) initialRestore.addListener(() -> deliver.accept(Futures.getUnchecked(initialRestore)), handler::post);
            else store.restoreAsync(handler, deliver);
            return result;
        }

        @Override public ListenableFuture<SessionResult> onCustomCommand(MediaSession mediaSession, MediaSession.ControllerInfo controller, SessionCommand command, Bundle args) {
            if (!trusted(controller)) return error(SessionError.ERROR_PERMISSION_DENIED);
            if (SET_PLAYBACK_OPTIONS.equals(command)) {
                int requested = args.getInt("crossfadeSeconds", crossfadeSeconds);
                if (!CrossfadeRules.validSeconds(requested)) return error(SessionError.ERROR_BAD_VALUE);
                crossfadeSeconds = requested;
                autoplayEnabled = args.getBoolean("autoplayEnabled", autoplayEnabled);
                stopOnTaskRemoved = args.getBoolean("stopOnTaskRemoved", stopOnTaskRemoved);
                crossfade.setSeconds(crossfadeSeconds);
                options.edit().putInt("crossfadeSeconds", crossfadeSeconds).putBoolean("autoplayEnabled", autoplayEnabled)
                        .putBoolean("stopOnTaskRemoved", stopOnTaskRemoved).apply();
                return ok(playbackOptions());
            }
            if (GET_PLAYBACK_OPTIONS.equals(command)) return ok(playbackOptions());
            if (SET_SLEEP_TIMER.equals(command)) {
                if (args.getBoolean("endOfTrack", false)) {
                    cancelSleepTimer();
                    crossfade.cancel();
                    sleepAtEndOfTrack = true;
                    player.setPauseAtEndOfMediaItems(true);
                    return ok(sleepState());
                }
                int minutes = args.getInt("minutes", -1);
                if (minutes < 0 || minutes > MAX_SLEEP_MINUTES) return error(SessionError.ERROR_BAD_VALUE);
                if (sleepAtEndOfTrack) clearEndOfTrack();
                cancelSleepTimer();
                if (minutes > 0) { sleepTimer.start(minutes); scheduleSleepFade(); }
                return ok(sleepState());
            }
            if (GET_SLEEP_TIMER.equals(command)) return ok(sleepState());
            if (SET_AUDIO_EFFECTS.equals(command)) {
                int result = effects.update(args);
                if (result == AudioEffects.BAD_VALUE) return error(SessionError.ERROR_BAD_VALUE);
                if (result == AudioEffects.NOT_SUPPORTED) return error(SessionError.ERROR_NOT_SUPPORTED);
                applyEffectSettings();
                return ok(effectsState());
            }
            if (GET_AUDIO_EFFECTS.equals(command)) return ok(effectsState());
            if (QUEUE_PLAY_NEXT.equals(command)) {
                int index = args.getInt("index", -1);
                if (index < 0 || index >= player.getMediaItemCount()) return error(SessionError.ERROR_BAD_VALUE);
                int current = player.getCurrentMediaItemIndex();
                int[] order = QueueOrders.shuffleOrder(player);
                if (order != null && index != current) QueueOrders.apply(player, QueueRestorePlan.playNext(order, current, index));
                return ok(Bundle.EMPTY);
            }
            if (GET_CACHE_INFO.equals(command)) return cacheInfo(false);
            if (CLEAR_CACHE.equals(command)) return cacheInfo(true);
            return error(SessionError.ERROR_NOT_SUPPORTED);
        }
    }

    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }
    private static long positive(long value) { return value > 0 && value != C.TIME_UNSET ? value : 0; }
}
