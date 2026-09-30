package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class CountlyTimer {

    ScheduledExecutorService timerService;
    //Volatile: written by whichever thread calls stopTimer/startTimer, read by the timer thread.
    private volatile boolean stopped = false;
    protected static int TIMER_DELAY_MS = 0; // for testing purposes

    protected void stopTimer(@NonNull ModuleLog L) {
        if (timerService != null) {
            L.i("[CountlyTimer] stopTimer, Stopping timer");
            try {
                //Set before shutting down so a tick that is already running, or one that wins the race with
                //shutdown(), returns without doing any work.
                stopped = true;
                //shutdown() only, and deliberately no awaitTermination. The runnable is scheduled with
                //scheduleWithFixedDelay, so shutdown() cancels every future execution on its own. Awaiting it
                //blocked the caller for up to 1s + 1s, and the callers are the main thread: module halt()
                //during a teardown, and startTimer() restarting a timer when the server changes its interval
                //(that arrives on ImmediateRequestMaker's onPostExecute, i.e. the main thread). Neither can
                //afford a two second stall.
                timerService.shutdown();
            } catch (Exception e) {
                L.e("[CountlyTimer] stopTimer, Error while stopping global timer " + e);
            }
            timerService = null;
        } else {
            L.d("[CountlyTimer] stopTimer, Timer already stopped");
        }
    }

    /**
     * Start a timer with the given delay
     *
     * @param timerDelay in seconds
     * @param runnable to run
     * @param L logger
     */
    protected void startTimer(long timerDelay, @NonNull Runnable runnable, @NonNull ModuleLog L) {
        startTimer(timerDelay, 0, runnable, L);
    }

    protected void startTimer(long timerDelay, long initialDelayMS, @NonNull Runnable runnable, @NonNull ModuleLog L) {
        long timerDelayInternal = timerDelay * 1000;

        if (timerDelayInternal < UtilsTime.ONE_SECOND_IN_MS) {
            timerDelayInternal = UtilsTime.ONE_SECOND_IN_MS;
        }

        if (TIMER_DELAY_MS > 0) {
            timerDelayInternal = TIMER_DELAY_MS;
        }

        L.i("[CountlyTimer] startTimer, Starting timer timerDelay: [" + timerDelayInternal + " ms], initialDelay: [" + initialDelayMS + " ms]");

        if (timerService != null) {
            L.d("[CountlyTimer] startTimer, timer was running, stopping it");
            stopTimer(L);
        }

        stopped = false;
        timerService = Executors.newSingleThreadScheduledExecutor();
        //Wrapped rather than scheduled directly. shutdown() already prevents any further execution of a
        //fixed-delay task, so this gate covers only the narrow case it cannot: a tick that has been dequeued
        //and is about to enter run() at the instant stopTimer lands. It is free, so it is worth closing - but
        //it is deliberately not covered by a test, because that interleaving cannot be forced deterministically
        //and a test that passes either way is worse than none.
        timerService.scheduleWithFixedDelay(() -> {
            if (stopped) {
                return;
            }
            runnable.run();
        }, initialDelayMS, timerDelayInternal, TimeUnit.MILLISECONDS);
    }
}
