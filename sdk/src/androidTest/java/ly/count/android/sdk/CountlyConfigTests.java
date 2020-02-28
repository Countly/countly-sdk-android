package ly.count.android.sdk;

import android.app.Activity;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class CountlyConfigTests {

    @Before
    public void setUp(){
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @Test
    public void constructor(){
        CountlyConfig config = new CountlyConfig(getContext(), "Som345345", "fsdf7349374");

        assertDefaultValues(config, false);
    }

    @Test
    public void settingAllValues() {
        String[] s = new String[]{"4234234234ff", "sssa2323", "sds", "sdfsdf232", "aa22", "xvcx", "hghn", "0gifg", "kfkfdd"};
        Context c = getContext();
        CountlyConfig config = new CountlyConfig();
        CountlyStore cs = new CountlyStore(c);

        CountlyStarRating.RatingCallback rc = new CountlyStarRating.RatingCallback() {
            @Override
            public void onRate(int rating) {

            }

            @Override
            public void onDismiss() {

            }
        };

        RemoteConfig.RemoteConfigCallback rcc = new RemoteConfig.RemoteConfigCallback() {
            @Override
            public void callback(String error) {

            }
        };

        Map<String, String> hv = new HashMap<>();
        hv.put("11", "22");
        hv.put("1331", "2332");

        String[] fn = new String[]{"ds dsd", "434f", "ngfhg"};

        Pattern[] rf = new Pattern[]{Pattern.compile("d dsd"), Pattern.compile("454gf")};

        Map<String, Object> vs = new HashMap<>();
        vs.put("ss", "fdf");
        vs.put("s22s", 2323);
        vs.put("s44s", 33434.33d);

        Class[] act = new Class[]{Activity.class};

        String[] appCrawlerNames = new String[] {"Some", "Crazy", "name"};

        String[] publicKeyCerts = new String[] { "ddd", "111", "ffd" };
        String[] certificateCerts = new String[] { "ddsd", "vvcv", "mbnb" };


        assertDefaultValues(config, true);


        config.setServerURL(s[0]);
        config.setContext(c);
        config.setAppKey(s[1]);
        config.setCountlyStore(cs);
        config.checkForNativeCrashDumps(false);
        config.setDeviceId(s[2]);
        config.setIdMode(DeviceId.Type.ADVERTISING_ID);
        config.setStarRatingLimit(1335);
        config.setStarRatingCallback(rc);
        config.setStarRatingTextDismiss(s[3]);
        config.setStarRatingTextMessage(s[4]);
        config.setStarRatingTextTitle(s[5]);
        config.setLoggingEnabled(true);
        config.enableCrashReporting();
        config.setViewTracking(true);
        config.setAutoTrackingUseShortName(true);
        config.addCustomNetworkRequestHeaders(hv);
        config.setPushIntentAddMetadata(true);
        config.setRemoteConfigAutomaticDownload(true, rcc);
        config.setRequiresConsent(true);
        config.setConsentEnabled(fn);
        config.setHttpPostForced(true);
        config.enableTemporaryDeviceIdMode();
        config.setCrashFilters(rf);
        config.setParameterTamperingProtectionSalt(s[6]);
        config.setAutomaticViewSegmentation(vs);
        config.setAutoTrackingExceptions(act);
        config.setTrackOrientationChanges(true);
        config.setEventQueueSizeToSend(1337);
        config.setRecordAllThreadsWithCrash();
        config.setShouldIgnoreAppCrawlers(true);
        config.setAppCrawlerNames(appCrawlerNames);
        config.enableCertificatePinning(certificateCerts);
        config.enablePublicKeyPinning(publicKeyCerts);
        config.setEnableAttribution(true);



        Assert.assertEquals(s[0], config.serverURL);
        Assert.assertEquals(c, config.context);
        Assert.assertEquals(s[1], config.appKey);
        Assert.assertEquals(cs, config.countlyStore);
        Assert.assertFalse(config.checkForNativeCrashDumps);
        Assert.assertEquals(s[2], config.deviceID);
        Assert.assertEquals(DeviceId.Type.ADVERTISING_ID, config.idMode);
        Assert.assertEquals(1335, config.starRatingLimit);
        Assert.assertEquals(rc, config.starRatingCallback);
        Assert.assertEquals(s[3], config.starRatingTextDismiss);
        Assert.assertEquals(s[4], config.starRatingTextMessage);
        Assert.assertEquals(s[5], config.starRatingTextTitle);
        Assert.assertTrue(config.loggingEnabled);
        Assert.assertTrue(config.enableUnhandledCrashReporting);
        Assert.assertTrue(config.enableViewTracking);
        Assert.assertTrue(config.autoTrackingUseShortName);
        Assert.assertEquals(hv, config.customNetworkRequestHeaders);
        Assert.assertTrue(config.pushIntentAddMetadata);
        Assert.assertTrue(config.enableRemoteConfigAutomaticDownload);
        Assert.assertEquals(rcc, config.remoteConfigCallback);
        Assert.assertTrue(config.shouldRequireConsent);
        Assert.assertArrayEquals(fn, config.enabledFeatureNames);
        Assert.assertTrue(config.httpPostForced);
        Assert.assertTrue(config.temporaryDeviceIdEnabled);
        Assert.assertArrayEquals(rf, config.crashRegexFilters);
        Assert.assertEquals(s[6], config.tamperingProtectionSalt);
        Assert.assertEquals(vs, config.automaticViewSegmentation);
        Assert.assertArrayEquals(act, config.autoTrackingExceptions);
        Assert.assertTrue(config.trackOrientationChange);
        Assert.assertEquals(1337, config.eventQueueSizeThreshold.intValue());
        Assert.assertTrue(config.recordAllThreadsWithCrash);
        Assert.assertTrue(config.shouldIgnoreAppCrawlers);
        Assert.assertArrayEquals(appCrawlerNames, config.appCrawlerNames);
        Assert.assertArrayEquals(certificateCerts, config.certificatePinningCertificates);
        Assert.assertArrayEquals(publicKeyCerts, config.publicKeyPinningCertificates);
        Assert.assertTrue(config.enableAttribution);
    }

    @Test
    public void defaultValues(){
        CountlyConfig config = new CountlyConfig();

        assertDefaultValues(config, true);
    }

    @Test (expected = IllegalArgumentException.class)
    public void autoTrackingExceptionNull() {
        CountlyConfig config = new CountlyConfig();
        config.setAutoTrackingExceptions(new Class[]{null});
    }

    void assertDefaultValues(CountlyConfig config, boolean includeConstructorValues){
        if(includeConstructorValues){
            Assert.assertNull(config.context);
            Assert.assertNull(config.serverURL);
            Assert.assertNull(config.appKey);
        }

        Assert.assertNull(config.countlyStore);
        Assert.assertTrue(config.checkForNativeCrashDumps);
        Assert.assertNull(config.deviceID);
        Assert.assertNull(config.idMode);
        Assert.assertEquals(5, config.starRatingLimit);
        Assert.assertNull(config.starRatingCallback);
        Assert.assertNull(config.starRatingTextDismiss);
        Assert.assertNull(config.starRatingTextMessage);
        Assert.assertNull(config.starRatingTextTitle);
        Assert.assertFalse(config.loggingEnabled);
        Assert.assertFalse(config.enableUnhandledCrashReporting);
        Assert.assertFalse(config.enableViewTracking);
        Assert.assertFalse(config.autoTrackingUseShortName);
        Assert.assertNull(config.customNetworkRequestHeaders);
        Assert.assertFalse(config.pushIntentAddMetadata);
        Assert.assertFalse(config.enableRemoteConfigAutomaticDownload);
        Assert.assertNull(config.remoteConfigCallback);
        Assert.assertFalse(config.shouldRequireConsent);
        Assert.assertNull(config.enabledFeatureNames);
        Assert.assertFalse(config.httpPostForced);
        Assert.assertFalse(config.temporaryDeviceIdEnabled);
        Assert.assertNull(config.crashRegexFilters);
        Assert.assertNull(config.tamperingProtectionSalt);
        Assert.assertNull(config.automaticViewSegmentation);
        Assert.assertNull(config.eventQueueSizeThreshold);
        Assert.assertFalse(config.trackOrientationChange);
        Assert.assertFalse(config.recordAllThreadsWithCrash);
        Assert.assertFalse(config.shouldIgnoreAppCrawlers);
        Assert.assertNull(config.appCrawlerNames);
        Assert.assertNull(config.publicKeyPinningCertificates);
        Assert.assertNull(config.certificatePinningCertificates);
        Assert.assertNull(config.enableAttribution);
    }
}
