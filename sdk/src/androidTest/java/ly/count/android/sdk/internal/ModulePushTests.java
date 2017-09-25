package ly.count.android.sdk.internal;

import android.os.Parcel;
import android.support.annotation.NonNull;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

public class ModulePushTests {
    private Config config = null;
    private InternalConfig internalConfig = null;
    private Core core = null;
    private ModulePush module = null;
    private ModuleDeviceId moduleDeviceId = null;
    private Module dummy = null;
    private Utils utils = null;

    @Before
    public void beforeEachTest() throws Exception {
        Core.initForApplication(TestingUtilityInternal.setupConfig(), getContext());
        Core.instance.purgeInternalStorage(null);
        Core.instance.deinit();
        utils = Mockito.spy(new Utils());
        Utils.reflectiveSetField(Utils.class, "utils", utils);
    }

    @Test(expected = IllegalStateException.class)
    public void checkNoMessagingException() throws Exception {
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Push);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(false);
    }

    @Test
    public void checkTokenGeneration() throws Exception {
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceId.INSTANCE_ID_CLASS_NAME);
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModulePush.FIREBASE_MESSAGING_CLASS);
        doReturn(new ModuleDeviceId.InstIdInstance()).when(utils)._reflectiveCall(eq(ModuleDeviceId.INSTANCE_ID_CLASS_NAME), ArgumentMatchers.isNull(), eq("getInstance"));

        android.content.Context context = getContext();
        ModuleDeviceId.InstIdInstance.deviceId = "123inst";
        config = TestingUtilityInternal.setupConfig();
        config.addFeature(Config.Feature.Push);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);
        config.enableTestMode();
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        init(false);

        core.onContextAcquired(TestingUtilityInternal.mockApplication(context));
        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        tasks.awaitTermination();

        Config.DID did = internalConfig.getDeviceId();
        Config.DID fid = internalConfig.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN);
        Assert.assertNotNull(did);
        Assert.assertNotNull(did.id);
        Assert.assertNotNull(fid);
        Assert.assertNotNull(fid.id);
        Assert.assertTrue(fid.id.equals(did.id));
        Assert.assertEquals(did.strategy, Config.DeviceIdStrategy.INSTANCE_ID);
        Assert.assertEquals(fid.strategy, Config.DeviceIdStrategy.INSTANCE_ID);
        Assert.assertEquals(did.realm, Config.DeviceIdRealm.DEVICE_ID);
        Assert.assertEquals(fid.realm, Config.DeviceIdRealm.FCM_TOKEN);
        Mockito.verify(dummy, times(1)).onDeviceId(did, null);
        Mockito.verify(dummy, times(1)).onDeviceId(fid, null);
    }

    @Test
    public void checkMessageParcel() throws Exception {
        Map<String, String> data = map();

        ModulePush.MessageImpl original = new ModulePush.MessageImpl(data);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();

        parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);

        ModulePush.MessageImpl message = ModulePush.MessageImpl.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        Assert.assertEquals(original.id, message.id);
        Assert.assertEquals(original.title(), message.title());
        Assert.assertEquals(original.message(), message.message());
        Assert.assertEquals(original.sound(), message.sound());
        Assert.assertEquals(original.badge(), message.badge());
        Assert.assertEquals(original.link(), message.link());
        Assert.assertEquals(original.media(), message.media());
        Assert.assertEquals(original.buttons(), message.buttons());
    }

    @Test
    public void checkDecodeNoId() throws Exception {
        Map<String, String> data = map();
        data.remove("id");
        Assert.assertNull(ModulePush.decodeMessage(data));
    }

    @NonNull
    private Map<String, String> map() {
        Map<String, String> data = new HashMap<>();
        data.put(ModulePush.KEY_ID, "id");
        data.put(ModulePush.KEY_TITLE, "title");
        data.put(ModulePush.KEY_MESSAGE, "message");
        data.put(ModulePush.KEY_SOUND, "default");
        data.put(ModulePush.KEY_BADGE, "3");
        data.put(ModulePush.KEY_LINK, "http://count.ly");
        data.put(ModulePush.KEY_MEDIA, "http://count.ly/logo");
        data.put(ModulePush.KEY_BUTTONS, "[{\"t\": \"b1 title\", \"l\": \"http://b1.com\"}, {\"t\": \"b2 title\", \"l\": \"http://b2.com\"}]");
        return data;
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
                if (m instanceof ModulePush) {
                    module = (ModulePush) m;
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

        Assert.assertNotNull(config);
        Assert.assertNotNull(internalConfig);
        Assert.assertNotNull(core);
        Assert.assertNotNull(module);
        Assert.assertNotNull(moduleDeviceId);
        Assert.assertNotNull(dummy);
        Assert.assertNotNull(utils);
    }

}
