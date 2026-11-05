package dev.lilt.player.stream;

/**
 * Display cleanup for text that comes from the catalogue (Audius titles and artist names often
 * carry emoji; AURA's UI uses none). Pure Java; tests/stream/RemoteTextTest.
 */
public final class RemoteText {
    private RemoteText() {}

    /** Removes emoji, variation selectors and joiners, then trims and collapses runs of spaces. */
    public static String stripEmoji(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (!isEmojiLike(cp)) out.appendCodePoint(cp);
        }
        String cleaned = out.toString().replaceAll("\\s{2,}", " ").trim();
        return cleaned.isEmpty() ? s.trim() : cleaned; // never blank out a title that was only symbols
    }

    private static boolean isEmojiLike(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF)   // pictographs, emoticons, transport, flags, supplemental
                || (cp >= 0x2600 && cp <= 0x27BF)  // misc symbols, dingbats
                || (cp >= 0x2B00 && cp <= 0x2BFF && (cp == 0x2B50 || cp == 0x2B55 || cp == 0x2B1B || cp == 0x2B1C))
                || cp == 0x2764 || cp == 0x203C || cp == 0x2049
                || (cp >= 0xFE00 && cp <= 0xFE0F)  // variation selectors
                || cp == 0x200D || cp == 0x20E3    // joiner, keycap
                || (cp >= 0xE0020 && cp <= 0xE007F);
    }
}
