package dev.lilt.player;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

public final class Artwork {
    private final Context context;private final Library library;
    private final ExecutorService worker=Executors.newFixedThreadPool(3);
    // PNG encoding never occupies a visible-cover decoder. Bounded queue limits retained bitmaps.
    private static final ThreadPoolExecutor diskWriter=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),new ThreadPoolExecutor.AbortPolicy());
    private boolean submit(Runnable task){if(closed)return false;try{worker.execute(task);return true;}catch(RejectedExecutionException ignored){return false;}}

    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private final Map<ImageView,Boolean> actualViews=new WeakHashMap<>();
    private static final LruCache<String,Boolean> actualCache=new LruCache<>(2048);
    public boolean isActual(ImageView view){return Boolean.TRUE.equals(actualViews.get(view));}
    public void loadActual(Uri mediaUri,Uri customUri,java.util.function.Consumer<Bitmap> callback){
        if(closed)return;submit(()->{if(closed)return;Bitmap image=null;if(customUri!=null)image=source(customUri,"custom:"+customUri,true);if(image==null&&mediaUri!=null)image=source(mediaUri,"actual:"+mediaUri,false);
            Bitmap ready=image==null?null:framed(image,DECODE_SIZE);main.post(()->{if(!closed)callback.accept(ready);});});
    }
    private static final LruCache<String,Bitmap> placeholders=new LruCache<String,Bitmap>(2*1024){@Override protected int sizeOf(String key,Bitmap b){return b.getByteCount()/1024;}};
    private final Map<String,List<WeakReference<ImageView>>> requests=new HashMap<>();
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(24*1024){@Override protected int sizeOf(String key,Bitmap b){return b.getByteCount()/1024;}};
    private static final LruCache<String,Bitmap> largeCache=new LruCache<String,Bitmap>(8*1024){@Override protected int sizeOf(String key,Bitmap b){return Math.max(1,b.getByteCount()/1024);}};
    private static volatile long lastPrune;
    public void logStats(){int pending;synchronized(requests){pending=requests.size();}android.util.Log.d("AuraArtwork",diagnostics()+" pending="+pending);}
    private static final java.util.concurrent.atomic.AtomicLong memoryHits=new java.util.concurrent.atomic.AtomicLong(),viewHits=new java.util.concurrent.atomic.AtomicLong(),diskHits=new java.util.concurrent.atomic.AtomicLong(),extractions=new java.util.concurrent.atomic.AtomicLong();
    public static String diagnostics(){return "memoryHits="+memoryHits.get()+" viewHits="+viewHits.get()+" diskHits="+diskHits.get()+" extractions="+extractions.get()+" thumbnailKiB="+cache.size()+" largeKiB="+largeCache.size();}
    private int displaySize(ImageView view){int width=view.getWidth();if(width<=0&&view.getLayoutParams()!=null)width=view.getLayoutParams().width;return ArtworkSizing.bucket(width>0?width:256);}
    private LruCache<String,Bitmap> rendered(int size){return size>512?largeCache:cache;}
    public void prefetch(List<Track> tracks,int pixels){for(Track track:tracks.subList(0,Math.min(12,tracks.size()))){ImageView view=new ImageView(context);view.setLayoutParams(new android.view.ViewGroup.LayoutParams(pixels,pixels));load(view,track);}}
    // Canonical source decodes let large player and small shelves reuse the same artwork.
    // Never merge embedded images by album: tracks in one album can carry different covers.
    private static final LruCache<String,Bitmap> sources=new LruCache<String,Bitmap>(16*1024){@Override protected int sizeOf(String key,Bitmap b){return Math.max(1,b.getByteCount()/1024);}};
    private static final LruCache<String,Boolean> missing=new LruCache<>(2048);
    private static final Object[] sourceLocks=new Object[32];
    static{for(int i=0;i<sourceLocks.length;i++)sourceLocks[i]=new Object();}
    private static Object sourceLock(String key){return sourceLocks[Math.floorMod(key.hashCode(),sourceLocks.length)];}
    private static final int DECODE_SIZE=900;
    public Artwork(Context context,Library library){this.context=context.getApplicationContext();this.library=library;}
    /** Warm only the first visible shelves; return on readiness or a bounded deadline. */
    public void warm(List<Track> tracks,Runnable done){
        List<ImageView> held=new ArrayList<>();for(Track track:tracks.subList(0,Math.min(12,tracks.size()))){ImageView view=new ImageView(context);held.add(view);view.setLayoutParams(new android.view.ViewGroup.LayoutParams(256,256));load(view,track);}
        long earliest=android.os.SystemClock.uptimeMillis()+1000;long deadline=earliest+800;
        main.post(new Runnable(){public void run(){boolean pending;synchronized(requests){pending=!requests.isEmpty();}if(closed)return;if((!pending&&android.os.SystemClock.uptimeMillis()>=earliest)||android.os.SystemClock.uptimeMillis()>=deadline){held.clear();done.run();}else main.postDelayed(this,30);}});
    }
    public void load(ImageView view,Track track) {
        if(closed)return;
        Uri custom=library.artwork(track);int size=displaySize(view);String sourceIdentity=track.id+":"+track.uri+":"+track.dateAdded+":"+custom;String identity=sourceIdentity+":"+size;LruCache<String,Bitmap> rendered=rendered(size);
        if(identity.equals(view.getTag())&&view.getDrawable()!=null){viewHits.incrementAndGet();return;}
        view.setTag(identity);view.setContentDescription("Album cover for "+track.album);view.setClipToOutline(true);
        String key=identity;
        String sharedKey=custom==null?null:"custom:"+custom+":"+size;

        int hue=Math.floorMod(track.albumKey().hashCode(),360);
        int first=Color.HSVToColor(new float[]{hue,.08f,.30f}),last=Color.HSVToColor(new float[]{hue,.05f,.16f});
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{DesignTokens.SURFACE,DesignTokens.SURFACE});background.setCornerRadius(DesignTokens.COVER_RADIUS*context.getResources().getDisplayMetrics().density);view.setBackground(background);
        Bitmap found=sharedKey==null?null:rendered.get(sharedKey);if(found==null)found=rendered.get(key);if(found!=null){memoryHits.incrementAndGet();actualViews.put(view,sharedKey!=null&&rendered.get(sharedKey)!=null||Boolean.TRUE.equals(actualCache.get(key)));view.setImageBitmap(found);return;}
        Bitmap fallback=placeholders.get(track.albumKey());if(fallback==null){fallback=placeholder(track.album,first,last);placeholders.put(track.albumKey(),fallback);}actualViews.put(view,false);view.setImageBitmap(fallback);
        final Bitmap defaultArt=fallback;
        synchronized(requests) {
            List<WeakReference<ImageView>> pending=requests.get(key);
            if(pending!=null){pending.add(new WeakReference<>(view));return;}
            pending=new ArrayList<>();pending.add(new WeakReference<>(view));requests.put(key,pending);
        }
        boolean accepted=submit(()-> {
            File saved=new File(context.getCacheDir(),"cover-"+cacheName(sourceIdentity)+".png");
            BitmapFactory.Options diskOptions=new BitmapFactory.Options();diskOptions.inJustDecodeBounds=true;BitmapFactory.decodeFile(saved.getPath(),diskOptions);diskOptions.inSampleSize=1;while(Math.max(diskOptions.outWidth,diskOptions.outHeight)/diskOptions.inSampleSize>size*2)diskOptions.inSampleSize*=2;diskOptions.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeFile(saved.getPath(),diskOptions);boolean diskHit=bitmap!=null;if(diskHit)diskHits.incrementAndGet();boolean fromCustom=diskHit&&custom!=null&&new File(saved.getPath()+".custom").exists();
            // Different covers decode concurrently; source() coalesces matching URI keys.
            {
                if(closed)return;
                if(!diskHit&&custom!=null){bitmap=source(custom,"custom:"+custom,true);fromCustom=bitmap!=null;}
                if(bitmap==null)bitmap=source(track.uri,"embedded:"+track.id+":"+track.uri+":"+track.dateAdded,false);
            }
            if(closed)return;
            Bitmap ready;
            if(bitmap==null)ready=defaultArt;
            else {
                String renderKey=fromCustom?sharedKey:key;
                {
                    ready=rendered.get(renderKey);
                    if(ready==null){ready=framed(bitmap,size);if(!closed)rendered.put(renderKey,ready);}
                }
            }
            // A failed custom URI falls back separately per track, preserving embedded covers.
            if(!fromCustom&&!closed)rendered.put(key,ready);
            final Bitmap display=ready;final boolean actual=bitmap!=null;actualCache.put(key,actual);
            main.post(()-> {
                List<WeakReference<ImageView>> waiting;
                synchronized(requests){waiting=requests.remove(key);}
                if(closed||waiting==null)return;
                for(WeakReference<ImageView> reference:waiting){ImageView image=reference.get();if(image!=null&&identity.equals(image.getTag())){actualViews.put(image,actual);image.setImageBitmap(display);}}
            });
            if(actual&&!diskHit&&!closed){final Bitmap persist=bitmap;final boolean persistCustom=fromCustom;try{diskWriter.execute(()->{if(closed)return;File temporary=new File(saved.getPath()+"."+Thread.currentThread().getId()+".tmp");try(FileOutputStream out=new FileOutputStream(temporary)){framed(persist,DECODE_SIZE).compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException ignored){temporary.delete();return;}if(closed){temporary.delete();return;}if(!temporary.renameTo(saved)){temporary.delete();return;}File marker=new File(saved.getPath()+".custom");if(persistCustom)try{marker.createNewFile();}catch(IOException ignored){}else marker.delete();pruneDisk();});}catch(RejectedExecutionException ignored){/* Cache persistence is optional; never block visible decoding. */}}

        });
        if(!accepted){synchronized(requests){requests.remove(key);}view.setTag(null);}
    }
    private void pruneDisk(){
        if(System.currentTimeMillis()-lastPrune<=30000)return;lastPrune=System.currentTimeMillis();
        File[] stored=context.getCacheDir().listFiles((d,n)->n.startsWith("cover-")&&n.endsWith(".png"));if(stored==null)return;Arrays.sort(stored,Comparator.comparingLong(File::lastModified));long bytes=0;for(File f:stored)bytes+=f.length();for(File f:stored){if(bytes<=256L*1024*1024)break;long length=f.length();if(f.delete()){bytes-=length;new File(f.getPath()+".custom").delete();}}
    }
    private Bitmap source(Uri uri,String key,boolean custom){
        synchronized(sourceLock(key)){return sourceLocked(uri,key,custom);}
    }
    private Bitmap sourceLocked(Uri uri,String key,boolean custom){
        if(closed)return null;
        Bitmap cached=sources.get(key);if(cached!=null)return cached;
        if(Boolean.TRUE.equals(missing.get(key)))return null;
        Bitmap bitmap=null;boolean definitelyMissing=false;
        if(custom){
            try(InputStream stream=context.getContentResolver().openInputStream(uri)){
                bitmap=decode(stream,DECODE_SIZE);
            }catch(Exception ignored){} // Permission/provider errors are retryable after refresh.
        }else {
            extractions.incrementAndGet();MediaMetadataRetriever retriever=new MediaMetadataRetriever();
            try{
                retriever.setDataSource(context,uri);byte[] bytes=retriever.getEmbeddedPicture();
                definitelyMissing=bytes==null;
                if(bytes!=null)try(InputStream stream=new ByteArrayInputStream(bytes)){bitmap=decode(stream,DECODE_SIZE);}
            }catch(Exception ignored){}
            finally{try{retriever.release();}catch(Exception ignored){}}
        }
        if(bitmap!=null&&!closed)sources.put(key,bitmap);
        else if(definitelyMissing&&!closed)missing.put(key,true);
        return bitmap;
    }
    /** Keep the complete cover while filling the frame with a soft version of its own colours. */
    private Bitmap framed(Bitmap source,int target) {
        if(source.getWidth()==source.getHeight()&&source.getWidth()<=target)return source;
        int size=Math.min(900,Math.max(96,target));
        Bitmap result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(result);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Bitmap soft=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);Canvas low=new Canvas(soft);
        float fill=Math.max(64f/source.getWidth(),64f/source.getHeight());
        float fw=source.getWidth()*fill,fh=source.getHeight()*fill;
        low.drawBitmap(source,null,new RectF((64-fw)/2,(64-fh)/2,(64+fw)/2,(64+fh)/2),paint);
        smooth(soft);canvas.drawBitmap(soft,null,new Rect(0,0,size,size),paint);soft.recycle();
        paint.setColor(0x18000000);canvas.drawRect(0,0,size,size,paint);paint.setColor(Color.WHITE);
        float fit=Math.min((float)size/source.getWidth(),(float)size/source.getHeight());
        float width=source.getWidth()*fit,height=source.getHeight()*fit;
        canvas.drawBitmap(source,null,new RectF((size-width)/2,(size-height)/2,(size+width)/2,(size+height)/2),paint);
        return result;
    }
    private void smooth(Bitmap bitmap) {
        int width=bitmap.getWidth(),height=bitmap.getHeight(),radius=5,window=radius*2+1;
        int[] pixels=new int[width*height],temporary=new int[pixels.length];bitmap.getPixels(pixels,0,width,0,0,width,height);
        for(int pass=0;pass<3;pass++) {
            for(int y=0;y<height;y++){int[] sum=new int[4];for(int k=-radius;k<=radius;k++)addColor(sum,pixels[y*width+Math.max(0,Math.min(width-1,k))],1);
                for(int x=0;x<width;x++){temporary[y*width+x]=average(sum,window);addColor(sum,pixels[y*width+Math.max(0,x-radius)],-1);addColor(sum,pixels[y*width+Math.min(width-1,x+radius+1)],1);}}
            for(int x=0;x<width;x++){int[] sum=new int[4];for(int k=-radius;k<=radius;k++)addColor(sum,temporary[Math.max(0,Math.min(height-1,k))*width+x],1);
                for(int y=0;y<height;y++){pixels[y*width+x]=average(sum,window);addColor(sum,temporary[Math.max(0,y-radius)*width+x],-1);addColor(sum,temporary[Math.min(height-1,y+radius+1)*width+x],1);}}
        }
        bitmap.setPixels(pixels,0,width,0,0,width,height);
    }
    private void addColor(int[] sum,int color,int direction){sum[0]+=Color.alpha(color)*direction;sum[1]+=Color.red(color)*direction;sum[2]+=Color.green(color)*direction;sum[3]+=Color.blue(color)*direction;}
    private int average(int[] sum,int count){return Color.argb(sum[0]/count,sum[1]/count,sum[2]/count,sum[3]/count);}
    private Bitmap placeholder(String album,int first,int last) {
        Bitmap image=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.scale(128f/160,128f/160);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0,0,160,160,first,last,Shader.TileMode.CLAMP));canvas.drawRect(0,0,160,160,paint);paint.setShader(null);paint.setColor(0x22ffffff);canvas.drawCircle(136,16,94,paint);canvas.drawCircle(12,156,78,paint);
        paint.setColor(Color.WHITE);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(64);paint.setTextAlign(Paint.Align.CENTER);String initial=album.substring(0,album.offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT);canvas.drawText(initial,80,103,paint);return image;
    }
    private Bitmap decode(InputStream stream,int target)throws Exception {
        if(stream==null)return null;
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=stream.read(buffer))!=-1){if(output.size()+count>12*1024*1024)return null;output.write(buffer,0,count);}
        byte[] bytes=output.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>target)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }
    private String cacheName(String identity){try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return name.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public void close(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);synchronized(requests){requests.clear();}actualViews.clear();}
}
