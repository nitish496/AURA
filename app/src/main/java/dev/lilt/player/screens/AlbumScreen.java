package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;

/** PLACEHOLDER — replaced by the owning frontend agent. Keep the class name and constructor. */
public final class AlbumScreen extends Screen {
    private final String albumKey;
    public AlbumScreen(String albumKey) { this.albumKey = albumKey; }
    @Override protected View onCreateView(Context context) { TextView v = new TextView(context); v.setText("AlbumScreen"); return v; }
    @Override public String key() { return "album:" + albumKey; }
    @Override public Tab tab() { return Tab.LIBRARY; }

}
