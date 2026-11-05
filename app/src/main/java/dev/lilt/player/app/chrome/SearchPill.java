package dev.lilt.player.app.chrome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * The glass Search control. A 64dp (48dp when the tab bar is minimised) circle with a magnifier;
 * when the Search tab is open it widens into a text field in place (the circle morphs into a
 * capsule), as in the prototype. The field is a real EditText: IME "search" action, clear button,
 * debounced callbacks are the screen's job.
 */
public final class SearchPill extends GlassView {
    public interface Listener {
        void onTap();
        void onQuery(String query, boolean submitted);
    }

    private final ColorRoles roles;
    private final ImageView icon, clear;
    private final EditText edit;
    private Listener listener;
    private float field; // 0 circle, 1 capsule
    private boolean silent;

    public SearchPill(Context c, ColorRoles roles) {
        super(c, roles);
        this.roles = roles;
        setClipChildren(false);
        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(icon);
        edit = new EditText(c);
        edit.setBackground(null);
        edit.setPadding(0, 0, 0, 0);
        Type.apply(edit, TypeScale.BODY);
        edit.setTextColor(roles.textPrimary);
        edit.setHintTextColor(roles.textTertiary);
        edit.setHint("Artists, songs, albums");
        edit.setSingleLine(true);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setHighlightColor((roles.accent & 0x00FFFFFF) | 0x40000000);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = Ui.roundRect(roles.accent, Ui.dpf(c, 1));
            cursor.setSize(Ui.dp(c, 2), Ui.dp(c, 22));
            edit.setTextCursorDrawable(cursor);
        }
        edit.setContentDescription("Search");
        edit.setVisibility(INVISIBLE);
        edit.setFocusable(false);
        edit.setFocusableInTouchMode(false);
        addView(edit);
        clear = new ImageView(c);
        clear.setScaleType(ImageView.ScaleType.CENTER);
        clear.setBackground(Ui.oval(roles.fillStrong));
        clear.setImageDrawable(Icons.get(c, R.drawable.ic_close, roles.textSecondary));
        clear.setContentDescription("Clear search");
        clear.setVisibility(GONE);
        clear.setOnClickListener(v -> { edit.setText(""); });
        addView(clear);
        icon.setImageDrawable(Icons.get(c, R.drawable.ic_search, roles.textPrimary));
        setContentDescription("Search");
        setClickable(true);
        setOnClickListener(v -> { if (field < 0.5f && listener != null) listener.onTap(); });
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 && field > 0.5f ? VISIBLE : GONE);
                if (!silent && listener != null) listener.onQuery(s.toString(), false);
            }
        });
        edit.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                if (listener != null) listener.onQuery(edit.getText().toString(), true);
                return true;
            }
            return false;
        });
    }

    public void setListener(Listener l) { listener = l; }

    public String query() { return edit.getText().toString(); }

    /** Sets the text without notifying the listener. */
    public void setQuerySilently(String q) {
        silent = true;
        edit.setText(q == null ? "" : q);
        edit.setSelection(edit.getText().length());
        silent = false;
    }

    /** 0 = circle, 1 = text field. */
    public void setField(float f) {
        field = f;
        boolean on = f > 0.05f;
        edit.setVisibility(on ? VISIBLE : INVISIBLE);
        edit.setFocusable(f > 0.5f);
        edit.setFocusableInTouchMode(f > 0.5f);
        edit.setAlpha(Math.max(0f, (f - 0.4f) / 0.6f));
        clear.setAlpha(edit.getAlpha());
        clear.setVisibility(f > 0.5f && edit.length() > 0 ? VISIBLE : GONE);
        icon.setImageDrawable(Icons.get(getContext(), R.drawable.ic_search, f > 0.5f ? roles.textSecondary : roles.textPrimary));
    }

    public void focusField() {
        if (field < 0.5f) return;
        edit.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        edit.clearFocus();
    }

    public boolean keyboardFocused() { return edit.hasFocus(); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        int ic = Ui.dp(getContext(), field > 0.5f ? DesignTokens.ICON_SMALL : DesignTokens.ICON);
        icon.measure(MeasureSpec.makeMeasureSpec(ic, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ic, MeasureSpec.EXACTLY));
        int cl = Ui.dp(getContext(), 28);
        clear.measure(MeasureSpec.makeMeasureSpec(cl, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cl, MeasureSpec.EXACTLY));
        int editW = Math.max(0, w - Ui.dp(getContext(), 16 + 20 + 10 + 8 + 28 + 8));
        edit.measure(MeasureSpec.makeMeasureSpec(editW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t, c16 = Ui.dp(getContext(), 16);
        int ic = icon.getMeasuredWidth();
        int centred = (w - ic) / 2;
        int x = Math.round(centred + (c16 - centred) * field);
        icon.layout(x, (h - ic) / 2, x + ic, (h + ic) / 2);
        int ex = c16 + Ui.dp(getContext(), 20 + 10);
        edit.layout(ex, 0, ex + edit.getMeasuredWidth(), h);
        int cl = clear.getMeasuredWidth();
        clear.layout(w - Ui.dp(getContext(), 10) - cl, (h - cl) / 2, w - Ui.dp(getContext(), 10), (h + cl) / 2);
    }

}
