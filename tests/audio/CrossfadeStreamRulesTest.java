package dev.lilt.player;

/** Stream-aware crossfade staging, tick scheduling and the sleep-timer fade. */
public final class CrossfadeStreamRulesTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }

    public static void main(String[] args) {
        check(CrossfadeRules.stageLeadMillis(true) == 10_000 && CrossfadeRules.stageLeadMillis(false) == 3_000, "streams staged earlier");
        check(CrossfadeRules.prepareTimeoutMillis(true) > CrossfadeRules.prepareTimeoutMillis(false), "streams get longer to buffer");

        // 6 s fade on a 200 s song: files stage 9 s before the end, streams 16 s before.
        check(!CrossfadeRules.shouldStage(6, 200_000, 190_999, false) && CrossfadeRules.shouldStage(6, 200_000, 191_000, false), "local staging point");
        check(!CrossfadeRules.shouldStage(6, 200_000, 183_999, true) && CrossfadeRules.shouldStage(6, 200_000, 184_000, true), "remote staging point");
        check(!CrossfadeRules.shouldStage(0, 200_000, 199_000, false), "crossfade off: gapless untouched");
        check(!CrossfadeRules.shouldStage(6, 500, 400, false), "clip too short to overlap");
        check(!CrossfadeRules.shouldStage(6, 0, 0, true) && !CrossfadeRules.shouldStage(13, 200_000, 199_000, false), "unknown duration or invalid seconds");
        // Short songs: the window is at most half the song.
        check(CrossfadeRules.shouldStage(12, 20_000, 7_000, false) && !CrossfadeRules.shouldStage(12, 20_000, 6_999, false), "window capped at half the song");

        check(CrossfadeRules.tickDelayMillis(true, false, 6, 0, 0) == 100, "overlapping: fast ticks");
        check(CrossfadeRules.tickDelayMillis(false, false, 6, 200_000, 0) == -1, "paused: no ticks");
        check(CrossfadeRules.tickDelayMillis(false, true, 0, 200_000, 0) == -1, "crossfade off: no ticks");
        check(CrossfadeRules.tickDelayMillis(false, true, 6, 0, 0) == 1000, "duration unknown: slow ticks");
        check(CrossfadeRules.tickDelayMillis(false, true, 6, 300_000, 0) == 5000, "far from the end: 5 s");
        check(CrossfadeRules.tickDelayMillis(false, true, 6, 300_000, 280_000) == 3000, "approaching: wake shortly before staging");
        check(CrossfadeRules.tickDelayMillis(false, true, 6, 300_000, 283_000) == 100, "near the end: fast ticks");
        // Never sleeps past the earliest (remote) staging point.
        for (long position = 0; position < 300_000; position += 250) {
            long delay = CrossfadeRules.tickDelayMillis(false, true, 6, 300_000, position);
            check(position + delay <= 300_000 - 6_000 - CrossfadeRules.REMOTE_STAGE_LEAD_MS || delay == 100, "no missed staging at " + position);
        }

        // Equal-power ramp: constant combined power, monotonic, exact end points.
        check(CrossfadeRules.equalPowerIn(0) == 0f && CrossfadeRules.equalPowerOut(0) == 1f, "fade starts with the outgoing song only");
        check(CrossfadeRules.equalPowerIn(1) == 1f && CrossfadeRules.equalPowerOut(1) < 1e-6f, "fade ends with the incoming song only");
        float previousIn = -1, previousOut = 2;
        for (long elapsed = 0; elapsed <= 6000; elapsed += CrossfadeRules.FADE_TICK_MS) {
            float progress = CrossfadeRules.incomingGain(elapsed, 6000);
            float in = CrossfadeRules.equalPowerIn(progress), out = CrossfadeRules.equalPowerOut(progress);
            check(Math.abs(in * in + out * out - 1f) < 1e-5f, "constant power at " + elapsed);
            check(in >= previousIn && out <= previousOut, "monotonic at " + elapsed);
            previousIn = in; previousOut = out;
        }
        check(Math.abs(CrossfadeRules.equalPowerIn(0.5f) - 0.70710677f) < 1e-6f, "-3 dB each at the midpoint");
        check(CrossfadeRules.equalPowerIn(-1) == 0f && CrossfadeRules.equalPowerIn(2) == 1f && CrossfadeRules.equalPowerIn(Float.NaN) == 0f, "clamped");

        check(CrossfadeRules.sleepFadeGain(10_000, 8_000) == 1f && CrossfadeRules.sleepFadeGain(8_000, 8_000) == 1f, "full volume before the fade");
        check(CrossfadeRules.sleepFadeGain(4_000, 8_000) == 0.5f && CrossfadeRules.sleepFadeGain(0, 8_000) == 0f, "linear fade to silence");
        check(CrossfadeRules.sleepFadeGain(-5, 8_000) == 0f && CrossfadeRules.sleepFadeGain(100, 0) == 1f, "fade edge cases");
        System.out.println("CrossfadeStreamRulesTest: " + checks + " checks passed");
    }
}
