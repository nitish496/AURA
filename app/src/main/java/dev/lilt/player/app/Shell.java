package dev.lilt.player.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import dev.lilt.player.Artwork;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.Track;
import dev.lilt.player.app.chrome.FloatingChrome;
import dev.lilt.player.app.chrome.GlassDialog;
import dev.lilt.player.app.chrome.MenuPopover;
import dev.lilt.player.app.chrome.StartupViews;
import dev.lilt.player.app.chrome.Toaster;
import dev.lilt.player.screens.BrowseScreen;
import dev.lilt.player.screens.HomeScreen;
import dev.lilt.player.screens.LibraryScreen;
import dev.lilt.player.screens.SearchScreen;
import dev.lilt.player.stream.AudiusClient;
import dev.lilt.player.stream.CatalogPlaylist;
import dev.lilt.player.ui.BottomSheet;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.EdgeToEdge;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.ScrollBehavior;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.Theme;
import dev.lilt.player.ui.Ui;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The application shell: owns the window layers, per-tab back stacks, presentations, the floating
 * glass chrome, menus and overlays, the library repository and the player hub. It implements both
 * {@link AppHost} (services for screens) and {@link Navigator}.
 *
 * <pre>
 * column (max 840dp, centred)
 *   content        per-tab stacks; the blur backdrop of the chrome
 *   chrome         FloatingChrome: tab bar, Search control, mini player
 *   gate           startup mark / first-run permission screen
 * presentLayer     full-width, full-screen presentations (Now Playing, Lyrics, Queue, Sound, Replay, Settings)
 * overlay          full-width menus, sheets, dialogs, toasts
 * </pre>
 */
public final class Shell implements AppHost, Navigator {
    static final int REQ_MEDIA = 10, REQ_NOTIFICATIONS = 11, REQ_COVER = 12;
    private static final int MAX_STATE_BYTES = 24 * 1024;

    /** One screen with the wrapper that gives it an opaque canvas during transitions. */
    private static final class Entry {
        final Screen screen;
        final FrameLayout wrapper;
        int top = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        boolean shown;
        Entry(Screen screen, FrameLayout wrapper) { this.screen = screen; this.wrapper = wrapper; }
    }

    private interface Closer { boolean close(); }

    /** Caps the tab content width on tablets and landscape foldables (presentations and overlays stay full width). */
    private static final class Column extends FrameLayout {
        Column(Context c) { super(c); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int cap = Ui.dp(getContext(), ShelfMetrics.COLUMN_MAX_DP);
            if (MeasureSpec.getSize(widthSpec) > cap) widthSpec = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.EXACTLY);
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    private final Activity activity;
    private final ColorRoles roles;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io;
    private final LibraryRepoImpl repo;
    private final PlayerHubImpl hub;
    private final Artwork artwork;
    private final Menus menus;
    private final SharedPreferences prefs;
    private final FrameLayout root, content, presentLayer, overlay;
    private final Column column;
    private final FloatingChrome chrome;
    private final Toaster toaster;
    private final ScrollBehavior behavior;
    private final EnumMap<Tab, ArrayList<Entry>> stacks = new EnumMap<>(Tab.class);
    private final ArrayList<Entry> presented = new ArrayList<>();
    private final ArrayList<Closer> overlays = new ArrayList<>();
    private final ArrayList<Runnable> finishers = new ArrayList<>();
    private Tab current = Tab.HOME, lastMain = Tab.HOME;
    private View gate;
    private int gateKind; // 0 none, 1 startup, 2 permission
    private int sysLeft, sysTop, sysRight, sysBottom, imeBottom;
    private boolean visible, destroyed, miniShown;
    private String miniTrackId = "";
    private Object backCallback;
    private boolean backRegistered;
    private String pendingCoverTrackId;
    private int cfgW, cfgH;

    public Shell(Activity activity, Bundle saved, LibraryRepoImpl.Model retained) {
        this.activity = activity;
        this.roles = Theme.colors(activity);
        this.prefs = activity.getSharedPreferences("aura_shell", Context.MODE_PRIVATE);
        final AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "AuraIo-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        io = Executors.newFixedThreadPool(3, factory);
        repo = new LibraryRepoImpl(activity, io, retained);
        io.execute(repo::warmStores);
        artwork = new Artwork(activity, repo.library());
        hub = new PlayerHubImpl(activity, repo, new PlayerHubImpl.Events() {
            @Override public void onMessage(String message) { toast(message); }
            @Override public void onTrackStarted(Track track) { repo.recordPlayed(track); }
            @Override public void onFirstPlayback() { maybeAskNotifications(); }
        });
        menus = new Menus(this);
        behavior = new ScrollBehavior(Ui.dp(activity, 24), Ui.dp(activity, 56));
        for (Tab t : Tab.values()) stacks.put(t, new ArrayList<>());

        EdgeToEdge.apply(activity, roles.dark);
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(roles.canvas));
        root = new FrameLayout(activity);
        root.setBackgroundColor(roles.canvas);
        column = new Column(activity);
        column.setClipChildren(false);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_HORIZONTAL));
        content = new FrameLayout(activity);
        content.setBackgroundColor(roles.canvas);
        column.addView(content, new FrameLayout.LayoutParams(-1, -1));
        chrome = new FloatingChrome(activity, roles);
        chrome.setBackdrop(content);
        column.addView(chrome, new FrameLayout.LayoutParams(-1, -1));
        presentLayer = new FrameLayout(activity);
        presentLayer.setVisibility(View.GONE);
        root.addView(presentLayer, new FrameLayout.LayoutParams(-1, -1));
        overlay = new FrameLayout(activity);
        overlay.setClipChildren(false);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        toaster = new Toaster(activity, roles);
        toaster.setBackdrop(content);
        overlay.addView(toaster, Toaster.params(activity));
        wireChrome();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] i = EdgeToEdge.read(insets);
            int ime = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
            onInsets(i[0], i[1], i[2], i[3], ime);
            return insets;
        });

        repo.addListener(this::libraryChanged);
        hub.addListener(this::playbackChanged);
        cfgW = activity.getResources().getConfiguration().screenWidthDp;
        cfgH = activity.getResources().getConfiguration().screenHeightDp;
        if (saved != null) restore(saved);
        finishBuild();
        registerBack();
        try { AudiusClient.get(activity).warmUp(); } catch (RuntimeException ignored) {}
    }

    /** Makes sure the current tab has a root, applies visibility and syncs chrome after (re)building the stacks. */
    private void finishBuild() {
        if (stacks.get(current).isEmpty()) {
            Entry e = makeEntry(newRoot(current));
            stacks.get(current).add(e);
            content.addView(e.wrapper, new FrameLayout.LayoutParams(-1, -1));
        }
        for (Tab t : Tab.values()) {
            ArrayList<Entry> st = stacks.get(t);
            for (int i = 0; i < st.size(); i++) st.get(i).wrapper.setVisibility(t == current && i == st.size() - 1 ? View.VISIBLE : View.GONE);
        }
        syncChrome(false);
        if (shortWindow() && current != Tab.SEARCH) chrome.setMinimised(true, false);
        syncMini();
        updateGate();
    }

    /** True in a landscape phone window, where the full-size chrome would eat too much height. */
    private boolean shortWindow() { return ShelfMetrics.compactChrome(activity.getResources().getConfiguration().screenHeightDp); }

    /** Returns the chrome to its resting state after navigation: expanded, or compact in short windows. */
    private void restChrome(boolean animate) {
        chrome.setMinimised(shortWindow() && current != Tab.SEARCH, animate);
    }

    /**
     * Rotation / resize / fold: the activity is not recreated (see the manifest's configChanges), so
     * the shell rebuilds the screen stacks from their saved state, which recomputes every cached dp
     * metric (shelf cards, hero sizes, columns), and re-requests insets. Playback, the library model
     * and the chrome keep running.
     */
    public void onConfigurationChanged(android.content.res.Configuration c) {
        if (destroyed) return;
        int w = c.screenWidthDp, h = c.screenHeightDp;
        boolean sizeChanged = w != cfgW || h != cfgH;
        cfgW = w;
        cfgH = h;
        if (sizeChanged) {
            settle();
            final PromptState reopen = activePrompt;
            final String typed = reopen == null ? null : reopen.dialog.text();
            for (Closer cl : new ArrayList<>(overlays)) cl.close();
            Entry shownNow = visibleEntry();
            if (shownNow != null) hide(shownNow);
            Bundle saved = new Bundle();
            save(saved);
            for (Tab t : Tab.values()) {
                for (Entry e : stacks.get(t)) { e.screen.onDestroy(); content.removeView(e.wrapper); }
                stacks.get(t).clear();
            }
            for (Entry e : presented) e.screen.onDestroy();
            presented.clear();
            presentLayer.removeAllViews();
            presentLayer.setVisibility(View.GONE);
            chrome.setVisibility(View.VISIBLE);
            chrome.setAlpha(1f);
            restore(saved);
            behavior.set(false);
            chrome.setMinimised(false, false);
            finishBuild();
            if (visible) show(visibleEntry());
            updateBars();
            if (reopen != null && typed != null) prompt(reopen.title, reopen.hint, typed, reopen.action, reopen.onDone);
        }
        root.requestApplyInsets();
        dispatchAllInsets();
    }

    // ------------------------------------------------------------------ accessors for helpers

    public View root() { return root; }
    @Override public Activity activity() { return activity; }
    ColorRoles colors0() { return roles; }
    LibraryRepoImpl repoImpl() { return repo; }
    PlayerHubImpl hubImpl() { return hub; }

    // ------------------------------------------------------------------ lifecycle

    public void onStart() {}

    public void onResume() {
        visible = true;
        repo.setVisible(true);
        updateGate();
        Screen top = top();
        Entry e = entryOf(top);
        if (e != null) show(e);
        updateBars();
    }

    public void onPause() {
        visible = false;
        repo.setVisible(false);
        Entry e = entryOf(top());
        if (e != null) hide(e);
    }

    public void onDestroy() {
        destroyed = true;
        settle();
        main.removeCallbacksAndMessages(null);
        unregisterBack();
        for (Tab t : Tab.values()) for (Entry e : stacks.get(t)) e.screen.onDestroy();
        for (Entry e : presented) e.screen.onDestroy();
        hub.release();
        repo.destroy();
        artwork.close();
        io.shutdownNow();
    }

    public LibraryRepoImpl.Model retain() { return repo.retain(); }

    // ------------------------------------------------------------------ chrome wiring

    private void wireChrome() {
        chrome.tabBar.setListener(new dev.lilt.player.app.chrome.TabBar.Listener() {
            @Override public void onTab(Tab tab) { switchTab(tab); }
            @Override public void onExpand() {
                if (current == Tab.SEARCH) switchTab(lastMain);
                else { behavior.set(false); chrome.setMinimised(false, true); }
            }
        });
        chrome.search.setListener(new dev.lilt.player.app.chrome.SearchPill.Listener() {
            @Override public void onTap() {
                switchTab(Tab.SEARCH);
                main.postDelayed(chrome.search::focusField, 260);
            }
            @Override public void onQuery(String query, boolean submitted) { deliverQuery(query, submitted); }
        });
        chrome.mini.setListener(new dev.lilt.player.app.chrome.MiniPlayer.Listener() {
            @Override public void onOpen() { openNowPlaying(); }
            @Override public void onToggle() { hub.toggle(); }
            @Override public void onNext() { hub.next(); }
        });
    }

    private void syncChrome(boolean animate) {
        chrome.tabBar.select(current == Tab.SEARCH ? null : current, animate);
        if (current == Tab.SEARCH) chrome.tabBar.setCollapsedIcon(lastMain, false);
        else chrome.tabBar.setCollapsedIcon(current, true);
        chrome.setSearching(current == Tab.SEARCH, animate);
        if (current == Tab.SEARCH) chrome.setMinimised(false, false);
    }

    private void deliverQuery(String q, boolean submitted) {
        ArrayList<Entry> st = stacks.get(Tab.SEARCH);
        if (st.isEmpty()) return;
        if (st.size() > 1) popToRoot(Tab.SEARCH, false);
        st.get(0).screen.onSearchQuery(q, submitted);
    }

    // ------------------------------------------------------------------ insets

    private void onInsets(int left, int top, int right, int bottom, int ime) {
        sysLeft = left; sysTop = top; sysRight = right; sysBottom = bottom; imeBottom = ime;
        root.setPadding(left, 0, right, 0);
        chrome.setBottomInset(bottom);
        chrome.setKeyboardInset(current == Tab.SEARCH ? ime : 0);
        if (gateKind == 2) rebuildGate();
        dispatchAllInsets();
    }

    private int topFor(boolean fullScreen) { return fullScreen ? sysTop : sysTop + Ui.dp(activity, DesignTokens.TOP_BAR); }

    private int bottomFor(Tab tab, boolean fullScreen) {
        if (fullScreen) return sysBottom;
        return chrome.coveredHeight(chrome.miniVisible(), tab == Tab.SEARCH);
    }

    private void dispatchInsets(Entry e, Tab tab, boolean isPresented) {
        boolean full = isPresented || e.screen.fullScreen();
        int t = topFor(full), b = bottomFor(tab, full);
        if (t == e.top && b == e.bottom) return;
        e.top = t;
        e.bottom = b;
        e.screen.onInsets(t, b);
    }

    private void dispatchAllInsets() {
        for (Tab t : Tab.values()) for (Entry e : stacks.get(t)) dispatchInsets(e, t, false);
        for (Entry e : presented) dispatchInsets(e, current, true);
    }

    // ------------------------------------------------------------------ entries

    private Entry makeEntry(Screen s) {
        s.attach(this);
        FrameLayout w = new FrameLayout(activity);
        w.setBackgroundColor(roles.canvas);
        w.addView(s.view(activity), new FrameLayout.LayoutParams(-1, -1));
        return new Entry(s, w);
    }

    private Entry entryOf(Screen s) {
        if (s == null) return null;
        for (Entry e : presented) if (e.screen == s) return e;
        for (Tab t : Tab.values()) for (Entry e : stacks.get(t)) if (e.screen == s) return e;
        return null;
    }

    private void show(Entry e) {
        if (e == null || e.shown || !visible) return;
        e.shown = true;
        e.screen.onShow();
    }

    private void hide(Entry e) {
        if (e == null || !e.shown) return;
        e.shown = false;
        e.screen.onHide();
    }

    private Screen newRoot(Tab t) {
        switch (t) {
            case BROWSE: return new BrowseScreen();
            case LIBRARY: return new LibraryScreen();
            case SEARCH: return new SearchScreen(chrome.search.query());
            default: return new HomeScreen();
        }
    }

    private ArrayList<Entry> stack(Tab t) { return stacks.get(t); }

    private Entry topEntry(Tab t) {
        ArrayList<Entry> st = stacks.get(t);
        return st.isEmpty() ? null : st.get(st.size() - 1);
    }

    private Entry visibleEntry() {
        return !presented.isEmpty() ? presented.get(presented.size() - 1) : topEntry(current);
    }

    private void afterAdd(Entry e, Tab tab, boolean isPresented, Bundle state) {
        dispatchInsets(e, tab, isPresented);
        if (state != null && !state.isEmpty()) {
            try { e.screen.restoreState(state); } catch (RuntimeException ignored) {}
        }
    }

    // ------------------------------------------------------------------ transitions

    /** Finishes running transitions immediately so the next navigation starts from a settled state. */
    private void settle() {
        if (finishers.isEmpty()) return;
        List<Runnable> copy = new ArrayList<>(finishers);
        finishers.clear();
        for (Runnable r : copy) r.run();
    }

    private void slide(final Entry in, final Entry out, final boolean forward, final Runnable after) {
        final float w = Math.max(1, column.getWidth() > 0 ? column.getWidth() : activity.getResources().getDisplayMetrics().widthPixels);
        final boolean animated = Motion.enabled() && column.isLaidOut();
        final Runnable[] finisher = new Runnable[1];
        finisher[0] = () -> {
            if (!finishers.remove(finisher[0])) return;
            in.wrapper.animate().cancel();
            if (out != null) out.wrapper.animate().cancel();
            in.wrapper.setTranslationX(0);
            in.wrapper.setAlpha(1f);
            in.wrapper.setVisibility(View.VISIBLE);
            if (out != null) { out.wrapper.setTranslationX(0); out.wrapper.setAlpha(1f); out.wrapper.setVisibility(View.GONE); }
            if (after != null) after.run();
            show(in);
            updateBars();
        };
        if (out != null) hide(out);
        in.wrapper.setVisibility(View.VISIBLE);
        finishers.add(finisher[0]);
        if (!animated) { finisher[0].run(); return; }
        in.wrapper.setTranslationX(forward ? w * 0.22f : -w * 0.10f);
        in.wrapper.setAlpha(0f);
        in.wrapper.animate().translationX(0).alpha(1f).setDuration(300).setInterpolator(Motion.standard()).withLayer()
                .withEndAction(finisher[0]).start();
        if (out != null) {
            out.wrapper.animate().translationX(forward ? -w * 0.10f : w * 0.22f).alpha(0f).setDuration(300)
                    .setInterpolator(Motion.standard()).withLayer().start();
        }
    }

    private void crossfade(final Entry in, final Entry out) {
        final boolean animated = Motion.enabled() && column.isLaidOut();
        final Runnable[] finisher = new Runnable[1];
        finisher[0] = () -> {
            if (!finishers.remove(finisher[0])) return;
            in.wrapper.animate().cancel();
            if (out != null) out.wrapper.animate().cancel();
            in.wrapper.setAlpha(1f);
            in.wrapper.setVisibility(View.VISIBLE);
            if (out != null) { out.wrapper.setAlpha(1f); out.wrapper.setVisibility(View.GONE); }
            show(in);
            updateBars();
        };
        if (out != null) hide(out);
        in.wrapper.setVisibility(View.VISIBLE);
        finishers.add(finisher[0]);
        if (!animated) { finisher[0].run(); return; }
        in.wrapper.setAlpha(0f);
        in.wrapper.animate().alpha(1f).setDuration(Motion.FAST).withEndAction(finisher[0]).start();
        if (out != null) out.wrapper.animate().alpha(0f).setDuration(Motion.FAST).start();
    }

    // ------------------------------------------------------------------ Navigator

    @Override public void push(Screen screen) {
        if (screen == null) return;
        settle();
        if (!presented.isEmpty()) dismissAll(false);
        ArrayList<Entry> st = stack(current);
        Entry prev = topEntry(current);
        if (prev != null && prev.screen.key().equals(screen.key())) return;
        Entry e = makeEntry(screen);
        st.add(e);
        content.addView(e.wrapper, new FrameLayout.LayoutParams(-1, -1));
        e.wrapper.setVisibility(View.INVISIBLE);
        afterAdd(e, current, false, null);
        behavior.set(false);
        restChrome(true);
        slide(e, prev, true, null);
        updateBackCallback();
    }

    @Override public boolean pop() {
        settle();
        ArrayList<Entry> st = stack(current);
        if (st.size() <= 1) return false;
        popInternal(current, true);
        return true;
    }

    private void popInternal(Tab tab, boolean animate) {
        ArrayList<Entry> st = stack(tab);
        if (st.size() <= 1) return;
        final Entry out = st.remove(st.size() - 1);
        final Entry in = st.get(st.size() - 1);
        behavior.set(false);
        if (tab == current) {
            restChrome(animate);
            if (animate) slide(in, out, false, () -> destroy(out));
            else { hide(out); in.wrapper.setVisibility(View.VISIBLE); destroy(out); show(in); updateBars(); }
        } else destroy(out);
        updateBackCallback();
    }

    private void destroy(Entry e) {
        hide(e);
        e.screen.onDestroy();
        ViewGroup parent = (ViewGroup) e.wrapper.getParent();
        if (parent != null) parent.removeView(e.wrapper);
    }

    private void popToRoot(Tab tab, boolean animate) {
        settle();
        ArrayList<Entry> st = stack(tab);
        while (st.size() > 2) destroy(st.remove(1));
        if (st.size() == 2) popInternal(tab, animate);
    }

    @Override public void switchTab(Tab tab) {
        if (tab == null) return;
        settle();
        if (!presented.isEmpty()) dismissAll(false);
        if (tab == current) {
            ArrayList<Entry> st = stack(tab);
            if (st.size() > 1) popToRoot(tab, true);
            else if (!st.isEmpty()) st.get(0).screen.scrollToTop();
            behavior.set(false);
            restChrome(true);
            return;
        }
        Entry out = topEntry(current);
        Tab previous = current;
        current = tab;
        if (tab != Tab.SEARCH) lastMain = tab;
        else if (previous != Tab.SEARCH) lastMain = previous;
        if (previous == Tab.SEARCH) chrome.search.hideKeyboard();
        ArrayList<Entry> st = stack(tab);
        Entry in;
        if (st.isEmpty()) {
            in = makeEntry(newRoot(tab));
            st.add(in);
            content.addView(in.wrapper, new FrameLayout.LayoutParams(-1, -1));
            in.wrapper.setVisibility(View.INVISIBLE);
            afterAdd(in, tab, false, null);
        } else in = st.get(st.size() - 1);
        dispatchInsets(in, tab, false);
        behavior.set(false);
        chrome.setKeyboardInset(tab == Tab.SEARCH ? imeBottom : 0);
        syncChrome(true);
        if (tab != Tab.SEARCH) restChrome(false);
        crossfade(in, out);
        updateBackCallback();
    }

    @Override public Tab currentTab() { return current; }

    @Override public void present(Screen screen) {
        if (screen == null) return;
        settle();
        Entry before = visibleEntry();
        if (before != null && before.screen.key().equals(screen.key())) return;
        chrome.search.hideKeyboard();
        Entry e = makeEntry(screen);
        presented.add(e);
        presentLayer.setVisibility(View.VISIBLE);
        presentLayer.addView(e.wrapper, new FrameLayout.LayoutParams(-1, -1));
        afterAdd(e, current, true, null);
        if (before != null) hide(before);
        final boolean first = presented.size() == 1;
        final boolean animated = Motion.enabled() && column.isLaidOut();
        final float h = Math.max(1, column.getHeight());
        final Runnable[] finisher = new Runnable[1];
        finisher[0] = () -> {
            if (!finishers.remove(finisher[0])) return;
            e.wrapper.animate().cancel();
            e.wrapper.setTranslationY(0);
            if (first) { chrome.setVisibility(View.GONE); chrome.setAlpha(1f); }
            show(e);
            updateBars();
        };
        finishers.add(finisher[0]);
        if (!animated) { finisher[0].run(); updateBackCallback(); return; }
        e.wrapper.setTranslationY(h);
        e.wrapper.animate().translationY(0).setDuration(Motion.SHEET + 40).setInterpolator(Motion.decelerate()).withLayer()
                .withEndAction(finisher[0]).start();
        if (first) chrome.animate().alpha(0f).setDuration(Motion.SHEET).start();
        updateBackCallback();
    }

    @Override public boolean dismiss() {
        settle();
        if (presented.isEmpty()) return false;
        dismissTop(true);
        return true;
    }

    private void dismissTop(boolean animate) {
        final Entry e = presented.remove(presented.size() - 1);
        final boolean last = presented.isEmpty();
        final Entry below = last ? topEntry(current) : presented.get(presented.size() - 1);
        hide(e);
        final Runnable[] finisher = new Runnable[1];
        finisher[0] = () -> {
            if (!finishers.remove(finisher[0])) return;
            e.wrapper.animate().cancel();
            e.screen.onDestroy();
            presentLayer.removeView(e.wrapper);
            if (last) { presentLayer.setVisibility(View.GONE); chrome.setAlpha(1f); chrome.setVisibility(View.VISIBLE); }
            show(below);
            updateBars();
        };
        if (last) { chrome.setVisibility(View.VISIBLE); }
        finishers.add(finisher[0]);
        if (!animate || !Motion.enabled() || !column.isLaidOut()) { finisher[0].run(); updateBackCallback(); return; }
        if (last) { chrome.setAlpha(0f); chrome.animate().alpha(1f).setDuration(Motion.SHEET).start(); }
        e.wrapper.animate().translationY(Math.max(1, column.getHeight())).setDuration(Motion.SHEET).setInterpolator(Motion.accelerate())
                .withLayer().withEndAction(finisher[0]).start();
        updateBackCallback();
    }

    /** Dismisses every presentation (used before navigating to a detail screen from a player). */
    void dismissAll(boolean animate) {
        settle();
        while (!presented.isEmpty()) {
            dismissTop(animate && presented.size() == 1);
            settle();
        }
    }

    @Override public Screen top() {
        Entry e = visibleEntry();
        return e == null ? null : e.screen;
    }

    // ------------------------------------------------------------------ back

    public boolean handleBack() {
        if (!overlays.isEmpty()) {
            Closer c = overlays.get(overlays.size() - 1);
            c.close();
            return true;
        }
        if (gateKind != 0 && gateKind != 1) return false;
        if (!presented.isEmpty()) {
            Screen s = presented.get(presented.size() - 1).screen;
            if (s.onBack()) return true;
            dismiss();
            return true;
        }
        if (current == Tab.SEARCH) {
            if (chrome.search.keyboardFocused()) { chrome.search.hideKeyboard(); return true; }
            Entry t = topEntry(current);
            if (t != null && t.screen.onBack()) return true;
            if (stack(current).size() > 1) { pop(); return true; }
            switchTab(lastMain);
            return true;
        }
        Entry t = topEntry(current);
        if (t != null && t.screen.onBack()) return true;
        if (stack(current).size() > 1) { pop(); return true; }
        if (current != Tab.HOME) { switchTab(Tab.HOME); return true; }
        return false;
    }

    /** True when Back has something closable to do; Back must never leave the app while this holds. */
    boolean canHandleBack() {
        return !overlays.isEmpty() || !presented.isEmpty() || current != Tab.HOME || stack(current).size() > 1
                || gateKind == 2 || chrome.search.keyboardFocused();
    }

    /** Leaves the app the way the system would for a launcher activity. */
    void leave() {
        if (Build.VERSION.SDK_INT >= 31) activity.moveTaskToBack(true); else activity.finish();
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        Api33.register(this);
        backRegistered = true;
    }

    private void unregisterBack() {
        if (Build.VERSION.SDK_INT >= 33) Api33.unregister(this);
    }

    private void updateBackCallback() {
        // The callback stays registered for the activity's whole life (see registerBack): the shell's
        // state changes in many places (menus, sheets, search focus, transitions) and a stale,
        // unregistered callback would let the system finish the activity with something still open.
        // At the root with nothing open the callback leaves the app itself (leave()).
        if (Build.VERSION.SDK_INT >= 33 && !backRegistered && !destroyed) registerBack();
    }

    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static void register(Shell s) {
            android.window.OnBackInvokedCallback cb = () -> { if (!s.handleBack()) s.leave(); };
            s.backCallback = cb;
            s.activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            s.backRegistered = true;
        }
        static void unregister(Shell s) {
            if (s.backCallback instanceof android.window.OnBackInvokedCallback) {
                s.activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) s.backCallback);
            }
            s.backCallback = null;
            s.backRegistered = false;
        }
    }

    // ------------------------------------------------------------------ system bars

    private void updateBars() {
        Entry e = visibleEntry();
        boolean artUnderBars = e != null && e.screen.lightSystemBars();
        boolean lightBackground = !roles.dark && !artUnderBars;
        EdgeToEdge.setBarIcons(activity.getWindow(), lightBackground);
    }

    @Override public void invalidateSystemBars() { updateBars(); }

    // ------------------------------------------------------------------ gate (startup / permission)

    private boolean skippedLocal() { return prefs.getBoolean("skip_local", false); }

    private void updateGate() {
        if (destroyed) return;
        int want;
        if (!repo.hasPermission() && !skippedLocal()) want = 2;
        else if (repo.hasPermission() && !repo.ready()) want = 1;
        else want = 0;
        if (want == gateKind) return;
        gateKind = want;
        rebuildGate();
        updateBackCallback();
    }

    private void rebuildGate() {
        final View old = gate;
        gate = null;
        if (gateKind == 1) gate = StartupViews.startup(activity, roles);
        else if (gateKind == 2) {
            gate = StartupViews.permission(activity, roles, sysTop, sysBottom, this::requestMediaPermission, () -> {
                prefs.edit().putBoolean("skip_local", true).apply();
                updateGate();
            });
        }
        if (gate != null) {
            column.addView(gate, new FrameLayout.LayoutParams(-1, -1));
            if (old != null) column.removeView(old);
        } else if (old != null) {
            old.animate().alpha(0f).setDuration(Motion.BASE).withEndAction(() -> column.removeView(old)).start();
        }
    }

    @Override public void requestMediaPermission() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (!activity.shouldShowRequestPermissionRationale(p) && repo.permissionAsked()) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName())));
            } catch (RuntimeException ignored) { toast("Open Settings and allow audio access for AURA"); }
            return;
        }
        repo.markPermissionAsked();
        activity.requestPermissions(new String[] {p}, REQ_MEDIA);
    }

    public void onPermissionResult(int request, String[] permissions, int[] results) {
        if (request == REQ_MEDIA) {
            if (repo.hasPermission()) repo.refresh();
            updateGate();
            libraryChanged();
        }
    }

    private void maybeAskNotifications() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        if (prefs.getBoolean("notifications_asked", false)) return;
        prefs.edit().putBoolean("notifications_asked", true).apply();
        activity.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    // ------------------------------------------------------------------ change notifications

    private int warmedFor = -1;

    /** Once per library size: load the covers of the first shelves now, the rest while idle. */
    private void warmCovers() {
        if (!repo.ready() || repo.loading()) return;
        List<Track> all = repo.localTracks();
        if (all.isEmpty() || all.size() == warmedFor) return;
        warmedFor = all.size();
        List<Track> urgent = new ArrayList<>();
        urgent.addAll(repo.recentlyPlayed(12));
        urgent.addAll(repo.recentlyAdded(12));
        urgent.addAll(repo.favourites().subList(0, Math.min(12, repo.favourites().size())));
        List<Track> albumCovers = new ArrayList<>();
        for (AlbumGroup g : repo.albums()) { Track c = g.cover(); if (c != null) albumCovers.add(c); }
        int cardPx = Ui.dp(activity, ShelfMetrics.squareCard(contentWidthDp()));
        artwork.warmLibrary(urgent, albumCovers, cardPx);
    }

    private void libraryChanged() {
        if (destroyed) return;
        warmCovers();
        updateGate();
        for (Tab t : Tab.values()) for (Entry e : stacks.get(t)) if (e.screen.hasView()) e.screen.onLibraryChanged();
        for (Entry e : presented) if (e.screen.hasView()) e.screen.onLibraryChanged();
    }

    private String prefetchedFor = "";

    /** Covers of the songs around the current one, so skipping tracks never waits on artwork. */
    private void prefetchNeighbours() {
        Track current = hub.current();
        if (current == null || current.id.equals(prefetchedFor)) return;
        prefetchedFor = current.id;
        List<Track> queue = hub.queue();
        int at = hub.index();
        if (queue == null || at < 0 || at >= queue.size()) return;
        List<Track> near = new ArrayList<>();
        for (int i = Math.max(0, at - 1); i <= Math.min(queue.size() - 1, at + 3); i++) if (i != at && !queue.get(i).isRemote()) near.add(queue.get(i));
        if (near.isEmpty()) return;
        artwork.prefetch(near, Ui.dp(activity, 44));
        artwork.prefetch(near, Ui.dp(activity, 340));
    }

    private void playbackChanged() {
        if (destroyed) return;
        prefetchNeighbours();
        syncMini();
        for (Tab t : Tab.values()) for (Entry e : stacks.get(t)) if (e.screen.hasView()) e.screen.onPlaybackChanged();
        for (Entry e : presented) if (e.screen.hasView()) e.screen.onPlaybackChanged();
    }

    private void syncMini() {
        Track t = hub.current();
        boolean has = t != null;
        boolean changed = has != miniShown;
        miniShown = has;
        chrome.setMiniVisible(has);
        if (has) {
            chrome.mini.bind(t);
            if (!t.id.equals(miniTrackId)) { miniTrackId = t.id; artwork.load(chrome.mini.cover(), t); }
            chrome.mini.setPlaying(hub.playing());
        } else miniTrackId = "";
        if (changed) dispatchAllInsets();
    }

    // ------------------------------------------------------------------ state

    public void save(Bundle out) {
        out.putString("tab", current.name());
        out.putString("lastMain", lastMain.name());
        for (Tab t : Tab.values()) {
            ArrayList<String> keys = new ArrayList<>();
            ArrayList<Entry> st = stacks.get(t);
            for (int i = 0; i < st.size(); i++) {
                Entry e = st.get(i);
                keys.add(e.screen.key());
                putState(out, "ss." + t.name() + "." + i, e.screen);
            }
            out.putStringArrayList("stack." + t.name(), keys);
        }
        ArrayList<String> keys = new ArrayList<>();
        for (int i = 0; i < presented.size(); i++) {
            keys.add(presented.get(i).screen.key());
            putState(out, "sp." + i, presented.get(i).screen);
        }
        out.putStringArrayList("presented", keys);
        out.putString("query", chrome.search.query());
        if (pendingCoverTrackId != null) out.putString("coverTrack", pendingCoverTrackId);
    }

    /** Screen state must stay small: oversized bundles are dropped (a TransactionTooLargeException crashes the app). */
    private static void putState(Bundle out, String key, Screen s) {
        try {
            if (!s.hasView()) return;
            Bundle b = s.saveState();
            if (b == null || b.isEmpty()) return;
            Parcel p = Parcel.obtain();
            try {
                p.writeBundle(b);
                if (p.dataSize() > MAX_STATE_BYTES) return;
            } finally { p.recycle(); }
            out.putBundle(key, b);
        } catch (RuntimeException ignored) {}
    }

    private void restore(Bundle saved) {
        try {
            current = Tab.valueOf(saved.getString("tab", Tab.HOME.name()));
            lastMain = Tab.valueOf(saved.getString("lastMain", Tab.HOME.name()));
        } catch (IllegalArgumentException ignored) { current = Tab.HOME; lastMain = Tab.HOME; }
        pendingCoverTrackId = saved.getString("coverTrack");
        String query = saved.getString("query", "");
        if (!query.isEmpty()) chrome.search.setQuerySilently(query);
        for (Tab t : Tab.values()) {
            ArrayList<String> keys = saved.getStringArrayList("stack." + t.name());
            if (keys == null) continue;
            for (int i = 0; i < keys.size(); i++) {
                Screen s = Screens.create(keys.get(i), this, query);
                if (s == null) continue;
                Entry e = makeEntry(s);
                stacks.get(t).add(e);
                content.addView(e.wrapper, new FrameLayout.LayoutParams(-1, -1));
                e.wrapper.setVisibility(View.GONE);
                afterAdd(e, t, false, saved.getBundle("ss." + t.name() + "." + i));
            }
        }
        ArrayList<String> pk = saved.getStringArrayList("presented");
        if (pk != null) for (int i = 0; i < pk.size(); i++) {
            Screen s = Screens.create(pk.get(i), this, query);
            if (s == null) continue;
            Entry e = makeEntry(s);
            presented.add(e);
            presentLayer.addView(e.wrapper, new FrameLayout.LayoutParams(-1, -1));
            afterAdd(e, current, true, saved.getBundle("sp." + i));
        }
        if (!presented.isEmpty()) { presentLayer.setVisibility(View.VISIBLE); chrome.setVisibility(View.GONE); }
    }

    // ------------------------------------------------------------------ AppHost

    @Override public Navigator nav() { return this; }
    @Override public PlayerHub player() { return hub; }
    @Override public LibraryRepo library() { return repo; }
    @Override public Artwork artwork() { return artwork; }
    @Override public Executor io() { return io; }
    @Override public Handler main() { return main; }
    @Override public ColorRoles colors() { return roles; }
    @Override public boolean dark() { return roles.dark; }

    @Override public int contentWidthDp() {
        int w = column.getWidth() > 0 ? column.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
        return Math.round(w / activity.getResources().getDisplayMetrics().density);
    }

    @Override public void toast(String message) {
        if (message == null || message.isEmpty() || destroyed) return;
        int h = column.getHeight();
        int bottom = presented.isEmpty() && chrome.getVisibility() == View.VISIBLE && chrome.getHeight() > 0
                ? h - chrome.topEdge() + Ui.dp(activity, 8)
                : sysBottom + Ui.dp(activity, 24);
        toaster.show(message, bottom);
    }

    private View overlayBackdrop() { return presented.isEmpty() ? content : presentLayer; }

    @Override public void showTrackMenu(Track track, View anchor) { menus.track(track, anchor, null); }
    @Override public void showTrackMenu(Track track, View anchor, List<MenuAction> extra) { menus.track(track, anchor, extra); }
    @Override public void showCollectionMenu(String title, List<Track> tracks, View anchor) { menus.collection(title, tracks, anchor, null); }
    @Override public void showCollectionMenu(String title, List<Track> tracks, View anchor, List<MenuAction> extra) { menus.collection(title, tracks, anchor, extra); }
    @Override public void pickPlaylist(List<Track> tracks) { menus.playlistPicker(tracks); }

    @Override public void showMenu(View anchor, List<MenuAction> actions) {
        if (actions == null || actions.isEmpty() || anchor == null) return;
        final MenuPopover m = new MenuPopover(activity, roles, overlayBackdrop(), actions);
        final Closer closer = () -> { m.dismiss(); return true; };
        overlays.add(closer);
        m.setOnClosed(() -> { overlays.remove(closer); updateBackCallback(); });
        m.show(overlay, anchor, sysTop, sysBottom);
        updateBackCallback();
    }

    @Override public void prompt(String title, String hint, String initial, String actionLabel, Consumer<String> onDone) {
        final GlassDialog d = GlassDialog.prompt(activity, roles, overlayBackdrop(), title, hint, initial, actionLabel, onDone);
        activePrompt = new PromptState(d, title, hint, actionLabel, onDone);
        trackDialog(d);
    }

    @Override public void confirm(String title, String message, String actionLabel, boolean destructive, Runnable onConfirm) {
        final GlassDialog d = GlassDialog.confirm(activity, roles, overlayBackdrop(), title, message, actionLabel, destructive, onConfirm);
        trackDialog(d);
    }

    /** An open text prompt, remembered so a rotation can re-open it with what the user typed. */
    private static final class PromptState {
        final GlassDialog dialog;
        final String title, hint, action;
        final Consumer<String> onDone;
        PromptState(GlassDialog d, String title, String hint, String action, Consumer<String> onDone) {
            this.dialog = d; this.title = title; this.hint = hint; this.action = action; this.onDone = onDone;
        }
    }
    private PromptState activePrompt;

    private void trackDialog(GlassDialog d) {
        final Closer closer = () -> { d.dismiss(); return true; };
        overlays.add(closer);
        d.setOnClosed(() -> { overlays.remove(closer); if (activePrompt != null && activePrompt.dialog == d) activePrompt = null; updateBackCallback(); });
        d.show(overlay, sysTop);
        updateBackCallback();
    }

    void showSheet(BottomSheet sheet) {
        final Closer closer = sheet::handleBack;
        overlays.add(closer);
        sheet.setOnDismiss(() -> { overlays.remove(closer); updateBackCallback(); });
        sheet.show(overlay, overlayBackdrop(), sysBottom);
        updateBackCallback();
    }

    // Navigation shortcuts. Detail screens are pushed on the current tab; presentations close first.

    @Override public void openNowPlaying() { present(new dev.lilt.player.screens.NowPlayingScreen()); }
    @Override public void openLyrics() { present(new dev.lilt.player.screens.LyricsScreen()); }
    @Override public void openQueue() { present(new dev.lilt.player.screens.QueueScreen()); }
    @Override public void openAlbum(String albumKey) { if (albumKey != null) push(new dev.lilt.player.screens.AlbumScreen(albumKey)); }
    @Override public void openArtist(String artistName) { if (artistName != null) push(new dev.lilt.player.screens.ArtistScreen(artistName)); }
    @Override public void openCatalogArtist(String userId, String name) { push(new dev.lilt.player.screens.CatalogArtistScreen(userId, name)); }
    @Override public void openCatalogPlaylist(CatalogPlaylist playlist) { if (playlist != null) push(new dev.lilt.player.screens.CatalogPlaylistScreen(playlist)); }
    @Override public void openPlaylist(String name) { if (name != null) push(new dev.lilt.player.screens.PlaylistScreen(name)); }

    @Override public void onScroll(Screen source, int scrollY, int dy) {
        if (current == Tab.SEARCH || !presented.isEmpty()) return;
        Entry t = topEntry(current);
        if (t == null || t.screen != source) return;
        chrome.setMinimised(behavior.onScroll(scrollY, dy), true);
    }

    @Override public void setSearchQuery(String query) {
        chrome.search.setQuerySilently(query);
        boolean wasSearch = current == Tab.SEARCH;
        switchTab(Tab.SEARCH);
        if (wasSearch || !stack(Tab.SEARCH).isEmpty()) deliverQuery(query == null ? "" : query, true);
    }

    @Override public int themeMode() { return Theme.mode(activity); }

    @Override public void setThemeMode(int mode) {
        if (mode == Theme.mode(activity)) return;
        Theme.setMode(activity, mode);
        activity.recreate();
    }

    // ------------------------------------------------------------------ custom album covers

    /** Opens the system picker for a cover image; the result is applied to {@code track}'s album. */
    @Override public void chooseCover(Track track) {
        if (track == null || track.isRemote()) return;
        pendingCoverTrackId = track.id;
        prefs.edit().putString("pending_cover", track.id).apply();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { activity.startActivityForResult(pick, REQ_COVER); } catch (RuntimeException e) { toast("No app can pick an image"); }
    }

    @Override public void resetCover(Track track) {
        repo.setCustomCover(track, null);
        toast("Cover reset");
    }

    public void onActivityResult(int request, int result, Intent data) {
        if (request != REQ_COVER) return;
        String id = pendingCoverTrackId != null ? pendingCoverTrackId : prefs.getString("pending_cover", null);
        pendingCoverTrackId = null;
        prefs.edit().remove("pending_cover").apply();
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || id == null) return;
        Uri uri = data.getData();
        try { activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (RuntimeException ignored) {}
        final Uri picked = uri;
        final String trackId = id;
        main.post(() -> {
            Track t = repo.find(trackId);
            if (t == null) { toast("That song isn't in the library any more"); return; }
            repo.setCustomCover(t, picked);
            toast("Cover updated");
        });
    }

    // ------------------------------------------------------------------ debug support (see DebugHooks)

    /** Resets to a clean state without animation: no presentations, root of {@code tab}. */
    void debugReset(Tab tab) {
        settle();
        overlays.clear();
        dismissAll(false);
        for (Tab t : Tab.values()) popToRoot(t, false);
        if (tab == current) {
            Entry e = topEntry(current);
            if (e != null) { e.wrapper.setVisibility(View.VISIBLE); show(e); }
            return;
        }
        switchTab(tab);
        settle();
    }

    boolean libraryReady() { return repo.ready() && !repo.loading(); }
}
