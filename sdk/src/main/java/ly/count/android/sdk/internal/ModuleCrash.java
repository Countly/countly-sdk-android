package ly.count.android.sdk.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Crash reporting functionality
 */

public class ModuleCrash extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleCrash");

    private long started = 0;
    private boolean limited = false;
    private boolean crashed = false;

    private int anrTimeout = 0;
    private volatile int tick = 0;
    private int tickToCheck = 0;
    private Thread.UncaughtExceptionHandler previousHandler = null;
    private Context context = null;
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
                onCrash(context, new IllegalStateException("ANR"), true, null, null);
                L.e("ANR detected. Waiting 3 x crashReportingANRTimeout and resuming watching for ANR.");
                executorService.schedule(new Runnable() {
                    @Override
                    public void run() {
                        nextTick();
                    }
                }, 3 * anrTimeout, TimeUnit.SECONDS);
            } else {
                nextTick();
            }
        }
    };

    @Override
    public void init(InternalConfig config) {
        limited = config.isLimited();
        anrTimeout = config.getCrashReportingANRTimeout();
        // TODO: shutdown
    }

    @Override
    public void stop(Context ctx, boolean clear) {
        try {
            executorService.shutdownNow();
            if (previousHandler != null) {
                Thread.setDefaultUncaughtExceptionHandler(previousHandler);
            }
            if (clear) {
                Core.purgeInternalStorage(ctx, CrashImpl.getStoragePrefix());
            }
            context = null;
            ticker = null;
            checker = null;
            executorService = null;
        } catch (Throwable t) {
            L.e("Exception while stopping crash reporting", t);
        }
    }

    @Override
    public void onContextAcquired(final Context ctx) {
        if (!limited) {
            final Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable throwable) {
                    // needed since following UncaughtExceptionHandler can keep reference to this one
                    crashed = true;

                    if (isActive()) {
                        onCrash(ctx, throwable, true, null, null);
                    }

                    if (handler != null) {
                        handler.uncaughtException(thread, throwable);
                    }
                }
            });
            started = System.nanoTime();

            if (anrTimeout > 0 && !Device.isDebuggerConnected()) {
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
        Core.handler.post(ticker);
        executorService.schedule(checker, anrTimeout, TimeUnit.SECONDS);
    }

    public void onCrash(Context ctx, Throwable t, boolean fatal, String name, String details) {
        onCrash(ctx, new CrashImpl().setThrowable(t).setFatal(fatal).setName(name).setDetails(details));
    }

    public void onCrash(Context ctx, final CrashImpl crash) {
        long running = started == 0 ? 0 : Device.nsToMs(System.nanoTime() - started);
        crash.putMetrics(ctx, running);
        if (!Storage.push(ctx, crash)) {
            L.e("Couldn't persist a crash, so dumping it here: " + crash.getJSON());
        } else {
            Map<String, Object> params = new HashMap<>();
            params.put(CountlyService.PARAM_CRASH_ID, crash.storageId());
            Core.sendToService(ctx, CountlyService.CMD_CRASH, params);
        }
//
//        ModuleRequests.injectParams(ctx, new ModuleRequests.ParamsInjector() {
//            @Override
//            public void call(Params params) {
//                params.add("crash", crash.getJSON());
//            }
//        });
    }

    public static void putCrashIntoParams(CrashImpl crash, Params params) {
        params.add("crash", crash.getJSON());
    }

    private static void stackOverflow() {
        stackOverflow();
    }

    public enum CrashType {
        STACK_OVERFLOW, DIVISION_BY_ZERO, OOM, RUNTIME_EXCEPTION, NULLPOINTER_EXCEPTION
    }

    public static void crashTest(CrashType type) {
        switch (type) {
            case STACK_OVERFLOW:
                stackOverflow();
            case DIVISION_BY_ZERO:
                int test = 10/0;
            case OOM:
                Object[] o = null;
                while (true) { o = new Object[] { o }; }
            case RUNTIME_EXCEPTION:
                throw new RuntimeException("This is a crash");
            case NULLPOINTER_EXCEPTION:
                String nullString = null;
                nullString.charAt(1);
        }
    }

}
