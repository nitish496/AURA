package dev.lilt.player.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import dev.lilt.player.DesignTokens;

/**
 * Motion: short and purposeful. Durations 150 / 250 / 350 ms, fast-out-slow-in curves and a
 * gentle spring for chrome. Everything collapses to "jump to end" when the user disables
 * animations (Developer options / Accessibility → Remove animations).
 *
 * <pre>{@code
 * Motion.animate(0f, 1f, Motion.BASE, Motion.spring(), p -> bottomChrome.setProgress(p));
 * Motion.pressFeedback(card);
 * }</pre>
 */
public final class Motion {
    private Motion() {}

    public static final int FAST = DesignTokens.DURATION_FAST, BASE = DesignTokens.DURATION_BASE,
            SLOW = DesignTokens.DURATION_SLOW, SHEET = DesignTokens.DURATION_SHEET;

    private static final TimeInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final TimeInterpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private static final TimeInterpolator ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    private static final TimeInterpolator SPRING = new SpringInterpolator(SpringCurve.DEFAULT);
    private static final TimeInterpolator GENTLE = new SpringInterpolator(SpringCurve.GENTLE);

    /** Moves within the screen (position, size, colour). */
    public static TimeInterpolator standard() { return STANDARD; }
    /** Things arriving (sheets, menus, snackbars). */
    public static TimeInterpolator decelerate() { return DECELERATE; }
    /** Things leaving. Pair with {@link #FAST}. */
    public static TimeInterpolator accelerate() { return ACCELERATE; }
    /** Chrome changes: tab bar minimise, mini player docking, lens slide, toggles. */
    public static TimeInterpolator spring() { return SPRING; }
    /** Large moves: player expand / collapse, sheets settling. */
    public static TimeInterpolator gentleSpring() { return GENTLE; }

    /** Spring duration matching {@link SpringCurve#DEFAULT}. */
    public static final int SPRING_MS = Math.round(SpringCurve.DEFAULT.durationSec * 1000);
    public static final int GENTLE_SPRING_MS = Math.round(SpringCurve.GENTLE.durationSec * 1000);

    /** False when the system animator scale is 0 ("Remove animations"). */
    public static boolean enabled() { return ValueAnimator.areAnimatorsEnabled(); }

    /** Callback for {@link #animate}. */
    public interface Update { void on(float value); }

    /**
     * Animates a float and reports each frame; reports {@code to} immediately when animations
     * are off. Returns the started animator (or null) so callers can cancel it.
     */
    public static ValueAnimator animate(float from, float to, long durationMs, TimeInterpolator interpolator, Update update) {
        if (!enabled() || durationMs <= 0) { update.on(to); return null; }
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(durationMs);
        a.setInterpolator(interpolator);
        a.addUpdateListener(v -> update.on((float) v.getAnimatedValue()));
        a.start();
        return a;
    }

    /** Fades {@code v} in (alpha 0 → 1, small rise) over {@link #BASE}. */
    public static void fadeIn(View v) {
        v.animate().cancel();
        if (v.getVisibility() != View.VISIBLE) { v.setAlpha(0f); v.setTranslationY(Ui.dpf(v.getContext(), 6)); }
        v.setVisibility(View.VISIBLE);
        if (!enabled()) { v.setAlpha(1f); v.setTranslationY(0); return; }
        v.animate().alpha(1f).translationY(0).setDuration(BASE).setInterpolator(DECELERATE).start();
    }

    /** Fades {@code v} out over {@link #FAST}, then sets it GONE (or INVISIBLE). */
    public static void fadeOut(View v, boolean gone) {
        v.animate().cancel();
        int end = gone ? View.GONE : View.INVISIBLE;
        if (!enabled() || v.getVisibility() != View.VISIBLE) { v.setVisibility(end); return; }
        v.animate().alpha(0f).setDuration(FAST).setInterpolator(ACCELERATE).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                v.animate().setListener(null);
                v.setVisibility(end);
                v.setAlpha(1f);
            }
        }).start();
    }

    /**
     * Subtle press feedback for cards and artwork: scales to 97% while pressed and springs back.
     * Does not consume touches, so click / long-click listeners keep working.
     */
    @SuppressLint("ClickableViewAccessibility") // returns false: the view still performs its own click handling
    public static void pressFeedback(View v) {
        v.setOnTouchListener((view, e) -> {
            if (!enabled()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(FAST).setInterpolator(STANDARD).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(SPRING_MS).setInterpolator(SPRING).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** {@link SpringCurve} as an Android interpolator. */
    public static final class SpringInterpolator implements TimeInterpolator {
        private final SpringCurve curve;
        public SpringInterpolator(SpringCurve curve) { this.curve = curve; }
        @Override public float getInterpolation(float input) { return curve.value(input); }
    }
}
