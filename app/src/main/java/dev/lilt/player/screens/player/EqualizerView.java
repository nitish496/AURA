package dev.lilt.player.screens.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * The equaliser graph: one draggable point per band of the phone's real equaliser, joined by a
 * smooth curve, with dB gridlines and band frequencies. The band count and level range come from
 * the device ({@link #setData}); nothing is assumed. Drag a point vertically (0.5 dB steps, snaps
 * to 0 dB). No allocation while drawing.
 */
public final class EqualizerView extends View {
    public interface Listener {
        /** New levels in millibels; {@code done} when the finger lifted. */
        void onLevels(short[] levels, boolean done);
    }

    private final float density;
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), curve = new Paint(Paint.ANTI_ALIAS_FLAG),
            thumb = new Paint(Paint.ANTI_ALIAS_FLAG), thumbRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int[] freqs = new int[0];
    private short[] levels = new short[0];
    private String[] freqLabels = new String[0];
    private float[] xs = new float[0], ys = new float[0];
    private int minMb = -1500, maxMb = 1500;
    private String topLabel = "+15 dB", midLabel = "0 dB", bottomLabel = "−15 dB";
    private int dragBand = -1, lastTickLevel = Integer.MIN_VALUE;
    private boolean active = true;
    private Listener listener;
    private final float left, right, top, bottomPad;

    public EqualizerView(Context c, ColorRoles roles) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        left = 52 * density; right = 20 * density; top = 14 * density; bottomPad = 32 * density;
        grid.setColor(roles.separator);
        grid.setStrokeWidth(Math.max(1f, density));
        curve.setColor(roles.accent);
        curve.setStyle(Paint.Style.STROKE);
        curve.setStrokeWidth(2.5f * density);
        curve.setStrokeCap(Paint.Cap.ROUND);
        curve.setStrokeJoin(Paint.Join.ROUND);
        thumb.setColor(roles.textPrimary);
        thumbRing.setColor(roles.surface);
        label.setTypeface(Type.typeface(c, TypeScale.CAPTION1));
        label.setTextSize(TypeScale.CAPTION1.size * c.getResources().getDisplayMetrics().scaledDensity);
        label.setColor(roles.textTertiary);
        label.setFontFeatureSettings("tnum");
        setContentDescription("Equaliser graph. Drag a point up or down to change a band.");
    }

    public void setListener(Listener l) { listener = l; }

    /** Greys the graph when the equaliser is switched off. */
    public void setActive(boolean on) {
        active = on;
        setAlpha(on ? 1f : 0.4f);
    }

    /** Device bands: centre frequencies (Hz), current levels and level range (millibels). */
    public void setData(int[] frequencies, short[] bandLevels, int minLevelMb, int maxLevelMb) {
        if (dragBand >= 0) return;
        freqs = frequencies == null ? new int[0] : frequencies.clone();
        levels = bandLevels == null || bandLevels.length != freqs.length ? new short[freqs.length] : bandLevels.clone();
        minMb = minLevelMb;
        maxMb = maxLevelMb > minLevelMb ? maxLevelMb : minLevelMb + 1;
        freqLabels = new String[freqs.length];
        for (int i = 0; i < freqs.length; i++) freqLabels[i] = EqMath.frequency(freqs[i]) + (i == freqs.length - 1 ? " Hz" : "");
        xs = new float[freqs.length];
        ys = new float[freqs.length];
        topLabel = EqMath.gain(maxMb);
        midLabel = "0 dB";
        bottomLabel = EqMath.gain(minMb);
        layoutPoints();
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 300), w), Ui.dp(getContext(), 208));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layoutPoints(); }

    private float plotTop() { return top; }
    private float plotHeight() { return Math.max(1, getHeight() - top - bottomPad); }

    private void layoutPoints() {
        int n = freqs.length;
        float plotW = getWidth() - left - right;
        for (int i = 0; i < n; i++) {
            xs[i] = left + plotW * (i + 0.5f) / n;
            ys[i] = EqMath.y(levels[i], minMb, maxMb, plotTop(), plotHeight());
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        float t = plotTop(), h = plotHeight(), r = getWidth() - right * 0.4f;
        float zero = EqMath.y(0, minMb, maxMb, t, h);
        canvas.drawLine(left, t, r, t, grid);
        canvas.drawLine(left, zero, r, zero, grid);
        canvas.drawLine(left, t + h, r, t + h, grid);
        label.setTextAlign(Paint.Align.RIGHT); // right-aligned in the gutter so text never meets the grid line
        float lx = left - 8 * density;
        canvas.drawText(topLabel, lx, t + 4 * density, label);
        canvas.drawText(midLabel, lx, zero + 4 * density, label);
        canvas.drawText(bottomLabel, lx, t + h + 4 * density, label);
        int n = xs.length;
        if (n == 0) return;
        path.rewind();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float mx = (xs[i - 1] + xs[i]) / 2f;
            path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i]);
        }
        canvas.drawPath(path, curve);
        label.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float rad = (i == dragBand ? 11 : 8) * density;
            canvas.drawCircle(xs[i], ys[i], rad + 2 * density, thumbRing);
            canvas.drawCircle(xs[i], ys[i], rad, thumb);
            canvas.drawText(freqLabels[i], xs[i], getHeight() - 8 * density, label);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!active || xs.length == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragBand = EqMath.nearestBand(e.getX(), xs);
                lastTickLevel = Integer.MIN_VALUE;
                moveTo(e.getY(), false);
                return true;
            case MotionEvent.ACTION_MOVE:
                moveTo(e.getY(), false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                moveTo(e.getY(), true);
                dragBand = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void moveTo(float y, boolean done) {
        if (dragBand < 0) return;
        short level = EqMath.levelAt(y, minMb, maxMb, plotTop(), plotHeight());
        if (level != levels[dragBand]) {
            levels[dragBand] = level;
            ys[dragBand] = EqMath.y(level, minMb, maxMb, plotTop(), plotHeight());
            if (level == 0 && lastTickLevel != 0) Haptics.tick(this);
            lastTickLevel = level;
            invalidate();
        }
        if (listener != null) listener.onLevels(levels.clone(), done);
    }

    @Override public boolean performClick() { return super.performClick(); }
}
