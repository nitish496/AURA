package app;

import dev.lilt.player.app.MixRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MixRulesTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        List<String> top = Arrays.asList("a", "b", "gone", "c", "d", "a");
        List<String> heavy = MixRules.heavyRotation(top, id -> !id.equals("gone"));
        check(heavy.equals(Arrays.asList("a", "b", "c", "d")), "heavy " + heavy);
        check(MixRules.heavyRotation(Arrays.asList("a", "b"), id -> true).isEmpty(), "too few -> empty");

        long now = 100 * MixRules.DAY_MS;
        List<MixRules.Played> played = new ArrayList<>();
        played.add(new MixRules.Played("old1", 5, now - 40 * MixRules.DAY_MS));
        played.add(new MixRules.Played("old2", 9, now - 50 * MixRules.DAY_MS));
        played.add(new MixRules.Played("once", 1, now - 50 * MixRules.DAY_MS));
        played.add(new MixRules.Played("fresh", 20, now - MixRules.DAY_MS));
        played.add(new MixRules.Played("old3", 2, now - 31 * MixRules.DAY_MS));
        played.add(new MixRules.Played("old4", 3, now - 60 * MixRules.DAY_MS));
        List<String> re = MixRules.rediscover(played, now, id -> true);
        check(re.equals(Arrays.asList("old2", "old1", "old4", "old3")), "rediscover " + re);

        Map<String, String> album = new HashMap<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) { ids.add("x" + i); album.put("x" + i, "X"); }
        for (int i = 0; i < 6; i++) { ids.add("y" + i); album.put("y" + i, "Y"); }
        List<String> mix = MixRules.spread(ids, album, 7);
        check(mix.size() == 12, "keeps all");
        for (int i = 1; i < mix.size(); i++) check(!album.get(mix.get(i)).equals(album.get(mix.get(i - 1))), "no album twice in a row " + mix);
        check(mix.equals(MixRules.spread(ids, album, 7)), "deterministic per seed");

        check(MixRules.summary(Arrays.asList("A", "B", "A", "C", "D")).equals("A, B, C and more"), "summary more");
        check(MixRules.summary(Arrays.asList("A", "B")).equals("A and B"), "summary two");
        check(MixRules.summary(Arrays.asList("A")).equals("A"), "summary one");
        check(MixRules.daySeed(MixRules.DAY_MS * 3 + 5, 0) == 3, "day seed");
        System.out.println("MixRulesTest OK");
    }
}
