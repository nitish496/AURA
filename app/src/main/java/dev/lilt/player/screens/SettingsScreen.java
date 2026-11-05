package dev.lilt.player.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import dev.lilt.player.PlaybackService;
import dev.lilt.player.R;
import dev.lilt.player.app.AppHost;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;
import dev.lilt.player.screens.player.SessionLink;
import dev.lilt.player.screens.player.StorageText;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassButton;
import dev.lilt.player.ui.GroupedSection;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.ListRow;
import dev.lilt.player.ui.SegmentedControl;
import dev.lilt.player.ui.Theme;
import dev.lilt.player.ui.Toggle;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Settings: appearance, library, playback shortcuts, storage, privacy and about (version and
 * open-source licences). The streaming-catalogue switch is stored in the {@code aura_ui}
 * preferences under {@link #PREF_SHOW_CATALOGUE} (default true) for Home and Browse to read.
 */
@OptIn(markerClass = UnstableApi.class)
public final class SettingsScreen extends Screen {
    /** SharedPreferences("aura_ui") key: show the streaming catalogue in Home, Browse and Search. */
    public static final String PREF_SHOW_CATALOGUE = "show_catalogue";

    private Context context;
    private ColorRoles roles;
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private int insetTop, insetBottom;
    private boolean visible;
    private SessionLink link;
    private ListRow musicRow, rescanRow, cacheRow, interRow, mediaRow;
    private TextView interText, mediaText;
    private boolean interLoaded;

    public SettingsScreen() { }

    @Override public String key() { return "settings"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override public boolean fullScreen() { return true; }

    /** True unless the user hid the streaming catalogue in Settings. */
    public static boolean showCatalogue(Context c) {
        return prefs(c).getBoolean(PREF_SHOW_CATALOGUE, true);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("aura_ui", Context.MODE_PRIVATE);
    }

    @Override protected View onCreateView(Context c) {
        context = c;
        roles = host.colors();
        link = SessionLink.get(c);
        root = new FrameLayout(c);
        root.setBackgroundColor(roles.canvas);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        content = Ui.column(c);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView title = Type.text(c, TypeScale.LARGE_TITLE, roles.textPrimary, "Settings");
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        title.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        buildAppearance();
        buildLibrary();
        buildPlayback();
        buildStorage();
        buildPrivacy();
        buildAbout();

        GlassButton back = new GlassButton(c, roles, R.drawable.ic_back, "Back", 40);
        back.setBackdrop(scroll);
        back.setOnClickListener(v -> host.nav().dismiss());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(back.size(), back.size(), Gravity.TOP | Gravity.START);
        bp.setMarginStart(Ui.dp(c, 10));
        root.addView(back, bp);
        applyInsets();
        return root;
    }

    private void buildAppearance() {
        GroupedSection group = new GroupedSection(context, roles, "Appearance");
        LinearLayout block = Ui.column(context);
        block.setPadding(Ui.dp(context, 16), Ui.dp(context, 6), Ui.dp(context, 16), Ui.dp(context, 6));
        SegmentedControl mode = new SegmentedControl(context, roles, "System", "Dark", "Light");
        int current = host.themeMode();
        mode.select(current == AppHost.THEME_DARK ? 1 : current == AppHost.THEME_LIGHT ? 2 : 0, false);
        mode.setListener(index -> host.setThemeMode(index == 1 ? AppHost.THEME_DARK : index == 2 ? AppHost.THEME_LIGHT : AppHost.THEME_SYSTEM));
        block.addView(mode, new LinearLayout.LayoutParams(-1, -2));
        group.add(block);
        ListRow reduce = group.add(new ListRow(context, roles).title("Reduce transparency").subtitle("Use solid surfaces instead of blurred glass"));
        Toggle t = reduce.toggle(Theme.reduceTransparency(context));
        t.setListener((toggle, on) -> { Theme.setReduceTransparency(context, on); host.toast(on ? "Solid surfaces on" : "Glass blur on"); });
        content.addView(group, section());
    }

    private void buildLibrary() {
        GroupedSection group = new GroupedSection(context, roles, "Library");
        musicRow = group.add(new ListRow(context, roles).title("Music on this phone"));
        musicRow.setOnClickListener(v -> { if (!host.library().hasPermission()) host.requestMediaPermission(); });
        rescanRow = group.add(new ListRow(context, roles).title("Rescan library").subtitle("Look for new, changed and removed files"));
        rescanRow.setOnClickListener(v -> {
            if (!host.library().hasPermission()) { host.requestMediaPermission(); return; }
            Haptics.click(v);
            host.library().refresh();
            host.toast("Scanning your music");
            updateLibrary();
        });
        ListRow catalogue = group.add(new ListRow(context, roles).title("Show streaming catalogue").subtitle("Audius songs in Home, Browse and Search"));
        Toggle t = catalogue.toggle(showCatalogue(context));
        t.setListener((toggle, on) -> {
            prefs(context).edit().putBoolean(PREF_SHOW_CATALOGUE, on).apply();
            host.toast(on ? "Streaming catalogue shown" : "Streaming catalogue hidden");
            host.library().refresh();
        });
        content.addView(group, section());
    }

    private void buildPlayback() {
        GroupedSection group = new GroupedSection(context, roles, "Playback");
        ListRow sound = group.add(new ListRow(context, roles).title("Sound").subtitle("Equaliser, speed, crossfade, sleep timer").accessory(ListRow.CHEVRON));
        sound.setOnClickListener(v -> host.nav().present(new SoundScreen()));
        ListRow replay = group.add(new ListRow(context, roles).title("Replay").subtitle("What you listened to").accessory(ListRow.CHEVRON));
        replay.setOnClickListener(v -> host.nav().present(new ReplayScreen()));
        content.addView(group, section());
    }

    private void buildStorage() {
        GroupedSection group = new GroupedSection(context, roles, "Storage");
        cacheRow = group.add(new ListRow(context, roles).title("Stream cache").subtitle("Songs you streamed, kept for offline listening"));
        ListRow clear = group.add(new ListRow(context, roles).title("Clear stream cache"));
        clear.titleView().setTextColor(roles.accent);
        clear.setOnClickListener(v -> {
            link.send(PlaybackService.CLEAR_CACHE, null, b -> {
                if (b == null) { host.toast("Couldn't clear the cache"); return; }
                Haptics.confirm(v);
                host.toast("Stream cache cleared");
                showCache(b);
            });
        });
        content.addView(group, section());
    }

    private void buildPrivacy() {
        GroupedSection group = new GroupedSection(context, roles, "Privacy");
        TextView text = Type.text(context, TypeScale.CALLOUT, roles.textSecondary,
                "AURA uses the network only to browse and stream the Audius catalogue and to look up lyrics on LRCLIB, "
                        + "which receives a song's title, artist, album and length. Songs with lyrics in the file never leave your phone. "
                        + "There is no account, advertising or analytics, and your library and listening history stay on this phone.");
        text.setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14));
        group.add(text);
        content.addView(group, section());
    }

    private void buildAbout() {
        GroupedSection group = new GroupedSection(context, roles, "About");
        String version = "";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName == null ? "" : info.versionName;
        } catch (Exception unavailable) { /* leave blank */ }
        group.add(new ListRow(context, roles).title("Version").value(version));
        interRow = group.add(new ListRow(context, roles).title("Inter typeface").subtitle("SIL Open Font License 1.1").accessory(ListRow.CHEVRON));
        interText = licenceText();
        group.add(interText);
        interRow.setOnClickListener(v -> toggleInter());
        mediaRow = group.add(new ListRow(context, roles).title("AndroidX Media3").subtitle("Apache License 2.0").accessory(ListRow.CHEVRON));
        mediaText = licenceText();
        mediaText.setText("Copyright The Android Open Source Project. Licensed under the Apache License, Version 2.0; you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0. "
                + "The software is distributed on an \"AS IS\" basis, without warranties or conditions of any kind.");
        group.add(mediaText);
        mediaRow.setOnClickListener(v -> mediaText.setVisibility(mediaText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        group.add(new ListRow(context, roles).title("Catalogue and lyrics").subtitle("Audius public API and LRCLIB"));
        content.addView(group, section());
    }

    private TextView licenceText() {
        TextView t = Type.text(context, TypeScale.CAPTION1, roles.textSecondary);
        t.setPadding(Ui.dp(context, 16), Ui.dp(context, 4), Ui.dp(context, 16), Ui.dp(context, 14));
        t.setVisibility(View.GONE);
        return t;
    }

    private void toggleInter() {
        if (interText.getVisibility() == View.VISIBLE) { interText.setVisibility(View.GONE); return; }
        interText.setVisibility(View.VISIBLE);
        if (interLoaded) return;
        interText.setText("Loading");
        host.io().execute(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.license_inter_ofl), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && sb.length() < 20_000) sb.append(line).append('\n');
            } catch (Exception failed) {
                sb.setLength(0);
                sb.append("The Inter typeface is licensed under the SIL Open Font License, Version 1.1 (https://openfontlicense.org).");
            }
            final String text = sb.toString().trim();
            host.main().post(() -> { if (interText != null) { interText.setText(text); interLoaded = true; } });
        });
    }

    private LinearLayout.LayoutParams section() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(context, 12);
        return lp;
    }

    // ---------------------------------------------------------------- insets / lifecycle

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
        link.acquire();
        updateLibrary();
        link.send(PlaybackService.GET_CACHE_INFO, null, this::showCache);
    }

    @Override public void onHide() {
        visible = false;
        link.release();
    }

    @Override public void onLibraryChanged() { if (visible && root != null) updateLibrary(); }

    @Override public void onThemeChanged() { if (root != null) root.setBackgroundColor(host.colors().canvas); }

    private void updateLibrary() {
        if (!host.library().hasPermission()) {
            musicRow.title("Allow access to your music").subtitle("AURA plays the audio files already on this phone").accessory(ListRow.CHEVRON).value(null);
        } else {
            int n = host.library().localTracks().size();
            musicRow.title("Music on this phone").subtitle(null).accessory(ListRow.NONE).value(n + (n == 1 ? " song" : " songs"));
        }
        rescanRow.value(host.library().loading() ? "Scanning" : null);
    }

    private void showCache(Bundle b) {
        if (b == null || cacheRow == null) return;
        cacheRow.subtitle(StorageText.usage(b.getLong("cacheBytes", 0), b.getLong("cacheLimitBytes", 0)));
    }
}
