package dev.lilt.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import androidx.media3.common.util.BitmapLoader;
import com.google.common.util.concurrent.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded, coalesced local artwork delivery; System UI owns notification styling. */
@androidx.media3.common.util.UnstableApi
public final class LocalBitmapLoader implements BitmapLoader,AutoCloseable {
    private final Context context;
    private final Artwork artwork;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<Uri,SettableFuture<Bitmap>> pending=new HashMap<>();
    private final LruCache<Uri,Bitmap> cache=new LruCache<Uri,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(Uri uri,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private final java.util.Set<SettableFuture<Bitmap>> decoding=new java.util.HashSet<>();
    private boolean closed;
    public LocalBitmapLoader(Context c){context=c.getApplicationContext();artwork=new Artwork(context,new Library(context));}
    public synchronized void close(){
        closed=true;worker.shutdownNow();artwork.close();
        for(SettableFuture<Bitmap> future:pending.values())future.cancel(false);
        pending.clear();for(SettableFuture<Bitmap> future:decoding)future.cancel(false);decoding.clear();cache.evictAll();
    }
    public boolean supportsMimeType(String mime){return mime!=null&&mime.startsWith("image/");}
    public ListenableFuture<Bitmap> decodeBitmap(byte[] data){
        if(data==null||data.length==0||data.length>12*1024*1024)return Futures.immediateFailedFuture(new java.io.IOException("Invalid artwork size"));
        SettableFuture<Bitmap> result=SettableFuture.create();
        synchronized(this){if(closed)return Futures.immediateCancelledFuture();decoding.add(result);}
        try{worker.execute(()->{
            try{
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new java.io.IOException("Invalid artwork");
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
                if(bitmap==null)throw new java.io.IOException("No artwork");
                result.set(bitmap);
            }catch(Exception failure){result.setException(new java.io.IOException("Artwork decoding unavailable"));}
            finally{synchronized(LocalBitmapLoader.this){decoding.remove(result);}}
        });}catch(java.util.concurrent.RejectedExecutionException stopped){synchronized(this){decoding.remove(result);}result.cancel(false);}
        return result;
    }
    public synchronized ListenableFuture<Bitmap> loadBitmap(Uri uri){
        if(closed)return Futures.immediateCancelledFuture();
        if(uri==null)return Futures.immediateFailedFuture(new java.io.IOException("Missing artwork URI"));
        Bitmap cached=cache.get(uri);if(cached!=null)return Futures.immediateFuture(cached);
        SettableFuture<Bitmap> existing=pending.get(uri);if(existing!=null)return existing;
        SettableFuture<Bitmap> result=SettableFuture.create();pending.put(uri,result);
        try{worker.execute(()->{
            try{
                // Content-provider MIME lookup may block or throw after permission changes.
                String mime=context.getContentResolver().getType(uri);
                boolean image=mime!=null&&mime.startsWith("image/");
                artwork.loadActual(image?null:uri,image?uri:null,bitmap->complete(uri,result,bitmap));
            }catch(Exception unavailable){complete(uri,result,null);}
        });}catch(java.util.concurrent.RejectedExecutionException stopped){pending.remove(uri);result.cancel(false);}
        return result;
    }
    private synchronized void complete(Uri uri,SettableFuture<Bitmap> result,Bitmap bitmap){
        if(pending.get(uri)!=result)return;
        pending.remove(uri);
        if(closed){result.cancel(false);return;}
        if(bitmap==null)result.setException(new java.io.IOException("Local artwork unavailable"));
        else{cache.put(uri,bitmap);result.set(bitmap);}
    }
}
