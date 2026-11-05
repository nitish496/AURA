package dev.lilt.player.app;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

/**
 * One destination in the app. Screens are plain programmatic views hosted by the shell
 * ({@link AppHost}); the shell owns navigation, the floating glass tab bar and the mini
 * player. All methods are called on the main thread. Never do I/O in these callbacks; use
 * {@link AppHost#io()} and post results back with {@link AppHost#main()}.
 */
public abstract class Screen {
    protected AppHost host;
    private View view;

    /** Called by the shell once before {@link #view(Context)}. */
    public final void attach(AppHost host) { this.host = host; }

    /** Build the view tree. Called once per screen instance. */
    protected abstract View onCreateView(Context context);

    public final View view(Context context) {
        if (view == null) view = onCreateView(context);
        return view;
    }

    public final boolean hasView() { return view != null; }

    /** Stable key used for state restoration and de-duplication (e.g. "album:<key>"). */
    public abstract String key();

    /** Tab this screen belongs to; detail screens inherit the tab they were pushed on. */
    public Tab tab() { return Tab.HOME; }

    /** Full-screen presentations (Now Playing, Lyrics...) hide the tab bar and mini player. */
    public boolean fullScreen() { return false; }

    /** Became the visible screen (after transitions). Start tickers/animations here. */
    public void onShow() {}

    /** Stopped being visible. Stop tickers/animations here. */
    public void onHide() {}

    /** Removed from the stack for good. Release listeners, bitmaps and pending work. */
    public void onDestroy() {}

    /** Return true to consume Back (e.g. close an inline search). */
    public boolean onBack() { return false; }

    /**
     * Space (px) covered by the shell's floating chrome: status bar + glass top bar at the top,
     * tab bar + mini player + gesture inset at the bottom. Pad scroll content with these so the
     * glass layer floats over content without hiding the first/last item.
     */
    public void onInsets(int top, int bottom) {}

    /** Local library, favourites, playlists or saved catalogue changed. */
    public void onLibraryChanged() {}

    /** Current item, play state, queue or shuffle/repeat changed. */
    public void onPlaybackChanged() {}

    /** Dark/light switched; rebuild colours from {@link AppHost#colors()}. */
    public void onThemeChanged() {}

    /**
     * True while this screen draws dark content (artwork, Now Playing tint) under the system bars
     * in both themes, so the shell draws light status/navigation bar icons. Call
     * {@link AppHost#invalidateSystemBars()} when the answer changes (e.g. a header scrolled away).
     */
    public boolean lightSystemBars() { return false; }

    /**
     * The shell's bottom glass search field (shown on the SEARCH tab) changed. Delivered to the
     * top screen of the SEARCH tab; {@code submitted} is true for the IME search action. Debounce
     * network work yourself.
     */
    public void onSearchQuery(String query, boolean submitted) {}

    /** The tab was selected again while this screen is the root of its stack: scroll to the top. */
    public void scrollToTop() {}

    public Bundle saveState() { return Bundle.EMPTY; }

    public void restoreState(Bundle state) {}
}
