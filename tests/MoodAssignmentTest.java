package dev.lilt.player;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** MoodRules.assignment backs MoodStore.assign: one write for "choose songs for this mood". */
public final class MoodAssignmentTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static Set<String> set(String... moods) {
        return new LinkedHashSet<>(Arrays.asList(moods));
    }

    public static void main(String[] args) {
        Map<String, Collection<String>> current = new HashMap<>();
        current.put("a", set("Chill"));
        current.put("b", set("Focus", "Chill"));
        current.put("c", set());
        current.put("d", set("Sad", "bogus"));
        List<String> ids = Arrays.asList("a", "b", "c", "d", "e");

        Map<String, Set<String>> changes = MoodRules.assignment(current, ids, Arrays.asList("a", "c", "e"), "Chill");
        check(changes.keySet().equals(set("b", "c", "e")), "only songs whose moods change are written: " + changes.keySet());
        check(changes.get("b").equals(set("Focus")), "deselected song loses only that mood");
        check(changes.get("c").equals(set("Chill")), "newly selected song gains the mood");
        check(changes.get("e").equals(set("Chill")), "song without stored moods gains the mood");
        check(!changes.containsKey("a"), "already assigned song is untouched");
        check(!changes.containsKey("d"), "unrelated song is untouched (and unknown labels are ignored)");

        Map<String, Set<String>> cleared = MoodRules.assignment(current, ids, null, "Chill");
        check(cleared.keySet().equals(set("a", "b")), "null selection removes the mood everywhere");
        check(cleared.get("a").isEmpty(), "an empty set means: clear the song's entry");

        check(MoodRules.assignment(current, ids, ids, "Party").isEmpty(), "unknown mood changes nothing");
        check(MoodRules.assignment(current, null, ids, "Chill").isEmpty(), "no ids, no changes");
        check(MoodRules.assignment(null, Arrays.asList("x"), Arrays.asList("x"), "Happy").get("x").equals(set("Happy")),
            "missing current map means no stored moods");

        Map<String, Set<String>> dup = MoodRules.assignment(current, Arrays.asList("c", "c", "", null), Arrays.asList("c"), "Happy");
        check(dup.size() == 1 && dup.get("c").equals(set("Happy")), "duplicates, blank and null ids handled once");

        Map<String, Set<String>> ordered = MoodRules.assignment(
            Collections.singletonMap("s", set("Sad", "Chill")), Arrays.asList("s"), Arrays.asList("s"), "Focus");
        check(Arrays.asList(ordered.get("s").toArray()).equals(Arrays.asList("Chill", "Focus", "Sad")),
            "result keeps the canonical label order");
        System.out.println("MoodAssignmentTest: " + checks + " checks passed");
    }
}
