package dev.lilt.player.screens;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.MenuAction;
import dev.lilt.player.app.SectionIndex;
import dev.lilt.player.app.Tab;
import dev.lilt.player.app.chrome.IndexBar;
import dev.lilt.player.app.chrome.TopBar;
import dev.lilt.player.screens.library.Kit;
import dev.lilt.player.screens.library.ListScreen;
import dev.lilt.player.ui.EmptyState;
import dev.lilt.player.ui.TimeText;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A flat list of songs: all songs (sortable, with the A–Z index), favourites, recently played,
 * most played, saved from Audius, recently added, or one mood's songs.
 */
public final class SongsScreen extends ListScreen {
    /** songs | favourites | recent | most | saved | added | mood:&lt;Mood&gt; */
    private final String mode;
    private String sort = "title";
    private IndexBar indexBar;
    private SectionIndex sections;
    private LinearLayout header;
    private TextView subtitle;

    public SongsScreen(String mode) { this.mode = mode == null ? "songs" : mode; }

    @Override public String key() { return "songs:" + mode; }
    @Override public Tab tab() { return Tab.LIBRARY; }

    private String title() {
        switch (mode) {
            case "favourites": return "Favourites";
            case "recent": return "Recently Played";
            case "most": return "Most Played";
            case "saved": return "Saved from Audius";
            case "added": return "Recently Added";
            case "songs": return "Songs";
            default: return mode.startsWith("mood:") ? mode.substring(5) + " Mix" : "Songs";
        }
    }

    @Override protected CharSequence barTitle() { return title(); }

    @Override protected void buildTopBar(TopBar bar) {
        bar.addLeft(R.drawable.ic_back, "Back", () -> host.nav().pop());
        if (mode.equals("songs")) bar.addRight(R.drawable.ic_sort, "Sort", () -> sortMenu(bar));
        if (mode.equals("recent")) bar.addRight(R.drawable.ic_trash, "Clear history", () ->
                host.confirm("Clear Recently Played?", "Your songs and files stay on the phone.", "Clear", true, () -> host.library().clearHistory()));
    }

    private void sortMenu(View anchor) {
        List<MenuAction> a = new ArrayList<>();
        a.add(new MenuAction("Title", sort.equals("title") ? "check" : null, () -> setSort("title")));
        a.add(new MenuAction("Artist", sort.equals("artist") ? "check" : null, () -> setSort("artist")));
        a.add(new MenuAction("Recently Added", sort.equals("added") ? "check" : null, () -> setSort("added")));
        host.showMenu(anchor, a);
    }

    private void setSort(String s) { sort = s; refreshItems(); if (list != null) list.setSelection(0); }

    @Override protected View buildHeader() {
        header = Ui.column(context);
        TextView title = Kit.largeTitle(context, roles, title());
        header.addView(title);
        subtitle = Kit.footnote(context, roles, "");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = Ui.dp(context, 2);
        header.addView(subtitle, sp);
        LinearLayout pills = Kit.pills(context, roles, () -> playAll(false), () -> playAll(true));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = Ui.dp(context, 16);
        pp.bottomMargin = Ui.dp(context, 8);
        pills.setTag("pills");
        header.addView(pills, pp);
        header.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        applyHeaderInsets();
        return header;
    }

    private void applyHeaderInsets() {
        if (header == null) return;
        header.setPadding(Ui.dp(context, 16), insetTop + Ui.dp(context, 8), Ui.dp(context, 16), 0);
        titleBottomPx = insetTop + Ui.dp(context, 8) + Ui.dp(context, 40);
    }

    @Override protected void onInsetsChanged() { applyHeaderInsets(); }

    private List<Track> source() {
        List<Track> t;
        switch (mode) {
            case "favourites": t = host.library().favourites(); break;
            case "recent": t = host.library().recentlyPlayed(50); break;
            case "most": t = host.library().mostPlayed(100); break;
            case "saved": t = host.library().saved().tracks(); break;
            case "added": t = host.library().recentlyAdded(200); break;
            case "songs": t = new ArrayList<>(host.library().allTracks()); break;
            default:
                if (mode.startsWith("mood:")) t = host.library().moodTracks(mode.substring(5));
                else t = new ArrayList<>(host.library().allTracks());
        }
        return new ArrayList<>(t);
    }

    @Override protected List<Object> buildItems() {
        List<Track> tracks = source();
        if (mode.equals("songs")) {
            if (sort.equals("artist")) SectionIndex.sort(tracks, t -> t.artist);
            else if (sort.equals("added")) tracks.sort((a, b) -> Long.compare(b.dateAdded, a.dateAdded));
        }
        long total = 0;
        for (Track t : tracks) total += Math.max(0, t.duration);
        String count = TimeText.songs(tracks.size(), Locale.getDefault());
        String order = mode.equals("songs") ? " · sorted by " + (sort.equals("added") ? "date added" : sort) : "";
        if (subtitle != null) subtitle.setText(tracks.isEmpty() ? "" : count + (total > 0 && !mode.equals("songs") ? " · " + TimeText.duration(total) : order));
        View pills = header == null ? null : header.findViewWithTag("pills");
        if (pills != null) pills.setVisibility(tracks.isEmpty() ? View.GONE : View.VISIBLE);

        List<Object> items = new ArrayList<>(tracks.size() + 1);
        if (tracks.isEmpty()) {
            items.add(empty());
        } else {
            for (Track t : tracks) items.add(new TrackRow(t, 0, null));
            items.add(Kit.spacer(context, 8));
        }
        boolean alphabetical = mode.equals("songs") && !sort.equals("added") && tracks.size() >= 30;
        List<String> keys = new ArrayList<>(tracks.size());
        if (alphabetical) for (Track t : tracks) keys.add(sort.equals("artist") ? t.artist : t.title);
        sections = alphabetical ? new SectionIndex(keys) : null;
        updateIndexBar();
        return items;
    }

    private View empty() {
        EmptyState e;
        switch (mode) {
            case "favourites":
                e = new EmptyState(context, roles, R.drawable.ic_heart, "No favourites yet", "Choose Favourite in a song’s menu, or tap the heart in Now Playing.");
                break;
            case "recent":
                e = new EmptyState(context, roles, R.drawable.ic_clock, "Nothing played yet", "Songs you play show up here.");
                break;
            case "most":
                e = new EmptyState(context, roles, R.drawable.ic_stats, "No plays counted yet", "A song counts as played after 30 seconds.");
                break;
            case "saved":
                e = new EmptyState(context, roles, R.drawable.ic_cloud_check, "No saved songs", "Save songs from Browse. They stream on demand and play offline once heard.")
                        .action(dev.lilt.player.ui.PillButton.SECONDARY, "Browse", R.drawable.ic_browse, () -> host.nav().switchTab(Tab.BROWSE));
                break;
            case "added":
                e = new EmptyState(context, roles, R.drawable.ic_song, "No songs yet", "Songs you add to this phone or save from Browse show up here.");
                break;
            default:
                if (mode.startsWith("mood:")) e = new EmptyState(context, roles, R.drawable.ic_waveform, "No songs tagged " + mode.substring(5),
                        "Choose Moods… in a song’s menu to add it to this mix.");
                else if (!host.library().hasPermission()) e = EmptyState.permission(context, host::requestMediaPermission, () -> host.nav().switchTab(Tab.BROWSE));
                else e = EmptyState.noMusic(context, () -> host.library().refresh(), () -> host.nav().switchTab(Tab.BROWSE));
        }
        e.setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0);
        return e;
    }

    private void updateIndexBar() {
        if (frame == null) return;
        if (sections == null) {
            if (indexBar != null) { indexBar.setVisibility(View.GONE); list.setPadding(0, 0, 0, insetBottom); }
            return;
        }
        if (indexBar == null) {
            indexBar = new IndexBar(context, roles);
            indexBar.setListener(section -> {
                int pos = sections.positionForSection(section);
                list.setSelectionFromTop(list.getHeaderViewsCount() + pos, insetTop);
            });
            frame.addView(indexBar, new FrameLayout.LayoutParams(Ui.dp(context, 24), -1, Gravity.END));
        }
        indexBar.setVisibility(View.VISIBLE);
        indexBar.setSections(sections);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indexBar.getLayoutParams();
        lp.topMargin = insetTop + Ui.dp(context, 8);
        lp.bottomMargin = insetBottom;
        indexBar.setLayoutParams(lp);
        list.setPadding(0, 0, Ui.dp(context, 14), insetBottom);
    }

    @Override public void onInsets(int top, int bottom) {
        super.onInsets(top, bottom);
        if (sections != null) updateIndexBar();
    }

    @Override protected void onScrolled(int y) {
        if (sections == null || indexBar == null) return;
        int first = list.getFirstVisiblePosition() - list.getHeaderViewsCount();
        if (first >= 0) indexBar.setCurrent(sections.sectionForPosition(first));
        else indexBar.setCurrent(-1);
    }

    @Override public Bundle saveState() {
        Bundle b = super.saveState();
        b.putString("sort", sort);
        return b;
    }

    @Override public void restoreState(Bundle state) {
        if (state != null && state.getString("sort") != null && mode.equals("songs")) { sort = state.getString("sort"); refreshItems(); }
        super.restoreState(state);
    }
}
