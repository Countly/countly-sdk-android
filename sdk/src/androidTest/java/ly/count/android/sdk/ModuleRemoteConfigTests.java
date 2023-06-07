package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import ly.count.android.sdk.internal.RemoteConfigHelper;
import ly.count.android.sdk.internal.RemoteConfigValueStore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleRemoteConfigTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
    }

    /**
     * validating that 'prepareKeysIncludeExclude' works as expected
     */
    @Test
    public void validateKeyIncludeExclude() {
        countlyStore.clear();

        //first a few cases with empty or null values
        String[] res = RemoteConfigHelper.prepareKeysIncludeExclude(null, null, mock(ModuleLog.class));
        Assert.assertNull(res[0]);
        Assert.assertNull(res[1]);

        res = RemoteConfigHelper.prepareKeysIncludeExclude(new String[0], new String[0], mock(ModuleLog.class));
        Assert.assertNull(res[0]);
        Assert.assertNull(res[1]);

        //setting first
        res = RemoteConfigHelper.prepareKeysIncludeExclude(new String[] { "a", "b" }, null, mock(ModuleLog.class));
        Assert.assertEquals("[\"a\",\"b\"]", res[0]);
        Assert.assertNull(res[1]);

        //setting second
        res = RemoteConfigHelper.prepareKeysIncludeExclude(null, new String[] { "c", "d" }, mock(ModuleLog.class));
        Assert.assertNull(res[0]);
        Assert.assertEquals("[\"c\",\"d\"]", res[1]);

        //setting both (include takes precedence)
        res = RemoteConfigHelper.prepareKeysIncludeExclude(new String[] { "e", "f" }, new String[] { "g", "h" }, mock(ModuleLog.class));
        Assert.assertEquals("[\"e\",\"f\"]", res[0]);
        Assert.assertNull(res[1]);
    }

    /**
     * validating that 'mergeCheckResponseIntoCurrentValues' works as expected
     * Simulating a few data updates, with and without clearing
     */
    @Test
    public void validateMergeReceivedResponse() throws Exception {
        countlyStore.clear();
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        Countly countly = new Countly();
        countly.init(cc);

        RemoteConfigValueStore rcvs1 = RemoteConfigValueStore.dataFromString("{\"a\": 123,\"b\": \"fg\"}");
        RemoteConfigValueStore rcvs2 = RemoteConfigValueStore.dataFromString("{\"b\": 33.44,\"c\": \"ww\"}");
        RemoteConfigValueStore rcvs3 = RemoteConfigValueStore.dataFromString("{\"t\": {},\"87\": \"yy\"}");

        //check initial state
        Map<String, Object> vals = countly.remoteConfig().getAllValues();
        Assert.assertNotNull(vals);
        Assert.assertEquals(0, vals.size());

        //add first values without clearing
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(false, rcvs1.values);

        vals = countly.remoteConfig().getAllValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(123, vals.get("a"));
        Assert.assertEquals("fg", vals.get("b"));

        //add second pair of values without clearing
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(false, rcvs2.values);

        vals = countly.remoteConfig().getAllValues();
        Assert.assertEquals(3, vals.size());
        Assert.assertEquals(123, vals.get("a"));
        Assert.assertEquals(33.44, vals.get("b"));
        Assert.assertEquals("ww", vals.get("c"));

        //add third pair with full clear
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(true, rcvs3.values);

        vals = countly.remoteConfig().getAllValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals("yy", vals.get("87"));
        Assert.assertNotNull(vals.get("t"));
        Assert.assertEquals(0, ((JSONObject) vals.get("t")).length());
    }
}
