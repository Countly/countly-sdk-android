package ly.count.android.sdk.internal;

import android.content.*;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.List;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ModuleAttributionTests {
    private static final String TEST_CID = "cb14e5f33b528334715f1809e4572842c74686df";
    private static final String TEST_UID = "ecf125107e4e27e6bcaacb3ae10ddba66459e6ae";
    private static final String TEST_REFERRER = "countly_cid%3D" + TEST_CID + "%26countly_cuid%3D" + TEST_UID;
    private static final String INVALID_REFERRER = "some=param";
    private Config config = null;
    private InternalConfig internalConfig = null;
    private Core core = null;
    private ModuleAttribution module = null;
    private ModuleDeviceId moduleDeviceId = null;
    private Module dummy = null;
    private Utils utils = null;
    private Context ctx = null;

    @Before
    public void beforeEachTest() throws Exception {
        ctx = new ContextImpl(getContext());
        Core.initForApplication(TestingUtilityInternal.setupConfig(), getContext());
        Core.instance.purgeInternalStorage(ctx, null);
        Core.instance.deinit();
    }

    @Test
    public void checkOpenUDID() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.AdvIdInfo.deviceId = "123adv";
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.OPEN_UDID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(false);

        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);
        doReturn(new ModuleDeviceId.AdvIdInfo()).when(utils)._reflectiveCallStrict(eq(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), eq(android.content.Context.class), isA(android.content.Context.class));

        core.onContextAcquired(TestingUtilityInternal.mockApplication(context));
        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        tasks.awaitTermination();

        Config.DID did = internalConfig.getDeviceId();
        Config.DID aid = internalConfig.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID);
        Assert.assertNotNull(did);
        Assert.assertNotNull(did.id);
        Assert.assertNotNull(aid);
        Assert.assertNotNull(aid.id);
        Assert.assertFalse(aid.id.equals(did.id));
        Assert.assertEquals(did.strategy, Config.DeviceIdStrategy.OPEN_UDID);
        Assert.assertEquals(aid.strategy, Config.DeviceIdStrategy.ADVERTISING_ID);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(Config.DID.class));
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(aid), isNull(Config.DID.class));
    }

    @Test
    public void checkAdvertisingId() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.AdvIdInfo.deviceId = "123adv";
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(false);

        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);
        doReturn(new ModuleDeviceId.AdvIdInfo()).when(utils)._reflectiveCallStrict(eq(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), eq(android.content.Context.class), isA(android.content.Context.class));

        core.onContextAcquired(TestingUtilityInternal.mockApplication(context));
        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        tasks.awaitTermination();

        Config.DID did = internalConfig.getDeviceId();
        Config.DID aid = internalConfig.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID);
        Assert.assertNotNull(did);
        Assert.assertNotNull(did.id);
        Assert.assertNotNull(aid);
        Assert.assertNotNull(aid.id);
        Assert.assertTrue(aid.id.equals(did.id));
        Assert.assertEquals(did.strategy, Config.DeviceIdStrategy.ADVERTISING_ID);
        Assert.assertEquals(aid.strategy, Config.DeviceIdStrategy.ADVERTISING_ID);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(Config.DID.class));
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(aid), isNull(Config.DID.class));
    }

    @Test
    public void requestNoAdId() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.AdvIdInfo.deviceId = "123adv";
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.OPEN_UDID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(false);

        doReturn(Boolean.FALSE).when(utils)._reflectiveClassExists(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);

        core.onContextAcquired(TestingUtilityInternal.mockApplication(context));
        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        tasks.awaitTermination();

        Assert.assertFalse(module.onRequest(new Request().own(ModuleAttribution.class)));
    }

    @Test
    public void requestAdId() throws Exception {
        android.content.Context context = getContext();
        ModuleDeviceId.AdvIdInfo.deviceId = "123adv";
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.OPEN_UDID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(false);

        Request request = new Request().own(ModuleAttribution.class);
        Assert.assertNull(module.onRequest(request));
        Assert.assertFalse(request.params.toString().contains(ModuleAttribution.CLY_AID));

        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);
        doReturn(new ModuleDeviceId.AdvIdInfo()).when(utils)._reflectiveCallStrict(eq(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), eq(android.content.Context.class), isA(android.content.Context.class));

        core.onContextAcquired(TestingUtilityInternal.mockApplication(context));
        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        tasks.awaitTermination();

        Assert.assertTrue(module.onRequest(request));
        Assert.assertTrue(request.params.toString().contains(ModuleAttribution.CLY_AID + "=" + ModuleDeviceId.AdvIdInfo.deviceId));
    }

    @Test
    public void receiver_action_bad() throws Exception {
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);

        Core.initForApplication(config, getContext());
        Storage.push(ctx, (Storable) Utils.reflectiveGetField(Core.instance, "config"));
        Core.instance.deinit();

        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
        Intent intent = new Intent("bad action");
        receiver.onReceive(getContext(), intent);
        verify(receiver, never()).extractReferrer(isA(android.content.Context.class), isA(BroadcastReceiver.PendingResult[].class), anyString());
    }

    @Test
    public void receiver_action_ok_nourl() throws Exception {
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);

        Core.initForApplication(config, getContext());
        Storage.push(ctx, (Storable) Utils.reflectiveGetField(Core.instance, "config"));
        Core.instance.deinit();

        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
        Intent intent = new Intent(ModuleAttribution.ACTION);
        receiver.onReceive(getContext(), intent);
        verify(receiver, never()).extractReferrer(isA(android.content.Context.class), isA(BroadcastReceiver.PendingResult[].class), anyString());
    }

    @Test
    public void receiver_action_ok_url_ok_nocly() throws Exception {
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);

        Core.initForApplication(config, getContext());
        Storage.push(ctx, (Storable) Utils.reflectiveGetField(Core.instance, "config"));
        Core.instance.deinit();

        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
        Intent intent = new Intent(ModuleAttribution.ACTION);
        intent.putExtra(ModuleAttribution.EXTRA, INVALID_REFERRER);
        receiver.onReceive(getContext(), intent);
        verify(receiver, times(1)).extractReferrer(isA(android.content.Context.class), any(BroadcastReceiver.PendingResult[].class), eq(Utils.urldecode(INVALID_REFERRER)));
        verify(receiver, never()).recordRequest(anyString(), anyString());
    }

    @Test
    public void receiver_action_ok_url_cly_ok() throws Exception {
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);

        Core.initForApplication(config, getContext());
        Storage.push(ctx, (Storable) Utils.reflectiveGetField(Core.instance, "config"));
        Core.instance.deinit();

        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
        Intent intent = new Intent(ModuleAttribution.ACTION);
        intent.putExtra(ModuleAttribution.EXTRA, TEST_REFERRER);
        receiver.onReceive(getContext(), intent);
        verify(receiver, times(1)).extractReferrer(isA(android.content.Context.class), any(BroadcastReceiver.PendingResult[].class), eq(Utils.urldecode(TEST_REFERRER)));
    }


    @Test
    public void receiver_action_ok_url_cly_ok_request() throws Exception {
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Attribution);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);

        Core.initForApplication(config, getContext());
        Storage.push(ctx, (Storable) Utils.reflectiveGetField(Core.instance, "config"));
        Core.instance.deinit();

        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
        Intent intent = new Intent(ModuleAttribution.ACTION);
        intent.putExtra(ModuleAttribution.EXTRA, TEST_REFERRER);
        receiver.onReceive(getContext(), intent);
        verify(receiver, times(1)).extractReferrer(isA(android.content.Context.class), any(BroadcastReceiver.PendingResult[].class), eq(Utils.urldecode(TEST_REFERRER)));
        verify(receiver, times(1)).recordRequest(eq(TEST_CID), eq(TEST_UID));
    }

    private void init(boolean mockModule) throws MalformedURLException {
        if (config == null) {
            config = TestingUtilityInternal.setupConfig();
            config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        }
        config.enableTestMode();
        if (core == null) {
            core = Core.initForApplication(config, getContext());
        }
        dummy = Mockito.mock(Module.class);
        List<Module> list = Whitebox.getInternalState(core, "modules");
        if (module == null) {
            for (Module m : list) {
                if (m instanceof ModuleAttribution) {
                    module = (ModuleAttribution) m;
                }
                if (m instanceof ModuleDeviceId) {
                    moduleDeviceId = (ModuleDeviceId) m;
                }
            }
            if (mockModule) {
                list.remove(module);
                module = Mockito.spy(module);
                list.add(1, module);
            }
        }
        list.add(dummy);
        internalConfig = Utils.reflectiveGetField(core, "config");
        new Log().init(internalConfig);
        utils = Mockito.spy(new Utils());
        Utils.reflectiveSetField(Utils.class, "utils", utils);

        Assert.assertNotNull(config);
        Assert.assertNotNull(internalConfig);
        Assert.assertNotNull(core);
        Assert.assertNotNull(module);
        Assert.assertNotNull(moduleDeviceId);
        Assert.assertNotNull(dummy);
        Assert.assertNotNull(utils);
    }

}
