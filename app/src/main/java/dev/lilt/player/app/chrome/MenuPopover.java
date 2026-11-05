package dev.lilt.player.app.chrome;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.app.MenuAction;
import dev.lilt.player.ui.CappedScrollView;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.List;

/**
 * Glass context menu: a dimmed window and a floating glass panel (radius 22) anchored beside the
 * view that opened it. Rows are 46dp with the action's icon trailing, groups separated by a darker
 * band, as in the prototype. Spring-in from the anchor corner; tapping outside dismisses.
 */
public final class MenuPopover extends FrameLayout {
    private final ColorRoles roles;
    private final View scrim;
    private final GlassView panel;
    private final CappedScrollView scroller;
    private boolean closing;
    private Runnable onClosed;

    public MenuPopover(Context c, ColorRoles roles, View backdrop, List<MenuAction> actions) {
        super(c);
        this.roles = roles;
        scrim = new View(c);
        scrim.setBackgroundColor(roles.scrim);
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        panel = new GlassView(c, roles);
        panel.setCornerRadius(Ui.dpf(c, 22));
        panel.setBackdrop(backdrop);
        LinearLayout column = Ui.column(c);
        int p = Ui.dp(c, 8);
        column.setPadding(p, p, p, p);
        for (MenuAction a : actions) {
            if (a.groupStart && column.getChildCount() > 0) {
                View sep = new View(c);
                sep.setBackgroundColor(roles.dark ? 0x2E000000 : 0x14000000);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 8));
                lp.setMargins(-p, Ui.dp(c, 4), -p, Ui.dp(c, 4));
                column.addView(sep, lp);
            }
            column.addView(row(c, a), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 46)));
        }
        scroller = new CappedScrollView(c);
        scroller.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(scroller, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        addView(panel, new LayoutParams(Ui.dp(c, 264), LayoutParams.WRAP_CONTENT));
        setClickable(true);
    }

    private View row(Context c, MenuAction a) {
        LinearLayout row = Ui.row(c);
        row.setPaddingRelative(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        row.setBackground(Ui.pressable(roles, 0, Ui.dpf(c, 14)));
        int color = a.destructive ? roles.error : roles.textPrimary;
        TextView label = Type.text(c, TypeScale.BODY, color, a.label);
        Type.singleLine(label);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (a.icon != null) {
            ImageView icon = new ImageView(c);
            int res = c.getResources().getIdentifier("ic_" + a.icon, "drawable", c.getPackageName());
            if (res != 0) icon.setImageDrawable(dev.lilt.player.ui.Icons.get(c, res, color));
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        }
        row.setClickable(true);
        row.setContentDescription(a.label);
        row.setOnClickListener(v -> {
            Haptics.click(v);
            dismiss();
            a.run.run();
        });
        return row;
    }

    public void setOnClosed(Runnable r) { onClosed = r; }

    /** Adds the popover to {@code host} (a full-window FrameLayout) next to {@code anchor}. */
    public void show(ViewGroup host, View anchor, int topInset, int bottomInset) {
        Context c = getContext();
        host.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int[] a = new int[2], h = new int[2];
        anchor.getLocationInWindow(a);
        host.getLocationInWindow(h);
        int ax = a[0] - h[0], ay = a[1] - h[1], aw = anchor.getWidth(), ah = anchor.getHeight();
        int width = Math.min(Ui.dp(c, 264), host.getWidth() > 0 ? host.getWidth() - Ui.dp(c, 32) : Ui.dp(c, 264));
        int hostW = host.getWidth() > 0 ? host.getWidth() : c.getResources().getDisplayMetrics().widthPixels;
        int hostH = host.getHeight() > 0 ? host.getHeight() : c.getResources().getDisplayMetrics().heightPixels;
        int margin = Ui.dp(c, 16), gap = Ui.dp(c, 6);
        scroller.setMaxHeight(ShelfMetrics.maxPanelHeightPx(hostH, topInset, bottomInset, margin, Ui.dp(c, 96)));
        panel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int height = panel.getMeasuredHeight();
        boolean alignRight = ax + aw / 2 > hostW / 2;
        int x = alignRight ? ax + aw - width : ax;
        x = Math.max(margin, Math.min(hostW - margin - width, x));
        int below = ay + ah + gap, above = ay - gap - height;
        int y = below + height <= hostH - bottomInset - margin ? below : above >= topInset + margin ? above : Math.max(topInset + margin, hostH - bottomInset - margin - height);
        LayoutParams lp = (LayoutParams) panel.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = x;
        lp.topMargin = y;
        panel.setLayoutParams(lp);
        panel.setPivotX(alignRight ? width : 0);
        panel.setPivotY(y == below ? 0 : height);
        scrim.setAlpha(0f);
        panel.setAlpha(0f);
        panel.setScaleX(0.86f);
        panel.setScaleY(0.86f);
        Motion.animate(0f, 1f, Motion.SPRING_MS, Motion.spring(), t -> {
            scrim.setAlpha(Math.min(1f, t * 1.4f));
            panel.setAlpha(Math.min(1f, t * 1.6f));
            panel.setScaleX(0.86f + 0.14f * t);
            panel.setScaleY(0.86f + 0.14f * t);
        });
    }

    public boolean isClosing() { return closing; }

    public void dismiss() {
        if (closing) return;
        closing = true;
        Motion.Update u = t -> {
            scrim.setAlpha(1 - t);
            panel.setAlpha(1 - t);
            panel.setScaleX(1 - 0.06f * t);
            panel.setScaleY(1 - 0.06f * t);
            if (t >= 1f) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) parent.removeView(this);
                if (onClosed != null) onClosed.run();
            }
        };
        if (Motion.animate(0f, 1f, Motion.FAST, Motion.accelerate(), u) == null) { /* animations off: update(1) already ran */ }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) dismiss();
        return true;
    }
}
