package dev.lilt.player.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;

/**
 * In-window bottom sheet: scrim + floating glass panel (radius 32, 8dp from the screen edges
 * and above the bottom inset), drag handle, headline title, optional footnote subtitle and any
 * content. Slides up with the gentle spring; drag down (or tap the scrim, or Back via
 * {@link #handleBack()}) to dismiss. Host it in the screen's root FrameLayout so the glass can
 * blur the content behind ({@code backdrop}).
 *
 * <pre>{@code
 * BottomSheet sheet = new BottomSheet(ctx, "Sleep Timer", "Playback pauses with a short fade.");
 * sheet.addOption("Off", true, () -> setTimer(0));
 * sheet.addOption("In 15 minutes", false, () -> setTimer(15));
 * sheet.show(root, contentLayer, bottomInsetPx);
 * // in onBackPressed(): if (sheet.handleBack()) return;
 * }</pre>
 */
public class BottomSheet extends FrameLayout {
    public interface OnDismiss { void onDismiss(); }

    private final ColorRoles roles;
    private final View scrim;
    private final GlassView panel;
    private final LinearLayout body;
    private final CappedScrollView scroller;
    private OnDismiss onDismiss;
    private boolean showing;
    private float downY, startTranslation;

    public BottomSheet(Context c, CharSequence title, CharSequence subtitle) { this(c, Theme.colors(c), title, subtitle); }

    public BottomSheet(Context c, ColorRoles roles, CharSequence title, CharSequence subtitle) {
        super(c);
        this.roles = roles;
        scrim = new View(c);
        scrim.setBackgroundColor(roles.scrim);
        scrim.setOnClickListener(v -> dismiss());
        scrim.setContentDescription("Close");
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        panel = new GlassView(c, roles);
        panel.setCornerRadius(Ui.dpf(c, DesignTokens.RADIUS_SHEET));
        body = Ui.column(c);
        body.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 16));
        View handle = new View(c);
        handle.setBackground(Ui.roundRect(roles.dark ? 0x66FFFFFF : 0x33000000, -1));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(c, 16);
        body.addView(handle, hp);
        if (title != null) {
            TextView t = Type.text(c, TypeScale.HEADLINE, roles.textPrimary, title);
            if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
            body.addView(t);
        }
        if (subtitle != null) body.addView(Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary, subtitle));
        scroller = new CappedScrollView(c);
        scroller.addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        panel.addView(scroller, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        int m = Ui.dp(c, 8);
        LayoutParams pp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        pp.setMargins(m, 0, m, m);
        addView(panel, pp);
        panel.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 28) panel.setAccessibilityPaneTitle(title);
    }

    /** Adds arbitrary content below the title. */
    public BottomSheet addContent(View v) { body.addView(v); return this; }

    /** A 52dp option row with an Ember check when selected; tapping runs {@code action} and closes. */
    public BottomSheet addOption(CharSequence label, boolean selected, Runnable action) {
        ListRow row = new ListRow(getContext(), roles).title(label).accessory(selected ? ListRow.CHECK : ListRow.NONE);
        row.setPaddingRelative(0, 0, 0, 0);
        row.setSelected(selected);
        int count = body.getChildCount();
        if (count > 0 && body.getChildAt(count - 1) instanceof ListRow) ((ListRow) body.getChildAt(count - 1)).separator(true);
        row.setOnClickListener(v -> { Haptics.confirm(v); dismiss(); action.run(); });
        body.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return this;
    }

    public void setOnDismiss(OnDismiss l) { onDismiss = l; }
    public boolean isShowing() { return showing; }
    public GlassView panel() { return panel; }

    /** Adds the sheet to {@code root} and animates it in. */
    public void show(ViewGroup root, View backdrop, int bottomInsetPx) {
        if (showing) return;
        showing = true;
        panel.setBackdrop(backdrop);
        Context c = getContext();
        LayoutParams lp = (LayoutParams) panel.getLayoutParams();
        lp.bottomMargin = Ui.dp(c, 8) + bottomInsetPx;
        int winW = root.getWidth() > 0 ? root.getWidth() : c.getResources().getDisplayMetrics().widthPixels;
        int winH = root.getHeight() > 0 ? root.getHeight() : c.getResources().getDisplayMetrics().heightPixels;
        int side = Ui.dp(c, 8);
        lp.width = ShelfMetrics.panelWidthPx(winW, side, Ui.dp(c, ShelfMetrics.SHEET_MAX_DP));
        int topInset = Ui.dp(c, 24);
        if (android.os.Build.VERSION.SDK_INT >= 23 && root.getRootWindowInsets() != null) topInset = root.getRootWindowInsets().getSystemWindowInsetTop();
        // window height minus insets minus 16dp, less the sheet's own 8dp bottom margin
        scroller.setMaxHeight(ShelfMetrics.maxPanelHeightPx(winH, topInset, bottomInsetPx, Ui.dp(c, 16) + lp.bottomMargin - bottomInsetPx, Ui.dp(c, 120)));
        root.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setAlpha(0f);
        panel.setTranslationY(Ui.dpf(getContext(), 480));
        post(() -> {
            float from = panel.getHeight() + Ui.dpf(getContext(), 16);
            Motion.animate(0f, 1f, Motion.GENTLE_SPRING_MS, Motion.gentleSpring(), p -> {
                scrim.setAlpha(Math.min(1f, p * 1.5f));
                panel.setTranslationY((1 - p) * from);
            });
        });
    }

    /** Animates out and removes itself. */
    public void dismiss() {
        if (!showing) return;
        showing = false;
        float start = panel.getTranslationY(), end = panel.getHeight() + Ui.dpf(getContext(), 16), a0 = scrim.getAlpha();
        Motion.Update u = p -> {
            panel.setTranslationY(start + (end - start) * p);
            scrim.setAlpha(a0 * (1 - p));
            if (p >= 1f) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) parent.removeView(this);
            }
        };
        if (Motion.animate(0f, 1f, Motion.FAST + 50, Motion.accelerate(), u) == null) u.on(1f);
        if (onDismiss != null) onDismiss.onDismiss();
    }

    /** Call from the Activity's back handling; returns true when it closed the sheet. */
    public boolean handleBack() {
        if (!showing) return false;
        dismiss();
        return true;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { downY = e.getRawY(); startTranslation = panel.getTranslationY(); }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && e.getRawY() - downY > Ui.dpf(getContext(), 8) && isOnPanel(e)) return true;
        return false;
    }

    private boolean isOnPanel(MotionEvent e) { return e.getY() >= panel.getTop() + panel.getTranslationY(); }

    @SuppressLint("ClickableViewAccessibility") // dragging is an alternative to the scrim/Back, both accessible
    @Override public boolean onTouchEvent(MotionEvent e) {
        float dy = e.getRawY() - downY;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                panel.setTranslationY(Math.max(0, startTranslation + dy));
                scrim.setAlpha(1f - Math.min(1f, panel.getTranslationY() / Math.max(1f, panel.getHeight())));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (panel.getTranslationY() > panel.getHeight() * 0.3f) { Haptics.gestureEnd(this); dismiss(); }
                else {
                    float from = panel.getTranslationY(), a = scrim.getAlpha();
                    Motion.animate(0f, 1f, Motion.SPRING_MS, Motion.spring(), p -> { panel.setTranslationY(from * (1 - p)); scrim.setAlpha(a + (1 - a) * p); });
                }
                return true;
            default:
                return true;
        }
    }

    /** Check icon used for selected options (exposed for custom rows). */
    public static int checkIcon() { return R.drawable.ic_check; }
}
