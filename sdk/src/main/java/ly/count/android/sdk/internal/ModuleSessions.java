package ly.count.android.sdk.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ly.count.android.sdk.Config;

/**
 * Sessions module responsible for default sessions handling: starting a session when
 * first {@link android.app.Activity} is started, stopping it when
 * last {@link android.app.Activity} is stopped and updating it each {@link ly.count.android.sdk.Config#sendUpdateEachSeconds}.
 */

public class ModuleSessions extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleSessions");

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
                        L.e("Sessions update thread must be locked");
                    }
                }
            } catch (Throwable t) {
                L.e("Error while stopping session update thread", t);
            }
            executor = null;
        }

        if (clear) {
            Core.purgeInternalStorage(ctx, SessionImpl.getStoragePrefix());
        }
    }

    /**
     * Handles one single case of device id change with auto sessions handling, see first {@code if} here:
     * @see ModuleDeviceId#onDeviceId(Context, Config.DID, Config.DID)
     */
    public void onDeviceId(final Context ctx, final Config.DID deviceId, final Config.DID oldDeviceId) {
        if (deviceId != null && oldDeviceId != null && deviceId.realm == Config.DeviceIdRealm.DEVICE_ID && !deviceId.equals(oldDeviceId) && Core.instance.sessionLeading() == null) {
            Core.instance.sessionAdd(ctx).begin();
        }
    }

    @Override
    public synchronized void onActivityStarted(Context ctx) {
        if (activityCount == 0) {
            L.i("starting new session");
            Core.instance.sessionAdd(ctx).begin();
            if (updateInterval > 0 && executor == null) {
                executor = Executors.newScheduledThreadPool(1);
                executor.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        if (isActive() && Core.instance.sessionLeading() != null) {
                            L.i("updating session");
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
        activityCount--;
        if (activityCount == 0 && Core.instance.sessionLeading() != null) {
            L.i("stopping session");
            if (executor != null) {
                try {
                    executor.shutdown();
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    L.w("Interrupted while waiting for session update executor to stop", e);
                    executor.shutdownNow();
                }
                executor = null;
            }
            Core.instance.sessionLeading().end();
        }
    }
}
