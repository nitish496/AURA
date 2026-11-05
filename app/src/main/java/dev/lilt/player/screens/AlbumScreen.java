package dev.lilt.player.screens;

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
import dev.lilt.player.app.LibraryGrouping;
import dev.lilt.player.app.MenuAction;
import dev.lilt.player.app.Tab;
import dev.lilt.player.app.chrome.TopBar;
import dev.lilt.player.screens.library.Kit;
import dev.lilt.player.screens.library.ListScreen;
import dev.lilt.player.ui.EmptyState;
import dev.lilt.player.ui.PlaceholderDrawable;
import dev.lilt.player.ui.TimeText;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Album detail: edge-to-edge artwork, title block, Play / Shuffle, numbered track list and a duration footer. */
public final class AlbumScreen extends ListScreen {
    private final String albumKey;
    private FrameLayout hero;
    private ImageView heroImage;
    private LinearLayout titleBlock;
    private TextView title, artist, meta;
    private int heroSize;
    private boolean heroUnderBars = true;
    private View pills;

    public AlbumScreen(String albumKey) { this.albumKey = albumKey; }

    @Override public String key() { return "album:" + albumKey; }
    @Override public Tab tab() { return Tab.LIBRARY; }
    @Override public boolean lightSystemBars() { return heroUnderBars && album() != null; }

    private AlbumGroup album() { return host.library().album(albumKey); }

    @Override protected CharSequence barTitle() { AlbumGroup a = album(); return a == null ? "Album" : a.title; }

    @Override protected void buildTopBar(TopBar bar) {
        bar.setOnArtwork(true);
        bar.addLeft(R.drawable.ic_back, "Back", () -> host.nav().pop());
        bar.addRight(R.drawable.ic_more, "More", () -> menu(bar));
    }

    private void menu(View anchor) {
        AlbumGroup a = album();
        if (a == null) return;
        List<MenuAction> extra = new ArrayList<>();
        Track cover = a.cover();
        if (cover != null && !cover.isRemote()) {
            extra.add(new MenuAction("Choose Cover…", "edit", () -> host.chooseCover(cover)));
            if (((dev.lilt.player.app.LibraryRepoImpl) host.library()).hasCustomCover(cover)) extra.add(new MenuAction("Reset Cover", "refresh", () -> host.resetCover(cover)));
        }
        host.showCollectionMenu(a.title, a.tracks, anchor, extra);
    }

    @Override protected View buildHeader() {
        int width = Ui.dp(context, host.contentWidthDp());
        heroSize = width;
        LinearLayout column = Ui.column(context);
        hero = new FrameLayout(context);
        heroImage = new ImageView(context);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackgroundColor(roles.placeholder);
        hero.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x61000000, 0x00000000}));
        hero.addView(scrim, new FrameLayout.LayoutParams(-1, Ui.dp(context, 128), Gravity.TOP));
        column.addView(hero, new LinearLayout.LayoutParams(-1, width));
        titleBlock = Ui.column(context);
        titleBlock.setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 4));
        title = Type.text(context, TypeScale.TITLE1, roles.textPrimary);
        Type.maxLines(title, 2);
        artist = Type.text(context, TypeScale.TITLE3.weight(500), roles.textSecondary);
        Type.singleLine(artist);
        meta = Kit.footnote(context, roles, "");
        titleBlock.addView(title);
        titleBlock.addView(artist);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
        mp.topMargin = Ui.dp(context, 4);
        titleBlock.addView(meta, mp);
        pills = Kit.pills(context, roles, () -> playAll(false), () -> playAll(true));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = Ui.dp(context, 16);
        pp.bottomMargin = Ui.dp(context, 12);
        titleBlock.addView(pills, pp);
        column.addView(titleBlock);
        titleBottomPx = width + Ui.dp(context, 16) + Ui.dp(context, 34);
        artist.setOnClickListener(v -> { AlbumGroup a = album(); if (a != null && host.library().artist(a.artist) != null) host.openArtist(a.artist); });
        return column;
    }

    @Override protected float collapseProgress(int scrollY) {
        return dev.lilt.player.ui.ScrollBehavior.collapseProgress(scrollY, titleBottomPx, insetTop, Ui.dp(context, 16));
    }

    @Override protected void onScrolled(int y) {
        boolean under = y < heroSize - insetTop;
        if (under != heroUnderBars) { heroUnderBars = under; host.invalidateSystemBars(); }
    }

    @Override protected List<Object> buildItems() {
        AlbumGroup a = album();
        List<Object> items = new ArrayList<>();
        if (a == null) {
            title.setText("Album");
            artist.setText("");
            meta.setText("");
            pills.setVisibility(View.GONE);
            hero.setVisibility(View.GONE);
            EmptyState e = new EmptyState(context, roles, R.drawable.ic_album, "This album isn’t in your library",
                    "Its songs may have been moved or deleted.");
            e.setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0);
            items.add(e);
            return items;
        }
        hero.setVisibility(View.VISIBLE);
        pills.setVisibility(View.VISIBLE);
        title.setText(a.title);
        artist.setText(a.artist);
        boolean anyRemote = false, anyLocal = false;
        for (Track t : a.tracks) { if (t.isRemote()) anyRemote = true; else anyLocal = true; }
        String where = anyLocal && anyRemote ? "On this phone and saved" : anyRemote ? "Saved from Audius" : "On this phone";
        meta.setText(TimeText.duration(a.durationMs()) + TimeText.DOT + where);
        Track cover = a.cover();
        if (cover != null) host.artwork().load(heroImage, cover);
        else heroImage.setImageDrawable(new PlaceholderDrawable(context, roles, a.title, false));
        int n = 1;
        for (Track t : a.tracks) items.add(new TrackRow(t, n++, TimeText.clock(t.duration)));
        items.add(Kit.footer(context, roles, TimeText.summary(a.tracks.size(), a.durationMs(), Locale.getDefault())));
        return items;
    }

    @Override protected boolean showArtwork() { return false; }

    @Override protected String rowSubtitle(Track t) {
        AlbumGroup a = album();
        if (a != null && (a.artist.equals(LibraryGrouping.VARIOUS) || !t.artist.equalsIgnoreCase(a.artist))) return t.artist;
        return "";
    }
}
