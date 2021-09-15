package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;

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
        Countly.CountlyFeatureNames.feedback,
    };

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Make sure that all used feature names are valid
     */
    @Test
    public void usingValidFeatureList() {
        Countly mCountly = new Countly();
        Assert.assertEquals(usedFeatureNames.length, ModuleConsent.validFeatureNames.length);

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertEquals(usedFeatureNames[a], ModuleConsent.validFeatureNames[a]);
        }
    }

    /**
     * Test scenario were consent is required but no consent given on init
     */
    @Test
    public void enableConsentWithoutConsentGiven() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setRequiresConsent(true);
        mCountly.init(config);

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertFalse(mCountly.consent().getConsent(usedFeatureNames[a]));
        }
    }

    /**
     * Test scenario were consent is required and consent for all features is given during init
     */
    @Test
    public void enableConsentGiveAll() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setRequiresConsent(true)
            .setConsentEnabled(usedFeatureNames);
        mCountly.init(config);

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertTrue(mCountly.consent().getConsent(usedFeatureNames[a]));
        }
    }

    /**
     * Give consent to all features during init and remove them all afterwards
     */
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

    /**
     * Makes sure all modules have the consent interface set
     */
    @Test
    public void checkIfConsentProviderSet() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setRequiresConsent(true);
        mCountly.init(config);

        for (ModuleBase module : mCountly.modules) {
            Assert.assertEquals(mCountly.moduleConsent, module.consentProvider);
        }

        Assert.assertEquals(mCountly.moduleConsent, mCountly.connectionQueue_.consentProvider);
        Assert.assertEquals(mCountly.moduleConsent, config.consentProvider);
    }

    /**
     * Set all consent values setting them one by one after init
     */
    @Test
    public void checkSettingConsentAfterInit() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setRequiresConsent(true);
        mCountly.init(config);

        Assert.assertFalse(mCountly.moduleConsent.consentProvider.anyConsentGiven());

        for (int a = 0; a < usedFeatureNames.length; a++) {
            mCountly.consent().giveConsent(new String[] { usedFeatureNames[a] });

            for (int b = 0; b < usedFeatureNames.length; b++) {
                if (b <= a) {
                    Assert.assertTrue(mCountly.consent().getConsent(usedFeatureNames[b]));
                } else {
                    Assert.assertFalse(mCountly.consent().getConsent(usedFeatureNames[b]));
                }
            }
        }

        Assert.assertTrue(mCountly.moduleConsent.consentProvider.anyConsentGiven());
    }

    /**
     * Make sure that feature names are not changed by accident
     */
    @Test
    public void validateFeatureNames() {
        Assert.assertEquals("sessions", Countly.CountlyFeatureNames.sessions);
        Assert.assertEquals("events", Countly.CountlyFeatureNames.events);
        Assert.assertEquals("views", Countly.CountlyFeatureNames.views);
        Assert.assertEquals("location", Countly.CountlyFeatureNames.location);
        Assert.assertEquals("crashes", Countly.CountlyFeatureNames.crashes);
        Assert.assertEquals("attribution", Countly.CountlyFeatureNames.attribution);
        Assert.assertEquals("users", Countly.CountlyFeatureNames.users);
        Assert.assertEquals("push", Countly.CountlyFeatureNames.push);
        Assert.assertEquals("star-rating", Countly.CountlyFeatureNames.starRating);
        Assert.assertEquals("apm", Countly.CountlyFeatureNames.apm);
        Assert.assertEquals("feedback", Countly.CountlyFeatureNames.feedback);
        Assert.assertEquals("remote-config", Countly.CountlyFeatureNames.remoteConfig);
    }
}
