package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;

/** PLACEHOLDER — replaced by the owning frontend agent. Keep the class name and constructor. */
public final class SearchScreen extends Screen {
    private final String initialQuery;
    public SearchScreen(String initialQuery) { this.initialQuery = initialQuery == null ? "" : initialQuery; }
    @Override protected View onCreateView(Context context) { TextView v = new TextView(context); v.setText("SearchScreen"); return v; }
    @Override public String key() { return "search"; }
    @Override public Tab tab() { return Tab.SEARCH; }

}
