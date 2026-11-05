package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;

/** PLACEHOLDER — replaced by the owning frontend agent. Keep the class name and constructor. */
public final class LyricsScreen extends Screen {
    
    public LyricsScreen() {  }
    @Override protected View onCreateView(Context context) { TextView v = new TextView(context); v.setText("LyricsScreen"); return v; }
    @Override public String key() { return "lyrics"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override public boolean fullScreen() { return true; }
}
