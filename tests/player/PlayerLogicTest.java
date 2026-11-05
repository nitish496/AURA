package dev.lilt.player.screens.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Scrubber, dismiss gesture, lyric scrolling, speed, sleep timer, queue, EQ and storage logic. */
public final class PlayerLogicTest {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    static void eq(Object a, Object b) { if (!a.equals(b)) throw new AssertionError("expected <" + b + "> got <" + a + ">"); }

    public static void main(String[] args) {
        scrub(); dismiss(); lyrics(); speed(); sleep(); replay(); format(); eqMath(); storage(); queue();
        System.out.println("PlayerLogicTest ok");
    }

    static void scrub() {
        eq(ScrubMath.fraction(-5, 0, 100), 0f);
        eq(ScrubMath.fraction(50, 0, 100), 0.5f);
        eq(ScrubMath.fraction(500, 0, 100), 1f);
        eq(ScrubMath.positionAt(0.5f, 200_000), 100_000L);
        eq(ScrubMath.dragPosition(10_000, 50, 100, 200_000), 110_000L);
        eq(ScrubMath.dragPosition(10_000, -500, 100, 200_000), 0L);
        eq(ScrubMath.dragPosition(10_000, 500, 100, 200_000), 200_000L);
        eq(ScrubMath.tickStepMs(180_000), 10_000L);           // 3-minute song: 18 ticks
        eq(ScrubMath.tickStepMs(3_600_000), 300_000L);        // an hour: 12 ticks
        check(ScrubMath.tickStepMs(100L * 3_600_000) > 0, "long step");
        check(100L * 3_600_000 / ScrubMath.tickStepMs(100L * 3_600_000) <= ScrubMath.MAX_TICKS, "long ticks bounded");
        eq(ScrubMath.tickStepMs(0), 0L);
        eq(ScrubMath.tickIndex(25_000, 10_000), 2L);
        eq(ScrubMath.refreshIntervalMs(180_000, 1000, 1f), 180L);
        eq(ScrubMath.refreshIntervalMs(5_000, 1000, 1f), 16L);
        eq(ScrubMath.refreshIntervalMs(0, 1000, 1f), 1000L);
        eq(ScrubMath.refreshIntervalMs(180_000, 1000, 2f), 90L);
        eq(ScrubMath.trackHeight(4, 10, 0.5f), 7f);
    }

    static void dismiss() {
        float d = 3f, h = 2400f;
        check(!DismissGesture.shouldDismiss(100, 0, h, d), "short drag springs back");
        check(DismissGesture.shouldDismiss(700, 0, h, d), "long drag dismisses");
        check(DismissGesture.shouldDismiss(200, 3000, h, d), "fling dismisses");
        check(!DismissGesture.shouldDismiss(30, 3000, h, d), "twitch does not dismiss");
        check(!DismissGesture.shouldDismiss(900, -1500, h, d), "upward fling cancels");
        check(DismissGesture.follow(120, d) == 120, "follows down 1:1");
        float up = DismissGesture.follow(-1000, d);
        check(up < 0 && up > -24 * d, "upward resistance bounded: " + up);
        eq(DismissGesture.progress(1200, h), 0.5f);
        check(DismissGesture.settleDurationMs(1000, 0) == 300, "default settle");
        long t = DismissGesture.settleDurationMs(1000, 100_000);
        check(t >= 150 && t <= 350, "settle clamped");
    }

    static void lyrics() {
        eq(LyricScroll.target(1000, 900, 5000), Math.round(1000 - 900 * LyricScroll.ANCHOR));
        eq(LyricScroll.target(10, 900, 5000), 0);
        eq(LyricScroll.target(4900, 900, 5000), 4100);
        check(LyricScroll.autoScroll(10_000, 0, false), "auto when never touched");
        check(!LyricScroll.autoScroll(10_000, 8_000, false), "paused after manual scroll");
        check(LyricScroll.autoScroll(12_000, 8_000, false), "resumes");
        check(!LyricScroll.autoScroll(99_000, 0, true), "never while touching");
        eq(LyricScroll.alpha(3, 3, false), 1f);
        check(LyricScroll.alpha(2, 3, false) < LyricScroll.alpha(4, 3, false), "past dimmer than future");
        eq(LyricScroll.blurDp(3, 3, false), 0f);
        eq(LyricScroll.blurDp(4, 3, true), 0f);
        check(LyricScroll.blurDp(9, 3, false) > LyricScroll.blurDp(4, 3, false), "far lines blur more");
        long dur = LyricScroll.durationMs(3000, 3);
        check(dur >= 350 && dur <= 700, "scroll duration");
    }

    static void speed() {
        eq(SpeedSteps.fromFraction(0), 0.5f);
        eq(SpeedSteps.fromFraction(1), 2.0f);
        eq(SpeedSteps.snap(1.03f), 1.0f);
        eq(SpeedSteps.snap(1.1f), 1.1f);
        eq(SpeedSteps.snap(1.27f), 1.25f);
        eq(SpeedSteps.snap(9f), 2.0f);
        eq(SpeedSteps.snap(Float.NaN), 1.0f);
        eq(SpeedSteps.label(1f), "1×");
        eq(SpeedSteps.label(1.25f), "1.25×");
        eq(SpeedSteps.label(0.8f), "0.8×");
        check(Math.abs(SpeedSteps.toFraction(1.25f) - 0.5f) < 1e-6, "fraction");
        check(SpeedSteps.isSnapPoint(1.5f) && !SpeedSteps.isSnapPoint(1.1f), "snap points");
    }

    static void sleep() {
        eq(SleepText.preset(15), "In 15 minutes");
        eq(SleepText.preset(60), "In 1 hour");
        eq(SleepText.preset(90), "In 1 hr 30 min");
        eq(SleepText.preset(1), "In 1 minute");
        eq(SleepText.countdown(0), "0:00");
        eq(SleepText.countdown(1), "0:01");
        eq(SleepText.countdown(23 * 60_000 + 41_000), "23:41");
        eq(SleepText.countdown(3_723_000), "1:02:03");
        eq(SleepText.summary(0, false), "Off");
        eq(SleepText.summary(0, true), "End of song");
        eq(SleepText.summary(61_000, false), "1:01");
        eq(SleepText.clampCustom(0), 1);
        eq(SleepText.clampCustom(500), 120);
        eq(SleepText.matchingPreset(10 * 60_000, 15), 15);
        eq(SleepText.matchingPreset(0, 15), -1);
    }

    static void replay() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        long wed = 1762300800000L + 12 * 3_600_000L; // Wed 5 Nov 2025 12:00 UTC
        long monthStart = ReplayText.since(ReplayText.MONTH, wed, utc, Locale.UK);
        eq(monthStart, 1761955200000L); // 1 Nov 2025
        long weekStart = ReplayText.since(ReplayText.WEEK, wed, utc, Locale.UK); // Monday first
        eq(weekStart, 1762128000000L); // Mon 3 Nov 2025
        long usWeek = ReplayText.since(ReplayText.WEEK, wed, utc, Locale.US); // Sunday first
        eq(usWeek, 1762041600000L);
        eq(ReplayText.since(ReplayText.ALL_TIME, wed, utc, Locale.UK), 0L);
        eq(ReplayText.number(1284, Locale.US), "1,284");
        eq(ReplayText.caption(ReplayText.MONTH, 5, wed, utc, Locale.US), "minutes in November");
        eq(ReplayText.caption(ReplayText.WEEK, 1, wed, utc, Locale.US), "minute this week");
        eq(ReplayText.breadth(412, 96, Locale.US), "412 songs · 96 artists");
        eq(ReplayText.breadth(1, 0, Locale.US), "1 song");
        eq(ReplayText.breadth(0, 0, Locale.US), "");
        eq(ReplayText.plays(1, Locale.US), "1 play");
        eq(ReplayText.plays(38, Locale.US), "38 plays");
        eq(ReplayText.minutes(214 * 60_000L + 5, Locale.US), "214 min");
        eq(ReplayText.minutes(5_000, Locale.US), "<1 min");
        eq(ReplayText.when(wed - 3_600_000, wed, utc, Locale.US), "Today");
        eq(ReplayText.when(wed - 86_400_000L, wed, utc, Locale.US), "Yesterday");
        eq(ReplayText.when(wed - 3 * 86_400_000L, wed, utc, Locale.US), "Sun");
        eq(ReplayText.when(wed - 30 * 86_400_000L, wed, utc, Locale.UK), "6 Oct");
    }

    static void format() {
        eq(FormatLabel.describe("audio/flac", 96000, 24, 0), "FLAC · 24-bit · 96 kHz");
        eq(FormatLabel.describe("audio/flac", 44100, 0, 0), "FLAC · 44.1 kHz");
        eq(FormatLabel.describe("audio/mpeg", 44100, 0, 320_000), "MP3 · 320 kbps");
        eq(FormatLabel.describe("audio/mp4a-latm", 44100, 0, 0), "AAC · 44.1 kHz");
        eq(FormatLabel.describe("audio/opus", -1, -1, -1), "Opus");
        eq(FormatLabel.describe("audio/eac3-joc", 48000, 0, 0), "Dolby Atmos");
        eq(FormatLabel.describe("application/unknown", 44100, 16, 0), "");
        eq(FormatLabel.describe(null, 44100, 16, 0), "");
        eq(FormatLabel.bitsForPcmEncoding(0x15), 24);
        eq(FormatLabel.bitsForPcmEncoding(-1), 0);
    }

    static void eqMath() {
        eq(EqMath.y(1200, -1200, 1200, 0, 100), 0f);
        eq(EqMath.y(0, -1200, 1200, 0, 100), 50f);
        eq(EqMath.y(-5000, -1200, 1200, 0, 100), 100f);
        eq((int) EqMath.levelAt(0, -1500, 1500, 0, 300), 1500);
        eq((int) EqMath.levelAt(151, -1500, 1500, 0, 300), 0);    // near 0 snaps
        eq((int) EqMath.levelAt(100, -1500, 1500, 0, 300), 500);
        eq((int) EqMath.levelAt(999, -1500, 1500, 0, 300), -1500);
        eq(EqMath.frequency(60), "60");
        eq(EqMath.frequency(3600), "3.6k");
        eq(EqMath.frequency(14000), "14k");
        eq(EqMath.frequency(1000), "1k");
        eq(EqMath.gain(300), "+3 dB");
        eq(EqMath.gain(0), "0 dB");
        eq(EqMath.gain(-450), "−4.5 dB");
        eq(EqMath.nearestBand(42, new float[] {10, 40, 90}), 1);
        eq(EqMath.nearestBand(42, new float[0]), -1);
    }

    static void storage() {
        eq(StorageText.bytes(0), "0 KB");
        eq(StorageText.bytes(820_000), "820 KB");
        eq(StorageText.bytes(412_300_000), "412 MB");
        eq(StorageText.bytes(1_000_000_000), "1 GB");
        eq(StorageText.bytes(1_240_000_000), "1.2 GB");
        eq(StorageText.usage(412_000_000, 1_000_000_000), "412 MB of 1 GB used");
        eq(StorageText.usage(5, 0), "0 KB used");
        eq(StorageText.share(500, 1000), 0.5f);
        eq(StorageText.share(5000, 1000), 1f);
    }

    static void queue() {
        QueueModel q = QueueModel.of(5, 1, null);
        eq(q.size(), 3);
        eq(q.queueIndex(0), 2);
        check(q.canReorder(), "reorder in queue order");
        check(Arrays.equals(q.move(0, 2), new int[] {2, 4}), "move maps to queue indices");
        check(q.move(1, 1) == null, "no-op move");
        QueueModel s = QueueModel.of(5, 1, new int[] {3, 1, 4, 0, 2});
        eq(s.size(), 3);
        eq(s.queueIndex(0), 4);
        eq(s.queueIndex(2), 2);
        check(!s.canReorder() && s.move(0, 1) == null, "no reorder while shuffled");
        eq(QueueModel.of(0, -1, null).size(), 0);
        eq(QueueModel.of(3, 2, null).size(), 0);
        eq(QueueModel.of(3, -1, null).size(), 3);
        List<String> l = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        QueueModel.moveInList(l, 0, 2);
        eq(l, Arrays.asList("b", "c", "a", "d"));
    }
}
