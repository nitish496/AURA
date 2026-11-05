package dev.lilt.player.app;

/** One row of a glass context menu: label, trailing icon (an {@code ic_*} name such as "heart") and the action. */
public final class MenuAction {
    public final String label, icon;
    public final Runnable run;
    /** Starts a new group: a separator is drawn above this row. */
    public final boolean groupStart;
    /** Destructive rows (delete, remove) use the error colour. */
    public final boolean destructive;
    public MenuAction(String label, String icon, Runnable run) { this(label, icon, run, false, false); }
    public MenuAction(String label, String icon, Runnable run, boolean groupStart, boolean destructive) {
        this.label = label; this.icon = icon; this.run = run; this.groupStart = groupStart; this.destructive = destructive;
    }
    public MenuAction group() { return new MenuAction(label, icon, run, true, destructive); }
}
