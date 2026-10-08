package dev.lilt.player;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.ArrayList;

/** The primary remains at full gain until the destination decoder actually plays. */
@androidx.media3.common.util.UnstableApi
final class CrossfadeEngine {
    interface Host {
        boolean debugEnabled();
        ExoPlayer active();
        ExoPlayer spare();
        void promote(ExoPlayer incoming, ExoPlayer outgoing);
    }
    private final Host host;
    private final Handler handler;
    private int seconds, nextIndex=C.INDEX_UNSET;
    private ExoPlayer outgoing,incoming;
    private long stageStarted,fadeStarted,fadeMillis,lastGainLog;
    private float baseVolume;
    private boolean startRequested,fading,closed;
    private String sourceId="",destinationId="",failedPair="";
    private final Runnable tick=new Runnable(){public void run(){
        if(closed)return;
        update();
        if(!closed)handler.postDelayed(this,incoming!=null||host.active().isPlaying()?100:1000);
    }};
    CrossfadeEngine(Host host,Handler handler){this.host=host;this.handler=handler;handler.post(tick);}
    void setSeconds(int value){cancel();seconds=value;failedPair="";}
    boolean overlapping(){return incoming!=null;}
    void activeEvent(Player.Events events){
        if(incoming==null)return;
        if(events.contains(Player.EVENT_TIMELINE_CHANGED)||events.contains(Player.EVENT_REPEAT_MODE_CHANGED)
                ||events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)||!host.active().getPlayWhenReady()
                ||host.active().getPlaybackSuppressionReason()!=Player.PLAYBACK_SUPPRESSION_REASON_NONE)cancel();
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
        ExoPlayer old=outgoing,next=incoming;
        reset();
        old.pause();old.setVolume(baseVolume);next.setVolume(baseVolume);
        debug("handoff destinationMs="+next.getCurrentPosition()+" playing="+next.isPlaying());host.promote(next,old);old.clearMediaItems();
    }
    private void update(){
        ExoPlayer active=host.active();
        if(incoming!=null){
            if(active!=outgoing||!outgoing.getPlayWhenReady()||outgoing.getPlaybackSuppressionReason()!=Player.PLAYBACK_SUPPRESSION_REASON_NONE){cancel();return;}
            if(incoming.getPlayerError()!=null){fail("destination_error_"+incoming.getPlayerError().errorCode);return;}
            if(outgoing.getPlayerError()!=null){fail("source_error_"+outgoing.getPlayerError().errorCode);return;}
            if(!outgoing.isPlaying()){fail("source_not_playing");return;}
            long now=SystemClock.elapsedRealtime();
            long remaining=outgoing.getDuration()-outgoing.getCurrentPosition();
            if(!startRequested){
                if(incoming.getPlaybackState()!=Player.STATE_READY && now-stageStarted>6000){fail("prepare_timeout");return;}
                if(incoming.getPlaybackState()!=Player.STATE_READY)return;
                long requested=CrossfadeRules.durationMillis(seconds,outgoing.getDuration(),incoming.getDuration());
                if(remaining>requested+300)return;
                if(remaining<650||requested<300){fail("too_late");return;}
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
            outgoing.setVolume(baseVolume*CrossfadeRules.outgoingGain(elapsed,fadeMillis));
            incoming.setVolume(baseVolume*CrossfadeRules.incomingGain(elapsed,fadeMillis));
            if(host.debugEnabled() && now-lastGainLog>=500){lastGainLog=now;debug("gain source="+outgoing.getVolume()+" destination="+incoming.getVolume()+" destinationMs="+incoming.getCurrentPosition());}
            if(elapsed>=fadeMillis)finish();
            return;
        }
        if(seconds==0||!active.isPlaying()||active.getRepeatMode()==Player.REPEAT_MODE_ONE
                ||active.getPlaybackParameters().speed!=1f||active.getDuration()<=0
                ||active.getDuration()==C.TIME_UNSET||!active.hasNextMediaItem())return;
        long window=Math.min(seconds*1000L,active.getDuration()/2);
        if(window<300||active.getDuration()-active.getCurrentPosition()>window+3000)return;
        int next=active.getNextMediaItemIndex();
        if(next==C.INDEX_UNSET||next==active.getCurrentMediaItemIndex())return;
        String source=active.getCurrentMediaItem().mediaId,destination=active.getMediaItemAt(next).mediaId;
        if(failedPair.equals(source+"\n"+destination))return;
        outgoing=active;incoming=host.spare();nextIndex=next;sourceId=source;destinationId=destination;baseVolume=active.getVolume();
        ArrayList<MediaItem> queue=new ArrayList<>();
        for(int i=0;i<active.getMediaItemCount();i++)queue.add(active.getMediaItemAt(i));
        incoming.setMediaItems(queue,next,0);incoming.setRepeatMode(active.getRepeatMode());
        incoming.setShuffleModeEnabled(active.getShuffleModeEnabled());incoming.setPlaybackParameters(active.getPlaybackParameters());
        incoming.setVolume(0);incoming.setPlayWhenReady(false);incoming.prepare();stageStarted=SystemClock.elapsedRealtime();debug("preparing");
    }
    private void debug(String message){if(host.debugEnabled())Log.d("AuraPlayback",message);}
    private void fail(String code){
        failedPair=sourceId+"\n"+destinationId;
        Log.w("GeetlyPlayback","Crossfade fallback: "+code);
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
