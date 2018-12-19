package ly.count.sdk.android.internal;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ly.count.sdk.android.Countly;
import ly.count.sdk.internal.Ctx;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;

/**
 * Android-related crash reporting functionality: reporting more metrics & ANR detection
 */

public class ModuleCrash extends ly.count.sdk.internal.ModuleCrash {
    private static final Log.Module L = Log.module("ModuleCrash");

    private boolean limited = false;
    private boolean crashed = false;

    private volatile int tickMain = 0;
    private int tickBg = 0;
    private Ctx context = null;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private Runnable ticker = new Runnable() {
        @Override
        public void run() {
            L.d("ticker " + tickMain + " => " + (tickMain + 1) + " / " + tickBg);
            tickMain++;
        }
    };

    private Runnable checker = new Runnable() {
        @Override
        public void run() {
            if (crashed) {
                return;
            }
            L.d("checker " + tickMain + " / " + tickBg);
            if (tickMain <= tickBg) {
                crashed = true;
                onANR(context);
                L.e("ANR detected. Waiting 3 x crashReportingANRCheckingPeriod and resuming watching for ANR.");
                executorService.schedule(new Runnable() {
                    @Override
                    public void run() {
                        nextTick();
                    }
                }, 3 * config.getCrashReportingANRCheckingPeriod(), TimeUnit.SECONDS);
            } else {
                nextTick();
            }
        }
    };

    @Override
    public void init(InternalConfig config) {
        super.init(config);
        limited = config.isLimited();
    }

    @Override
    public void stop(Ctx ctx, boolean clear) {
        try {
            super.stop(ctx, clear);
            executorService.shutdownNow();
        } catch (Throwable t) {
            L.e("Exception while stopping crash reporting", t);
        }
        context = null;
        ticker = null;
        checker = null;
        executorService = null;
    }

    @Override
    public void onContextAcquired(final Ctx ctx) {
        // uncomment the debugger disabling line below to debug ANRs
        if (!limited) {
            if (config.getCrashReportingANRCheckingPeriod() > 0) {
//            if (config.getCrashReportingANRCheckingPeriod() > 0 && !Device.dev.isDebuggerConnected()) {
                context = ctx;
                nextTick();
            }
        }
    }

    public void nextTick() {
        if (!isActive()) {
            return;
        }
        L.d("next tickMain " + tickMain);
        tickBg = tickMain;
        SDK.instance.postToMainThread(ticker);
        executorService.schedule(checker, config.getCrashReportingANRCheckingPeriod(), TimeUnit.SECONDS);
    }

    public CrashImpl onANR(Ctx ctx) {
        CrashImpl crash = new CrashImpl();
        crash.addTraces(SDK.instance.mainThread(), Thread.getAllStackTraces());

        if (Countly.legacyMethodCrashProcessor != null) {
            Countly.legacyMethodCrashProcessor.process(crash);
        }

        return (CrashImpl) onCrash(ctx, crash);
    }

    public CrashImpl onCrash(Ctx ctx, Throwable t, boolean fatal, String name, Map<String, String> segments, String... logs) {
        CrashImpl crash = new CrashImpl();
        crash.addThrowable(t).setFatal(fatal).setName(name).setSegments(segments).setLogs(logs);

        if (Countly.legacyMethodCrashProcessor != null) {
            Countly.legacyMethodCrashProcessor.process(crash);
        }

        return (CrashImpl) onCrash(ctx, crash);
    }
}
