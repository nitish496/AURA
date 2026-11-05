package dev.lilt.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import com.google.common.util.concurrent.*;
import dev.lilt.player.audio.MediaCache;
import dev.lilt.player.audio.MediaIds;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bounded, coalesced artwork delivery for the media notification and system media controls.
 * Device artwork comes from {@link Artwork}; catalogue (https) artwork is fetched through the
 * shared media cache, so covers of cached songs also appear offline. System UI owns styling.
 */
@androidx.media3.common.util.UnstableApi
public final class LocalBitmapLoader implements BitmapLoader,AutoCloseable {
    private static final int MAX_BYTES=12*1024*1024, MAX_EDGE=512;
    private final Context context;
    private final Artwork artwork;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"AuraNotificationArt"));
    private final Map<Uri,SettableFuture<Bitmap>> pending=new HashMap<>();
    private final LruCache<Uri,Bitmap> cache=new LruCache<Uri,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(Uri uri,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private final java.util.Set<SettableFuture<Bitmap>> decoding=new java.util.HashSet<>();
    private boolean closed;
    private byte[] lastData;
    private Bitmap lastDecoded;
    public LocalBitmapLoader(Context c){context=c.getApplicationContext();artwork=new Artwork(context,new Library(context));}
    public synchronized void close(){
        closed=true;worker.shutdownNow();artwork.close();
        for(SettableFuture<Bitmap> future:pending.values())future.cancel(false);
        pending.clear();for(SettableFuture<Bitmap> future:decoding)future.cancel(false);decoding.clear();cache.evictAll();
        lastData=null;lastDecoded=null;
    }
    public boolean supportsMimeType(String mime){return mime!=null&&mime.startsWith("image/");}
    public ListenableFuture<Bitmap> decodeBitmap(byte[] data){
        if(data==null||data.length==0||data.length>MAX_BYTES)return Futures.immediateFailedFuture(new java.io.IOException("Invalid artwork size"));
        SettableFuture<Bitmap> result=SettableFuture.create();
        synchronized(this){
            if(closed)return Futures.immediateCancelledFuture();
            // Every notification update re-requests the same embedded picture; decode it once.
            if(lastDecoded!=null&&(data==lastData||java.util.Arrays.equals(data,lastData)))return Futures.immediateFuture(lastDecoded);
            decoding.add(result);
        }
        try{worker.execute(()->{
            try{
                Bitmap bitmap=decodeSampled(data,data.length);
                synchronized(LocalBitmapLoader.this){if(!closed){lastData=data;lastDecoded=bitmap;}}
                result.set(bitmap);
            }
            catch(Exception failure){result.setException(new java.io.IOException("Artwork decoding unavailable"));}
            finally{synchronized(LocalBitmapLoader.this){decoding.remove(result);}}
        });}catch(java.util.concurrent.RejectedExecutionException stopped){synchronized(this){decoding.remove(result);}result.cancel(false);}
        return result;
    }
    /**
     * The player's metadata carries both the file's embedded picture and our artwork URI. A cover
     * the user chose (or a catalogue cover) wins over the embedded one, which Media3's default
     * would prefer; the audio file's own URI falls back to the embedded data.
     */
    @Override public ListenableFuture<Bitmap> loadBitmapFromMetadata(MediaMetadata metadata){
        Uri art=metadata.artworkUri;
        if(art!=null&&!isAudioUri(art))return loadBitmap(art);
        if(metadata.artworkData!=null)return decodeBitmap(metadata.artworkData);
        return art==null?null:loadBitmap(art);
    }
    private static boolean isAudioUri(Uri uri){
        String text=uri.toString();
        return text.startsWith("content://media/")&&text.contains("/audio/");
    }
    public synchronized ListenableFuture<Bitmap> loadBitmap(Uri uri){
        if(closed)return Futures.immediateCancelledFuture();
        if(uri==null)return Futures.immediateFailedFuture(new java.io.IOException("Missing artwork URI"));
        Bitmap cached=cache.get(uri);if(cached!=null)return Futures.immediateFuture(cached);
        SettableFuture<Bitmap> existing=pending.get(uri);if(existing!=null)return existing;
        SettableFuture<Bitmap> result=SettableFuture.create();pending.put(uri,result);
        try{worker.execute(()->{
            if(MediaIds.isHttp(uri.toString())){
                Bitmap bitmap=null;
                try{bitmap=fetch(uri);}catch(Exception unavailable){ /* offline and not cached */ }
                complete(uri,result,bitmap);
                return;
            }
            try{
                // Content-provider MIME lookup may block or throw after permission changes.
                String mime=context.getContentResolver().getType(uri);
                boolean image=mime!=null&&mime.startsWith("image/");
                artwork.loadActual(image?null:uri,image?uri:null,bitmap->complete(uri,result,bitmap));
            }catch(Exception unavailable){complete(uri,result,null);}
        });}catch(java.util.concurrent.RejectedExecutionException stopped){pending.remove(uri);result.cancel(false);}
        return result;
    }
    /** Reads remote artwork (cached on disk by the media cache) and decodes it at notification size. */
    private Bitmap fetch(Uri uri)throws java.io.IOException{
        DataSource source=MediaCache.dataSourceFactory(context).createDataSource();
        try{
            source.open(new DataSpec(uri));
            byte[] buffer=new byte[64*1024];int length=0;
            for(;;){
                if(length==buffer.length){
                    if(buffer.length>=MAX_BYTES)throw new java.io.IOException("Artwork too large");
                    buffer=java.util.Arrays.copyOf(buffer,Math.min(MAX_BYTES,buffer.length*2));
                }
                int read=source.read(buffer,length,buffer.length-length);
                if(read==C.RESULT_END_OF_INPUT)break;
                length+=read;
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
            }
            return length==0?null:decodeSampled(buffer,length);
        }finally{
            try{source.close();}catch(java.io.IOException ignored){}
        }
    }
    private static Bitmap decodeSampled(byte[] data,int length)throws java.io.IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new java.io.IOException("Invalid artwork");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>MAX_EDGE)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,length,options);
        if(bitmap==null)throw new java.io.IOException("No artwork");
        return bitmap;
    }
    private synchronized void complete(Uri uri,SettableFuture<Bitmap> result,Bitmap bitmap){
        if(pending.get(uri)!=result)return;
        pending.remove(uri);
        if(closed){result.cancel(false);return;}
        if(bitmap==null)result.setException(new java.io.IOException("Artwork unavailable"));
        else{cache.put(uri,bitmap);result.set(bitmap);}
    }
}
