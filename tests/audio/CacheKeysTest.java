package dev.lilt.player.audio;

/** Stream cache keys survive host failover; the cache budget respects free space. */
public final class CacheKeysTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }

    public static void main(String[] args) {
        String a = CacheKeys.forUri("https://discoveryprovider.audius.co/v1/tracks/D7KyD/stream?app_name=AURA");
        String b = CacheKeys.forUri("https://api.audius.co/v1/tracks/D7KyD/stream");
        check(a.equals("audius-stream:D7KyD") && a.equals(b), "same track, different hosts: one key");
        check(!CacheKeys.forUri("https://h/v1/tracks/Other/stream").equals(a), "different tracks differ");
        String meta = "https://h/v1/tracks/D7KyD?app_name=AURA";
        check(CacheKeys.forUri(meta).equals(meta), "metadata endpoint keeps its URL");
        String odd = "https://h/v1/tracks/D7KyD/streamfoo";
        check(CacheKeys.forUri(odd).equals(odd), "only the stream endpoint");
        String art = "https://img.example/1000x1000.jpg";
        check(CacheKeys.forUri(art).equals(art), "artwork keyed by URL");
        String local = "content://media/external/audio/media/5";
        check(CacheKeys.forUri(local).equals(local) && CacheKeys.forUri(null).isEmpty(), "non-http input");

        long gb = 1024L * 1024 * 1024;
        check(CacheKeys.budgetBytes(100 * gb) == CacheKeys.MAX_CACHE_BYTES, "about 1 GB with plenty of space");
        check(CacheKeys.budgetBytes(2 * gb) == 2 * gb / 5, "a fifth of free space when tight");
        check(CacheKeys.budgetBytes(100L * 1024 * 1024) == CacheKeys.MIN_CACHE_BYTES && CacheKeys.budgetBytes(0) == CacheKeys.MIN_CACHE_BYTES, "floor");
        System.out.println("CacheKeysTest: " + checks + " checks passed");
    }
}
