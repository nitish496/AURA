package dev.lilt.player.ui;

import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;

/**
 * Empty, offline and error states. Left aligned like the rest of the app (never a centred
 * illustration): 32dp secondary icon, Title 2, a plain one- or two-sentence explanation
 * (15/20 secondary, max 330dp), then at most two actions. Copy states what happened and what
 * still works; no marketing, no apologies.
 *
 * <pre>{@code
 * content.addView(EmptyState.offline(ctx, this::openLibrary));
 * }</pre>
 */
public class EmptyState extends LinearLayout {
    private final ColorRoles roles;
    private final LinearLayout actions;

    public EmptyState(Context c, int iconRes, CharSequence title, CharSequence body) { this(c, Theme.colors(c), iconRes, title, body); }

    public EmptyState(Context c, ColorRoles roles, int iconRes, CharSequence title, CharSequence body) {
        super(c);
        this.roles = roles;
        setOrientation(VERTICAL);
        int m = Ui.dp(c, DesignTokens.MARGIN);
        setPaddingRelative(m, Ui.dp(c, 24), m, Ui.dp(c, 24));
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(Icons.get(c, iconRes, roles.textSecondary));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, new LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
        TextView t = Type.text(c, TypeScale.TITLE2, roles.textPrimary, title);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(c, 16);
        addView(t, tp);
        TextView b = Type.text(c, TypeScale.SUBHEAD, roles.textSecondary, body);
        b.setMaxWidth(Ui.dp(c, 330));
        LayoutParams bp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(c, 8);
        addView(b, bp);
        actions = Ui.row(c);
        LayoutParams ap = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ap.topMargin = Ui.dp(c, 20);
        addView(actions, ap);
        actions.setVisibility(GONE);
    }

    /** Adds an action button ({@link PillButton} kind); returns this for chaining. */
    public EmptyState action(int kind, CharSequence label, int iconRes, Runnable onClick) {
        PillButton p = new PillButton(getContext(), roles, kind, label, iconRes);
        p.setOnClickListener(v -> onClick.run());
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (actions.getChildCount() > 0) lp.setMarginStart(Ui.dp(getContext(), 8));
        actions.addView(p, lp);
        actions.setVisibility(VISIBLE);
        return this;
    }

    /** Browse without a connection. */
    public static EmptyState offline(Context c, Runnable goToLibrary) {
        return new EmptyState(c, R.drawable.ic_cloud_off, "You’re offline",
                "Browse needs a connection. Your library, saved songs and anything you played recently still work.")
                .action(PillButton.SECONDARY, "Go to Library", R.drawable.ic_library, goToLibrary);
    }

    /** First run without audio permission. */
    public static EmptyState permission(Context c, Runnable allow, Runnable browse) {
        EmptyState e = new EmptyState(c, R.drawable.ic_song, "Allow access to your music",
                "AURA plays the audio files already on this phone. Nothing is uploaded, and you can change this in Settings.")
                .action(PillButton.FILLED, "Allow Access", 0, allow);
        return browse == null ? e : e.action(PillButton.TEXT, "Browse the catalogue", 0, browse);
    }

    /** Permission granted but no audio files found. */
    public static EmptyState noMusic(Context c, Runnable rescan, Runnable browse) {
        return new EmptyState(c, R.drawable.ic_song, "No music on this phone yet",
                "Copy audio files to the Music or Download folder, or save songs from Browse.")
                .action(PillButton.SECONDARY, "Scan Again", R.drawable.ic_refresh, rescan)
                .action(PillButton.TEXT, "Browse", 0, browse);
    }

    /** A load failed; {@code what} names the thing ("Browse", "this playlist"). */
    public static EmptyState error(Context c, CharSequence what, Runnable retry) {
        return new EmptyState(c, R.drawable.ic_alert, "Couldn’t load " + what,
                "Check your connection and try again. Your library still works offline.")
                .action(PillButton.SECONDARY, "Try Again", R.drawable.ic_refresh, retry);
    }

    /** Search with no results. */
    public static EmptyState noResults(Context c, CharSequence query) {
        return new EmptyState(c, R.drawable.ic_search, "No results for “" + query + "”",
                "Check the spelling, or search for an artist, album or song title.");
    }
}
