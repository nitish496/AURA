package dev.lilt.player.screens.discover;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.Screen;
import dev.lilt.player.stream.Connectivity;
import dev.lilt.player.stream.RemoteTrackStore;
import dev.lilt.player.ui.AlbumCard;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.ScrollTracker;
import dev.lilt.player.ui.SectionHeader;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.ShelfView;
import dev.lilt.player.ui.SongRow;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared scaffolding of the discovery screens: one recycled ListView (padded by the shell's
 * chrome insets, reporting scroll to the tab bar), item builders (titles, headers, song rows,
 * shelves, genre grid, offline / error / skeleton states), network helpers that ignore stale
 * results, and playback / library / connectivity plumbing. Subclasses implement {@link #build()}
 * and call {@link #render()} whenever their state changes. Main thread only, except {@link #work}.
 */
public abstract class DiscoverScreen extends Screen {
    protected Context ctx;
    protected dev.lilt.player.ui.ColorRoles roles;
    protected ListView list;
    protected Rows rows = new Rows();
    protected boolean destroyed, shownOnce;
    protected boolean online = true;
    private FrameLayout root;
    private int padTop, padBottom;
    private Connectivity.Subscription net;

    /** The items of the screen for its current state. */
    protected abstract List<Rows.Item> build();

    /** Called once after the view exists (start the first load here). */
    protected void onReady() {}

    /** Connectivity flipped. */
    protected void onConnectivity(boolean nowOnline) {}

    @Override protected final View onCreateView(Context context) {
        ctx = context;
        roles = host.colors();
        root = new FrameLayout(context);
        root.setBackgroundColor(roles.canvas);
        list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setAdapter(rows);
        ScrollTracker.attach(list, null).add((y, dy) -> host.onScroll(this, y, dy));
        root.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyInsets();
        online = Connectivity.online(context);
        net = Connectivity.listen(context, on -> {
            if (destroyed) return;
            online = on;
            onConnectivity(on);
        });
        onReady();
        render();
        return root;
    }

    @Override public void onInsets(int top, int bottom) {
        padTop = top;
        padBottom = bottom;
        applyInsets();
    }

    private void applyInsets() {
        if (list != null) list.setPadding(0, padTop, 0, padBottom + Ui.dp(ctx, 16));
    }

    @Override public void onShow() { shownOnce = true; }

    @Override public void onDestroy() {
        destroyed = true;
        if (net != null) net.cancel();
        if (list != null) list.setAdapter(null);
    }

    @Override public void onPlaybackChanged() { if (!destroyed) rows.notifyDataSetChanged(); }
    @Override public void onLibraryChanged() { if (!destroyed) rows.notifyDataSetChanged(); }

    @Override public void onThemeChanged() {
        if (destroyed || ctx == null) return;
        roles = host.colors();
        root.setBackgroundColor(roles.canvas);
        rows = new Rows();
        list.setAdapter(rows);
        render();
    }

    /** Rebuilds the item list from the current state (keeps the scroll position). */
    protected final void render() {
        if (destroyed || ctx == null) return;
        rows.set(build());
    }

    // ---- Work off the main thread --------------------------------------------------------------

    /** Blocking work (network / disk). */
    protected interface Job<T> { T run() throws Exception; }
    protected interface Result<T> { void accept(T value); }

    /**
     * Runs {@code job} on the shared I/O executor and delivers on the main thread only while
     * {@code gate} still holds generation {@code gen} and the screen is alive.
     */
    protected final <T> void work(QueryGate gate, int gen, Job<T> job, Result<T> ok, Result<Exception> failed) {
        host.io().execute(() -> {
            if (!gate.isCurrent(gen)) return;
            T value = null;
            Exception error = null;
            try { value = job.run(); } catch (Exception e) { error = e; }
            final T result = value;
            final Exception failure = error;
            host.main().post(() -> {
                if (destroyed || !gate.isCurrent(gen)) return;
                if (failure != null) { if (failed != null) failed.accept(failure); }
                else ok.accept(result);
            });
        });
    }

    protected static boolean isNetworkError(Exception e) { return e instanceof IOException; }

    // ---- Playback -----------------------------------------------------------------------------

    /** Plays {@code queue} from {@code index}; remembers remote tracks so the queue resolves offline. */
    protected final void play(List<Track> queue, int index, boolean shuffle) {
        if (queue == null || queue.isEmpty()) return;
        List<Track> remote = new ArrayList<>();
        for (Track t : queue) if (t.isRemote()) remote.add(t);
        if (!remote.isEmpty()) host.io().execute(() -> new RemoteTrackStore(ctx).putAll(remote));
        host.player().play(queue, Math.max(0, Math.min(index, queue.size() - 1)), shuffle);
    }

    protected final boolean isCurrent(Track t) {
        Track now = host.player().current();
        return now != null && t != null && now.id.equals(t.id);
    }

    protected final void toggleSaved(Track t, View anchor) {
        if (!t.isRemote()) return;
        boolean was = host.library().isSaved(t.id);
        host.library().setSaved(t, !was);
        Haptics.confirm(anchor);
        host.toast(was ? "Removed from Library" : "Saved to Library");
        rows.notifyDataSetChanged();
    }

    // ---- Item builders ------------------------------------------------------------------------

    protected final int dp(float v) { return Ui.dp(ctx, v); }

    protected final Rows.Item title(String text) {
        TextView t = Type.text(ctx, TypeScale.LARGE_TITLE, roles.textPrimary, text);
        Type.singleLine(t);
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        t.setPadding(dp(DesignTokens.MARGIN), dp(4), dp(DesignTokens.MARGIN), dp(DesignTokens.SPACE_12));
        return Rows.of(t);
    }

    protected final Rows.Item spacer(int heightDp) { return Rows.of(Ui.space(ctx, 1, heightDp)); }

    /** Section header with the standard gap above it; {@code seeAll} adds the chevron. */
    protected final Rows.Item header(String text, String subtitle, Runnable seeAll) {
        SectionHeader h = new SectionHeader(ctx, roles, text);
        if (seeAll != null) h.onSeeAll(seeAll);
        if (subtitle != null) h.subtitle(subtitle);
        LinearLayout wrap = Ui.column(ctx);
        wrap.setPadding(0, dp(DesignTokens.SECTION_GAP - 8), 0, dp(4));
        wrap.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return Rows.of(wrap);
    }

    protected final int windowDp() { return ctx.getResources().getConfiguration().screenWidthDp; }

    /** Shelf of square cards: {@code count} items, {@code bind} fills text, {@code art} loads the image. */
    protected interface CardBinder {
        void bind(AlbumCard card, int i);
        void art(AlbumCard card, int i);
        void click(int i, View card);
    }

    protected final Rows.Item shelf(int count, int cardDp, boolean circle, CardBinder binder) {
        ShelfView shelf = new ShelfView(ctx);
        shelf.setSnapToItems(false);
        shelf.setAdapter(new ShelfView.Adapter() {
            @Override public int count() { return count; }
            @Override public View create(ViewGroup parent) { return new AlbumCard(ctx, roles, cardDp, circle, false); }
            @Override public void bind(View v, int i) {
                AlbumCard card = (AlbumCard) v;
                binder.bind(card, i);
                card.setOnClickListener(x -> binder.click(i, x));
                if (circle) Art.placeholder(card.cover(), "", true);
            }
            @Override public void onVisible(View v, int i) { binder.art((AlbumCard) v, i); }
        });
        return Rows.of(shelf);
    }

    /** Card width for shelves that show about {@code visible} cards. */
    protected final int cardDp(float visible, int min, int max) { return ShelfMetrics.cardWidth(windowDp(), visible, min, max); }

    protected final int sourceOf(Track t) {
        if (!t.isRemote()) return SongRow.SOURCE_LOCAL_BADGE;
        return host.library().isSaved(t.id) ? SongRow.SOURCE_SAVED : SongRow.SOURCE_STREAM;
    }

    /** Subtitle of a song row: artist, plus the album unless it is a single. */
    protected static String songSubtitle(Track t, boolean withAlbum) {
        if (!withAlbum || t.album == null || t.album.isEmpty() || Track.REMOTE_SINGLE.equals(t.album)) return t.artist;
        return DiscoverText.joined(t.artist, t.album);
    }

    /** One song row. */
    protected final class SongItem extends Rows.Item {
        final Track track;
        final List<Track> queue;
        final int index;
        int rank;
        boolean withAlbum, showSave = true;
        CharSequence titleText;
        Runnable onOpen;

        SongItem(Track track, List<Track> queue, int index) { this.track = track; this.queue = queue; this.index = index; }
        public SongItem rank(int n) { rank = n; return this; }
        public SongItem album(boolean on) { withAlbum = on; return this; }
        public SongItem highlighted(CharSequence t) { titleText = t; return this; }
        public SongItem noSave() { showSave = false; return this; }
        public SongItem onOpen(Runnable r) { onOpen = r; return this; }

        @Override public int type() { return Rows.SONG; }
        @Override public View create(ViewGroup parent) { return new DiscoverRow(ctx, roles); }
        @Override public void bind(View v) {
            DiscoverRow row = (DiscoverRow) v;
            row.bind(titleText != null ? titleText : track.title, songSubtitle(track, withAlbum), false, sourceOf(track));
            row.setRank(rank);
            boolean cur = isCurrent(track);
            row.setPlaying(cur, cur && host.player().playing());
            row.setSaveState(track.isRemote() && showSave
                    ? (host.library().isSaved(track.id) ? DiscoverRow.SAVE_ON : DiscoverRow.SAVE_OFF) : DiscoverRow.SAVE_HIDDEN);
            row.save().setOnClickListener(x -> toggleSaved(track, x));
            row.more().setOnClickListener(x -> host.showTrackMenu(track, x));
            row.setOnClickListener(x -> {
                if (onOpen != null) onOpen.run();
                play(queue, index, false);
            });
            row.setOnLongClickListener(x -> { Haptics.longPress(x); host.showTrackMenu(track, x); return true; });
            Art.track(host, row.cover(), track);
        }
    }

    protected final SongItem song(Track t, List<Track> queue, int index) { return new SongItem(t, queue, index); }

    /** Two genre tiles per row. */
    protected final List<Rows.Item> genreGrid(List<String> genres, GenreOpen open) {
        List<Rows.Item> out = new ArrayList<>();
        for (int i = 0; i < genres.size(); i += 2) {
            String a = genres.get(i), b = i + 1 < genres.size() ? genres.get(i + 1) : null;
            out.add(new Rows.Item() {
                @Override public int type() { return Rows.GENRES; }
                @Override public View create(ViewGroup parent) { return new GenrePair(ctx); }
                @Override public void bind(View v) { ((GenrePair) v).bind(a, b, open); }
            });
        }
        return out;
    }

    protected interface GenreOpen { void open(String genre); }

    private static final class GenrePair extends LinearLayout {
        private final TextView left, right;
        GenrePair(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            int m = Ui.dp(c, DesignTokens.MARGIN), g = Ui.dp(c, DesignTokens.GUTTER);
            setPadding(m, 0, m, g);
            left = tile(c);
            right = tile(c);
            addView(left, new LayoutParams(0, Ui.dp(c, DesignTokens.GENRE_TILE_HEIGHT), 1));
            LayoutParams rp = new LayoutParams(0, Ui.dp(c, DesignTokens.GENRE_TILE_HEIGHT), 1);
            rp.setMarginStart(g);
            addView(right, rp);
        }
        private static TextView tile(Context c) {
            TextView t = Type.text(c, TypeScale.HEADLINE, dev.lilt.player.ui.ColorRoles.ON_ART_PRIMARY);
            Type.maxLines(t, 2);
            t.setGravity(Gravity.TOP | Gravity.START);
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            t.setClickable(true);
            return t;
        }
        void bind(String a, String b, GenreOpen open) {
            fill(left, a, open);
            fill(right, b, open);
        }
        private void fill(TextView t, String genre, GenreOpen open) {
            if (genre == null) { t.setVisibility(INVISIBLE); t.setOnClickListener(null); return; }
            t.setVisibility(VISIBLE);
            t.setText(DiscoverText.genre(genre));
            t.setBackground(Ui.pressable(dev.lilt.player.ui.ColorRoles.DARK, GenrePalette.color(genre), Ui.dpf(getContext(), DesignTokens.RADIUS_M)));
            t.setContentDescription(DiscoverText.genre(genre) + " genre");
            t.setOnClickListener(x -> open.open(genre));
        }
    }

    // ---- States -------------------------------------------------------------------------------

    /** Icon, Title 2, body and an optional pill action (offline, error, empty). */
    protected final Rows.Item stateBlock(int iconRes, String titleText, String body, String actionLabel, int actionKind, Runnable action) {
        LinearLayout col = Ui.column(ctx);
        col.setPadding(dp(DesignTokens.MARGIN), dp(16), dp(DesignTokens.MARGIN), dp(8));
        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(Icons.get(ctx, iconRes, roles.textSecondary, dp(32)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        col.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView t = Type.text(ctx, TypeScale.TITLE2, roles.textPrimary, titleText);
        col.addView(t, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 16, 0, 0));
        TextView b = Type.text(ctx, TypeScale.SUBHEAD, roles.textSecondary, body);
        col.addView(b, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 8, 0, 0));
        if (actionLabel != null && action != null) {
            PillButton p = new PillButton(ctx, roles, actionKind, actionLabel, 0);
            p.setOnClickListener(x -> action.run());
            col.addView(p, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 20, 0, 0));
        }
        return Rows.of(col);
    }

    /** Inline notice card: icon, two lines and a text action ("Couldn't reach Audius" / "Try Again"). */
    protected final Rows.Item notice(String line1, String line2, String actionLabel, Runnable action) {
        LinearLayout card = Ui.row(ctx);
        card.setBackground(Ui.roundRect(roles.surface, Ui.dpf(ctx, DesignTokens.RADIUS_L)));
        card.setPadding(dp(16), dp(10), dp(8), dp(10));
        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(Icons.get(ctx, R.drawable.ic_alert, roles.textSecondary, dp(22)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout text = Ui.column(ctx);
        text.addView(Type.text(ctx, TypeScale.SUBHEAD.weight(500), roles.textPrimary, line1));
        text.addView(Type.text(ctx, TypeScale.FOOTNOTE, roles.textSecondary, line2));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(dp(12));
        card.addView(text, tp);
        if (actionLabel != null && action != null) {
            PillButton p = new PillButton(ctx, roles, PillButton.TEXT, actionLabel, 0);
            p.setOnClickListener(x -> action.run());
            card.addView(p);
        }
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setPadding(dp(DesignTokens.MARGIN), dp(4), dp(DesignTokens.MARGIN), dp(8));
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return Rows.of(wrap);
    }

    /** {@code n} skeleton song rows. */
    protected final List<Rows.Item> skeletonSongs(int n) {
        List<Rows.Item> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Rows.Item() {
            @Override public int type() { return Rows.SKELETON_ROW; }
            @Override public View create(ViewGroup parent) { return skeletonRow(); }
        });
        return out;
    }

    private View skeletonRow() {
        LinearLayout row = Ui.row(ctx);
        row.setMinimumHeight(dp(DesignTokens.ROW_SONG));
        row.setPaddingRelative(dp(DesignTokens.MARGIN), 0, dp(DesignTokens.MARGIN), 0);
        row.addView(new Skeleton(ctx, roles, dpf(DesignTokens.RADIUS_S)), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout text = Ui.column(ctx);
        text.addView(new Skeleton(ctx, roles, dpf(5)), new LinearLayout.LayoutParams(dp(150), dp(14)));
        text.addView(new Skeleton(ctx, roles, dpf(5)), Ui.margins(ctx, new LinearLayout.LayoutParams(dp(96), dp(11)), 0, 6, 0, 0));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(dp(12));
        row.addView(text, tp);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return row;
    }

    /** A shelf of skeleton cards (header skeleton included when {@code header}). */
    protected final Rows.Item skeletonShelf(int cardDp, boolean circle) {
        LinearLayout strip = Ui.row(ctx);
        strip.setGravity(Gravity.TOP);
        strip.setPadding(dp(DesignTokens.MARGIN), dp(4), 0, 0);
        for (int i = 0; i < 4; i++) {
            LinearLayout cell = Ui.column(ctx);
            cell.addView(new Skeleton(ctx, roles, circle ? 10_000f : dpf(DesignTokens.artworkRadius(cardDp))), new LinearLayout.LayoutParams(dp(cardDp), dp(cardDp)));
            cell.addView(new Skeleton(ctx, roles, dpf(5)), Ui.margins(ctx, new LinearLayout.LayoutParams(dp(cardDp * 0.7f), dp(13)), 0, 10, 0, 0));
            cell.addView(new Skeleton(ctx, roles, dpf(5)), Ui.margins(ctx, new LinearLayout.LayoutParams(dp(cardDp * 0.45f), dp(11)), 0, 6, 0, 0));
            strip.addView(cell, Ui.margins(ctx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), 0, 0, DesignTokens.GUTTER, 0));
        }
        strip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return Rows.of(strip);
    }

    protected final Rows.Item skeletonHeader() {
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setPadding(dp(DesignTokens.MARGIN), dp(DesignTokens.SECTION_GAP - 4), dp(DesignTokens.MARGIN), dp(8));
        wrap.addView(new Skeleton(ctx, roles, dpf(6)), new FrameLayout.LayoutParams(dp(180), dp(22)));
        wrap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return Rows.of(wrap);
    }

    protected final float dpf(float v) { return Ui.dpf(ctx, v); }

    /** 48dp round icon button on a fill (Save, More). */
    protected final ImageView iconPill(int iconRes, int tint, String description, Runnable action) {
        ImageView b = new ImageView(ctx);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setImageDrawable(Icons.get(ctx, iconRes, tint, dp(22)));
        b.setBackground(Ui.pressable(roles, roles.fill, -1));
        b.setContentDescription(description);
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        return b;
    }
}
