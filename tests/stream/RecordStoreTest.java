package dev.lilt.player.stream;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONException;
import org.json.JSONObject;

/** The persisted, bounded, ordered store behind RemoteTrackStore and SavedCatalog. */
public final class RecordStoreTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static void equal(Object expected, Object actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected <" + expected + "> but was <" + actual + ">");
        checks++;
    }

    private static final RecordStore.Codec<String> CODEC = new RecordStore.Codec<String>() {
        @Override public JSONObject encode(String value) throws JSONException { return new JSONObject().put("title", value); }
        @Override public String decode(JSONObject json) {
            String title = json == null ? "" : AudiusParser.text(json, "title");
            return title.isEmpty() ? null : title;
        }
    };

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("aura-store-test");
        File file = new File(dir.toFile(), "nested/store.json");
        AtomicLong clock = new AtomicLong(1_000);
        try {
            RecordStore<String> lru = new RecordStore<>(file, CODEC, 3, RecordStore.Overflow.EVICT_OLDEST, 1 << 20, 60_000, clock::get);
            equal(RecordStore.ADDED, lru.put("a", "A", true), "add a");
            clock.set(2_000);
            lru.put("b", "B", true);
            clock.set(3_000);
            lru.put("c", "C", true);
            equal(Arrays.asList("C", "B", "A"), lru.newestFirst(), "newest first");
            List<String> cached = lru.newestFirst();
            check(cached == lru.newestFirst(), "snapshot reused until a change");
            clock.set(4_000);
            equal(RecordStore.UPDATED, lru.put("a", "A2", true), "touch existing");
            equal(Arrays.asList("A2", "C", "B"), lru.newestFirst(), "touch moves to newest");
            equal(4_000L, lru.time("a"), "touch refreshes time");
            lru.put("b", "B2", false);
            equal(Arrays.asList("A2", "C", "B2"), lru.newestFirst(), "update in place keeps position");
            equal(2_000L, lru.time("b"), "update in place keeps time");
            clock.set(5_000);
            lru.put("d", "D", true);
            check(!lru.contains("b") && lru.size() == 3, "least recently put evicted");
            equal("C", lru.remove("c"), "remove returns value");
            equal(null, lru.remove("c"), "remove twice");
            check(lru.revision() > 0, "revision counts changes");
            lru.flush();
            check(file.isFile(), "flush writes (parent created)");
            String[] leftovers = file.getParentFile().list((d, n) -> n.endsWith(".tmp"));
            equal(0, leftovers.length, "no temporary files left");

            RecordStore<String> reloaded = new RecordStore<>(file, CODEC, 3, RecordStore.Overflow.EVICT_OLDEST, 1 << 20, 60_000, clock::get);
            equal(Arrays.asList("D", "A2"), reloaded.newestFirst(), "order survives a restart");
            equal(4_000L, reloaded.time("a"), "times survive a restart");
            equal(0L, reloaded.time("missing"), "absent time is zero");

            // REJECT overflow (the saved library never silently drops songs).
            RecordStore<String> library = new RecordStore<>(null, CODEC, 2, RecordStore.Overflow.REJECT, 1 << 20, 0, clock::get);
            library.put("1", "one", false);
            library.put("2", "two", false);
            check(library.full(), "full");
            equal(RecordStore.REJECTED, library.put("3", "three", false), "rejected when full");
            equal(RecordStore.UPDATED, library.put("1", "uno", false), "updates allowed when full");
            equal(Arrays.asList("two", "uno"), library.newestFirst(), "memory-only store");

            // Unusable entries are dropped; a corrupt file is moved aside instead of crashing.
            Files.write(file.toPath(), ("{\"v\":1,\"items\":[{\"id\":\"x\",\"t\":5,\"value\":{\"title\":\"X\"}},{\"id\":\"\",\"value\":{\"title\":\"no id\"}},"
                    + "{\"id\":\"y\",\"value\":{}},\"junk\",{\"id\":\"x\",\"t\":9,\"value\":{\"title\":\"X2\"}}]}").getBytes(StandardCharsets.UTF_8));
            RecordStore<String> tolerant = new RecordStore<>(file, CODEC, 10, RecordStore.Overflow.EVICT_OLDEST, 1 << 20, 60_000, clock::get);
            equal(Arrays.asList("X2"), tolerant.newestFirst(), "bad entries skipped, duplicate ids keep the last");
            Files.write(file.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
            RecordStore<String> corrupt = new RecordStore<>(file, CODEC, 10, RecordStore.Overflow.EVICT_OLDEST, 1 << 20, 60_000, clock::get);
            check(corrupt.newestFirst().isEmpty(), "corrupt file loads empty");
            check(new File(file.getPath() + ".corrupt").isFile() && !file.exists(), "corrupt file kept aside");
            RecordStore<String> bounded = new RecordStore<>(new File(dir.toFile(), "big.json"), CODEC, 10, RecordStore.Overflow.EVICT_OLDEST, 16, 60_000, clock::get);
            Files.write(new File(dir.toFile(), "big.json").toPath(), new byte[64]);
            check(bounded.newestFirst().isEmpty(), "oversized file refused");

            // Writes are debounced onto a background thread.
            File debounced = new File(dir.toFile(), "debounced.json");
            RecordStore<String> auto = new RecordStore<>(debounced, CODEC, 10, RecordStore.Overflow.EVICT_OLDEST, 1 << 20, 50, clock::get);
            for (int i = 0; i < 20; i++) auto.put("k" + i, "v" + i, true);
            long deadline = System.currentTimeMillis() + 5_000;
            while (!debounced.isFile() && System.currentTimeMillis() < deadline) Thread.sleep(20);
            Thread.sleep(150);
            RecordStore<String> check = new RecordStore<>(debounced, CODEC, 10, RecordStore.Overflow.EVICT_OLDEST, 1 << 20, 50, clock::get);
            equal(10, check.size(), "background write holds the bounded state");
            equal("v19", check.newestFirst().get(0), "latest change written");

            // Atomic replace helper.
            File plain = new File(dir.toFile(), "plain.txt");
            JsonFiles.writeAtomic(plain, "first");
            JsonFiles.writeAtomic(plain, "second ✓");
            equal("second ✓", JsonFiles.read(plain, 1024), "atomic replace, UTF-8");
            equal(null, JsonFiles.read(new File(dir.toFile(), "absent"), 1024), "missing file reads as null");
        } finally {
            File[] files = dir.toFile().listFiles();
            if (files != null) for (File f : files) { File[] inner = f.listFiles(); if (inner != null) for (File g : inner) g.delete(); f.delete(); }
            dir.toFile().delete();
        }
        System.out.println(checks + " record store tests passed");
    }
}
