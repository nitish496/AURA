package dev.lilt.player.app.chrome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import dev.lilt.player.R;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import java.util.ArrayList;
import java.util.List;

/**
 * Typographic artwork for generated mixes ("Heavy Rotation", "Rediscover"): a flat tint taken from
 * the mix's artwork (graphite when there is none), the AURA mark with a letter-spaced "MIX" label
 * and the title in Inter Display Bold at the bottom left. Proportions follow the prototype.
 */
public final class MixArt extends Drawable {
    private final Paint fill = new Paint(), label = new Paint(Paint.ANTI_ALIAS_FLAG), titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable mark;
    private final String title;
    private final List<String> lines = new ArrayList<>();
    private float titleX, titleBaseline, lineHeight, labelX, labelBaseline;

    public MixArt(Context c, String title, int color) {
        this.title = title;
        fill.setColor(color);
        mark = c.getDrawable(R.drawable.aura_logo).mutate();
        label.setTypeface(Type.typeface(c, TypeScale.CAPTION1.weight(600)));
        label.setColor(0xD9F5F5F7);
        label.setLetterSpacing(0.12f);
        titlePaint.setTypeface(Type.typeface(c, TypeScale.LARGE_TITLE));
        titlePaint.setColor(0xFFF5F5F7);
        titlePaint.setLetterSpacing(-0.025f);
    }

    public void setColor(int color) { fill.setColor(color); invalidateSelf(); }

    @Override protected void onBoundsChange(Rect b) {
        float w = b.width();
        if (w <= 0) return;
        float margin = w * 0.08f;
        titlePaint.setTextSize(w * 0.115f);
        label.setTextSize(w * 0.046f);
        lineHeight = w * 0.115f * 0.96f;
        lines.clear();
        float max = w - 2 * margin;
        StringBuilder line = new StringBuilder();
        for (String word : title.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && titlePaint.measureText(candidate) > max) { lines.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(candidate);
        }
        if (line.length() > 0) lines.add(line.toString());
        titleX = b.left + margin;
        titleBaseline = b.bottom - margin - titlePaint.descent() - (lines.size() - 1) * lineHeight;
        int markSize = Math.round(w * 0.13f);
        int mx = Math.round(b.left + margin - markSize * 0.22f), my = Math.round(b.top + margin - markSize * 0.22f);
        mark.setBounds(mx, my, mx + markSize, my + markSize);
        labelX = b.left + margin + markSize * 0.6f + w * 0.02f;
        labelBaseline = b.top + margin + markSize * 0.22f + label.getTextSize() * 0.62f;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.drawRect(b, fill);
        mark.draw(canvas);
        canvas.drawText("MIX", labelX, labelBaseline, label);
        for (int i = 0; i < lines.size(); i++) canvas.drawText(lines.get(i), titleX, titleBaseline + i * lineHeight, titlePaint);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
