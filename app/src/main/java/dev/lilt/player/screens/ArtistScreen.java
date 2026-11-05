package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;

/** PLACEHOLDER — replaced by the owning frontend agent. Keep the class name and constructor. */
public final class ArtistScreen extends Screen {
    private final String artistName;
    public ArtistScreen(String artistName) { this.artistName = artistName; }
    @Override protected View onCreateView(Context context) { TextView v = new TextView(context); v.setText("ArtistScreen"); return v; }
    @Override public String key() { return "artist:" + artistName; }
    @Override public Tab tab() { return Tab.LIBRARY; }

}
