package dev.lilt.player.screens.discover;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Heterogeneous ListView adapter for the discovery screens. Repeated rows (songs, headers,
 * genre pairs, results) use recycled view types; one-off blocks (titles, headers with artwork,
 * shelves, states) are {@link #STATIC} and keep their own view. Rows handle their own clicks, so
 * the ListView selector stays off. Main thread only.
 */
public final class Rows extends BaseAdapter {
    public static final int STATIC = AdapterView.ITEM_VIEW_TYPE_IGNORE;
    public static final int SONG = 0, HEADER = 1, GENRES = 2, ARTIST = 3, PLAYLIST = 4, RECENT = 5, SKELETON_ROW = 6, RECENT_SQUARE = 7;
    private static final int TYPES = 8;

    /** One list item. Recyclable items implement {@link #create} and {@link #bind}; static ones {@link #view}. */
    public abstract static class Item {
        public abstract int type();
        public View create(ViewGroup parent) { throw new UnsupportedOperationException("static item"); }
        public void bind(View view) {}
        /** The item's own view ({@link #STATIC} items only). */
        public View view() { return null; }
    }

    /** A one-off view. */
    public static Item of(View view) {
        return new Item() {
            @Override public int type() { return STATIC; }
            @Override public View view() { return view; }
        };
    }

    private List<Item> items = Collections.emptyList();

    public void set(List<Item> list) {
        items = list == null ? Collections.<Item>emptyList() : new ArrayList<>(list);
        notifyDataSetChanged();
    }

    public List<Item> items() { return items; }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public int getViewTypeCount() { return TYPES; }
    @Override public int getItemViewType(int position) { return items.get(position).type(); }
    @Override public boolean areAllItemsEnabled() { return false; }
    @Override public boolean isEnabled(int position) { return false; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Item item = items.get(position);
        if (item.type() == STATIC) {
            View v = item.view();
            if (v.getParent() instanceof ViewGroup && v.getParent() != parent) ((ViewGroup) v.getParent()).removeView(v);
            return v;
        }
        View v = convertView != null ? convertView : item.create(parent);
        item.bind(v);
        return v;
    }
}
