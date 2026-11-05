package dev.lilt.player;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import dev.lilt.player.audio.QueueOrders;
import java.util.ArrayList;

/**
 * Two-player crossfade. The next song is prepared on the spare player a little before the fade
 * window (longer ahead for streams), and the ramp only starts once that player is actually
 * playing; the outgoing song stays at full level until then. Anything unexpected (not ready in
 * time, a stall, an error, the user changing the queue) cancels the overlap and the main player
 * continues with its normal gapless transition.
 */
@androidx.media3.common.util.UnstableApi
final class CrossfadeEngine {
    interface Host {
        boolean debugEnabled();
        ExoPlayer active();
        ExoPlayer spare();
        /** False while the sleep timer is fading out or stopping at the end of the song. */
        boolean allowCrossfade();
        boolean isRemote(MediaItem item);
        /** The spare player is about to prepare {@code destination} for an overlap. */
        void staging(ExoPlayer incoming, MediaItem destination);
        /** Applies normalisation for the incoming player's song and returns its target volume. */
        float prepareVolume(ExoPlayer incoming);
        void promote(ExoPlayer incoming, ExoPlayer outgoing);
    }
    private final Host host;
    private final Handler handler;
    private int seconds, nextIndex=C.INDEX_UNSET;
    private ExoPlayer outgoing,incoming;
    private long stageStarted,fadeStarted,fadeMillis,lastGainLog,prepareTimeout;
    private float baseVolume,incomingTarget=1f;
    private boolean startRequested,fading,closed;
    private String sourceId="",destinationId="",failedPair="";
    private final Runnable tick=new Runnable(){public void run(){
        if(closed)return;
        update();
        schedule();
    }};
    CrossfadeEngine(Host host,Handler handler){this.host=host;this.handler=handler;}
    void setSeconds(int value){cancel();seconds=value;failedPair="";schedule();}
    boolean overlapping(){return incoming!=null;}
    /** Called for each event batch of the active player; also re-arms the timer. */
    void activeEvent(Player.Events events){
        if(incoming!=null&&(events.contains(Player.EVENT_TIMELINE_CHANGED)||events.contains(Player.EVENT_REPEAT_MODE_CHANGED)
                ||events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)||!host.active().getPlayWhenReady()
                ||host.active().getPlaybackSuppressionReason()!=Player.PLAYBACK_SUPPRESSION_REASON_NONE))cancel();
        schedule();
    }
    /** Ticks quickly near and during a transition, slowly otherwise, and not at all when idle. */
    void schedule(){
        if(closed)return;
        handler.removeCallbacks(tick);
        ExoPlayer active=host.active();
        long delay=fading?CrossfadeRules.FADE_TICK_MS
                :CrossfadeRules.tickDelayMillis(incoming!=null,active.isPlaying()&&host.allowCrossfade(),seconds,active.getDuration(),active.getCurrentPosition());
        if(delay>=0)handler.postDelayed(tick,delay);
    }
    void playerError(ExoPlayer source,int code){
        if(source==incoming)fail("destination_error_"+code);
        else if(source==outgoing)fail("source_error_"+code);
    }
    void positionDiscontinuity(int reason){
        if(incoming==null)return;
        if(reason==Player.DISCONTINUITY_REASON_AUTO_TRANSITION&&fading&&incoming.isPlaying()
                &&outgoing.getCurrentMediaItemIndex()==nextIndex)finish();
        else cancel();
    }
    private void finish(){
        if(incoming==null||!incoming.isPlaying()||incoming.getPlaybackState()!=Player.STATE_READY){fail("destination_not_playing");return;}
        ExoPlayer old=outgoing,next=incoming;float target=incomingTarget;
        reset();
        old.pause();old.setVolume(1f);next.setVolume(target);
        debug("handoff destinationMs="+next.getCurrentPosition()+" playing="+next.isPlaying());host.promote(next,old);old.clearMediaItems();
    }
    private void update(){
        ExoPlayer active=host.active();
        if(incoming!=null){
            if(active!=outgoing||!outgoing.getPlayWhenReady()||outgoing.getPlaybackSuppressionReason()!=Player.PLAYBACK_SUPPRESSION_REASON_NONE||!host.allowCrossfade()){cancel();return;}
            if(incoming.getPlayerError()!=null){fail("destination_error_"+incoming.getPlayerError().errorCode);return;}
            if(outgoing.getPlayerError()!=null){fail("source_error_"+outgoing.getPlayerError().errorCode);return;}
            if(!outgoing.isPlaying()){fail("source_not_playing");return;}
            long now=SystemClock.elapsedRealtime();
            long remaining=outgoing.getDuration()-outgoing.getCurrentPosition();
            if(!startRequested){
                // The incoming song must be buffered (READY) before anything is heard; else hard cut.
                if(incoming.getPlaybackState()!=Player.STATE_READY && now-stageStarted>prepareTimeout){fail("prepare_timeout");return;}
                if(incoming.getPlaybackState()!=Player.STATE_READY){if(remaining<650)fail("not_buffered");return;}
                long requested=CrossfadeRules.durationMillis(seconds,outgoing.getDuration(),incoming.getDuration());
                if(remaining>requested+300)return;
                if(remaining<650||requested<300){fail("too_late");return;}
                incomingTarget=host.prepareVolume(incoming);
                // Starting is asynchronous. Do not cancel, or attenuate the source, in this turn.
                startRequested=true;stageStarted=now;incoming.play();debug("start_requested remainingMs="+remaining);return;
            }
            if(!fading){
                if(!incoming.isPlaying()||incoming.getPlaybackState()!=Player.STATE_READY){
                    if(now-stageStarted>2000||remaining<500)fail("start_timeout");
                    return;
                }
                fadeMillis=CrossfadeRules.safeFadeMillis(seconds,outgoing.getDuration(),incoming.getDuration(),remaining);
                if(fadeMillis<300){fail("too_late");return;}
                fading=true;fadeStarted=now;lastGainLog=0;debug("fading durationMs="+fadeMillis+" destinationMs="+incoming.getCurrentPosition());
            }
            if(!incoming.isPlaying()||incoming.getPlaybackState()!=Player.STATE_READY){fail("destination_stalled");return;}
            long elapsed=now-fadeStarted;
            float progress=CrossfadeRules.incomingGain(elapsed,fadeMillis);
            outgoing.setVolume(baseVolume*CrossfadeRules.equalPowerOut(progress));
            incoming.setVolume(incomingTarget*CrossfadeRules.equalPowerIn(progress));
            if(host.debugEnabled() && now-lastGainLog>=500){lastGainLog=now;debug("gain source="+outgoing.getVolume()+" destination="+incoming.getVolume()+" destinationMs="+incoming.getCurrentPosition());}
            if(elapsed>=fadeMillis)finish();
            return;
        }
        if(seconds==0||!host.allowCrossfade()||!active.isPlaying()||active.getRepeatMode()==Player.REPEAT_MODE_ONE
                ||active.getPlaybackParameters().speed!=1f||active.getDuration()<=0
                ||active.getDuration()==C.TIME_UNSET||!active.hasNextMediaItem()||active.getCurrentMediaItem()==null)return;
        int next=active.getNextMediaItemIndex();
        if(next==C.INDEX_UNSET||next==active.getCurrentMediaItemIndex())return;
        MediaItem destinationItem=active.getMediaItemAt(next);
        boolean remote=host.isRemote(destinationItem);
        if(!CrossfadeRules.shouldStage(seconds,active.getDuration(),active.getCurrentPosition(),remote))return;
        String source=active.getCurrentMediaItem().mediaId,destination=destinationItem.mediaId;
        if(failedPair.equals(source+"\n"+destination))return;
        outgoing=active;incoming=host.spare();nextIndex=next;sourceId=source;destinationId=destination;baseVolume=active.getVolume();
        prepareTimeout=CrossfadeRules.prepareTimeoutMillis(remote);incomingTarget=1f;
        ArrayList<MediaItem> queue=new ArrayList<>();
        for(int i=0;i<active.getMediaItemCount();i++)queue.add(active.getMediaItemAt(i));
        incoming.setVolume(0);incoming.setPlayWhenReady(false);
        host.staging(incoming,destinationItem);
        incoming.setMediaItems(queue,next,0);
        // Keep "Up next" identical after the handoff: copy the shuffled play order too.
        QueueOrders.apply(incoming,QueueOrders.shuffleOrder(active));
        incoming.setRepeatMode(active.getRepeatMode());
        incoming.setShuffleModeEnabled(active.getShuffleModeEnabled());incoming.setPlaybackParameters(active.getPlaybackParameters());
        incoming.prepare();stageStarted=SystemClock.elapsedRealtime();debug("preparing remote="+remote);
    }
    private void debug(String message){if(host.debugEnabled())Log.d("AuraPlayback",message);}
    private void fail(String code){
        failedPair=sourceId+"\n"+destinationId;
        Log.w("AuraPlayback","Crossfade fallback: "+code);
        cancel();
    }
    private void reset(){incoming=outgoing=null;nextIndex=C.INDEX_UNSET;startRequested=fading=false;sourceId=destinationId="";}
    void cancel(){
        ExoPlayer destination=incoming,source=outgoing;
        reset();
        if(destination!=null){destination.pause();destination.clearMediaItems();destination.setVolume(1);}
        if(source!=null)source.setVolume(baseVolume);
    }
    void close(){closed=true;handler.removeCallbacks(tick);cancel();}
}
