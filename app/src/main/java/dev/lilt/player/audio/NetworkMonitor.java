package dev.lilt.player.audio;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;

/**
 * Connectivity for playback recovery: whether the phone is online, and a callback (on the given
 * handler's thread) when a validated internet connection becomes available. Without the
 * ACCESS_NETWORK_STATE permission it reports online, so errors are retried instead of waiting.
 */
public final class NetworkMonitor {
    public interface Listener { void onOnline(); }

    private final ConnectivityManager connectivity;
    private final Handler handler;
    private final Listener listener;
    private ConnectivityManager.NetworkCallback callback;

    public NetworkMonitor(Context context, Handler handler, Listener listener) {
        this.connectivity = context.getApplicationContext().getSystemService(ConnectivityManager.class);
        this.handler = handler;
        this.listener = listener;
    }

    public boolean online() {
        if (connectivity == null) return true;
        try {
            Network network = connectivity.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException unavailable) {
            return true;
        }
    }

    public void start() {
        if (connectivity == null || callback != null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    handler.post(listener::onOnline);
                }
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(callback, handler);
        } catch (RuntimeException unavailable) {
            callback = null;
        }
    }

    public void stop() {
        if (connectivity == null || callback == null) return;
        try { connectivity.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) {}
        callback = null;
    }
}
