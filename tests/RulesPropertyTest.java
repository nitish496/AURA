package dev.lilt.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Randomised invariants for HomeMixRules and AutoplayRules (the example-based tests cover the
 * specific rankings; these check the guarantees the UI and service rely on, for any library).
 */
public final class RulesPropertyTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Random random = new Random(1105);
        for (int run = 0; run < 400; run++) homeMix(random);
        for (int run = 0; run < 400; run++) autoplay(random);
        System.out.println("RulesPropertyTest: " + checks + " checks passed");
    }

    /** Home shelves: a permutation, album order kept, and one song per album per round. */
    private static void homeMix(Random random) {
        int n = random.nextInt(60), albums = 1 + random.nextInt(8);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) keys.add("album" + random.nextInt(albums));
        List<Integer> order = HomeMixRules.order(keys);
        check(order.size() == n && new HashSet<>(order).size() == n, "every song exactly once");
        Map<String, Integer> lastIndex = new HashMap<>();
        for (int i : order) {
            check(i >= 0 && i < n, "valid index");
            Integer previous = lastIndex.put(keys.get(i), i);
            check(previous == null || previous < i, "songs of one album keep their library order");
        }
        // Round-robin: position p belongs to round r; within a round each album appears at most once.
        Map<String, Integer> remaining = new HashMap<>();
        for (String k : keys) remaining.merge(k, 1, Integer::sum);
        int p = 0;
        while (p < order.size()) {
            int roundSize = 0;
            for (int left : remaining.values()) if (left > 0) roundSize++;
            Set<String> seen = new HashSet<>();
            for (int j = 0; j < roundSize; j++, p++) {
                String album = keys.get(order.get(p));
                check(seen.add(album), "an album repeats within one round");
                remaining.merge(album, -1, Integer::sum);
            }
        }
    }

    /** Autoplay: never the seed or an upcoming song; fresh songs before recent ones; null only when nothing is eligible. */
    private static void autoplay(Random random) {
        int n = random.nextInt(25);
        List<AutoplayRules.Song> library = new ArrayList<>();
        String[] albums = {"Movie", "Album", "Downloads", "Other", " movie "};
        String[] artists = {"Singer", "Band", "Unknown artist", "singer"};
        for (int i = 0; i < n; i++) {
            List<String> moods = random.nextBoolean() ? Collections.singletonList(MoodRules.LABELS.get(random.nextInt(5))) : Collections.emptyList();
            library.add(new AutoplayRules.Song("s" + i, albums[random.nextInt(albums.length)], artists[random.nextInt(artists.length)], moods));
        }
        AutoplayRules.Song seed = n > 0 && random.nextBoolean() ? library.get(random.nextInt(n)) : null;
        Set<String> upcoming = new HashSet<>();
        List<String> recent = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (random.nextInt(5) == 0) upcoming.add("s" + i);
            if (random.nextInt(3) == 0) recent.add("s" + i);
        }
        Collections.shuffle(recent, random);
        String chosen = AutoplayRules.choose(library, seed, upcoming, recent, new Random(random.nextLong()));

        Set<String> eligible = new HashSet<>();
        for (AutoplayRules.Song s : library) if (!upcoming.contains(s.id) && (seed == null || !s.id.equals(seed.id))) eligible.add(s.id);
        if (eligible.isEmpty()) {
            check(chosen == null, "nothing eligible must return null");
            return;
        }
        check(chosen != null && eligible.contains(chosen), "chooses an eligible song: " + chosen);
        boolean freshExists = false;
        for (String id : eligible) if (!recent.contains(id)) freshExists = true;
        if (freshExists) check(!recent.contains(chosen), "a fresh song exists, so a recent one must not be picked");
        else {
            // Only recent songs remain: the least recently played (largest history index) wins.
            int worst = -1;
            for (String id : eligible) worst = Math.max(worst, recent.indexOf(id));
            check(recent.indexOf(chosen) == worst, "least recent fallback");
        }
        // Same Random seed, same answer: rotation is driven only by the caller's Random.
        long seedValue = random.nextLong();
        check(String.valueOf(AutoplayRules.choose(library, seed, upcoming, recent, new Random(seedValue)))
                .equals(String.valueOf(AutoplayRules.choose(library, seed, upcoming, recent, new Random(seedValue)))),
            "deterministic for a given Random");
        check(Arrays.asList(library.toArray()).size() == n, "library untouched");
    }
}
