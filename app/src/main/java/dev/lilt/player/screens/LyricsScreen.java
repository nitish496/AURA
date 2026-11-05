package dev.lilt.player.screens;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.PlayerHub;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;
import dev.lilt.player.screens.player.LyricScroll;
import dev.lilt.player.screens.player.PlayerBackdrop;
import dev.lilt.player.screens.player.PlayerCapsule;
import dev.lilt.player.screens.player.PlayerColors;
import dev.lilt.player.stream.Connectivity;
import dev.lilt.player.stream.Lyrics;
import dev.lilt.player.stream.LyricsClient;
import dev.lilt.player.ui.ArtworkPalette;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassButton;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lyrics in the style of Apple Music: large bold lines, the active line bright and the rest
 * dimmed (lightly blurred on Android 12+), kept at about a third of the height while the song
 * plays. Tap a line to jump there; scroll freely and the view follows the song again after a
 * few seconds. Plain (unsynced) lyrics just scroll. Lyrics are fetched only while this screen
 * is open (a lookup sends the song's title, artist and length to LRCLIB; lyrics embedded in a
 * file never leave the phone).
 */
public final class LyricsScreen extends Screen {
    private static final int ON_PRIMARY = ColorRoles.ON_ART_PRIMARY;
    /** Highlight a line this long before its timestamp so the eye leads the voice. */
    private static final long LEAD_MS = 120;

    private Context context;
    private float density;
    private FrameLayout root;
    private PlayerBackdrop backdrop;
    private LinearLayout header, bottom;
    private ImageView cover;
    private TextView title, artist;
    private GlassButton heart, more;
    private ScrollView scroll;
    private LinearLayout content;
    private ImageView prev, play, next;
    private View progressFill;
    private GlassView capsule;
    /** Wide-and-short window (landscape phone): the capsule sits beside the transport instead of below it. */
    private boolean compact;

    private Track shown;
    private String shownId = "";
    private Lyrics lyrics;
    private long[] times = new long[0];
    private final List<TextView> lines = new ArrayList<>();
    private float[] shownAlpha = new float[0];
    private byte[] shownBlur = new byte[0];
    private int active = -1;
    private boolean visible, playShown = true, touching, manualShown, firstRefresh = true;
    private long lastManual;
    private int generation, insetTop, insetBottom;
    private ValueAnimator scrollAnim;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            update();
            if (host.player().playing()) host.main().postDelayed(this, 200);
        }
    };

    public LyricsScreen() { }

    @Override public String key() { return "lyrics"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override public boolean fullScreen() { return true; }
    @Override public boolean lightSystemBars() { return true; }

    @SuppressLint("ClickableViewAccessibility")
    @Override protected View onCreateView(Context c) {
        context = c;
        density = c.getResources().getDisplayMetrics().density;
        ColorRoles roles = host.colors();
        LyricsClient.get(c); // enables the disk cache and embedded lyrics

        root = new FrameLayout(c);
        backdrop = new PlayerBackdrop(c);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.dp(c, 40));
        scroll.setClipToPadding(false);
        content = Ui.column(c);
        content.setPadding(Ui.dp(c, 24), 0, Ui.dp(c, 24), 0);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        scroll.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touching = true;
                    if (scrollAnim != null) scrollAnim.cancel();
                    lastManual = SystemClock.uptimeMillis();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    touching = false;
                    lastManual = SystemClock.uptimeMillis();
                    break;
                default: break;
            }
            return false;
        });
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> { if (b - t != ob - ot) applyPadding(); });
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        header = Ui.row(c);
        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.roundOutline(cover, Ui.dpf(c, 6));
        header.addView(cover, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        LinearLayout text = Ui.column(c);
        text.setGravity(Gravity.CENTER_VERTICAL);
        title = Type.text(c, TypeScale.HEADLINE, ON_PRIMARY);
        Type.singleLine(title);
        artist = Type.text(c, TypeScale.SUBHEAD, ColorRoles.ON_ART_SECONDARY);
        Type.singleLine(artist);
        text.addView(title, new LinearLayout.LayoutParams(-1, -2));
        text.addView(artist, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMarginStart(Ui.dp(c, 12));
        tp.setMarginEnd(Ui.dp(c, 4));
        header.addView(text, tp);
        heart = new GlassButton(c, roles, R.drawable.ic_heart, "Favourite", 36);
        heart.setOnArtwork(true);
        heart.setIconColor(ON_PRIMARY);
        heart.setBackdrop(backdrop);
        heart.setOnClickListener(v -> toggleFavourite());
        more = new GlassButton(c, roles, R.drawable.ic_more, "More", 36);
        more.setOnArtwork(true);
        more.setIconColor(ON_PRIMARY);
        more.setBackdrop(backdrop);
        more.setOnClickListener(v -> { if (shown != null) host.showTrackMenu(shown, v); });
        header.addView(heart, new LinearLayout.LayoutParams(heart.size(), heart.size()));
        header.addView(more, new LinearLayout.LayoutParams(more.size(), more.size()));
        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottom = Ui.column(c);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout progress = new FrameLayout(c);
        View track = new View(c);
        track.setBackground(Ui.roundRect(ColorRoles.ON_ART_TRACK, -1));
        progressFill = new View(c);
        progressFill.setBackground(Ui.roundRect(0xE0F5F5F7, -1));
        progressFill.setPivotX(0f);
        progressFill.setScaleX(0f);
        progress.addView(track, new FrameLayout.LayoutParams(-1, -1));
        progress.addView(progressFill, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
        pp.setMargins(Ui.dp(c, 24), 0, Ui.dp(c, 24), Ui.dp(c, 4));
        bottom.addView(progress, pp);
        LinearLayout transport = Ui.row(c);
        transport.setGravity(Gravity.CENTER);
        prev = transportButton(R.drawable.ic_previous, "Previous", 10);
        prev.setOnClickListener(v -> { Haptics.click(v); host.player().previous(); });
        play = transportButton(R.drawable.ic_pause, "Pause", 8);
        play.setOnClickListener(v -> { Haptics.click(v); host.player().toggle(); });
        next = transportButton(R.drawable.ic_next, "Next", 10);
        next.setOnClickListener(v -> { Haptics.click(v); host.player().next(); });
        transport.addView(prev, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 52)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 52));
        pl.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        transport.addView(play, pl);
        transport.addView(next, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 52)));
        bottom.addView(transport, new LinearLayout.LayoutParams(-2, -2));
        capsule = PlayerCapsule.build(c, host, roles, backdrop, PlayerCapsule.LYRICS);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(c, PlayerCapsule.WIDTH_DP), Ui.dp(c, PlayerCapsule.HEIGHT_DP));
        cp.topMargin = Ui.dp(c, 8);
        compact = c.getResources().getConfiguration().screenHeightDp < 480 && c.getResources().getConfiguration().screenWidthDp >= 600;
        if (compact) {
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(cp.width, cp.height, Gravity.BOTTOM | Gravity.END);
            fp.setMarginEnd(Ui.dp(c, 24));
            root.addView(capsule, fp);
        } else bottom.addView(capsule, cp);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        applyInsets();
        return root;
    }

    private ImageView transportButton(int icon, String description, int paddingDp) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(context, paddingDp);
        v.setPadding(p, p, p, p);
        v.setImageDrawable(Icons.get(context, icon, ON_PRIMARY));
        v.setContentDescription(description);
        v.setBackground(Ui.pressable(host.colors(), 0, -1));
        return v;
    }

    // ---------------------------------------------------------------- insets / padding

    @Override public void onInsets(int top, int bottomPx) {
        insetTop = top;
        insetBottom = bottomPx;
        if (root != null) applyInsets();
    }

    private void applyInsets() {
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        hp.topMargin = insetTop + Ui.dp(context, 12);
        hp.setMarginStart(Ui.dp(context, 24));
        hp.setMarginEnd(Ui.dp(context, 24) - (heart.size() - Ui.dp(context, 36)) / 2);
        header.setLayoutParams(hp);
        if (compact) {
            FrameLayout.LayoutParams kp = (FrameLayout.LayoutParams) capsule.getLayoutParams();
            kp.bottomMargin = insetBottom + Ui.dp(context, 12);
            capsule.setLayoutParams(kp);
        }
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) bottom.getLayoutParams();
        bp.bottomMargin = insetBottom + Ui.dp(context, 12);
        bottom.setLayoutParams(bp);
        FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) scroll.getLayoutParams();
        sp.topMargin = insetTop + Ui.dp(context, 12 + 48 + 8);
        // progress 7 + transport 52 + capsule 8 + 52 + bottom margin 12 + breathing room
        sp.bottomMargin = insetBottom + Ui.dp(context, compact ? 7 + 52 + 12 + 8 : 7 + 52 + 8 + 52 + 12 + 8);
        scroll.setLayoutParams(sp);
    }

    /** Padding that lets the first line start at the anchor and the last one reach it. */
    private void applyPadding() {
        int h = scroll.getHeight();
        if (h <= 0) return;
        boolean syncedList = !lines.isEmpty();
        int top = syncedList ? Math.round(h * LyricScroll.ANCHOR) : Ui.dp(context, 8);
        int bottomPad = syncedList ? Math.round(h * 0.65f) : Ui.dp(context, 24);
        if (content.getPaddingTop() != top || content.getPaddingBottom() != bottomPad) {
            content.setPadding(content.getPaddingLeft(), top, content.getPaddingRight(), bottomPad);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override public void onShow() {
        visible = true;
        refresh();
        host.main().post(tick);
    }

    @Override public void onHide() {
        visible = false;
        host.main().removeCallbacks(tick);
        if (scrollAnim != null) { scrollAnim.cancel(); scrollAnim = null; }
        backdrop.setAnimating(false);
        generation++; // drop a pending lookup result
    }

    @Override public void onDestroy() {
        visible = false;
        if (host != null) host.main().removeCallbacks(tick);
        if (scrollAnim != null) scrollAnim.cancel();
        lines.clear();
        lyrics = null;
        shown = null;
        if (cover != null) cover.setImageDrawable(null);
    }

    @Override public void onPlaybackChanged() {
        if (!visible || root == null) return;
        refresh();
        host.main().removeCallbacks(tick);
        host.main().post(tick);
    }

    @Override public void onLibraryChanged() { if (visible && shown != null) updateHeart(); }

    // ---------------------------------------------------------------- state

    private void refresh() {
        PlayerHub hub = host.player();
        Track t = hub.current();
        if (t == null) {
            if (!shownId.isEmpty() || firstRefresh) {
                shown = null;
                shownId = "";
                title.setText("Not playing");
                artist.setText("");
                cover.setImageDrawable(null);
                showMessage("Nothing is playing", "Start a song to see its lyrics.", false);
                applyPalette(ArtworkPalette.NEUTRAL, !firstRefresh);
            }
        } else if (!t.id.equals(shownId)) {
            shown = t;
            shownId = t.id;
            title.setText(t.title);
            artist.setText(t.artist);
            host.artwork().load(cover, t);
            final String id = t.id;
            PlayerColors.load(host, t, p -> { if (id.equals(shownId)) applyPalette(p, !firstRefresh); });
            loadLyrics(t);
        } else {
            shown = t;
        }
        updateHeart();
        updatePlayIcon(hub.playing());
        backdrop.setAnimating(visible && hub.playing());
        firstRefresh = false;
    }

    private void applyPalette(ArtworkPalette p, boolean animate) {
        backdrop.setPalette(p, animate);
        artist.setTextColor(p.textSecondary);
    }

    private void updateHeart() {
        boolean fav = shown != null && host.library().isFavourite(shown.id);
        heart.setIconColor(fav ? host.colors().accent : ON_PRIMARY);
        heart.setIcon(fav ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        heart.setContentDescription(fav ? "Remove from favourites" : "Add to favourites");
        heart.setEnabled(shown != null);
        more.setEnabled(shown != null);
    }

    private void toggleFavourite() {
        if (shown == null) return;
        Haptics.confirm(heart);
        host.library().setFavourite(shown, !host.library().isFavourite(shown.id));
        updateHeart();
    }

    private void updatePlayIcon(boolean playing) {
        if (playing == playShown && play.getDrawable() != null && !firstRefresh) return;
        playShown = playing;
        play.setImageDrawable(Icons.get(context, playing ? R.drawable.ic_pause : R.drawable.ic_play, ON_PRIMARY));
        play.setContentDescription(playing ? "Pause" : "Play");
    }

    // ---------------------------------------------------------------- lyrics loading

    private void loadLyrics(Track t) {
        final int gen = ++generation;
        final String id = t.id;
        Lyrics cached = LyricsClient.get().cached(t);
        if (cached != null) { show(cached); return; }
        showMessage("Loading lyrics", null, false);
        host.io().execute(() -> {
            Lyrics result = null;
            boolean failed = false;
            try { result = LyricsClient.get().fetch(t); }
            catch (IOException | RuntimeException e) { failed = true; }
            final Lyrics found = result;
            final boolean error = failed;
            host.main().post(() -> {
                if (gen != generation || !id.equals(shownId) || root == null) return;
                if (error) showUnavailable(); else show(found);
            });
        });
    }

    private void showUnavailable() {
        boolean online = Connectivity.online(context);
        showMessage(online ? "Couldn't load lyrics" : "You're offline",
                online ? "Lyrics are looked up on LRCLIB. Try again in a moment." : "Lyrics for this song aren't saved on this phone yet.", true);
    }

    private void show(Lyrics l) {
        lyrics = l;
        if (l == null || l.isEmpty()) { showMessage("No lyrics for this song", null, false); return; }
        if (l.instrumental && !l.synced()) { showMessage("Instrumental", null, false); return; }
        resetList();
        if (l.synced()) buildSynced(l);
        else buildPlain(l);
        applyPadding();
        scroll.scrollTo(0, 0);
        syncNow(false);
    }

    private void resetList() {
        if (scrollAnim != null) scrollAnim.cancel();
        content.removeAllViews();
        lines.clear();
        times = new long[0];
        active = -2; // force the first style pass
        manualShown = false;
    }

    private void buildSynced(Lyrics l) {
        int n = l.lines.size();
        times = new long[n];
        shownAlpha = new float[n];
        shownBlur = new byte[n];
        for (int i = 0; i < n; i++) {
            final int index = i;
            Lyrics.Line line = l.lines.get(i);
            times[i] = line.timeMs;
            TextView v = Type.text(context, TypeScale.LYRIC, ON_PRIMARY, line.text.trim().isEmpty() ? "· · ·" : line.text.trim());
            v.setAlpha(LyricScroll.FUTURE_ALPHA);
            shownAlpha[i] = LyricScroll.FUTURE_ALPHA;
            v.setBackground(Ui.pressable(host.colors(), 0, Ui.dpf(context, 8)));
            v.setOnClickListener(x -> jumpTo(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(context, 18);
            content.addView(v, lp);
            lines.add(v);
        }
        addAttribution(l);
    }

    private void buildPlain(Lyrics l) {
        TextView body = Type.text(context, TypeScale.TITLE2, ON_PRIMARY, l.plain);
        body.setAlpha(0.92f);
        body.setLineSpacing(Ui.dpf(context, 10), 1f);
        body.setTextIsSelectable(false);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));
        addAttribution(l);
    }

    private void addAttribution(Lyrics l) {
        String label = Lyrics.SOURCE_EMBEDDED.equals(l.source) ? "Lyrics from file" : Lyrics.SOURCE_LRCLIB.equals(l.source) ? "Lyrics via LRCLIB" : "";
        if (label.isEmpty()) return;
        TextView t = Type.text(context, TypeScale.CAPTION1, ColorRoles.ON_ART_SECONDARY, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(context, 16);
        content.addView(t, lp);
    }

    private void showMessage(String headline, String body, boolean retry) {
        resetList();
        lyrics = null;
        TextView h = Type.text(context, TypeScale.TITLE2, ON_PRIMARY, headline);
        content.addView(h, new LinearLayout.LayoutParams(-1, -2));
        if (body != null) {
            TextView b = Type.text(context, TypeScale.CALLOUT, ColorRoles.ON_ART_SECONDARY, body);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = Ui.dp(context, 8);
            content.addView(b, bp);
        }
        if (retry) {
            PillButton again = new PillButton(context, host.colors(), PillButton.SECONDARY, "Try Again", 0);
            again.setOnClickListener(v -> { if (shown != null) { Haptics.click(v); loadLyrics(shown); } });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, Ui.dp(context, 48));
            rp.topMargin = Ui.dp(context, 16);
            content.addView(again, rp);
        }
        applyPadding();
        scroll.scrollTo(0, 0);
    }

    // ---------------------------------------------------------------- following the song

    private void update() {
        PlayerHub hub = host.player();
        long dur = hub.duration() > 0 ? hub.duration() : shown == null ? 0 : shown.duration;
        progressFill.setScaleX(dur > 0 ? Math.max(0f, Math.min(1f, hub.position() / (float) dur)) : 0f);
        syncNow(true);
    }

    private void syncNow(boolean animate) {
        if (lyrics == null || lines.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        boolean follow = LyricScroll.autoScroll(now, lastManual, touching);
        int idx = lyrics.indexAt(host.player().position() + LEAD_MS);
        boolean manual = !follow;
        if (idx != active || manual != manualShown) {
            active = idx;
            manualShown = manual;
            styleLines(animate);
            if (follow) scrollToActive(animate);
        }
    }

    private void jumpTo(int index) {
        if (index < 0 || index >= times.length) return;
        Haptics.tick(content);
        host.player().seek(times[index]);
        lastManual = 0;
        touching = false;
        active = index;
        manualShown = false;
        styleLines(true);
        scrollToActive(true);
        host.main().removeCallbacks(tick);
        host.main().postDelayed(tick, 120);
    }

    private void styleLines(boolean animate) {
        boolean blurOk = Build.VERSION.SDK_INT >= 31 && !host.activity().isInMultiWindowMode();
        for (int i = 0; i < lines.size(); i++) {
            TextView v = lines.get(i);
            float a = LyricScroll.alpha(i, active, manualShown);
            if (Math.abs(a - shownAlpha[i]) > 0.005f) {
                shownAlpha[i] = a;
                v.animate().cancel();
                if (animate && Motion.enabled() && Math.abs(i - active) < 8) v.animate().alpha(a).setDuration(Motion.BASE).setInterpolator(Motion.standard()).start();
                else v.setAlpha(a);
            }
            if (blurOk) {
                float dp = LyricScroll.blurDp(i, active, manualShown);
                byte bucket = (byte) (dp <= 0 ? 0 : dp < 1.2f ? 1 : 2);
                if (bucket != shownBlur[i]) {
                    shownBlur[i] = bucket;
                    float px = dp * density;
                    v.setRenderEffect(bucket == 0 ? null : RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP));
                }
            }
        }
    }

    private void scrollToActive(boolean animate) {
        if (lines.isEmpty() || scroll.getHeight() <= 0) { host.main().post(() -> { if (visible && !lines.isEmpty()) scrollToActive(false); }); return; }
        final int target;
        if (active < 0) target = 0;
        else target = LyricScroll.target(lines.get(active).getTop(), scroll.getHeight(), content.getHeight());
        int from = scroll.getScrollY();
        if (scrollAnim != null) scrollAnim.cancel();
        if (from == target) return;
        if (!animate || !Motion.enabled()) { scroll.scrollTo(0, target); return; }
        scrollAnim = ValueAnimator.ofInt(from, target);
        scrollAnim.setDuration(LyricScroll.durationMs(target - from, density));
        scrollAnim.setInterpolator(Motion.standard());
        scrollAnim.addUpdateListener(a -> scroll.scrollTo(0, (int) a.getAnimatedValue()));
        scrollAnim.start();
    }
}
