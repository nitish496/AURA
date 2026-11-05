package dev.lilt.player.app.chrome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.ui.CappedScrollView;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Motion;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.ShelfMetrics;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;
import java.util.function.Consumer;

/** Glass alert for short text prompts (new playlist, rename) and confirmations (delete, clear). */
public final class GlassDialog extends FrameLayout {
    private final ColorRoles roles;
    private final View scrim;
    private final GlassView panel;
    private final LinearLayout column;
    private final CappedScrollView scroller;
    private EditText input;
    private boolean closing, promptMode;
    private Runnable onClosed;

    private GlassDialog(Context c, ColorRoles roles, View backdrop, String title, String message) {
        super(c);
        this.roles = roles;
        scrim = new View(c);
        scrim.setBackgroundColor(roles.scrim);
        scrim.setOnClickListener(v -> dismiss());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        panel = new GlassView(c, roles);
        panel.setCornerRadius(Ui.dpf(c, 28));
        panel.setBackdrop(backdrop);
        panel.setClickable(true);
        column = Ui.column(c);
        int p = Ui.dp(c, 24);
        column.setPadding(p, p, p, Ui.dp(c, 20));
        TextView t = Type.text(c, TypeScale.TITLE3, roles.textPrimary, title);
        column.addView(t);
        if (message != null) {
            TextView m = Type.text(c, TypeScale.SUBHEAD, roles.textSecondary, message);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(c, 8);
            column.addView(m, lp);
        }
        scroller = new CappedScrollView(c);
        scroller.addView(column, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroller, new FrameLayout.LayoutParams(-1, -2));
        addView(panel, new LayoutParams(Ui.dp(c, 320), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setClickable(true);
    }

    private void buttons(String actionLabel, boolean destructive, Runnable action) {
        Context c = getContext();
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        PillButton cancel = new PillButton(c, roles, PillButton.SECONDARY, "Cancel", 0);
        cancel.setOnClickListener(v -> dismiss());
        PillButton ok = new PillButton(c, roles, destructive ? PillButton.SECONDARY : PillButton.FILLED, actionLabel, 0);
        if (destructive) ok.setTextColor(roles.error);
        ok.setOnClickListener(v -> action.run());
        row.addView(cancel, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Ui.dp(c, 8));
        row.addView(ok, lp);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(c, 20);
        column.addView(row, rl);
    }

    public static GlassDialog confirm(Context c, ColorRoles roles, View backdrop, String title, String message,
                                      String actionLabel, boolean destructive, Runnable onConfirm) {
        GlassDialog d = new GlassDialog(c, roles, backdrop, title, message);
        d.buttons(actionLabel, destructive, () -> { d.dismiss(); onConfirm.run(); });
        return d;
    }

    /** Current text of a prompt dialog ({@code null} for other dialogs). */
    public String text() { return input == null ? null : input.getText().toString(); }

    public static GlassDialog prompt(Context c, ColorRoles roles, View backdrop, String title, String hint, String initial,
                                     String actionLabel, Consumer<String> onDone) {
        GlassDialog d = new GlassDialog(c, roles, backdrop, title, null);
        d.promptMode = true;
        EditText e = new EditText(c);
        e.setBackground(Ui.roundRect(roles.fill, Ui.dpf(c, 12)));
        e.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        Type.apply(e, TypeScale.BODY);
        e.setTextColor(roles.textPrimary);
        e.setHintTextColor(roles.textTertiary);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setText(initial == null ? "" : initial);
        e.setSelectAllOnFocus(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(80)});
        e.setHighlightColor((roles.accent & 0x00FFFFFF) | 0x40000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 48));
        lp.topMargin = Ui.dp(c, 16);
        d.column.addView(e, lp);
        d.input = e;
        Runnable submit = () -> {
            String text = e.getText().toString().trim();
            if (text.isEmpty()) return;
            d.dismiss();
            onDone.accept(text);
        };
        e.setOnEditorActionListener((v, action, ev) -> { if (action == EditorInfo.IME_ACTION_DONE) { submit.run(); return true; } return false; });
        d.buttons(actionLabel, false, submit);
        return d;
    }

    public void setOnClosed(Runnable r) { onClosed = r; }

    public void show(ViewGroup host, int topInset) {
        Context c = getContext();
        host.addView(this, new ViewGroup.LayoutParams(-1, -1));
        LayoutParams lp = (LayoutParams) panel.getLayoutParams();
        int hostW = host.getWidth() > 0 ? host.getWidth() : c.getResources().getDisplayMetrics().widthPixels;
        int hostH = host.getHeight() > 0 ? host.getHeight() : c.getResources().getDisplayMetrics().heightPixels;
        lp.width = ShelfMetrics.panelWidthPx(hostW, Ui.dp(c, 24), Ui.dp(c, 340));
        int top = 0;
        if (promptMode) {
            // In short windows the keyboard needs the lower half: sit at the top, below the status bar.
            top = hostH < Ui.dp(c, 480) ? topInset + Ui.dp(c, 8) : topInset + Ui.dp(c, 72);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = top;
        }
        scroller.setMaxHeight(ShelfMetrics.maxPanelHeightPx(hostH, top > 0 ? top : topInset, top > 0 ? 0 : topInset, Ui.dp(c, 16), Ui.dp(c, 120)));
        panel.setLayoutParams(lp);
        scrim.setAlpha(0f);
        panel.setAlpha(0f);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        Motion.animate(0f, 1f, Motion.SPRING_MS, Motion.spring(), t -> {
            scrim.setAlpha(Math.min(1f, t * 1.4f));
            panel.setAlpha(Math.min(1f, t * 1.6f));
            panel.setScaleX(0.92f + 0.08f * t);
            panel.setScaleY(0.92f + 0.08f * t);
        });
        if (input != null) {
            input.requestFocus();
            input.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && input.isAttachedToWindow()) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 120);
        }
    }

    public void dismiss() {
        if (closing) return;
        closing = true;
        if (input != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        Motion.Update u = t -> {
            scrim.setAlpha(1 - t);
            panel.setAlpha(1 - t);
            if (t >= 1f) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) parent.removeView(this);
                if (onClosed != null) onClosed.run();
            }
        };
        Motion.animate(0f, 1f, Motion.FAST, Motion.accelerate(), u);
    }
}
