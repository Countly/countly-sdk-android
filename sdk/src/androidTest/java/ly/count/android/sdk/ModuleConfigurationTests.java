package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleConfigurationTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void init_disabled_storageEmpty() {
        countlyStore.clear();
        CountlyConfig config = TestUtils.createConfigurationConfig();
        Countly countly = Countly.sharedInstance().init(config);

        Assert.assertNull(countlyStore.getServerConfig());
        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
    }

    @Test
    public void init_enabled_storageEmpty() {
        CountlyConfig config = TestUtils.createConfigurationConfig();
        config.enableServerConfiguration();
        Countly countly = Countly.sharedInstance().init(config);

        Assert.assertNull(countlyStore.getServerConfig());
        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
    }

    @Test
    public void init_storageAllowing() throws JSONException {
        countlyStore.setServerConfig(getStorageString(true, true));
        CountlyConfig config = TestUtils.createConfigurationConfig();
        config.enableServerConfiguration();
        Countly countly = Countly.sharedInstance().init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
    }

    @Test
    public void init_storageForbidding() throws JSONException {
        countlyStore.setServerConfig(getStorageString(false, false));
        CountlyConfig config = TestUtils.createConfigurationConfig();
        config.enableServerConfiguration();
        Countly countly = Countly.sharedInstance().init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
    }

    String getStorageString(boolean tracking, boolean networking) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONObject jsonObjectConfig = new JSONObject();

        jsonObjectConfig.put("tracking", tracking);
        jsonObjectConfig.put("networking", networking);

        jsonObject.put("v", 1);
        jsonObject.put("t", 1681808287464L);
        jsonObject.put("c", jsonObjectConfig);

        return jsonObject.toString();
    }
}
