package dev.lilt.player.screens;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;

/** PLACEHOLDER — replaced by the owning frontend agent. Keep the class name and constructor. */
public final class CatalogArtistScreen extends Screen {
    private final String userId, name;
    public CatalogArtistScreen(String userId, String name) { this.userId = userId; this.name = name; }
    @Override protected View onCreateView(Context context) { TextView v = new TextView(context); v.setText("CatalogArtistScreen"); return v; }
    @Override public String key() { return "cartist:" + userId; }
    @Override public Tab tab() { return Tab.BROWSE; }

}
