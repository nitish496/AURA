package dev.lilt.player;

/**
 * Library.scan()'s MediaStore selection. Library imports android.*, so this runs against the
 * Robolectric android-all jar (scripts/run-logic-tests.sh puts it on the classpath).
 */
public final class LibraryQueryTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String where = Library.AUDIO_SELECTION;
        check(where.startsWith("duration > 0"), "zero-length/broken files stay excluded");
        for (String flag : new String[] {"is_alarm", "is_notification", "is_ringtone"}) {
            // System sounds are not songs, but a NULL flag (old MediaProvider rows) must not hide music.
            check(where.contains("(" + flag + " IS NULL OR " + flag + " = 0)"), flag + " excluded, NULL tolerated");
        }
        check(!where.contains("is_music"), "is_music is not required: songs in Download/ etc. stay visible");
        check(!where.contains("?"), "no selection arguments are needed");
        // Balanced parentheses: a malformed selection makes MediaProvider throw at query time.
        int depth = 0;
        for (char c : where.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            check(depth >= 0, "parentheses never close early");
        }
        check(depth == 0, "parentheses balanced");
        System.out.println("LibraryQueryTest: " + checks + " checks passed");
    }
}
