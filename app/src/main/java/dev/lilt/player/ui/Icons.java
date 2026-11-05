package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

/**
 * Tinted icons from the AURA family ({@code R.drawable.ic_*}, generated from
 * design/src/icons.mjs). Every icon is a 24dp VectorDrawable drawn in white; tint decides colour.
 *
 * <pre>{@code
 * image.setImageDrawable(Icons.get(ctx, R.drawable.ic_heart_fill, roles.accent));
 * }</pre>
 * Use {@link dev.lilt.player.GlyphDrawable} where no Context is available (custom drawing).
 */
public final class Icons {
    private Icons() {}

    /** A mutated, tinted copy of {@code resId}. */
    public static Drawable get(Context c, int resId, int color) {
        Drawable d = c.getDrawable(resId);
        if (d == null) throw new IllegalArgumentException("missing drawable " + resId);
        d = d.mutate();
        d.setTint(color);
        return d;
    }

    /** As {@link #get(Context, int, int)} with bounds set to {@code sizePx} square (for compound drawables). */
    public static Drawable get(Context c, int resId, int color, int sizePx) {
        Drawable d = get(c, resId, color);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }
}
