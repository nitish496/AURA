package dev.lilt.player.stream;

/** Emoji stripping for catalogue titles. */
public final class RemoteTextTest {
    private static void eq(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }

    public static void main(String[] args) {
        eq("Low Tide", RemoteText.stripEmoji("Low Tide"));
        eq("Low Tide", RemoteText.stripEmoji("🔥 Low Tide 🔥"));
        eq("Night Bus Home", RemoteText.stripEmoji("Night Bus ❤️ Home"));
        eq("Kinetic", RemoteText.stripEmoji("Kinetic 🇮🇳"));
        eq("Mira Vale", RemoteText.stripEmoji("Mira 👩‍🎤 Vale"));
        eq("Beyoncé – Café", RemoteText.stripEmoji("Beyoncé – Café"));
        eq("東京", RemoteText.stripEmoji("東京"));
        eq("🔥", RemoteText.stripEmoji("🔥"));
        eq(null, RemoteText.stripEmoji(null));
        eq("", RemoteText.stripEmoji(""));
        System.out.println("RemoteTextTest OK");
    }
}
