package dev.lilt.player.app.chrome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import dev.lilt.player.DesignTokens;
import dev.lilt.player.R;
import dev.lilt.player.Track;
import dev.lilt.player.ui.ColorRoles;
import dev.lilt.player.ui.GlassView;
import dev.lilt.player.ui.Icons;
import dev.lilt.player.ui.Type;
import dev.lilt.player.ui.TypeScale;
import dev.lilt.player.ui.Ui;

/**
 * Glass accessory above the tab bar: artwork, title / artist, play-pause and next. Tapping it or
 * swiping up opens Now Playing. When the tab bar minimises it "docks" (docked = 1) into a slimmer
 * 48dp capsule between the two circles: smaller artwork, title only.
 */
public final class MiniPlayer extends GlassView {
    public interface Listener {
        void onOpen();
        void onToggle();
        void onNext();
    }

    private final ColorRoles roles;
    private final ImageView cover, playPause, next;
    private final TextView title, artist;
    private Listener listener;
    private float docked;
    private boolean playing;
    private final GestureDetector gestures;

    @SuppressLint("ClickableViewAccessibility")
    public MiniPlayer(Context c, ColorRoles roles) {
        super(c, roles);
        this.roles = roles;
        setClipChildren(false);
        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.roundOutline(cover, Ui.dpf(c, 8));
        cover.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(cover);
        title = Type.text(c, TypeScale.CARD_TITLE, roles.textPrimary);
        Type.singleLine(title);
        artist = Type.text(c, TypeScale.FOOTNOTE, roles.textSecondary);
        Type.singleLine(artist);
        addView(title);
        addView(artist);
        playPause = button(c, R.drawable.ic_play, "Play");
        next = button(c, R.drawable.ic_next, "Next song");
        playPause.setOnClickListener(v -> { if (listener != null) listener.onToggle(); });
        next.setOnClickListener(v -> { if (listener != null) listener.onNext(); });
        addView(playPause);
        addView(next);
        setClickable(true);
        setContentDescription("Now Playing");
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { if (listener != null) listener.onOpen(); return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (vy < -600 && listener != null) { listener.onOpen(); return true; }
                return false;
            }
        });
        setOnTouchListener((v, e) -> gestures.onTouchEvent(e));
    }

    private ImageView button(Context c, int res, String description) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setImageDrawable(Icons.get(c, res, roles.textPrimary));
        v.setBackground(Ui.borderlessRipple(roles, Ui.dp(c, 22)));
        v.setContentDescription(description);
        return v;
    }

    public void setListener(Listener l) { listener = l; }
    public ImageView cover() { return cover; }

    public void bind(Track track) {
        if (track == null) return;
        title.setText(track.title);
        artist.setText(track.artist);
        setContentDescription("Now Playing: " + track.title + ", " + track.artist);
    }

    public void setPlaying(boolean isPlaying) {
        if (playing == isPlaying && playPause.getTag() != null) return;
        playing = isPlaying;
        playPause.setTag(Boolean.TRUE);
        playPause.setImageDrawable(Icons.get(getContext(), isPlaying ? R.drawable.ic_pause : R.drawable.ic_play, roles.textPrimary));
        playPause.setContentDescription(isPlaying ? "Pause" : "Play");
    }

    /** 0 = full accessory above the tab bar, 1 = docked beside the minimised circles. */
    public void setDocked(float d) {
        docked = d;
        artist.setAlpha(Math.max(0f, 1f - d * 2.2f));
        requestLayout();
    }

    private int lerp(float a, float b) { return Math.round(a + (b - a) * docked); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        Context c = getContext();
        int coverPx = Ui.dp(c, lerp(DesignTokens.COVER_MINI, DesignTokens.COVER_MINI_DOCKED));
        int exact = MeasureSpec.EXACTLY;
        cover.measure(MeasureSpec.makeMeasureSpec(coverPx, exact), MeasureSpec.makeMeasureSpec(coverPx, exact));
        int btn = Ui.dp(c, lerp(44, 40));
        playPause.measure(MeasureSpec.makeMeasureSpec(btn, exact), MeasureSpec.makeMeasureSpec(btn, exact));
        next.measure(MeasureSpec.makeMeasureSpec(btn, exact), MeasureSpec.makeMeasureSpec(btn, exact));
        int textW = Math.max(0, w - Ui.dp(c, 8) - coverPx - Ui.dp(c, 12) - 2 * btn - Ui.dp(c, 8));
        int at = MeasureSpec.AT_MOST;
        title.measure(MeasureSpec.makeMeasureSpec(textW, at), MeasureSpec.makeMeasureSpec(h, at));
        artist.measure(MeasureSpec.makeMeasureSpec(textW, at), MeasureSpec.makeMeasureSpec(h, at));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Context c = getContext();
        int w = r - l, h = b - t, pad = Ui.dp(c, 8);
        int cp = cover.getMeasuredWidth();
        cover.layout(pad, (h - cp) / 2, pad + cp, (h + cp) / 2);
        int btn = next.getMeasuredWidth();
        next.layout(w - pad / 2 - btn, (h - btn) / 2, w - pad / 2, (h + btn) / 2);
        playPause.layout(w - pad / 2 - 2 * btn, (h - btn) / 2, w - pad / 2 - btn, (h + btn) / 2);
        int tx = pad + cp + Ui.dp(c, 12);
        int th = title.getMeasuredHeight(), ah = artist.getMeasuredHeight();
        // Title sits above the artist line; docked it is centred on its own.
        int blockH = Math.round(th + ah * (1 - docked));
        int ty = (h - blockH) / 2;
        title.layout(tx, ty, tx + title.getMeasuredWidth(), ty + th);
        artist.layout(tx, ty + th, tx + artist.getMeasuredWidth(), ty + th + ah);
    }
}
