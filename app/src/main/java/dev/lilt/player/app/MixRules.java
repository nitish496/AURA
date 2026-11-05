package dev.lilt.player.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * How the Home "Top Picks" mixes are built from real listening data. Pure Java (ids only), so
 * the rules are testable; LibraryRepo resolves ids to tracks.
 *
 * <ul>
 *   <li><b>Heavy Rotation</b>: most played in the last 30 days, at least {@link #MIN_MIX} songs.</li>
 *   <li><b>Rediscover</b>: songs played at least twice but not in the last 30 days, most played first.</li>
 *   <li><b>Favourites mix</b>: favourites spread across albums (no album twice in a row), seeded per day.</li>
 *   <li><b>Mood mixes</b>: a mood with at least {@link #MIN_MIX} tagged songs.</li>
 * </ul>
 */
public final class MixRules {
    private MixRules() {}

    public static final int MIN_MIX = 4, MAX_MIX = 50;
    public static final long DAY_MS = 86_400_000L, RECENT_MS = 30 * DAY_MS;

    /** One listening record (from ListeningStats). */
    public static final class Played {
        public final String id;
        public final int plays;
        public final long lastPlayedAt;
        public Played(String id, int plays, long lastPlayedAt) { this.id = id; this.plays = plays; this.lastPlayedAt = lastPlayedAt; }
    }

    /** Ids of {@code topRecent} (already ranked) that are playable; empty below {@link #MIN_MIX}. */
    public static List<String> heavyRotation(List<String> topRecent, Predicate<String> playable) {
        List<String> out = new ArrayList<>();
        for (String id : new LinkedHashSet<>(topRecent)) {
            if (playable.test(id)) out.add(id);
            if (out.size() >= MAX_MIX) break;
        }
        return out.size() >= MIN_MIX ? out : new ArrayList<>();
    }

    /** Played ≥ 2 times, last play older than 30 days; most plays first. */
    public static List<String> rediscover(Collection<Played> allTime, long now, Predicate<String> playable) {
        List<Played> stale = new ArrayList<>();
        for (Played p : allTime) if (p.plays >= 2 && now - p.lastPlayedAt > RECENT_MS && playable.test(p.id)) stale.add(p);
        stale.sort((a, b) -> a.plays != b.plays ? Integer.compare(b.plays, a.plays) : Long.compare(b.lastPlayedAt, a.lastPlayedAt));
        List<String> out = new ArrayList<>();
        for (Played p : stale) { out.add(p.id); if (out.size() >= MAX_MIX) break; }
        return out.size() >= MIN_MIX ? out : new ArrayList<>();
    }

    /**
     * Shuffles ids with a fixed seed, then spreads them so the same album never plays twice in a
     * row when it can be avoided (round-robin over albums, HomeMixRules).
     */
    public static List<String> spread(List<String> ids, Map<String, String> albumOf, long seed) {
        List<String> shuffled = new ArrayList<>(new LinkedHashSet<>(ids));
        java.util.Collections.shuffle(shuffled, new Random(seed));
        List<String> keys = new ArrayList<>(shuffled.size());
        for (String id : shuffled) { String k = albumOf.get(id); keys.add(k == null ? id : k); }
        List<String> out = new ArrayList<>(shuffled.size());
        for (int i : dev.lilt.player.HomeMixRules.order(keys)) out.add(shuffled.get(i));
        return out.size() > MAX_MIX ? new ArrayList<>(out.subList(0, MAX_MIX)) : out;
    }

    /** Seed that changes once per local day, so mixes are stable within a day. */
    public static long daySeed(long nowMs, int utcOffsetMs) { return Math.floorDiv(nowMs + utcOffsetMs, DAY_MS); }

    /** "Low Tide, Paper Lanterns, Night Bus Home and more": up to three distinct names. */
    public static String summary(List<String> names) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String n : names) { if (n != null && !n.trim().isEmpty()) distinct.add(n.trim()); if (distinct.size() == 4) break; }
        List<String> list = new ArrayList<>(distinct);
        if (list.isEmpty()) return "";
        if (list.size() == 1) return list.get(0);
        if (list.size() <= 3) return String.join(", ", list.subList(0, list.size() - 1)) + " and " + list.get(list.size() - 1);
        return String.join(", ", list.subList(0, 3)) + " and more";
    }
}
