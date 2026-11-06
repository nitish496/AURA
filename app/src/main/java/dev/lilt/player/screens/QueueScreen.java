package dev.lilt.player.screens;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.Player;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.PlayerHub;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;
import dev.lilt.player.screens.player.PlayerBackdrop;
import dev.lilt.player.screens.player.PlayerCapsule;
import dev.lilt.player.screens.player.PlayerColors;
import dev.lilt.player.screens.player.QueueModel;
import dev.lilt.player.screens.player.ReorderListView;
import dev.lilt.player.screens.player.SessionLink;
import dev.lilt.player.ui.ArtworkPalette;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassButton;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Toggle;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The queue: the current song on top, then "Playing Next" with drag handles (reorder) and
 * swipe-to-remove, shuffle / repeat / autoplay toggles, an Autoplay section and Clear Upcoming.
 * While shuffle is on the songs are listed in their shuffled play order and cannot be moved.
 */
public final class QueueScreen extends Screen {
    private static final int ON_PRIMARY = ColorRoles.ON_ART_PRIMARY;

    private Context context;
    private FrameLayout root;
    private PlayerBackdrop backdrop;
    private LinearLayout header;
    private ImageView cover;
    private TextView title, artist, subtitle, emptyNote, shuffleNote;
    private GlassButton more;
    private ImageView shuffleButton, repeatButton, autoplayButton;
    private ReorderListView list;
    private Toggle autoplayToggle;
    private GlassView capsule;
    private final QueueAdapter adapter = new QueueAdapter();

    private final List<Track> upcoming = new ArrayList<>();
    private QueueModel model = QueueModel.of(0, -1, null);
    private Track shown;
    private String shownId = "";
    private boolean visible, autoplay, firstRefresh = true, rebuildAfterDrag;
    private int insetTop, insetBottom;
    private SessionLink link;
    private final SessionLink.Listener linkListener = this::rebuild;

    public QueueScreen() { }

    @Override public String key() { return "queue"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override public boolean fullScreen() { return true; }
    @Override public boolean lightSystemBars() { return true; }

    @Override protected View onCreateView(Context c) {
        context = c;
        ColorRoles roles = host.colors();
        link = SessionLink.get(c);
        root = new FrameLayout(c);
        backdrop = new PlayerBackdrop(c);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        list = new ReorderListView(c);
        list.setClipToPadding(false);
        list.setCallbacks(new ReorderListView.Callbacks() {
            @Override public int count() { return upcoming.size(); }
            @Override public boolean canDrag(int index) { return model.canReorder() && !host.player().shuffle(); }
            @Override public boolean canSwipe(int index) { return true; }
            @Override public void onMove(int from, int to) { QueueModel.moveInList(upcoming, from, to); adapter.notifyDataSetChanged(); }
            @Override public void onDrop(int from, int to) { drop(from, to); }
            @Override public void onSwipeRemove(int index) { remove(index); }
        });
        buildListHeader();
        buildListFooter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            int index = position - list.getHeaderViewsCount();
            if (index < 0 || index >= upcoming.size()) return;
            int queueIndex = queueIndexOf(upcoming.get(index), index);
            if (queueIndex >= 0) { Haptics.click(view); host.player().playQueueIndex(queueIndex); }
        });
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));

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
        more = new GlassButton(c, roles, R.drawable.ic_more, "More", 36);
        more.setOnArtwork(true);
        more.setIconColor(ON_PRIMARY);
        more.setBackdrop(backdrop);
        more.setOnClickListener(v -> { if (shown != null) host.showTrackMenu(shown, v); });
        header.addView(more, new LinearLayout.LayoutParams(more.size(), more.size()));
        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        capsule = PlayerCapsule.build(c, host, roles, backdrop, PlayerCapsule.QUEUE);
        root.addView(capsule, new FrameLayout.LayoutParams(Ui.dp(c, PlayerCapsule.WIDTH_DP), Ui.dp(c, PlayerCapsule.HEIGHT_DP), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        applyInsets();
        return root;
    }

    private void buildListHeader() {
        LinearLayout h = Ui.column(context);
        h.setPadding(Ui.dp(context, 24), Ui.dp(context, 12), Ui.dp(context, 24), Ui.dp(context, 8));
        LinearLayout row = Ui.row(context);
        LinearLayout labels = Ui.column(context);
        TextView heading = Type.text(context, TypeScale.TITLE2, ON_PRIMARY, "Playing Next");
        subtitle = Type.text(context, TypeScale.FOOTNOTE, ColorRoles.ON_ART_SECONDARY);
        Type.singleLine(subtitle);
        labels.addView(heading, new LinearLayout.LayoutParams(-2, -2));
        labels.addView(subtitle, new LinearLayout.LayoutParams(-2, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        shuffleButton = toggleButton(R.drawable.ic_shuffle, "Shuffle", v -> {
            Haptics.toggle(v, !host.player().shuffle());
            host.player().setShuffle(!host.player().shuffle());
        });
        repeatButton = toggleButton(R.drawable.ic_repeat, "Repeat", v -> {
            int mode = host.player().repeatMode();
            int nextMode = mode == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL : mode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
            Haptics.tick(v);
            host.player().setRepeatMode(nextMode);
        });
        autoplayButton = toggleButton(R.drawable.ic_autoplay, "Autoplay", v -> setAutoplay(!autoplay));
        for (ImageView b : new ImageView[] {shuffleButton, repeatButton, autoplayButton}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48));
            lp.setMarginStart(Ui.dp(context, 8));
            row.addView(b, lp);
        }
        h.addView(row, new LinearLayout.LayoutParams(-1, -2));
        shuffleNote = Type.text(context, TypeScale.FOOTNOTE, ColorRoles.ON_ART_SECONDARY, "Shuffle is on, so songs play in a shuffled order.");
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = Ui.dp(context, 8);
        h.addView(shuffleNote, np);
        shuffleNote.setVisibility(View.GONE);
        list.addHeaderView(h, null, false);
    }

    private void buildListFooter() {
        LinearLayout f = Ui.column(context);
        f.setPadding(Ui.dp(context, 24), Ui.dp(context, 8), Ui.dp(context, 24), Ui.dp(context, 8));
        emptyNote = Type.text(context, TypeScale.CALLOUT, ColorRoles.ON_ART_SECONDARY, "Nothing is queued after this song.");
        f.addView(emptyNote, new LinearLayout.LayoutParams(-2, -2));
        TextView clear = pill("Clear Upcoming");
        clear.setOnClickListener(v -> clearUpcoming());
        clear.setTag("clear");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, Ui.dp(context, 44));
        cp.topMargin = Ui.dp(context, 12);
        f.addView(clear, cp);

        LinearLayout auto = Ui.row(context);
        auto.setPadding(0, Ui.dp(context, 28), 0, Ui.dp(context, 8));
        LinearLayout labels = Ui.column(context);
        labels.addView(Type.text(context, TypeScale.TITLE2, ON_PRIMARY, "Autoplay"), new LinearLayout.LayoutParams(-2, -2));
        TextView hint = Type.text(context, TypeScale.FOOTNOTE, ColorRoles.ON_ART_SECONDARY, "Autoplay similar songs when the queue ends");
        labels.addView(hint, new LinearLayout.LayoutParams(-2, -2));
        auto.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        autoplayToggle = new Toggle(context, host.colors());
        autoplayToggle.setListener((t, on) -> setAutoplay(on));
        auto.addView(autoplayToggle, new LinearLayout.LayoutParams(-2, -2));
        f.addView(auto, new LinearLayout.LayoutParams(-1, -2));
        list.addFooterView(f, null, false);
    }

    private TextView pill(String label) {
        TextView t = Type.text(context, TypeScale.BUTTON, ON_PRIMARY, label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
        t.setBackground(Ui.pressable(host.colors(), ColorRoles.ON_ART_GLASS, -1));
        t.setClickable(true);
        return t;
    }

    /** 36dp-tall capsule inside a 48dp touch target. */
    private android.graphics.drawable.Drawable pill(int color) {
        int inset = Ui.dp(context, 6);
        return new android.graphics.drawable.InsetDrawable(Ui.roundRect(color, -1), 0, inset, 0, inset);
    }

    private ImageView toggleButton(int icon, String description, View.OnClickListener click) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = Ui.dp(context, 8);
        v.setPadding(p, p + Ui.dp(context, 6), p, p + Ui.dp(context, 6));
        v.setContentDescription(description);
        v.setImageDrawable(Icons.get(context, icon, ON_PRIMARY));
        v.setBackground(pill(0x1FFFFFFF));
        v.setOnClickListener(click);
        return v;
    }

    private void styleToggle(ImageView v, int icon, boolean on, String description) {
        v.setImageDrawable(Icons.get(context, icon, on ? 0xFF151517 : ON_PRIMARY));
        v.setBackground(pill(on ? 0xFFF5F5F7 : 0x1FFFFFFF));
        v.setSelected(on);
        v.setContentDescription(description);
    }

    // ---------------------------------------------------------------- insets / lifecycle

    @Override public void onInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        if (root != null) applyInsets();
    }

    private void applyInsets() {
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        hp.topMargin = insetTop + Ui.dp(context, 12);
        hp.setMarginStart(Ui.dp(context, 24));
        hp.setMarginEnd(Ui.dp(context, 24) - (more.size() - Ui.dp(context, 36)) / 2);
        header.setLayoutParams(hp);
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) capsule.getLayoutParams();
        cp.bottomMargin = insetBottom + Ui.dp(context, dev.lilt.player.ui.ShelfMetrics.compactChrome(context.getResources().getConfiguration().screenHeightDp) ? 8 : 12);
        capsule.setLayoutParams(cp);
        boolean compact = dev.lilt.player.ui.ShelfMetrics.compactChrome(context.getResources().getConfiguration().screenHeightDp);
        // Short (landscape) windows: tighter gaps around the header and capsule, same clearance for the last row.
        list.setPadding(0, insetTop + Ui.dp(context, compact ? 12 + 48 : 12 + 48 + 4), 0, insetBottom + Ui.dp(context, compact ? 8 + 52 + 8 : 12 + 52 + 16));
    }

    @Override public void onShow() {
        visible = true;
        link.acquire();
        link.addListener(linkListener);
        refreshAll();
        loadOptions();
    }

    @Override public void onHide() {
        visible = false;
        link.removeListener(linkListener);
        link.release();
        backdrop.setAnimating(false);
    }

    @Override public void onDestroy() {
        visible = false;
        upcoming.clear();
        if (cover != null) cover.setImageDrawable(null);
        shown = null;
    }

    @Override public void onPlaybackChanged() {
        if (visible && root != null) refreshAll();
    }

    @Override public void onThemeChanged() { }

    // ---------------------------------------------------------------- data

    private void refreshAll() {
        PlayerHub hub = host.player();
        Track t = hub.current();
        if (t == null) {
            if (!shownId.isEmpty() || firstRefresh) {
                shown = null;
                shownId = "";
                title.setText("Not playing");
                artist.setText("");
                cover.setImageDrawable(null);
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
        } else {
            shown = t;
        }
        more.setEnabled(shown != null);
        backdrop.setAnimating(visible && hub.playing());
        styleToggle(shuffleButton, R.drawable.ic_shuffle, hub.shuffle(), hub.shuffle() ? "Shuffle on" : "Shuffle off");
        int mode = hub.repeatMode();
        styleToggle(repeatButton, mode == Player.REPEAT_MODE_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat, mode != Player.REPEAT_MODE_OFF,
                mode == Player.REPEAT_MODE_ONE ? "Repeat one" : mode == Player.REPEAT_MODE_ALL ? "Repeat all" : "Repeat off");
        shuffleNote.setVisibility(hub.shuffle() ? View.VISIBLE : View.GONE);
        firstRefresh = false;
        if (list.isDragging()) { rebuildAfterDrag = true; return; }
        rebuild();
    }

    private void loadOptions() {
        host.player().playbackOptions(b -> {
            if (b == null || !visible) return;
            autoplay = b.getBoolean("autoplayEnabled", autoplay);
            showAutoplay();
        });
    }

    private void setAutoplay(boolean on) {
        autoplay = on;
        showAutoplay();
        Bundle b = new Bundle();
        b.putBoolean("autoplayEnabled", on);
        host.player().setPlaybackOptions(b);
    }

    private void showAutoplay() {
        styleToggle(autoplayButton, R.drawable.ic_autoplay, autoplay, autoplay ? "Autoplay on" : "Autoplay off");
        if (autoplayToggle.isOn() != autoplay) autoplayToggle.setOn(autoplay, false, false);
    }

    private void applyPalette(ArtworkPalette p, boolean animate) {
        backdrop.setPalette(p, animate);
        artist.setTextColor(p.textSecondary);
    }

    /** Rebuilds the "Playing Next" rows from the player's queue. */
    private void rebuild() {
        if (root == null || list.isDragging()) return;
        PlayerHub hub = host.player();
        List<Track> queue = hub.queue();
        int index = hub.index();
        model = QueueModel.of(queue.size(), index, hub.shuffle() ? link.shuffleOrder() : null);
        upcoming.clear();
        for (int i = 0; i < model.size(); i++) {
            int q = model.queueIndex(i);
            if (q >= 0 && q < queue.size()) upcoming.add(queue.get(q));
        }
        Track current = hub.current();
        String from = current == null ? "" : meaningful(current.album) ? "From " + current.album : "";
        subtitle.setText(hub.shuffle() ? "Shuffled" + (from.isEmpty() ? "" : " · " + from) : from);
        subtitle.setVisibility(subtitle.getText().length() == 0 ? View.GONE : View.VISIBLE);
        boolean empty = upcoming.isEmpty();
        emptyNote.setVisibility(empty ? View.VISIBLE : View.GONE);
        View clear = list.findViewWithTag("clear");
        if (clear != null) clear.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
        rebuildAfterDrag = false;
    }

    private static boolean meaningful(String album) {
        return album != null && !album.isEmpty() && !album.equals(Track.REMOTE_SINGLE) && !album.toLowerCase(java.util.Locale.ROOT).contains("unknown");
    }

    /** The queue index of the row, resolved from the model (the list may be mid-drag). */
    private int queueIndexOf(Track track, int row) {
        List<Track> queue = host.player().queue();
        if (row < model.size()) {
            int q = model.queueIndex(row);
            if (q >= 0 && q < queue.size() && queue.get(q).id.equals(track.id)) return q;
        }
        for (int i = 0; i < queue.size(); i++) if (queue.get(i) == track) return i;
        return -1;
    }

    private void drop(int from, int to) {
        if (from != to) {
            int[] move = model.move(from, to);
            if (move != null) host.player().moveQueueItem(move[0], move[1]);
        }
        rebuildAfterDrag = false;
        adapter.notifyDataSetChanged();
    }

    private void remove(int row) {
        if (row < 0 || row >= upcoming.size() || row >= model.size()) return;
        int q = model.queueIndex(row);
        upcoming.remove(row);
        adapter.notifyDataSetChanged();
        host.player().removeQueueItem(q);
    }

    private void clearUpcoming() {
        if (upcoming.isEmpty()) return;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) indices.add(model.queueIndex(i));
        Collections.sort(indices, Collections.reverseOrder());
        Haptics.confirm(list);
        for (int q : indices) host.player().removeQueueItem(q);
        upcoming.clear();
        adapter.notifyDataSetChanged();
        host.toast("Upcoming songs cleared");
    }

    // ---------------------------------------------------------------- adapter

    private final class QueueAdapter extends BaseAdapter {
        @Override public int getCount() { return upcoming.size(); }
        @Override public Object getItem(int position) { return upcoming.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View reuse, ViewGroup parent) {
            Row row = reuse instanceof Row ? (Row) reuse : new Row(context);
            Track t = upcoming.get(position);
            row.title.setText(t.title);
            row.subtitle.setText(t.isRemote() ? t.artist + " · Audius" : t.artist);
            host.artwork().load(row.cover, t);
            boolean movable = model.canReorder() && !host.player().shuffle();
            row.handle.setVisibility(movable ? View.VISIBLE : View.INVISIBLE);
            row.setTranslationX(0f);
            row.setAlpha(1f);
            row.setVisibility(position == list.draggingIndex() ? View.INVISIBLE : View.VISIBLE);
            return row;
        }
    }

    private final class Row extends LinearLayout {
        final ImageView cover, handle;
        final TextView title, subtitle;
        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(c, 60));
            setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
            cover = new ImageView(c);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.roundOutline(cover, Ui.dpf(c, 6));
            addView(cover, new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
            LinearLayout text = Ui.column(c);
            title = Type.text(c, TypeScale.ROW_TITLE, ON_PRIMARY);
            Type.singleLine(title);
            subtitle = Type.text(c, TypeScale.FOOTNOTE, ColorRoles.ON_ART_SECONDARY);
            Type.singleLine(subtitle);
            text.addView(title, new LayoutParams(-1, -2));
            text.addView(subtitle, new LayoutParams(-1, -2));
            LayoutParams tp = new LayoutParams(0, -2, 1f);
            tp.setMarginStart(Ui.dp(c, 12));
            addView(text, tp);
            handle = new ImageView(c);
            handle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            handle.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            handle.setImageDrawable(Icons.get(c, R.drawable.ic_drag, ColorRoles.ON_ART_TERTIARY));
            handle.setContentDescription("Reorder");
            addView(handle, new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            setBackground(Ui.rowRipple(host.colors()));
        }
    }
}
