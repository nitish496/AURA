package dev.lilt.player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Size limits of the persisted queue: the service must never crash while saving or restoring. */
public final class QueueSnapshotLimitsTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static QueueSnapshot.Entry entry(int i) {
        return new QueueSnapshot.Entry("id" + i, "content://media/external/audio/media/" + i, "Title " + i, "Artist", "Album", "");
    }

    public static void main(String[] args) throws Exception {
        List<QueueSnapshot.Entry> full = new ArrayList<>();
        for (int i = 0; i < 2000; i++) full.add(entry(i));
        QueueSnapshot max = QueueSnapshot.decode(new QueueSnapshot(full, 1999, 5, false, 1).encode());
        check(max.entries.size() == 2000 && max.index == 1999, "2000 entries round-trip");
        check(max.entries.get(1234).id.equals("id1234"), "order preserved");

        full.add(entry(2000));
        try {
            new QueueSnapshot(full, 0, 0, false, 0);
            throw new AssertionError("2001 entries must be rejected");
        } catch (IllegalArgumentException expected) {
            checks++; // PlaybackStore caps at 2000 and catches this; it must stay an unchecked, catchable error
        }

        // Fields are truncated so DataOutputStream.writeUTF (65535-byte limit) can never throw,
        // even for 3-byte characters or emoji (two surrogate chars) everywhere.
        StringBuilder emoji = new StringBuilder();
        while (emoji.length() < 20000) emoji.append("🎵");
        StringBuilder telugu = new StringBuilder();
        while (telugu.length() < 30000) telugu.append("నువ్వే");
        QueueSnapshot.Entry huge = new QueueSnapshot.Entry(emoji.toString(), telugu.toString(), emoji.toString(), null, telugu.toString(), emoji.toString());
        QueueSnapshot restored = QueueSnapshot.decode(new QueueSnapshot(Collections.singletonList(huge), 0, 0, true, 2).encode());
        QueueSnapshot.Entry back = restored.entries.get(0);
        check(back.title.length() <= QueueSnapshot.MAX_FIELD_CHARS, "title truncated");
        check(back.uri.length() == QueueSnapshot.MAX_FIELD_CHARS, "BMP text truncated to the limit");
        check(!Character.isHighSurrogate(back.title.charAt(back.title.length() - 1)), "no dangling half emoji");
        check(back.title.codePoints().allMatch(cp -> cp == 0x1F3B5), "truncated emoji text is still valid text");
        check(back.artist.isEmpty(), "null fields become empty strings");

        // Odd-length emoji cut: limit falls between the two halves of a surrogate pair.
        String odd = "a" + emoji;
        QueueSnapshot.Entry oddEntry = new QueueSnapshot.Entry("x", "u", odd, "", "", "");
        check(oddEntry.title.length() == QueueSnapshot.MAX_FIELD_CHARS - 1, "cut moves before the high surrogate");

        // A random/corrupt record is always an IOException (caught by PlaybackStore), never a crash.
        byte[] good = max.encode();
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 300; i++) {
            byte[] bad = Arrays.copyOf(good, random.nextInt(good.length));
            if (bad.length > 16) bad[12 + random.nextInt(bad.length - 12)] ^= (byte) (1 + random.nextInt(255));
            try {
                QueueSnapshot.decode(bad);
            } catch (IOException expected) {
                // fine
            }
            checks++;
        }
        System.out.println("QueueSnapshotLimitsTest: " + checks + " checks passed");
    }
}
