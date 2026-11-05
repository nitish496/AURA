package app;

import dev.lilt.player.app.SectionIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SectionIndexTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        check(SectionIndex.initial("Élan") == 'E', "accent folded");
        check(SectionIndex.initial("  \"Blue\"") == 'B', "skips quotes");
        check(SectionIndex.initial("2 Lane Road") == '#', "digit -> #");
        check(SectionIndex.initial("") == '#', "empty -> #");
        check(SectionIndex.initial("Ünder") == 'U', "umlaut");
        List<String> titles = new ArrayList<>(Arrays.asList("zebra", "#1 Hit", "apple", "Émile", "banana", "Echo"));
        SectionIndex.sort(titles, t -> t);
        check(titles.equals(Arrays.asList("apple", "banana", "Echo", "Émile", "zebra", "#1 Hit")), "order " + titles);
        SectionIndex index = new SectionIndex(titles);
        check(index.positionForSection(0) == 0, "A at 0");
        check(index.positionForSection(4) == 2, "E at 2");
        check(index.positionForSection(2) == 2, "empty C jumps to next (E)");
        check(index.positionForSection(SectionIndex.OTHER) == 5, "# last");
        check(index.sectionForPosition(4) == 25, "zebra is Z");
        check(index.present().equals(Arrays.asList("A", "B", "E", "Z", "#")), "present " + index.present());
        SectionIndex empty = new SectionIndex(new ArrayList<>());
        check(empty.positionForSection(3) == 0 && empty.sectionForPosition(5) == 0, "empty safe");
        System.out.println("SectionIndexTest OK");
    }
}
