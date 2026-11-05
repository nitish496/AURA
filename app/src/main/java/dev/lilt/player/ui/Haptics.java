package dev.lilt.player.ui;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Haptics for key interactions, using the platform's semantic feedback constants (they follow
 * the user's system "touch feedback" setting and the device's tuned effects). Use sparingly:
 * one effect per meaningful event, never on scroll.
 *
 * <ul>
 *   <li>{@link #tick} — scrubber reaches start/end, segment or chip changes, reorder slot passes</li>
 *   <li>{@link #click} — play / pause, skip</li>
 *   <li>{@link #longPress} — a context menu opens</li>
 *   <li>{@link #confirm} — saved to library, added to queue, timer set</li>
 *   <li>{@link #reject} — an action failed (offline, unavailable)</li>
 *   <li>{@link #toggle} — a toggle flips</li>
 *   <li>{@link #dragStart} — a queue row lifts for reordering</li>
 *   <li>{@link #gestureEnd} — a sheet or the player settles after a drag</li>
 * </ul>
 */
public final class Haptics {
    private Haptics() {}

    private static volatile boolean enabled = true;

    /** App-level switch (e.g. a Settings toggle). The system setting is always respected too. */
    public static void setEnabled(boolean on) { enabled = on; }

    public static void tick(View v) { perform(v, HapticFeedbackConstants.CLOCK_TICK); }
    public static void click(View v) { perform(v, HapticFeedbackConstants.CONTEXT_CLICK); }
    public static void longPress(View v) { perform(v, HapticFeedbackConstants.LONG_PRESS); }

    public static void confirm(View v) {
        perform(v, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.CONTEXT_CLICK);
    }

    public static void reject(View v) {
        perform(v, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
    }

    public static void toggle(View v, boolean on) {
        if (Build.VERSION.SDK_INT >= 34) perform(v, on ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.TOGGLE_OFF);
        else perform(v, HapticFeedbackConstants.CLOCK_TICK);
    }

    public static void dragStart(View v) {
        perform(v, Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.DRAG_START : HapticFeedbackConstants.LONG_PRESS);
    }

    public static void gestureEnd(View v) {
        perform(v, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.GESTURE_END : HapticFeedbackConstants.CLOCK_TICK);
    }

    private static void perform(View v, int constant) {
        if (enabled && v != null) v.performHapticFeedback(constant);
    }
}
