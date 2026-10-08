package dev.lilt.player;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Main-thread, elapsed-time timer. Owner must cancel it when its playback owner ends. */
public final class SleepTimer {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable pause;
    private long deadline;
    private final Runnable finish;
    public SleepTimer(Runnable pause) {
        this.pause=pause;
        finish=()->{deadline=0;this.pause.run();};
    }
    public void start(int minutes) {
        cancel();
        if(minutes<=0)return;
        long delay=Math.min(minutes,1440)*60_000L;
        deadline=SystemClock.elapsedRealtime()+delay;
        handler.postDelayed(finish,delay);
    }
    public void cancel() { handler.removeCallbacks(finish);deadline=0; }
    public long remainingMillis() { return Math.max(0,deadline-SystemClock.elapsedRealtime()); }
    public boolean isActive() { return deadline>0; }
}
