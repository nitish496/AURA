package dev.lilt.player.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;
import dev.lilt.player.audio.ListeningLedger;
import dev.lilt.player.audio.ListeningStats;
import dev.lilt.player.screens.player.ReplayText;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.EmptyState;
import dev.lilt.player.ui.GlassButton;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.PlaceholderDrawable;
import dev.lilt.player.ui.SectionHeader;
import dev.lilt.player.ui.SegmentedControl;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Replay: what the listener actually played. Minutes listened for this week, this month or all
 * time, top artists, top songs and recently played, all read from {@link ListeningStats}. There
 * are no estimates: with no history the screen says so.
 */
public final class ReplayScreen extends Screen {
    private static final int TOP_SONGS = 10, TOP_ARTISTS = 5, RECENT = 8;

    /** One computed snapshot, built on the background thread. */
    private static final class Snapshot {
        long minutes;
        int songs, artists;
        List<ListeningLedger.ArtistStat> topArtists;
        List<ListeningLedger.TrackStat> topSongs, recent;
        long now;
    }

    private Context context;
    private ColorRoles roles;
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content, body;
    private SegmentedControl segmented;
    private int period = ReplayText.MONTH, insetTop, insetBottom, generation;
    private boolean visible;
    private Snapshot snapshot;
    private final Runnable statsChanged = () -> { if (visible) load(); };

    public ReplayScreen() { }

    @Override public String key() { return "replay"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override public boolean fullScreen() { return true; }

    @Override protected View onCreateView(Context c) {
        context = c;
        roles = host.colors();
        root = new FrameLayout(c);
        root.setBackgroundColor(roles.canvas);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        content = Ui.column(c);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView largeTitle = Type.text(c, TypeScale.LARGE_TITLE, roles.textPrimary, "Replay");
        if (android.os.Build.VERSION.SDK_INT >= 28) largeTitle.setAccessibilityHeading(true);
        largeTitle.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 8));
        content.addView(largeTitle, new LinearLayout.LayoutParams(-1, -2));

        segmented = new SegmentedControl(c, roles, (CharSequence[]) ReplayText.SEGMENTS);
        segmented.select(1, false);
        segmented.setListener(index -> { period = index; load(); });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        content.addView(segmented, sp);

        body = Ui.column(c);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));

        GlassButton back = new GlassButton(c, roles, R.drawable.ic_back, "Back", 40);
        back.setBackdrop(scroll);
        back.setOnClickListener(v -> host.nav().dismiss());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(back.size(), back.size(), Gravity.TOP | Gravity.START);
        bp.setMarginStart(Ui.dp(c, 10));
        root.addView(back, bp);
        applyInsets();
        return root;
    }

    @Override public void onInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        if (root != null) applyInsets();
    }

    private void applyInsets() {
        View back = root.getChildAt(1);
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) back.getLayoutParams();
        bp.topMargin = insetTop + Ui.dp(context, 2);
        back.setLayoutParams(bp);
        scroll.setPadding(0, insetTop + Ui.dp(context, 52), 0, insetBottom + Ui.dp(context, 32));
    }

    @Override public void onShow() {
        visible = true;
        ListeningStats.get(context).addListener(statsChanged);
        load();
    }

    @Override public void onHide() {
        visible = false;
        ListeningStats.get(context).removeListener(statsChanged);
        generation++;
    }

    @Override public void onDestroy() {
        visible = false;
        if (context != null) ListeningStats.get(context).removeListener(statsChanged);
        generation++;
        snapshot = null;
    }

    @Override public void onThemeChanged() { if (root != null) root.setBackgroundColor(host.colors().canvas); }

    @Override public void onLibraryChanged() { if (visible && snapshot != null) render(snapshot); }

    // ---------------------------------------------------------------- loading

    private void load() {
        final int gen = ++generation;
        final int selected = period;
        final Context app = context.getApplicationContext();
        host.io().execute(() -> {
            long now = System.currentTimeMillis();
            long since = ReplayText.since(selected, now, TimeZone.getDefault(), Locale.getDefault());
            ListeningStats stats = ListeningStats.get(app);
            Snapshot s = new Snapshot();
            s.now = now;
            s.minutes = stats.totalMinutes(since);
            s.songs = stats.topTracks(100_000, since).size();
            s.artists = stats.topArtists(100_000, since).size();
            s.topArtists = stats.topArtists(TOP_ARTISTS, since);
            s.topSongs = stats.topTracks(TOP_SONGS, since);
            s.recent = stats.recent(RECENT);
            host.main().post(() -> {
                if (gen != generation || !visible) return;
                snapshot = s;
                render(s);
            });
        });
    }

    private void render(Snapshot s) {
        body.removeAllViews();
        if (s.songs == 0 && s.minutes == 0 && s.recent.isEmpty()) {
            EmptyState empty = new EmptyState(context, roles, R.drawable.ic_stats, "No listening history yet",
                    "Songs you play show up here once you have listened for 30 seconds. Nothing is estimated.");
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
            ep.setMargins(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0);
            body.addView(empty, ep);
            return;
        }
        Locale locale = Locale.getDefault();
        TextView number = Type.text(context, TypeScale.STAT, roles.textPrimary, ReplayText.number(s.minutes, locale));
        Type.tabular(number);
        number.setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), 0);
        body.addView(number, new LinearLayout.LayoutParams(-1, -2));
        TextView caption = Type.text(context, TypeScale.CALLOUT, roles.textSecondary,
                ReplayText.caption(period, s.minutes, s.now, TimeZone.getDefault(), locale));
        caption.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        body.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        String breadth = ReplayText.breadth(s.songs, s.artists, locale);
        if (!breadth.isEmpty()) {
            TextView b = Type.text(context, TypeScale.FOOTNOTE, roles.textTertiary, breadth);
            b.setPadding(Ui.dp(context, 16), Ui.dp(context, 4), Ui.dp(context, 16), 0);
            body.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }

        if (!s.topArtists.isEmpty()) {
            header("Top Artists");
            for (int i = 0; i < s.topArtists.size(); i++) {
                ListeningLedger.ArtistStat a = s.topArtists.get(i);
                body.addView(artistRow(i + 1, a, locale), new LinearLayout.LayoutParams(-1, Ui.dp(context, 56)));
            }
        }
        if (!s.topSongs.isEmpty()) {
            header("Top Songs");
            List<Track> playable = new ArrayList<>();
            for (ListeningLedger.TrackStat t : s.topSongs) { Track found = host.library().find(t.id); if (found != null) playable.add(found); }
            for (int i = 0; i < s.topSongs.size(); i++) {
                ListeningLedger.TrackStat t = s.topSongs.get(i);
                body.addView(songRow(i + 1, t, ReplayText.plays(t.plays, locale), playable), new LinearLayout.LayoutParams(-1, Ui.dp(context, 60)));
            }
        }
        if (!s.recent.isEmpty()) {
            header("Recently Played");
            List<Track> playable = new ArrayList<>();
            for (ListeningLedger.TrackStat t : s.recent) { Track found = host.library().find(t.id); if (found != null) playable.add(found); }
            for (ListeningLedger.TrackStat t : s.recent) {
                body.addView(songRow(0, t, ReplayText.when(t.lastPlayedAt, s.now, TimeZone.getDefault(), locale), playable), new LinearLayout.LayoutParams(-1, Ui.dp(context, 60)));
            }
        }
    }

    private void header(String text) {
        SectionHeader h = new SectionHeader(context, roles, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(context, 28);
        body.addView(h, lp);
    }

    private LinearLayout baseRow(int rank) {
        LinearLayout row = Ui.row(context);
        row.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        if (rank > 0) {
            TextView r = Type.text(context, TypeScale.CALLOUT, roles.textSecondary, Integer.toString(rank));
            Type.tabular(r);
            r.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(r, new LinearLayout.LayoutParams(Ui.dp(context, 28), -2));
        }
        return row;
    }

    private View artistRow(int rank, ListeningLedger.ArtistStat a, Locale locale) {
        LinearLayout row = baseRow(rank);
        ImageView avatar = new ImageView(context);
        avatar.setImageDrawable(new PlaceholderDrawable(context, roles, a.name, true));
        Ui.ovalOutline(avatar);
        row.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));
        TextView name = Type.text(context, TypeScale.ROW_TITLE, roles.textPrimary, a.name);
        Type.singleLine(name);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.setMarginStart(Ui.dp(context, 12));
        row.addView(name, np);
        TextView time = Type.text(context, TypeScale.FOOTNOTE, roles.textTertiary, ReplayText.minutes(a.msListened, locale));
        Type.tabular(time);
        row.addView(time, new LinearLayout.LayoutParams(-2, -2));
        row.setBackground(Ui.rowRipple(roles));
        row.setOnClickListener(v -> { Haptics.click(v); host.nav().dismiss(); host.openArtist(a.name); });
        return row;
    }

    private View songRow(int rank, ListeningLedger.TrackStat stat, String trailing, List<Track> playable) {
        LinearLayout row = baseRow(rank);
        ImageView art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.roundOutline(art, Ui.dpf(context, 6));
        final Track track = host.library().find(stat.id);
        if (track != null) host.artwork().load(art, track);
        else art.setImageDrawable(new PlaceholderDrawable(context, roles, stat.album.isEmpty() ? stat.title : stat.album, false));
        row.addView(art, new LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));
        LinearLayout text = Ui.column(context);
        TextView title = Type.text(context, TypeScale.ROW_TITLE, track == null ? roles.textSecondary : roles.textPrimary, stat.title.isEmpty() ? "Unknown song" : stat.title);
        Type.singleLine(title);
        TextView sub = Type.text(context, TypeScale.FOOTNOTE, roles.textSecondary, stat.artist);
        Type.singleLine(sub);
        text.addView(title, new LinearLayout.LayoutParams(-1, -2));
        text.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMarginStart(Ui.dp(context, 12));
        row.addView(text, tp);
        TextView end = Type.text(context, TypeScale.FOOTNOTE, roles.textTertiary, trailing);
        Type.tabular(end);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
        ep.setMarginStart(Ui.dp(context, 8));
        row.addView(end, ep);
        if (track != null) {
            row.setBackground(Ui.rowRipple(roles));
            row.setOnClickListener(v -> {
                int at = 0;
                for (int i = 0; i < playable.size(); i++) if (playable.get(i).id.equals(track.id)) at = i;
                Haptics.click(v);
                host.player().play(playable, at, false);
            });
        }
        return row;
    }
}
