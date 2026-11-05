package dev.lilt.player.screens;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.PlayerHub;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;
import dev.lilt.player.screens.player.FormatLabel;
import dev.lilt.player.screens.player.NowPlayingStage;
import dev.lilt.player.screens.player.OutputSwitcher;
import dev.lilt.player.screens.player.PlayerBackdrop;
import dev.lilt.player.screens.player.PlayerColors;
import dev.lilt.player.screens.player.ScrubMath;
import dev.lilt.player.screens.player.Scrubber;
import dev.lilt.player.screens.player.SessionLink;
import dev.lilt.player.ui.ArtworkPalette;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassButton;
import dev.lilt.player.ui.GlassDrawable;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.NowPlayingLayout;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * Now Playing: full-bleed artwork-colour background, large square artwork that relaxes when
 * paused, title / artist (tappable) with favourite and more, a slim scrubber with a truthful
 * format line, transport, and the bottom glass capsule (Lyrics / Output / Queue). There is no
 * volume slider: hardware keys own volume. The layout adapts to every window height
 * ({@link NowPlayingLayout}); dragging down dismisses.
 */
public final class NowPlayingScreen extends Screen {
    private static final int ON_PRIMARY = ColorRoles.ON_ART_PRIMARY;

    private Context context;
    private float density;
    private FrameLayout root;
    private NowPlayingStage stage;
    private PlayerBackdrop backdrop;
    private ImageView art, prev, play, next;
    private TextView title, artist;
    private GlassButton heart, more;
    private Scrubber scrubber;
    private GlassView capsule;
    private ArtworkPalette palette = ArtworkPalette.NEUTRAL;

    private Track shown;
    private String shownId = "";
    private boolean visible, playShown = true, pausedArt, firstRefresh = true;
    private float artScale = 1f;
    private ValueAnimator artAnim;
    private SessionLink link;
    private final SessionLink.Listener linkListener = this::updateFormat;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            updateProgress();
            PlayerHub hub = host.player();
            if (hub.playing()) host.main().postDelayed(this, Math.max(50, ScrubMath.refreshIntervalMs(duration(hub), Math.max(1, scrubber.getWidth()), 1f)));
        }
    };

    public NowPlayingScreen() { }

    @Override public String key() { return "nowplaying"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override public boolean fullScreen() { return true; }
    @Override public boolean lightSystemBars() { return true; }

    @SuppressLint("ClickableViewAccessibility")
    @Override protected View onCreateView(Context c) {
        context = c;
        density = c.getResources().getDisplayMetrics().density;
        ColorRoles roles = host.colors();

        backdrop = new PlayerBackdrop(c);

        View handle = new dev.lilt.player.screens.player.HandleView(c);

        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        final float radius = Ui.dpf(c, DesignTokens.artworkRadius(300));
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        art.setClipToOutline(true);
        art.setElevation(Ui.dpf(c, DesignTokens.ELEVATION_ARTWORK));
        if (android.os.Build.VERSION.SDK_INT >= 28) { art.setOutlineAmbientShadowColor(0x66000000); art.setOutlineSpotShadowColor(0x99000000); }
        art.setContentDescription("Album artwork");

        title = Type.text(c, TypeScale.TITLE3, ON_PRIMARY);
        Type.singleLine(title);
        artist = Type.text(c, TypeScale.TITLE3.weight(400), ColorRoles.ON_ART_SECONDARY);
        Type.singleLine(artist);
        artist.setBackground(Ui.pressable(roles, 0, Ui.dpf(c, 6)));
        artist.setOnClickListener(v -> openArtist());
        LinearLayout titleBlock = Ui.column(c);
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        titleBlock.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.topMargin = Ui.dp(c, 2);
        titleBlock.addView(artist, ap);

        heart = glassButton(R.drawable.ic_heart, "Favourite");
        heart.setOnClickListener(v -> toggleFavourite());
        more = glassButton(R.drawable.ic_more, "More");
        more.setOnClickListener(v -> { if (shown != null) host.showTrackMenu(shown, v); });

        scrubber = new Scrubber(c);
        scrubber.setListener(new Scrubber.Listener() {
            @Override public void onSeek(long positionMs) { host.player().seek(positionMs); if (host.player().playing()) scheduleTick(); }
            @Override public void onScrubbing(boolean scrubbing) { if (!scrubbing) scheduleTick(); }
        });

        prev = transport(R.drawable.ic_previous, "Previous", 12);
        prev.setOnClickListener(v -> { Haptics.click(v); host.player().previous(); });
        play = transport(R.drawable.ic_pause, "Pause", 10);
        play.setOnClickListener(v -> { Haptics.click(v); host.player().toggle(); });
        next = transport(R.drawable.ic_next, "Next", 12);
        next.setOnClickListener(v -> { Haptics.click(v); host.player().next(); });

        capsule = new GlassView(c, roles);
        capsule.setOnArtwork(true);
        capsule.setCornerRadius(GlassDrawable.CAPSULE);
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER);
        row.addView(capsuleButton(R.drawable.ic_lyrics, "Lyrics", () -> host.openLyrics()), segment(false));
        row.addView(capsuleButton(R.drawable.ic_output, "Audio output", () -> OutputSwitcher.open(host.activity())), segment(true));
        row.addView(capsuleButton(R.drawable.ic_queue, "Queue", () -> host.openQueue()), segment(true));
        capsule.addView(row, new FrameLayout.LayoutParams(-1, -1));

        stage = new NowPlayingStage(c, backdrop, handle, art, titleBlock, heart, more, scrubber, prev, play, next, capsule);
        heart.setBackdrop(backdrop);
        more.setBackdrop(backdrop);
        capsule.setBackdrop(backdrop);
        stage.setListener(new NowPlayingStage.Listener() {
            @Override public void onDismissProgress(float progress) { }
            @Override public void onDismissed() { host.nav().dismiss(); }
        });

        root = new FrameLayout(c);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        link = SessionLink.get(c);
        return root;
    }

    private GlassButton glassButton(int icon, String description) {
        GlassButton b = new GlassButton(context, host.colors(), icon, description, dev.lilt.player.screens.player.NowPlayingGeometry.GLASS_BUTTON);
        b.setOnArtwork(true);
        b.setIconColor(ON_PRIMARY);
        return b;
    }

    @SuppressLint("ClickableViewAccessibility")
    private ImageView transport(int icon, String description, int paddingDp) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(context, paddingDp);
        v.setPadding(p, p, p, p);
        v.setImageDrawable(Icons.get(context, icon, ON_PRIMARY));
        v.setContentDescription(description);
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnTouchListener((view, e) -> {
            if (!Motion.enabled()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: view.animate().scaleX(0.86f).scaleY(0.86f).setDuration(Motion.FAST).setInterpolator(Motion.standard()).start(); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: view.animate().scaleX(1f).scaleY(1f).setDuration(Motion.SPRING_MS).setInterpolator(Motion.spring()).start(); break;
                default: break;
            }
            return false;
        });
        return v;
    }

    private ImageView capsuleButton(int icon, String description, Runnable action) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = Ui.dp(context, 14);
        v.setPadding(p, Ui.dp(context, 8), p, Ui.dp(context, 8));
        v.setImageDrawable(Icons.get(context, icon, ON_PRIMARY));
        v.setContentDescription(description);
        v.setBackground(Ui.pressable(host.colors(), 0, -1));
        v.setOnClickListener(x -> { Haptics.tick(x); action.run(); });
        return v;
    }

    private LinearLayout.LayoutParams segment(boolean gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(context, 52), Ui.dp(context, 40));
        if (gap) p.setMarginStart(Ui.dp(context, 10));
        return p;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override public void onInsets(int top, int bottom) {
        if (stage != null) stage.setInsets(top, bottom);
    }

    @Override public void onShow() {
        visible = true;
        stage.reset();
        link.acquire();
        link.addListener(linkListener);
        refresh();
        scheduleTick();
    }

    @Override public void onHide() {
        visible = false;
        host.main().removeCallbacks(tick);
        link.removeListener(linkListener);
        link.release();
        backdrop.setAnimating(false);
        if (artAnim != null) { artAnim.cancel(); artAnim = null; }
    }

    @Override public void onDestroy() {
        visible = false;
        if (host != null) host.main().removeCallbacks(tick);
        if (artAnim != null) artAnim.cancel();
        if (art != null) art.setImageDrawable(null);
        shown = null;
    }

    @Override public void onPlaybackChanged() {
        if (!visible || stage == null) return;
        refresh();
        scheduleTick();
    }

    @Override public void onLibraryChanged() { if (visible && shown != null) updateHeart(); }

    @Override public void onThemeChanged() { }

    // ---------------------------------------------------------------- state

    private static long duration(PlayerHub hub) {
        long d = hub.duration();
        if (d > 0) return d;
        Track t = hub.current();
        return t == null ? 0 : Math.max(0, t.duration);
    }

    private void scheduleTick() {
        host.main().removeCallbacks(tick);
        if (visible) host.main().post(tick);
    }

    private void updateProgress() {
        PlayerHub hub = host.player();
        if (!scrubber.isScrubbing()) scrubber.setProgress(hub.position(), duration(hub));
    }

    private void refresh() {
        PlayerHub hub = host.player();
        Track t = hub.current();
        boolean playing = hub.playing();
        if (t == null) {
            shown = null;
            shownId = "";
            title.setText("Not playing");
            artist.setText("");
            artist.setClickable(false);
            art.setImageDrawable(null);
            scrubber.setProgress(0, 0);
            scrubber.setFormat("");
            applyPalette(ArtworkPalette.NEUTRAL, !firstRefresh);
        } else if (!t.id.equals(shownId)) {
            shown = t;
            shownId = t.id;
            title.setText(t.title);
            artist.setText(t.artist);
            artist.setClickable(true);
            host.artwork().load(art, t);
            final String id = t.id;
            PlayerColors.load(host, t, p -> { if (id.equals(shownId)) applyPalette(p, !firstRefresh); });
        } else {
            shown = t;
        }
        updateHeart();
        updatePlayIcon(playing);
        if (playing == pausedArt || firstRefresh) setArtPaused(!playing, !firstRefresh);
        backdrop.setAnimating(visible && playing);
        updateProgress();
        updateFormat();
        firstRefresh = false;
    }

    private void applyPalette(ArtworkPalette p, boolean animate) {
        palette = p;
        backdrop.setPalette(p, animate);
        scrubber.setColors(p.controlFill, p.controlTrack, p.textSecondary, p.textPrimary);
        artist.setTextColor(p.textSecondary);
    }

    private void updateHeart() {
        boolean fav = shown != null && host.library().isFavourite(shown.id);
        heart.setIcon(fav ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        heart.setContentDescription(fav ? "Remove from favourites" : "Add to favourites");
        heart.setEnabled(shown != null);
        more.setEnabled(shown != null);
    }

    private void toggleFavourite() {
        if (shown == null) return;
        boolean fav = host.library().isFavourite(shown.id);
        Haptics.confirm(heart);
        host.library().setFavourite(shown, !fav);
        updateHeart();
    }

    private void updatePlayIcon(boolean playing) {
        if (playing == playShown && play.getDrawable() != null && !firstRefresh) return;
        playShown = playing;
        play.setImageDrawable(Icons.get(context, playing ? R.drawable.ic_pause : R.drawable.ic_play, ON_PRIMARY));
        play.setContentDescription(playing ? "Pause" : "Play");
        // The triangle's weight sits left of its box; nudge it so it looks centred.
        play.setTranslationX(playing ? 0 : Ui.dpf(context, 1.5f));
    }

    private void setArtPaused(boolean paused, boolean animate) {
        pausedArt = paused;
        float target = paused ? NowPlayingLayout.PAUSED_ART_SCALE : 1f;
        if (artAnim != null) artAnim.cancel();
        if (!animate) { setArtScale(target); return; }
        artAnim = Motion.animate(artScale, target, 300, Motion.spring(), this::setArtScale);
    }

    private void setArtScale(float s) {
        artScale = s;
        art.setScaleX(s);
        art.setScaleY(s);
    }

    private void updateFormat() {
        if (scrubber == null) return;
        PlayerHub hub = host.player();
        String text = "";
        if (hub.waitingForNetwork()) text = "Waiting for network";
        else if (shown != null) text = shown.isRemote() ? FormatLabel.AUDIUS : link.formatLabel();
        scrubber.setFormat(text);
    }

    private void openArtist() {
        final Track t = shown;
        if (t == null) return;
        host.nav().dismiss();
        if (t.isRemote() && !t.artistId.isEmpty()) host.openCatalogArtist(t.artistId, t.artist);
        else host.openArtist(t.artist);
    }
}
