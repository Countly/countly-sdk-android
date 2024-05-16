package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleSessionsTests {
    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void manualSessionBegin() {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.sessions().beginSession();

        TestUtils.verifyBeginSessionValues(requestQueueProvider, false, null, null, null, null);
    }

    @Test
    public void manualSessionBeginUpdateEnd() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.sessions().beginSession();
        TestUtils.verifyBeginSessionValues(requestQueueProvider, false, null, null, null, null);

        Thread.sleep(1000);
        mCountly.sessions().updateSession();

        verify(requestQueueProvider, times(1)).updateSession(1);
        Thread.sleep(2000);
        mCountly.sessions().endSession();
        verify(requestQueueProvider, times(1)).endSession(2, null);
        verify(requestQueueProvider, never()).endSession(anyInt());
    }

    @Test
    public void manualSessionBeginUpdateEndManualDisabled() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableCrashReporting();
        Countly mCountly = new Countly().init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.sessions().beginSession();
        TestUtils.verifyBeginSessionNotCalled(requestQueueProvider);

        Thread.sleep(1000);
        mCountly.sessions().updateSession();

        verify(requestQueueProvider, never()).updateSession(anyInt());
        Thread.sleep(2000);
        mCountly.sessions().endSession();
        verify(requestQueueProvider, never()).endSession(anyInt(), anyString());
        verify(requestQueueProvider, never()).endSession(anyInt());
    }

    @Test
    public void automaticSessionBeginEndWithManualEnabled() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        Countly mCountly = new Countly().init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStart(null);

        TestUtils.verifyBeginSessionNotCalled(requestQueueProvider);
        Thread.sleep(1000);

        mCountly.onStopInternal();

        verify(requestQueueProvider, never()).endSession(anyInt(), anyString());
        verify(requestQueueProvider, never()).endSession(anyInt());
    }

    @Test
    public void automaticSessionBeginEndWithManualDisabled() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig();
        Countly mCountly = new Countly().init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStartInternal(null);

        TestUtils.verifyBeginSessionValues(requestQueueProvider, false, null, null, null, null);
        Thread.sleep(1000);

        mCountly.onStopInternal();

        verify(requestQueueProvider, times(1)).endSession(1, null);
        verify(requestQueueProvider, never()).endSession(anyInt());
    }

    /**
     * No session related requests should be done when no session consent is given
     */
    @Test
    public void consentNotGivenNothingHappens() {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.setRequiresConsent(true);
        Countly mCountly = new Countly().init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

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
        mCountly.sessions().updateSession();
        mCountly.sessions().endSession();

        TestUtils.verifyBeginSessionNotCalled(requestQueueProvider);
        verify(requestQueueProvider, never()).updateSession(anyInt());
        verify(requestQueueProvider, never()).endSession(anyInt(), anyString());
        verify(requestQueueProvider, never()).endSession(anyInt());
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
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        TestUtils.verifyBeginSessionNotCalled(requestQueueProvider);
        verify(requestQueueProvider, never()).updateSession(anyInt());
        verify(requestQueueProvider, never()).endSession(anyInt(), anyString());
        verify(requestQueueProvider, never()).endSession(anyInt());

        mCountly.sessions().updateSession();
        mCountly.sessions().endSession();

        TestUtils.verifyBeginSessionNotCalled(requestQueueProvider);
        verify(requestQueueProvider, never()).updateSession(anyInt());
        verify(requestQueueProvider, never()).endSession(anyInt(), anyString());
        verify(requestQueueProvider, never()).endSession(anyInt());
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
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        TestUtils.verifyBeginSessionNotCalled(requestQueueProvider);

        mCountly.sessions().beginSession();
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

        validateSessionRequest(2, null, null, false);

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

        validateSessionRequest(4, 1, null, true);
        TestUtils.validateRequiredParams(RQ[5]); // this is consent request
        Assert.assertEquals(consentForSession(false), RQ[0].get("consent"));
    }

    private String consentForSession(boolean consent) {
        return "{\"sessions\":" + consent + ",\"crashes\":false,\"users\":false,\"push\":false,\"feedback\":false,\"scrolls\":false,\"remote-config\":false,\"attribution\":false,\"clicks\":false,\"location\":false,\"star-rating\":false,\"events\":false,\"views\":false,\"apm\":false}";
    }

    static void validateSessionRequest(int idx, Integer duration, String deviceId, boolean endSession) {
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
        } else {
            Assert.assertTrue(request.containsKey("begin_session"));
        }
    }

    //TODO add tests that make sure that init time consent is handled correctly
    //todo react to receiving consent and removing consent
}
