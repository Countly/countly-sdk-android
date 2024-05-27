package ly.count.android.sdk;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Init options:
 * M:Manual Sessions enabled
 * A:Automatic sessions enabled
 * H:Hybrid Sessions enabled
 * CR:Consent Required
 * CNR:Consent not Required
 * CG:Session Consent given
 * CNG:Session Consent not given
 */
@RunWith(AndroidJUnit4.class)
public class ScenarioSessionTests {

    @Before
    public void setUp() {
        TestUtils.getCountyStore().clear();
    }

    @After
    public void tearDown() {
        TestUtils.getCountyStore().clear();
    }

    /**
     * In the configuration below configs are given:
     * Manual Session is enabled
     * Consent is required
     * Session consent is given
     * --- Scenario Flow ---
     * endSession();
     * endSession();
     * updateSession();
     * updateSession();
     * wait (seconds: 2);
     * beginSession();
     * wait (seconds: 2);
     * beginSession();
     * updateSession();
     * wait(seconds: 2);
     * updateSession();
     * wait(seconds: 2));
     * endSession();
     * wait(seconds: 2));
     * endSession();
     * updateSession();
     * updateSession();
     * -- Expected Requests --
     * Check request queue and verify in this order:
     * 1. 5 reqs only
     * 2. 1 consent status req
     * 3. 1 begin session req
     * 4. 2 session update reqs with duration 2 seconds
     * 5. 1 end session req with duration 2 secs
     */
    @Test
    public void SE_200_CR_CG_M() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl().setRequiresConsent(true).setConsentEnabled(new String[] { "sessions" });
        Countly countly = new Countly().init(config);

        flowManualSessions(countly);

        Assert.assertEquals(6, TestUtils.getCurrentRQ().length);
        validateConsentRequest(0, true, TestUtils.commonDeviceId);
        validateRequest(TestUtils.map("location", ""), 1);
        validateSessionBeginRequest(2, TestUtils.commonDeviceId);
        validateSessionUpdateRequest(3, 2, TestUtils.commonDeviceId);
        validateSessionUpdateRequest(4, 2, TestUtils.commonDeviceId);
        validateSessionEndRequest(5, 2, TestUtils.commonDeviceId);
    }

    /**
     * In the configuration below configs are given:
     * Manual Session is enabled
     * Consent is not required
     * --- Scenario Flow ---
     * Same flow as 200_CR_CG_M
     * --- Expected Requests ---
     * Same results as 200_CR_CG_M except:
     * 1. 4 reqs instead of 5 (no consent status req)
     */
    @Test
    public void SE_201_CNR_M() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl().setRequiresConsent(false);
        Countly countly = new Countly().init(config);

        flowManualSessions(countly);

        Assert.assertEquals(4, TestUtils.getCurrentRQ().length);
        validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        validateSessionUpdateRequest(1, 2, TestUtils.commonDeviceId);
        validateSessionUpdateRequest(2, 2, TestUtils.commonDeviceId);
        validateSessionEndRequest(3, 2, TestUtils.commonDeviceId);
    }

    /**
     * In the configuration below configs are given:
     * Manual Session is enabled
     * Consent is required
     * Session consent is not given
     * --- Scenario Flow ---
     * Same flow as 200_CR_CG_M
     * --- Expected Requests ---
     * Same as 200_CR_CG_M except:
     * 1. No requests generated
     */
    @Test
    public void SE_202_CR_CNG_M() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl().setRequiresConsent(true);
        Countly countly = new Countly().init(config);

        flowManualSessions(countly);

        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        validateConsentRequest(0, false, TestUtils.commonDeviceId);
        validateRequest(TestUtils.map("location", ""), 1);
    }

    /**
     * In the configuration below configs are given:
     * Manual Session is enabled
     * Consent is required
     * Session consent is given
     * --- Scenario Flow ---
     * endSession();
     * updateSession();
     * updateSession();
     * wait (seconds: 2);
     * deviceID - changeWithMerge('newID');
     * beginSession();
     * wait (seconds:2);
     * endSession();
     * deviceID - changeWithoutMerge('newID_2');
     * beginSession();
     * endSession();
     * --- Expected Requests ---
     * Check request queue and verify:
     * 1. consent status req (session consent given)
     * 2. begin session req
     * 3. end session req 2 seconds
     * 4. consent status req (session consent not given)
     */
    @Test
    public void SE_203_CR_CG_M_id_change() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl().setRequiresConsent(true).setConsentEnabled(new String[] { "sessions" });
        Countly countly = new Countly().init(config);

        countly.sessions().endSession();
        countly.sessions().updateSession();
        countly.sessions().updateSession();

        Thread.sleep(2000);

        countly.deviceId().changeWithMerge("newID");
        countly.sessions().beginSession();

        Thread.sleep(2000);

        countly.sessions().endSession();

        countly.deviceId().changeWithoutMerge("newID_2");
        countly.sessions().beginSession();
        countly.sessions().endSession();

        Assert.assertEquals(5, TestUtils.getCurrentRQ().length);
        validateConsentRequest(0, true, TestUtils.commonDeviceId);
        validateRequest(TestUtils.map("location", ""), 1);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", TestUtils.commonDeviceId), 2);
        validateSessionBeginRequest(3, "newID");
        validateSessionEndRequest(4, 2, "newID");
    }

    /**
     * In the configuration below configs are given:
     * Consent is not required
     * --- Scenario Flow ---
     * wait (seconds: 1);
     * changeIDwithMerge('newID');
     * wait (seconds: 1);
     * changeIDwithoutMerge('newID_2');
     * wait (seconds: 1);
     * changeIDwithMerge('newID');
     * wait (seconds: 1);
     * changeIDwithoutMerge('newID_2');
     * wait (seconds: 1);
     * *go to background*
     * wait (seconds: 1);
     * *back to foreground*
     * changeIDwithMerge('newID');
     * *go to background*
     * wait (seconds: 1);
     * *back to foreground*
     * --- Expected Requests ---
     * Check request queue and verify:
     * 1. too lazy to calculate just let us know and lets verify together
     */
    @Test
    public void SE_204_CNR_A_id_change() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig();
        Countly countly = new Countly().init(config);

        flowAutomaticSessions(countly);

        Assert.assertEquals(5, TestUtils.getCurrentRQ().length);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", TestUtils.commonDeviceId), 0);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", "newID_2"), 1);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", "newID_2"), 2);
        validateSessionBeginRequest(3, "newID");
        // 4 is orientation change
        validateSessionEndRequest(5, null, "newID");
    }

    /**
     * In the configuration below configs are given:
     * Consent is required
     * Session consent is given
     * --- Scenario Flow ---
     * wait (seconds: 1);
     * changeIDwithMerge('newID');
     * wait (seconds: 1);
     * changeIDwithoutMerge('newID_2');
     * wait (seconds: 1);
     * changeIDwithMerge('newID');
     * wait (seconds: 1);
     * changeIDwithoutMerge('newID_2');
     * wait (seconds: 1);
     * *go to background*
     * wait (seconds: 1);
     * *back to foreground*
     * changeIDwithMerge('newID');
     * *go to background*
     * wait (seconds: 1);
     * *back to foreground*
     * --- Expected Requests ---
     * Check request queue and verify:
     * 1. too lazy to calculate just let us know and let's verify together
     */
    @Test
    public void SE_205_CR_CG_A_id_change() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().setRequiresConsent(true).setConsentEnabled(new String[] { "sessions" });
        Countly countly = new Countly().init(config);

        flowAutomaticSessions(countly);

        Assert.assertEquals(5, TestUtils.getCurrentRQ().length);
        validateConsentRequest(0, true, TestUtils.commonDeviceId);
        validateRequest(TestUtils.map("location", ""), 1);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", TestUtils.commonDeviceId), 2);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", "newID_2"), 3);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", "newID_2"), 4);
    }

    /**
     * In the configuration below configs are given:
     * Consent is required
     * Session consent is not given
     * --- Scenario Flow ---
     * wait (seconds: 1);
     * changeIDwithMerge('newID');
     * wait (seconds: 1);
     * changeIDwithoutMerge('newID_2');
     * wait (seconds: 1);
     * changeIDwithMerge('newID');
     * wait (seconds: 1);
     * changeIDwithoutMerge('newID_2');
     * wait (seconds: 1);
     * *go to background*
     * wait (seconds: 1);
     * *back to foreground*
     * changeIDwithMerge('newID');
     * *go to background*
     * wait (seconds: 1);
     * *back to foreground*
     * --- Expected Requests ---
     * Check request queue and verify:
     * 1. too lazy to calculate just let us know and lets verify together
     */
    @Test
    public void SE_206_CR_CNG_A_id_change() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig().setRequiresConsent(true);
        Countly countly = new Countly().init(config);

        flowAutomaticSessions(countly);

        Assert.assertEquals(5, TestUtils.getCurrentRQ().length);
        validateConsentRequest(0, false, TestUtils.commonDeviceId);
        validateRequest(TestUtils.map("location", ""), 1);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", TestUtils.commonDeviceId), 2);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", "newID_2"), 3);
        TestUtils.validateRequest("newID", TestUtils.map("old_device_id", "newID_2"), 4);
    }

    private void flowManualSessions(Countly countly) throws InterruptedException {
        countly.sessions().endSession();
        countly.sessions().endSession();
        countly.sessions().updateSession();
        countly.sessions().updateSession();

        Thread.sleep(2000);
        countly.sessions().beginSession();
        Thread.sleep(2000);
        countly.sessions().beginSession();
        countly.sessions().updateSession();
        Thread.sleep(2000);
        countly.sessions().updateSession();
        Thread.sleep(2000);
        countly.sessions().endSession();
        Thread.sleep(2000);
        countly.sessions().endSession();
        countly.sessions().updateSession();
        countly.sessions().updateSession();
    }

    private void flowAutomaticSessions(Countly countly) throws InterruptedException {
        Thread.sleep(1000);
        countly.deviceId().changeWithMerge("newID");
        Thread.sleep(1000);
        countly.deviceId().changeWithoutMerge("newID_2");
        Thread.sleep(1000);
        countly.deviceId().changeWithMerge("newID");
        Thread.sleep(1000);
        countly.deviceId().changeWithoutMerge("newID_2");
        Thread.sleep(1000);
        sendAppToBackground();
        Thread.sleep(1000);
        bringAppToForeground();
        countly.deviceId().changeWithMerge("newID");
        sendAppToBackground();
        Thread.sleep(1000);
        bringAppToForeground();
    }

    private void validateSessionBeginRequest(int idx, String deviceId) {
        TestUtils.validateRequest(deviceId, TestUtils.map("begin_session", "1"), idx);
    }

    private void validateSessionEndRequest(int idx, Integer duration, String deviceId) {
        Map<String, String> request = validateSessionUpdateRequest(idx, duration, deviceId);
        Assert.assertEquals("1", request.get("end_session"));
    }

    private Map<String, String> validateSessionUpdateRequest(int idx, Integer duration, String deviceId) {
        Map<String, String> request = TestUtils.getCurrentRQ()[idx];

        TestUtils.validateRequiredParams(TestUtils.getCurrentRQ()[idx], deviceId);
        if (duration != null) {
            Assert.assertEquals(duration.toString(), request.get("session_duration"));
        }

        return request;
    }

    private void validateConsentRequest(int idx, boolean consentForSession, String deviceId) {
        TestUtils.validateRequest(deviceId, TestUtils.map("consent",
            "{\"sessions\":" + consentForSession + ",\"crashes\":false,\"users\":false,\"push\":false,\"feedback\":false,\"scrolls\":false,\"remote-config\":false,\"attribution\":false,\"clicks\":false,\"location\":false,\"star-rating\":false,\"events\":false,\"views\":false,\"apm\":false}"), idx);
    }

    private void sendAppToBackground() {
        // TODO use ActivityScenario to test this
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TestUtils.getApplication().startActivity(intent);
    }

    private void bringAppToForeground() {
        Intent intent = new Intent(TestUtils.getApplication(), TestUtils.Activity2.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        TestUtils.getApplication().startActivity(intent);
    }

    private void validateRequest(Map<String, Object> expectedExtras, int idx) {
        TestUtils.validateRequest(TestUtils.commonDeviceId, expectedExtras, idx);
    }
}
