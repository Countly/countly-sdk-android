package ly.count.sdk.android.internal;

import junit.framework.Assert;

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import ly.count.sdk.android.Config;
//import ly.count.sdk.internal.ModuleCrash;
import ly.count.sdk.android.internal.ModuleCrash;
import ly.count.sdk.internal.SDKCore;

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

//    @Test(expected = OutOfMemoryError.class)
//    public void raise_oom() {
//        ModuleCrash.crashTest(ModuleCrash.CrashType.OOM);
//    }

    @Test
    public void checkTicks() throws Exception {
        setUpApplication(null);

        ModuleCrash module = module(ModuleCrash.class, false);

        Assert.assertEquals(1, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickToCheck"));

        Thread.sleep(500);

        Assert.assertEquals(1, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(0, Whitebox.getInternalState(module, "tickToCheck"));

        Thread.sleep(config.getCrashReportingANRTimeout() * 1000);

        Assert.assertEquals(2, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickToCheck"));

        SDK.instance.stop(SDK.instance.ctx(getContext()), true);

        Thread.sleep(config.getCrashReportingANRTimeout() * 1000);

        Assert.assertEquals(2, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickToCheck"));

        Thread.sleep(config.getCrashReportingANRTimeout() * 1000);

        Assert.assertEquals(2, Whitebox.getInternalState(module, "tick"));
        Assert.assertEquals(1, Whitebox.getInternalState(module, "tickToCheck"));

//        Assert.assertNotSame(Whitebox.getInternalState(module, "tick"), Whitebox.getInternalState(module, "tickToCheck"));
    }

//    @Test
//    public void checkANRs() throws Exception {
//        Ctx ctx = new CtxImpl(getContext());
//        Core.purgeInternalStorage(ctx, null);
//
//        final Config config = TestingUtilityInternal.setupConfig().enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG).enableFeatures(Config.Feature.CrashReporting);
//        Core.initForApplication(config, getContext());
//        Core.instance.onContextAcquired(TestingUtilityInternal.mockApplication(getContext()));
//
//        ModuleCrash module = (ModuleCrash) Core.instance.module(Config.Feature.CrashReporting);
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
