package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class ModuleSessionsTests {
    @Before
    public void setUp() {
        TestUtils.getCountyStore().clear();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void manualSessionBegin() {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);
        mCountly.sessions().beginSession();

        validateSessionBeginRequest(0, TestUtils.commonDeviceId);
    }

    @Test
    public void manualSessionBeginUpdateEnd() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        mCountly.sessions().beginSession();
        validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        Thread.sleep(1000);
        mCountly.sessions().updateSession();
        validateSessionUpdateRequest(1, 1, TestUtils.commonDeviceId);

        Thread.sleep(2000);
        mCountly.sessions().endSession();
        validateSessionEndRequest(3, 2, TestUtils.commonDeviceId); // not idx 2 anymore, it will send orientation event RQ
    }

    @Test
    public void manualSessionBeginUpdateEndManualDisabled() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig();
        Countly mCountly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        mCountly.sessions().beginSession();

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        Thread.sleep(1000);
        mCountly.sessions().updateSession();
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        Thread.sleep(2000);
        mCountly.sessions().endSession();

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
    }

    @Test
    public void automaticSessionBeginEndWithManualEnabled() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        mCountly.onStart(null);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        Thread.sleep(1000);

        mCountly.onStopInternal();

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
    }

    @Test
    public void automaticSessionBeginEndWithManualDisabled() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig();
        Countly mCountly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        mCountly.onStartInternal(null);
        validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        Thread.sleep(1000);

        mCountly.onStopInternal();

        validateSessionEndRequest(2, 1, TestUtils.commonDeviceId); // not idx 1 anymore, it will send orientation event RQ
    }

    /**
     * No session related requests should be done when no session consent is given
     */
    @Test
    public void consentNotGivenNothingHappens() {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.setRequiresConsent(true);
        Countly mCountly = new Countly().init(config);

        Map<String, String>[] RQ = TestUtils.getCurrentRQ();

        Assert.assertEquals(2, RQ.length);
        validateSessionConsentRequest(0, false, TestUtils.commonDeviceId);
        TestUtils.validateRequiredParams(RQ[1]); // this is location request
        Assert.assertEquals("", RQ[1].get("location"));

        mCountly.onStart(Mockito.mock(TestUtils.Activity2.class));
        mCountly.onStopInternal();

        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        mCountly.sessions().beginSession();
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        mCountly.sessions().updateSession();
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        mCountly.sessions().endSession();

        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
    }

    /**
     * Validating manual session flow
     * a session hasn't been begun and we are trying to stop it or call update
     * No session requests should be recorded
     */
    @Test
    public void manualSessionsNoUpdateStopWithoutBegin() {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length); // validate that no requests have been recorded

        mCountly.sessions().updateSession();
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        mCountly.sessions().endSession();
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
    }

    /**
     * Validating manual session flow
     * If a session has been started, starting another session should do nothing
     * Only a single begin session request should be recorded
     */
    @Test
    public void manualSessionsNoReactionStartingSessionAgain() {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        mCountly.sessions().beginSession();

        validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        mCountly.sessions().beginSession();

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
    }

    @Test
    public void sessionBeginEndConsentChanges() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.lifecycleObserver = () -> true;
        config.setRequiresConsent(true);
        Countly mCountly = new Countly().init(config);

        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(2, RQ.length);
        validateSessionConsentRequest(0, false, TestUtils.commonDeviceId);
        TestUtils.validateRequiredParams(RQ[1]); // this is location request
        Assert.assertEquals("", RQ[1].get("location"));

        mCountly.sessions().beginSession();
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);

        mCountly.sessions().endSession();
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);

        mCountly.sessions().endSession();
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);

        mCountly.consent().giveConsent(new String[] { "sessions" });
        RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(4, RQ.length);

        validateSessionBeginRequest(2, TestUtils.commonDeviceId);

        validateSessionConsentRequest(3, true, TestUtils.commonDeviceId);

        mCountly.sessions().beginSession();
        Assert.assertEquals(4, TestUtils.getCurrentRQ().length);

        mCountly.sessions().endSession();
        Assert.assertEquals(4, TestUtils.getCurrentRQ().length);

        Thread.sleep(1000);

        mCountly.consent().removeConsent(new String[] { "sessions" });

        RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(6, RQ.length);

        validateSessionEndRequest(4, 1, TestUtils.commonDeviceId);

        validateSessionConsentRequest(5, false, TestUtils.commonDeviceId);
    }

    protected static void validateSessionConsentRequest(int idx, boolean consentForSession, String deviceId) {
        ModuleConsentTests.validateConsentRequest(deviceId, idx, new boolean[] { consentForSession, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false });
    }

    protected static void validateSessionBeginRequest(int idx, String deviceId) {
        TestUtils.validateRequest(deviceId, TestUtils.map("begin_session", "1"), idx);
    }

    protected static void validateSessionEndRequest(int idx, Integer duration, String deviceId) {
        Map<String, String> request = validateSessionUpdateRequest(idx, duration, deviceId);
        Assert.assertEquals("1", request.get("end_session"));
    }

    protected static Map<String, String> validateSessionUpdateRequest(int idx, Integer duration, String deviceId) {
        Map<String, String> request = TestUtils.getCurrentRQ()[idx];

        TestUtils.validateRequiredParams(TestUtils.getCurrentRQ()[idx], deviceId);
        if (duration != null) {
            Assert.assertEquals(duration.toString(), request.get("session_duration"));
        }

        return request;
    }

    //TODO add tests that make sure that init time consent is handled correctly
    //todo react to receiving consent and removing consent
}
