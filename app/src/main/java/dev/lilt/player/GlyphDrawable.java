package dev.lilt.player;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import dev.lilt.player.ui.IconPaths;
import dev.lilt.player.ui.SvgPath;
import java.util.HashMap;
import java.util.Map;

/**
 * Context-free icon drawable for the AURA icon family. It draws the same paths as the
 * {@code res/drawable/ic_*} vectors (both are generated from design/src/icons.mjs), so it suits
 * custom views and code without a Context. Unknown names draw nothing.
 */
public final class GlyphDrawable extends Drawable {
    private static final Map<String, Path[]> CACHE = new HashMap<>();

    private final IconPaths.Glyph glyph;
    private final Path[] paths;
    private final int size;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlyphDrawable(String name, int color, int size) {
        this.glyph = IconPaths.get(name);
        this.paths = glyph == null ? new Path[0] : paths(glyph);
        this.size = size;
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(IconPaths.STROKE);
    }

    private static synchronized Path[] paths(IconPaths.Glyph g) {
        Path[] cached = CACHE.get(g.name);
        if (cached != null) return cached;
        Path[] out = new Path[g.layers()];
        for (int i = 0; i < out.length; i++) {
            Path p = toPath(SvgPath.parse(g.path(i)));
            if (g.mode(i) == IconPaths.FILL_EVEN_ODD) p.setFillType(Path.FillType.EVEN_ODD);
            out[i] = p;
        }
        CACHE.put(g.name, out);
        return out;
    }

    private static Path toPath(SvgPath s) {
        Path p = new Path();
        int k = 0;
        for (int i = 0; i < s.opCount(); i++) {
            switch (s.op(i)) {
                case SvgPath.MOVE: p.moveTo(s.coord(k), s.coord(k + 1)); break;
                case SvgPath.LINE: p.lineTo(s.coord(k), s.coord(k + 1)); break;
                case SvgPath.QUAD: p.quadTo(s.coord(k), s.coord(k + 1), s.coord(k + 2), s.coord(k + 3)); break;
                case SvgPath.CUBIC: p.cubicTo(s.coord(k), s.coord(k + 1), s.coord(k + 2), s.coord(k + 3), s.coord(k + 4), s.coord(k + 5)); break;
                default: p.close(); break;
            }
            k += SvgPath.arity(s.op(i));
        }
        return p;
    }

    public void setColor(int color) { paint.setColor(color); invalidateSelf(); }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }

    @Override public void draw(Canvas canvas) {
        if (glyph == null) return;
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        for (int i = 0; i < paths.length; i++) {
            int mode = glyph.mode(i);
            if (mode != IconPaths.STROKE_ONLY) { paint.setStyle(Paint.Style.FILL); canvas.drawPath(paths[i], paint); }
            if (mode == IconPaths.STROKE_ONLY || mode == IconPaths.FILL_AND_STROKE) { paint.setStyle(Paint.Style.STROKE); canvas.drawPath(paths[i], paint); }
        }
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** Maps the 1.x text glyphs to icon names (null when the text is not an icon). */
    public static String name(String text) {
        switch (text) {
            case "▶": return "play";
            case "Ⅱ": return "pause";
            case "›": case "▶▶": return "next";
            case "◀◀": return "previous";
            case "•••": return "more";
            case "⌄": return "down";
            case "↻": return "refresh";
            case "shuffle": return "shuffle";
            case "repeat": return "repeat";
            case "repeat_one": return "repeat_one";
            default: return null;
        }
    }
}
