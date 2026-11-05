package dev.lilt.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Switch: 51 x 31dp track, 27dp thumb, Ember when on. Animates with the spring, ticks a haptic,
 * reports itself to accessibility as a Switch. Measures at least 48dp tall for touch.
 */
public class Toggle extends View {
    public interface Listener { void onChanged(Toggle toggle, boolean on); }

    private final ColorRoles roles;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean on;
    private float position; // 0 off … 1 on
    private Listener listener;
    private android.animation.ValueAnimator anim;

    public Toggle(Context c) { this(c, Theme.colors(c)); }

    public Toggle(Context c, ColorRoles roles) {
        super(c);
        this.roles = roles;
        thumb.setColor(0xFFFFFFFF);
        thumb.setShadowLayer(Ui.dpf(c, 2), 0, Ui.dpf(c, 1), 0x33000000);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> setOn(!on, true, true));
    }

    public boolean isOn() { return on; }
    public void setListener(Listener l) { listener = l; }

    /** Sets the state; {@code fromUser} fires the listener and the haptic. */
    public void setOn(boolean value, boolean animate, boolean fromUser) {
        if (value == on) return;
        on = value;
        if (anim != null) anim.cancel();
        if (animate) anim = Motion.animate(position, on ? 1f : 0f, Motion.SPRING_MS, Motion.spring(), p -> { position = p; invalidate(); });
        else { position = on ? 1f : 0f; invalidate(); }
        if (fromUser) {
            Haptics.toggle(this, on);
            if (listener != null) listener.onChanged(this, on);
        }
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 51), w), resolveSize(Ui.dp(getContext(), 48), h));
    }

    @Override protected void onDraw(Canvas canvas) {
        float tw = Ui.dpf(getContext(), 51), th = Ui.dpf(getContext(), 31);
        float left = (getWidth() - tw) / 2f, top = (getHeight() - th) / 2f;
        rect.set(left, top, left + tw, top + th);
        track.setColor(ColorMath.mix(roles.toggleOff, roles.accent, position));
        canvas.drawRoundRect(rect, th / 2f, th / 2f, track);
        float r = Ui.dpf(getContext(), 13.5f), inset = Ui.dpf(getContext(), 2);
        float cx = left + inset + r + position * (tw - 2 * inset - 2 * r);
        canvas.drawCircle(cx, top + th / 2f, r, thumb);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Switch.class.getName());
        info.setCheckable(true);
        info.setChecked(on);
    }
}
