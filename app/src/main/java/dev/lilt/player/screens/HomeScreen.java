package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;

/** PLACEHOLDER — replaced by the owning frontend agent. Keep the class name and constructor. */
public final class HomeScreen extends Screen {
    
    public HomeScreen() {  }
    @Override protected View onCreateView(Context context) { TextView v = new TextView(context); v.setText("HomeScreen"); return v; }
    @Override public String key() { return "home"; }
    @Override public Tab tab() { return Tab.HOME; }

}
