package dev.lilt.player.app;

/** Stack navigation per tab plus full-screen presentations. Main thread only. */
public interface Navigator {
    /** Push onto the current tab's stack with the standard slide/fade transition. */
    void push(Screen screen);

    /** Pop the top screen of the current stack; false when already at the root. */
    boolean pop();

    /** Switch tab; selecting the current tab again pops to its root (scroll to top). */
    void switchTab(Tab tab);

    Tab currentTab();

    /** Present a full-screen modal (Now Playing, Lyrics, Queue, Sound, Replay...). */
    void present(Screen screen);

    /** Dismiss the top-most presented screen; false when none. */
    boolean dismiss();

    Screen top();
}
