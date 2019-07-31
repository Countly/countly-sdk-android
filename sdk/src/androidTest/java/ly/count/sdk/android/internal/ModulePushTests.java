package ly.count.sdk.android.internal;

import org.junit.Test;


import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;

public class ModulePushTests extends BaseTestsAndroid {
    private ModuleDeviceId moduleDeviceId = null;

    @Test
    public void filler(){

    }

/*
    @Override
    protected ConfigCore defaultConfig() throws Exception {
        return super.defaultConfig().enableFeatures(ConfigCore.Feature.Push);
    }

    @Test(expected = IllegalStateException.class)
    public void checkNoMessagingException() throws Exception {
        setUpApplication(defaultConfig());
    }

    @Test
    public void checkTokenGeneration() throws Exception {
        ModuleDeviceIdCore.InstIdInstance.deviceId = "123inst";
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceIdCore.INSTANCE_ID_CLASS_NAME);
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModulePush.FIREBASE_MESSAGING_CLASS);

        ModuleDeviceIdCore.InstIdInstance instance = new ModuleDeviceIdCore.InstIdInstance();
        doReturn(instance).when(utils)._reflectiveCall(eq(ModuleDeviceIdCore.INSTANCE_ID_CLASS_NAME), ArgumentMatchers.isNull(), eq("getInstance"));
        doReturn(instance).when(utils)._reflectiveCall(eq(ModulePush.FIREBASE_MESSAGING_CLASS), ArgumentMatchers.isNull(), eq("getInstance"));

        setUpApplication(defaultConfig().setDeviceIdStrategy(ConfigCore.DeviceIdStrategy.INSTANCE_ID));
        moduleDeviceId = module(ModuleDeviceIdCore.class, false);

        Tasks tasks = Utils.reflectiveGetField(moduleDeviceId, "tasks");
        tasks.await();

        ConfigCore.DID did = config.getDeviceId();
        ConfigCore.DID fid = config.getDeviceId(ConfigCore.DeviceIdRealm.FCM_TOKEN);
        Assert.assertNotNull(did);
        Assert.assertNotNull(did.id);
        Assert.assertNotNull(fid);
        Assert.assertNotNull(fid.id);
        Assert.assertTrue(fid.id.equals(did.id));
        Assert.assertEquals(did.strategy, ConfigCore.DeviceIdStrategy.INSTANCE_ID);
        Assert.assertEquals(fid.strategy, ConfigCore.DeviceIdStrategy.INSTANCE_ID);
        Assert.assertEquals(did.realm, ConfigCore.DeviceIdRealm.DEVICE_ID);
        Assert.assertEquals(fid.realm, ConfigCore.DeviceIdRealm.FCM_TOKEN);
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(did), isNull(ConfigCore.DID.class));
        Mockito.verify(dummy, times(1)).onDeviceId(isA(ctx.getClass()), eq(fid), isNull(ConfigCore.DID.class));

        UserImpl user = Utils.reflectiveGetField(sdk, "user");
        user.edit().addToCohort("COHORT_NEW").removeFromCohort("COHORT_OLD").commit();

        Mockito.verify(dummy, times(1)).onUserChanged(isA(ctx.getClass()), isA(JSONObject.class), eq(new HashSet<String>(Collections.singletonList("COHORT_NEW"))), eq(new HashSet<String>(Collections.singletonList("COHORT_OLD"))));
        Mockito.verify(utils, times(1))._reflectiveCall(eq((String)null), eq(instance), eq("subscribeToTopic"), eq("COHORT_NEW"));
        Mockito.verify(utils, times(1))._reflectiveCall(eq((String)null), eq(instance), eq("unsubscribeFromTopic"), eq("COHORT_OLD"));
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
        data.remove(ModulePush.KEY_ID);
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
    */
}
