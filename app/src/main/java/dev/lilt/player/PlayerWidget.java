package dev.lilt.player;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.widget.RemoteViews;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.util.concurrent.Executors;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.*;
import com.google.common.util.concurrent.ListenableFuture;

@androidx.media3.common.util.UnstableApi
public class PlayerWidget extends AppWidgetProvider {
    private static final String ACTION="dev.lilt.player.WIDGET_CONTROL";
    private static Artwork artwork;
    private static volatile String requestedArt="";
    private static Bitmap widgetArt;
    private static String bitmapKey="";
    private static Bitmap backdropCover,backdropBitmap;
    private static Bitmap deckBackdropCover,deckBackdropBitmap;
    private static final java.util.concurrent.ExecutorService disk=Executors.newSingleThreadExecutor();
    private static void requestArt(Context c,String uri,String custom){
        String key=uri+"|"+custom;
        if(key.equals(requestedArt))return;
        requestedArt=key;widgetArt=null;bitmapKey="";
        // Callers publish their new text/state immediately; avoid a stale duplicate update here.
        if(uri.isEmpty())return;
        disk.execute(()->{
            if(key.equals(prefs(c).getString("cached_art_key",""))){
                Bitmap saved=BitmapFactory.decodeFile(new File(c.getCacheDir(),"widget-cover.png").getPath());
                if(saved!=null&&saved.getWidth()<=180&&saved.getHeight()<=180)new Handler(Looper.getMainLooper()).post(()->{
                    if(key.equals(requestedArt)&&!key.equals(bitmapKey)){widgetArt=saved;bitmapKey=key;refresh(c);}
                });
            }
        });
        if(artwork==null)artwork=new Artwork(c,new Library(c));
        artwork.loadActual(Uri.parse(uri),custom.isEmpty()?null:Uri.parse(custom),bitmap->{
            if(!key.equals(requestedArt))return;
            Bitmap small=bitmap==null?null:roundedCover(bitmap);
            widgetArt=small;bitmapKey=key;
            prefs(c).edit().putBoolean("actual_art",small!=null).apply();refresh(c);
            disk.execute(()->{
                File file=new File(c.getCacheDir(),"widget-cover.png");
                if(!key.equals(requestedArt))return;
                if(small==null){file.delete();prefs(c).edit().remove("cached_art_key").apply();return;}
                try(FileOutputStream out=new FileOutputStream(file)){small.compress(Bitmap.CompressFormat.PNG,100,out);prefs(c).edit().putString("cached_art_key",key).apply();}catch(IOException ignored){}
            });
        });
    }
    private static Bitmap roundedCover(Bitmap bitmap){
        Bitmap scaled=Bitmap.createScaledBitmap(bitmap,180,180,true);
        Bitmap result=Bitmap.createBitmap(180,180,Bitmap.Config.ARGB_8888);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new BitmapShader(scaled,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
        new Canvas(result).drawRoundRect(0,0,180,180,38,38,paint);
        return result;
    }
    private static Bitmap backdrop(Bitmap cover){
        if(cover==backdropCover && backdropBitmap!=null)return backdropBitmap;
        Bitmap sample=Bitmap.createScaledBitmap(cover,24,24,true);
        int[] pixels=new int[24*24];sample.getPixels(pixels,0,24,0,0,24,24);
        int colour=ArtworkTheme.palette(pixels).background;
        Bitmap bg=Bitmap.createBitmap(720,144,Bitmap.Config.ARGB_8888);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(colour);new Canvas(bg).drawRoundRect(0,0,720,144,44,44,paint);backdropCover=cover;backdropBitmap=bg;return bg;
    }
    private static Bitmap deckBackdrop(Bitmap cover){
        if(cover==deckBackdropCover&&deckBackdropBitmap!=null)return deckBackdropBitmap;
        Bitmap sample=Bitmap.createScaledBitmap(cover,24,24,true);int[] pixels=new int[576];sample.getPixels(pixels,0,24,0,0,24,24);
        Bitmap bg=Bitmap.createBitmap(720,336,Bitmap.Config.ARGB_8888);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(ArtworkTheme.palette(pixels).background);
        new Canvas(bg).drawRoundRect(0,0,720,336,60,60,paint);deckBackdropCover=cover;deckBackdropBitmap=bg;return bg;
    }
    public static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("widgets",Context.MODE_PRIVATE);}
    public static void publish(Context c,Player player){
        CharSequence title=player.getMediaMetadata().title,artist=player.getMediaMetadata().artist;
        String titleText=title==null?"Nothing playing":title.toString();
        String artistText=artist==null?"Tap play to resume":artist.toString();
        boolean playing=player.getPlayWhenReady()&&player.getPlaybackState()!=Player.STATE_ENDED;
        boolean hasQueue=player.getMediaItemCount()>0;
        MediaItem item=player.getCurrentMediaItem();
        String uri=item==null||item.localConfiguration==null?"":item.localConfiguration.uri.toString();
        String custom=player.getMediaMetadata().artworkUri==null?"":player.getMediaMetadata().artworkUri.toString();
        if(custom.equals(uri))custom="";
        android.content.SharedPreferences settings=prefs(c);
        if(titleText.equals(settings.getString("title",null))&&artistText.equals(settings.getString("artist",null))
                &&uri.equals(settings.getString("uri",""))&&custom.equals(settings.getString("custom",""))&&playing==settings.getBoolean("playing",false)&&hasQueue==settings.getBoolean("has_queue",false))return;
        settings.edit().putString("title",titleText).putString("artist",artistText)
                .putBoolean("playing",playing).putBoolean("has_queue",hasQueue).putString("uri",uri).putString("custom",custom).apply();
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        if(manager.getAppWidgetIds(new ComponentName(c,PlayerWidget.class)).length>0||manager.getAppWidgetIds(new ComponentName(c,LargePlayerWidget.class)).length>0||manager.getAppWidgetIds(new ComponentName(c,DeckPlayerWidget.class)).length>0)requestArt(c.getApplicationContext(),uri,custom);
        refresh(c);
    }
    public static void refresh(Context c){AppWidgetManager manager=AppWidgetManager.getInstance(c);for(Class<?> provider:new Class<?>[]{PlayerWidget.class,LargePlayerWidget.class,DeckPlayerWidget.class})for(int id:manager.getAppWidgetIds(new ComponentName(c,provider)))update(c,manager,id);}
    public static void update(Context c,AppWidgetManager m,int id){
        AppWidgetProviderInfo info=m.getAppWidgetInfo(id);boolean large=info!=null&&info.provider.getClassName().endsWith("LargePlayerWidget");
        boolean deck=info!=null&&info.provider.getClassName().endsWith("DeckPlayerWidget");
        RemoteViews v=new RemoteViews(c.getPackageName(),deck?R.layout.widget_deck:large?R.layout.widget_large:R.layout.widget_compact);
        int backgroundView=large||deck?R.id.widget_root:R.id.widget_panel;
        int theme=prefs(c).getInt("theme:"+id,3);
        // 0 Dark graphite, 1 Light, 2 Ember (graphite with an Ember play button), 3 Artwork (default).
        boolean lightTheme=theme==1;int ink=lightTheme?0xff0b0b0c:0xfff5f5f7,inkSecondary=lightTheme?0xff545459:0xffa1a1a8,controlTint=ink;
        v.setInt(backgroundView,"setBackgroundResource",lightTheme?R.drawable.widget_light:R.drawable.widget_dark);
        Bitmap cover=bitmapKey.equals(requestedArt)?widgetArt:null;
        boolean hasArt=cover!=null;
        if(!hasArt&&theme==3){ink=0xfff5f5f7;inkSecondary=0xffa1a1a8;controlTint=ink;v.setInt(backgroundView,"setBackgroundResource",R.drawable.widget_black);}
        v.setImageViewBitmap(R.id.widget_cover,cover);
        v.setImageViewBitmap(R.id.widget_background,hasArt&&theme==3?(deck?deckBackdrop(cover):backdrop(cover)):null);
        if(theme==3&&hasArt)v.setInt(backgroundView,"setBackgroundResource",android.R.color.transparent);
        v.setTextViewText(R.id.widget_title,prefs(c).getString("title","Nothing playing"));v.setTextViewText(R.id.widget_artist,prefs(c).getString("artist","Open AURA to choose music"));
        if(theme==3&&hasArt)inkSecondary=0xb8f5f5f7;
        v.setTextColor(R.id.widget_title,ink);v.setTextColor(R.id.widget_artist,inkSecondary);
        boolean isPlaying=prefs(c).getBoolean("playing",false);
        v.setImageViewResource(R.id.widget_play,isPlaying?R.drawable.ic_pause:R.drawable.ic_play);
        v.setImageViewResource(R.id.widget_previous,R.drawable.ic_previous);v.setImageViewResource(R.id.widget_next,R.drawable.ic_next);v.setImageViewResource(R.id.widget_settings,R.drawable.ic_settings);
        for(int view:new int[]{R.id.widget_previous,R.id.widget_next})v.setInt(view,"setColorFilter",controlTint);
        v.setInt(R.id.widget_settings,"setColorFilter",inkSecondary);
        v.setInt(R.id.widget_play,"setColorFilter",theme==2?0xffff6a3d:controlTint);
        v.setContentDescription(R.id.widget_play,isPlaying?"Pause music":"Play music");
        Intent open=new Intent(c,MainActivity.class);v.setOnClickPendingIntent(R.id.widget_title,PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        Intent config=new Intent(c,WidgetSettings.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);v.setOnClickPendingIntent(R.id.widget_settings,PendingIntent.getActivity(c,id+10000,config,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        for(int i=0;i<3;i++){int view=new int[]{R.id.widget_previous,R.id.widget_play,R.id.widget_next}[i];Intent action=new Intent(c,PlayerWidget.class).setAction(ACTION).putExtra("control",i);PendingIntent control=PendingIntent.getBroadcast(c,i,action,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(view,control);if(deck&&i==1){v.setOnClickPendingIntent(R.id.widget_cover,control);v.setContentDescription(R.id.widget_cover,prefs(c).getBoolean("playing",false)?"Album cover, pause music":"Album cover, play music");}}
        // Play/pause always goes through the broadcast: with an empty queue play() resumes the saved queue.
        m.updateAppWidget(id,v);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
        requestArt(c.getApplicationContext(),prefs(c).getString("uri",""),prefs(c).getString("custom",""));
        for(int id:ids)update(c,m,id);
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){update(c,m,id);}
    @Override public void onDeleted(Context c,int[] ids){for(int id:ids)prefs(c).edit().remove("theme:"+id).apply();}
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if(!ACTION.equals(intent.getAction()))return;
        int action=intent.getIntExtra("control",-1);
        if(action<0||action>2)return;
        Context context=c.getApplicationContext();
        PendingResult pending=goAsync();
        Handler handler=new Handler(Looper.getMainLooper());
        ListenableFuture<MediaController> future=new MediaController.Builder(context,
                new SessionToken(context,new ComponentName(context,PlaybackService.class))).buildAsync();
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable finish=()->{
            if(finished.compareAndSet(false,true)){
                // releaseFuture also releases a controller that connects after the receiver times out.
                MediaController.releaseFuture(future);
                pending.finish();
            }
        };
        Runnable timeout=finish;
        handler.postDelayed(timeout,7500);
        future.addListener(()->{
            if(finished.get())return;
            try{
                MediaController player=future.get();
                Player.Listener listener=new Player.Listener(){
                    @Override public void onEvents(Player p,Player.Events events){
                        if(!finished.get())publish(context,p);
                    }
                };
                player.addListener(listener);
                if(player.getMediaItemCount()==0){
                    // Nothing loaded (app was closed): play() makes the service resume the saved queue.
                    // Previous/Next have nothing to act on. The title remains the way to open AURA.
                    if(action==1){
                        player.play();
                        handler.postDelayed(()->{if(!finished.get())publish(context,player);handler.removeCallbacks(timeout);finish.run();},1500);
                        return;
                    }
                    publish(context,player);
                    handler.removeCallbacks(timeout);
                    finish.run();
                    return;
                }
                if(action==0)player.seekToPreviousMediaItem();
                else if(action==2)player.seekToNextMediaItem();
                else if(player.getPlayWhenReady())player.pause();
                else {if(player.getPlaybackState()==Player.STATE_ENDED)player.seekTo(0);if(player.getPlaybackState()==Player.STATE_IDLE)player.prepare();player.play();}
                // Keep the controller connected briefly for the asynchronous service state;
                // never publish the pre-command snapshot over the service's newer update.
                handler.postDelayed(()->{
                    if(!finished.get())publish(context,player);
                    handler.removeCallbacks(timeout);
                    finish.run();
                },1500);
            }catch(Exception ignored){
                handler.removeCallbacks(timeout);
                finish.run();
            }
        },command->handler.post(command));
    }
}
