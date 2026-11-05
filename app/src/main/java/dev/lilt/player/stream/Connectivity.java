package dev.lilt.player.stream;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

/**
 * Network state for the streaming UI (requires ACCESS_NETWORK_STATE). "Online" means the default
 * network offers validated internet access, so a captive portal counts as offline.
 */
public final class Connectivity {
    private Connectivity() {}

    /** Receives changes on the main thread. */
    public interface Listener { void onConnectivityChanged(boolean online); }

    /** Handle returned by {@link #listen(Context, Listener)}. */
    public interface Subscription { void cancel(); }

    /** True when the default network has validated internet access. Cheap; safe on the main thread. */
    public static boolean online(Context context) {
        ConnectivityManager manager = manager(context);
        if (manager == null) return false;
        try {
            Network network = manager.getActiveNetwork();
            return network != null && online(manager.getNetworkCapabilities(network));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** True when the default network is metered (mobile data, hotspot): prefer lighter artwork and no prefetch. */
    public static boolean metered(Context context) {
        ConnectivityManager manager = manager(context);
        if (manager == null) return true;
        try { return manager.isActiveNetworkMetered(); } catch (RuntimeException unavailable) { return true; }
    }

    /**
     * Calls {@code listener} on the main thread whenever online/offline flips (not for the current
     * state; read {@link #online(Context)} first). Cancel the subscription in {@code onDestroy}.
     */
    public static Subscription listen(Context context, Listener listener) {
        ConnectivityManager manager = manager(context);
        if (manager == null || listener == null) return () -> { };
        Handler main = new Handler(Looper.getMainLooper());
        boolean initial = online(context);
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            private boolean last = initial;
            private void report(boolean now) {
                if (last == now) return;
                last = now;
                listener.onConnectivityChanged(now);
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) { report(online(capabilities)); }
            @Override public void onLost(Network network) { report(false); }
        };
        try {
            manager.registerDefaultNetworkCallback(callback, main);
        } catch (RuntimeException unavailable) {
            return () -> { };
        }
        return () -> {
            try { manager.unregisterNetworkCallback(callback); } catch (RuntimeException alreadyGone) { /* idempotent */ }
        };
    }

    private static boolean online(NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static ConnectivityManager manager(Context context) {
        if (context == null) return null;
        return context.getApplicationContext().getSystemService(ConnectivityManager.class);
    }
}
