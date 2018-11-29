package ly.count.sdk.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ly.count.sdk.Config;

public class ModuleSessions extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleSessions");

    private int activityCount = 0;
    private ScheduledExecutorService executor = null;

    /**
     * Current Session instance
     */
    private SessionImpl session = null;

    public SessionImpl getSession() {
        return session;
    }

    public synchronized SessionImpl session(Ctx ctx, Long id) {
        if (session == null) {
            session = new SessionImpl(ctx, id);
        }
        return session;
    }

    public void forgetSession() {
        session = null;
    }

    @Override
    public Integer getFeature() {
        return CoreFeature.Sessions.getIndex();
    }

    /**
     * @throws IllegalArgumentException when {@link ly.count.sdk.Config#autoSessionsTracking}
     * is off since this module is for a case when it's on
     */
    @Override
    public void init(InternalConfig config) throws IllegalStateException {
        super.init(config);
    }

    @Override
    public boolean isActive() {
        return super.isActive() || executor != null;
    }

    @Override
    public void stop(Ctx ctx, boolean clear) {
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
            ctx.getSDK().storablePurge(ctx, SessionImpl.getStoragePrefix());
        }
    }

    /**
     * Handles one single case of device id change with auto sessions handling, see first {@code if} here:
     * @see ModuleDeviceId#onDeviceId(Ctx, Config.DID, Config.DID)
     */
    public void onDeviceId(final Ctx ctx, final Config.DID deviceId, final Config.DID oldDeviceId) {
        L.d("onDeviceId " + deviceId + ", old " + oldDeviceId);
        if (deviceId != null && oldDeviceId != null && deviceId.realm == Config.DID.REALM_DID && !deviceId.equals(oldDeviceId) && getSession() == null) {
            session(ctx, null).begin();
        }
    }

    @Override
    public synchronized void onActivityStarted(Ctx ctx) {
        if (activityCount == 0) {
            if (getSession() == null) {
                L.i("starting new session");
                session(ctx, null).begin();
            }
            if (ctx.getConfig().getSendUpdateEachSeconds() > 0 && executor == null) {
                executor = Executors.newScheduledThreadPool(1);
                executor.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        if (isActive() && getSession() != null) {
                            L.i("updating session");
                            getSession().update();
                        }
                    }
                }, ctx.getConfig().getSendUpdateEachSeconds(), ctx.getConfig().getSendUpdateEachSeconds(), TimeUnit.SECONDS);
            }
        }
        activityCount++;
    }

    @Override
    public synchronized void onActivityStopped(Ctx ctx) {
        activityCount--;
        if (activityCount == 0) {
            if (executor == null && ctx.getConfig().isAutoSessionsTrackingEnabled()) {
                executor = Executors.newScheduledThreadPool(1);
            }
            if (executor != null) {
                L.i("stopping session");
                try {
                    executor.schedule(new Runnable() {
                        @Override
                        public void run() {
                            L.i("ending session? activities " + activityCount + " session null? " + (getSession() == null) + " active? " + (getSession() != null && getSession().isActive()));
                            if (activityCount == 0 && getSession() != null && getSession().isActive()) {
                                getSession().end();
                            }
                        }
                    }, ctx.getConfig().getSessionAutoCloseAfter(), TimeUnit.SECONDS);

                    executor.shutdown();

                    if (!executor.awaitTermination(Math.min(31, ctx.getConfig().getSessionAutoCloseAfter() + 1), TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    L.w("Interrupted while waiting for session update executor to stop", e);
                    executor.shutdownNow();
                }
                executor = null;
            }
        }
    }

}
