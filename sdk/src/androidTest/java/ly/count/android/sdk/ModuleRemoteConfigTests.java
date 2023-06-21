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
import static java.lang.Thread.sleep;
import static ly.count.android.sdk.RemoteConfigValueStoreTests.rcArrIntoJSON;
import static ly.count.android.sdk.RemoteConfigValueStoreTests.rcEStr;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleRemoteConfigTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));

        countlyStore.clear();
    }

    /**
     * Consent removal should clear stored remote config values
     */
    @Test
    public void valuesClearedOnConsentRemoval() {
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.setRequiresConsent(true);
        config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.remoteConfig });
        config.enableRemoteConfigValueCaching();
        config.enableRemoteConfigAutomaticTriggers();
        Countly countly = (new Countly()).init(config);

        //set RC
        String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "fg") };
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false);
        countlyStore.setRemoteConfigValues(rcvs.dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);

        countly.consent().removeConsentAll();

        Assert.assertNull(countly.remoteConfig().getValue("a").value);
        Assert.assertNull(countly.remoteConfig().getValue("b").value);
    }

    /**
     * Making sure that automatic RC is triggered on the right requests
     * Some differences apply depending on if consent is required or isn't
     */
    @Test
    public void automaticRCTriggers() {
        for (int a = 0; a < 2; a++) {
            countlyStore.clear();
            final int[] triggerCounter = new int[] { 0 };
            int intendedCount = 0;

            CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
            config.enableRemoteConfigAutomaticTriggers();
            if (a == 0) {
                config.setRequiresConsent(true);
                config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.remoteConfig });
            }
            config.immediateRequestGenerator = () -> (ImmediateRequestI) (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                triggerCounter[0]++;
            };
            Countly countly = (new Countly()).init(config);
            Assert.assertEquals(++intendedCount, triggerCounter[0]);//init should create a request

            countly.consent().removeConsentAll();
            Assert.assertEquals(intendedCount, triggerCounter[0]);//consent removal does nothing

            countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.remoteConfig });
            if (a == 0) {
                Assert.assertEquals(++intendedCount, triggerCounter[0]);//giving consent should create a request
            } else {
                Assert.assertEquals(intendedCount, triggerCounter[0]);//giving consent would not create a request if no consent is required
            }

            countly.deviceId().changeWithMerge("dd");
            Assert.assertEquals(intendedCount, triggerCounter[0]);//changing device ID with merging should not create a request

            countly.deviceId().changeWithoutMerge("dd11");
            //todo the current behaviour is slightly out of spec as it would download RC after the RQ would have executed that request
            //todo this should be updated once the RQ is reworked
            Assert.assertEquals(intendedCount, triggerCounter[0]);//changing device ID without merging should create a request

            countly.deviceId().enableTemporaryIdMode();
            Assert.assertEquals(intendedCount, triggerCounter[0]);//entering tempID mode should not create a request

            countly.deviceId().changeWithMerge("dd");
            if (a == 0) {
                Assert.assertEquals(intendedCount, triggerCounter[0]);//exiting temp ID mode with "withMerge" should create a request, but would not since there is no consent
            } else {
                Assert.assertEquals(++intendedCount, triggerCounter[0]);//exiting temp ID mode with "withMerge" should create a request since consent mode is not prohibiting it
            }

            countly.deviceId().enableTemporaryIdMode();
            Assert.assertEquals(intendedCount, triggerCounter[0]);//entering tempID mode should not create a request

            countly.deviceId().changeWithoutMerge("dd");
            if (a == 0) {
                Assert.assertEquals(intendedCount, triggerCounter[0]);//exiting temp ID mode with "withoutMerge" should create a request, but would not since there is no consent
            } else {
                Assert.assertEquals(++intendedCount, triggerCounter[0]);//exiting temp ID mode with "withoutMerge" should create a request since consent mode is not prohibiting it
            }
        }
    }

    /**
     * validating that 'prepareKeysIncludeExclude' works as expected
     */
    @Test
    public void validateKeyIncludeExclude() {
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
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        Countly countly = new Countly();
        countly.init(cc);

        RemoteConfigValueStore rcvs1 = RemoteConfigValueStore.dataFromString("{\"a\": 123,\"b\": \"fg\"}", false);
        RemoteConfigValueStore rcvs2 = RemoteConfigValueStore.dataFromString("{\"b\": 33.44,\"c\": \"ww\"}", false);
        RemoteConfigValueStore rcvs3 = RemoteConfigValueStore.dataFromString("{\"t\": {},\"87\": \"yy\"}", false);

        //check initial state
        Map<String, Object> vals = countly.remoteConfig().getAllValues();
        Assert.assertNotNull(vals);
        Assert.assertEquals(0, vals.size());

        //add first values without clearing
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(false, RemoteConfigHelper.DownloadedValuesIntoMap(rcvs1.values));

        vals = countly.remoteConfig().getAllValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(123, vals.get("a"));
        Assert.assertEquals("fg", vals.get("b"));

        //add second pair of values without clearing
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(false, RemoteConfigHelper.DownloadedValuesIntoMap(rcvs2.values));

        vals = countly.remoteConfig().getAllValues();
        Assert.assertEquals(3, vals.size());
        Assert.assertEquals(123, vals.get("a"));
        Assert.assertEquals(33.44, vals.get("b"));
        Assert.assertEquals("ww", vals.get("c"));

        //add third pair with full clear
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(true, RemoteConfigHelper.DownloadedValuesIntoMap(rcvs3.values));

        vals = countly.remoteConfig().getAllValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals("yy", vals.get("87"));
        Assert.assertNotNull(vals.get("t"));
        Assert.assertEquals(0, ((JSONObject) vals.get("t")).length());
    }
}
