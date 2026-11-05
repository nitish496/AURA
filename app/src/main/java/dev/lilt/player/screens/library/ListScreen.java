package dev.lilt.player.screens.library;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.Track;
import dev.lilt.player.app.MenuAction;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.chrome.TopBar;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.ScrollBehavior;
import dev.lilt.player.ui.ScrollTracker;
import dev.lilt.player.ui.SongRow;
import dev.lilt.player.ui.TimeText;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for the library's list screens (Songs, Album, Artist, Playlist): a recycled ListView with
 * a header block, {@link SongRow}s that bind in place (artwork requests are keyed to the row's
 * current track, so recycled rows never show stale covers), a glass {@link TopBar} whose strip
 * fades in as the large title scrolls away, tab-bar minimising via the shell, and live
 * "playing" indicators. Subclasses supply the header and the items.
 *
 * <p>Items are {@link TrackRow}s or arbitrary Views (section headers, shelves, footers).
 */
public abstract class ListScreen extends Screen {
    /** One song in the list. {@code number} &gt; 0 shows a track number instead of artwork. */
    public static final class TrackRow {
        public final Track track;
        public final int number;
        public final String trailing;
        public TrackRow(Track track, int number, String trailing) { this.track = track; this.number = number; this.trailing = trailing; }
    }

    protected Context context;
    protected ColorRoles roles;
    protected FrameLayout frame;
    protected ListView list;
    protected TopBar topBar;
    protected int insetTop, insetBottom;
    protected int titleBottomPx;
    private final ArrayList<Object> items = new ArrayList<>();
    private Adapter adapter;
    private View header;
    private Parcelable pendingScroll;
    private int lastScrollY;

    // ---- subclass API

    /** Header block above the rows (large title, hero, buttons). */
    protected abstract View buildHeader();
    /** Rows in order. Called at creation and whenever the library or playback queue changes. */
    protected abstract List<Object> buildItems();
    /** Small title shown in the glass strip once the header has scrolled away. */
    protected abstract CharSequence barTitle();
    /** Add buttons to {@link #topBar}. */
    protected void buildTopBar(TopBar bar) {}
    /** 0..1: how far the header has scrolled under the bar. */
    protected float collapseProgress(int scrollY) {
        int bottom = titleBottomPx > 0 ? titleBottomPx : insetTop + Ui.dp(context, 40);
        return ScrollBehavior.collapseProgress(scrollY, bottom, insetTop, Ui.dp(context, 16));
    }
    protected void onInsetsChanged() {}
    protected void onScrolled(int scrollY) {}
    protected void onRowClick(TrackRow row) { playFrom(row); }
    protected List<MenuAction> rowExtras(TrackRow row) { return null; }
    protected String rowSubtitle(Track t) { return t.artist; }
    protected boolean showLocalBadge() { return false; }
    protected boolean showArtwork() { return true; }

    // ---- life cycle

    @Override protected View onCreateView(Context c) {
        context = c;
        roles = host.colors();
        frame = new FrameLayout(c);
        list = new ListView(c);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setScrollingCacheEnabled(false);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setHeaderDividersEnabled(false);
        list.setFooterDividersEnabled(false);
        frame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        topBar = new TopBar(c, roles, list);
        topBar.setTitle(barTitle());
        buildTopBar(topBar);
        frame.addView(topBar, TopBar.params());
        header = buildHeader();
        if (header != null) list.addHeaderView(header, null, false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        ScrollTracker.attach(list, null).add((y, dy) -> {
            lastScrollY = y;
            host.onScroll(this, y, dy);
            topBar.setProgress(collapseProgress(y));
            onScrolled(y);
        });
        refreshItems();
        if (pendingScroll != null) { list.onRestoreInstanceState(pendingScroll); pendingScroll = null; }
        return frame;
    }

    @Override public void onInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        if (list == null) return;
        topBar.setInsetTop(top - Ui.dp(context, DesignTokens.TOP_BAR));
        list.setPadding(0, 0, 0, bottom);
        onInsetsChanged();
        topBar.setProgress(collapseProgress(lastScrollY));
    }

    protected void refreshItems() {
        items.clear();
        List<Object> built = buildItems();
        if (built != null) items.addAll(built);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override public void onLibraryChanged() { if (list != null) refreshItems(); }

    @Override public void onPlaybackChanged() { if (list != null) updatePlaying(); }

    @Override public void scrollToTop() { if (list != null) list.smoothScrollToPositionFromTop(0, 0); }

    @Override public Bundle saveState() {
        Bundle b = new Bundle();
        if (list != null && list.getFirstVisiblePosition() > 0) {
            b.putInt("pos", list.getFirstVisiblePosition());
            View first = list.getChildAt(0);
            b.putInt("off", first == null ? 0 : first.getTop() - list.getPaddingTop());
        }
        return b;
    }

    @Override public void restoreState(Bundle state) {
        if (state == null || list == null || !state.containsKey("pos")) return;
        final int pos = state.getInt("pos"), off = state.getInt("off");
        list.post(() -> list.setSelectionFromTop(pos, off));
    }

    @Override public void onDestroy() {
        if (list != null) { list.setAdapter(null); }
    }

    // ---- playback helpers

    /** Tracks of all {@link TrackRow}s in display order. */
    protected List<Track> trackList() {
        List<Track> out = new ArrayList<>();
        for (Object o : items) if (o instanceof TrackRow) out.add(((TrackRow) o).track);
        return out;
    }

    protected void playFrom(TrackRow row) {
        List<Track> tracks = trackList();
        int index = 0;
        for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).id.equals(row.track.id)) { index = i; break; }
        if (tracks.size() > 800) {
            // A very long list: queue a window around the song, not the whole library.
            int from = Math.max(0, index - 100), to = Math.min(tracks.size(), from + 800);
            host.player().play(new ArrayList<>(tracks.subList(from, to)), index - from, false);
        } else host.player().play(tracks, index, false);
    }

    protected void playAll(boolean shuffle) {
        List<Track> tracks = trackList();
        if (tracks.isEmpty()) return;
        host.player().play(tracks, 0, shuffle);
    }

    protected long totalDuration() {
        long d = 0;
        for (Object o : items) if (o instanceof TrackRow) d += Math.max(0, ((TrackRow) o).track.duration);
        return d;
    }

    protected int trackCount() {
        int n = 0;
        for (Object o : items) if (o instanceof TrackRow) n++;
        return n;
    }

    private void updatePlaying() {
        Track current = host.player().current();
        String id = current == null ? null : current.id;
        boolean playing = host.player().playing();
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (v instanceof SongRow && v.getTag() instanceof TrackRow) {
                TrackRow r = (TrackRow) v.getTag();
                ((SongRow) v).setPlaying(id != null && id.equals(r.track.id), playing);
            }
        }
    }

    // ---- adapter

    private final View.OnClickListener rowClick = v -> { if (v.getTag() instanceof TrackRow) onRowClick((TrackRow) v.getTag()); };
    private final View.OnLongClickListener rowLong = v -> {
        if (!(v.getTag() instanceof TrackRow)) return false;
        TrackRow r = (TrackRow) v.getTag();
        Haptics.longPress(v);
        host.showTrackMenu(r.track, v, rowExtras(r));
        return true;
    };
    private final View.OnClickListener moreClick = v -> {
        View row = (View) v.getParent();
        if (row.getTag() instanceof TrackRow) {
            TrackRow r = (TrackRow) row.getTag();
            host.showTrackMenu(r.track, v, rowExtras(r));
        }
    };
    private final View.OnClickListener saveClick = v -> {
        View row = (View) v.getParent();
        if (row.getTag() instanceof TrackRow) {
            Track t = ((TrackRow) row.getTag()).track;
            boolean saved = host.library().isSaved(t.id);
            host.library().setSaved(t, !saved);
            Haptics.toggle(v, !saved);
            ((SongRow) row).setSaveState(!saved);
        }
    };

    private SongRow makeRow() {
        SongRow row = new SongRow(context, roles);
        row.setOnClickListener(rowClick);
        row.setOnLongClickListener(rowLong);
        row.more().setOnClickListener(moreClick);
        row.save().setOnClickListener(saveClick);
        return row;
    }

    private void bindRow(SongRow row, TrackRow r) {
        Track t = r.track;
        row.setTag(r);
        int source = t.isRemote() ? (host.library().isSaved(t.id) ? SongRow.SOURCE_SAVED : SongRow.SOURCE_STREAM)
                : showLocalBadge() ? SongRow.SOURCE_LOCAL_BADGE : SongRow.SOURCE_LOCAL;
        row.setNumber(showArtwork() ? 0 : r.number);
        String sub = rowSubtitle(t);
        row.bind(t.title, sub, false, source);
        row.subtitle().setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        row.setTrailingText(r.trailing);
        if (showArtwork()) host.artwork().load(row.cover(), t);
        Track current = host.player().current();
        row.setPlaying(current != null && current.id.equals(t.id), host.player().playing());
        if (t.isRemote()) row.setSaveState(host.library().isSaved(t.id)); else row.hideSave();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public int getItemViewType(int position) {
            return items.get(position) instanceof TrackRow ? 0 : android.widget.Adapter.IGNORE_ITEM_VIEW_TYPE;
        }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return false; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            Object it = items.get(position);
            if (it instanceof TrackRow) {
                SongRow row = convert instanceof SongRow ? (SongRow) convert : makeRow();
                bindRow(row, (TrackRow) it);
                return row;
            }
            View v = (View) it;
            if (v.getParent() instanceof ViewGroup && v.getParent() != parent) ((ViewGroup) v.getParent()).removeView(v);
            return v;
        }
    }

    protected static String durationText(long ms) { return TimeText.clock(ms); }
}
