package dev.lilt.player.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.TextView;
import dev.lilt.player.R;

/**
 * Applies {@link TypeScale} styles to TextViews with the bundled Inter / Inter Display fonts
 * (res/font, OFL — see docs/licenses). Typefaces load once per process and are cached.
 *
 * <pre>{@code
 * TextView title = Type.text(ctx, TypeScale.LARGE_TITLE, roles.textPrimary, "Library");
 * TextView time = Type.text(ctx, TypeScale.CAPTION1, roles.textSecondary);
 * Type.tabular(time);
 * }</pre>
 */
public final class Type {
    private Type() {}

    private static Typeface regular, medium, semibold, bold, displayBold;

    /** The typeface for {@code style} (falls back to the system sans if a font fails to load). */
    public static synchronized Typeface typeface(Context c, TypeScale.Style style) {
        if (regular == null) load(c.getApplicationContext().getResources());
        if (style.display && style.weight >= 700) return displayBold;
        if (style.weight >= 700) return bold;
        if (style.weight >= 600) return semibold;
        if (style.weight >= 500) return medium;
        return regular;
    }

    private static void load(Resources r) {
        regular = font(r, R.font.inter_regular, Typeface.NORMAL);
        medium = font(r, R.font.inter_medium, Typeface.NORMAL);
        semibold = font(r, R.font.inter_semibold, Typeface.BOLD);
        bold = font(r, R.font.inter_bold, Typeface.BOLD);
        displayBold = font(r, R.font.inter_display_bold, Typeface.BOLD);
    }

    private static Typeface font(Resources r, int id, int fallbackStyle) {
        try {
            return r.getFont(id);
        } catch (RuntimeException e) {
            return Typeface.create(Typeface.SANS_SERIF, fallbackStyle);
        }
    }

    /** A new TextView in {@code style} and {@code color}. */
    public static TextView text(Context c, TypeScale.Style style, int color) {
        TextView t = new TextView(c);
        apply(t, style);
        t.setTextColor(color);
        return t;
    }

    public static TextView text(Context c, TypeScale.Style style, int color, CharSequence value) {
        TextView t = text(c, style, color);
        t.setText(value);
        return t;
    }

    /** Sets typeface, size (sp), tracking and line height; removes legacy font padding. */
    public static void apply(TextView t, TypeScale.Style style) {
        t.setTypeface(typeface(t.getContext(), style));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.size);
        t.setLetterSpacing(style.tracking);
        t.setIncludeFontPadding(false);
        float lineHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.lineHeight, t.getResources().getDisplayMetrics());
        Paint.FontMetrics fm = t.getPaint().getFontMetrics();
        // Never negative: older StaticLayouts apply the extra to the last line and would clip descenders.
        t.setLineSpacing(Math.max(0f, TypeScale.extraLineSpacing(lineHeightPx, fm.ascent, fm.descent)), 1f);
    }

    /** Tabular figures, for every time, count and rank ("1:24", "−2:48", "1,248"). */
    public static void tabular(TextView t) { t.setFontFeatureSettings("tnum"); }

    /** One line, ellipsized at the end. */
    public static void singleLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** At most {@code lines} lines, ellipsized at the end. */
    public static void maxLines(TextView t, int lines) {
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }
}
