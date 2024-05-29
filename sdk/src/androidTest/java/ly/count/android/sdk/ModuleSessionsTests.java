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

        validateSessionRequest(0, null, null, false, true);
    }

    @Test
    public void manualSessionBeginUpdateEnd() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        mCountly.sessions().beginSession();
        validateSessionRequest(0, null, null, false, true);

        Thread.sleep(1000);
        mCountly.sessions().updateSession();
        validateSessionRequest(1, 1, null, false, false);

        Thread.sleep(2000);
        mCountly.sessions().endSession();
        validateSessionRequest(2, 2, null, true, false);
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
        validateSessionRequest(0, null, null, false, true);

        Thread.sleep(1000);

        mCountly.onStopInternal();

        validateSessionRequest(1, 1, null, true, false);
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
        TestUtils.validateRequiredParams(RQ[0]); // this is consent request
        Assert.assertEquals(consentForSession(false), RQ[0].get("consent"));
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

        validateSessionRequest(0, null, null, false, true);
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
        TestUtils.validateRequiredParams(RQ[0]); // this is consent request
        Assert.assertEquals(consentForSession(false), RQ[0].get("consent"));
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

        validateSessionRequest(2, null, null, false, true);

        TestUtils.validateRequiredParams(RQ[3]);
        Assert.assertEquals(consentForSession(true), RQ[3].get("consent"));

        mCountly.sessions().beginSession();
        Assert.assertEquals(4, TestUtils.getCurrentRQ().length);

        mCountly.sessions().endSession();
        Assert.assertEquals(4, TestUtils.getCurrentRQ().length);

        Thread.sleep(1000);

        mCountly.consent().removeConsent(new String[] { "sessions" });

        RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(6, RQ.length);

        validateSessionRequest(4, 1, null, true, false);
        TestUtils.validateRequiredParams(RQ[5]); // this is consent request
        Assert.assertEquals(consentForSession(false), RQ[0].get("consent"));
    }

    private String consentForSession(boolean consent) {
        return "{\"sessions\":" + consent + ",\"crashes\":false,\"users\":false,\"push\":false,\"feedback\":false,\"scrolls\":false,\"remote-config\":false,\"attribution\":false,\"clicks\":false,\"location\":false,\"star-rating\":false,\"events\":false,\"views\":false,\"apm\":false}";
    }

    static void validateSessionRequest(int idx, Integer duration, String deviceId, boolean endSession, boolean beginSession) {
        Map<String, String> request = TestUtils.getCurrentRQ()[idx];

        if (deviceId != null) {
            TestUtils.validateRequiredParams(request, deviceId);
        } else {
            TestUtils.validateRequiredParams(request);
        }

        if (endSession) {
            Assert.assertTrue(request.containsKey("end_session"));
        }

        if (duration != null) {
            Assert.assertEquals(duration, Integer.valueOf(request.get("session_duration")));
        }

        if (beginSession) {
            Assert.assertTrue(request.containsKey("begin_session"));
        }
    }

    //TODO add tests that make sure that init time consent is handled correctly
    //todo react to receiving consent and removing consent
}
