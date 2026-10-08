package dev.lilt.player;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.media3.common.Player;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionError;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

@androidx.media3.common.util.UnstableApi
public final class PlaybackService extends MediaSessionService {
    public static final SessionCommand SET_SLEEP_TIMER = new SessionCommand("dev.lilt.player.SET_SLEEP_TIMER", Bundle.EMPTY);
    public static final SessionCommand GET_SLEEP_TIMER = new SessionCommand("dev.lilt.player.GET_SLEEP_TIMER", Bundle.EMPTY);
    public static final SessionCommand SET_PLAYBACK_OPTIONS = new SessionCommand("dev.lilt.player.SET_PLAYBACK_OPTIONS", Bundle.EMPTY);
    public static final SessionCommand GET_PLAYBACK_OPTIONS = new SessionCommand("dev.lilt.player.GET_PLAYBACK_OPTIONS", Bundle.EMPTY);
    private android.content.SharedPreferences options;
    private CrossfadeEngine crossfade;
    private int crossfadeSeconds;
    private boolean autoplayEnabled;
    private ExoPlayer spare;
    private final java.util.concurrent.ExecutorService recommendationWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean recommendationPending, destroyed;
    private long lastRecommendationAttempt;
    private String recentId="",retriedErrorId="";
    private final java.util.ArrayList<String> recent=new java.util.ArrayList<>();
    private final Runnable recommendationTick=new Runnable(){public void run(){maybeAutoplay();handler.postDelayed(this,1000);}};
    private long sleepDeadline;
    private ExoPlayer player;
    private MediaSession session;
    private LocalBitmapLoader bitmapLoader;
    private PlaybackStore store;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable sleepTimer = new Runnable() {
        @Override public void run() {
            if (sleepDeadline == 0) return;
            long remaining = sleepDeadline - SystemClock.elapsedRealtime();
            if (remaining > 0) { handler.postDelayed(this, remaining); return; }
            sleepDeadline = 0;
            if(crossfade!=null)crossfade.cancel();
            player.pause();
            store.save(player);
        }
    };
    private final Runnable checkpoint=new Runnable(){@Override public void run(){if(player.isPlaying()){store.save(player);handler.postDelayed(this,5000);}}};
    @Override public void onCreate() {
        super.onCreate();
        player=new ExoPlayer.Builder(this).build();
        spare=new ExoPlayer.Builder(this).build();
        spare.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),false);
        spare.setHandleAudioBecomingNoisy(true);
        spare.setWakeMode(C.WAKE_MODE_LOCAL);
        options=getSharedPreferences("playback_options",MODE_PRIVATE);
        crossfadeSeconds=options.getInt("crossfadeSeconds",0);
        if(!CrossfadeRules.validSeconds(crossfadeSeconds)) crossfadeSeconds=0;
        autoplayEnabled=options.getBoolean("autoplayEnabled",true);
        String savedRecent=options.getString("recent","");
        if(!savedRecent.isEmpty())java.util.Collections.addAll(recent,savedRecent.split("\n"));
        // Keep Media3's native spatial track selection and Android's AUTO behavior.
        // USAGE_MEDIA permits compatible multichannel content to be spatialized by
        // the system; do not force stereo content or pre-spatialized audio through it.
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        store=new PlaybackStore(this);store.restore(player);PlayerWidget.publish(this,player);
        Player.Listener playbackListener=new Player.Listener(){@Override public void onEvents(Player p,Player.Events events){
            if(p!=player)return;
            if(events.size()==1 && events.contains(Player.EVENT_VOLUME_CHANGED))return;
            if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
                    &&(events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)||events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)))
                android.util.Log.d("AuraPlayback","Intent playing="+player.getPlayWhenReady()+" suppression="+player.getPlaybackSuppressionReason());
            if(crossfade!=null)crossfade.activeEvent(events);
            boolean mediaChanged=events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)||events.contains(Player.EVENT_MEDIA_METADATA_CHANGED);
            boolean stateChanged=events.contains(Player.EVENT_IS_PLAYING_CHANGED)||events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                    ||events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)||events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED);
            if(mediaChanged||events.contains(Player.EVENT_IS_PLAYING_CHANGED))rememberPlaying();
            // Decoder/loading/device events do not change the saved queue or widget content.
            if(mediaChanged||stateChanged||events.contains(Player.EVENT_TIMELINE_CHANGED)
                    ||events.contains(Player.EVENT_POSITION_DISCONTINUITY)||events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                    ||events.contains(Player.EVENT_REPEAT_MODE_CHANGED))store.save(player);
            if(mediaChanged||stateChanged||events.contains(Player.EVENT_TIMELINE_CHANGED))PlayerWidget.publish(PlaybackService.this,player);
            if(stateChanged){handler.removeCallbacks(checkpoint);if(player.isPlaying())handler.postDelayed(checkpoint,5000);}
        }};
        player.addListener(playbackListener);spare.addListener(playbackListener);
        attachDiscontinuityListener(player);attachDiscontinuityListener(spare);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        bitmapLoader=new LocalBitmapLoader(this);session=new MediaSession.Builder(this,player).setBitmapLoader(bitmapLoader).setSessionActivity(open).setCallback(new MediaSession.Callback() {
            @Override public MediaSession.ConnectionResult onConnect(MediaSession mediaSession, MediaSession.ControllerInfo controller) {
                if (!trusted(controller)) return MediaSession.ConnectionResult.reject();
                MediaSession.ConnectionResult base = MediaSession.Callback.super.onConnect(mediaSession,controller);
                return new MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                        .setAvailableSessionCommands(base.availableSessionCommands.buildUpon().add(SET_SLEEP_TIMER).add(GET_SLEEP_TIMER).add(SET_PLAYBACK_OPTIONS).add(GET_PLAYBACK_OPTIONS).build())
                        .setAvailablePlayerCommands(base.availablePlayerCommands).build();
            }
            @Override public ListenableFuture<SessionResult> onCustomCommand(MediaSession mediaSession, MediaSession.ControllerInfo controller, SessionCommand command, Bundle args) {
                if (!trusted(controller)) return Futures.immediateFuture(new SessionResult(SessionError.ERROR_PERMISSION_DENIED));
                if(SET_PLAYBACK_OPTIONS.equals(command) || GET_PLAYBACK_OPTIONS.equals(command)) {
                    if(SET_PLAYBACK_OPTIONS.equals(command)) {
                        int requested=args.getInt("crossfadeSeconds",crossfadeSeconds);
                        if(!CrossfadeRules.validSeconds(requested))return Futures.immediateFuture(new SessionResult(SessionError.ERROR_BAD_VALUE));
                        crossfadeSeconds=requested;autoplayEnabled=args.getBoolean("autoplayEnabled",autoplayEnabled);
                        crossfade.setSeconds(crossfadeSeconds);
                        options.edit().putInt("crossfadeSeconds",crossfadeSeconds).putBoolean("autoplayEnabled",autoplayEnabled).apply();
                    }
                    Bundle result=new Bundle();result.putInt("crossfadeSeconds",crossfadeSeconds);result.putBoolean("autoplayEnabled",autoplayEnabled);
                    return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS,result));
                }
                if (SET_SLEEP_TIMER.equals(command)) {
                    int minutes = args.getInt("minutes", -1);
                    if (minutes < 0 || minutes > 60)
                        return Futures.immediateFuture(new SessionResult(SessionError.ERROR_BAD_VALUE));
                    handler.removeCallbacks(sleepTimer);
                    sleepDeadline = minutes == 0 ? 0 : SystemClock.elapsedRealtime() + minutes * 60_000L;
                    if (sleepDeadline != 0) handler.postDelayed(sleepTimer, minutes * 60_000L);
                } else if (!GET_SLEEP_TIMER.equals(command)) {
                    return Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
                }
                Bundle result = new Bundle();
                result.putLong("remainingMillis", Math.max(0, sleepDeadline-SystemClock.elapsedRealtime()));
                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS,result));
            }
        }).build();
        crossfade=new CrossfadeEngine(new CrossfadeEngine.Host(){
            public boolean debugEnabled(){return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
            public ExoPlayer active(){return player;}
            public ExoPlayer spare(){return spare;}
            public void promote(ExoPlayer incoming,ExoPlayer outgoing){
                outgoing.setAudioAttributes(outgoing.getAudioAttributes(),false);
                player=incoming;spare=outgoing;
                incoming.setAudioAttributes(incoming.getAudioAttributes(),true);
                session.setPlayer(incoming);rememberPlaying();store.save(incoming);PlayerWidget.publish(PlaybackService.this,incoming);
            }
        },handler);
        crossfade.setSeconds(crossfadeSeconds);
        handler.post(recommendationTick);
    }
    @Override public void onTaskRemoved(Intent rootIntent){
        // Explicit user preference: dismissing this app ends background playback.
        android.util.Log.i("AuraPlayback","Task dismissed: stopping both decoders");
        destroyed=true;
        handler.removeCallbacksAndMessages(null);
        if(crossfade!=null)crossfade.close();
        player.pause();spare.pause();store.save(player);
        player.stop();spare.stop();
        PlayerWidget.publish(this,player);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
    private void attachDiscontinuityListener(ExoPlayer source){
        source.addListener(new Player.Listener(){
            @Override public void onPlayWhenReadyChanged(boolean wanted,int reason){
                if(source==player && (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)
                    android.util.Log.d("AuraPlayback","Play intent="+wanted+" changeReason="+reason+" suppression="+source.getPlaybackSuppressionReason());
            }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error){
                // Error messages/cause stacks may contain private paths; record codes only.
                android.util.Log.w("GeetlyPlayback","Decoder error code="+error.errorCode+" active="+(source==player));
                options.edit().putInt("lastErrorCode",error.errorCode).putLong("lastErrorTime",System.currentTimeMillis()).apply();
                if(crossfade!=null)crossfade.playerError(source,error.errorCode);
                if(source!=player||destroyed)return;
                androidx.media3.common.MediaItem item=source.getCurrentMediaItem();
                String id=item==null?"":item.mediaId;
                boolean wanted=source.getPlayWhenReady();long position=source.getCurrentPosition();
                // A single retry for unspecified local I/O. Permission/missing/corrupt errors
                // stay paused; never loop, skip the user's queue or silently restart playback.
                if(error.errorCode==androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                        &&wanted&&!id.isEmpty()&&!id.equals(retriedErrorId)){
                    retriedErrorId=id;
                    handler.postDelayed(()->{
                        if(destroyed||source!=player||!source.getPlayWhenReady()||source.getCurrentMediaItem()==null
                                ||!id.equals(source.getCurrentMediaItem().mediaId)||source.getPlayerError()==null)return;
                        source.seekTo(position);source.prepare();
                    },300);
                }else{
                    source.pause();store.save(source);
                    android.widget.Toast.makeText(PlaybackService.this,"Couldn't play this song. Try another track.",android.widget.Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){
                if(source==player && crossfade!=null)crossfade.positionDiscontinuity(reason);
            }
        });
    }
    private void rememberPlaying(){
        androidx.media3.common.MediaItem item=player.getCurrentMediaItem();
        if(!player.isPlaying()||item==null||item.mediaId.equals(recentId))return;
        recentId=item.mediaId;recent.remove(recentId);recent.add(0,recentId);
        while(recent.size()>50)recent.remove(recent.size()-1);
        options.edit().putString("recent",android.text.TextUtils.join("\n",recent)).apply();
    }
    private void maybeAutoplay(){
        if(destroyed||recommendationPending||SystemClock.elapsedRealtime()-lastRecommendationAttempt<5000||!autoplayEnabled||crossfade.overlapping()
                ||player.getRepeatMode()!=Player.REPEAT_MODE_OFF||!player.getPlayWhenReady()
                ||player.hasNextMediaItem()||player.getMediaItemCount()==0||player.getMediaItemCount()>=2000)return;
        long duration=player.getDuration();
        if(player.getPlaybackState()!=Player.STATE_ENDED && (duration<=0||duration-player.getCurrentPosition()>20_000))return;
        androidx.media3.common.MediaItem seedItem=player.getCurrentMediaItem();if(seedItem==null)return;
        final String seedId=seedItem.mediaId;final ExoPlayer requestedPlayer=player;
        final int count=player.getMediaItemCount();final int index=player.getCurrentMediaItemIndex();
        final java.util.ArrayList<String> history=new java.util.ArrayList<>(recent);
        recommendationPending=true;lastRecommendationAttempt=SystemClock.elapsedRealtime();
        recommendationWorker.execute(()->{
            androidx.media3.common.MediaItem next=null;
            try{
                Library library=new Library(this);MoodStore moods=new MoodStore(this);
                java.util.List<Track> tracks=library.scan();java.util.ArrayList<AutoplayRules.Song> songs=new java.util.ArrayList<>();
                AutoplayRules.Song seed=null;
                for(Track track:tracks){AutoplayRules.Song song=new AutoplayRules.Song(track.id,track.album,track.artist,moods.moodsFor(track.id));songs.add(song);if(track.id.equals(seedId))seed=song;}
                // Deleted seed may still have playable metadata, without granting it a mood.
                if(seed==null)seed=new AutoplayRules.Song(seedId,String.valueOf(seedItem.mediaMetadata.albumTitle),String.valueOf(seedItem.mediaMetadata.artist),java.util.Collections.emptySet());
                String choice=AutoplayRules.choose(songs,seed,java.util.Collections.emptySet(),history,new java.util.Random());
                if(choice!=null)for(Track track:tracks)if(track.id.equals(choice)){next=track.item(library.artwork(track));break;}
            }catch(RuntimeException unavailable){ /* Revoked media permission or unavailable provider: keep original queue. */ }
            final androidx.media3.common.MediaItem chosen=next;
            handler.post(()->{
                recommendationPending=false;
                if(destroyed||chosen==null||!autoplayEnabled||player!=requestedPlayer||crossfade.overlapping()
                        ||player.getRepeatMode()!=Player.REPEAT_MODE_OFF||!player.getPlayWhenReady()
                        ||player.getMediaItemCount()!=count||player.getCurrentMediaItemIndex()!=index
                        ||player.getCurrentMediaItem()==null||!seedId.equals(player.getCurrentMediaItem().mediaId)||player.hasNextMediaItem())return;
                boolean ended=player.getPlaybackState()==Player.STATE_ENDED;
                player.addMediaItem(chosen);
                if(ended){player.seekTo(count,0);player.prepare();player.play();}
            });
        });
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo info) {
        // Only this app and trusted system clients may control the session.
        return trusted(info) ? session : null;
    }
    private boolean trusted(MediaSession.ControllerInfo info) { return info.getPackageName().equals(getPackageName()) || info.isTrusted(); }
    @Override public void onDestroy() {if(bitmapLoader!=null)bitmapLoader.close(); destroyed=true;recommendationWorker.shutdownNow();handler.removeCallbacksAndMessages(null);if(crossfade!=null)crossfade.close();store.save(player);store.close();session.release();player.release();spare.release();PlayerWidget.prefs(this).edit().putBoolean("playing",false).apply();PlayerWidget.refresh(this);super.onDestroy(); }
}
