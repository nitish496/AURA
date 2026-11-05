package dev.lilt.player;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.AtomicFile;
import android.util.Base64;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import dev.lilt.player.audio.MediaIds;
import dev.lilt.player.audio.QueueOrders;
import dev.lilt.player.audio.QueueRestorePlan;
import dev.lilt.player.stream.RemoteTrackStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Persists the playback queue and restores it on start-up.
 *
 * <p>Storage: the queue itself ({@link QueueSnapshot} + shuffled play order) lives in
 * {@code files/queue.bin}, written atomically and only when the queue changes. The frequently
 * changing part (current index, position, shuffle, repeat) is a tiny "state" string in the
 * "playback" preferences, so the 5-second position checkpoint never rewrites a large file. Queues
 * longer than 2000 items keep a 2000-item window around the current song. All disk work runs on
 * the store's own thread; only player calls run on the main thread.
 *
 * <p>Restore: device songs need the media permission; catalogue songs ("audius:" ids) resolve
 * through {@link RemoteTrackStore} for fresh metadata, falling back to what the snapshot recorded
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

    private static final int FILE_MAGIC=0x41513032, FILE_VERSION=1, MAX_ITEMS=2000; // "AQ02"
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->new Thread(r,"AuraQueueStore"));
    private AtomicFile file; // created on the writer thread (getFilesDir may touch the disk)

    // Main thread.
    private boolean initialized;
    private Timeline savedTimeline;
    private List<QueueSnapshot.Entry> savedEntries=Collections.emptyList();
    private int windowStart,windowEnd;
    private int[] entryOf=new int[0];
    private long queueVersion;

    // Guarded by this.
    private byte[] pendingFile;
    private String pendingState;
    private boolean writing;

    public PlaybackStore(Context context) {
        this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("playback",Context.MODE_PRIVATE);
    }

    /** Lets queued writes finish, then stops the store's thread. */
    public void close(){writer.shutdown();}

    // ------------------------------------------------------------------ save

    public void save(ExoPlayer player) {
        if(player.getMediaItemCount()>0)initialized=true;
        if(!initialized)return;
        try {
            Timeline timeline=player.getCurrentTimeline();
            int count=player.getMediaItemCount(),current=player.getCurrentMediaItemIndex();
            byte[] fileBytes=null;
            boolean outsideWindow=count>0&&(current<windowStart||current>=windowEnd);
            if(timeline!=savedTimeline||outsideWindow) {
                rebuild(player,count,current);
                savedTimeline=timeline;
                queueVersion=Math.max(queueVersion+1,System.currentTimeMillis());
                QueueSnapshot snapshot=new QueueSnapshot(savedEntries,entryIndex(current),player.getCurrentPosition(),player.getShuffleModeEnabled(),player.getRepeatMode());
                boolean whole=windowStart==0&&windowEnd==count&&savedEntries.size()==count;
                fileBytes=encodeFile(queueVersion,snapshot,whole?QueueOrders.shuffleOrder(player):null);
            }
            String state=queueVersion+";"+entryIndex(current)+";"+Math.max(0,player.getCurrentPosition())+";"+(player.getShuffleModeEnabled()?1:0)+";"+player.getRepeatMode();
            enqueue(fileBytes,state);
        } catch(Exception ignored) { /* Persistence failure should not interrupt music. */ }
    }

    /** Snapshot entries for at most MAX_ITEMS player items around the current one. */
    private void rebuild(ExoPlayer player,int count,int current){
        int start=count<=MAX_ITEMS?0:Math.max(0,Math.min(current-MAX_ITEMS/2,count-MAX_ITEMS));
        int end=Math.min(count,start+MAX_ITEMS);
        List<QueueSnapshot.Entry> entries=new ArrayList<>(end-start);
        int[] map=new int[end-start];
        for(int i=start;i<end;i++) {
            MediaItem item=player.getMediaItemAt(i);
            if(item.localConfiguration==null){map[i-start]=-1;continue;}
            MediaMetadata meta=item.mediaMetadata;
            map[i-start]=entries.size();
            entries.add(new QueueSnapshot.Entry(item.mediaId,item.localConfiguration.uri.toString(),string(meta.title),string(meta.artist),string(meta.albumTitle),meta.artworkUri==null?"":meta.artworkUri.toString()));
        }
        savedEntries=entries;windowStart=start;windowEnd=end;entryOf=map;
    }

    /** Index of the current player item inside the saved entries (its nearest saved predecessor). */
    private int entryIndex(int current){
        if(current<windowStart||current>=windowEnd)return 0;
        for(int k=current-windowStart;k>=0;k--)if(entryOf[k]>=0)return entryOf[k];
        return 0;
    }

    private static byte[] encodeFile(long version,QueueSnapshot snapshot,int[] order)throws IOException{
        byte[] queue=snapshot.encode();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(queue.length+64+(order==null?0:order.length*4));
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(FILE_MAGIC);out.writeInt(FILE_VERSION);out.writeLong(version);
        out.writeInt(order==null?0:order.length);
        if(order!=null)for(int value:order)out.writeInt(value);
        out.writeInt(queue.length);out.write(queue);
        out.flush();
        return bytes.toByteArray();
    }

    private synchronized void enqueue(byte[] fileBytes,String state) {
        if(fileBytes!=null)pendingFile=fileBytes;
        pendingState=state;
        if(writing)return;
        writing=true;
        try{writer.execute(()-> {
            for(;;) {
                byte[] latestFile;String latestState;
                synchronized(this){latestFile=pendingFile;latestState=pendingState;pendingFile=null;pendingState=null;
                    if(latestFile==null&&latestState==null){writing=false;return;}}
                if(latestFile!=null)writeQueueFile(latestFile);
                // The small state refers to a queue version; written after that version's file.
                if(latestState!=null)try{prefs.edit().putString("state",latestState).apply();}catch(RuntimeException ignored){}
            }
        });}catch(RejectedExecutionException closed){writing=false;}
    }

    /** Writer thread. */
    private void writeQueueFile(byte[] bytes){
        FileOutputStream out=null;
        try{
            out=queueFile().startWrite();
            out.write(bytes);
            queueFile().finishWrite(out);
            // Migrated: drop the pre-2.0 copy that lived in SharedPreferences.
            if(prefs.contains("queue"))prefs.edit().remove("queue").remove("shuffleOrder").apply();
        }catch(IOException|RuntimeException failed){
            if(out!=null)queueFile().failWrite(out);
        }
    }

    private AtomicFile queueFile(){
        if(file==null)file=new AtomicFile(new File(context.getFilesDir(),"queue.bin"));
        return file;
    }

    // ------------------------------------------------------------------ restore

    /** Reads the saved queue off the main thread and delivers it (or null) on {@code main}. */
    public void restoreAsync(Handler main,Consumer<Restored> done){
        try{writer.execute(()->{
            Restored restored=null;
            try{restored=load();}catch(Exception corrupt){ /* A corrupt/old record does not block the library. */ }
            final Restored result=restored;
            main.post(()->done.accept(result));
        });}catch(RejectedExecutionException closed){main.post(()->done.accept(null));}
    }

    /** Decodes and resolves the saved queue. Blocking: the store's thread or another worker only. */
    public Restored load() throws IOException {
        QueueSnapshot snapshot=null;int[] order=null;long version=-1;
        byte[] bytes=null;
        try{bytes=queueFile().readFully();}catch(FileNotFoundException none){ /* first run or legacy */ }
        if(bytes!=null&&bytes.length>0){
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
            if(in.readInt()!=FILE_MAGIC||in.readInt()!=FILE_VERSION)throw new IOException("Unknown queue file");
            version=in.readLong();
            int orderLength=in.readInt();
            if(orderLength<0||orderLength>MAX_ITEMS)throw new IOException("Invalid order");
            if(orderLength>0){order=new int[orderLength];for(int i=0;i<orderLength;i++)order[i]=in.readInt();}
            int length=in.readInt();
            if(length<0||length>in.available())throw new IOException("Truncated queue file");
            byte[] queue=new byte[length];in.readFully(queue);
            snapshot=QueueSnapshot.decode(queue);
        } else {
            String legacy=prefs.getString("queue",null);
            if(legacy==null)return null;
            snapshot=QueueSnapshot.decode(Base64.decode(legacy,Base64.NO_WRAP));
            order=QueueRestorePlan.parseOrder(prefs.getString("shuffleOrder",null));
        }
        int savedIndex=snapshot.index,savedRepeat=snapshot.repeat;long savedPosition=snapshot.position;boolean savedShuffle=snapshot.shuffle;
        String[] state=prefs.getString("state","").split(";");
        if(state.length==5&&version>=0)try{
            if(Long.parseLong(state[0])==version){
                savedIndex=Integer.parseInt(state[1]);savedPosition=Long.parseLong(state[2]);
                savedShuffle=state[3].equals("1");savedRepeat=Integer.parseInt(state[4]);
                if(savedRepeat<0||savedRepeat>2)savedRepeat=0;
            }
        }catch(NumberFormatException stale){ /* keep the snapshot's own values */ }

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
        QueueRestorePlan plan=QueueRestorePlan.plan(keep,savedIndex,savedPosition,order);
        if(plan.isEmpty())return null;
        List<MediaItem> list=new ArrayList<>(plan.kept.length);
        for(int k:plan.kept)list.add(items[k]);
        return new Restored(list,plan.index,plan.position,savedShuffle,savedRepeat,plan.shuffleOrder);
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

    private String string(CharSequence text) {return text==null?"":text.toString();}
}
