package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class CountlyTimer {

    ScheduledExecutorService timerService;
    protected static int TIMER_DELAY_MS = 0; // for testing purposes

    protected void stopTimer(@NonNull ModuleLog L) {
        if (timerService != null) {
            L.i("[CountlyTimer] stopTimer, Stopping timer");
            try {
                timerService.shutdown();
                if (!timerService.awaitTermination(1, TimeUnit.SECONDS)) {
                    timerService.shutdownNow();
                    if (!timerService.awaitTermination(1, TimeUnit.SECONDS)) {
                        L.e("[CountlyTimer] stopTimer, Global timer must be locked");
                    }
                }
            } catch (Exception e) {
                L.e("[CountlyTimer] stopTimer, Error while stopping global timer " + e);
            }
            timerService = null;
        } else {
            L.d("[CountlyTimer] stopTimer, Timer already stopped");
        }
    }

    protected void startTimer(long timerDelay, long initialDelay, @NonNull Runnable runnable, @NonNull ModuleLog L) {
        long timerDelayInternal = timerDelay * 1000;

        if (timerDelayInternal < UtilsTime.ONE_SECOND_IN_MS) {
            timerDelayInternal = UtilsTime.ONE_SECOND_IN_MS;
        }

        if (TIMER_DELAY_MS > 0) {
            timerDelayInternal = TIMER_DELAY_MS;
        }

        L.i("[CountlyTimer] startTimer, Starting timer timerDelay: [" + timerDelayInternal + " ms]");

        if (timerService != null) {
            L.d("[CountlyTimer] startTimer, timer was running, stopping it");
            stopTimer(L);
        }

        timerService = Executors.newSingleThreadScheduledExecutor();
        timerService.scheduleWithFixedDelay(runnable, 0, timerDelayInternal, TimeUnit.MILLISECONDS);
    }
    
    protected void startTimer(long timerDelay, @NonNull Runnable runnable, @NonNull ModuleLog L) {
        startTimer(timerDelay, 0, runnable, L);
    }
}
