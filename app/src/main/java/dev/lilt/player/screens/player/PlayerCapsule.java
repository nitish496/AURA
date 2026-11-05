package dev.lilt.player.screens.player;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import dev.lilt.player.R;
import dev.lilt.player.app.AppHost;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassDrawable;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Ui;

/**
 * The bottom glass capsule of the player screens: Lyrics / Output / Queue. The active section
 * shows a white lens with a dark icon (as in the design). From a sibling screen (Lyrics, Queue)
 * a different section replaces the current screen; the active one closes it.
 */
public final class PlayerCapsule {
    private PlayerCapsule() {}

    public static final int NONE = -1, LYRICS = 0, OUTPUT = 1, QUEUE = 2;
    public static final int WIDTH_DP = 192, HEIGHT_DP = 52;

    /** @param active section highlighted (and closing the screen when tapped), or {@link #NONE} */
    public static GlassView build(Context c, AppHost host, ColorRoles roles, View backdrop, int active) {
        GlassView capsule = new GlassView(c, roles);
        capsule.setOnArtwork(true);
        capsule.setCornerRadius(GlassDrawable.CAPSULE);
        capsule.setBackdrop(backdrop);
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER);
        int[] icons = {R.drawable.ic_lyrics, R.drawable.ic_output, R.drawable.ic_queue};
        String[] names = {"Lyrics", "Audio output", "Queue"};
        for (int i = 0; i < 3; i++) {
            final int section = i;
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int p = Ui.dp(c, 14);
            v.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
            boolean on = i == active;
            v.setImageDrawable(Icons.get(c, icons[i], on ? 0xFF151517 : ColorRoles.ON_ART_PRIMARY));
            v.setBackground(on ? Ui.roundRect(0xFFF5F5F7, -1) : Ui.pressable(roles, 0, -1));
            v.setContentDescription(names[i] + (on ? ", selected" : ""));
            v.setOnClickListener(x -> { Haptics.tick(x); select(host, section, active); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 40));
            if (i > 0) lp.setMarginStart(Ui.dp(c, 10));
            row.addView(v, lp);
        }
        capsule.addView(row, new FrameLayout.LayoutParams(-1, -1));
        return capsule;
    }

    private static void select(AppHost host, int section, int active) {
        if (section == OUTPUT) { OutputSwitcher.open(host.activity()); return; }
        if (section == active) { host.nav().dismiss(); return; }
        Runnable open = section == LYRICS ? host::openLyrics : host::openQueue;
        if (active == NONE) { open.run(); return; }
        host.nav().dismiss();
        host.main().post(open);
    }
}
