package dev.lilt.player.screens.player;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Periods and wording for Replay (listening statistics). Only real counts are shown; nothing is
 * estimated. Pure Java; tests/player/ReplayTextTest.
 */
public final class ReplayText {
    private ReplayText() {}

    public static final int WEEK = 0, MONTH = 1, ALL_TIME = 2;
    /** Local midnight today (used for "Today" / "Yesterday"). */
    static final int TODAY = 9;
    public static final String[] SEGMENTS = {"This Week", "This Month", "All Time"};

    /**
     * Start of the period in epoch ms: the first day of this week (locale's first weekday) or
     * month at local midnight; 0 for all time.
     */
    public static long since(int period, long nowMs, TimeZone zone, Locale locale) {
        if (period == ALL_TIME) return 0;
        Calendar c = Calendar.getInstance(zone, locale);
        c.setTimeInMillis(nowMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (period == MONTH) {
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else if (period == WEEK) {
            int back = (c.get(Calendar.DAY_OF_WEEK) - c.getFirstDayOfWeek() + 7) % 7;
            c.add(Calendar.DAY_OF_MONTH, -back);
        }
        return c.getTimeInMillis();
    }

    /** "1,284" with the locale's grouping. */
    public static String number(long n, Locale locale) {
        return NumberFormat.getIntegerInstance(locale).format(Math.max(0, n));
    }

    /** Caption under the big number: "minutes this week", "minutes in November", "minutes in total". */
    public static String caption(int period, long minutes, long nowMs, TimeZone zone, Locale locale) {
        String unit = minutes == 1 ? "minute" : "minutes";
        if (period == WEEK) return unit + " this week";
        if (period == MONTH) {
            SimpleDateFormat f = new SimpleDateFormat("LLLL", locale);
            f.setTimeZone(zone);
            return unit + " in " + f.format(new Date(nowMs));
        }
        return unit + " in total";
    }

    /** "12 songs · 5 artists" (empty parts are left out). */
    public static String breadth(int songs, int artists, Locale locale) {
        StringBuilder b = new StringBuilder();
        if (songs > 0) b.append(number(songs, locale)).append(songs == 1 ? " song" : " songs");
        if (artists > 0) {
            if (b.length() > 0) b.append(" · ");
            b.append(number(artists, locale)).append(artists == 1 ? " artist" : " artists");
        }
        return b.toString();
    }

    /** "1 play", "38 plays". */
    public static String plays(int plays, Locale locale) {
        return number(plays, locale) + (plays == 1 ? " play" : " plays");
    }

    /** Listening time of a row: "214 min", "<1 min" for a few seconds. */
    public static String minutes(long ms, Locale locale) {
        if (ms <= 0) return "";
        if (ms < 60_000) return "<1 min";
        return number(ms / 60_000, locale) + " min";
    }

    /** "Today", "Yesterday", "Mon", "3 Nov" for recently played rows. */
    public static String when(long atMs, long nowMs, TimeZone zone, Locale locale) {
        if (atMs <= 0) return "";
        long today = since(TODAY, nowMs, zone, locale);
        if (atMs >= today) return "Today";
        if (atMs >= today - 86_400_000L) return "Yesterday";
        SimpleDateFormat f = new SimpleDateFormat(atMs >= today - 6 * 86_400_000L ? "EEE" : "d MMM", locale);
        f.setTimeZone(zone);
        return f.format(new Date(atMs));
    }
}
