package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(AndroidJUnit4.class)
public class ModuleConsentTests {

    protected static final String[] usedFeatureNames = {
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
        Countly.CountlyFeatureNames.clicks,
        Countly.CountlyFeatureNames.scrolls,
        Countly.CountlyFeatureNames.content,
    };

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    Countly helperCreateAndInitCountly() {
        return null;
    }

    /**
     * Make sure that all used feature names are valid
     */
    @Test
    public void usingValidFeatureList() {
        Assert.assertEquals(usedFeatureNames.length, ModuleConsent.validFeatureNames.length);

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertEquals(usedFeatureNames[a], ModuleConsent.validFeatureNames[a]);
        }
    }

    /**
     * Test scenario were consent is required but no consent given on init
     * This should create a scenario where "getConsent" returns "false" for all features
     */
    @Test
    public void enableConsentWithoutConsentGiven() {
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, null, null));

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertFalse(mCountly.consent().getConsent(usedFeatureNames[a]));
        }
    }

    /**
     * Test scenario were consent is required and consent for all features is given during init
     * This should create a scenario where "getConsent" returns "true" for all features
     */
    @Test
    public void enableConsentGiveAll() {
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, usedFeatureNames, null));

        for (int a = 0; a < usedFeatureNames.length; a++) {
            Assert.assertTrue(mCountly.consent().getConsent(usedFeatureNames[a]));
        }
    }

    /**
     * Give consent to all features during init and remove them all afterwards
     * Make sure that giving all and removing all correctly toggles the feature state
     */
    @Test
    public void enableConsentRemoveAfter() {
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, usedFeatureNames, null));

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
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, null, null));

        for (ModuleBase module : mCountly.modules) {
            Assert.assertEquals(mCountly.moduleConsent, module.consentProvider);
        }

        Assert.assertEquals(mCountly.moduleConsent, mCountly.connectionQueue_.consentProvider);
        Assert.assertEquals(mCountly.moduleConsent, mCountly.config_.consentProvider);
    }

    /**
     * Set all consent values setting them one by one after init
     * Validate "anyConsentGiven" in a couple of scenarios
     */
    @Test
    public void checkSettingConsentAfterInit() {
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, null, null));

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
        Assert.assertEquals("scrolls", Countly.CountlyFeatureNames.scrolls);
        Assert.assertEquals("clicks", Countly.CountlyFeatureNames.clicks);
    }

    /**
     * Require no consent at init time and provide no consent values.
     * Does not provide any location or anything else
     * No requests should be created.
     * There should be no interactions with the mock
     */
    @Test
    public void initTimeNoConsentRequiredRQ() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(false, null, null, rqp));
        verifyZeroInteractions(rqp);
    }

    /**
     * Consent required at init time but no consent values are given
     *
     * This should create a request of false consent values and a location request with removed location
     */
    @Test
    public void initTimeNoConsentGivenRQ() throws JSONException {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, null, null, rqp));
        Assert.assertEquals(2, Mockito.mockingDetails(rqp).getInvocations().size());

        TestUtils.verifyLocationValuesInRQMock(1, true, null, null, null, null, rqp);
        TestUtils.verifyConsentValuesInRQMock(1, new String[] {}, usedFeatureNames, rqp);
    }

    /**
     * Consent required at init time and a few consent values are given
     * No location consent is given
     *
     * This should create a request with the relevant consent values and a location request with removed location
     */
    @Test
    public void initTimeSetConsentRQ_2() throws JSONException {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        String[] initialConsent = { Countly.CountlyFeatureNames.clicks, Countly.CountlyFeatureNames.push, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.feedback };
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, initialConsent, null, rqp));

        //this should send consent state and empty location
        TestUtils.verifyConsentValuesInRQMock(1, initialConsent, TestUtils.getReminderConsent(initialConsent), rqp);
        TestUtils.verifyLocationValuesInRQMockDisabled(rqp);
    }

    /**
     * Consent required at init time and a few consent values are given
     * No location consent is given
     *
     * This should create a request with the relevant consent values and no location request (consent given but no values provided)
     */
    @Test
    public void initTimeSetConsentRQ_3() throws JSONException {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        String[] initialConsent = { Countly.CountlyFeatureNames.clicks, Countly.CountlyFeatureNames.push, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.feedback, Countly.CountlyFeatureNames.location };
        Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, initialConsent, null, rqp));

        TestUtils.verifyConsentValuesInRQMock(1, initialConsent, TestUtils.getReminderConsent(initialConsent), rqp);
        TestUtils.verifyLocationValuesInRQMockNotGiven(rqp);
    }

    /**
     * Consent required at init time and a few consent values are given
     * No location consent is given
     *
     * This should create a request with the relevant consent values and location request with the given values
     */
    @Test
    public void initTimeSetConsentRQ_4() throws JSONException {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        String[] initialConsent = { Countly.CountlyFeatureNames.attribution, Countly.CountlyFeatureNames.starRating, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.feedback, Countly.CountlyFeatureNames.location };
        CountlyConfig cc = TestUtils.createConsentCountlyConfig(true, initialConsent, null, rqp);
        cc.setLocation("qw", "Böston 墨尔本", "123.9009", "qwe890");
        Countly mCountly = new Countly().init(cc);

        TestUtils.verifyConsentValuesInRQMock(1, initialConsent, TestUtils.getReminderConsent(initialConsent), rqp);
        TestUtils.verifyLocationValuesInRQMockValues(cc.locationCountyCode, cc.locationCity, cc.locationLocation, cc.locationIpAddress, rqp);
    }

    protected static void validateConsentRequest(String deviceId, int idx, boolean[] consents) {
        String consentParam = "{\"sessions\":%b,\"crashes\":%b,\"users\":%b,\"push\":%b,\"content\":%b,\"feedback\":%b,\"scrolls\":%b,\"remote-config\":%b,\"attribution\":%b,\"clicks\":%b,\"location\":%b,\"star-rating\":%b,\"events\":%b,\"views\":%b,\"apm\":%b}";
        String consentsStr = String.format(consentParam, consents[0], consents[1], consents[2], consents[3], consents[4], consents[5], consents[6], consents[7], consents[8], consents[9], consents[10], consents[11], consents[12], consents[13], consents[14]);
        TestUtils.validateRequest(deviceId, TestUtils.map("consent", consentsStr), idx);
    }

    protected static void validateNoConsentRequest(String deviceId, int idx) {
        validateConsentRequest(deviceId, idx, new boolean[] { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false });
    }

    protected static void validateAllConsentRequest(String deviceId, int idx) {
        validateConsentRequest(deviceId, idx, new boolean[] { true, true, true, true, true, true, true, true, true, true, true, true, true, true, true });
    }

    // TODO test that makes sure that the consent change request is created correctly
    // TODO test that makes sure that the consent change request is not created for duplicate triggers
    // TODO make sure that the consent request is correctly created after init
    // TODO test that consent given / removed triggers are played correctly
    // TODO ?make tests for formatConsentState
    // TODO test give all consent
}
