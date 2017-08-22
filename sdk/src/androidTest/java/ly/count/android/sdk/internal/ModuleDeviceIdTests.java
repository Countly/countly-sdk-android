package ly.count.android.sdk.internal;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.List;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ModuleDeviceIdTests {
    private Config config = null;
    private InternalConfig internalConfig = null;
    private Core core = null;
    private ModuleDeviceId module = null;
    private Module dummy = null;
    private Utils utils = null;

    @Test(expected = IllegalArgumentException.class)
    public void checkStrictAdvertisingId() throws Exception {
        InternalConfig config = TestingUtilityInternal.setupLogs(null);
        config.setDeviceIdFallbackAllowed(false);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID);
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkStrictInstanceId() throws Exception {
        InternalConfig config = TestingUtilityInternal.setupLogs(null);
        config.setDeviceIdFallbackAllowed(false);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
    }

    @Test
    public void checkNotStrictAdvertisingId() throws Exception {
        InternalConfig config = TestingUtilityInternal.setupLogs(null);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID);
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
        Assert.assertTrue(true);
    }

    @Test
    public void checkNotStrictInstanceId() throws Exception {
        InternalConfig config = TestingUtilityInternal.setupLogs(null);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);
        ModuleDeviceId module = new ModuleDeviceId();
        module.init(config);
        Assert.assertTrue(true);
    }

    @Test
    public void checkCustomId_fresh() throws Exception {
        String deviceId = "123dev";
        config = TestingUtilityInternal.setupConfig();
        config.setCustomDeviceId(deviceId);
        init(false);

        core.onContextCreated(getContext());

        Config.DID did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID);
        Assert.assertEquals(deviceId, did.id);
        Mockito.verify(dummy).onDeviceId(did, null);

        internalConfig = Storage.read(internalConfig);
        Assert.assertNotNull(internalConfig);
        did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID);
        Assert.assertEquals(deviceId, did.id);
    }

    @Test
    public void checkCustomId_legacy() throws Exception {
        String deviceId = "123dev";
        String legacyId = "123legacy";
        config = TestingUtilityInternal.setupConfig();
        config.setCustomDeviceId(deviceId);
        init(false);

        getContext().getSharedPreferences(Legacy.PREFERENCES, android.content.Context.MODE_PRIVATE).edit().putString(Legacy.KEY_ID_ID, legacyId).commit();

        core.onContextCreated(getContext());

        Config.DID did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID);
        Assert.assertEquals(legacyId, did.id);
        Mockito.verify(dummy).onDeviceId(did, did);

        internalConfig = Storage.read(internalConfig);
        Assert.assertNotNull(internalConfig);
        did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertTrue(did.strategy == Config.DeviceIdStrategy.CUSTOM_ID);
        Assert.assertEquals(legacyId, did.id);
    }

    @Test
    public void checkAdvId_fresh() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.AdvIdInfo.deviceId = "123adv";
        config = TestingUtilityInternal.setupConfig();
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(true);

        when(utils._reflectiveClassExists(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME)).thenReturn(Boolean.TRUE);
        when(utils._reflectiveCall(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME, null, "getAdvertisingIdInfo", context)).thenReturn(new ModuleDeviceId.AdvIdInfo());

        core.onContextCreated(context);
        Tasks tasks = Utils.reflectiveGetField(module, "tasks");
        tasks.awaitTermination();

        Config.DID did = internalConfig.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(ModuleDeviceId.AdvIdInfo.deviceId, did.id);
        Mockito.verify(dummy).onDeviceId(did, null);

        internalConfig = Storage.read(internalConfig);
        Assert.assertNotNull(internalConfig);
        did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertEquals(ModuleDeviceId.AdvIdInfo.deviceId, did.id);
    }

    @Test
    public void checkAdvId_legacy() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.AdvIdInfo.deviceId = "123adv";
        String legacyId = "123legacy";
        config = TestingUtilityInternal.setupConfig();
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(true);

        getContext().getSharedPreferences(Legacy.PREFERENCES, android.content.Context.MODE_PRIVATE).edit().putString(Legacy.KEY_ID_ID, legacyId).commit();

        when(utils._reflectiveClassExists(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME)).thenReturn(Boolean.TRUE);
        when(utils._reflectiveCall(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME, null, "getAdvertisingIdInfo", context)).thenReturn(new ModuleDeviceId.AdvIdInfo());

        core.onContextCreated(context);

        Assert.assertNull(Utils.reflectiveGetField(module, "tasks"));

        Config.DID did = internalConfig.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
        Mockito.verify(dummy).onDeviceId(did, did);

        internalConfig = Storage.read(internalConfig);
        Assert.assertNotNull(internalConfig);
        did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
    }


    @Test
    public void checkInstId_fresh() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.InstIdInstance.deviceId = "123adv";
        config = TestingUtilityInternal.setupConfig();
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(true);

        when(utils._reflectiveClassExists(ModuleDeviceId.INSTANCE_ID_CLASS_NAME)).thenReturn(Boolean.TRUE);
        when(utils._reflectiveCall(ModuleDeviceId.INSTANCE_ID_CLASS_NAME, null, "getInstance", context)).thenReturn(new ModuleDeviceId.InstIdInstance());

        core.onContextCreated(context);
        Tasks tasks = Utils.reflectiveGetField(module, "tasks");
        tasks.awaitTermination();

        Config.DID did = internalConfig.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(ModuleDeviceId.InstIdInstance.deviceId, did.id);
        Mockito.verify(dummy).onDeviceId(did, null);

        internalConfig = Storage.read(internalConfig);
        Assert.assertNotNull(internalConfig);
        did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertEquals(ModuleDeviceId.InstIdInstance.deviceId, did.id);
    }

    @Test
    public void checkInstId_legacy() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.InstIdInstance.deviceId = "123adv";
        String legacyId = "123legacy";
        config = TestingUtilityInternal.setupConfig();
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(true);

        getContext().getSharedPreferences(Legacy.PREFERENCES, android.content.Context.MODE_PRIVATE).edit().putString(Legacy.KEY_ID_ID, legacyId).commit();

        when(utils._reflectiveClassExists(ModuleDeviceId.INSTANCE_ID_CLASS_NAME)).thenReturn(Boolean.TRUE);
        when(utils._reflectiveCall(ModuleDeviceId.INSTANCE_ID_CLASS_NAME, null, "getInstance", context)).thenReturn(new ModuleDeviceId.InstIdInstance());

        core.onContextCreated(context);

        Assert.assertNull(Utils.reflectiveGetField(module, "tasks"));

        Config.DID did = internalConfig.getDeviceId();
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
        Mockito.verify(dummy).onDeviceId(did, did);

        internalConfig = Storage.read(internalConfig);
        Assert.assertNotNull(internalConfig);
        did = internalConfig.getDeviceId(Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertNotNull(did);
        Assert.assertEquals(legacyId, did.id);
    }

    private void init(boolean mockModule) throws MalformedURLException {
        if (config == null) {
            config = TestingUtilityInternal.setupConfig();
            config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        }
        config.enableTestMode();
        if (core == null) {
            core = new Core();
            core.init(config, getContext());
        }
        dummy = Mockito.mock(Module.class);
        if (module == null) {
            List<Module> list = Whitebox.getInternalState(core, "modules");
            for (Module m : list) {
                if (m instanceof ModuleDeviceId) {
                    module = (ModuleDeviceId) m;
                    break;
                }
            }
            if (mockModule) {
                list.remove(module);
                module = Mockito.spy(module);
                list.add(1, module);
            }
            list.add(dummy);
        } else {
            List<Module> list = Whitebox.getInternalState(core, "modules");
            list.add(dummy);
        }
        internalConfig = Utils.reflectiveGetField(core, "config");
//        internalConfig.setLoggerClass(Log.SystemLogger.class);
        new Log().init(internalConfig);
        utils = Mockito.spy(new Utils());
        Utils.reflectiveSetField(Utils.class, "utils", utils);

        Assert.assertNotNull(config);
        Assert.assertNotNull(internalConfig);
        Assert.assertNotNull(core);
        Assert.assertNotNull(module);
        Assert.assertNotNull(dummy);
        Assert.assertNotNull(utils);
    }
}
