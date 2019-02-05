package ly.count.android.sdk;

import android.test.AndroidTestCase;

import org.json.JSONException;

public class RemoteConfigTests extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testSerializeDeserialize() throws JSONException {
        RemoteConfig.RemoteConfigValueStore remoteConfigValueStore = RemoteConfig.RemoteConfigValueStore.dataFromString(null);

        remoteConfigValueStore.values.put("fd", 12);
        remoteConfigValueStore.values.put("2fd", 142);
        remoteConfigValueStore.values.put("f3d", 123);

        RemoteConfig.RemoteConfigValueStore.dataFromString(remoteConfigValueStore.dataToString());
    }
}
