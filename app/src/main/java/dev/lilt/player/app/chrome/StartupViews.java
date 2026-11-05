package dev.lilt.player.app.chrome;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import dev.lilt.player.R;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Notice;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/** The two full-window states before the app proper: the minimal startup mark and the first-run permission screen. */
public final class StartupViews {
    private StartupViews() {}

    /** The AURA mark, centred, shown only while the first library scan runs. */
    public static FrameLayout startup(Context c, ColorRoles roles) {
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundColor(roles.canvas);
        ImageView mark = new ImageView(c);
        mark.setImageDrawable(Icons.get(c, R.drawable.aura_logo, roles.textPrimary));
        mark.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        f.addView(mark, new FrameLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88), Gravity.CENTER));
        f.setClickable(true);
        f.setContentDescription("AURA");
        return f;
    }

    /**
     * First run without audio access: why AURA asks, the one thing to tap, and a way to carry on
     * with streaming only. Left aligned, no illustration.
     */
    public static FrameLayout permission(Context c, ColorRoles roles, int topInset, int bottomInset, Runnable allow, Runnable skip) {
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundColor(roles.canvas);
        f.setClickable(true);
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = Ui.column(c);
        int m = Ui.dp(c, 24);
        col.setPadding(m, topInset + Ui.dp(c, 32), m, bottomInset + Ui.dp(c, 24));
        ImageView mark = new ImageView(c);
        mark.setImageDrawable(Icons.get(c, R.drawable.aura_logo, roles.textPrimary));
        mark.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        col.addView(mark, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
        android.view.View spacer = new android.view.View(c);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        col.addView(Type.text(c, TypeScale.TITLE1, roles.textPrimary, "Allow access to your music"));
        android.widget.TextView body = Type.text(c, TypeScale.BODY, roles.textSecondary,
                "AURA plays the audio files already on this phone. Nothing is uploaded, and you can change this in Settings.");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = Ui.dp(c, 12);
        col.addView(body, bp);
        PillButton allowButton = new PillButton(c, roles, PillButton.FILLED, "Allow Access", 0);
        allowButton.setOnClickListener(v -> allow.run());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = Ui.dp(c, 28);
        col.addView(allowButton, ap);
        PillButton skipButton = new PillButton(c, roles, PillButton.TEXT, "Continue without local music", 0);
        skipButton.setOnClickListener(v -> skip.run());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Ui.dp(c, 4);
        col.addView(skipButton, sp);
        Notice notice = new Notice(c, roles, R.drawable.ic_cloud_check, "Stream from Browse",
                "Songs you save stream on demand and play offline once heard.", null, null);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = Ui.dp(c, 28);
        col.addView(notice, np);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        f.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }
}
