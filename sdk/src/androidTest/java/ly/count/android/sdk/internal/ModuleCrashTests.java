package ly.count.android.sdk.internal;

import android.os.Handler;
import android.os.Looper;

import junit.framework.Assert;

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;

public class ModuleCrashTests {
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

//    @Test(expected = OutOfMemoryError.class)
//    public void raise_oom() {
//        ModuleCrash.crashTest(ModuleCrash.CrashType.OOM);
//    }

    @Test
    public void checkTicks() throws Exception {
        Config config = TestingUtilityInternal.setupConfig().enableTestMode().addFeature(Config.Feature.Crash);
        Core.initForApplication(config, getContext());

        ModuleCrash module = (ModuleCrash) Core.instance.module(Config.Feature.Crash);

        Assert.assertEquals(0, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickToCheck"));

        Core.instance.onContextAcquired(TestingUtilityInternal.mockApplication(getContext()));
        Thread.sleep(500);

        Assert.assertEquals(1, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickToCheck"));

        Thread.sleep(config.getCrashReportingANRTimeout() * 1000);

        Assert.assertEquals(2, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickToCheck"));

//        Assert.assertNotSame(Whitebox.getInternalState(module, "tick"), Whitebox.getInternalState(module, "tickToCheck"));
    }

//    @Test
//    public void checkANRs() throws Exception {
//        Context ctx = new ContextImpl(getContext());
//        Core.purgeInternalStorage(ctx, null);
//
//        final Config config = TestingUtilityInternal.setupConfig().enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG).addFeature(Config.Feature.Crash);
//        Core.initForApplication(config, getContext());
//        Core.instance.onContextAcquired(TestingUtilityInternal.mockApplication(getContext()));
//
//        ModuleCrash module = (ModuleCrash) Core.instance.module(Config.Feature.Crash);
//
//        Assert.assertEquals(1, Whitebox.getInternalState(module, "tick"));
//        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickToCheck"));
//
//        Thread.sleep(500);
//
//        Assert.assertEquals(1, Whitebox.getInternalState(module, "tick"));
//        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickToCheck"));
//        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
//        Assert.assertEquals(0, Storage.list(ctx, Request.getStoragePrefix()).size());
//
//        new Handler(getContext().getMainLooper()).post(new Runnable() {
//            @Override
//            public void run() {
//                long start = System.nanoTime(), tmp = 0;
//                while (System.nanoTime() - start < Device.secToNs(config.getCrashReportingANRTimeout())) {
//                    tmp++;
//                }
//            }
//        });
//
//        Thread.sleep(config.getCrashReportingANRTimeout() * 2000);
//
//        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
//        Assert.assertEquals(0, Storage.list(ctx, Request.getStoragePrefix()).size());
//
//        Assert.assertEquals(2, Whitebox.getInternalState(module, "tick"));
//        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickToCheck"));
//
////        Assert.assertNotSame(Whitebox.getInternalState(module, "tick"), Whitebox.getInternalState(module, "tickToCheck"));
//    }
}
