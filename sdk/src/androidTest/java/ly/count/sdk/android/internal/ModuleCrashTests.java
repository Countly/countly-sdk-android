package ly.count.sdk.android.internal;

import android.os.Handler;

import junit.framework.Assert;

import org.json.JSONObject;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import ly.count.sdk.android.Config;
//import ly.count.sdk.internal.ModuleCrash;
import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.Storage;

import static android.support.test.InstrumentationRegistry.getContext;

public class ModuleCrashTests extends BaseTests {
    @Override
    protected ly.count.sdk.android.Config defaultConfig() throws Exception {
        return super.defaultConfig().enableFeatures(Config.Feature.CrashReporting);
    }

    @Test(expected = StackOverflowError.class)
    public void raise_stackOverflow() {
        ModuleCrash.crashTest(ModuleCrash.CrashType.STACK_OVERFLOW);
    }

    @Test(expected = ArithmeticException.class)
    public void raise_divisionByZero() {
        ModuleCrash.crashTest(ModuleCrash.CrashType.DIVISION_BY_ZERO);
    }

    @Test(expected = NullPointerException.class)
    public void raise_nullPointerException() {
        ModuleCrash.crashTest(ModuleCrash.CrashType.NULLPOINTER_EXCEPTION);
    }

    @Test(expected = RuntimeException.class)
    public void raise_runtimeException() {
        ModuleCrash.crashTest(ModuleCrash.CrashType.RUNTIME_EXCEPTION);
    }

    @Test(expected = OutOfMemoryError.class)
    public void raise_oom() {
        ModuleCrash.crashTest(ModuleCrash.CrashType.OOM);
    }

    @Test
    public void checkTicks() throws Exception {
        setUpApplication(null);

        ModuleCrash module = module(ModuleCrash.class, false);

        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickMain"));
        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickBg"));

        Thread.sleep(500);

        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickMain"));
        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickBg"));

        Thread.sleep(config.getCrashReportingANRCheckingPeriod() * 1000);

        Assert.assertEquals(2, Whitebox.getInternalState(module, "tickMain"));
        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickBg"));

        SDK.instance.stop(SDK.instance.ctx(getContext()), true);

        Thread.sleep(config.getCrashReportingANRCheckingPeriod() * 1000);

        Assert.assertEquals(2, Whitebox.getInternalState(module, "tickMain"));
        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickBg"));

        Thread.sleep(config.getCrashReportingANRCheckingPeriod() * 1000);

        Assert.assertEquals(2, Whitebox.getInternalState(module, "tickMain"));
        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickBg"));

        Assert.assertNotSame(Whitebox.getInternalState(module, "tickMain"), Whitebox.getInternalState(module, "tickBg"));
    }

    @Test
    public void checkANRs() throws Exception {
        setUpApplication(null);
        ModuleCrash module = module(ModuleCrash.class, false);

        Thread.sleep(500);

        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickMain"));
        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickBg"));
        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
        Assert.assertEquals(0, Storage.list(ctx, Request.getStoragePrefix()).size());

        new Handler(getContext().getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                long start = System.nanoTime(), tmp = 0;
                while (System.nanoTime() - start < Device.dev.secToNs(config.getCrashReportingANRCheckingPeriod())) {
                    tmp++;
                }
            }
        });

        Thread.sleep(config.getCrashReportingANRCheckingPeriod() * 2000);

        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
        Assert.assertEquals(0, Storage.list(ctx, Request.getStoragePrefix()).size());

        int tickMain = Whitebox.getInternalState(module, "tickMain");
        int tickBg = Whitebox.getInternalState(module, "tickBg");
        if (tickMain == 3) {
            Assert.assertEquals(tickBg, 2);
        } else if (tickMain == 2) {
            Assert.assertEquals(tickBg, 1);
        } else {
            Assert.fail("invalid tickMain");
        }

        Assert.assertNotSame(Whitebox.getInternalState(module, "tickMain"), Whitebox.getInternalState(module, "tickBg"));

        new Handler(getContext().getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                long start = System.nanoTime(), tmp = 0;
                while (System.nanoTime() - start < 2 * Device.dev.secToNs(config.getCrashReportingANRCheckingPeriod())) {
                    tmp++;
                }
            }
        });

        Thread.sleep(config.getCrashReportingANRCheckingPeriod() * 2500);

        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
        Assert.assertEquals(1, Storage.list(ctx, Request.getStoragePrefix()).size());

        Request request = Storage.readOne(ctx, ModuleRequests.nonSessionRequest(ctx), false);
        Assert.assertNotNull(request);

        Assert.assertTrue(request.params.has("crash"));

        JSONObject crash = new JSONObject(request.params.get("crash"));

        Assert.assertTrue(crash.has("_error"));
        String error = crash.getString("_error");
        System.out.println(error);
        Assert.assertTrue(error.contains("Thread [main]"));
        Assert.assertTrue(error.contains("Thread ["));
    }

// ▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▃▄▅▆▇█▓▒░ K K ░▒▓█▇▆▅▄▃▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂
//                                   █▀▀█ █░░ █▀▀ █▀▀█ ▀▀█▀▀
//                                   █▄▄█ █░░ █▀▀ █▄▄▀ ░░█░░
//                                   ▀░░▀ ▀▀▀ ▀▀▀ ▀░▀▀ ░░▀░░
//
//                                            (◕‿◕)
//
//                                These tests are not for Travis
//                                    They run for minutes
//                               I commented them out on purpose!
// ▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂
//
//    @Test
//    public void checkNoANRsInCPUHeavyCase() throws Exception {
//        setUpApplication(null);
//        ModuleCrash module = module(ModuleCrash.class, false);
//
//        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickMain"));
//        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickBg"));
//
//        Thread.sleep(500);
//
//        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickMain"));
//        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickBg"));
//        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
//        Assert.assertEquals(0, Storage.list(ctx, Request.getStoragePrefix()).size());
//
//        for (int i = 0; i < 50; i++) {
//            new Handler(getContext().getMainLooper()).post(new Runnable() {
//                @Override
//                public void run() {
//                    long start = System.nanoTime(), tmp = 0;
//                    while (System.nanoTime() - start < Device.dev.secToNs(config.getCrashReportingANRCheckingPeriod())) {
//                        tmp++;
//                    }
//                }
//            });
//
//            Thread.sleep(config.getCrashReportingANRCheckingPeriod() * 1000);
//
//            Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
//            Assert.assertEquals(0, Storage.list(ctx, Request.getStoragePrefix()).size());
//        }
//    }
//
//    @Test
//    public void checkSomeANRsInCPUHeavierCase() throws Exception {
//        setUpApplication(null);
//        ModuleCrash module = module(ModuleCrash.class, false);
//
//        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickMain"));
//        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickBg"));
//
//        Thread.sleep(500);
//
//        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickMain"));
//        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickBg"));
//        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
//        Assert.assertEquals(0, Storage.list(ctx, Request.getStoragePrefix()).size());
//
//        for (int i = 0; i < 50; i++) {
//            new Handler(getContext().getMainLooper()).post(new Runnable() {
//                @Override
//                public void run() {
//                    long start = System.nanoTime(), tmp = 0;
//                    while (System.nanoTime() - start < 1.1f * Device.dev.secToNs(config.getCrashReportingANRCheckingPeriod())) {
//                        tmp++;
//                    }
//                }
//            });
//
//            Thread.sleep(config.getCrashReportingANRCheckingPeriod() * 1000);
//        }
//
//        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
//        Assert.assertTrue(Storage.list(ctx, Request.getStoragePrefix()).size() > 0);
//    }
//
// ▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂
}
