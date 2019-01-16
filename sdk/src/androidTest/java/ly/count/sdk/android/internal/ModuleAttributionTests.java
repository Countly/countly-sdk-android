package ly.count.sdk.android.internal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ModuleAttributionTests extends BaseTests {

    @Test
    public void Filler(){

    }
//    private static final String TEST_CID = "cb14e5f33b528334715f1809e4572842c74686df";
//    private static final String TEST_UID = "ecf125107e4e27e6bcaacb3ae10ddba66459e6ae";
//    private static final String TEST_REFERRER = "countly_cid%3D" + TEST_CID + "%26countly_cuid%3D" + TEST_UID;
//    private static final String INVALID_REFERRER = "some=param";
//    private ModuleAttribution moduleAttribution = null;
//    private ModuleDeviceIdCore moduleDeviceId = null;
//
//    @Before
//    public void setUp() throws Exception {
//        super.setUp();
//    }
//
//    @Override
//    protected ConfigCore defaultConfig() throws Exception {
//        return super.defaultConfig().enableFeatures(ConfigCore.Feature.Attribution);
//    }
//
//    @Test
//    public void checkOpenUDID() throws Exception {
//        ModuleDeviceIdCore.AdvIdInfo.deviceId = "123adv";
//        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceIdCore.ADVERTISING_ID_CLIENT_CLASS_NAME);
//        doReturn(new ModuleDeviceIdCore.AdvIdInfo()).when(utils)._reflectiveCallStrict(eq(ModuleDeviceIdCore.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), eq(android.content.Context.class), isA(android.content.Context.class));
//
//        setUpApplication(defaultConfig());
//        moduleDeviceId = module(ModuleDeviceIdCore.class, false);
//
//        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
//        tasks.await();
//
//        ConfigCore.DID did = config.getDeviceId();
//        ConfigCore.DID aid = config.getDeviceId(ConfigCore.DeviceIdRealm.ADVERTISING_ID);
//        Assert.assertNotNull(did);
//        Assert.assertNotNull(did.id);
//        Assert.assertNotNull(aid);
//        Assert.assertNotNull(aid.id);
//        Assert.assertFalse(aid.id.equals(did.id));
//        Assert.assertEquals(did.strategy, ConfigCore.DeviceIdStrategy.ANDROID_ID);
//        Assert.assertEquals(aid.strategy, ConfigCore.DeviceIdStrategy.ADVERTISING_ID);
//        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(ConfigCore.DID.class));
//        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(aid), isNull(ConfigCore.DID.class));
//    }
//
//    @Test
//    public void checkAdvertisingId() throws Exception {
//        ModuleDeviceIdCore.AdvIdInfo.deviceId = "123adv";
//        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceIdCore.ADVERTISING_ID_CLIENT_CLASS_NAME);
//        doReturn(new ModuleDeviceIdCore.AdvIdInfo()).when(utils)._reflectiveCallStrict(eq(ModuleDeviceIdCore.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), eq(android.content.Context.class), isA(android.content.Context.class));
//
//        setUpApplication(defaultConfig().setDeviceIdStrategy(ConfigCore.DeviceIdStrategy.ADVERTISING_ID));
//        moduleDeviceId = module(ModuleDeviceIdCore.class, false);
//
//        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
//        tasks.await();
//
//        ConfigCore.DID did = config.getDeviceId();
//        ConfigCore.DID aid = config.getDeviceId(ConfigCore.DeviceIdRealm.ADVERTISING_ID);
//        Assert.assertNotNull(did);
//        Assert.assertNotNull(did.id);
//        Assert.assertNotNull(aid);
//        Assert.assertNotNull(aid.id);
//        Assert.assertTrue(aid.id.equals(did.id));
//        Assert.assertEquals(did.strategy, ConfigCore.DeviceIdStrategy.ADVERTISING_ID);
//        Assert.assertEquals(aid.strategy, ConfigCore.DeviceIdStrategy.ADVERTISING_ID);
//        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(ConfigCore.DID.class));
//        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(aid), isNull(ConfigCore.DID.class));
//    }
//
//    @Test
//    public void requestNoAdId() throws Exception {
//        ModuleDeviceIdCore.AdvIdInfo.deviceId = "123adv";
//        doReturn(Boolean.FALSE).when(utils)._reflectiveClassExists(ModuleDeviceIdCore.ADVERTISING_ID_CLIENT_CLASS_NAME);
//
//        setUpApplication(defaultConfig().setDeviceIdStrategy(ConfigCore.DeviceIdStrategy.ANDROID_ID));
//        moduleDeviceId = module(ModuleDeviceIdCore.class, false);
//        moduleAttribution = module(ModuleAttribution.class, false);
//
//        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
//        tasks.await();
//
//        Assert.assertFalse(moduleAttribution.onRequest(new Request().own(ModuleAttribution.class)));
//    }
//
//    @Test
//    public void requestAdId() throws Exception {
//        ModuleDeviceIdCore.AdvIdInfo.deviceId = "123adv";
//        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceIdCore.ADVERTISING_ID_CLIENT_CLASS_NAME);
//        doReturn(new ModuleDeviceIdCore.AdvIdInfo()).when(utils)._reflectiveCallStrict(eq(ModuleDeviceIdCore.ADVERTISING_ID_CLIENT_CLASS_NAME), ArgumentMatchers.isNull(), eq("getAdvertisingIdInfo"), eq(android.content.Context.class), isA(android.content.Context.class));
//
//        setUpApplication(defaultConfig().setDeviceIdStrategy(ConfigCore.DeviceIdStrategy.ANDROID_ID));
//        moduleDeviceId = module(ModuleDeviceIdCore.class, false);
//        moduleAttribution = module(ModuleAttribution.class, false);
//
//        Request request = new Request().own(ModuleAttribution.class);
//        Assert.assertNull(moduleAttribution.onRequest(request));
//        Assert.assertFalse(request.params.toString().contains(ModuleAttribution.CLY_AID));
//
//        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
//        tasks.await();
//
//        Assert.assertTrue(moduleAttribution.onRequest(request));
//        Assert.assertTrue(request.params.toString().contains(ModuleAttribution.CLY_AID + "=" + ModuleDeviceIdCore.AdvIdInfo.deviceId));
//    }
//
//    @Test
//    public void receiver_action_bad() throws Exception {
//        setUpApplication(null);
//        Core.deinit();
//
//        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
//        Intent intent = new Intent("bad action");
//        receiver.onReceive(getContext(), intent);
//        verify(receiver, never()).extractReferrer(isA(android.content.Context.class), isA(BroadcastReceiver.PendingResult[].class), anyString());
//    }
//
//    @Test
//    public void receiver_action_ok_nourl() throws Exception {
//        setUpApplication(null);
//        Core.deinit();
//
//        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
//        Intent intent = new Intent(ModuleAttribution.ACTION);
//        receiver.onReceive(getContext(), intent);
//        verify(receiver, never()).extractReferrer(isA(android.content.Context.class), isA(BroadcastReceiver.PendingResult[].class), anyString());
//    }
//
//    @Test
//    public void receiver_action_ok_url_ok_nocly() throws Exception {
//        setUpApplication(null);
//
//        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
//        Intent intent = new Intent(ModuleAttribution.ACTION);
//        intent.putExtra(ModuleAttribution.EXTRA, INVALID_REFERRER);
//        receiver.onReceive(getContext(), intent);
//        verify(receiver, times(1)).extractReferrer(isA(android.content.Context.class), any(BroadcastReceiver.PendingResult[].class), eq(Utils.urldecode(INVALID_REFERRER)));
//        verify(receiver, never()).recordRequest(anyString(), anyString());
//    }
//
//    @Test
//    public void receiver_action_ok_url_cly_ok() throws Exception {
//        setUpApplication(null);
//
//        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
//        Intent intent = new Intent(ModuleAttribution.ACTION);
//        intent.putExtra(ModuleAttribution.EXTRA, TEST_REFERRER);
//        receiver.onReceive(getContext(), intent);
//        verify(receiver, times(1)).extractReferrer(isA(android.content.Context.class), any(BroadcastReceiver.PendingResult[].class), eq(Utils.urldecode(TEST_REFERRER)));
//    }
//
//    @Test
//    public void receiver_action_ok_url_cly_ok_request() throws Exception {
//        setUpApplication(null);
//
//        ModuleAttribution.AttributionReferrerReceiver receiver = spy(new ModuleAttribution.AttributionReferrerReceiver());
//        Intent intent = new Intent(ModuleAttribution.ACTION);
//        intent.putExtra(ModuleAttribution.EXTRA, TEST_REFERRER);
//        receiver.onReceive(getContext(), intent);
//        verify(receiver, times(1)).extractReferrer(isA(android.content.Context.class), any(BroadcastReceiver.PendingResult[].class), eq(Utils.urldecode(TEST_REFERRER)));
//        verify(receiver, times(1)).recordRequest(eq(TEST_CID), eq(TEST_UID));
//    }
}
