package dev.lilt.player.audio;

import android.content.Context;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import java.io.File;

/**
 * One {@link SimpleCache} per process (SimpleCache locks its folder, so a second instance would
 * throw) in {@code cacheDir/media}, with an LRU budget of about 1 GB.
 *
 * <p>{@link #dataSourceFactory(Context)} routes by scheme: {@code content://}, {@code file://}
 * and other local URIs go straight to the platform (no copy in the cache), while http(s) streams
 * go through a {@link CacheDataSource} over {@link DefaultHttpDataSource}. Everything that is
 * streamed is written to the cache as it plays, so repeat listens work offline. Stream cache keys
 * come from {@link CacheKeys} so they survive catalogue host changes.
 */
@OptIn(markerClass = UnstableApi.class)
public final class MediaCache {
    public static final String USER_AGENT = "AURA/2.0 (Android)";
    private static final int CONNECT_TIMEOUT_MS = 10_000, READ_TIMEOUT_MS = 15_000;
    private static SimpleCache cache;
    private static StandaloneDatabaseProvider database;
    private static DataSource.Factory factory;
    private static long budget;

    private MediaCache() {}

    /** The shared cache. Construction does no disk I/O on the calling thread (index loads on its own thread). */
    public static synchronized Cache cache(Context context) {
        if (cache == null) {
            Context app = context.getApplicationContext();
            File folder = new File(app.getCacheDir(), "media");
            database = new StandaloneDatabaseProvider(app);
            File parent = folder.getParentFile();
            budget = CacheKeys.budgetBytes(parent == null ? 0 : parent.getUsableSpace());
            cache = new SimpleCache(folder, new LeastRecentlyUsedCacheEvictor(budget), database);
        }
        return cache;
    }

    public static DefaultHttpDataSource.Factory httpFactory() {
        return new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS);
    }

    /** Data sources for playback and notification artwork: local URIs direct, http(s) cached. */
    public static synchronized DataSource.Factory dataSourceFactory(Context context) {
        if (factory == null) {
            Context app = context.getApplicationContext();
            CacheDataSource.Factory streams = new CacheDataSource.Factory()
                    .setCache(cache(app))
                    .setUpstreamDataSourceFactory(httpFactory())
                    .setCacheKeyFactory(spec -> spec.key != null ? spec.key : CacheKeys.forUri(spec.uri.toString()))
                    // A damaged cache must never stop playback: fall back to the network.
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            factory = new DefaultDataSource.Factory(app, streams);
        }
        return factory;
    }

    /** Bytes used by cached streams and artwork. Blocking (waits for the cache index): worker threads only. */
    public static long usedBytes(Context context) {
        return cache(context).getCacheSpace();
    }

    /** LRU limit in bytes chosen at start-up (about 1 GB, less on a nearly full phone). */
    public static synchronized long limitBytes(Context context) {
        cache(context);
        return budget;
    }

    /** Deletes every cached stream and artwork. Blocking: worker threads only. */
    public static void clear(Context context) {
        Cache shared = cache(context);
        for (String key : shared.getKeys()) {
            try { shared.removeResource(key); } catch (RuntimeException inUse) { /* removed on a later clear */ }
        }
    }
}
