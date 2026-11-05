package dev.lilt.player.screens;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.Tab;
import dev.lilt.player.screens.discover.DiscoverScreen;
import dev.lilt.player.screens.discover.DiscoverText;
import dev.lilt.player.screens.discover.QueryGate;
import dev.lilt.player.screens.discover.Rows;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.SegmentedControl;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trending songs in one genre (or across the whole catalogue when {@code genre} is null, which is
 * Browse's "See all" chart) with a This Week / This Month / All Time control. Each window is
 * fetched once and kept while the screen lives.
 */
public final class GenreScreen extends DiscoverScreen {
    private static final int CHART_SIZE = 50;

    private final String genre;
    private final QueryGate gate = new QueryGate();
    private final Map<String, List<Track>> charts = new HashMap<>();
    private final Map<String, Boolean> failed = new HashMap<>();
    private int window;
    private SegmentedControl segments;

    public GenreScreen(String genre) { this.genre = genre; }

    @Override public String key() { return "genre:" + genre; }
    @Override public Tab tab() { return Tab.BROWSE; }

    private String windowKey() { return DiscoverText.WINDOWS[window]; }

    @Override protected void onReady() {
        segments = new SegmentedControl(ctx, roles, DiscoverText.WINDOW_LABELS);
        segments.setListener(i -> { window = i; load(); });
        load();
    }

    @Override protected void onConnectivity(boolean nowOnline) {
        if (nowOnline && !charts.containsKey(windowKey())) load();
        else render();
    }

    private void load() {
        String key = windowKey();
        if (charts.containsKey(key)) { render(); return; }
        if (!online) { failed.put(key, true); render(); return; }
        failed.remove(key);
        int gen = gate.begin(key);
        render();
        work(gate, gen, () -> AudiusClient.get(ctx).trending(genre, key, CHART_SIZE), found -> {
            charts.put(key, found);
            render();
        }, e -> { failed.put(key, true); render(); });
    }

    @Override protected List<Rows.Item> build() {
        List<Rows.Item> out = new ArrayList<>();
        out.add(title(genre == null ? "Trending" : DiscoverText.genre(genre)));
        if (segments != null) {
            if (segments.getParent() instanceof ViewGroup) ((ViewGroup) segments.getParent()).removeView(segments);
            FrameLayout wrap = new FrameLayout(ctx);
            wrap.setPadding(dp(16), 0, dp(16), dp(4));
            wrap.addView(segments, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            out.add(Rows.of(wrap));
        }
        List<Track> tracks = charts.get(windowKey());
        if (tracks == null) {
            if (failed.containsKey(windowKey())) {
                out.add(online
                        ? stateBlock(R.drawable.ic_alert, "Couldn’t load this chart", "The catalogue didn’t answer. Try again in a moment.", "Try Again", PillButton.SECONDARY, this::load)
                        : stateBlock(R.drawable.ic_cloud_off, "You’re offline", "Charts need a connection. Your library and saved songs still work.", null, 0, null));
            } else {
                out.add(spacer(8));
                out.addAll(skeletonSongs(8));
            }
            return out;
        }
        if (tracks.isEmpty()) {
            out.add(stateBlock(R.drawable.ic_song, "Nothing charting yet", "No songs in this genre for " + DiscoverText.WINDOW_LABELS[window].toLowerCase() + ".", null, 0, null));
            return out;
        }
        out.add(playRow(tracks));
        for (int i = 0; i < tracks.size(); i++) out.add(song(tracks.get(i), tracks, i).rank(i + 1));
        return out;
    }

    private Rows.Item playRow(List<Track> tracks) {
        LinearLayout row = Ui.row(ctx);
        row.setPadding(dp(16), dp(8), dp(16), dp(12));
        PillButton play = new PillButton(ctx, roles, PillButton.PRIMARY, "Play", R.drawable.ic_play);
        play.setOnClickListener(v -> play(tracks, 0, false));
        PillButton shuffle = new PillButton(ctx, roles, PillButton.SECONDARY, "Shuffle", R.drawable.ic_shuffle);
        shuffle.setOnClickListener(v -> play(tracks, 0, true));
        row.addView(play, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sp.setMarginStart(dp(8));
        row.addView(shuffle, sp);
        return Rows.of(row);
    }
}
