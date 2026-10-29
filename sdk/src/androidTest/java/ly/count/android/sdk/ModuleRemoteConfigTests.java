package ly.count.android.sdk;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import ly.count.android.sdk.internal.RemoteConfigHelper;
import ly.count.android.sdk.internal.RemoteConfigValueStore;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static ly.count.android.sdk.RemoteConfigValueStoreTests.rcArrIntoJSON;
import static ly.count.android.sdk.RemoteConfigValueStoreTests.rcEStr;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleRemoteConfigTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));

        countlyStore.clear();
        countlyStore.setDataSchemaVersion(MigrationHelper.DATA_SCHEMA_VERSIONS);
    }

    /**
     * Consent removal shouldn't clear stored remote config values
     */
    @Test
    public void valuesClearedOnConsentRemoval() {
        CountlyConfig config = TestUtils.withCrashReporting(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true));
        config.setRequiresConsent(true);
        config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.remoteConfig });
        config.enableRemoteConfigValueCaching();
        config.enableRemoteConfigAutomaticTriggers();
        Countly countly = new Countly().init(config);

        //set RC
        String[] rcArr = { rcEStr("a", 123), rcEStr("b", "fg") };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);

        countly.consent().removeConsentAll();

        Assert.assertEquals(2, countly.remoteConfig().getValues().size()); // values are cache cleared
        for (Map.Entry<String, RCData> kv : countly.remoteConfig().getValues().entrySet()) {
            Assert.assertTrue(kv.getValue().isCurrentUsersData);
        }
    }

    /**
     * Making sure that automatic RC is triggered on the right requests
     * Some differences apply depending on if consent is required or isn't
     */
    @Test
    public void automaticRCTriggers() {
        for (int a = 0; a < 2; a++) {
            countlyStore.clear();
            final int[] triggerCounter = { 0 }; // because we now have server config fetch
            int intendedCount = 0;

            CountlyConfig config = TestUtils.withCrashReporting(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true)).disableHealthCheck();
            config.disableHealthCheck();//mocked tests fail without disabling this
            config.enableRemoteConfigAutomaticTriggers();
            if (a == 0) {
                config.setRequiresConsent(true);
                config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.remoteConfig });
            }

            config.immediateRequestGenerator = new ImmediateRequestGenerator() {
                @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                    return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                        if (!requestData.endsWith("method=sc")) { // this is server config, disabling it for this test
                            triggerCounter[0]++;
                        }
                    };
                }

                @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                    return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                        if (!requestData.endsWith("method=sc")) { // this is server config, disabling it for this test
                            triggerCounter[0]++;
                        }
                    };
                }
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
            Assert.assertEquals(++intendedCount, triggerCounter[0]);//changing device ID with merging should create a request

            countly.deviceId().changeWithoutMerge("dd11");
            if (a == 0) {
                Assert.assertEquals(intendedCount, triggerCounter[0]);//changing device ID without merging won't create a request because all consents are revoked
            } else {
                Assert.assertEquals(++intendedCount, triggerCounter[0]);//changing device ID without merging would create a request because consent is not required
            }

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
     * Creating a "matrix" that goes over combinations of RC config flags and consent
     */
    @Test
    public void rcValueCaching() {
        boolean valuesAreCached = false;
        for (int a = 0; a < 4; a++) {
            countlyStore.clear();

            CountlyConfig config = TestUtils.createBaseConfig();
            config.enableRemoteConfigAutomaticTriggers();

            if (a == 0 || a == 1) {
                config.setRequiresConsent(true);
                config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.remoteConfig });
            }

            if (a == 0 || a == 2) {
                config.enableRemoteConfigValueCaching();
                valuesAreCached = true;
            }

            Countly countly = new Countly().init(config);

            Assert.assertEquals(0, countly.remoteConfig().getValues().size());

            String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "fg") };
            countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());
            Assert.assertEquals(2, countly.remoteConfig().getValues().size());
            assertCValueCachedState(countly.remoteConfig().getValues(), false);

            //changing with merging should trigger caching
            countly.deviceId().changeWithMerge("dd");
            assertCValueCachedState(countly.remoteConfig().getValues(), valuesAreCached);

            //changing without merging should trigger caching. Lack of consent should leave no impact on this
            assertCValueCachedState(countly.remoteConfig().getValues(), valuesAreCached);
            countly.deviceId().changeWithoutMerge("dd11");

            for (int b = 0; b < 2; b++) {
                if (b == 1) {
                    countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.remoteConfig });
                }
                if (a == 0 || a == 2) {
                    //we preserve
                    Assert.assertEquals(2, countly.remoteConfig().getValues().size());
                    assertCValueCachedState(countly.remoteConfig().getValues(), true);
                    Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
                    Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);
                } else {
                    Assert.assertEquals(0, countly.remoteConfig().getValues().size());
                }
            }

            //entering temp ID mode should trigger caching. Lack of consent should leave no impact on this
            countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());
            countly.deviceId().enableTemporaryIdMode();

            for (int b = 0; b < 2; b++) {
                if (b == 1) {
                    countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.remoteConfig });
                }
                if (a == 0 || a == 2) {
                    Assert.assertEquals(2, countly.remoteConfig().getValues().size());
                    assertCValueCachedState(countly.remoteConfig().getValues(), true);
                    Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
                    Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);
                } else {
                    Assert.assertEquals(0, countly.remoteConfig().getValues().size());
                }
            }

            valuesAreCached = false;
        }
    }

    @Test
    public void validateValuePersistence() {
        //set RC
        String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "fg") };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());

        CountlyConfig config = TestUtils.withCrashReporting(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true));
        config.enableRemoteConfigValueCaching();
        Countly countly = new Countly().init(config);

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);

        Countly countly2 = new Countly().init(config);
        Assert.assertEquals(123, countly2.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly2.remoteConfig().getValue("b").value);
    }

    /**
     * Validate that "clearAll" clears values after they are directly put into storage
     */
    @Test
    public void validateClear() {
        CountlyConfig config = TestUtils.withCrashReporting(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true));
        config.enableRemoteConfigValueCaching();
        Countly countly = new Countly().init(config);

        //set RC
        String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "fg") };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);

        countly.remoteConfig().clearAll();

        Assert.assertEquals(0, countly.remoteConfig().getValues().size());
    }

    RCDownloadCallback createCallback(int index, int[] resArray) {
        RCDownloadCallback ret = new RCDownloadCallback() {
            @Override public void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, Map<String, RCData> downloadedValues) {
                resArray[index]++;
            }
        };

        return ret;
    }

    /**
     * Validating that the RC callbacks are called for the appropriate actions
     */
    @Test
    public void rcGlobalCallback() {
        int[] resArray = new int[10];
        int cIndex = 0;

        RCDownloadCallback c0 = createCallback(cIndex++, resArray);
        RCDownloadCallback c1 = createCallback(cIndex++, resArray);
        RCDownloadCallback c2 = createCallback(cIndex++, resArray);
        RCDownloadCallback c3 = createCallback(cIndex++, resArray);
        RCDownloadCallback c4 = createCallback(cIndex++, resArray);
        RCDownloadCallback c5 = createCallback(cIndex++, resArray);
        RCDownloadCallback c6 = createCallback(cIndex, resArray);

        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableRemoteConfigAutomaticTriggers();
        config.RemoteConfigRegisterGlobalCallback(c1);
        config.RemoteConfigRegisterGlobalCallback(c2);
        config.RemoteConfigRegisterGlobalCallback(c0);
        config.immediateRequestGenerator = new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> callback.callback(null);
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> callback.callback(null);
            }
        };

        Countly countly = new Countly().init(config);

        //check initial global ones
        Assert.assertEquals(1, resArray[0]);
        Assert.assertEquals(1, resArray[1]);
        Assert.assertEquals(1, resArray[2]);
        Assert.assertEquals(0, resArray[3]);

        countly.remoteConfig().removeDownloadCallback(c1);
        countly.remoteConfig().registerDownloadCallback(c3);

        countly.remoteConfig().downloadAllKeys(c4);
        Assert.assertEquals(2, resArray[0]);
        Assert.assertEquals(1, resArray[1]);
        Assert.assertEquals(2, resArray[2]);
        Assert.assertEquals(1, resArray[3]);
        Assert.assertEquals(1, resArray[4]);

        countly.remoteConfig().downloadSpecificKeys(new String[] {}, c5);
        Assert.assertEquals(3, resArray[0]);
        Assert.assertEquals(1, resArray[1]);
        Assert.assertEquals(3, resArray[2]);
        Assert.assertEquals(2, resArray[3]);
        Assert.assertEquals(1, resArray[4]);
        Assert.assertEquals(1, resArray[5]);

        countly.remoteConfig().removeDownloadCallback(c1);
        countly.remoteConfig().downloadOmittingKeys(new String[] {}, c6);
        Assert.assertEquals(4, resArray[0]);
        Assert.assertEquals(1, resArray[1]);
        Assert.assertEquals(4, resArray[2]);
        Assert.assertEquals(3, resArray[3]);
        Assert.assertEquals(1, resArray[4]);
        Assert.assertEquals(1, resArray[5]);
        Assert.assertEquals(1, resArray[6]);
    }

    /**
     * Validate that the getters return the correct values when putting them directly into storage
     *
     * @throws JSONException
     */
    @Test
    public void validateGetters() throws JSONException {
        CountlyConfig config = TestUtils.withCrashReporting(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true));
        config.enableRemoteConfigValueCaching();
        Countly countly = new Countly().init(config);

        //set RC
        JSONArray jArrI = new JSONArray("[3,\"44\",5.1,7.7]");
        JSONObject jObjI = new JSONObject("{\"q\":6,\"w\":\"op\"}");
        String[] rcArr = new String[] { rcEStr("a", 123, false), rcEStr("b", "fg"), rcEStr("c", 222222222222L, false), rcEStr("d", 1.5d), rcEStr("e", jArrI, false), rcEStr("f", jObjI) };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());

        Assert.assertEquals(123, countly.remoteConfig().getValue("a").value);
        Assert.assertEquals(123, countly.remoteConfig().getValueAndEnroll("a").value);
        Assert.assertFalse(countly.remoteConfig().getValue("a").isCurrentUsersData);

        Assert.assertEquals("fg", countly.remoteConfig().getValue("b").value);
        Assert.assertEquals("fg", countly.remoteConfig().getValueAndEnroll("b").value);
        Assert.assertTrue(countly.remoteConfig().getValue("b").isCurrentUsersData);

        Assert.assertEquals(222_222_222_222L, countly.remoteConfig().getValue("c").value);
        Assert.assertEquals(222_222_222_222L, countly.remoteConfig().getValueAndEnroll("c").value);
        Assert.assertFalse(countly.remoteConfig().getValue("c").isCurrentUsersData);

        Assert.assertEquals(1.5d, countly.remoteConfig().getValue("d").value);
        Assert.assertEquals(1.5d, countly.remoteConfig().getValueAndEnroll("d").value);
        Assert.assertTrue(countly.remoteConfig().getValue("d").isCurrentUsersData);

        Assert.assertEquals(jArrI.toString(), countly.remoteConfig().getValue("e").value.toString());
        Assert.assertEquals(jArrI.toString(), countly.remoteConfig().getValueAndEnroll("e").value.toString());
        Assert.assertFalse(countly.remoteConfig().getValue("e").isCurrentUsersData);

        Assert.assertEquals(jObjI.toString(), countly.remoteConfig().getValue("f").value.toString());
        Assert.assertEquals(jObjI.toString(), countly.remoteConfig().getValueAndEnroll("f").value.toString());
        Assert.assertTrue(countly.remoteConfig().getValue("f").isCurrentUsersData);

        Map<String, RCData> valsNew = countly.remoteConfig().getValues();
        Map<String, RCData> valsNewEnroll = countly.remoteConfig().getAllValuesAndEnroll();

        Assert.assertEquals(6, valsNew.size());
        Assert.assertEquals(valsNew.size(), valsNewEnroll.size());

        for (Map.Entry<String, RCData> entry : valsNewEnroll.entrySet()) {
            Object valE = entry.getValue().value;
            Object valN = valsNew.get(entry.getKey()).value;

            if (valE instanceof JSONObject || valE instanceof JSONArray) {
                Assert.assertEquals(valN.toString(), valE.toString());
            } else {
                Assert.assertEquals(valN, valE);
            }
        }
    }

    /**
     * Just making sure nothing explodes when passing bad values
     */
    @Test
    public void passingBadValues() {
        CountlyConfig config = TestUtils.withCrashReporting(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true));
        config.enableRemoteConfigValueCaching();
        config.enableRemoteConfigAutomaticTriggers();
        Countly countly = new Countly().init(config);

        countly.remoteConfig().getValue(null);
        countly.remoteConfig().getValue("");
        countly.remoteConfig().getValueAndEnroll(null);
        countly.remoteConfig().getValueAndEnroll("");

        countly.remoteConfig().downloadAllKeys(null);

        countly.remoteConfig().downloadOmittingKeys(null, null);
        countly.remoteConfig().downloadOmittingKeys(new String[] {}, null);

        countly.remoteConfig().downloadSpecificKeys(null, null);
        countly.remoteConfig().downloadSpecificKeys(new String[] {}, null);

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
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        Countly countly = new Countly();
        countly.init(cc);

        RemoteConfigValueStore rcvs1 = RemoteConfigValueStore.dataFromString("{\"a\": 123,\"b\": \"fg\"}", false, new ModuleLog());
        RemoteConfigValueStore rcvs2 = RemoteConfigValueStore.dataFromString("{\"b\": 33.44,\"c\": \"ww\"}", false, new ModuleLog());
        RemoteConfigValueStore rcvs3 = RemoteConfigValueStore.dataFromString("{\"t\": {},\"87\": \"yy\"}", false, new ModuleLog());

        //check initial state
        Map<String, RCData> vals = countly.remoteConfig().getValues();
        Assert.assertNotNull(vals);
        Assert.assertEquals(0, vals.size());

        //add first values without clearing
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(false, RemoteConfigHelper.DownloadedValuesIntoMap(rcvs1.values, new ModuleLog()));

        vals = countly.remoteConfig().getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(123, vals.get("a").value);
        Assert.assertEquals("fg", vals.get("b").value);

        //add second pair of values without clearing
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(false, RemoteConfigHelper.DownloadedValuesIntoMap(rcvs2.values, new ModuleLog()));

        vals = countly.remoteConfig().getValues();
        Assert.assertEquals(3, vals.size());
        Assert.assertEquals(123, vals.get("a").value);
        Assert.assertEquals(33.44, vals.get("b").value);
        Assert.assertEquals("ww", vals.get("c").value);

        //add third pair with full clear
        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(true, RemoteConfigHelper.DownloadedValuesIntoMap(rcvs3.values, new ModuleLog()));

        vals = countly.remoteConfig().getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals("yy", vals.get("87").value);
        Assert.assertNotNull(vals.get("t").value);
        Assert.assertEquals(0, ((JSONObject) vals.get("t").value).length());
    }

    /**
     * Concurrency test: simulate rapid merge operations while concurrently reading values
     * to ensure no ConcurrentModificationException or data corruption occurs after introducing
     * fine-grained remoteConfigLock and CopyOnWriteArrayList for callbacks.
     */
    @Test
    public void concurrentMergeAndReads() throws Exception {
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("dev1").setLoggingEnabled(true);
        cc.immediateRequestGenerator = new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() { return (a,b,c,d,e,f,g) -> { if (f!=null) f.callback(null); }; }
            @Override public ImmediateRequestI CreatePreflightRequestMaker() { return (a,b,c,d,e,f,g) -> { if (f!=null) f.callback(null); }; }
        };
        Countly countly = new Countly().init(cc);

        RemoteConfigValueStore rcvsA = RemoteConfigValueStore.dataFromString("{\"k1\":123,\"k2\":\"v2\"}", false, new ModuleLog());
        RemoteConfigValueStore rcvsB = RemoteConfigValueStore.dataFromString("{\"k2\":777,\"k3\":{}}", false, new ModuleLog());
        RemoteConfigValueStore rcvsC = RemoteConfigValueStore.dataFromString("{\"k4\":true,\"k5\":55.5}", false, new ModuleLog());

        RemoteConfigValueStore[] arr = new RemoteConfigValueStore[] { rcvsA, rcvsB, rcvsC };

        final int MERGE_THREADS = 3;
        final int READ_THREADS = 4;
        final int MERGE_OPS = 150;
        final int READ_OPS = 600;

        Thread[] mergers = new Thread[MERGE_THREADS];
        Thread[] readers = new Thread[READ_THREADS];
        final Exception[] failure = { null };

        for (int i = 0; i < MERGE_THREADS; i++) {
            final int idx = i;
            mergers[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < MERGE_OPS; j++) {
                        RemoteConfigValueStore pick = arr[(idx + j) % arr.length];
                        boolean clear = (j % 10) == 0; // occasionally force a clear path
                        countly.moduleRemoteConfig.mergeCheckResponseIntoCurrentValues(clear, RemoteConfigHelper.DownloadedValuesIntoMap(pick.values, new ModuleLog()));
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, "RC-Merger-" + i);
            mergers[i].start();
        }

        for (int i = 0; i < READ_THREADS; i++) {
            readers[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < READ_OPS; j++) {
                        countly.remoteConfig().getValue("k1");
                        countly.remoteConfig().getValues();
                        countly.remoteConfig().getAllValuesAndEnroll();
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, "RC-Reader-" + i);
            readers[i].start();
        }

        for (Thread t : mergers) { t.join(); }
        for (Thread t : readers) { t.join(); }

        if (failure[0] != null) {
            Assert.fail("Concurrency test failure: " + failure[0]);
        }

        // resulting map should not contain partially written entries 
        Map<String, RCData> finalVals = countly.remoteConfig().getValues();
        for (Map.Entry<String, RCData> e : finalVals.entrySet()) {
            Assert.assertNotNull("Null RCData encountered for key=" + e.getKey(), e.getValue());
        }
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

    /**
     * A download whose response arrives after halt() belongs to the user the instance had, and halt() erased the store.
     * The response is dropped and the caller is told, so nothing lands in the store the next init reads.
     */
    @Test
    public void downloadResponseAfterHalt_isDroppedAndReportedAsError() throws JSONException {
        final ImmediateRequestMaker.InternalImmediateRequestCallback[] pending = { null };
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.immediateRequestGenerator = new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                    if (requestData.contains("method=rc")) {
                        pending[0] = callback;
                    }
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                };
            }
        };
        Countly countly = new Countly().init(config);

        final RequestResult[] result = { null };
        final String[] error = { null };
        countly.remoteConfig().downloadAllKeys((rResult, rError, fullValueUpdate, downloadedValues) -> {
            result[0] = rResult;
            error[0] = rError;
        });
        Assert.assertNotNull("the download must still be in flight", pending[0]);
        Assert.assertNull(result[0]);

        countly.halt();
        pending[0].callback(new JSONObject("{\"a\":123}"));

        Assert.assertEquals(RequestResult.Error, result[0]);
        Assert.assertNotNull(error[0]);
        String stored = countlyStore.getRemoteConfigValues();
        Assert.assertTrue("nothing may land in the erased store, found:[" + stored + "]", stored == null || stored.isEmpty());
    }

    /**
     * A callback registered on the main thread after a download finished is called once before the register call
     * returns, with every stored value as a full update. Registering downloads nothing.
     */
    @Test
    public void registerDownloadCallbackAndReplay_afterDownload_callsItAtOnceWithStoredValues() {
        RCServerMock server = new RCServerMock("{\"a\":123,\"b\":\"fg\"}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server).enableRemoteConfigAutomaticTriggers());
        Assert.assertEquals(1, server.rcRequests);

        RecordingRCCallback late = new RecordingRCCallback();
        int[] callsWhenReturned = { -1 };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            countly.remoteConfig().registerDownloadCallbackAndReplay(late);
            callsWhenReturned[0] = late.calls();
        });

        Assert.assertEquals(1, callsWhenReturned[0]);
        Assert.assertEquals(1, late.calls());
        assertHandedOver(late, 0, TestUtils.map("a", 123, "b", "fg"));
        Assert.assertSame(Looper.getMainLooper().getThread(), late.threads.get(0));
        Assert.assertEquals(1, server.rcRequests);
    }

    /**
     * Called off the main thread, the registration and the first call happen on the main thread. The callback then
     * hears later downloads as usual.
     */
    @Test
    public void registerDownloadCallbackAndReplay_offTheMainThread_handsOverOnTheMainThread() {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server).enableRemoteConfigAutomaticTriggers());

        RecordingRCCallback callback = new RecordingRCCallback();
        countly.remoteConfig().registerDownloadCallbackAndReplay(callback);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Assert.assertEquals(1, callback.calls());
        assertHandedOver(callback, 0, TestUtils.map("a", 1));
        Assert.assertSame(Looper.getMainLooper().getThread(), callback.threads.get(0));

        server.response = "{\"a\":2}";
        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(2, callback.calls());
        assertHandedOver(callback, 1, TestUtils.map("a", 2));
    }

    /**
     * A download that finishes while a registration from another thread waits for the main thread is neither lost
     * nor reported twice: the waiting registration hands its values over once.
     */
    @Test
    public void registerDownloadCallbackAndReplay_downloadWhileTheRegistrationWaits_handsOverOnce() throws Exception {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server).enableRemoteConfigAutomaticTriggers());
        RecordingRCCallback callback = new RecordingRCCallback();

        CountDownLatch releaseMainThread = blockMainThread();
        countly.remoteConfig().registerDownloadCallbackAndReplay(callback);
        server.response = "{\"a\":2}";
        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(0, callback.calls());
        releaseMainThread.countDown();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Assert.assertEquals(1, callback.calls());
        assertHandedOver(callback, 0, TestUtils.map("a", 2));
    }

    /**
     * Removing the token while its registration waits for the main thread cancels the registration, and leaves an
     * earlier registration of the same callback in place.
     */
    @Test
    public void registerDownloadCallbackAndReplay_removedWhileTheRegistrationWaits_isNeverRegistered() throws Exception {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server).enableRemoteConfigAutomaticTriggers());
        RecordingRCCallback callback = new RecordingRCCallback();
        countly.remoteConfig().registerDownloadCallback(callback);

        CountDownLatch releaseMainThread = blockMainThread();
        RCCallbackToken token = countly.remoteConfig().registerDownloadCallbackAndReplay(callback);
        token.remove();
        releaseMainThread.countDown();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Assert.assertEquals(0, callback.calls());

        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(1, callback.calls());
    }

    /**
     * Downloads of specific keys alone are no download of all values: the store may still hold values of an earlier
     * run, so nothing is handed over until all keys are downloaded.
     */
    @Test
    public void registerDownloadCallbackAndReplay_afterPartialDownloadsOnly_waitsForAFullDownload() {
        String[] rcArr = { rcEStr("a", 123), rcEStr("b", "fg") };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());
        RCServerMock server = new RCServerMock("{\"c\":3}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server));
        countly.remoteConfig().downloadSpecificKeys(new String[] { "c" }, null);
        countly.remoteConfig().downloadOmittingKeys(new String[] { "a" }, null);
        Assert.assertEquals(3, countly.remoteConfig().getValues().size());
        assertNoReplay(countly);

        server.response = "{\"d\":4}";
        countly.remoteConfig().downloadAllKeys(null);
        RecordingRCCallback callback = new RecordingRCCallback();
        registerOnMainThread(countly, callback);
        Assert.assertEquals(1, callback.calls());
        assertHandedOver(callback, 0, TestUtils.map("d", 4));
    }

    /**
     * Values stored by an earlier run are no finished download: the callback is not called when it is registered,
     * and the next download calls it once with the downloaded values.
     */
    @Test
    public void registerDownloadCallbackAndReplay_storedValuesWithoutDownload_waitsForTheDownload() {
        String[] rcArr = { rcEStr("a", 123), rcEStr("b", "fg") };
        countlyStore.setRemoteConfigValues(RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false, new ModuleLog()).dataToString());
        RCServerMock server = new RCServerMock("{\"c\":5}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server));

        RecordingRCCallback callback = new RecordingRCCallback();
        registerOnMainThread(countly, callback);
        Assert.assertEquals(0, callback.calls());
        Assert.assertEquals(2, countly.remoteConfig().getValues().size());

        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(1, callback.calls());
        assertHandedOver(callback, 0, TestUtils.map("c", 5));
    }

    /**
     * A failed download is no finished download: nothing is handed over until a download succeeds.
     */
    @Test
    public void registerDownloadCallbackAndReplay_afterFailedDownload_waitsForASuccessfulOne() {
        RCServerMock server = new RCServerMock(null);
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server).enableRemoteConfigAutomaticTriggers());
        Assert.assertEquals(1, server.rcRequests);

        RecordingRCCallback callback = new RecordingRCCallback();
        registerOnMainThread(countly, callback);
        Assert.assertEquals(0, callback.calls());

        server.response = "{\"a\":1}";
        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(1, callback.calls());
        assertHandedOver(callback, 0, TestUtils.map("a", 1));
    }

    /**
     * A download that fails after a successful one leaves the stored values in place, and a callback registered then
     * gets those values.
     */
    @Test
    public void registerDownloadCallbackAndReplay_failureAfterSuccess_handsOverTheStoredValues() {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server));
        countly.remoteConfig().downloadAllKeys(null);

        server.response = null;
        RecordingRCCallback failedDownload = new RecordingRCCallback();
        countly.remoteConfig().downloadAllKeys(failedDownload);
        Assert.assertEquals(RequestResult.Error, failedDownload.results.get(0));

        RecordingRCCallback callback = new RecordingRCCallback();
        registerOnMainThread(countly, callback);
        Assert.assertEquals(1, callback.calls());
        assertHandedOver(callback, 0, TestUtils.map("a", 1));
    }

    /**
     * After a download of some keys, the callback gets every stored value as a full update, not only the keys of the
     * last download.
     */
    @Test
    public void registerDownloadCallbackAndReplay_afterPartialDownload_handsOverAllStoredValues() {
        RCServerMock server = new RCServerMock("{\"a\":1,\"b\":\"x\"}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server));
        countly.remoteConfig().downloadAllKeys(null);
        server.response = "{\"a\":2}";
        countly.remoteConfig().downloadSpecificKeys(new String[] { "a" }, null);

        RecordingRCCallback callback = new RecordingRCCallback();
        registerOnMainThread(countly, callback);

        Assert.assertEquals(1, callback.calls());
        assertHandedOver(callback, 0, TestUtils.map("a", 2, "b", "x"));
    }

    /**
     * Registering calls only the new callback. Callbacks registered earlier, at init or with "registerDownloadCallback",
     * are not called again, and later downloads reach all of them.
     */
    @Test
    public void registerDownloadCallbackAndReplay_callsOnlyTheNewCallback() {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        RecordingRCCallback global = new RecordingRCCallback();
        CountlyConfig config = TestUtils.createIRGeneratorConfig(server).enableRemoteConfigAutomaticTriggers();
        config.RemoteConfigRegisterGlobalCallback(global);
        Countly countly = new Countly().init(config);
        RecordingRCCallback plain = new RecordingRCCallback();
        countly.remoteConfig().registerDownloadCallback(plain);
        Assert.assertEquals(1, global.calls());
        Assert.assertEquals(0, plain.calls());

        RecordingRCCallback replayed = new RecordingRCCallback();
        registerOnMainThread(countly, replayed);
        Assert.assertEquals(1, global.calls());
        Assert.assertEquals(0, plain.calls());
        Assert.assertEquals(1, replayed.calls());

        server.response = "{\"a\":2}";
        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(2, global.calls());
        Assert.assertEquals(1, plain.calls());
        Assert.assertEquals(2, replayed.calls());
        assertHandedOver(replayed, 1, TestUtils.map("a", 2));
    }

    /**
     * Each token removes its own registration once. Removing twice leaves a second registration of the same callback
     * in place, and a removed callback hears no later download.
     */
    @Test
    public void registerDownloadCallbackAndReplay_tokenRemovesItsOwnRegistrationOnce() {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server));
        RecordingRCCallback callback = new RecordingRCCallback();
        RCCallbackToken first = registerOnMainThread(countly, callback);
        RCCallbackToken second = registerOnMainThread(countly, callback);

        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(2, callback.calls());

        first.remove();
        first.remove();
        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(3, callback.calls());

        second.remove();
        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(3, callback.calls());
    }

    /**
     * Clearing the values, changing the device ID with or without merge and entering temporary ID mode each mean the
     * stored values are no longer the current user's download, so nothing is handed over until the next download of
     * all keys. A download of specific keys meanwhile leaves the cached values in place and does not change that.
     */
    @Test
    public void registerDownloadCallbackAndReplay_afterClearOrUserChange_waitsForTheNextDownload() {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server).enableRemoteConfigValueCaching());

        countly.remoteConfig().downloadAllKeys(null);
        countly.remoteConfig().clearAll();
        assertNoReplay(countly);

        countly.remoteConfig().downloadAllKeys(null);
        RecordingRCCallback afterNextDownload = new RecordingRCCallback();
        registerOnMainThread(countly, afterNextDownload).remove();
        Assert.assertEquals(1, afterNextDownload.calls());
        assertHandedOver(afterNextDownload, 0, TestUtils.map("a", 1));

        countly.deviceId().changeWithoutMerge("other");
        Assert.assertFalse(countly.remoteConfig().getValue("a").isCurrentUsersData);
        assertNoReplay(countly);

        server.response = "{\"b\":2}";
        countly.remoteConfig().downloadSpecificKeys(new String[] { "b" }, null);
        Assert.assertFalse(countly.remoteConfig().getValue("a").isCurrentUsersData);
        assertNoReplay(countly);

        server.response = "{\"a\":1}";
        countly.remoteConfig().downloadAllKeys(null);
        countly.deviceId().changeWithMerge("merged");
        assertNoReplay(countly);

        countly.remoteConfig().downloadAllKeys(null);
        countly.deviceId().enableTemporaryIdMode();
        assertNoReplay(countly);
    }

    /**
     * Without remote config consent nothing is downloaded, so nothing is handed over. Removing the consent later keeps
     * the stored values readable, and a callback registered then still gets them, without a download.
     */
    @Test
    public void registerDownloadCallbackAndReplay_consent() {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        CountlyConfig config = TestUtils.createIRGeneratorConfig(server).enableRemoteConfigAutomaticTriggers().setRequiresConsent(true);
        Countly countly = new Countly().init(config);
        Assert.assertEquals(0, server.rcRequests);

        RecordingRCCallback beforeConsent = new RecordingRCCallback();
        registerOnMainThread(countly, beforeConsent);
        Assert.assertEquals(0, beforeConsent.calls());

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.remoteConfig });
        Assert.assertEquals(1, server.rcRequests);
        Assert.assertEquals(1, beforeConsent.calls());

        countly.consent().removeConsent(new String[] { Countly.CountlyFeatureNames.remoteConfig });
        RecordingRCCallback afterRemoval = new RecordingRCCallback();
        registerOnMainThread(countly, afterRemoval);
        Assert.assertEquals(1, afterRemoval.calls());
        assertHandedOver(afterRemoval, 0, TestUtils.map("a", 1));
        Assert.assertEquals(1, server.rcRequests);
    }

    /**
     * A null callback is ignored: its token removes nothing and later downloads still reach the other callbacks.
     */
    @Test
    public void registerDownloadCallbackAndReplay_nullCallback_isIgnored() {
        RCServerMock server = new RCServerMock("{\"a\":1}");
        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(server));
        countly.remoteConfig().downloadAllKeys(null);
        RecordingRCCallback other = new RecordingRCCallback();
        countly.remoteConfig().registerDownloadCallback(other);

        RCCallbackToken token = countly.remoteConfig().registerDownloadCallbackAndReplay(null);
        Assert.assertNotNull(token);
        token.remove();

        countly.remoteConfig().downloadAllKeys(null);
        Assert.assertEquals(1, other.calls());
    }

    /**
     * End to end against a local server: the automatic download at init stores the served values, a callback
     * registered afterwards gets them at once, and the server sees exactly one remote config request.
     */
    @Test
    public void registerDownloadCallbackAndReplay_endToEnd() throws Exception {
        final List<RecordedRequest> rcRequests = new CopyOnWriteArrayList<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NonNull @Override public MockResponse dispatch(@NonNull RecordedRequest request) {
                    String body = "{\"result\":\"Success\"}";
                    if (String.valueOf(request.getPath()).contains("method=rc")) {
                        rcRequests.add(request);
                        body = "{\"a\":123,\"b\":\"fg\"}";
                    }
                    return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
                }
            });
            server.start();

            final CountDownLatch downloaded = new CountDownLatch(1);
            CountlyConfig config = TestUtils.createBaseConfig().setServerURL(server.url("/").toString()).disableHealthCheck().enableRemoteConfigAutomaticTriggers();
            config.RemoteConfigRegisterGlobalCallback((downloadResult, error, fullValueUpdate, downloadedValues) -> downloaded.countDown());
            Countly countly = new Countly().init(config);
            Assert.assertTrue("the automatic download must finish", downloaded.await(30, TimeUnit.SECONDS));

            RecordingRCCallback late = new RecordingRCCallback();
            registerOnMainThread(countly, late);

            Assert.assertEquals(1, late.calls());
            assertHandedOver(late, 0, TestUtils.map("a", 123, "b", "fg"));
            Assert.assertEquals(1, rcRequests.size());
            Assert.assertEquals("GET", rcRequests.get(0).getMethod());
            Assert.assertEquals("/o/sdk", rcRequests.get(0).getRequestUrl().encodedPath());
            Assert.assertEquals(TestUtils.commonDeviceId, rcRequests.get(0).getRequestUrl().queryParameter("device_id"));
            Assert.assertEquals(TestUtils.commonAppKey, rcRequests.get(0).getRequestUrl().queryParameter("app_key"));
        }
    }

    /**
     * Calls "registerDownloadCallbackAndReplay" on the main thread, where it hands over the values before it returns.
     */
    static RCCallbackToken registerOnMainThread(Countly countly, RCDownloadCallback callback) {
        RCCallbackToken[] token = { null };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> token[0] = countly.remoteConfig().registerDownloadCallbackAndReplay(callback));
        return token[0];
    }

    /**
     * Keeps the main thread busy until the returned latch is counted down, so work posted to it waits.
     */
    static CountDownLatch blockMainThread() throws InterruptedException {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            blocked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        Assert.assertTrue(blocked.await(10, TimeUnit.SECONDS));
        return release;
    }

    /**
     * Registers a fresh callback with "registerDownloadCallbackAndReplay", asserts it was not called, and removes it.
     */
    static void assertNoReplay(Countly countly) {
        RecordingRCCallback callback = new RecordingRCCallback();
        registerOnMainThread(countly, callback).remove();
        Assert.assertEquals(0, callback.calls());
    }

    /**
     * Asserts that one recorded call was a successful full update without error that carried exactly the expected
     * values, all of them the current user's.
     */
    static void assertHandedOver(RecordingRCCallback callback, int index, Map<String, Object> expectedValues) {
        Assert.assertEquals(RequestResult.Success, callback.results.get(index));
        Assert.assertNull(callback.errors.get(index));
        Assert.assertTrue(callback.fullValueUpdates.get(index));
        Map<String, RCData> values = callback.values.get(index);
        Assert.assertEquals(expectedValues.keySet(), values.keySet());
        for (Map.Entry<String, Object> entry : expectedValues.entrySet()) {
            Assert.assertEquals(entry.getValue(), values.get(entry.getKey()).value);
        }
        assertCValueCachedState(values, false);
    }

    /**
     * Records every call of a download callback with its arguments and the thread it ran on.
     */
    static class RecordingRCCallback implements RCDownloadCallback {
        final List<RequestResult> results = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<Boolean> fullValueUpdates = new CopyOnWriteArrayList<>();
        final List<Map<String, RCData>> values = new CopyOnWriteArrayList<>();
        final List<Thread> threads = new CopyOnWriteArrayList<>();

        @Override public void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, Map<String, RCData> downloadedValues) {
            results.add(downloadResult);
            errors.add(error);
            fullValueUpdates.add(fullValueUpdate);
            values.add(downloadedValues);
            threads.add(Thread.currentThread());
        }

        /**
         * @return how many times the callback was called
         */
        int calls() {
            return results.size();
        }
    }

    /**
     * Answers every remote config download at once with "response", where null stands for an unreachable server, and
     * counts them. Every other immediate request is left unanswered.
     */
    static class RCServerMock implements ImmediateRequestGenerator {
        String response;
        int rcRequests = 0;

        /**
         * @param response the JSON the remote config downloads get, null for an unreachable server
         */
        RCServerMock(String response) {
            this.response = response;
        }

        @Override public ImmediateRequestI CreateImmediateRequestMaker() {
            return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                if (!requestData.contains("method=rc")) {
                    return;
                }
                rcRequests++;
                if (response == null) {
                    callback.callback(null);
                    return;
                }
                try {
                    callback.callback(new JSONObject(response));
                } catch (JSONException e) {
                    throw new IllegalArgumentException(e);
                }
            };
        }

        @Override public ImmediateRequestI CreatePreflightRequestMaker() {
            return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
            };
        }
    }
}
