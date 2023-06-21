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
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false).dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);

        countly.consent().removeConsentAll();

        Assert.assertEquals(0, countly.remoteConfig().getValues().size());
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
     * Making sure that caching occurs on the required actions and with the required config options
     */
    @Test
    public void rcValueCaching() {
        for (int a = 0; a < 4; a++) {
            countlyStore.clear();

            CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
            config.enableRemoteConfigAutomaticTriggers();

            if (a == 0 || a == 1) {
                config.setRequiresConsent(true);
                config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.remoteConfig });
            }

            if (a == 0 || a == 2) {
                config.enableRemoteConfigValueCaching();
            }

            Countly countly = (new Countly()).init(config);

            Assert.assertEquals(0, countly.remoteConfig().getValues().size());

            String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "fg") };
            countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false).dataToString());
            Assert.assertEquals(2, countly.remoteConfig().getValues().size());
            assertCValueCachedState(countly.remoteConfig().getValues(), false);

            countly.deviceId().changeWithMerge("dd");
            Assert.assertEquals(2, countly.remoteConfig().getValues().size());
            assertCValueCachedState(countly.remoteConfig().getValues(), false);

            countly.deviceId().changeWithoutMerge("dd11");
            countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.remoteConfig });
            if (a == 0 || a == 2) {
                //we preserve
                Assert.assertEquals(2, countly.remoteConfig().getValues().size());
                assertCValueCachedState(countly.remoteConfig().getValues(), true);
                Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
                Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);
            } else {
                Assert.assertEquals(0, countly.remoteConfig().getValues().size());
            }

            countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false).dataToString());

            countly.deviceId().enableTemporaryIdMode();
            countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.remoteConfig });
            if (a == 0 || a == 2) {
                Assert.assertEquals(2, countly.remoteConfig().getValues().size());
                assertCValueCachedState(countly.remoteConfig().getValues(), true);
                Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
                Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);
            } else {
                Assert.assertEquals(0, countly.remoteConfig().getValues().size());
            }
        }
    }

    /**
     * Validate the the new and old clears are clearing values after they are directly put into storage
     */
    @Test
    public void validateClear() {
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.enableRemoteConfigValueCaching();
        Countly countly = (new Countly()).init(config);

        //set RC
        String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "fg") };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false).dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);

        countly.remoteConfig().clearAll();

        Assert.assertEquals(0, countly.remoteConfig().getValues().size());

        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false).dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);

        countly.remoteConfig().clearStoredValues();

        Assert.assertEquals(0, countly.remoteConfig().getValues().size());
    }

    /**
     * Validate that the new and old getters return the correct values when putting them directly into storage
     *
     * @throws JSONException
     */
    @Test
    public void validateGetters() throws JSONException {
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.enableRemoteConfigValueCaching();
        Countly countly = (new Countly()).init(config);

        //set RC
        JSONArray jArrI = new JSONArray("[3,\"44\",5.1,7.7]");
        JSONObject jObjI = new JSONObject("{\"q\":6,\"w\":\"op\"}");
        String[] rcArr = new String[] { rcEStr("a", 123, false), rcEStr("b", "fg"), rcEStr("c", 222222222222L, false), rcEStr("d", 1.5d), rcEStr("e", jArrI, false), rcEStr("f", jObjI) };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false).dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals(123, countly.remoteConfig().getValueForKey("a"));
        Assert.assertFalse(countly.remoteConfig().getValue("a").isCurrentUsersData);

        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValueForKey("b"));
        Assert.assertTrue(countly.remoteConfig().getValue("b").isCurrentUsersData);

        Assert.assertEquals(222222222222L, countly.remoteConfig().getValue("c").value);
        Assert.assertEquals(222222222222L, countly.remoteConfig().getValueForKey("c"));
        Assert.assertFalse(countly.remoteConfig().getValue("c").isCurrentUsersData);

        Assert.assertEquals(1.5d, countly.remoteConfig().getValue("d").value);
        Assert.assertEquals(1.5d, countly.remoteConfig().getValueForKey("d"));
        Assert.assertTrue(countly.remoteConfig().getValue("d").isCurrentUsersData);

        Assert.assertEquals(jArrI.toString(), countly.remoteConfig().getValue("e").value.toString());
        Assert.assertEquals(jArrI.toString(), countly.remoteConfig().getValueForKey("e").toString());
        Assert.assertFalse(countly.remoteConfig().getValue("e").isCurrentUsersData);

        Assert.assertEquals(jObjI.toString(), countly.remoteConfig().getValue("f").value.toString());
        Assert.assertEquals(jObjI.toString(), countly.remoteConfig().getValueForKey("f").toString());
        Assert.assertTrue(countly.remoteConfig().getValue("f").isCurrentUsersData);

        Map<String, Object> valsOld = countly.remoteConfig().getAllValues();
        Map<String, RCData> valsNew = countly.remoteConfig().getValues();

        Assert.assertEquals(valsNew.size(), valsOld.size());

        for (Map.Entry<String, RCData> entry : valsNew.entrySet()) {
            Object valN = entry.getValue().value;
            Object valO = valsOld.get(entry.getKey());

            if (valN instanceof JSONObject || valN instanceof JSONArray) {
                Assert.assertEquals(valN.toString(), valO.toString());
            } else {
                Assert.assertEquals(valN, valO);
            }
        }
    }

    /**
     * Just making sure nothing explodes when passing bad values
     */
    @Test
    public void passingBadValues() {
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.enableRemoteConfigValueCaching();
        config.enableRemoteConfigAutomaticTriggers();
        Countly countly = (new Countly()).init(config);

        countly.remoteConfig().getValue(null);
        countly.remoteConfig().getValue("");
        countly.remoteConfig().getValueForKey(null);
        countly.remoteConfig().getValueForKey("");

        countly.remoteConfig().update(null);
        countly.remoteConfig().downloadAllKeys(null);

        countly.remoteConfig().downloadOmittingKeys(null, null);
        countly.remoteConfig().downloadOmittingKeys(new String[] {}, null);
        countly.remoteConfig().updateExceptKeys(null, null);
        countly.remoteConfig().updateExceptKeys(new String[] {}, null);

        countly.remoteConfig().downloadSpecificKeys(null, null);
        countly.remoteConfig().downloadSpecificKeys(new String[] {}, null);
        countly.remoteConfig().updateForKeysOnly(null, null);
        countly.remoteConfig().updateForKeysOnly(new String[] {}, null);

        countly.remoteConfig().enrollIntoABTestsForKeys(null);
        countly.remoteConfig().enrollIntoABTestsForKeys(new String[] {});

        countly.remoteConfig().exitABTestsForKeys(null);
        countly.remoteConfig().exitABTestsForKeys(new String[] {});

        countly.remoteConfig().testingGetVariantsForKey(null);
        countly.remoteConfig().testingGetVariantsForKey("");

        countly.remoteConfig().testingEnrollIntoVariant(null, null, null);
        countly.remoteConfig().testingEnrollIntoVariant("", "", null);

        countly.remoteConfig().testingDownloadVariantInformation(null);

        countly.remoteConfig().registerDownloadCallback(null);
        countly.remoteConfig().removeDownloadCallback(null);
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

    static void assertCValueCachedState(Map<String, RCData> rcValues, boolean valuesAreCached) {
        for (Map.Entry<String, RCData> entry : rcValues.entrySet()) {
            if (valuesAreCached) {
                Assert.assertFalse(entry.getValue().isCurrentUsersData);
            } else {
                Assert.assertTrue(entry.getValue().isCurrentUsersData);
            }
        }
    }
}
