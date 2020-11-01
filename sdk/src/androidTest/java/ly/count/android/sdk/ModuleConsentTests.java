package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleConsentTests {

    protected final String[] usedFeatureNames = new String[] {
        Countly.CountlyFeatureNames.sessions,
        Countly.CountlyFeatureNames.events,
        Countly.CountlyFeatureNames.views,
        Countly.CountlyFeatureNames.location,
        Countly.CountlyFeatureNames.crashes,
        Countly.CountlyFeatureNames.attribution,
        Countly.CountlyFeatureNames.users,
        Countly.CountlyFeatureNames.push,
        Countly.CountlyFeatureNames.starRating,
        Countly.CountlyFeatureNames.remoteConfig,
        Countly.CountlyFeatureNames.apm,
        Countly.CountlyFeatureNames.surveys,
    };

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void usingValidFeatureList() {
        Countly mCountly = new Countly();
        Assert.assertEquals(usedFeatureNames.length, mCountly.validFeatureNames.length);

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertEquals(usedFeatureNames[a], mCountly.validFeatureNames[a]);
        }
    }

    @Test
    public void enableConsentWithoutConsentGiven() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setRequiresConsent(true);
        mCountly.init(config);
    }

    @Test
    public void enableConsentGiveAll() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setRequiresConsent(true)
            .setConsentEnabled(usedFeatureNames);
        mCountly.init(config);
    }

    @Test
    public void enableConsentRemoveAfter() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setRequiresConsent(true)
            .setConsentEnabled(usedFeatureNames);
        mCountly.init(config);

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertTrue(mCountly.consent().getConsent(usedFeatureNames[a]));
        }

        mCountly.consent().removeConsentAll();

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertFalse(mCountly.consent().getConsent(usedFeatureNames[a]));
        }

        mCountly.consent().giveConsentAll();

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertTrue(mCountly.consent().getConsent(usedFeatureNames[a]));
        }
    }
}
