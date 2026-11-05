package dev.lilt.player;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import dev.lilt.player.audio.MediaIds;
import dev.lilt.player.audio.QueueOrders;
import dev.lilt.player.audio.QueueRestorePlan;
import dev.lilt.player.stream.RemoteTrackStore;
import java.util.*;
import java.util.function.Consumer;

/**
 * Persists the playback queue (items, current index, position, shuffle/repeat and the shuffled
 * play order) and restores it on start-up. Reading, decoding and resolving happen on the store's
 * own thread; only the final player calls run on the main thread.
 *
 * <p>Device songs need the media permission; catalogue songs ("audius:" ids) resolve through
 * {@link RemoteTrackStore} for fresh metadata, falling back to what the snapshot itself recorded
 * (stream URL, title, artist, artwork). Entries that cannot be resolved are dropped gracefully
 * ({@link QueueRestorePlan}).
 */
@androidx.media3.common.util.UnstableApi
public final class PlaybackStore {
    /** A saved queue ready for a player. */
    public static final class Restored {
        public final List<MediaItem> items;
        public final int index, repeat;
        public final long position;
        public final boolean shuffle;
        final int[] shuffleOrder;
        Restored(List<MediaItem> items,int index,long position,boolean shuffle,int repeat,int[] shuffleOrder){
            this.items=Collections.unmodifiableList(items);this.index=index;this.position=position;this.shuffle=shuffle;this.repeat=repeat;this.shuffleOrder=shuffleOrder;
        }
    }

    private final Context context;
    private final android.content.SharedPreferences prefs;
    private boolean initialized;
    private Timeline savedTimeline;
    private List<QueueSnapshot.Entry> savedEntries=Collections.emptyList();
    private String savedOrder="";
    private final java.util.concurrent.ExecutorService writer=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"AuraQueueStore"));
    private QueueSnapshot pending;
    private String pendingOrder="";
    private boolean writing;

    public PlaybackStore(Context context) {
        this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("playback",Context.MODE_PRIVATE);
    }

    private synchronized void enqueue(QueueSnapshot snapshot,String order) {
        pending=snapshot;pendingOrder=order;
        if(writing)return;
        writing=true;
        try{writer.execute(()-> {
            for(;;) {
                QueueSnapshot latest;String latestOrder;
                synchronized(this){latest=pending;latestOrder=pendingOrder;pending=null;if(latest==null){writing=false;return;}}
                try{prefs.edit().putString("queue",Base64.encodeToString(latest.encode(),Base64.NO_WRAP)).putString("shuffleOrder",latestOrder).apply();}
                catch(Exception ignored) { /* Keep playback independent of storage errors. */ }
            }
        });}catch(java.util.concurrent.RejectedExecutionException closed){writing=false;}
    }

    /** Lets queued writes finish, then stops the store's thread. */
    public void close(){writer.shutdown();}

    /** Reads the saved queue off the main thread and delivers it (or null) on {@code main}. */
    public void restoreAsync(Handler main,Consumer<Restored> done){
        try{writer.execute(()->{
            Restored restored=null;
            try{restored=load();}catch(Exception corrupt){ /* A corrupt/old record does not block the library. */ }
            final Restored result=restored;
            main.post(()->done.accept(result));
        });}catch(java.util.concurrent.RejectedExecutionException closed){main.post(()->done.accept(null));}
    }

    /** Decodes and resolves the saved queue. Blocking: worker threads only. */
    public Restored load() throws java.io.IOException {
        String saved=prefs.getString("queue",null);if(saved==null)return null;
        QueueSnapshot snapshot=QueueSnapshot.decode(Base64.decode(saved,Base64.NO_WRAP));
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        boolean localAllowed=context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
        int n=snapshot.entries.size();
        boolean[] keep=new boolean[n];MediaItem[] items=new MediaItem[n];
        RemoteTrackStore remote=null;
        for(int i=0;i<n;i++) {
            QueueSnapshot.Entry e=snapshot.entries.get(i);
            try{
                if(MediaIds.isRemote(e.id,e.uri)) {
                    if(remote==null)remote=new RemoteTrackStore(context);
                    Track track=remote.get(e.id);
                    if(track==null&&MediaIds.isHttp(e.uri))
                        track=Track.remote(e.id,e.title,e.artist,e.album,e.uri,e.artwork.isEmpty()?null:e.artwork,0,"");
                    if(track!=null){items[i]=track.item(null);keep[i]=true;}
                } else if(localAllowed&&MediaIds.isLocalMedia(e.uri)) {
                    MediaMetadata.Builder meta=new MediaMetadata.Builder().setTitle(MusicRules.displayTitle(e.title)).setArtist(e.artist).setAlbumTitle(e.album);
                    meta.setArtworkUri(Uri.parse(e.artwork.startsWith("content://")?e.artwork:e.uri));
                    items[i]=new MediaItem.Builder().setMediaId(e.id).setUri(e.uri).setMediaMetadata(meta.build()).build();keep[i]=true;
                }
            }catch(RuntimeException unresolvable){ keep[i]=false; }
        }
        QueueRestorePlan plan=QueueRestorePlan.plan(keep,snapshot.index,snapshot.position,QueueRestorePlan.parseOrder(prefs.getString("shuffleOrder",null)));
        if(plan.isEmpty())return null;
        List<MediaItem> list=new ArrayList<>(plan.kept.length);
        for(int k:plan.kept)list.add(items[k]);
        return new Restored(list,plan.index,plan.position,snapshot.shuffle,snapshot.repeat,plan.shuffleOrder);
    }

    /** Puts a restored queue on the player, paused. Reopening the app must never start sound. */
    public void apply(ExoPlayer player,Restored restored){
        if(restored==null||restored.items.isEmpty())return;
        int index=Math.min(restored.index,restored.items.size()-1);
        player.setMediaItems(restored.items,index,restored.position);
        QueueOrders.apply(player,restored.shuffleOrder);
        player.setRepeatMode(restored.repeat);player.setShuffleModeEnabled(restored.shuffle);
        player.setPlayWhenReady(false);
        // Device files prepare instantly; a stream is only opened when the user presses play.
        MediaItem current=restored.items.get(index);
        String uri=current.localConfiguration==null?"":current.localConfiguration.uri.toString();
        if(!MediaIds.isRemote(current.mediaId,uri))player.prepare();
        initialized=true;
    }

    public void save(ExoPlayer player) {
        if(player.getMediaItemCount()>0)initialized=true;
        if(!initialized)return;
        try {
            Timeline timeline=player.getCurrentTimeline();
            if(timeline!=savedTimeline) {
                List<QueueSnapshot.Entry> entries=new ArrayList<>();
                for(int i=0;i<Math.min(player.getMediaItemCount(),2000);i++) {
                    MediaItem item=player.getMediaItemAt(i);if(item.localConfiguration==null)continue;
                    MediaMetadata meta=item.mediaMetadata;
                    entries.add(new QueueSnapshot.Entry(item.mediaId,item.localConfiguration.uri.toString(),string(meta.title),string(meta.artist),string(meta.albumTitle),meta.artworkUri==null?"":meta.artworkUri.toString()));
                }
                int[] order=entries.size()==player.getMediaItemCount()?QueueOrders.shuffleOrder(player):null;
                savedEntries=entries;savedTimeline=timeline;savedOrder=QueueRestorePlan.formatOrder(order);
            }
            QueueSnapshot snapshot=new QueueSnapshot(savedEntries,player.getCurrentMediaItemIndex(),player.getCurrentPosition(),player.getShuffleModeEnabled(),player.getRepeatMode());
            enqueue(snapshot,savedOrder);
        } catch(Exception ignored) { /* Persistence failure should not interrupt music. */ }
    }
    private String string(CharSequence text) {return text==null?"":text.toString();}
}
