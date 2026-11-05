package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import dev.lilt.player.GlyphDrawable;

/**
 * Typographic placeholder for missing artwork: graphite with a deterministic tonal shift and up
 * to two initials in Inter Display Bold (34% of the size), or the AURA mark when the title has
 * no letters. Use {@code circle = true} for artists. Corner radius follows the artwork rule.
 *
 * <pre>{@code imageView.setImageDrawable(new PlaceholderDrawable(ctx, album.title, false)); }</pre>
 */
public final class PlaceholderDrawable extends Drawable {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String initials;
    private final boolean circle;
    private final float density;
    private final RectF rect = new RectF();
    private final GlyphDrawable mark;

    public PlaceholderDrawable(Context c, String title, boolean circle) { this(c, Theme.colors(c), title, circle); }

    public PlaceholderDrawable(Context c, ColorRoles roles, String title, boolean circle) {
        this.initials = Initials.of(title);
        this.circle = circle;
        this.density = c.getResources().getDisplayMetrics().density;
        bg.setColor(Initials.background(title == null ? "" : title, roles));
        text.setColor(roles.placeholderText);
        text.setTypeface(Type.typeface(c, TypeScale.LARGE_TITLE));
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(-0.02f);
        mark = initials.isEmpty() ? new GlyphDrawable("aura", roles.placeholderText, 0) : null;
    }

    @Override public void draw(Canvas canvas) {
        rect.set(getBounds());
        float size = Math.min(rect.width(), rect.height());
        if (circle) canvas.drawOval(rect, bg);
        else {
            float r = dev.lilt.player.DesignTokens.artworkRadius(Math.round(size / density)) * density;
            canvas.drawRoundRect(rect, r, r, bg);
        }
        if (mark != null) {
            int m = Math.round(size * 0.36f), cx = Math.round(rect.centerX()), cy = Math.round(rect.centerY());
            mark.setBounds(cx - m / 2, cy - m / 2, cx + m / 2, cy + m / 2);
            mark.draw(canvas);
            return;
        }
        text.setTextSize(size * 0.34f);
        Paint.FontMetrics fm = text.getFontMetrics();
        canvas.drawText(initials, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, text);
    }

    @Override public void setAlpha(int alpha) { bg.setAlpha(alpha); text.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { bg.setColorFilter(cf); invalidateSelf(); }
    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
