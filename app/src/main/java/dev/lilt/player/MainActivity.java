package dev.lilt.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import static dev.lilt.player.DesignTokens.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {

    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Library library; private MoodStore moods; private Artwork artwork; private MediaController player; private ListenableFuture<MediaController> future;
    private List<Track> tracks=new ArrayList<>();
    private SearchIndex searchIndex=new SearchIndex(Collections.emptyList()),titleSearchIndex=new SearchIndex(Collections.emptyList());
    private boolean startupComplete=false;private boolean libraryDirty=true;private long libraryRevision;private android.database.ContentObserver mediaObserver;
    private final Map<String,android.os.Parcelable> listPositions=new HashMap<>();private AbsListView displayedList;private String displayedListKey="";
    private final Runnable refreshDirty=()->{if(this.visible&&!this.full&&!this.loading&&libraryDirty)scan();};
    private int searchGeneration; private Runnable pendingSearch;
    private String searchFilter="All";private TextView searchCount;private ListView searchResults;private final Map<String,android.os.Parcelable> searchPositions=new HashMap<>();
    private final Map<String,Track> trackIndex=new HashMap<>(); private final Map<String,String> coverKeys=new HashMap<>();private boolean visible;
    private ArrayList<String> collectionIds; private String collectionTitle="", currentPlaylist="";
    private boolean seeking=false; private String pendingCoverId="";
    private FrameLayout content; private long lastScan;
    private FrameLayout windowFrame; private LinearLayout root, body, mini; private TextView miniTitle, miniArtist; private Button miniPlay;
    private String upcomingCoverKey="";private String lastHistoryId=""; private Set<String> favouriteIds=new HashSet<>();private Button nowFavourite;
    private String tab="Home", section="", query=""; private boolean loading=false, full=false; private String error="";
    private Track coverTarget; private ImageView nowArt; private TextView nowTitle, nowArtist, elapsed, remaining; private SeekBar seek; private Button playButton, shuffleButton, repeatButton;
    private TextView homeTitle,homeArtist,homeRemaining; private ImageView homeArt; private Button homePlay; private ProgressBar homeProgress,miniProgress; private Track homeSeed;
    private final Runnable tick=new Runnable() { @Override public void run() { if(visible&&(full||homeProgress!=null||miniProgress!=null)&&player!=null&&player.isPlaying()){updatePlayback();handler.postDelayed(this,1000);} } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); library=new Library(this);favouriteIds=new HashSet<>(library.prefs().getStringSet("favourites",Collections.emptySet())); moods=new MoodStore(this); artwork=new Artwork(this,library);
        if(state!=null) {
            tab=state.getString("tab","Home"); section=state.getString("section",""); query=state.getString("query","");searchFilter=state.getString("searchFilter","All");
            full=state.getBoolean("full",false); collectionIds=state.getStringArrayList("collectionIds");
            collectionTitle=state.getString("collectionTitle","");currentPlaylist=state.getString("currentPlaylist","");pendingCoverId=state.getString("coverTarget","");
        }
        Object retained=getLastNonConfigurationInstance();if(retained instanceof List && permission()){tracks=new ArrayList<>((List<Track>)retained);trackIndex.clear();List<String[]> metadata=new ArrayList<>(),titles=new ArrayList<>();for(Track track:tracks){trackIndex.put(track.id,track);metadata.add(new String[]{track.id,track.title,track.artist,track.album});titles.add(new String[]{track.id,track.title,"",""});}searchIndex=new SearchIndex(metadata);titleSearchIndex=new SearchIndex(titles);startupComplete=true;libraryDirty=false;}
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        future.addListener(()-> { try { player=future.get(); player.addListener(new Player.Listener() {
            @Override public void onEvents(Player p,Player.Events e) { updatePlayback(); scheduleTick(); }
            @Override public void onPlayerError(PlaybackException e) { toast("This file couldn't play. It may have moved or use an unsupported format."); }
        }); updatePlayback(); } catch(Exception e) { error="Playback couldn't connect. Close and reopen AURA."; render(); } },command -> handler.post(command));
        registerMediaObserver();if(permission()&&!startupComplete)scan();else render();
    }
    @Override public Object onRetainNonConfigurationInstance(){return startupComplete?new ArrayList<>(tracks):null;}
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("tab",tab); state.putString("section",section); state.putString("query",query);state.putString("searchFilter",searchFilter); state.putBoolean("full",full);
        state.putStringArrayList("collectionIds",collectionIds);state.putString("collectionTitle",collectionTitle);state.putString("currentPlaylist",currentPlaylist);
        state.putString("coverTarget",coverTarget==null?pendingCoverId:coverTarget.id);super.onSaveInstanceState(state);
    }
    @Override public void onResume() { super.onResume();visible=true;scheduleTick(); if(library!=null&&!loading&&(!permission()||libraryDirty))scan(); }
    @Override public void onPause(){visible=false;handler.removeCallbacks(tick);if(artwork!=null)artwork.logStats();if(displayedList!=null)listPositions.put(displayedListKey,displayedList.onSaveInstanceState());super.onPause();}
    private void scheduleTick(){handler.removeCallbacks(tick);if(visible&&(full||homeProgress!=null||miniProgress!=null)&&player!=null&&player.isPlaying())handler.postDelayed(tick,1000);}
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null);if(mediaObserver!=null)getContentResolver().unregisterContentObserver(mediaObserver); worker.shutdownNow(); artwork.close(); MediaController.releaseFuture(future); super.onDestroy(); }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    // Configuration dimensions describe the current app window, including split screen.
    private int windowWidthDp(){int w=getResources().getConfiguration().screenWidthDp;return w>0?w:Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);}
    private int contentWidthDp(){return ResponsiveLayout.contentWidth(windowWidthDp());}
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String s,int size,int color,boolean bold) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);t.setEllipsize(android.text.TextUtils.TruncateAt.END); if(bold)t.setTypeface(null,Typeface.BOLD); return t; }
    private GradientDrawable background(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private final class IconControl extends Button {
        private GlyphDrawable glyph; private boolean primary; private boolean active; private int glyphSize=22;private boolean plain; private String iconName="";private final android.graphics.Paint activeDot=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        IconControl(){super(MainActivity.this);}
        void icon(String name){if(name.equals(iconName))return;iconName=name;glyph=new GlyphDrawable(name,primary?BG:plain||active?ACCENT:MUTED,dp(primary?28:glyphSize));setText("");setCompoundDrawables(null,null,null,null);invalidate();}
        void active(boolean value){if(active==value)return;active=value;String name=iconName;iconName="";icon(name);setSelected(value);}
        @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);if(glyph!=null){int size=dp(primary?28:glyphSize);int left=(getWidth()-size)/2,top=(getHeight()-size)/2;glyph.setBounds(left,top,left+size,top+size);glyph.draw(canvas);if(active){activeDot.setColor(ACCENT);canvas.drawCircle(getWidth()/2f,getHeight()-dp(6),dp(2),activeDot);}}}
    }
    private Button button(String label,Runnable action) { Button b=new IconControl(); b.setText(label); b.setAllCaps(false); b.setTextColor(ACCENT); b.setTextSize(14); b.setMinHeight(dp(TOUCH)); b.setMinimumHeight(dp(TOUCH)); b.setMinWidth(0);b.setMinimumWidth(0);b.setStateListAnimator(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20ffffff),background(TINT,24),null)); b.setPadding(dp(16),0,dp(16),0); b.setOnClickListener(v->action.run());if(GlyphDrawable.name(label)!=null){b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20ffffff),background(Color.TRANSPARENT,24),null));setGlyph(b,label);} return b; }
    private void setGlyph(Button button,String icon){String name=GlyphDrawable.name(icon);if(name==null){button.setText(icon);return;}((IconControl)button).icon(name);}
    private Button iconButton(String icon,String description,Runnable action) { Button b=button(icon,action);b.setContentDescription(description);b.setMinWidth(dp(TOUCH));b.setPadding(0,0,0,0);return b; }
    private Button roundPlay(int size){IconControl b=(IconControl)iconButton("▶","Play music",this::toggle);b.primary=true;b.iconName="";b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000),background(ACCENT,size/2),null));b.setElevation(dp(2));b.icon("play");return b;}
    private Button largeTransport(String icon,String description,Runnable action,int size){IconControl b=(IconControl)iconButton(icon,description,action);b.glyphSize=size;b.plain=true;b.iconName="";b.icon(GlyphDrawable.name(icon));return b;}
    private final class SquareCover extends ImageView {
        SquareCover(){super(MainActivity.this);setScaleType(ImageView.ScaleType.FIT_CENTER);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){super.onMeasure(widthSpec,heightSpec);int size=getMeasuredWidth();setMeasuredDimension(size,size);}
    }
    private void space(LinearLayout l,int h) { View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private boolean permission() { return checkSelfPermission(Build.VERSION.SDK_INT>=33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED; }
    private void allow() {
        String p=Build.VERSION.SDK_INT>=33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if(!shouldShowRequestPermissionRationale(p) && library.prefs().getBoolean("requested",false)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); return;
        }
        library.prefs().edit().putBoolean("requested",true).apply(); requestPermissions(new String[]{p},10);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] grants) { super.onRequestPermissionsResult(r,p,grants); if(r==10)scan(); }
    private void registerMediaObserver(){
        mediaObserver=new android.database.ContentObserver(handler){@Override public void onChange(boolean selfChange){libraryDirty=true;libraryRevision++;handler.removeCallbacks(refreshDirty);handler.postDelayed(refreshDirty,600);}};
        getContentResolver().registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,mediaObserver);
    }
    private void rememberList(AbsListView list,String key){if(listPositions.size()>64)listPositions.clear();displayedList=list;displayedListKey=key;android.os.Parcelable position=listPositions.get(key);if(position!=null)list.post(()->{if(list.isAttachedToWindow())list.onRestoreInstanceState(position);});}
    private List<Track> visibleWarmTracks(List<Track> found){LinkedHashMap<String,Track> selected=new LinkedHashMap<>();Track current=currentTrack();if(current!=null&&trackIndex.containsKey(current.id))selected.put(current.id,trackIndex.get(current.id));for(Track t:recentHistory()){if(selected.size()>=4)break;selected.put(t.id,t);}for(Track t:found){if(selected.size()>=6)break;if(favouriteIds.contains(t.id))selected.put(t.id,t);}for(Track t:found){if(selected.size()>=6)break;selected.put(t.id,t);}return new ArrayList<>(selected.values());}
    private void scan() {
        if(loading)return; if(!permission()) { tracks.clear();trackIndex.clear();coverKeys.clear();searchIndex=new SearchIndex(Collections.emptyList());titleSearchIndex=searchIndex;libraryDirty=true; render(); return; }
        final long revision=libraryRevision;boolean firstLoad=!startupComplete;loading=true; error="";if(firstLoad)render();
        worker.execute(()-> { try { List<Track> found=library.scan(); List<String[]> metadata=new ArrayList<>(),titles=new ArrayList<>();for(Track t:found){metadata.add(new String[]{t.id,t.title,t.artist,t.album});titles.add(new String[]{t.id,t.title,"",""});}SearchIndex indexed=new SearchIndex(metadata),titleIndexed=new SearchIndex(titles); handler.post(()-> { if(isFinishing()||isDestroyed())return;searchIndex=indexed;titleSearchIndex=titleIndexed;libraryDirty=libraryRevision!=revision;tracks=found;trackIndex.clear();for(Track t:found)trackIndex.put(t.id,t);coverKeys.clear();lastScan=SystemClock.elapsedRealtime(); if(!pendingCoverId.isEmpty())coverTarget=trackById(pendingCoverId); List<Track> warmTracks=visibleWarmTracks(found);Runnable ready=()->{if(isFinishing()||isDestroyed())return;startupComplete=true;loading=false;render();if(libraryDirty)handler.postDelayed(refreshDirty,600);};if(firstLoad)artwork.warm(warmTracks,ready);else ready.run(); }); }
        catch(Exception e) { handler.post(()-> { loading=false; error="Your library couldn't be read. Check audio access, then try refreshing."; render(); }); } });
    }
    private void render() {
        if(isDestroyed())return;colouredSong="";if(displayedList!=null)listPositions.put(displayedListKey,displayedList.onSaveInstanceState());displayedList=null;searchResults=null;searchCount=null;coverKeys.clear();scheduleTick();
        homeTitle=null;homeArtist=null;homeRemaining=null;homeArt=null;homePlay=null;homeProgress=null;miniProgress=null;homeSeed=null;seeking=false;content=null;body=null; mini=null; miniTitle=null; miniArtist=null; miniPlay=null; nowArt=null;nowFavourite=null;nowTitle=null;nowArtist=null;elapsed=null;remaining=null;seek=null;playButton=null;shuffleButton=null;repeatButton=null;
        root=column(); root.setBackgroundColor(BG);boolean searchPage=tab.equals("Search")&&collectionIds==null&&section.isEmpty();boolean home=tab.equals("Home")&&collectionIds==null&&section.isEmpty();if(home||searchPage)root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{HOME_TOP,HOME_BG}));
        root.setOnApplyWindowInsetsListener((v,insets)-> { if(Build.VERSION.SDK_INT>=30) { android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets; });
        windowFrame=new FrameLayout(this);windowFrame.setBackground((home||searchPage)?new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{HOME_TOP,HOME_BG}):new ColorDrawable(BG));windowFrame.addView(root,new FrameLayout.LayoutParams(dp(contentWidthDp()),-1,Gravity.CENTER_HORIZONTAL));setContentView(windowFrame); root.requestApplyInsets();
        if(loading&&!startupComplete){startupLoading();return;}
        if(full) { nowPlaying(); return; }
        if(home)root.addView(homeHeader());else if(searchPage){
            LinearLayout header=column();header.setPadding(dp(20),dp(12),dp(20),dp(8));TextView eyebrow=text("AURA // OFFLINE LIBRARY",10,HOME_GOLD,true);header.addView(eyebrow);LinearLayout top=row();top.addView(text("Search",26,HOME_TEXT,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(iconButton("•••","Library options",this::libraryOptions));header.addView(top);header.addView(text(loading?"Finding your music…":tracks.size()+" offline songs on your phone",12,HOME_MUTED,false));root.addView(header);
        }else{
        LinearLayout header=column(); header.setPadding(dp(24),dp(18),dp(24),dp(8));
        LinearLayout top=row();top.addView(text(collectionIds!=null?collectionTitle:section.isEmpty()?tab:section,28,INK,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(iconButton("•••","Library options",this::libraryOptions));header.addView(top);space(header,4);
        header.addView(text(loading?"Finding your music…":tracks.size()+" songs on your phone",12,MUTED,false));root.addView(header);}
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); body=column(); body.setPadding(dp(24),dp(12),dp(24),dp(24)); scroll.addView(body); content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(!permission()) empty("Your music, right here.","Let AURA read the audio files already on your phone. Your songs stay on your device.","Allow audio access",this::allow);
        else if(!error.isEmpty()) empty("Let's try that again",error,"Refresh library",this::scan);
        else if(loading) { for(int i=0;i<6;i++) { View skeleton=new View(this); skeleton.setBackground(background(TINT,14)); body.addView(skeleton,new LinearLayout.LayoutParams(-1,dp(64))); space(body,12); } }
        else if(tracks.isEmpty()) empty("A home for your songs","Copy audio files into your phone’s Music folder, then refresh. Files hidden inside other apps may not be available.","Refresh library",this::scan);
        else if(collectionIds!=null) collectionScreen();
        else if(tab.equals("Home")) homeScreen();
        else if(tab.equals("Search")) searchScreen();
        else if(tab.equals("Playlists")) playlistScreen();
        else libraryScreen();
        mini=row(); mini.setPadding(dp(12),dp(6),dp(8),dp(6)); mini.setBackground(homeCard(18));mini.setElevation(dp(5));
        ImageView small=new ImageView(this); small.setScaleType(ImageView.ScaleType.FIT_CENTER); mini.addView(small,new LinearLayout.LayoutParams(dp(44),dp(44))); Track current=currentTrack(); if(current!=null)artwork.load(small,current);
        LinearLayout labels=column(); labels.setPadding(dp(12),0,dp(8),0); miniTitle=text("Choose something to play",15,INK,true); miniTitle.setSingleLine(true); miniArtist=text("Your library, your soundtrack",12,MUTED,false); miniArtist.setSingleLine(true); labels.addView(miniTitle); labels.addView(miniArtist); mini.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.setOnClickListener(v->openNow()); small.setOnClickListener(v->openNow()); miniPlay=roundPlay(48);miniPlay.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000),new android.graphics.drawable.InsetDrawable(background(ACCENT,20),dp(4)),null)); mini.addView(miniPlay,new LinearLayout.LayoutParams(dp(48),dp(48))); mini.addView(iconButton("›","Next song",()->{ if(player!=null)player.seekToNextMediaItem(); })); LinearLayout.LayoutParams miniParams=new LinearLayout.LayoutParams(-1,-2);miniParams.setMargins(dp(12),dp(4),dp(12),dp(4));{LinearLayout floating=column();floating.setBackground(homeCard(18));floating.setElevation(dp(5));mini.setBackgroundColor(Color.TRANSPARENT);miniProgress=homeProgressBar();floating.addView(miniProgress,new LinearLayout.LayoutParams(-1,dp(2)));floating.addView(mini);root.addView(floating,miniParams);}
        LinearLayout nav=row(); for(String name:new String[]{"Home","Library","Search","Playlists"}) { Button b=button(name,()->{tab=name;section="";query="";collectionIds=null;currentPlaylist="";render();}); b.setBackgroundColor(BG);int color=name.equals(tab)?((home||searchPage)?HOME_ACCENT:ACCENT):MUTED;b.setTextColor(color);b.setTextSize(12);b.setPadding(0,0,0,0);b.setCompoundDrawablesWithIntrinsicBounds(null,new GlyphDrawable(name.toLowerCase(Locale.ROOT),color,dp(22)),null,null);b.setCompoundDrawablePadding(dp(3));nav.addView(b,new LinearLayout.LayoutParams(0,dp(60),1)); } root.addView(nav); updatePlayback();scheduleTick();
    }
    private void startupLoading(){
        LinearLayout launch=column();launch.setGravity(Gravity.CENTER);launch.setPadding(dp(24),dp(24),dp(24),dp(24));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.aura_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("AURA logo");launch.addView(logo,new LinearLayout.LayoutParams(dp(108),dp(108)));space(launch,20);
        root.addView(launch,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void empty(String title,String subtitle,String action,Runnable r) { space(body,40); body.addView(text("♫",72,ACCENT,true)); space(body,20); body.addView(text(title,26,INK,true)); space(body,12); body.addView(text(subtitle,16,MUTED,false)); space(body,24); body.addView(button(action,r)); }
    private void libraryScreen() {
        if(section.isEmpty()){for(String name:new String[]{"Songs","Favourites","Albums","Artists"}){Button shortcut=button(name+"  ›",()->{section=name;render();});shortcut.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);shortcut.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20ffffff),background(SURFACE,16),null));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(56));params.bottomMargin=dp(10);body.addView(shortcut,params);}space(body,14);moodChips(body);}
        else if(section.equals("Songs")) songList(tracks);
        else if(section.equals("Favourites")){List<Track> list=favouriteTracks();if(list.isEmpty())empty("Your favourites", "Tap the heart beside a song to save it here.","Browse songs",()->{section="Songs";render();});else songList(list);}
        else if(section.equals("Albums")) albumGrid();
        else artistList();
    }
    private void artistList(){
        Map<String,SearchGroup> grouped=new LinkedHashMap<>();for(Track track:tracks){String name=track.artist==null||track.artist.trim().isEmpty()?"Unknown artist":track.artist;grouped.computeIfAbsent(name,key->new SearchGroup(name)).songs.add(track);}
        List<SearchGroup> artists=new ArrayList<>(grouped.values());artists.sort(Comparator.comparing(g->g.title,String.CASE_INSENSITIVE_ORDER));ListView list=new ListView(this);list.setDivider(null);list.setPadding(dp(20),dp(8),dp(20),dp(12));list.setAdapter(new SearchGroupsAdapter(artists));content.removeAllViews();content.addView(list,new FrameLayout.LayoutParams(-1,-1));rememberList(list,"Library|Artists");
    }
    private List<Track> favouriteTracks(){List<Track> list=new ArrayList<>();for(Track t:tracks)if(favouriteIds.contains(t.id))list.add(t);return list;}
    private void favouriteLabel(Button b,Track t){boolean saved=t!=null&&favouriteIds.contains(t.id);changed(b,saved?"♥":"♡");description(b,(saved?"Remove from favourites: ":"Add to favourites: ")+(t==null?"current song":t.title));if(b.isSelected()!=saved)b.setSelected(saved);}
    private void toggleFavourite(Track t){if(t==null)return;if(!favouriteIds.remove(t.id))favouriteIds.add(t.id);library.prefs().edit().putStringSet("favourites",new HashSet<>(favouriteIds)).apply();updatePlayback();}
    private static final class Shelf {final String title;final List<Track> songs;final int size;final boolean albums;int scrollX;Shelf(String title,List<Track> songs,int size,boolean albums){this.title=title;this.songs=new ArrayList<>(songs);this.size=size;this.albums=albums;}}
    private List<Track> recentHistory(){String stored=library.prefs().getString("play_history","");List<String> ids=stored.isEmpty()?Collections.emptyList():Arrays.asList(stored.split("\n"));return resolve(ids);}
    private void recordHistory(){if(player==null||!player.isPlaying()||player.getCurrentMediaItem()==null)return;String id=player.getCurrentMediaItem().mediaId;if(id.equals(lastHistoryId))return;lastHistoryId=id;List<String> ids=new ArrayList<>(Arrays.asList(library.prefs().getString("play_history","").split("\n")));ids.removeIf(value->value.isEmpty()||value.equals(id));ids.add(0,id);if(ids.size()>50)ids=new ArrayList<>(ids.subList(0,50));library.prefs().edit().putString("play_history",android.text.TextUtils.join("\n",ids)).apply();}
    private final class ShelfScroll extends HorizontalScrollView {
        final List<ImageView> images=new ArrayList<>();final List<Track> songs=new ArrayList<>();final List<View> anchors=new ArrayList<>();Shelf shelf;boolean restoring;
        ShelfScroll(){super(MainActivity.this);setHorizontalScrollBarEnabled(false);addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->loadVisible());}
        void loadVisible(){if(restoring||getWidth()==0||!isAttachedToWindow()||!isShown())return;int left=getScrollX()-getPaddingLeft(),right=left+getWidth();int first=-1,last=-1;for(int i=0;i<anchors.size();i++){View card=anchors.get(i);if(card.getRight()>left&&card.getLeft()<right){if(first<0)first=i;last=i;}}if(first<0)return;for(int i=Math.max(0,first-1);i<=Math.min(images.size()-1,last+1);i++){ImageView image=images.get(i);if(image.getTag()==null)artwork.load(image,songs.get(i));}}
        @Override protected void onScrollChanged(int left,int top,int oldLeft,int oldTop){super.onScrollChanged(left,top,oldLeft,oldTop);if(shelf!=null&&!restoring)shelf.scrollX=left;loadVisible();}
    }
    private final class ShelfCell {LinearLayout card;ImageView cover;TextView title,subtitle;Track track;}
    private final class ShelfHolder {Shelf bound;LinearLayout block,cards;TextView heading,hint;ShelfScroll scroll;final List<ShelfCell> cells=new ArrayList<>();}
    private ShelfCell shelfCell(Shelf shelf){
        ShelfCell cell=new ShelfCell();cell.card=column();boolean portrait=shelf.title.startsWith("Favourites");int height=portrait?shelf.size+80:shelf.size;
        if(portrait){FrameLayout hero=new FrameLayout(this);hero.setBackground(background(SURFACE,18));hero.setClipToOutline(true);cell.cover=artworkView(hero);cell.cover.setScaleType(ImageView.ScaleType.FIT_CENTER);hero.addView(cell.cover,new FrameLayout.LayoutParams(-1,dp(shelf.size),Gravity.TOP));LinearLayout labels=column();labels.setPadding(dp(12),dp(8),dp(12),dp(10));cell.title=text("",17,INK,true);cell.subtitle=text("",12,INK,false);labels.addView(cell.title);space(labels,2);labels.addView(cell.subtitle);hero.addView(labels,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));cell.card.addView(hero,new LinearLayout.LayoutParams(dp(shelf.size),dp(height)));}
        else{cell.cover=artworkView(cell.card);cell.cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cell.cover.setBackground(background(SURFACE,14));cell.cover.setClipToOutline(true);cell.cover.setElevation(dp(4));cell.card.addView(cell.cover,new LinearLayout.LayoutParams(dp(shelf.size),dp(height)));space(cell.card,8);cell.title=text("",shelf.size>180?17:shelf.size<=128?12:14,INK,true);cell.subtitle=text("",shelf.size<=128?11:12,MUTED,false);cell.card.addView(cell.title);cell.card.addView(cell.subtitle);}
        cell.title.setLines(2);cell.title.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.subtitle.setSingleLine(true);cell.subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.card.setOnClickListener(v->{int index=tracks.indexOf(cell.track);if(index>=0)play(tracks,index,false);});cell.card.setOnLongClickListener(v->{if(cell.track!=null)trackMenu(cell.track);return true;});return cell;
    }
    private GradientDrawable homeCard(int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{HOME_SURFACE,HOME_BG});d.setCornerRadius(dp(radius));d.setStroke(dp(1),0x30ffffff);return d;}
    private ProgressBar homeProgressBar(){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(1000);p.setProgressTintList(ColorStateList.valueOf(HOME_ACCENT));p.setProgressBackgroundTintList(ColorStateList.valueOf(0x28ffffff));GradientDrawable base=background(0x28ffffff,3),fill=background(HOME_ACCENT,3);android.graphics.drawable.ClipDrawable clipped=new android.graphics.drawable.ClipDrawable(fill,Gravity.LEFT,android.graphics.drawable.ClipDrawable.HORIZONTAL);android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base,clipped});layers.setId(0,android.R.id.background);layers.setId(1,android.R.id.progress);p.setProgressDrawable(layers);return p;}
    private void homeNavigate(String destination){tab=destination.equals("Playlists")?"Playlists":"Library";section=destination.equals("Playlists")?"":destination;collectionIds=null;currentPlaylist="";render();}
    private LinearLayout homeHeader(){LinearLayout h=column();h.setPadding(dp(20),dp(16),dp(20),dp(6));TextView vault=text("Your phone. Your music. Your aura.",11,HOME_ACCENT,true);vault.setLetterSpacing(.14f);h.addView(vault);LinearLayout line=row();line.addView(text("Home",26,HOME_TEXT,true),new LinearLayout.LayoutParams(0,-2,1));Button audio=iconButton("•••","Sound and playback settings",this::playerOptions);line.addView(audio,new LinearLayout.LayoutParams(dp(48),dp(48)));Button settings=iconButton("⚙","Library settings",this::libraryOptions);line.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));h.addView(line);LinearLayout status=row();status.setPadding(dp(12),dp(8),dp(12),dp(8));status.setBackground(homeCard(16));TextView ready=text(loading?"Finding your music…":"●  Offline ready · "+tracks.size()+" songs",12,HOME_ACCENT,true);status.addView(ready,new LinearLayout.LayoutParams(0,-2,1));if(contentWidthDp()>=360&&getResources().getConfiguration().fontScale<=1.2f)status.addView(text("ON DEVICE",9,HOME_MUTED,true));h.addView(status);return h;}
    private LinearLayout homeIntro(){LinearLayout intro=column();intro.setPadding(dp(20),dp(8),dp(20),dp(4));HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();for(String name:new String[]{"All","Playlists","Artists","Albums","Favourites"}){Button chip=button(name,()->{if(!name.equals("All"))homeNavigate(name);});chip.setTextSize(11);chip.setTextColor(name.equals("All")?HOME_BG:HOME_MUTED);chip.setBackground(new android.graphics.drawable.InsetDrawable(name.equals("All")?background(HOME_TEXT,16):homeCard(16),0,dp(8),0,dp(8)));chip.setPadding(dp(12),0,dp(12),0);chip.setMinimumWidth(dp(48));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(48));cp.setMarginEnd(dp(6));chips.addView(chip,cp);}filters.addView(chips);intro.addView(filters);space(intro,10);
        homeSeed=currentTrack();if(homeSeed==null){List<Track> recent=recentHistory();homeSeed=recent.isEmpty()?tracks.get(0):recent.get(0);}LinearLayout hero=column();hero.setPadding(dp(16),dp(16),dp(16),dp(18));hero.setBackground(homeCard(24));hero.setElevation(dp(8));LinearLayout label=row();label.addView(text("●  JUMP BACK IN",10,HOME_ACCENT,true),new LinearLayout.LayoutParams(0,-2,1));homeRemaining=text("",10,HOME_MUTED,false);label.addView(homeRemaining);hero.addView(label);space(hero,8);LinearLayout info=row();homeArt=new ImageView(this);homeArt.setScaleType(ImageView.ScaleType.FIT_CENTER);homeArt.setBackground(background(HOME_SURFACE,12));homeArt.setClipToOutline(true);homeArt.setElevation(dp(6));info.addView(homeArt,new LinearLayout.LayoutParams(dp(76),dp(76)));LinearLayout labels=column();labels.setPadding(dp(10),0,dp(8),0);homeTitle=text(homeSeed.title,16,HOME_TEXT,true);homeTitle.setSingleLine(true);homeArtist=text(homeSeed.artist,12,HOME_MUTED,false);homeArtist.setSingleLine(true);labels.addView(homeTitle);space(labels,3);labels.addView(homeArtist);space(labels,10);homeProgress=homeProgressBar();labels.addView(homeProgress,new LinearLayout.LayoutParams(-1,dp(4)));info.addView(labels,new LinearLayout.LayoutParams(0,-2,1));homePlay=roundPlay(48);homePlay.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000),new android.graphics.drawable.InsetDrawable(background(HOME_ACCENT,20),dp(4)),null));homePlay.setOnClickListener(v->{if(currentTrack()==null)play(tracks,tracks.indexOf(homeSeed),false);else toggle();});info.addView(homePlay,new LinearLayout.LayoutParams(dp(48),dp(48)));labels.setOnClickListener(v->{if(currentTrack()==null)play(tracks,tracks.indexOf(homeSeed),false);openNow();});homeArt.setOnClickListener(v->labels.performClick());hero.addView(info);intro.addView(hero);artwork.load(homeArt,homeSeed);
        List<Track> recent=recentHistory();if(!recent.isEmpty()){space(intro,16);LinearLayout heading=row();heading.addView(text("Recently played",16,HOME_TEXT,true),new LinearLayout.LayoutParams(0,-2,1));Button clear=button("Clear",()->{library.prefs().edit().remove("play_history").apply();lastHistoryId=currentTrack()==null?"":currentTrack().id;render();});clear.setTextColor(HOME_MUTED);clear.setTextSize(12);clear.setBackgroundColor(Color.TRANSPARENT);heading.addView(clear);intro.addView(heading);for(Track track:recent.subList(0,Math.min(3,recent.size()))){LinearLayout item=row();item.setPadding(dp(10),dp(8),dp(4),dp(8));item.setBackground(homeCard(16));ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.setBackground(background(HOME_SURFACE,10));art.setClipToOutline(true);item.addView(art,new LinearLayout.LayoutParams(dp(40),dp(40)));artwork.load(art,track);LinearLayout names=column();names.setPadding(dp(12),0,dp(6),0);TextView title=text(track.title,13,HOME_TEXT,true);title.setSingleLine(true);markSongTitle(title,track);names.addView(title);TextView artist=text(track.artist,11,HOME_MUTED,false);artist.setSingleLine(true);names.addView(artist);item.addView(names,new LinearLayout.LayoutParams(0,-2,1));item.addView(text(time(track.duration),11,HOME_MUTED,false));item.addView(iconButton("•••","Options for "+track.title,()->trackMenu(track)),new LinearLayout.LayoutParams(dp(48),dp(48)));names.setOnClickListener(v->play(tracks,tracks.indexOf(track),false));art.setOnClickListener(v->names.performClick());LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.bottomMargin=dp(6);intro.addView(item,ip);}}
        return intro;
    }
    private View homeFavouriteRow(Shelf shelf){LinearLayout block=column();block.setPadding(dp(20),dp(8),dp(20),dp(8));if(shelf.title.equals("Favourites")){LinearLayout heading=row();heading.addView(text("Favourites · "+favouriteTracks().size(),16,HOME_TEXT,true),new LinearLayout.LayoutParams(0,-2,1));Button all=button("View all",()->homeNavigate("Favourites"));all.setTextColor(HOME_MUTED);all.setTextSize(12);all.setBackgroundColor(Color.TRANSPARENT);heading.addView(all);block.addView(heading);}if(shelf.songs.isEmpty()){block.addView(text("Save songs from the player or song options",13,HOME_MUTED,false));return block;}LinearLayout cards=row();cards.setGravity(Gravity.TOP);for(Track t:shelf.songs){LinearLayout card=column();card.setPadding(dp(10),dp(10),dp(10),dp(12));card.setBackground(homeCard(18));card.setElevation(dp(3));ImageView art=new SquareCover();art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.setBackground(background(HOME_SURFACE,12));art.setClipToOutline(true);card.addView(art,new LinearLayout.LayoutParams(-1,-2));artwork.load(art,t);space(card,10);TextView title=text(t.title,14,HOME_TEXT,true);title.setSingleLine(true);card.addView(title);TextView artist=text(t.artist,12,HOME_MUTED,false);artist.setSingleLine(true);card.addView(artist);card.setOnClickListener(v->play(tracks,tracks.indexOf(t),false));card.setOnLongClickListener(v->{trackMenu(t);return true;});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(cards.getChildCount()==0)cp.setMarginEnd(dp(14));cards.addView(card,cp);}if(shelf.songs.size()==1)cards.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));block.addView(cards);return block;}
    private void homeScreen(){
        List<Shelf> shelves=new ArrayList<>();List<Track> pinned=new ArrayList<>();for(Track t:tracks)if(java.util.regex.Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])radh?imma(?![\\p{L}\\p{N}])").matcher(t.title).find())pinned.add(t);if(!pinned.isEmpty())shelves.add(new Shelf("Pinned for you",pinned,128,false));
        List<String> albumKeys=new ArrayList<>();for(Track t:tracks)albumKeys.add(t.albumKey());List<Track> mixed=new ArrayList<>();for(int index:HomeMixRules.order(albumKeys))mixed.add(tracks.get(index));for(int start=0;start<mixed.size();start+=12)shelves.add(new Shelf(start==0?"Your music mix":"More to explore · "+(start/12+1),mixed.subList(start,Math.min(start+12,mixed.size())),128,false));
        List<Track> favourites=favouriteTracks();shelves.add(0,new Shelf("Favourites",favourites,220,false));
        ListView list=new ListView(this);list.setDivider(null);list.setClipToPadding(false);list.setPadding(0,dp(4),0,dp(12));list.addHeaderView(homeIntro(),null,false);list.setAdapter(new BaseAdapter(){
            public int getCount(){return shelves.size();}public Object getItem(int position){return shelves.get(position);}public long getItemId(int position){return position;}
            public int getViewTypeCount(){return 4;}public int getItemViewType(int position){Shelf shelf=shelves.get(position);return shelf.size==0?2:shelf.songs.isEmpty()?3:shelf.size>180?1:0;}
            public View getView(int position,View reused,ViewGroup parent){
                Shelf shelf=shelves.get(position);if(shelf.size==0)return homeFavouriteRow(shelf);ShelfHolder holder;
                if(reused==null){holder=new ShelfHolder();holder.block=column();holder.block.setPadding(0,dp(8),0,dp(8));holder.heading=text("",16,HOME_TEXT,true);holder.heading.setPadding(dp(20),0,dp(20),dp(12));holder.block.addView(holder.heading);holder.hint=text("Save favourites from song options or the player",14,MUTED,false);holder.hint.setPadding(dp(20),dp(12),dp(20),dp(20));holder.block.addView(holder.hint);holder.scroll=new ShelfScroll();holder.scroll.setClipToPadding(false);holder.scroll.setPadding(dp(20),0,dp(12),0);holder.cards=row();holder.cards.setGravity(Gravity.TOP);holder.scroll.addView(holder.cards);holder.block.addView(holder.scroll);holder.block.setTag(holder);}else holder=(ShelfHolder)reused.getTag();
                if(holder.bound==shelf){holder.scroll.loadVisible();return holder.block;}
                holder.bound=shelf;holder.heading.setText(shelf.title);holder.hint.setVisibility(shelf.songs.isEmpty()?View.VISIBLE:View.GONE);holder.scroll.setVisibility(shelf.songs.isEmpty()?View.GONE:View.VISIBLE);holder.scroll.shelf=shelf;holder.scroll.restoring=true;holder.scroll.images.clear();holder.scroll.songs.clear();holder.scroll.anchors.clear();
                for(int i=0;i<shelf.songs.size();i++){ShelfCell cell;if(i<holder.cells.size())cell=holder.cells.get(i);else{cell=shelfCell(shelf);holder.cells.add(cell);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(shelf.size),-2);params.setMarginEnd(dp(14));holder.cards.addView(cell.card,params);}Track track=shelf.songs.get(i);boolean sameTrack=cell.track!=null&&cell.track.id.equals(track.id);cell.track=track;cell.card.setVisibility(View.VISIBLE);if(!sameTrack){cell.card.setBackgroundColor(Color.TRANSPARENT);cell.cover.setTag(null);}cell.cover.setContentDescription("Album cover for "+track.album);cell.title.setText(shelf.albums?track.album:track.title);if(!shelf.albums)markSongTitle(cell.title,track);cell.subtitle.setText(shelf.albums?track.albumArtist:track.artist);holder.scroll.images.add(cell.cover);holder.scroll.songs.add(track);holder.scroll.anchors.add(cell.card);}
                for(int i=shelf.songs.size();i<holder.cells.size();i++){ShelfCell cell=holder.cells.get(i);cell.track=null;cell.cover.setTag(null);cell.card.setVisibility(View.GONE);}
                ShelfHolder boundHolder=holder;holder.scroll.post(()->{if(boundHolder.bound!=shelf)return;boundHolder.scroll.scrollTo(shelf.scrollX,0);boundHolder.scroll.restoring=false;boundHolder.scroll.loadVisible();});return holder.block;
            }
        });content.removeAllViews();content.addView(list,new FrameLayout.LayoutParams(-1,-1));rememberList(list,"Home");
    }
    private void moodChips(LinearLayout parent){
        parent.addView(text("Mood mixes",18,INK,true));space(parent,8);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();for(String mood:MoodStore.labels()){Button chip=button(mood,()->playMood(mood));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(TOUCH));params.setMarginEnd(dp(8));chips.addView(chip,params);}scroll.addView(chips);parent.addView(scroll);
    }
    private void playMood(String mood){List<Track> mix=moods.tracksFor(tracks,mood);if(!mix.isEmpty()){play(mix,0,true);toast(mood+" mix · "+mix.size()+" songs");return;}
        new AlertDialog.Builder(this).setTitle("Build your "+mood+" mix").setMessage("Choose songs that fit this mood. Your choices stay on your phone.").setPositiveButton("Choose songs",(d,i)->chooseMoodSongs(mood)).setNegativeButton("Cancel",null).show();
    }
    private void chooseMoodSongs(String mood){
        List<Track> songs=new ArrayList<>(tracks);String[] labels=new String[songs.size()];boolean[] checked=new boolean[songs.size()];for(int i=0;i<songs.size();i++){Track track=songs.get(i);labels[i]=track.title+" · "+track.artist;checked[i]=moods.moodsFor(track.id).contains(mood);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Songs for "+mood).setMultiChoiceItems(labels,checked,(d,i,value)->checked[i]=value).setPositiveButton("Save and play",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{boolean any=false;for(boolean selected:checked)if(selected){any=true;break;}if(!any){toast("Choose at least one song");return;}for(int i=0;i<songs.size();i++){Track track=songs.get(i);Set<String> tags=new LinkedHashSet<>(moods.moodsFor(track.id));if(checked[i])tags.add(mood);else tags.remove(mood);moods.setMoods(track.id,tags);}dialog.dismiss();playMood(mood);}));dialog.show();
    }
    private void setSongMoods(Track track){List<String> labels=MoodStore.labels();Set<String> selected=new LinkedHashSet<>(moods.moodsFor(track.id));boolean[] checked=new boolean[labels.size()];for(int i=0;i<labels.size();i++)checked[i]=selected.contains(labels.get(i));new AlertDialog.Builder(this).setTitle("Moods for "+track.title).setMultiChoiceItems(labels.toArray(new String[0]),checked,(d,i,value)->{if(value)selected.add(labels.get(i));else selected.remove(labels.get(i));}).setPositiveButton("Save",(d,i)->{moods.setMoods(track.id,selected);toast("Song moods saved");}).setNegativeButton("Cancel",null).show();}
    private void chooseMood(){new AlertDialog.Builder(this).setTitle("Play a mood mix").setItems(MoodStore.labels().toArray(new String[0]),(d,i)->playMood(MoodStore.labels().get(i))).setNegativeButton("Cancel",null).show();}
    private void sleepTimer() {
        if(player==null){toast("Playback is still connecting");return;}
        ListenableFuture<SessionResult> status=player.sendCustomCommand(PlaybackService.GET_SLEEP_TIMER,Bundle.EMPTY);
        status.addListener(()->{
            if(isFinishing()||isDestroyed())return;
            long remaining=0;
            try{SessionResult result=status.get();if(result.resultCode!=SessionResult.RESULT_SUCCESS){toast("Sleep timer is unavailable");return;}remaining=result.extras.getLong("remainingMillis");}
            catch(Exception e){toast("Sleep timer is unavailable");return;}
            int initial=(int)Math.min(60,(remaining+59999)/60000);LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));TextView duration=text(initial==0?"Off":initial+" min",28,INK,true);duration.setGravity(Gravity.CENTER);form.addView(duration);SeekBar slider=new SeekBar(this);slider.setMax(60);slider.setKeyProgressIncrement(1);slider.setProgress(initial);slider.setMinimumHeight(dp(TOUCH));slider.setContentDescription("Sleep timer minutes, zero turns timer off");slider.setProgressTintList(ColorStateList.valueOf(ACCENT));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean user){duration.setText(value==0?"Off":value+" min");}});form.addView(slider,new LinearLayout.LayoutParams(-1,dp(TOUCH)));form.addView(text("Pause playback after 0–60 minutes",12,MUTED,false));
            new AlertDialog.Builder(this).setTitle("Sleep timer").setView(form).setPositiveButton("Set",(d,i)->setSleepTimer(slider.getProgress())).setNeutralButton("Cancel timer",(d,i)->setSleepTimer(0)).setNegativeButton("Close",null).show();
        },command->handler.post(command));
    }
    private void setSleepTimer(int minutes) {
        if(player==null){toast("Playback is still connecting");return;}
        Bundle args=new Bundle();args.putInt("minutes",minutes);
        ListenableFuture<SessionResult> result=player.sendCustomCommand(PlaybackService.SET_SLEEP_TIMER,args);
        result.addListener(()->{if(isFinishing()||isDestroyed())return;try{if(result.get().resultCode==SessionResult.RESULT_SUCCESS)toast(minutes==0?"Sleep timer cancelled":"Music will pause in "+minutes+" minutes");else toast("Sleep timer couldn't be changed");}catch(Exception e){toast("Sleep timer couldn't be changed");}},command->handler.post(command));
    }
    private void spatialAudio(){new AlertDialog.Builder(this).setTitle("Spatial audio").setMessage(SpatialAudioSupport.description(this)).setPositiveButton("Sound settings",(d,i)->{try{startActivity(SpatialAudioSupport.settingsIntent(this));}catch(ActivityNotFoundException e){toast("Sound settings are unavailable on this phone");}}).setNegativeButton("Close",null).show();}
    private static final class AlbumHolder {ImageView cover;TextView title,artist;}
    private void albumGrid() {
        Map<String,List<Track>> map=new LinkedHashMap<>();for(Track t:tracks)map.computeIfAbsent(t.albumKey(),key->new ArrayList<>()).add(t);
        List<List<Track>> albums=new ArrayList<>(map.values());albums.sort(Comparator.comparing(group->group.get(0).album,String.CASE_INSENSITIVE_ORDER));
        int columns=ResponsiveLayout.albumColumns(contentWidthDp());
        GridView grid=new GridView(this);grid.setNumColumns(columns);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(18));grid.setPadding(dp(20),dp(12),dp(20),dp(20));grid.setClipToPadding(false);
        grid.setAdapter(new BaseAdapter(){
            @Override public int getCount(){return albums.size();}@Override public Object getItem(int index){return albums.get(index);}@Override public long getItemId(int index){return index;}
            @Override public View getView(int index,View reused,ViewGroup parent){
                LinearLayout tile;AlbumHolder holder;
                if(reused==null){tile=column();holder=new AlbumHolder();holder.cover=new SquareCover();tile.addView(holder.cover,new LinearLayout.LayoutParams(-1,-2));space(tile,8);holder.title=text("",15,INK,true);holder.title.setMaxLines(2);holder.artist=text("",12,MUTED,false);holder.artist.setSingleLine(true);tile.addView(holder.title);tile.addView(holder.artist);tile.setTag(holder);}else{tile=(LinearLayout)reused;holder=(AlbumHolder)tile.getTag();}
                Track track=albums.get(index).get(0);holder.title.setText(track.album);holder.artist.setText(track.albumArtist);artwork.load(holder.cover,track);tile.setOnClickListener(v->showTracks(track.album,albumTracks(track)));return tile;
            }
        });content.removeAllViews();content.addView(grid,new FrameLayout.LayoutParams(-1,-1));rememberList(grid,"Albums");
    }
    private List<Track> albumTracks(Track t) { List<Track> list=new ArrayList<>();for(Track s:tracks)if(s.albumKey().equals(t.albumKey()))list.add(s);list.sort(Comparator.comparingInt((Track song)->song.trackNumber<=0?Integer.MAX_VALUE:song.trackNumber).thenComparing(song->song.title,String.CASE_INSENSITIVE_ORDER));return list; }
    private void groupRow(String title,String subtitle,Track track,Runnable action) {
        LinearLayout r=row(); r.setPadding(0,dp(8),0,dp(8)); ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); r.addView(image,new LinearLayout.LayoutParams(dp(64),dp(64))); artwork.load(image,track);
        LinearLayout info=column(); info.setPadding(dp(14),0,dp(8),0); TextView t=text(title,17,INK,true);t.setMaxLines(2);info.addView(t);info.addView(text(subtitle,13,MUTED,false));r.addView(info,new LinearLayout.LayoutParams(0,-2,1));r.addView(text("›",24,ACCENT,false));r.setOnClickListener(v->action.run());body.addView(r);
    }
    private void showTracks(String title,List<Track> list) {
        collectionTitle=title;collectionIds=new ArrayList<>();currentPlaylist="";
        for(Track t:list)collectionIds.add(t.id);full=false;render();
    }
    private void openPlaylist(String name) {
        currentPlaylist=name; collectionTitle=name; collectionIds=new ArrayList<>(library.playlists().getOrDefault(name,new ArrayList<>()));full=false;render();
    }
    private void collectionScreen() {
        List<Track> list=currentPlaylist.isEmpty()?resolve(collectionIds):resolve(library.playlists().getOrDefault(currentPlaylist,new ArrayList<>()));
        LinearLayout top=row();top.addView(button("‹ Back",()->{collectionIds=null;currentPlaylist="";render();}));
        if(!currentPlaylist.isEmpty()) { String name=currentPlaylist; top.addView(iconButton("•••","Playlist options",()->playlistMenu(name,list))); }body.addView(top);space(body,20);
        if(!list.isEmpty()) {
            ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(180),dp(180));params.gravity=Gravity.CENTER_HORIZONTAL;body.addView(cover,params);artwork.load(cover,list.get(0));space(body,18);
        }
        TextView count=text(list.size()+" songs · "+time(list.stream().mapToLong(t->t.duration).sum()),14,MUTED,false);count.setGravity(Gravity.CENTER);body.addView(count);space(body,18);
        LinearLayout actions=row();actions.addView(button("▶  Play",()->play(list,0,false)),new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(button("⇄  Shuffle",()->play(list,0,true)),new LinearLayout.LayoutParams(0,dp(52),1));body.addView(actions);space(body,20);
        if(list.isEmpty()) {body.addView(text("Ready for your first song",23,INK,true));space(body,10);body.addView(text("Use a song’s options in your library to add it to this playlist.",16,MUTED,false));} else songList(list);
    }
    private static final class SongHolder {
        ImageView cover;TextView title,subtitle;Button options,favourite;
    }
    private final class SongsAdapter extends BaseAdapter {
        private List<Track> songs;private final boolean searchStyle;
        SongsAdapter(List<Track> tracks){this(tracks,false);}
        SongsAdapter(List<Track> tracks,boolean searchStyle){this.searchStyle=searchStyle;submit(tracks);}
        void submit(List<Track> tracks){songs=new ArrayList<>(tracks);notifyDataSetChanged();}
        @Override public int getCount(){return songs.size();}
        @Override public Track getItem(int position){return songs.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View reused,ViewGroup parent) {
            SongHolder h;LinearLayout r;
            if(reused==null) {
                r=row();r.setElevation(dp(2));r.setMinimumHeight(dp(ROW));r.setPadding(0,dp(8),0,dp(8));h=new SongHolder();
                h.cover=artworkView(r);h.cover.setScaleType(ImageView.ScaleType.FIT_CENTER);r.addView(h.cover,new LinearLayout.LayoutParams(dp(ROW_COVER),dp(ROW_COVER)));
                LinearLayout info=column();info.setPadding(dp(12),0,dp(8),0);h.title=text("",15,INK,true);h.title.setMaxLines(2);h.subtitle=text("",12,MUTED,false);h.subtitle.setSingleLine(true);info.addView(h.title);info.addView(h.subtitle);r.addView(info,new LinearLayout.LayoutParams(0,-2,1));
                h.favourite=button("♡",()->{});r.addView(h.favourite,new LinearLayout.LayoutParams(dp(TOUCH),dp(TOUCH)));h.options=iconButton("•••","Song options",()->{});r.addView(h.options,new LinearLayout.LayoutParams(dp(TOUCH),dp(TOUCH)));r.setTag(h);
            } else {r=(LinearLayout)reused;h=(SongHolder)r.getTag();}
            if(searchStyle){r.setBackground(homeCard(14));r.setPadding(dp(8),dp(8),dp(4),dp(8));h.title.setTextColor(HOME_TEXT);h.subtitle.setTextColor(HOME_MUTED);}
            Track track=getItem(position);h.title.setText(track.title);markSongTitle(h.title,track);h.subtitle.setText(track.artist+" · "+track.album);artwork.load(h.cover,track);
            favouriteLabel(h.favourite,track);h.favourite.setOnClickListener(v->{toggleFavourite(track);if(searchStyle&&searchResults!=null){requestSearch(searchResults);}else if(section.equals("Favourites"))submit(favouriteTracks());else notifyDataSetChanged();});h.options.setContentDescription("Options for "+track.title);h.options.setOnClickListener(v->trackMenu(track));
            r.setOnClickListener(v->{int index=songs.indexOf(track);if(index>=0)play(songs,index,false);});r.setOnLongClickListener(v->{trackMenu(track);return true;});
            return r;
        }
    }
    private ListView songView(List<Track> songs) {
        ListView list=new ListView(this);list.setPadding(dp(20),0,dp(20),dp(16));list.setClipToPadding(false);list.setDivider(null);list.setDividerHeight(0);list.setSelector(new ColorDrawable(TINT));list.setAdapter(new SongsAdapter(songs));return list;
    }
    private void songList(List<Track> songs) {
        ListView list=songView(songs);
        if(body.getChildCount()>0) {
            if(body.getParent() instanceof ViewGroup)((ViewGroup)body.getParent()).removeView(body);
            body.setPadding(0,dp(12),0,dp(20));list.addHeaderView(body,null,false);
        }
        content.removeAllViews();content.addView(list,new FrameLayout.LayoutParams(-1,-1));rememberList(list,tab+"|"+section+"|"+collectionTitle);
    }
    private String searchPositionKey(){return searchFilter+"\n"+query;}
    private void searchScreen() {
        searchFilter="All";
        LinearLayout screen=column();LinearLayout field=column();field.setPadding(dp(20),dp(4),dp(20),dp(8));
        LinearLayout slot=row();slot.setBackground(homeCard(14));slot.setPadding(dp(12),0,0,0);
        ImageView magnifier=new ImageView(this);magnifier.setImageDrawable(new GlyphDrawable("search",HOME_ACCENT,dp(18)));slot.addView(magnifier,new LinearLayout.LayoutParams(dp(20),dp(20)));
        EditText search=new EditText(this);search.setSingleLine();search.setTextColor(HOME_TEXT);search.setHintTextColor(HOME_MUTED);search.setHint("Songs, artists, albums");search.setContentDescription("Search your music");search.setTextSize(14);search.setMinimumHeight(dp(TOUCH));search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(10),0,dp(4),0);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setText(query);slot.addView(search,new LinearLayout.LayoutParams(0,dp(TOUCH),1));
        Button clear=button("×",()->search.setText(""));clear.setTextSize(22);clear.setPadding(0,0,0,0);clear.setTextColor(HOME_MUTED);clear.setContentDescription("Clear search");clear.setBackgroundColor(Color.TRANSPARENT);clear.setVisibility(query.isEmpty()?View.INVISIBLE:View.VISIBLE);slot.addView(clear,new LinearLayout.LayoutParams(dp(TOUCH),dp(TOUCH)));field.addView(slot);
        searchCount=text("Searching your library…",11,HOME_GOLD,false);searchCount.setPadding(0,dp(2),0,dp(2));field.addView(searchCount);screen.addView(field);
        ListView results=new ListView(this);searchResults=results;results.setPadding(dp(16),dp(2),dp(16),dp(12));results.setClipToPadding(false);results.setDivider(new ColorDrawable(Color.TRANSPARENT));results.setDividerHeight(dp(8));results.setSelector(new ColorDrawable(0x22e07a5f));results.setAdapter(new SongsAdapter(Collections.emptyList(),true));screen.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        TextView empty=text("No matches. Try another title, artist or album.",14,HOME_MUTED,false);empty.setPadding(dp(20),dp(20),dp(20),0);screen.addView(empty);results.setEmptyView(empty);
        results.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView view,int state){if(state!=SCROLL_STATE_IDLE)hideSearchKeyboard(search);if(state==SCROLL_STATE_IDLE)searchPositions.put(searchPositionKey(),results.onSaveInstanceState());}public void onScroll(AbsListView view,int first,int visible,int total){}});
        content.removeAllViews();content.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){hideSearchKeyboard(search);requestSearch(results);return true;}return false;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();clear.setVisibility(query.isEmpty()?View.INVISIBLE:View.VISIBLE);searchPositions.clear();requestSearch(results);}});
        requestSearch(results);
    }
    private void styleSearchPill(Button b){boolean active=b.getText().toString().equals(searchFilter);b.setSelected(active);b.setTextColor(active?HOME_BG:HOME_MUTED);b.setBackground(new android.graphics.drawable.InsetDrawable(background(active?HOME_ACCENT:HOME_SURFACE,16),0,dp(8),0,dp(8)));}
    private void hideSearchKeyboard(View view){android.view.inputmethod.InputMethodManager input=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(input!=null)input.hideSoftInputFromWindow(view.getWindowToken(),0);view.clearFocus();}
    private static final class SearchGroup {final String title;final List<Track> songs=new ArrayList<>();SearchGroup(String title){this.title=title;}}
    private boolean searchFieldMatches(String value,String requested){String field=SearchIndex.normalize(value);String normalized=SearchIndex.normalize(requested);if(normalized.isEmpty())return true;for(String word:normalized.split(" "))if(!field.contains(word))return false;return true;}
    private final class SearchGroupsAdapter extends BaseAdapter {
        final List<SearchGroup> groups;SearchGroupsAdapter(List<SearchGroup> groups){this.groups=groups;}
        public int getCount(){return groups.size();}public SearchGroup getItem(int p){return groups.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View reused,ViewGroup parent){SongHolder h;LinearLayout card;if(reused==null){card=row();card.setPadding(dp(8),dp(8),dp(12),dp(8));card.setBackground(homeCard(14));card.setElevation(dp(2));h=new SongHolder();h.cover=artworkView(card);card.addView(h.cover,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout info=column();info.setPadding(dp(12),0,0,0);h.title=text("",14,HOME_TEXT,true);h.title.setMaxLines(2);h.subtitle=text("",12,HOME_MUTED,false);info.addView(h.title);info.addView(h.subtitle);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(text("›",20,HOME_GOLD,false));card.setTag(h);}else{card=(LinearLayout)reused;h=(SongHolder)card.getTag();}SearchGroup group=getItem(p);h.title.setText(group.title);h.subtitle.setText(group.songs.size()+" songs");artwork.load(h.cover,group.songs.get(0));card.setOnClickListener(v->showTracks(group.title,group.songs));return card;}
    }
    private void requestSearch(ListView results) {
        if(results==null)return;if(pendingSearch!=null)handler.removeCallbacks(pendingSearch);
        final int generation=++searchGeneration;final String requested=query,filter=searchFilter;final SearchIndex index=searchIndex,ranking=filter.equals("Songs")?titleSearchIndex:searchIndex;final List<Track> librarySnapshot=new ArrayList<>(tracks);final Set<String> saved=new HashSet<>(favouriteIds);final String positionKey=searchPositionKey();
        if(searchCount!=null)searchCount.setText("Searching your library…");
        pendingSearch=()->worker.execute(()->{
            List<String> ids=ranking.search(requested);List<SearchGroup> grouped=new ArrayList<>();
            if(filter.equals("Artists")||filter.equals("Albums")){Map<String,SearchGroup> groups=new LinkedHashMap<>();for(Track t:librarySnapshot){String name=filter.equals("Artists")?t.artist:t.album;if(searchFieldMatches(name,requested)){String key=filter.equals("Artists")?SearchIndex.normalize(name):t.albumKey();groups.computeIfAbsent(key,k->new SearchGroup(name)).songs.add(t);}}grouped.addAll(groups.values());grouped.sort(Comparator.comparingInt(g->SearchIndex.normalize(g.title).equals(SearchIndex.normalize(requested))?0:1));}
            handler.post(()->{if(generation==searchGeneration&&index==searchIndex&&query.equals(requested)&&searchFilter.equals(filter)&&results.isAttachedToWindow()&&tab.equals("Search")&&!full){
                int count;if(filter.equals("Artists")||filter.equals("Albums")){results.setAdapter(new SearchGroupsAdapter(grouped));count=grouped.size();}else{List<Track> found=resolve(ids);if(filter.equals("Favourites"))found.removeIf(t->!saved.contains(t.id));if(results.getAdapter() instanceof SongsAdapter)((SongsAdapter)results.getAdapter()).submit(found);else results.setAdapter(new SongsAdapter(found,true));count=found.size();}
                if(searchCount!=null)searchCount.setText(count+" "+(filter.equals("Artists")?"artists":filter.equals("Albums")?"albums":"songs")+(requested.trim().isEmpty()?" in your library":" found"));rememberList(results,"Search|"+positionKey);android.os.Parcelable position=searchPositions.get(positionKey);if(position!=null)results.onRestoreInstanceState(position);else results.setSelection(0);
            }});
        });handler.postDelayed(pendingSearch,100);
    }
    private void playlistScreen() {
        body.addView(button("+  New playlist",()->newPlaylist(null)));space(body,18);
        Map<String,List<String>> lists=library.playlists(); if(lists.isEmpty()) { body.addView(text("Make room for a mood",24,INK,true));space(body,10);body.addView(text("Create a playlist, then use a song’s options to add it.",16,MUTED,false)); }
        for(Map.Entry<String,List<String>> e:lists.entrySet()) {
            List<Track> songs=resolve(e.getValue());LinearLayout playlist=row();playlist.setPadding(0,dp(8),0,dp(8));
            if(!songs.isEmpty()) {ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);playlist.addView(cover,new LinearLayout.LayoutParams(dp(64),dp(64)));artwork.load(cover,songs.get(0));cover.setOnClickListener(v->openPlaylist(e.getKey()));}
            LinearLayout info=column();info.setPadding(dp(12),0,dp(8),0);info.addView(text(e.getKey(),18,INK,true));info.addView(text(songs.size()+" songs",13,MUTED,false));playlist.addView(info,new LinearLayout.LayoutParams(0,-2,1));info.setOnClickListener(v->openPlaylist(e.getKey()));playlist.addView(iconButton("•••","Options for playlist "+e.getKey(),()->playlistMenu(e.getKey(),songs)));body.addView(playlist);
        }
    }
    private List<Track> resolve(List<String> ids) { List<Track> result=new ArrayList<>();for(String id:ids)if(trackIndex.containsKey(id))result.add(trackIndex.get(id));return result; }
    private void newPlaylist(Track add) {
        EditText input=new EditText(this);input.setSingleLine();input.setHint("Playlist name");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("A new collection").setView(input).setPositiveButton("Create",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String name=input.getText().toString().trim();Map<String,List<String>> lists=library.playlists();if(name.isEmpty()||name.length()>80){input.setError("Use 1–80 characters");return;}if(lists.containsKey(name)){input.setError("That name already exists");return;}List<String> ids=new ArrayList<>();if(add!=null)ids.add(add.id);lists.put(name,ids);library.savePlaylists(lists);dialog.dismiss();render();}));dialog.show();
    }
    private void playlistMenu(String name,List<Track> songs) {
        new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Play","Shuffle","View / remove songs","Rename","Delete playlist"},(d,i)-> {
            if(i==0)play(songs,0,false);if(i==1)play(songs,0,true);
            if(i==2)new AlertDialog.Builder(this).setTitle("Tap a song to remove it").setItems(songs.stream().map(t->t.title).toArray(String[]::new),(a,p)->{Map<String,List<String>> lists=library.playlists();lists.get(name).remove(songs.get(p).id);library.savePlaylists(lists);render();}).setNegativeButton("Close",null).show();
            if(i==3){EditText input=new EditText(this);input.setText(name);AlertDialog rename=new AlertDialog.Builder(this).setTitle("Rename playlist").setView(input).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();rename.setOnShowListener(a->rename.getButton(-1).setOnClickListener(v->{String n=input.getText().toString().trim();Map<String,List<String>> lists=library.playlists();if(n.isEmpty()||n.length()>80||(!n.equals(name)&&lists.containsKey(n))){input.setError("Choose a unique name, 1–80 characters");return;}List<String> ids=lists.remove(name);lists.put(n,ids);library.savePlaylists(lists);if(currentPlaylist.equals(name)){currentPlaylist=n;collectionTitle=n;}rename.dismiss();render();}));rename.show();}
            if(i==4)new AlertDialog.Builder(this).setTitle("Delete “"+name+"”?").setMessage("Your audio files will stay on the phone.").setPositiveButton("Delete",(a,p)->{Map<String,List<String>> lists=library.playlists();lists.remove(name);library.savePlaylists(lists);if(currentPlaylist.equals(name)){currentPlaylist="";collectionIds=null;}render();}).setNegativeButton("Keep",null).show();
        }).show();
    }
    private void trackMenu(Track t) {
        List<String> options=new ArrayList<>(Arrays.asList("Play next","Add to queue","Add to playlist","Choose album cover","Reset album cover","Set moods"));
        if(!currentPlaylist.isEmpty()&&library.playlists().getOrDefault(currentPlaylist,new ArrayList<>()).contains(t.id))options.add("Remove from this playlist");
        options.add(favouriteIds.contains(t.id)?"♥ Remove from favourites":"♡ Add to favourites");final int favouriteOption=options.size()-1;
        new AlertDialog.Builder(this).setTitle(t.title).setItems(options.toArray(new String[0]),(d,i)-> {
            if(i==favouriteOption){toggleFavourite(t);if(section.equals("Favourites"))render();return;}
            if(i<=1){if(player==null){toast("Playback is connecting");return;}if(player.getMediaItemCount()==0)play(Collections.singletonList(t),0,false);else {boolean wasShuffled=player.getShuffleModeEnabled();if(i==0&&wasShuffled)player.setShuffleModeEnabled(false);player.addMediaItem(i==0?player.getCurrentMediaItemIndex()+1:player.getMediaItemCount(),t.item(library.artwork(t)));toast(i==0&&wasShuffled?"Added next · shuffle turned off":"Added to queue");}}
            if(i==2){Map<String,List<String>> lists=library.playlists();List<String> names=new ArrayList<>(lists.keySet());names.add("+ New playlist");new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names.toArray(new String[0]),(a,p)->{if(p==names.size()-1)newPlaylist(t);else{List<String> ids=lists.get(names.get(p));if(!ids.contains(t.id))ids.add(t.id);library.savePlaylists(lists);toast("Added to "+names.get(p));}}).show();}
            if(i==3){coverTarget=t;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,20);}
            if(i==6&&!currentPlaylist.isEmpty()){Map<String,List<String>> lists=library.playlists();List<String> ids=lists.get(currentPlaylist);if(ids!=null){ids.remove(t.id);library.savePlaylists(lists);render();}}
            if(i==5)setSongMoods(t);
            if(i==4){library.prefs().edit().remove("cover:"+t.albumKey()).remove("cover:"+MusicRules.albumKey(t.artist,t.album)).remove("cover:album:"+t.albumId).apply();refreshQueueArtwork(t);render();}
        }).show();
    }
    @Override protected void onActivityResult(int r,int result,Intent data) { super.onActivityResult(r,result,data);if(r==20&&result==RESULT_OK&&data!=null&&data.getData()!=null&&coverTarget!=null){Uri uri=data.getData();try {getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);library.prefs().edit().putString("cover:"+coverTarget.albumKey(),uri.toString()).apply();refreshQueueArtwork(coverTarget);render();}catch(SecurityException e){toast("That image couldn't be saved. Try another image provider.");}} }
    private void refreshQueueArtwork(Track target) {if(player==null)return;for(int i=0;i<player.getMediaItemCount();i++){Track t=trackById(player.getMediaItemAt(i).mediaId);if(t!=null&&t.albumKey().equals(target.albumKey()))player.replaceMediaItem(i,t.item(library.artwork(t)));}}
    private ImageView artworkView(View target){return new ImageView(this){private android.graphics.Bitmap previousBitmap;@Override public void setImageBitmap(android.graphics.Bitmap bitmap){if(bitmap==previousBitmap)return;previousBitmap=bitmap;super.setImageBitmap(bitmap);if(bitmap==null||bitmap.isRecycled())return;int[] pixels=new int[256];for(int y=0;y<16;y++)for(int x=0;x<16;x++)pixels[y*16+x]=bitmap.getPixel(Math.min(bitmap.getWidth()-1,x*bitmap.getWidth()/16),Math.min(bitmap.getHeight()-1,y*bitmap.getHeight()/16));GradientDrawable tint=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{ArtworkTheme.color(pixels),SURFACE});tint.setCornerRadius(dp(14));target.setBackground(tint);}};}
    private void libraryShuffle(){
        if(player==null)return;if(player.getShuffleModeEnabled()){player.setShuffleModeEnabled(false);return;}
        Track current=currentTrack();if(current==null){if(!tracks.isEmpty()&&player.getMediaItemCount()==0)play(tracks,0,true);else player.setShuffleModeEnabled(true);return;}
        Set<String> libraryIds=new HashSet<>();for(Track t:tracks)libraryIds.add(t.id);Set<String> queued=new HashSet<>();for(int i=0;i<player.getMediaItemCount();i++)queued.add(player.getMediaItemAt(i).mediaId);
        if(player.getMediaItemCount()==libraryIds.size()&&queued.equals(libraryIds)){player.setShuffleModeEnabled(true);return;}
        // Keep the playing MediaItem in place; queue expansion must not reload its audio source.
        int index=player.getCurrentMediaItemIndex();if(index<0)return;
        if(index+1<player.getMediaItemCount())player.removeMediaItems(index+1,player.getMediaItemCount());if(index>0)player.removeMediaItems(0,index);
        List<MediaItem> additions=new ArrayList<>();for(Track t:tracks)if(!t.id.equals(current.id))additions.add(t.item(library.artwork(t)));if(!additions.isEmpty())player.addMediaItems(additions);player.setShuffleModeEnabled(true);
    }

    private void play(List<Track> list,int index,boolean shuffle) {
        if(list.isEmpty()){toast("Add some songs first");return;}if(!shuffle&&currentPlaylist.isEmpty()&&list!=tracks){Track selected=list.get(index);int allIndex=tracks.indexOf(selected);if(allIndex>=0){list=tracks;index=allIndex;}}if(player==null){toast("Playback is connecting. Try again in a moment.");return;}
        List<MediaItem> items=new ArrayList<>();for(Track t:list)items.add(t.item(library.artwork(t)));player.setMediaItems(items,shuffle?new Random().nextInt(items.size()):index,0);player.setShuffleModeEnabled(shuffle);player.prepare();player.play();updatePlayback();
    }
    private Track trackById(String id){return trackIndex.get(id);}
    private Track currentTrack(){return player==null||player.getCurrentMediaItem()==null?null:trackById(player.getCurrentMediaItem().mediaId);}
    private void toggle(){if(player==null)return;if(player.getMediaItemCount()==0){if(!tracks.isEmpty())play(tracks,0,false);return;}if(player.isPlaying())player.pause();else{if(player.getPlaybackState()==Player.STATE_ENDED)player.seekTo(player.getCurrentMediaItemIndex(),0);player.play();}}
    private void openNow(){if(player==null||player.getMediaItemCount()==0){toast("Choose a song first");return;}full=true;render();}
    /** Native seek interaction/accessibility, with a rounded thumb-free timeline. */
    private final class PlaybackTimeline extends SeekBar {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private boolean dragging;
        PlaybackTimeline(){super(MainActivity.this);setPadding(0,0,0,0);setThumb(new ColorDrawable(Color.TRANSPARENT));setThumbOffset(0);setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));setSplitTrack(false);setKeyProgressIncrement(10);}
        void dragging(boolean value){dragging=value;invalidate();}
        @Override protected synchronized void onDraw(android.graphics.Canvas canvas){
            float h=dp(dragging?10:6),y=(getHeight()-h)/2f,w=getWidth();paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(0x40ffffff);canvas.drawRoundRect(0,y,w,y+h,h/2,h/2,paint);
            float fill=w*getProgress()/Math.max(1f,getMax());if(fill>0){paint.setColor(dragging?INK:0xbfffffff);if(getLayoutDirection()==View.LAYOUT_DIRECTION_RTL)canvas.drawRoundRect(w-fill,y,w,y+h,h/2,h/2,paint);else canvas.drawRoundRect(0,y,fill,y+h,h/2,h/2,paint);}
            if(isFocused()){paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x99ffffff);canvas.drawRoundRect(dp(1),y-dp(3),w-dp(1),y+h+dp(3),dp(5),dp(5),paint);}
        }
    }
    private void playbackTimes(long position,long duration){changed(elapsed,time(position));changed(remaining,duration>0?"−"+time(Math.max(0,duration-position)):"−0:00");}
    private void nowPlaying() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout outer=column();outer.setGravity(Gravity.CENTER);outer.setPadding(dp(20),dp(12),dp(20),dp(20));LinearLayout pane=column();int width=dp(ResponsiveLayout.playerWidth(contentWidthDp(),getResources().getConfiguration().screenHeightDp));outer.addView(pane,new LinearLayout.LayoutParams(width,-2));scroll.addView(outer,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        nowArt=new ImageView(this){@Override public void setImageBitmap(android.graphics.Bitmap bitmap){super.setImageBitmap(bitmap);if(bitmap==null||bitmap.isRecycled()||!full||nowArt!=this)return;int[] samples=new int[256];for(int y=0;y<16;y++)for(int x=0;x<16;x++)samples[y*16+x]=bitmap.getPixel(Math.min(bitmap.getWidth()-1,x*bitmap.getWidth()/16),Math.min(bitmap.getHeight()-1,y*bitmap.getHeight()/16));int shade=artwork.isActual(this)?ArtworkTheme.playerColor(samples):android.graphics.Color.BLACK;root.setBackgroundColor(shade);if(windowFrame!=null)windowFrame.setBackgroundColor(shade);}};nowArt.setScaleType(ImageView.ScaleType.FIT_CENTER);nowArt.setElevation(dp(12));if(Build.VERSION.SDK_INT>=28){nowArt.setOutlineAmbientShadowColor(0x80000000);nowArt.setOutlineSpotShadowColor(0xa0000000);}pane.addView(nowArt,new LinearLayout.LayoutParams(width,width));space(pane,20);
        LinearLayout metadata=row();LinearLayout labels=column();nowTitle=text("",22,INK,true);nowTitle.setMaxLines(2);nowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(nowTitle);nowArtist=text("",15,INK,false);nowArtist.setSingleLine(true);nowArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(nowArtist);metadata.addView(labels,new LinearLayout.LayoutParams(0,-2,1));nowFavourite=button("♡",()->toggleFavourite(currentTrack()));nowFavourite.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20ffffff),null,null));metadata.addView(nowFavourite,new LinearLayout.LayoutParams(dp(48),dp(48)));metadata.addView(iconButton("•••","Player options",this::playerOptions),new LinearLayout.LayoutParams(dp(48),dp(48)));pane.addView(metadata);space(pane,10);
        seek=new PlaybackTimeline();seek.setContentDescription("Playback position");seek.setMax(1000);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){seeking=true;((PlaybackTimeline)bar).dragging(true);}
            public void onStopTrackingTouch(SeekBar bar){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*bar.getProgress()/1000);seeking=false;((PlaybackTimeline)bar).dragging(false);updatePlayback();}
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(!user||player==null)return;long duration=player.getDuration();if(duration>0){long position=duration*progress/1000;playbackTimes(position,duration);if(!seeking)player.seekTo(position);}}
        });pane.addView(seek,new LinearLayout.LayoutParams(-1,dp(TOUCH)));LinearLayout times=row();elapsed=text("",11,INK,false);remaining=text("",11,INK,false);remaining.setGravity(Gravity.END);times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));times.addView(remaining,new LinearLayout.LayoutParams(0,-2,1));pane.addView(times);space(pane,16);
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER);shuffleButton=iconButton("shuffle","Shuffle off",()->{libraryShuffle();updatePlayback();});controls.addView(shuffleButton,new LinearLayout.LayoutParams(0,dp(TOUCH),1));controls.addView(largeTransport("◀◀","Previous song",()->{if(player!=null){if(player.getCurrentPosition()>3000)player.seekTo(0);else player.seekToPreviousMediaItem();}},30),new LinearLayout.LayoutParams(0,dp(TOUCH),1));playButton=largeTransport("▶","Play music",this::toggle,44);controls.addView(playButton,new LinearLayout.LayoutParams(dp(64),dp(64)));controls.addView(largeTransport("▶▶","Next song",()->{if(player!=null)player.seekToNextMediaItem();},30),new LinearLayout.LayoutParams(0,dp(TOUCH),1));repeatButton=iconButton("repeat","Repeat off",()->{if(player!=null)player.setRepeatMode((player.getRepeatMode()+1)%3);updatePlayback();});controls.addView(repeatButton,new LinearLayout.LayoutParams(0,dp(TOUCH),1));pane.addView(controls);space(pane,18);
        Button next=button("Up next",this::queue);next.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20ffffff),background(Color.TRANSPARENT,18),null));pane.addView(next,new LinearLayout.LayoutParams(-1,dp(TOUCH)));updatePlayback();scheduleTick();
    }
    private void libraryOptions(){new AlertDialog.Builder(this).setTitle("Library").setItems(new String[]{"Refresh music","Mood mixes","About AURA"},(d,i)->{if(i==0)scan();if(i==1)chooseMood();if(i==2)about();}).show();}
    private void about(){String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException ignored){}new AlertDialog.Builder(this).setTitle("AURA "+version).setMessage("Your music, on your phone. Offline playback with local playlists and mood mixes you choose.").setPositiveButton("Close",null).show();}
    private void playbackSettings(){
        if(player==null){toast("Playback is connecting");return;}
        com.google.common.util.concurrent.ListenableFuture<SessionResult> request=player.sendCustomCommand(PlaybackService.GET_PLAYBACK_OPTIONS,Bundle.EMPTY);
        request.addListener(()->{try{SessionResult result=request.get();if(isFinishing()||result.resultCode!=SessionResult.RESULT_SUCCESS)return;
            LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));TextView label=text("",20,INK,true);form.addView(label);SeekBar fade=new SeekBar(this);fade.setMax(12);fade.setKeyProgressIncrement(1);fade.setContentDescription("Crossfade seconds, zero turns crossfade off");fade.setProgress(result.extras.getInt("crossfadeSeconds",0));label.setText(fade.getProgress()==0?"Crossfade off":fade.getProgress()+" second crossfade");fade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(value==0?"Crossfade off":value+" second crossfade");}});form.addView(fade,new LinearLayout.LayoutParams(-1,dp(48)));Switch autoplay=new Switch(this);autoplay.setText("Autoplay related local songs");autoplay.setTextColor(INK);autoplay.setChecked(result.extras.getBoolean("autoplayEnabled",true));form.addView(autoplay,new LinearLayout.LayoutParams(-1,dp(56)));form.addView(text("Continue after your queue ends, avoiding recent repeats.",12,MUTED,false));
            new AlertDialog.Builder(this).setTitle("Playback settings").setView(form).setPositiveButton("Save",(d,i)->{Bundle args=new Bundle();args.putInt("crossfadeSeconds",fade.getProgress());args.putBoolean("autoplayEnabled",autoplay.isChecked());com.google.common.util.concurrent.ListenableFuture<SessionResult> save=player.sendCustomCommand(PlaybackService.SET_PLAYBACK_OPTIONS,args);save.addListener(()->{try{toast(save.get().resultCode==SessionResult.RESULT_SUCCESS?"Playback settings saved":"Settings could not be saved");}catch(Exception e){toast("Settings could not be saved");}},command->handler.post(command));}).setNegativeButton("Cancel",null).show();
        }catch(Exception e){toast("Playback settings are unavailable");}},command->handler.post(command));
    }
    private void dolbySettings(){Intent intent=DolbySettings.find(this);if(intent!=null){try{startActivity(intent);return;}catch(RuntimeException ignored){}}new AlertDialog.Builder(this).setTitle("Dolby Atmos").setMessage("No accessible Dolby Atmos control was found. Some phones include it inside Sound settings; AURA cannot confirm that through Android's public API.").setPositiveButton("Sound settings",(d,w)->{try{startActivity(SpatialAudioSupport.settingsIntent(this));}catch(RuntimeException ignored){toast("Sound settings unavailable");}}).setNegativeButton("Close",null).show();}
    private void playerOptions(){new AlertDialog.Builder(this).setTitle("Player").setItems(new String[]{"Song options","Mood mix","Spatial audio","Sleep timer","Playback settings","Dolby Atmos"},(d,i)->{if(i==0){Track t=currentTrack();if(t!=null)trackMenu(t);}if(i==1)chooseMood();if(i==2)spatialAudio();if(i==3)sleepTimer();if(i==4)playbackSettings();if(i==5)dolbySettings();}).show();}
    private void queue() {
        if(player==null||player.getMediaItemCount()==0)return;
        int count=player.getMediaItemCount();int[] next=new int[count];Timeline timeline=player.getCurrentTimeline();
        for(int i=0;i<count;i++)next[i]=timeline.getWindowCount()==count?timeline.getNextWindowIndex(i,player.getRepeatMode()==Player.REPEAT_MODE_ALL?Player.REPEAT_MODE_ALL:Player.REPEAT_MODE_OFF,player.getShuffleModeEnabled()):(i+1<count?i+1:C.INDEX_UNSET);
        List<Integer> order=MusicRules.queueOrder(player.getCurrentMediaItemIndex(),next);List<String> labels=new ArrayList<>();List<String> ids=new ArrayList<>();
        for(int i:order){MediaItem item=player.getMediaItemAt(i);labels.add((i==player.getCurrentMediaItemIndex()?"Playing · ":"")+item.mediaMetadata.title);ids.add(item.mediaId);}
        BaseAdapter queueAdapter=new BaseAdapter(){
            public int getCount(){return ids.size();}public Object getItem(int position){return ids.get(position);}public long getItemId(int position){return position;}
            public View getView(int position,View reused,android.view.ViewGroup parent){
                LinearLayout row;ImageView cover;TextView title,artist;
                if(reused==null){row=row();row.setPadding(dp(20),dp(8),dp(20),dp(8));cover=new ImageView(MainActivity.this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(cover,new LinearLayout.LayoutParams(dp(ROW_COVER),dp(ROW_COVER)));LinearLayout labels=column();labels.setPadding(dp(12),0,0,0);title=text("",14,INK,true);title.setMaxLines(2);artist=text("",12,MUTED,false);artist.setSingleLine(true);labels.addView(title);labels.addView(artist);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.setTag(new Object[]{cover,title,artist});}else row=(LinearLayout)reused;
                Object[] views=(Object[])row.getTag();cover=(ImageView)views[0];title=(TextView)views[1];artist=(TextView)views[2];title.setText(labels.get(position));Track track=trackById(ids.get(position));artist.setText(track==null?"On your phone":track.artist);if(track!=null)artwork.load(cover,track);else{cover.setTag(null);cover.setImageDrawable(new GlyphDrawable("library",MUTED,dp(24)));cover.setBackground(background(TINT,COVER_RADIUS));}return row;
            }
        };
        Dialog sheet=new Dialog(this);LinearLayout panel=column();panel.setPadding(0,dp(12),0,dp(12));panel.setBackground(background(SURFACE,24));
        LinearLayout heading=row();heading.setPadding(dp(20),0,dp(12),dp(10));TextView queueTitle=text(player.getShuffleModeEnabled()?"Up next · shuffled":"Up next",22,INK,true);heading.addView(queueTitle,new LinearLayout.LayoutParams(0,-2,1));heading.addView(iconButton("⌄","Close queue",sheet::dismiss),new LinearLayout.LayoutParams(dp(TOUCH),dp(TOUCH)));panel.addView(heading);
        ListView queueList=new ListView(this);queueList.setDivider(null);queueList.setAdapter(queueAdapter);panel.addView(queueList,new LinearLayout.LayoutParams(-1,0,1));
        queueList.setOnItemClickListener((parent,row,i,id)->{sheet.dismiss();
            // Resolve the ID again: the notification or playback may have changed the queue while this dialog was open.
            int original=order.get(i);if(original<player.getMediaItemCount()&&player.getMediaItemAt(original).mediaId.equals(ids.get(i))){player.seekTo(original,0);player.play();return;}
            for(int n=0;n<player.getMediaItemCount();n++)if(player.getMediaItemAt(n).mediaId.equals(ids.get(i))){player.seekTo(n,0);player.play();break;}
        });
        Button edit=button("Edit queue",()->{sheet.dismiss();editQueue();});LinearLayout.LayoutParams editParams=new LinearLayout.LayoutParams(-1,dp(TOUCH));editParams.setMargins(dp(20),dp(8),dp(20),dp(8));panel.addView(edit,editParams);
        sheet.setContentView(panel);Window window=sheet.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.setNavigationBarColor(BG);}sheet.show();if(window!=null)window.setLayout(-1,Math.min(dp(520),getResources().getDisplayMetrics().heightPixels*3/4));
    }
    private void editQueue(){if(player==null)return;List<String> labels=new ArrayList<>();for(int i=0;i<player.getMediaItemCount();i++)labels.add(String.valueOf(player.getMediaItemAt(i).mediaMetadata.title));new AlertDialog.Builder(this).setTitle("Choose a queued song").setItems(labels.toArray(new String[0]),(d,i)->new AlertDialog.Builder(this).setTitle(labels.get(i)).setItems(new String[]{"Move earlier","Move later","Remove"},(a,p)->{if(player==null||i>=player.getMediaItemCount())return;if(p==0&&i>0)player.moveMediaItem(i,i-1);if(p==1&&i<player.getMediaItemCount()-1)player.moveMediaItem(i,i+1);if(p==2)player.removeMediaItem(i);if(player.getMediaItemCount()==0){full=false;render();}else editQueue();}).show()).setNegativeButton("Close",null).show();}
    private String time(long ms){return MusicRules.time(ms);}
    
    private void changed(TextView view,String value){if(view!=null&&!value.contentEquals(view.getText()))view.setText(value);}
    private void description(View view,String value){if(view!=null&&!value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))view.setContentDescription(value);}
    private void cover(ImageView view,Track track){if(view!=null&&track!=null)artwork.load(view,track);}
    private void warmUpcomingCover(){
        if(player==null)return;List<Track> neighbours=new ArrayList<>();for(int index:new int[]{player.getNextMediaItemIndex(),player.getPreviousMediaItemIndex()}){if(index==C.INDEX_UNSET||index<0||index>=player.getMediaItemCount())continue;Track track=trackById(player.getMediaItemAt(index).mediaId);if(track!=null&&!neighbours.contains(track))neighbours.add(track);}
        int pixels=dp(Math.max(128,Math.min(420,contentWidthDp()-64)));StringBuilder identity=new StringBuilder().append(pixels);for(Track track:neighbours)identity.append('|').append(track.id).append(':').append(library.artwork(track));String key=identity.toString();if(key.equals(upcomingCoverKey))return;upcomingCoverKey=key;
        artwork.prefetch(neighbours,dp(44));artwork.prefetch(neighbours,pixels);
    }
    private String colouredSong="";
    private void markSongTitle(TextView view,Track track){view.setTag("song:"+track.id);MediaItem item=player==null?null:player.getCurrentMediaItem();view.setTextColor(item!=null&&item.mediaId.equals(track.id)?HOME_ACCENT:INK);}
    private void colourSongTitles(View view,String id){if(view instanceof TextView&&view.getTag() instanceof String&&((String)view.getTag()).startsWith("song:"))((TextView)view).setTextColor(view.getTag().equals("song:"+id)?HOME_ACCENT:INK);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)colourSongTitles(((ViewGroup)view).getChildAt(i),id);}
    private void updatePlayback(){if(player==null)return;recordHistory();Track track=currentTrack();MediaItem item=player.getCurrentMediaItem();String title=item==null?"Choose something to play":String.valueOf(item.mediaMetadata.title);String artist=item==null?"Your library, your soundtrack":String.valueOf(item.mediaMetadata.artist);String icon=player.isPlaying()?"Ⅱ":"▶";
        if(mini!=null)mini.setVisibility(item==null?View.GONE:View.VISIBLE);changed(miniTitle,title);changed(miniArtist,artist);if(miniPlay!=null)setGlyph(miniPlay,icon);if(mini!=null)cover((ImageView)mini.getChildAt(0),track);
        long homeDuration=player.getDuration();int homePosition=homeDuration>0?(int)Math.min(1000,player.getCurrentPosition()*1000/homeDuration):0;if(miniProgress!=null&&miniProgress.getProgress()!=homePosition)miniProgress.setProgress(homePosition);if(homeProgress!=null){Track shown=track==null?homeSeed:track;if(shown!=null){changed(homeTitle,shown.title);changed(homeArtist,shown.artist);cover(homeArt,shown);}if(homeProgress.getProgress()!=homePosition)homeProgress.setProgress(homePosition);changed(homeRemaining,homeDuration>0?time(Math.max(0,homeDuration-player.getCurrentPosition()))+" left":"");setGlyph(homePlay,icon);description(homePlay,player.isPlaying()?"Pause playback":"Resume playing");}
        if(full&&nowTitle!=null&&playButton!=null&&shuffleButton!=null&&repeatButton!=null&&seek!=null){changed(nowTitle,title);changed(nowArtist,artist);setGlyph(playButton,icon);boolean shuffle=player.getShuffleModeEnabled();((IconControl)shuffleButton).active(shuffle);description(shuffleButton,shuffle?"Shuffle on":"Shuffle off");int repeat=player.getRepeatMode();setGlyph(repeatButton,repeat==Player.REPEAT_MODE_ONE?"repeat_one":"repeat");((IconControl)repeatButton).active(repeat!=Player.REPEAT_MODE_OFF);description(repeatButton,repeat==Player.REPEAT_MODE_ONE?"Repeat one":repeat==Player.REPEAT_MODE_ALL?"Repeat all":"Repeat off");long duration=player.getDuration();if(!seeking&&duration>0){int progress=(int)(player.getCurrentPosition()*1000/duration);if(progress!=seek.getProgress())seek.setProgress(progress);}if(!seeking)playbackTimes(player.getCurrentPosition(),duration);cover(nowArt,track);}
        if(nowFavourite!=null)favouriteLabel(nowFavourite,track);
        if(miniTitle!=null)miniTitle.setTextColor(HOME_ACCENT);if(homeTitle!=null)homeTitle.setTextColor(HOME_ACCENT);if(nowTitle!=null)nowTitle.setTextColor(HOME_ACCENT);String selected=item==null?"":item.mediaId;if(!selected.equals(colouredSong)){colouredSong=selected;if(root!=null)colourSongTitles(root,selected);}
        String action=player.isPlaying()?"Pause playback":"Play music";description(miniPlay,action);description(playButton,action);warmUpcomingCover();
    }
    @Override public void onBackPressed(){if(full){full=false;render();if(libraryDirty)handler.postDelayed(refreshDirty,600);}else if(collectionIds!=null){collectionIds=null;currentPlaylist="";render();}else if(!section.isEmpty()){section="";render();}else if(!tab.equals("Home")){tab="Home";render();}else super.onBackPressed();}
}
