package dev.lilt.player.ui;

/**
 * A damped-spring easing curve, parameterised like Apple's springs: {@code response} (seconds,
 * roughly the period of the oscillation) and {@code dampingRatio} (1 = no overshoot, 0.8 = a
 * gentle settle). The curve is normalised so {@code value(0) = 0} and {@code value(1) = 1}.
 * Pure Java; {@link Motion#spring()} wraps it as an Android interpolator.
 */
public final class SpringCurve {
    /** AURA's default: quick, with a barely visible settle. */
    public static final SpringCurve DEFAULT = new SpringCurve(0.86f, 0.32f, 0.36f);
    /** For larger moves (player expand, sheets). */
    public static final SpringCurve GENTLE = new SpringCurve(0.9f, 0.42f, 0.45f);

    public final float dampingRatio, response, durationSec;
    private final double omega, endValue;

    public SpringCurve(float dampingRatio, float response, float durationSec) {
        if (dampingRatio <= 0 || response <= 0 || durationSec <= 0) throw new IllegalArgumentException("spring parameters must be > 0");
        this.dampingRatio = dampingRatio;
        this.response = response;
        this.durationSec = durationSec;
        this.omega = 2 * Math.PI / response;
        this.endValue = raw(durationSec);
    }

    /** Position for {@code fraction} (0..1) of the animation's duration. */
    public float value(float fraction) {
        if (fraction <= 0) return 0;
        if (fraction >= 1) return 1;
        double t = fraction * durationSec;
        // Spread the small residual (the spring has not fully settled at the end) over the run.
        return (float) (raw(t) + (1 - endValue) * fraction);
    }

    /** Largest value reached during the run (1 = no overshoot). */
    public float peak() {
        float max = 0;
        for (int i = 0; i <= 200; i++) max = Math.max(max, value(i / 200f));
        return max;
    }

    private double raw(double t) {
        double z = dampingRatio;
        if (z >= 1) return 1 - Math.exp(-omega * t) * (1 + omega * t); // critically damped
        double wd = omega * Math.sqrt(1 - z * z);
        return 1 - Math.exp(-z * omega * t) * (Math.cos(wd * t) + (z * omega / wd) * Math.sin(wd * t));
    }
}
