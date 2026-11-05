package dev.lilt.player.screens;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import dev.lilt.player.PlaybackService;
import dev.lilt.player.R;
import dev.lilt.player.app.Screen;
import dev.lilt.player.app.Tab;
import dev.lilt.player.audio.AudioEffects;
import dev.lilt.player.audio.EqualizerPresets;
import dev.lilt.player.screens.player.EqualizerView;
import dev.lilt.player.screens.player.SessionLink;
import dev.lilt.player.screens.player.SleepText;
import dev.lilt.player.screens.player.SpeedSteps;
import dev.lilt.player.screens.player.StorageText;
import dev.lilt.player.ui.Chip;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassButton;
import dev.lilt.player.ui.GroupedSection;
import dev.lilt.player.ui.Haptics;
import dev.lilt.player.ui.ListRow;
import dev.lilt.player.ui.PillButton;
import dev.lilt.player.ui.Slider;
import dev.lilt.player.ui.Toggle;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * Sound settings: equaliser (real band count and range from the phone), loudness normalisation,
 * playback speed with snap points, crossfade (0 = gapless), sleep timer with a live
 * countdown, and the stream cache. Everything is read from and written to the playback service.
 */
@OptIn(markerClass = UnstableApi.class)
public final class SoundScreen extends Screen {
    private static final String[] SLEEP_LABELS = {"Off", "15 min", "30 min", "45 min", "1 hour", "End of song", "Custom"};

    private Context context;
    private ColorRoles roles;
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private int insetTop, insetBottom;
    private SessionLink link;
    private boolean visible;

    // Equaliser
    private Toggle eqToggle;
    private Chip.Row presetRow;
    private EqualizerView graph;
    private TextView eqUnavailable;
    private String[] presetNames = new String[0];
    private int[] bandFreqs = new int[0];
    private short[] range = {-1500, 1500};
    private boolean eqEnabled, eqAvailable;
    private String eqPreset = EqualizerPresets.FLAT;
    private long lastBandSend;

    // Playback
    private Toggle normaliseToggle;
    private Slider speedSlider, crossfadeSlider;
    private TextView speedValue, crossfadeValue;
    private float speed = 1f;

    // Sleep timer
    private TextView sleepValue;
    private Chip.Row sleepRow;
    private LinearLayout customSleep;
    private Slider customSlider;
    private TextView customValue;
    private long sleepEndsAt;
    private boolean sleepEndOfTrack;
    private int lastSleepMinutes;
    private int customMinutes = 30;
    private int sleepTicks;

    // Storage
    private TextView cacheValue;
    private View cacheUsed, cacheFree;
    private PillButton clearCache;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            if (sleepEndsAt > 0) {
                long left = sleepEndsAt - SystemClock.elapsedRealtime();
                if (left <= 0 || ++sleepTicks % 15 == 0) loadSleep();
                if (left > 0) sleepValue.setText(SleepText.summary(left, false));
                host.main().postDelayed(this, 1000);
            }
        }
    };

    public SoundScreen() { }

    @Override public String key() { return "sound"; }
    @Override public Tab tab() { return Tab.HOME; }
    @Override public boolean fullScreen() { return true; }

    @Override protected View onCreateView(Context c) {
        context = c;
        roles = host.colors();
        link = SessionLink.get(c);
        root = new FrameLayout(c);
        root.setBackgroundColor(roles.canvas);

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        content = Ui.column(c);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        buildEqualiser();
        buildPlayback();
        buildSleep();
        buildStorage();

        FrameLayout bar = new FrameLayout(c);
        GlassButton back = new GlassButton(c, roles, R.drawable.ic_back, "Back", 40);
        back.setBackdrop(scroll);
        back.setOnClickListener(v -> host.nav().dismiss());
        bar.addView(back, new FrameLayout.LayoutParams(back.size(), back.size(), Gravity.CENTER_VERTICAL | Gravity.START));
        TextView heading = Type.text(c, TypeScale.HEADLINE, roles.textPrimary, "Sound");
        heading.setGravity(Gravity.CENTER);
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        bar.addView(heading, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, Ui.dp(c, 52), Gravity.TOP);
        bp.setMarginStart(Ui.dp(c, 10));
        root.addView(bar, bp);
        applyInsets();
        return root;
    }

    // ---------------------------------------------------------------- building

    private void buildEqualiser() {
        GroupedSection group = new GroupedSection(context, roles, null);
        ListRow row = group.add(new ListRow(context, roles).title("Equaliser"));
        eqToggle = row.toggle(false);
        eqToggle.setListener((t, on) -> {
            eqEnabled = on;
            graph.setActive(on);
            Bundle b = new Bundle();
            b.putBoolean(AudioEffects.KEY_EQ_ENABLED, on);
            host.player().setAudioEffects(b);
        });
        presetRow = new Chip.Row(context);
        presetRow.setChips(roles, "Flat");
        presetRow.setListener(index -> {
            if (index < 0 || index >= presetNames.length) return;
            eqPreset = presetNames[index];
            if (EqualizerPresets.CUSTOM.equals(eqPreset)) return; // keeps the user's curve
            Bundle b = new Bundle();
            b.putString(AudioEffects.KEY_EQ_PRESET, eqPreset);
            if (!eqEnabled) { eqEnabled = true; eqToggle.setOn(true, true, false); graph.setActive(true); b.putBoolean(AudioEffects.KEY_EQ_ENABLED, true); }
            host.player().setAudioEffects(b);
            host.main().postDelayed(this::loadFx, 200);
        });
        group.add(presetRow);
        graph = new EqualizerView(context, roles);
        graph.setListener((levels, done) -> {
            long now = SystemClock.uptimeMillis();
            if (!done && now - lastBandSend < 120) return;
            lastBandSend = now;
            Bundle b = new Bundle();
            b.putShortArray(AudioEffects.KEY_EQ_BANDS, levels);
            if (done) eqPreset = EqualizerPresets.CUSTOM;
            host.player().setAudioEffects(b);
            if (done) presetRow.select(presetNames.length - 1);
        });
        group.add(graph);
        eqUnavailable = Type.text(context, TypeScale.CALLOUT, roles.textSecondary, "This phone doesn't offer an equaliser to apps.");
        eqUnavailable.setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 16));
        eqUnavailable.setVisibility(View.GONE);
        group.add(eqUnavailable);
        content.addView(group, section());
    }

    private void buildPlayback() {
        GroupedSection group = new GroupedSection(context, roles, "Playback");
        ListRow normalise = group.add(new ListRow(context, roles).title("Loudness normalisation").subtitle("Plays every song at a similar volume"));
        normaliseToggle = normalise.toggle(false);
        normaliseToggle.setListener((t, on) -> {
            Bundle b = new Bundle();
            b.putBoolean(AudioEffects.KEY_NORMALIZE, on);
            host.player().setAudioEffects(b);
        });

        LinearLayout speedBlock = sliderBlock("Playback speed");
        speedValue = (TextView) ((LinearLayout) speedBlock.getChildAt(0)).getChildAt(1);
        speedValue.setOnClickListener(v -> { Haptics.tick(v); setSpeed(1f, true); });
        speedValue.setContentDescription("Playback speed. Tap to reset to normal.");
        speedSlider = new Slider(context, roles, Slider.SETTINGS);
        speedSlider.setListener(new Slider.Listener() {
            @Override public void onChange(Slider s, float value, boolean fromUser) {
                if (!fromUser) return;
                float next = SpeedSteps.fromFraction(value);
                if (next != speed && SpeedSteps.isSnapPoint(next)) Haptics.tick(s);
                speed = next;
                speedValue.setText(SpeedSteps.label(speed));
            }
            @Override public void onStop(Slider s, float value) { setSpeed(SpeedSteps.fromFraction(value), true); }
        });
        speedBlock.addView(speedSlider, new LinearLayout.LayoutParams(-1, Ui.dp(context, 40)));
        group.add(speedBlock);

        LinearLayout fadeBlock = sliderBlock("Crossfade");
        crossfadeValue = (TextView) ((LinearLayout) fadeBlock.getChildAt(0)).getChildAt(1);
        crossfadeSlider = new Slider(context, roles, Slider.SETTINGS);
        crossfadeSlider.setSteps(12);
        crossfadeSlider.setListener(new Slider.Listener() {
            @Override public void onChange(Slider s, float value, boolean fromUser) { if (fromUser) crossfadeValue.setText(crossfadeLabel(Math.round(value * 12))); }
            @Override public void onStop(Slider s, float value) {
                Bundle b = new Bundle();
                b.putInt("crossfadeSeconds", Math.round(value * 12));
                host.player().setPlaybackOptions(b);
            }
        });
        fadeBlock.addView(crossfadeSlider, new LinearLayout.LayoutParams(-1, Ui.dp(context, 40)));
        TextView hint = Type.text(context, TypeScale.FOOTNOTE, roles.textSecondary, "Off keeps playback gapless.");
        fadeBlock.addView(hint, new LinearLayout.LayoutParams(-2, -2));
        group.add(fadeBlock);

        // Autoplay lives in the Queue (DESIGN.md: Queue toggles); not duplicated here.
        content.addView(group, section());
    }

    private LinearLayout sliderBlock(String name) {
        LinearLayout block = Ui.column(context);
        block.setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 10));
        LinearLayout top = Ui.row(context);
        TextView label = Type.text(context, TypeScale.BODY, roles.textPrimary, name);
        TextView value = Type.text(context, TypeScale.BODY, roles.textSecondary);
        Type.tabular(value);
        value.setMinHeight(Ui.dp(context, 32));
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(value, new LinearLayout.LayoutParams(-2, -2));
        block.addView(top, new LinearLayout.LayoutParams(-1, -2));
        return block;
    }

    private void buildSleep() {
        GroupedSection group = new GroupedSection(context, roles, "Sleep timer");
        LinearLayout head = Ui.row(context);
        head.setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), 0);
        TextView label = Type.text(context, TypeScale.BODY, roles.textPrimary, "Pause playback");
        sleepValue = Type.text(context, TypeScale.BODY, roles.textSecondary, "Off");
        Type.tabular(sleepValue);
        head.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(sleepValue, new LinearLayout.LayoutParams(-2, -2));
        group.add(head);
        sleepRow = new Chip.Row(context);
        sleepRow.setChips(roles, (CharSequence[]) SLEEP_LABELS);
        sleepRow.select(0);
        sleepRow.setListener(this::onSleepChip);
        group.add(sleepRow);

        customSleep = Ui.column(context);
        customSleep.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 12));
        LinearLayout crow = Ui.row(context);
        TextView cl = Type.text(context, TypeScale.CALLOUT, roles.textSecondary, "Custom length");
        customValue = Type.text(context, TypeScale.CALLOUT, roles.textPrimary, customMinutes + " min");
        Type.tabular(customValue);
        crow.addView(cl, new LinearLayout.LayoutParams(0, -2, 1f));
        crow.addView(customValue, new LinearLayout.LayoutParams(-2, -2));
        customSleep.addView(crow, new LinearLayout.LayoutParams(-1, -2));
        customSlider = new Slider(context, roles, Slider.SETTINGS);
        customSlider.setSteps(SleepText.CUSTOM_MAX - SleepText.CUSTOM_MIN);
        customSlider.setValue((customMinutes - SleepText.CUSTOM_MIN) / (float) (SleepText.CUSTOM_MAX - SleepText.CUSTOM_MIN));
        customSlider.setListener(new Slider.Listener() {
            @Override public void onChange(Slider s, float value, boolean fromUser) {
                customMinutes = SleepText.clampCustom(SleepText.CUSTOM_MIN + Math.round(value * (SleepText.CUSTOM_MAX - SleepText.CUSTOM_MIN)));
                customValue.setText(customMinutes + " min");
            }
        });
        customSleep.addView(customSlider, new LinearLayout.LayoutParams(-1, Ui.dp(context, 40)));
        PillButton start = new PillButton(context, roles, PillButton.SECONDARY, "Start Timer", 0);
        start.setOnClickListener(v -> startSleep(customMinutes));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, Ui.dp(context, 48));
        sp.topMargin = Ui.dp(context, 4);
        customSleep.addView(start, sp);
        customSleep.setVisibility(View.GONE);
        group.add(customSleep);
        TextView note = Type.text(context, TypeScale.FOOTNOTE, roles.textSecondary, "Playback fades out and pauses.");
        note.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 14));
        group.add(note);
        content.addView(group, section());
    }

    private void buildStorage() {
        GroupedSection group = new GroupedSection(context, roles, "Storage");
        LinearLayout block = Ui.column(context);
        block.setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 8));
        TextView name = Type.text(context, TypeScale.BODY, roles.textPrimary, "Stream cache");
        cacheValue = Type.text(context, TypeScale.FOOTNOTE, roles.textSecondary, "Calculating");
        Type.tabular(cacheValue);
        TextView explain = Type.text(context, TypeScale.FOOTNOTE, roles.textSecondary, "Songs you stream are kept so they play again without a connection.");
        LinearLayout bar = Ui.row(context);
        cacheUsed = new View(context);
        cacheUsed.setBackground(Ui.roundRect(roles.accent, -1));
        cacheFree = new View(context);
        cacheFree.setBackground(Ui.roundRect(roles.fillStrong, -1));
        bar.addView(cacheUsed, new LinearLayout.LayoutParams(0, -1, 0f));
        bar.addView(cacheFree, new LinearLayout.LayoutParams(0, -1, 1f));
        block.addView(name, new LinearLayout.LayoutParams(-2, -2));
        block.addView(cacheValue, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(context, 6));
        bp.topMargin = Ui.dp(context, 10);
        block.addView(bar, bp);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
        ep.topMargin = Ui.dp(context, 10);
        block.addView(explain, ep);
        clearCache = new PillButton(context, roles, PillButton.TEXT, "Clear Cache", 0);
        clearCache.setOnClickListener(v -> clearCache());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, Ui.dp(context, 48));
        cp.topMargin = Ui.dp(context, 4);
        cp.setMarginStart(-Ui.dp(context, 8));
        block.addView(clearCache, cp);
        group.add(block);
        content.addView(group, section());
    }

    private LinearLayout.LayoutParams section() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(context, 12);
        return lp;
    }

    // ---------------------------------------------------------------- insets / lifecycle

    @Override public void onInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        if (root != null) applyInsets();
    }

    private void applyInsets() {
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) root.getChildAt(1).getLayoutParams();
        bp.topMargin = insetTop;
        root.getChildAt(1).setLayoutParams(bp);
        scroll.setPadding(0, insetTop + Ui.dp(context, 52), 0, insetBottom + Ui.dp(context, 24));
    }

    @Override public void onShow() {
        visible = true;
        link.acquire();
        loadFx();
        loadOptions();
        loadSleep();
        loadCache();
    }

    @Override public void onHide() {
        visible = false;
        host.main().removeCallbacks(ticker);
        link.release();
    }

    @Override public void onDestroy() {
        visible = false;
        if (host != null) host.main().removeCallbacks(ticker);
    }

    @Override public void onThemeChanged() { if (root != null) root.setBackgroundColor(host.colors().canvas); }

    // ---------------------------------------------------------------- engine state

    private void loadFx() {
        host.player().audioEffects(b -> {
            if (b == null || !visible) return;
            eqAvailable = b.getBoolean(AudioEffects.KEY_EQ_AVAILABLE, false);
            eqEnabled = b.getBoolean(AudioEffects.KEY_EQ_ENABLED, false);
            String preset = b.getString(AudioEffects.KEY_EQ_PRESET, EqualizerPresets.FLAT);
            eqPreset = preset;
            String[] names = b.getStringArray(AudioEffects.KEY_EQ_PRESETS);
            if (names == null || names.length == 0) names = EqualizerPresets.names().toArray(new String[0]);
            String[] all = new String[names.length + 1];
            System.arraycopy(names, 0, all, 0, names.length);
            all[names.length] = EqualizerPresets.CUSTOM;
            boolean sameChips = java.util.Arrays.equals(all, presetNames);
            presetNames = all;
            if (!sameChips) presetRow.setChips(roles, (CharSequence[]) all);
            int sel = -1;
            for (int i = 0; i < all.length; i++) if (all[i].equalsIgnoreCase(preset)) sel = i;
            presetRow.select(sel);
            eqToggle.setOn(eqEnabled, false, false);
            bandFreqs = b.getIntArray(AudioEffects.KEY_EQ_FREQUENCIES);
            if (bandFreqs == null) bandFreqs = new int[0];
            short[] levels = b.getShortArray(AudioEffects.KEY_EQ_BANDS);
            short[] r = b.getShortArray(AudioEffects.KEY_EQ_RANGE);
            if (r != null && r.length == 2) range = r;
            graph.setData(bandFreqs, levels, range[0], range[1]);
            graph.setActive(eqEnabled);
            boolean show = eqAvailable && bandFreqs.length > 0;
            presetRow.setVisibility(show ? View.VISIBLE : View.GONE);
            graph.setVisibility(show ? View.VISIBLE : View.GONE);
            eqUnavailable.setVisibility(show ? View.GONE : View.VISIBLE);
            eqToggle.setEnabled(show);
            normaliseToggle.setOn(b.getBoolean(AudioEffects.KEY_NORMALIZE, false), false, false);
            setSpeed(b.getFloat(AudioEffects.KEY_SPEED, 1f), false);
        });
    }

    private void setSpeed(float value, boolean send) {
        speed = SpeedSteps.snap(value);
        speedValue.setText(SpeedSteps.label(speed));
        speedSlider.setValue(SpeedSteps.toFraction(speed));
        if (send) {
            Bundle b = new Bundle();
            b.putFloat(AudioEffects.KEY_SPEED, speed);
            host.player().setAudioEffects(b);
        }
    }

    private void loadOptions() {
        host.player().playbackOptions(b -> {
            if (b == null || !visible) return;
            int seconds = Math.max(0, Math.min(12, b.getInt("crossfadeSeconds", 0)));
            crossfadeSlider.setValue(seconds / 12f);
            crossfadeValue.setText(crossfadeLabel(seconds));
        });
    }

    private static String crossfadeLabel(int seconds) { return seconds <= 0 ? "Off" : seconds + " s"; }

    // ---------------------------------------------------------------- sleep timer

    private void onSleepChip(int index) {
        if (index == 0) { startSleep(0); customSleep.setVisibility(View.GONE); return; }
        if (index >= 1 && index <= 4) { customSleep.setVisibility(View.GONE); startSleep(SleepText.PRESETS[index - 1]); return; }
        if (index == 5) {
            customSleep.setVisibility(View.GONE);
            link.send(PlaybackService.SET_SLEEP_TIMER, endOfTrackArgs(), b -> { if (b != null) applySleep(b); else host.toast("Couldn't set the timer"); });
            Haptics.confirm(sleepRow);
            return;
        }
        customSleep.setVisibility(View.VISIBLE);
    }

    private static Bundle endOfTrackArgs() {
        Bundle b = new Bundle();
        b.putBoolean("endOfTrack", true);
        return b;
    }

    private void startSleep(int minutes) {
        lastSleepMinutes = minutes;
        host.player().setSleepTimer(minutes);
        if (minutes > 0) Haptics.confirm(sleepRow);
        sleepEndsAt = minutes > 0 ? SystemClock.elapsedRealtime() + minutes * 60_000L : 0;
        sleepEndOfTrack = false;
        showSleep(minutes > 0 ? minutes * 60_000L : 0);
        host.main().postDelayed(this::loadSleep, 300);
    }

    private void loadSleep() {
        link.send(PlaybackService.GET_SLEEP_TIMER, null, b -> { if (b != null && visible) applySleep(b); });
    }

    private void applySleep(Bundle b) {
        sleepEndOfTrack = b.getBoolean("endOfTrack", false);
        long remaining = b.getLong("remainingMillis", 0);
        sleepEndsAt = remaining > 0 ? SystemClock.elapsedRealtime() + remaining : 0;
        showSleep(remaining);
    }

    private void showSleep(long remaining) {
        sleepValue.setText(SleepText.summary(remaining, sleepEndOfTrack));
        int chip;
        if (sleepEndOfTrack) chip = 5;
        else if (remaining <= 0) chip = 0;
        else {
            int preset = SleepText.matchingPreset(remaining, lastSleepMinutes);
            chip = preset < 0 ? 6 : indexOf(SleepText.PRESETS, preset) + 1;
        }
        sleepRow.select(chip);
        host.main().removeCallbacks(ticker);
        if (sleepEndsAt > 0 && visible) host.main().postDelayed(ticker, 1000);
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    // ---------------------------------------------------------------- cache

    private void loadCache() { link.send(PlaybackService.GET_CACHE_INFO, null, this::showCache); }

    private void clearCache() {
        clearCache.setEnabled(false);
        link.send(PlaybackService.CLEAR_CACHE, null, b -> {
            clearCache.setEnabled(true);
            if (b == null) { host.toast("Couldn't clear the cache"); return; }
            Haptics.confirm(clearCache);
            host.toast("Stream cache cleared");
            showCache(b);
        });
    }

    private void showCache(Bundle b) {
        if (b == null || !visible) { if (b == null && cacheValue != null) cacheValue.setText("Not available"); return; }
        long used = b.getLong("cacheBytes", 0), limit = b.getLong("cacheLimitBytes", 0);
        cacheValue.setText(StorageText.usage(used, limit));
        float share = StorageText.share(used, limit);
        ((LinearLayout.LayoutParams) cacheUsed.getLayoutParams()).weight = share;
        ((LinearLayout.LayoutParams) cacheFree.getLayoutParams()).weight = Math.max(0.0001f, 1f - share);
        cacheUsed.setVisibility(share > 0 ? View.VISIBLE : View.INVISIBLE);
        cacheUsed.requestLayout();
        clearCache.setVisibility(used > 0 ? View.VISIBLE : View.GONE);
    }
}
