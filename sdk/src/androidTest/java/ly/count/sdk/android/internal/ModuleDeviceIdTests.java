package ly.count.sdk.android.internal;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.List;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.android.Config;
import ly.count.sdk.android.Countly;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.ModuleDeviceIdCore;
import ly.count.sdk.internal.ModuleSessions;
import ly.count.sdk.internal.Params;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.SessionImpl;
import ly.count.sdk.internal.Storage;
import ly.count.sdk.internal.Tasks;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class ModuleDeviceIdTests extends BaseTestsAndroid {
    private ModuleDeviceId moduleDeviceId = null;

    @Before
    public void setUp() throws Exception {
        //log needed for some tests to throw exceptions
        defaultConfigWithLogsForConfigTests();
        super.setUp();
    }

    @Test(expected = IllegalStateException.class)
    public void checkStrictAdvertisingId() throws Exception {
        doReturn(Boolean.FALSE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);

        ConfigCore cfg = defaultConfig();
        cfg.setDeviceIdFallbackAllowed(false);

        Whitebox.setInternalState(cfg, "deviceIdStrategy", Config.DeviceIdStrategy.ADVERTISING_ID.getIndex());
        InternalConfig config = new InternalConfig(cfg);
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
    }

    @Test(expected = IllegalStateException.class)
    public void checkStrictInstanceId() throws Exception {
        doReturn(Boolean.FALSE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.INSTANCE_ID_CLASS_NAME);

        InternalConfig config = new InternalConfig(defaultConfig());
        config.setDeviceIdFallbackAllowed(false);
        Whitebox.setInternalState(config, "deviceIdStrategy", Config.DeviceIdStrategy.INSTANCE_ID.getIndex());
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
    }

    @Test
    public void checkNotStrictAdvertisingId() throws Exception {
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);

        InternalConfig config = new InternalConfig(defaultConfig());
        Whitebox.setInternalState(config, "deviceIdStrategy", Config.DeviceIdStrategy.ADVERTISING_ID.getIndex());
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
        Assert.assertTrue(true);
    }

    @Test
    public void checkNotStrictInstanceId() throws Exception {
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.INSTANCE_ID_CLASS_NAME);

        InternalConfig config = new InternalConfig(defaultConfig());
        Whitebox.setInternalState(config, "deviceIdStrategy", Config.DeviceIdStrategy.INSTANCE_ID.getIndex());
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
        Assert.assertTrue(true);
    }

    @Test
    public void checkLimitedContext() throws Exception {
        setUpService(null);

        InternalConfig iConfig = new InternalConfig(defaultConfig());
        iConfig.setLimited(true);
        config = Storage.read(ctx, iConfig);
        Assert.assertNull(config);
    }

    @Test
    public void checkCustomId_fresh() throws Exception {
        String deviceId = "123dev";
        setUpApplication(defaultConfig().setCustomDeviceId(deviceId));

        ConfigCore.DID did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID.getIndex());
        Assert.assertEquals(deviceId, did.id);

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID.getIndex());
        Assert.assertEquals(deviceId, did.id);
    }

    @Test//todo, this is failing
    public void checkCustomId_legacy() throws Exception {
        String deviceId = "123dev";
        String legacyId = "123legacy";

        getContext().getSharedPreferences(Legacy.PREFERENCES, android.content.Context.MODE_PRIVATE).edit().putString(Legacy.KEY_ID_ID, legacyId).commit();

        setUpApplication(defaultConfig().setCustomDeviceId(deviceId));

        ConfigCore.DID did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID.getIndex());
        Assert.assertEquals(legacyId, did.id);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), eq(did));

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID.getIndex());
        Assert.assertEquals(legacyId, did.id);
    }

    @Test
    public void checkAdvId_fresh() throws Exception {
        BaseTestsAndroid.AdvIdInfo.deviceId = "123adv";
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);
        doReturn(new AdvIdInfo()).when(utils)._reflectiveCallStrict(eq(ly.count.sdk.android.internal.ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), eq(android.content.Context.class), isA(android.content.Context.class));

        setUpApplication(defaultConfig().setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID));
        moduleDeviceId = module(ModuleDeviceId.class, false);

        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        Whitebox.invokeMethod(tasks, "await");

        ConfigCore.DID did = config.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(BaseTestsAndroid.AdvIdInfo.deviceId, did.id);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(ConfigCore.DID.class));

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertEquals(BaseTestsAndroid.AdvIdInfo.deviceId, did.id);
    }

    @Test
    public void checkAdvId_legacy() throws Exception {
        BaseTestsAndroid.AdvIdInfo.deviceId = "123adv";
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);
        doReturn(new AdvIdInfo()).when(utils)._reflectiveCall(eq(ly.count.sdk.android.internal.ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), isA(android.content.Context.class));

        String legacyId = "123legacy";
        getContext().getSharedPreferences(Legacy.PREFERENCES, android.content.Context.MODE_PRIVATE).edit().putString(Legacy.KEY_ID_ID, legacyId).commit();

        setUpApplication(defaultConfig().setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID));
        moduleDeviceId = module(ModuleDeviceId.class, false);

        Assert.assertNull(Utils.reflectiveGetField(moduleDeviceId, "tasks"));

        ConfigCore.DID did = config.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), eq(did));

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
    }

    @Test
    public void checkAdvId_Fallback() throws Exception {
        BaseTestsAndroid.AdvIdInfo.deviceId = "123adv";
        doReturn(Boolean.FALSE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);

        setUpApplication(defaultConfig().setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID));
        moduleDeviceId = module(ModuleDeviceId.class, false);

        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        Whitebox.invokeMethod(tasks, "await");

        ConfigCore.DID did = config.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertFalse(did.id.equals(BaseTestsAndroid.AdvIdInfo.deviceId));
        Assert.assertEquals(did.strategy, Config.DeviceIdStrategy.ANDROID_ID.getIndex());
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(ConfigCore.DID.class));

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertEquals(did.strategy, Config.DeviceIdStrategy.ANDROID_ID.getIndex());
    }

    @Test
    public void checkInstId_fresh() throws Exception {
        BaseTestsAndroid.InstIdInstance.deviceId = "123inst";
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.INSTANCE_ID_CLASS_NAME);
        doReturn(new InstIdInstance()).when(utils)._reflectiveCall(eq(ly.count.sdk.android.internal.ModuleDeviceId.INSTANCE_ID_CLASS_NAME), ArgumentMatchers.isNull(), eq("getInstance"));

        setUpApplication(defaultConfig().setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID));
        moduleDeviceId = module(ModuleDeviceId.class, false);

        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        Whitebox.invokeMethod(tasks, "await");

        ConfigCore.DID did = config.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(BaseTestsAndroid.InstIdInstance.deviceId, did.id);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(ConfigCore.DID.class));

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertEquals(BaseTestsAndroid.InstIdInstance.deviceId, did.id);
    }

    @Test
    public void checkInstId_legacy() throws Exception {
        BaseTestsAndroid.InstIdInstance.deviceId = "123inst";
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.INSTANCE_ID_CLASS_NAME);
        doReturn(new InstIdInstance()).when(utils)._reflectiveCall(eq(ly.count.sdk.android.internal.ModuleDeviceId.INSTANCE_ID_CLASS_NAME), ArgumentMatchers.isNull(), eq("getInstance"), isA(android.content.Context.class));

        String legacyId = "123legacy";
        getContext().getSharedPreferences(Legacy.PREFERENCES, android.content.Context.MODE_PRIVATE).edit().putString(Legacy.KEY_ID_ID, legacyId).commit();

        setUpApplication(defaultConfig().setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID));
        moduleDeviceId = module(ModuleDeviceId.class, false);

        Assert.assertNull(Utils.reflectiveGetField(moduleDeviceId, "tasks"));

        ConfigCore.DID did = config.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), eq(did));

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
    }

    @Test
    public void checkInstId_Fallback() throws Exception {
        BaseTestsAndroid.InstIdInstance.deviceId = "123inst";
        doReturn(Boolean.FALSE).when(utils)._reflectiveClassExists(ly.count.sdk.android.internal.ModuleDeviceId.INSTANCE_ID_CLASS_NAME);

        setUpApplication(defaultConfig().setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID));
        moduleDeviceId = module(ModuleDeviceId.class, false);

        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        Whitebox.invokeMethod(tasks, "await");

        ConfigCore.DID did = config.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertFalse(did.id.equals(BaseTestsAndroid.AdvIdInfo.deviceId));
        Assert.assertEquals(did.strategy, Config.DeviceIdStrategy.ANDROID_ID.getIndex());
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(ConfigCore.DID.class));

        config = Storage.read(ctx, config);
        Assert.assertNotNull(config);
        did = config.getDeviceId(Config.DeviceIdRealm.DEVICE_ID.getIndex());
        Assert.assertNotNull(did);
        Assert.assertEquals(did.strategy, Config.DeviceIdStrategy.ANDROID_ID.getIndex());
    }

    @Test
    public void checkLogin() throws Exception {
        Utils.reflectiveSetField(ModuleDeviceIdCore.class, "testSleep", 2000L);

        setUpApplication(defaultConfig().setSendUpdateEachSeconds(5));
        moduleDeviceId = module(ly.count.sdk.android.internal.ModuleDeviceId.class, false);

        ModuleSessions moduleSessions = sdk.module(ModuleSessions.class);

        Assert.assertNull(Utils.reflectiveGetField(moduleSessions, "session"));

        //  make ModuleAutoSessions begin a session
        moduleSessions.onActivityStarted(ctx);
        Assert.assertNotNull(Utils.reflectiveGetField(moduleSessions, "session"));

        // ensure session is written
        List<Long> ids = Storage.list(ctx, SessionImpl.getStoragePrefix());
        Assert.assertEquals(1, ids.size());
        SessionImpl session = Storage.read(ctx, new SessionImpl(ctx, ids.get(0)));
        Assert.assertNotNull(session);

        // there must be only single begin request
        ids = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(1, ids.size());
        Request request = Storage.read(ctx, new Request(ids.get(0)));
        Assert.assertNotNull(request);
        Assert.assertTrue(request.params.toString().contains("begin_session=1"));
        // .. and it shouldn't have an id yet - its generation takes 2 seconds
        Assert.assertFalse(request.params.toString().contains((String)Whitebox.getInternalState(Params.class, "PARAM_DEVICE_ID")));

        // now sleep update time for ModuleAutoSessions to create update request
        Thread.sleep(1000 * config.getSendUpdateEachSeconds());
        // ... and ensure session was updated and request was created
        ids = Storage.list(ctx, SessionImpl.getStoragePrefix());
        Assert.assertEquals(1, ids.size());
        session = Storage.read(ctx, new SessionImpl(ctx, ids.get(0)));
        Assert.assertNotNull(session);
        Assert.assertNotNull(Whitebox.getInternalState(session, "updated"));

        // in the meantime, since it takes 2 seconds for ModuleDeviceIdCore to get an id while we slept 5 seconds,
        // there must be an id already
        Assert.assertNotNull(config.getDeviceId());
        String idString = config.getDeviceId().id;

        // now we should have begin & update requests with ids filled
        ids = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(2, ids.size());
        request = Storage.read(ctx, request);
        Assert.assertNotNull(request);
        Assert.assertTrue(request.params.toString().contains("device_id=" + idString));
        request = Storage.read(ctx, new Request(ids.get(1)));
        Assert.assertNotNull(request);
        Assert.assertTrue(request.params.toString().contains("device_id=" + idString));

        // ----------------- now changing device id --------------------
        Countly.login(ctx.getContext(), "did");

        // let's wait for tasks to finish pushing requests and such
        Tasks deviceIdTasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        Whitebox.invokeMethod(deviceIdTasks, "await");

        // there must be: begin, update, end from first session + id change request + begin from second session
        ids = Storage.list(ctx, Request.getStoragePrefix());
        Log.w("going to read requests: " + Utils.join(ids, ", "));
        Assert.assertEquals(5, ids.size());

        // first begin
        request = Storage.read(ctx, new Request(ids.get(0)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("device_id=" + idString));
        Assert.assertTrue(request.params.toString().contains("begin_session=1"));

        // first update
        request = Storage.read(ctx, new Request(ids.get(1)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("session_duration="));
        Assert.assertTrue(request.params.toString().contains("device_id=" + idString));

        // first end
        request = Storage.read(ctx, new Request(ids.get(2)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("end_session=1"));
        Assert.assertTrue(request.params.toString().contains("device_id=" + idString));
        Assert.assertFalse(request.params.toString().contains("old_device_id="));

        // id change
        request = Storage.read(ctx, new Request(ids.get(3)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("device_id=did"));
        Assert.assertTrue(request.params.toString().contains("old_device_id=" + idString));

        // second begin
        request = Storage.read(ctx, new Request(ids.get(4)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("device_id=did"));
        Assert.assertTrue(request.params.toString().contains("begin_session=1"));
    }

    @Test
    public void checkLogout() throws Exception {
        // ----------------- fast-forward to end phase of checkLogin() --------------------
        Utils.reflectiveSetField(ModuleDeviceIdCore.class, "testSleep", 2000L);

        setUpApplication(defaultConfig().setSendUpdateEachSeconds(5));
        moduleDeviceId = sdk.module(ly.count.sdk.android.internal.ModuleDeviceId.class);

        ModuleSessions moduleSessions = sdk.module(ModuleSessions.class);

        moduleSessions.onActivityStarted(ctx);
        Thread.sleep(1000 * config.getSendUpdateEachSeconds());

        String idString = config.getDeviceId().id;

        Countly.login(ctx.getContext(), "did");

        Tasks deviceIdTasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        Whitebox.invokeMethod(deviceIdTasks, "await");

        // there must be: begin, update, end from first session + id change request + begin from second session
        List<Long> ids = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(5, ids.size());

        // ----------------- now logging out --------------------
        Countly.logout(ctx.getContext());
        Whitebox.invokeMethod(deviceIdTasks, "await");

        ids = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(6, ids.size());

        // second begin
        Request request = Storage.read(ctx, new Request(ids.get(4)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("device_id=did"));
        Assert.assertTrue(request.params.toString().contains("begin_session=1"));

        // second end
        request = Storage.read(ctx, new Request(ids.get(5)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("device_id=did"));
        Assert.assertTrue(request.params.toString().contains("end_session=1"));

        // Activity stop doesn't do anything
        Assert.assertNull(Utils.reflectiveGetField(moduleSessions, "session"));
        moduleSessions.onActivityStopped(ctx);
        Thread.sleep(1000);
        Assert.assertNull(Utils.reflectiveGetField(moduleSessions, "session"));
        ids = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(6, ids.size());

        // Starting another activity begins session
        moduleSessions.onActivityStarted(ctx);
        Assert.assertNotNull(Utils.reflectiveGetField(moduleSessions, "session"));

        // new session begin with a id which might be the same as the old id
        ids = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(7, ids.size());
        request = Storage.read(ctx, new Request(ids.get(6)));
        Assert.assertNotNull(request);
        Log.w("read request " + request.params);
        Assert.assertTrue(request.params.toString().contains("device_id="));
        Assert.assertTrue(request.params.toString().contains("begin_session=1"));
    }
}
