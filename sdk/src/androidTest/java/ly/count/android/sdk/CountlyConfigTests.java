package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class CountlyConfigTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @Test
    public void constructor() {
        Context ctx = TestUtils.getContext();
        CountlyConfig config = new CountlyConfig(ctx, "Som345345", "fsdf7349374");

        Assert.assertEquals(ctx, config.context);
        Assert.assertEquals("Som345345", config.appKey);
        Assert.assertEquals("fsdf7349374", config.serverURL);

        assertDefaultValues(config, false);
    }

    @Test
    public void settingAllValues() {
        String[] s = { "4234234234ff", "sssa2323", "sds", "sdfsdf232", "aa22", "xvcx", "hghn", "0gifg", "kfkfdd" };
        Context c = TestUtils.getContext();
        CountlyConfig config = new CountlyConfig();
        CountlyStore cs = new CountlyStore(c, mock(ModuleLog.class));

        StarRatingCallback rc = new StarRatingCallback() {
            @Override
            public void onRate(int rating) {

            }

            @Override
            public void onDismiss() {

            }
        };

        RemoteConfigCallback rcc2 = new RemoteConfigCallback() {
            @Override public void callback(String error) {

            }
        };

        Map<String, String> hv = new HashMap<>();
        hv.put("11", "22");
        hv.put("1331", "2332");

        String[] fn = { "ds dsd", "434f", "ngfhg" };

        CrashFilterCallback callback = new CrashFilterCallback() {
            @Override
            public boolean filterCrash(String crash) {
                return false;
            }
        };

        Map<String, Object> vs = new HashMap<>();
        vs.put("ss", "fdf");
        vs.put("s22s", 2323);
        vs.put("s44s", 33_434.33d);
        vs.put("dds44s", true);

        Class[] act = { Activity.class };

        String[] appCrawlerNames = { "Some", "Crazy", "name" };

        String[] publicKeyCerts = { "ddd", "111", "ffd" };
        String[] certificateCerts = { "ddsd", "vvcv", "mbnb" };

        Map<String, Object> crashSegments = new HashMap<>();
        crashSegments.put("s2s", "fdf");
        crashSegments.put("s224s", 2323);
        crashSegments.put("s434s", 33_434.33d);
        crashSegments.put("ddsa44s", true);

        Map<String, String> metricOverride = new HashMap<>();
        metricOverride.put("SomeKey", "123");
        metricOverride.put("_Carrier", "BoneyK");

        ModuleLog.LogCallback logCallback = new ModuleLog.LogCallback() {
            @Override public void LogHappened(String logMessage, ModuleLog.LogLevel logLevel) {

            }
        };

        Application app = new Application();

        assertDefaultValues(config, true);

        config.setServerURL(s[0]);
        config.setContext(c);
        config.setAppKey(s[1]);
        config.setCountlyStore(cs);
        config.checkForNativeCrashDumps(false);
        config.setDeviceId(s[2]);
        config.setIdMode(DeviceIdType.DEVELOPER_SUPPLIED);
        config.setStarRatingSessionLimit(1335);
        config.setStarRatingCallback(rc);
        config.setStarRatingTextDismiss(s[3]);
        config.setStarRatingTextMessage(s[4]);
        config.setStarRatingTextTitle(s[5]);
        config.setLoggingEnabled(true);
        config.enableCrashReporting();
        config.setViewTracking(true);
        config.setAutoTrackingUseShortName(true);
        config.setEnableAutoViewStartStop(true);
        config.addCustomNetworkRequestHeaders(hv);
        config.setPushIntentAddMetadata(true);
        config.setRemoteConfigAutomaticDownload(true, rcc2);
        config.setRequiresConsent(true);
        config.setConsentEnabled(fn);
        config.setHttpPostForced(true);
        config.enableTemporaryDeviceIdMode();
        config.setCrashFilterCallback(callback);
        config.setParameterTamperingProtectionSalt(s[6]);
        config.setAutomaticViewSegmentation(vs);
        config.setAutoTrackingExceptions(act);
        config.setTrackOrientationChanges(false);
        config.setEventQueueSizeToSend(1337);
        config.enableManualSessionControl();
        config.setRecordAllThreadsWithCrash();
        config.setDisableUpdateSessionRequests(true);
        config.setShouldIgnoreAppCrawlers(true);
        config.setAppCrawlerNames(appCrawlerNames);
        config.enableCertificatePinning(certificateCerts);
        config.enablePublicKeyPinning(publicKeyCerts);
        config.setEnableAttribution(true);
        config.setCustomCrashSegment(crashSegments);
        config.setUpdateSessionTimerDelay(137);
        config.setIfStarRatingDialogIsCancellable(true);
        config.setIfStarRatingShownAutomatically(true);
        config.setStarRatingDisableAskingForEachAppVersion(true);
        config.setApplication(app);
        config.setRecordAppStartTime(true);
        config.setDisableLocation();
        config.setLocation("CC", "city", "loc", "ip");
        config.setMetricOverride(metricOverride);
        config.setAppStartTimestampOverride(123L);
        config.enableManualAppLoadedTrigger();
        config.enableManualForegroundBackgroundTriggerAPM();
        config.setLogListener(logCallback);

        Assert.assertEquals(s[0], config.serverURL);
        Assert.assertEquals(c, config.context);
        Assert.assertEquals(s[1], config.appKey);
        Assert.assertEquals(cs, config.countlyStore);
        Assert.assertTrue(config.crashes.checkForNativeCrashDumps); // this will always be true, SDK will always check for native crash dumps
        Assert.assertEquals(s[2], config.deviceID);
        Assert.assertEquals(1335, config.starRatingSessionLimit);
        Assert.assertEquals(rc, config.starRatingCallback);
        Assert.assertEquals(s[3], config.starRatingTextDismiss);
        Assert.assertEquals(s[4], config.starRatingTextMessage);
        Assert.assertEquals(s[5], config.starRatingTextTitle);
        Assert.assertTrue(config.loggingEnabled);
        Assert.assertTrue(config.crashes.enableUnhandledCrashReporting);
        Assert.assertTrue(config.enableAutomaticViewTracking);
        Assert.assertTrue(config.autoTrackingUseShortName);
        Assert.assertTrue(config.enableAutoViewStartStop);
        Assert.assertEquals(hv, config.customNetworkRequestHeaders);
        Assert.assertTrue(config.pushIntentAddMetadata);
        Assert.assertTrue(config.enableRemoteConfigAutomaticDownloadTriggers);
        Assert.assertEquals(rcc2, config.remoteConfigCallbackLegacy);
        Assert.assertTrue(config.shouldRequireConsent);
        Assert.assertArrayEquals(fn, config.enabledFeatureNames);
        Assert.assertTrue(config.httpPostForced);
        Assert.assertTrue(config.temporaryDeviceIdEnabled);
        Assert.assertEquals(callback, config.crashFilterCallback);
        Assert.assertEquals(s[6], config.tamperingProtectionSalt);
        Assert.assertEquals(vs, config.globalViewSegmentation);
        Assert.assertArrayEquals(act, config.automaticViewTrackingExceptions);
        Assert.assertFalse(config.trackOrientationChange);
        Assert.assertEquals(1337, config.eventQueueSizeThreshold.intValue());
        Assert.assertTrue(config.manualSessionControlEnabled);
        Assert.assertTrue(config.crashes.recordAllThreadsWithCrash);
        Assert.assertTrue(config.disableUpdateSessionRequests);
        Assert.assertTrue(config.shouldIgnoreAppCrawlers);
        Assert.assertArrayEquals(appCrawlerNames, config.appCrawlerNames);
        Assert.assertArrayEquals(certificateCerts, config.certificatePinningCertificates);
        Assert.assertArrayEquals(publicKeyCerts, config.publicKeyPinningCertificates);
        Assert.assertEquals(crashSegments, config.crashes.customCrashSegment);
        Assert.assertEquals(137, config.sessionUpdateTimerDelay.intValue());
        Assert.assertTrue(config.starRatingDialogIsCancellable);
        Assert.assertTrue(config.starRatingShownAutomatically);
        Assert.assertTrue(config.starRatingDisableAskingForEachAppVersion);
        Assert.assertEquals(app, config.application);
        Assert.assertTrue(config.apm.trackAppStartTime);
        Assert.assertTrue(config.disableLocation);
        Assert.assertEquals("CC", config.locationCountyCode);
        Assert.assertEquals("city", config.locationCity);
        Assert.assertEquals("loc", config.locationLocation);
        Assert.assertEquals("ip", config.locationIpAddress);
        Assert.assertEquals(metricOverride, config.metricOverride);
        Assert.assertEquals((Long) 123l, config.apm.appStartTimestampOverride);
        Assert.assertTrue(config.apm.appLoadedManualTrigger);
        Assert.assertTrue(config.apm.manualForegroundBackgroundTrigger);
        Assert.assertEquals(logCallback, config.providedLogCallback);

        config.setLocation("CC", "city", "loc", "ip");
    }

    @Test
    public void defaultValues() {
        CountlyConfig config = new CountlyConfig();

        assertDefaultValues(config, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void autoTrackingExceptionNull() {
        CountlyConfig config = new CountlyConfig();
        config.setAutoTrackingExceptions(new Class[] { null });
    }

    /**
     * "ConfigExperimental"
     * Assert that interface not null and default values are set
     * and using functions will change the values.
     * And using them again will not change the values again.
     */
    @Test
    public void config_testExperimentalInterface() {
        CountlyConfig config = new CountlyConfig();
        Assert.assertNotNull(config.experimental);

        Assert.assertFalse(config.experimental.viewNameRecordingEnabled);
        Assert.assertFalse(config.experimental.visibilityTrackingEnabled);

        config.experimental.enablePreviousNameRecording();
        Assert.assertTrue(config.experimental.viewNameRecordingEnabled);

        config.experimental.enableVisibilityTracking();
        Assert.assertTrue(config.experimental.visibilityTrackingEnabled);

        config.experimental.enablePreviousNameRecording();
        Assert.assertTrue(config.experimental.viewNameRecordingEnabled);

        config.experimental.enableVisibilityTracking();
        Assert.assertTrue(config.experimental.visibilityTrackingEnabled);
    }

    void assertDefaultValues(CountlyConfig config, boolean includeConstructorValues) {
        if (includeConstructorValues) {
            Assert.assertNull(config.context);
            Assert.assertNull(config.serverURL);
            Assert.assertNull(config.appKey);
            Assert.assertNull(config.application);
        }

        Assert.assertNull(config.countlyStore);
        Assert.assertTrue(config.crashes.checkForNativeCrashDumps);
        Assert.assertNull(config.deviceID);
        Assert.assertEquals(5, config.starRatingSessionLimit);
        Assert.assertNull(config.starRatingCallback);
        Assert.assertNull(config.starRatingTextDismiss);
        Assert.assertNull(config.starRatingTextMessage);
        Assert.assertNull(config.starRatingTextTitle);
        Assert.assertFalse(config.loggingEnabled);
        Assert.assertFalse(config.crashes.enableUnhandledCrashReporting);
        Assert.assertFalse(config.enableAutomaticViewTracking);
        Assert.assertFalse(config.autoTrackingUseShortName);
        Assert.assertFalse(config.enableAutoViewStartStop);
        Assert.assertNull(config.customNetworkRequestHeaders);
        Assert.assertFalse(config.pushIntentAddMetadata);
        Assert.assertFalse(config.enableRemoteConfigAutomaticDownloadTriggers);
        Assert.assertNull(config.remoteConfigCallbackLegacy);
        Assert.assertFalse(config.shouldRequireConsent);
        Assert.assertNull(config.enabledFeatureNames);
        Assert.assertFalse(config.httpPostForced);
        Assert.assertFalse(config.temporaryDeviceIdEnabled);
        Assert.assertNull(config.crashes.globalCrashFilterCallback);
        Assert.assertNull(config.tamperingProtectionSalt);
        Assert.assertNull(config.globalViewSegmentation);
        Assert.assertNull(config.eventQueueSizeThreshold);
        Assert.assertTrue(config.trackOrientationChange);
        Assert.assertFalse(config.manualSessionControlEnabled);
        Assert.assertFalse(config.crashes.recordAllThreadsWithCrash);
        Assert.assertFalse(config.disableUpdateSessionRequests);
        Assert.assertFalse(config.shouldIgnoreAppCrawlers);
        Assert.assertNull(config.appCrawlerNames);
        Assert.assertNull(config.publicKeyPinningCertificates);
        Assert.assertNull(config.certificatePinningCertificates);
        Assert.assertNull(config.crashes.customCrashSegment);
        Assert.assertNull(config.sessionUpdateTimerDelay);
        Assert.assertFalse(config.starRatingDialogIsCancellable);
        Assert.assertFalse(config.starRatingShownAutomatically);
        Assert.assertFalse(config.starRatingDisableAskingForEachAppVersion);
        Assert.assertFalse(config.apm.trackAppStartTime);
        Assert.assertFalse(config.disableLocation);
        Assert.assertNull(config.locationCountyCode);
        Assert.assertNull(config.locationCity);
        Assert.assertNull(config.locationLocation);
        Assert.assertNull(config.locationIpAddress);
        Assert.assertNull(config.metricOverride);
        Assert.assertNull(config.apm.appStartTimestampOverride);
        Assert.assertFalse(config.apm.appLoadedManualTrigger);
        Assert.assertFalse(config.apm.manualForegroundBackgroundTrigger);
        Assert.assertNull(config.providedLogCallback);
    }
}
