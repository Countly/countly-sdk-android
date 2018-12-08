package ly.count.sdk.android.internal;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ly.count.sdk.internal.Ctx;
import ly.count.sdk.internal.Device;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;

/**
 * Android-related crash reporting functionality: reporting more metrics & ANR detection
 */

public class ModuleCrash extends ly.count.sdk.internal.ModuleCrash {
    private static final Log.Module L = Log.module("ModuleCrash");

    private boolean limited = false;
    private boolean crashed = false;

    private volatile int tick = 0;
    private int tickToCheck = 0;
    private InternalConfig config;
    private Ctx context = null;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private Runnable ticker = new Runnable() {
        @Override
        public void run() {
            L.d("ticker " + tick + " => " + (tick + 1) + " / " + tickToCheck);
            tick++;
        }
    };

    private Runnable checker = new Runnable() {
        @Override
        public void run() {
            if (crashed) {
                return;
            }
            L.d("checker " + tick + " / " + tickToCheck);
            if (tick <= tickToCheck) {
                crashed = true;
                // TODO: report all stacktraces here
                onCrash(context, new IllegalStateException("ANR"), true, null, null, null);
                L.e("ANR detected. Waiting 3 x crashReportingANRTimeout and resuming watching for ANR.");
                executorService.schedule(new Runnable() {
                    @Override
                    public void run() {
                        nextTick();
                    }
                }, 3 * config.getCrashReportingANRTimeout(), TimeUnit.SECONDS);
            } else {
                nextTick();
            }
        }
    };

    @Override
    public void init(InternalConfig config) {
        this.config = config;
        limited = config.isLimited();
    }

    @Override
    public void stop(Ctx ctx, boolean clear) {
        try {
            super.stop(ctx, clear);

            executorService.shutdownNow();
            context = null;
            ticker = null;
            checker = null;
            executorService = null;
        } catch (Throwable t) {
            L.e("Exception while stopping crash reporting", t);
        }
    }

    @Override
    public void onContextAcquired(final Ctx ctx) {
        if (!limited) {
            if (config.getCrashReportingANRTimeout() > 0 && !Device.dev.isDebuggerConnected()) {
                context = ctx;
                nextTick();
            }
        }
    }

    public void nextTick() {
        if (!isActive()) {
            return;
        }
        L.d("next tick " + tick);
        tickToCheck = tick;
        SDK.instance.postToMainThread(ticker);
        executorService.schedule(checker, config.getCrashReportingANRTimeout(), TimeUnit.SECONDS);
    }

    public void onCrash(Ctx ctx, Throwable t, boolean fatal, String name, Map<String, String> segments, String... logs) {
        onCrash(ctx, new CrashImpl().setThrowable(t).setFatal(fatal).setName(name).setSegments(segments).setLogs(logs));
    }
}
