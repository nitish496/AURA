package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;

/** PLACEHOLDER — replaced by the owning frontend agent. Keep the class name and constructor. */
public final class CatalogPlaylistScreen extends Screen {
    private final dev.lilt.player.stream.CatalogPlaylist playlist;
    public CatalogPlaylistScreen(dev.lilt.player.stream.CatalogPlaylist playlist) { this.playlist = playlist; }
    @Override protected View onCreateView(Context context) { TextView v = new TextView(context); v.setText("CatalogPlaylistScreen"); return v; }
    @Override public String key() { return "cplaylist:" + playlist.id; }
    @Override public Tab tab() { return Tab.BROWSE; }

}
