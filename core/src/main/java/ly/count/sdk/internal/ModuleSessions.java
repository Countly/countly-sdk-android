package ly.count.sdk.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ly.count.sdk.ConfigCore;

public class ModuleSessions extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleSessions");

    private int activityCount = 0;
    private ScheduledExecutorService executor = null;

    /**
     * Current Session instance
     */
    private SessionImpl session = null;
    private TimedEvents timedEvents;

    public SessionImpl getSession() {
        return session;
    }

    public synchronized SessionImpl session(CtxCore ctx, Long id) {
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
     * @throws IllegalArgumentException when {@link ConfigCore#autoSessionsTracking}
     * is off since this module is for a case when it's on
     */
    @Override
    public void init(InternalConfig config) throws IllegalStateException {
        super.init(config);
    }

    @Override
    public void onContextAcquired(CtxCore ctx) {
        super.onContextAcquired(ctx);

        try {
            timedEvents = Storage.read(ctx, new TimedEvents());
            if (timedEvents == null) {
                timedEvents = new TimedEvents();
            }
        } catch (Throwable e) {
            L.wtf("Cannot happen", e);
            timedEvents = new TimedEvents();
        }
    }

    @Override
    public boolean isActive() {
        return super.isActive() || executor != null;
    }

    @Override
    public void stop(CtxCore ctx, boolean clear) {
        if (!clear) {
            Storage.pushAsync(ctx, timedEvents);
        }
        timedEvents = null;

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
     * @see ModuleDeviceId#onDeviceId(CtxCore, ConfigCore.DID, ConfigCore.DID)
     */
    public void onDeviceId(final CtxCore ctx, final ConfigCore.DID deviceId, final ConfigCore.DID oldDeviceId) {
        L.d("onDeviceId " + deviceId + ", old " + oldDeviceId);
        if (deviceId != null && oldDeviceId != null && deviceId.realm == ConfigCore.DID.REALM_DID && !deviceId.equals(oldDeviceId) && getSession() == null) {
            session(ctx, null).begin();
        }
    }

    @Override
    public synchronized void onActivityStarted(CtxCore ctx) {
        if (ctx.getConfig().isAutoSessionsTrackingEnabled() && activityCount == 0) {
            if (getSession() == null) {
                L.i("starting new session");
                session(ctx, null).begin();
            }
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
        activityCount++;
    }

    @Override
    public synchronized void onActivityStopped(CtxCore ctx) {
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

    public TimedEvents timedEvents() {
        return timedEvents;
    }
}
