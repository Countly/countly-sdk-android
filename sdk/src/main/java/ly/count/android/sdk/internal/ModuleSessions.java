package ly.count.android.sdk.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ly.count.android.sdk.Session;

/**
 * Sessions module responsible for default sessions handling: starting a session when
 * first {@link android.app.Activity} is started, stopping it when
 * last {@link android.app.Activity} is stopped and updating it each {@link ly.count.android.sdk.Config#sendUpdateEachSeconds}.
 */

public class ModuleSessions extends ModuleBase {
    private int activityCount;
    private int updateInterval = 0;
    private ScheduledExecutorService executor = null;

    /**
     * @throws IllegalArgumentException when programmaticSessionsControl is on since this module is
     * for a case when it's off
     */
    @Override
    public void init(InternalConfig config) throws IllegalStateException {
        super.init(config);
        if (config.isProgrammaticSessionsControl()) {
            // should never happen actually
            throw new IllegalStateException("ModuleSessions must not be initialized when programmaticSessionsControl is on");
        }
        updateInterval = config.getSendUpdateEachSeconds();
    }

    @Override
    public boolean isActive() {
        return super.isActive() || executor != null;
    }

    @Override
    public void stop(Context ctx, boolean clear) {
        if (executor != null) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        Log.e("Sessions update thread must be locked");
                    }
                }
            } catch (Throwable t) {
                Log.e("Error while stopping session update thread", t);
            }
            executor = null;
        }

        if (clear) {
            Core.purgeInternalStorage(ctx, SessionImpl.getStoragePrefix());
        }
    }

    @Override
    public synchronized void onActivityStarted(Context ctx) {
        super.onActivityStarted(ctx);
        if (activityCount == 0) {
            Core.instance.sessionBegin(ctx, Core.instance.sessionAdd(ctx));
            if (updateInterval > 0) {
                executor = Executors.newScheduledThreadPool(1);
                executor.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        if (isActive() && Core.instance.sessionLeading() != null) {
                            Core.instance.sessionLeading().update();
                        }
                    }
                }, updateInterval, updateInterval, TimeUnit.SECONDS);
            }
        }
        activityCount++;
    }

    @Override
    public synchronized void onActivityStopped(Context ctx) {
        super.onActivityStopped(ctx);
        activityCount--;
        if (activityCount == 0 && Core.instance.sessionLeading() != null) {
            if (executor != null) {
                try {
                    executor.shutdown();
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Log.w("Interrupted while waiting for session update executor to stop", e);
                    executor.shutdownNow();
                }
                executor = null;
            }
            Core.instance.sessionRemove(Core.instance.sessionEnd(ctx, Core.instance.sessionLeading()));
        }
    }
}
