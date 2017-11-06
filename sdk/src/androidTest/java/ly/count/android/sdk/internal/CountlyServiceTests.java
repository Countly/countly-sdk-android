package ly.count.android.sdk.internal;

import android.content.Intent;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.List;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class CountlyServiceTests extends BaseTests {
//    @Rule
//    public ServiceTestRule rule = ServiceTestRule.withTimeout(3, TimeUnit.SECONDS);

    @Test
    public void constructor_basic() throws Exception {
        setUpService(defaultConfig());
        service.onCreate();
        Assert.assertNotNull(Whitebox.getInternalState(service, "tasks"));
        Assert.assertNotNull(Whitebox.getInternalState(service, "ctx"));
        Assert.assertNotNull(Whitebox.getInternalState(service, "crashes"));
        Assert.assertNotNull(Whitebox.getInternalState(service, "sessions"));
        Assert.assertNotNull(Whitebox.getInternalState(service, "config"));
        Assert.assertNotNull(Whitebox.getInternalState(service, "core"));
        Assert.assertNull(Whitebox.getInternalState(service, "network"));
    }

    @Test
    public void constructor_noConfig() throws Exception {
        setUpService(null);
        service.onCreate();
        Assert.assertNull(Whitebox.getInternalState(service, "core"));
    }

    @Test
    public void recovery_sessionOld() throws Exception {
        setUpApplication(null);
        SessionImpl session = new SessionImpl(ctx, Device.uniqueTimestamp() - Device.secToMs(2020));
        session.begin(System.nanoTime() - Device.secToNs(2020));
        session.update(System.nanoTime() - Device.secToNs(2000));

        session = new SessionImpl(ctx);
        session.begin(System.nanoTime() - Device.secToNs(10));

        Storage.await();

        Assert.assertEquals(2, Storage.list(ctx, SessionImpl.getStoragePrefix()).size());
        Assert.assertEquals(3, Storage.list(ctx, Request.getStoragePrefix()).size());

        Core.deinit();
        setUpService(null);
        service.onCreate();

        doReturn(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
            @Override
            public Boolean call() throws Exception {
                Log.d("+++++++");
                Thread.sleep(1000);
                return null;
            }
        }).when(service).submit();

        Intent intent = new Intent();
        intent.putExtra(CountlyService.CMD, CountlyService.CMD_START);
        service.onStartCommand(intent, 0, 0);

        Tasks tasks = Utils.reflectiveGetField(service, "tasks");
        tasks.await();

        Assert.assertEquals(1, Storage.list(ctx, SessionImpl.getStoragePrefix()).size());
    }

    @Test
    public void recovery_sessionBad() throws Exception {
        setUpApplication(null);
        SessionImpl notBegan = Core.instance.session(ctx, Device.uniqueTimestamp() - Device.secToMs(2020));
        Storage.push(ctx, notBegan);
        Utils.reflectiveSetField(core, "session", null);

        SessionImpl ended = Core.instance.session(ctx, Device.uniqueTimestamp() - Device.secToMs(2010));
        ended.begin(System.nanoTime() - Device.secToNs(2010));
        ended.end();
        Storage.await();

        List<Long> sessions = Storage.list(ctx, SessionImpl.getStoragePrefix());
        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals(notBegan.id, sessions.get(0));
        Assert.assertEquals(2, Storage.list(ctx, Request.getStoragePrefix()).size());

        Core.deinit();
        setUpService(null);
        service.onCreate();

        doReturn(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
            @Override
            public Boolean call() throws Exception {
                Log.d("+++++++");
                Thread.sleep(1000);
                return null;
            }
        }).when(service).submit();

        Intent intent = new Intent();
        intent.putExtra(CountlyService.CMD, CountlyService.CMD_START);
        service.onStartCommand(intent, 0, 0);

        Tasks tasks = Utils.reflectiveGetField(service, "tasks");
        tasks.await();

        Assert.assertEquals(0, Storage.list(ctx, SessionImpl.getStoragePrefix()).size());
        Assert.assertEquals(2, Storage.list(ctx, Request.getStoragePrefix()).size());
    }

    @Test
    public void receiving_crash() throws Exception {
        setUpApplication(defaultConfig().addFeature(Config.Feature.Crash));
        SessionImpl session = new SessionImpl(ctx);
        session.begin();

        Storage.await();

        List<Long> sessions = Storage.list(ctx, SessionImpl.getStoragePrefix());
        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals(1, Storage.list(ctx, Request.getStoragePrefix()).size());

        Core.deinit();
        setUpService(null);
        service.onCreate();

        doReturn(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
            @Override
            public Boolean call() throws Exception {
                Log.d("+++++++");
                Thread.sleep(1000);
                return null;
            }
        }).when(service).submit();

        Intent intent = new Intent();
        intent.putExtra(CountlyService.CMD, CountlyService.CMD_START);
        service.onStartCommand(intent, 0, 0);

        session.addCrashReport(new IllegalStateException("Some bad state"), false);

        Storage.await();

        CrashImpl crash = Storage.read(ctx, new CrashImpl(Storage.list(ctx, CrashImpl.getStoragePrefix()).get(0)));
        Assert.assertNotNull(crash);

        intent.putExtra(CountlyService.CMD, CountlyService.CMD_CRASH);
        intent.putExtra(CountlyService.PARAM_CRASH_ID, crash.storageId());

        service.onStartCommand(intent, 0, 0);

        Tasks tasks = Utils.reflectiveGetField(service, "tasks");
        tasks.await();

        List<Long> requests = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(1, Storage.list(ctx, SessionImpl.getStoragePrefix()).size());
        Assert.assertEquals(0, Storage.list(ctx, CrashImpl.getStoragePrefix()).size());
        Assert.assertEquals(2, requests.size());

        Request crashRequest = Storage.read(ctx, new Request(requests.get(1)));
        Assert.assertNotNull(crashRequest);
        Assert.assertTrue(crashRequest.params.toString().contains("crash=" + Utils.urlencode("{")));
    }
}
