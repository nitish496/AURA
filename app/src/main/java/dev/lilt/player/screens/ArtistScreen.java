package dev.lilt.player.screens;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.app.AlbumGroup;
import dev.lilt.player.app.ArtistGroup;
import dev.lilt.player.app.SectionIndex;
import dev.lilt.player.app.Tab;
import dev.lilt.player.app.chrome.TopBar;
import dev.lilt.player.screens.library.Kit;
import dev.lilt.player.screens.library.ListScreen;
import dev.lilt.player.ui.ArtworkColors;
import dev.lilt.player.ui.ColorMath;
import dev.lilt.player.ui.EmptyState;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Initials;
import dev.lilt.player.ui.TimeText;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A local artist: a hero tinted from their artwork with the name and one Play button, then Top
 * Songs (by plays), Albums and the rest of their songs.
 */
public final class ArtistScreen extends ListScreen {
    private final String artistName;
    private FrameLayout hero;
    private TextView initials, name, meta;
    private View playButton;
    private int heroHeight;
    private boolean heroUnderBars = true;
    private String tintedFor = "";

    public ArtistScreen(String artistName) { this.artistName = artistName; }

    @Override public String key() { return "artist:" + artistName; }
    @Override public Tab tab() { return Tab.LIBRARY; }
    @Override public boolean lightSystemBars() { return heroUnderBars; }

    private ArtistGroup artist() { return host.library().artist(artistName); }

    @Override protected CharSequence barTitle() { return artistName; }

    @Override protected void buildTopBar(TopBar bar) {
        bar.setOnArtwork(true);
        bar.addLeft(R.drawable.ic_back, "Back", () -> host.nav().pop());
        bar.addRight(R.drawable.ic_more, "More", () -> {
            ArtistGroup a = artist();
            if (a != null) host.showCollectionMenu(a.name, a.tracks, bar);
        });
    }

    @Override protected View buildHeader() {
        heroHeight = Ui.dp(context, 320);
        hero = new FrameLayout(context);
        hero.setBackgroundColor(roles.dark ? 0xFF1C1C1F : 0xFF2A2A2D);
        initials = Type.text(context, TypeScale.STAT, 0x14FFFFFF);
        initials.setTextSize(300);
        initials.setIncludeFontPadding(false);
        initials.setLetterSpacing(-0.04f);
        initials.setSingleLine(true);
        initials.setTranslationX(-Ui.dp(context, 12));
        initials.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(initials, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0xD90B0B0C}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, Ui.dp(context, 180), Gravity.BOTTOM));
        View top = new View(context);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x61000000, 0x00000000}));
        hero.addView(top, new FrameLayout.LayoutParams(-1, Ui.dp(context, 128), Gravity.TOP));
        LinearLayout text = Ui.column(context);
        name = Type.text(context, TypeScale.LARGE_TITLE, 0xFFF5F5F7);
        Type.maxLines(name, 2);
        meta = Type.text(context, TypeScale.FOOTNOTE, 0xB8F5F5F7);
        text.addView(name);
        text.addView(meta);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        tp.setMargins(Ui.dp(context, 16), 0, Ui.dp(context, 88), Ui.dp(context, 20));
        hero.addView(text, tp);
        ImageView fab = new ImageView(context);
        fab.setScaleType(ImageView.ScaleType.CENTER);
        fab.setBackground(Ui.oval(roles.accent));
        fab.setImageDrawable(Icons.get(context, R.drawable.ic_play, roles.onAccent));
        fab.setContentDescription("Play " + artistName);
        fab.setElevation(Ui.dpf(context, 4));
        fab.setOnClickListener(v -> { Haptics.click(v); playAll(false); });
        playButton = fab;
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(context, 56), Ui.dp(context, 56), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.dp(context, 16), Ui.dp(context, 20));
        hero.addView(fab, fp);
        hero.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, heroHeight));
        titleBottomPx = heroHeight - Ui.dp(context, 20);
        return hero;
    }

    @Override protected float collapseProgress(int scrollY) {
        return dev.lilt.player.ui.ScrollBehavior.collapseProgress(scrollY, heroHeight - Ui.dp(context, 32), insetTop, Ui.dp(context, 16));
    }

    @Override protected void onScrolled(int y) {
        boolean under = y < heroHeight - insetTop;
        if (under != heroUnderBars) { heroUnderBars = under; host.invalidateSystemBars(); }
    }

    @Override protected List<Object> buildItems() {
        ArtistGroup a = artist();
        List<Object> items = new ArrayList<>();
        if (a == null) {
            name.setText(artistName);
            meta.setText("");
            initials.setText(Initials.of(artistName));
            playButton.setVisibility(View.GONE);
            EmptyState e = new EmptyState(context, roles, R.drawable.ic_artist, "No songs by " + artistName, "Their songs may have been moved or deleted.");
            e.setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0);
            items.add(e);
            return items;
        }
        playButton.setVisibility(View.VISIBLE);
        name.setText(a.name);
        initials.setText(Initials.of(a.name));
        meta.setText(TimeText.songs(a.tracks.size(), Locale.getDefault()) + (a.albums.isEmpty() ? ""
                : TimeText.DOT + a.albums.size() + (a.albums.size() == 1 ? " album" : " albums")));
        tint(a);

        // Top songs by play count, then the remainder alphabetically.
        Map<String, Integer> rank = new HashMap<>();
        List<Track> played = host.library().mostPlayed(100);
        for (int i = 0; i < played.size(); i++) rank.put(played.get(i).id, i);
        List<Track> ordered = new ArrayList<>(a.tracks);
        ordered.sort((x, y) -> {
            Integer rx = rank.get(x.id), ry = rank.get(y.id);
            if (rx != null && ry != null) return Integer.compare(rx, ry);
            if (rx != null) return -1;
            if (ry != null) return 1;
            return SectionIndex.ORDER.compare(x.title, y.title);
        });
        int top = Math.min(5, ordered.size());
        items.add(Kit.section(context, roles, "Top Songs", null));
        for (int i = 0; i < top; i++) items.add(new TrackRow(ordered.get(i), 0, null));
        if (!a.albums.isEmpty()) {
            items.add(Kit.section(context, roles, "Albums", null));
            items.add(Kit.albumShelf(host, new ArrayList<AlbumGroup>(a.albums)));
        }
        if (ordered.size() > top) {
            items.add(Kit.section(context, roles, "All Songs", null));
            for (int i = top; i < ordered.size(); i++) items.add(new TrackRow(ordered.get(i), 0, null));
        }
        items.add(Kit.spacer(context, 8));
        return items;
    }

    @Override protected String rowSubtitle(Track t) { return t.album; }

    /** Tints the hero from the artist's first artwork (graphite when there is none). */
    private void tint(ArtistGroup a) {
        Track cover = a.cover();
        if (cover == null || tintedFor.equals(cover.id)) return;
        tintedFor = cover.id;
        final String id = cover.id;
        host.artwork().loadActual(cover.uri, ((dev.lilt.player.app.LibraryRepoImpl) host.library()).customCover(cover), bitmap -> {
            if (bitmap == null || hero == null || !id.equals(tintedFor)) return;
            ArtworkColors.paletteAsync(bitmap, host.io(), p -> host.main().post(() -> {
                if (hero == null || !id.equals(tintedFor) || !p.fromArtwork) return;
                hero.setBackgroundColor(p.background);
                initials.setTextColor(ColorMath.withAlpha(0xFFFFFFFF, 0.07f));
            }));
        });
    }
}
