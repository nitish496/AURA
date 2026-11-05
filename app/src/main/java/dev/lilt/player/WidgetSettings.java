package dev.lilt.player;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.EdgeToEdge;
import dev.lilt.player.ui.GroupedSection;
import dev.lilt.player.ui.ListRow;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.Theme;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/** Widget configuration: a theme choice (Dark, Light, Ember, Artwork) in the AURA design language. */
@androidx.media3.common.util.UnstableApi
public final class WidgetSettings extends Activity {
    private static final String[] NAMES = {"Dark", "Light", "Ember", "Artwork"};
    private static final String[] NOTES = {"Graphite", "Light grey", "Graphite with an Ember play button", "Colour follows the cover"};
    private int selected;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        android.appwidget.AppWidgetProviderInfo info = id == AppWidgetManager.INVALID_APPWIDGET_ID ? null : AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null || !getPackageName().equals(info.provider.getPackageName())) { finish(); return; }
        ColorRoles roles = Theme.colors(this);
        EdgeToEdge.apply(this, Theme.isDark(this));
        selected = Math.max(0, Math.min(3, PlayerWidget.prefs(this).getInt("theme:" + id, 3)));
        LinearLayout form = Ui.column(this);
        form.setBackgroundColor(roles.canvas);
        form.addView(Type.text(this, TypeScale.LARGE_TITLE, roles.textPrimary, "Widget"), Ui.margins(this, new LinearLayout.LayoutParams(-2, -2), 16, 24, 16, 8));
        GroupedSection group = new GroupedSection(this, roles, "Appearance");
        ListRow[] rows = new ListRow[4];
        for (int i = 0; i < 4; i++) {
            final int index = i;
            rows[i] = group.add(new ListRow(this, roles).title(NAMES[i]).subtitle(NOTES[i]).accessory(i == selected ? ListRow.CHECK : ListRow.NONE));
            rows[i].setOnClickListener(v -> { selected = index; for (int k = 0; k < 4; k++) rows[k].accessory(k == selected ? ListRow.CHECK : ListRow.NONE); });
        }
        form.addView(group);
        PillButton save = new PillButton(this, roles, PillButton.FILLED, "Save", 0);
        save.setOnClickListener(v -> {
            PlayerWidget.prefs(this).edit().putInt("theme:" + id, selected).apply();
            PlayerWidget.update(this, AppWidgetManager.getInstance(this), id);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        });
        form.addView(save, Ui.margins(this, new LinearLayout.LayoutParams(-1, -2), 16, 24, 16, 24));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(form);
        EdgeToEdge.padding(scroll, EdgeToEdge.TOP | EdgeToEdge.BOTTOM);
        setContentView(scroll);
    }
}
