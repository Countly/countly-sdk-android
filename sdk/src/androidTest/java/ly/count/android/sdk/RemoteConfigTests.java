package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteConfigTests {

    @Before
    public void setUp() {

    }

    @Test
    public void testSerializeDeserialize() throws JSONException {
        RemoteConfig.RemoteConfigValueStore remoteConfigValueStore = RemoteConfig.RemoteConfigValueStore.dataFromString(null);

        remoteConfigValueStore.values.put("fd", 12);
        remoteConfigValueStore.values.put("2fd", 142);
        remoteConfigValueStore.values.put("f3d", 123);

        RemoteConfig.RemoteConfigValueStore.dataFromString(remoteConfigValueStore.dataToString());
    }
}
