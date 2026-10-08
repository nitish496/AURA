package dev.lilt.player;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class PlaybackStore {
    private final Context context;
    private final android.content.SharedPreferences prefs;
    private boolean initialized;
    private Timeline savedTimeline;
    private List<QueueSnapshot.Entry> savedEntries=Collections.emptyList();
    private final java.util.concurrent.ExecutorService writer=java.util.concurrent.Executors.newSingleThreadExecutor();
    private QueueSnapshot pending;
    private boolean writing;
    private synchronized void enqueue(QueueSnapshot snapshot) {
        pending=snapshot;
        if(writing)return;
        writing=true;
        writer.execute(()-> {
            for(;;) {
                QueueSnapshot latest;
                synchronized(this){latest=pending;pending=null;if(latest==null){writing=false;return;}}
                try{prefs.edit().putString("queue",Base64.encodeToString(latest.encode(),Base64.NO_WRAP)).apply();}
                catch(Exception ignored) { /* Keep playback independent of storage errors. */ }
            }
        });
    }
    public void close(){writer.shutdown();}
    public PlaybackStore(Context context) {
        this.context=context.getApplicationContext();prefs=context.getSharedPreferences("playback",Context.MODE_PRIVATE);
    }
    public void restore(ExoPlayer player) {
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(context.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)return;
        String saved=prefs.getString("queue",null);if(saved==null)return;
        try {
            QueueSnapshot snapshot=QueueSnapshot.decode(Base64.decode(saved,Base64.NO_WRAP));
            List<MediaItem> items=new ArrayList<>();int index=0;
            for(int i=0;i<snapshot.entries.size();i++) {
                QueueSnapshot.Entry e=snapshot.entries.get(i);
                if(!e.uri.startsWith("content://media/"))continue;
                if(i<=snapshot.index)index=items.size();
                MediaMetadata.Builder meta=new MediaMetadata.Builder().setTitle(MusicRules.displayTitle(e.title)).setArtist(e.artist).setAlbumTitle(e.album);
                meta.setArtworkUri(Uri.parse(e.artwork.startsWith("content://")?e.artwork:e.uri));
                items.add(new MediaItem.Builder().setMediaId(e.id).setUri(e.uri).setMediaMetadata(meta.build()).build());
            }
            if(!items.isEmpty()) {
                player.setMediaItems(items,Math.min(index,items.size()-1),snapshot.position);
                player.setRepeatMode(snapshot.repeat);player.setShuffleModeEnabled(snapshot.shuffle);
                // Restore a paused queue. Reopening the app must never start sound unexpectedly.
                player.setPlayWhenReady(false);player.prepare();
            }
            initialized=true;
        } catch(Exception ignored) { /* A corrupt/old record does not block the real library. */ }
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
            savedEntries=entries;savedTimeline=timeline;
            }
            QueueSnapshot snapshot=new QueueSnapshot(savedEntries,player.getCurrentMediaItemIndex(),player.getCurrentPosition(),player.getShuffleModeEnabled(),player.getRepeatMode());
            enqueue(snapshot);
        } catch(Exception ignored) { /* Persistence failure should not interrupt music. */ }
    }
    private String string(CharSequence text) {return text==null?"":text.toString();}
}
