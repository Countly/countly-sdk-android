package ly.count.android.sdk;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

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
        Countly.CountlyFeatureNames.clicks,
        Countly.CountlyFeatureNames.scrolls,
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

    void verifyLocationValuesInRQMock(int count, Boolean enabled, String countryCode, String city, String location, String ip, RequestQueueProvider rqp) {
        ArgumentCaptor<Boolean> acLocationDisabled = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> acCountryCode = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> acCity = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> acGps = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> acIp = ArgumentCaptor.forClass(String.class);
        verify(rqp, times(count)).sendLocation(acLocationDisabled.capture(), acCountryCode.capture(), acCity.capture(), acGps.capture(), acIp.capture());

        if (count == 0) {
            return;
        }

        Assert.assertEquals(enabled, acLocationDisabled.getValue());
        Assert.assertEquals(countryCode, acCountryCode.getValue());
        Assert.assertEquals(city, acCity.getValue());
        Assert.assertEquals(location, acGps.getValue());
        Assert.assertEquals(ip, acIp.getValue());
    }

    void verifyConsentValuesInRQMock(int count, String[] valuesTrue, String[] valuesFalse, RequestQueueProvider rqp) throws JSONException {
        ArgumentCaptor<String> consentChanges = ArgumentCaptor.forClass(String.class);
        verify(rqp, times(count)).sendConsentChanges(consentChanges.capture());

        String changes = consentChanges.getValue();
        Assert.assertNotNull(changes);

        JSONObject jObj = new JSONObject(changes);

        Assert.assertEquals(usedFeatureNames.length, jObj.length());
        Assert.assertEquals(usedFeatureNames.length, valuesTrue.length + valuesFalse.length);

        for (String v : valuesTrue) {
            Assert.assertTrue((Boolean) jObj.get(v));
        }

        for (String v : valuesFalse) {
            Assert.assertFalse((Boolean) jObj.get(v));
        }
    }

    String[] subtractConsentFromArray(String[] input, String[] subtraction) {
        ArrayList<String> res = new ArrayList<>();

        for(String v:input) {
            boolean contains = false;
            for(String sv:subtraction) {
                if(sv.equals(v)) {
                    contains = true;
                    break;
                }
            }

            if(!contains) {
                res.add(v);
            }
        }

        return (String[]) res.toArray();
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

        verifyLocationValuesInRQMock(1, true, null, null, null, null, rqp);
        verifyConsentValuesInRQMock(1, new String[] {}, usedFeatureNames, rqp);
    }

    //@Test
    //public void initTimeSetConsentRQ_2() {
    //    RequestQueueProvider rqp = mock(RequestQueueProvider.class);
    //    Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, new String[] { Countly.CountlyFeatureNames.clicks }, null, rqp));
    //
    //    //this should send consent changes and empty location
    //    verify(rqp, times(1)).sendConsentChanges("{\"clicks\":true}");
    //    verify(rqp, times(1)).sendLocation(true, null, null, null, null);
    //}
    //
    ///**
    // * Setting single consent value during init.
    // * That produces appropriate consent request and since it's the location request, does not clear location
    // */
    //@Test
    //public void initTimeSetConsentRQ_3() {
    //    RequestQueueProvider rqp = mock(RequestQueueProvider.class);
    //    Countly mCountly = new Countly().init(TestUtils.createConsentCountlyConfig(true, new String[] { Countly.CountlyFeatureNames.location }, null, rqp));
    //
    //    //this should send consent changes and empty location
    //    verify(rqp, times(1)).sendConsentChanges("{\"location\":true}");
    //    verify(rqp, times(0)).sendLocation(true, null, null, null, null);
    //}

    // TODO test that makes sure that the consent change request is created correctly
    // TODO test that makes sure that the consent change request is not created for duplicate triggers
    // TODO make sure that the consent request is correctly created after init
    // TODO test that consent given / removed triggers are played correctly
    // TODO ?make tests for formatConsentState
}
