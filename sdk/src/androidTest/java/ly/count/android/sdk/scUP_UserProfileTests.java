package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * M:Manual Sessions enabled
 * A:Automatic sessions enabled
 * H:Hybrid Sessions enabled
 * CR:Consent Required
 * CNR:Consent not Required
 * CG:Consent given (All)
 * CNG:Consent not given (All)
 */
@RunWith(AndroidJUnit4.class)
public class scUP_UserProfileTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().halt();
        TestUtils.getCountyStore().clear();
    }

    @After
    public void tearDown() {
        TestUtils.getCountyStore().clear();
    }

    /**
     * Related user properties should be saved before event recordings
     * call order, begin session, user property with "dark_mode", event, user property with "light_mode", end session
     * Manual sessions are enabled
     * generated request order  begin_session + first user property request + 3 events + user property request with light_mode + end_session
     */
    @Test
    public void eventSaveScenario_manualSessions() throws JSONException {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().enableManualSessionControl());
        TestUtils.assertRQSize(0);

        countly.sessions().beginSession();
        TestUtils.assertRQSize(1); // begin session request
        countly.userProfile().setProperty("theme", "dark_mode");

        countly.events().recordEvent("test_event1");
        TestUtils.assertRQSize(2); // begin session request + user property request with dark_mode
        countly.events().recordEvent("test_event2");
        countly.events().recordEvent("test_event3");

        countly.userProfile().setProperty("theme", "light_mode");
        TestUtils.assertRQSize(2); // no request is generated on the way

        countly.sessions().endSession();
        // begin_session + first user property request + 3 events + user property request with light_mode + end_session
        ModuleUserProfileTests.validateUserProfileRequest(1, 5, TestUtils.map(), TestUtils.map("theme", "dark_mode"));
        ModuleUserProfileTests.validateUserProfileRequest(3, 5, TestUtils.map(), TestUtils.map("theme", "light_mode"));
    }

    /**
     * Related user properties should be saved before event recordings
     * call order, user property with "dark_mode", event, user property with "light_mode"
     * No consent for sessions
     * generated request order first user property request + 3 events + user property request with light_mode
     */
    @Test
    public void eventSaveScenario_onTimer() throws InterruptedException, JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sessionUpdateTimerDelay = 2; // trigger update call for property save
        Countly countly = new Countly().init(config);

        TestUtils.assertRQSize(0); // no begin session because of no consent

        countly.userProfile().setProperty("theme", "dark_mode");

        countly.events().recordEvent("test_event1");
        TestUtils.assertRQSize(1); // user property request with dark_mode
        countly.events().recordEvent("test_event2");
        countly.events().recordEvent("test_event3");

        countly.userProfile().setProperty("theme", "light_mode");
        TestUtils.assertRQSize(1); // no request is generated on the way

        Thread.sleep(3000);

        // first user property request + 3 events + user property request with light_mode
        ModuleUserProfileTests.validateUserProfileRequest(0, 3, TestUtils.map(), TestUtils.map("theme", "dark_mode"));
        ModuleUserProfileTests.validateUserProfileRequest(2, 3, TestUtils.map(), TestUtils.map("theme", "light_mode"));
    }

    /**
     * Related user properties should be saved before event recordings
     * call order, user property with "dark_mode", event, user property with "light_mode"
     * generated request order first user property request + 3 events + user property request with light_mode + begin session
     */
    @Test
    public void eventSaveScenario_changeDeviceIDWithoutMerge() throws JSONException {
        Countly countly = new Countly().init(TestUtils.createBaseConfig());

        TestUtils.assertRQSize(0);
        countly.userProfile().setProperty("theme", "dark_mode");

        countly.events().recordEvent("test_event1");
        TestUtils.assertRQSize(1); // user property request with dark_mode
        countly.events().recordEvent("test_event2");
        countly.events().recordEvent("test_event3");

        countly.userProfile().setProperty("theme", "light_mode");
        TestUtils.assertRQSize(1); // no request is generated on the way

        countly.deviceId().changeWithoutMerge("new_device_id"); // this will begin a new session

        // first user property request + 3 events + user property request with light_mode
        ModuleUserProfileTests.validateUserProfileRequest(0, 4, TestUtils.map(), TestUtils.map("theme", "dark_mode"));
        ModuleUserProfileTests.validateUserProfileRequest(2, 4, TestUtils.map(), TestUtils.map("theme", "light_mode"));
    }

    /**
     * 1. 200_CNR_A
     * Init SDK
     * sendUserProperties
     * sendUserData
     * Check request queue:
     * - There can be a began session request
     * - There should be no user property request
     * - There should be no user data request
     */
    @Test
    public void UP_200_CNR_A() {
        Countly countly = new Countly().init(TestUtils.createBaseConfig());

        sendUserProperties(countly);
        sendUserData(countly);

        TestUtils.assertRQSize(0);
    }

    /**
     * 2. 201_CR_CG_A => same as 200
     */
    @Test
    public void UP_201_CR_CG_A() {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().setRequiresConsent(true).giveAllConsents());

        sendUserProperties(countly);
        sendUserData(countly);

        TestUtils.assertRQSize(1);
        ModuleConsentTests.validateAllConsentRequest(TestUtils.commonDeviceId, 0);
    }

    /**
     * 3. 202_CR_CNG_A => same as 200 but there should be no request except consent and location
     */
    @Test
    public void UP_202_CR_CNG_A() {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().setRequiresConsent(true));

        sendUserProperties(countly);
        sendUserData(countly);

        TestUtils.assertRQSize(2);
        ModuleSessionsTests.validateSessionConsentRequest(0, false, TestUtils.commonDeviceId);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 1);
    }

    /**
     * 4. 203_CNR_A_events
     * Init SDK
     * RecordBasicEvent A
     * RecordBasicEvent B
     * sendSameData
     * RecordBasicEvent C
     * sendSameData
     * RecordBasicEvent D
     * sendSameData
     * RecordBasicEvent E
     * Check requests queue:
     * 1. Begin session
     * 2. Event A and B
     * 3. User Property a12345 = 4
     * 4. Event C
     * 5. User Property a12345 = 4
     * 6. Event D
     * 7. User Property a12345 = 4
     * Check event queue:
     * 1. Event E
     */
    @Test
    public void UP_203_CNR_A_events() throws JSONException {
        Countly countly = new Countly().init(TestUtils.createBaseConfig());

        countly.events().recordEvent("A");
        countly.events().recordEvent("B");
        sendSameData(countly);
        countly.events().recordEvent("C");
        sendSameData(countly);
        countly.events().recordEvent("D");
        sendSameData(countly);
        countly.events().recordEvent("E");

        TestUtils.assertRQSize(6);

        ModuleEventsTests.validateEventInRQ("A", 0, 0, 2);
        ModuleEventsTests.validateEventInRQ("B", 0, 1, 2);

        ModuleUserProfileTests.validateUserProfileRequest(1, 6, TestUtils.map(), TestUtils.map("a12345", "4"));

        ModuleEventsTests.validateEventInRQ("C", 2, 0, 1);

        ModuleUserProfileTests.validateUserProfileRequest(3, 6, TestUtils.map(), TestUtils.map("a12345", "4"));

        ModuleEventsTests.validateEventInRQ("D", 4, 0, 1);

        ModuleUserProfileTests.validateUserProfileRequest(5, 6, TestUtils.map(), TestUtils.map("a12345", "4"));
    }

    /**
     * 5. 204_CR_CG_A_events => same as 203
     */
    @Test
    public void UP_205_CR_CG_A() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig().setRequiresConsent(true).giveAllConsents();
        Countly countly = new Countly().init(config);

        countly.events().recordEvent("A");
        countly.events().recordEvent("B");
        sendSameData(countly);
        countly.events().recordEvent("C");
        sendSameData(countly);
        countly.events().recordEvent("D");
        sendSameData(countly);
        countly.events().recordEvent("E");

        TestUtils.assertRQSize(7);
        ModuleConsentTests.validateAllConsentRequest(TestUtils.commonDeviceId, 0);

        ModuleEventsTests.validateEventInRQ("A", 1, 0, 2);
        ModuleEventsTests.validateEventInRQ("B", 1, 1, 2);

        ModuleUserProfileTests.validateUserProfileRequest(2, 7, TestUtils.map(), TestUtils.map("a12345", "4"));

        ModuleEventsTests.validateEventInRQ("C", 3, 0, 1);

        ModuleUserProfileTests.validateUserProfileRequest(4, 7, TestUtils.map(), TestUtils.map("a12345", "4"));

        ModuleEventsTests.validateEventInRQ("D", 5, 0, 1);

        ModuleUserProfileTests.validateUserProfileRequest(6, 7, TestUtils.map(), TestUtils.map("a12345", "4"));
    }

    /**
     * 6. 206_CR_CNG_A => same as 203 but there should be no request
     */
    @Test
    public void UP_206_CR_CNG_A() {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().setRequiresConsent(true));

        countly.events().recordEvent("A");
        countly.events().recordEvent("B");
        sendSameData(countly);
        countly.events().recordEvent("C");
        sendSameData(countly);
        countly.events().recordEvent("D");
        sendSameData(countly);
        countly.events().recordEvent("E");

        TestUtils.assertRQSize(2);
        ModuleSessionsTests.validateSessionConsentRequest(0, false, TestUtils.commonDeviceId);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 1);
    }

    /**
     * 7. 207_CNR_M
     * Init SDK
     * Begin Session
     * RecordBasicEvent A
     * RecordBasicEvent B
     * sendSameData
     * End Session
     * RecordBasicEvent C
     * sendUserData
     * EndSession
     * Change device ID Merge ('merge_id')
     * sendSameData
     * Change device ID Non Merge ('non_merge_id')
     * sendSameData
     * RecordBasicEvent D
     * Check requests queue:
     * 1. Begin session
     * 2. Event A and B
     * 3. User Property a12345 = 4
     * 4. End Session
     * 5. Event C
     * 6. User Data
     * 7. Merge ID
     * 8. User Property a12345 = 4
     * 9. Non Merge ID
     * 10. User Property a12345 = 4
     * Check event queue:
     * 1. Event D
     */
    @Test
    public void UP_207_CNR_M() throws JSONException {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().enableManualSessionControl());

        countly.sessions().beginSession();
        countly.events().recordEvent("A");
        countly.events().recordEvent("B");
        sendSameData(countly);
        countly.sessions().endSession();
        countly.events().recordEvent("C");
        sendUserData(countly);
        countly.sessions().endSession();
        countly.deviceId().changeWithMerge("merge_id");
        sendSameData(countly);
        countly.deviceId().changeWithoutMerge("non_merge_id");
        sendSameData(countly);
        countly.events().recordEvent("D");

        TestUtils.assertRQSize(8);
        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        ModuleEventsTests.validateEventInRQ("A", 1, 0, 2);
        ModuleEventsTests.validateEventInRQ("B", 1, 1, 2);

        ModuleUserProfileTests.validateUserProfileRequest(2, 8, TestUtils.map(), TestUtils.map("a12345", "4"));

        ModuleSessionsTests.validateSessionEndRequest(3, null, TestUtils.commonDeviceId);

        TestUtils.validateRequest("merge_id", TestUtils.map("old_device_id", TestUtils.commonDeviceId), 4);

        ModuleEventsTests.validateEventInRQ("merge_id", "C", 1, 0.0d, 0.0d, 5, 0, 1, 8);

        validateUserDataRequest(6, 8, "4", "merge_id");

        ModuleUserProfileTests.validateUserProfileRequest("non_merge_id", 7, 8, TestUtils.map(), TestUtils.map("a12345", "4"));
    }

    /**
     * 8. 208_CR_CG_M => same as 207
     * Check requests queue:
     * 1. Begin session
     * 2. Event A and B
     * 3. User Property a12345 = 4
     * 4. End Session
     * 5. Event C
     * 6. User Data
     * 7. Merge ID
     * 8. User Property a12345 = 4
     * 9. Non Merge ID
     * Check event queue:
     * 1. -
     */
    @Test
    public void UP_208_CR_CG_M() throws JSONException {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().enableManualSessionControl().setRequiresConsent(true).giveAllConsents());

        countly.sessions().beginSession();
        countly.events().recordEvent("A");
        countly.events().recordEvent("B");
        sendSameData(countly);
        countly.sessions().endSession();
        countly.events().recordEvent("C");
        sendUserData(countly);
        countly.sessions().endSession();
        countly.deviceId().changeWithMerge("merge_id");
        sendSameData(countly);
        countly.deviceId().changeWithoutMerge("non_merge_id");
        sendSameData(countly);
        countly.events().recordEvent("D");

        TestUtils.assertRQSize(9);
        ModuleConsentTests.validateAllConsentRequest(TestUtils.commonDeviceId, 0);
        ModuleSessionsTests.validateSessionBeginRequest(1, TestUtils.commonDeviceId);

        ModuleEventsTests.validateEventInRQ("A", 2, 0, 2);
        ModuleEventsTests.validateEventInRQ("B", 2, 1, 2);

        ModuleUserProfileTests.validateUserProfileRequest(3, 9, TestUtils.map(), TestUtils.map("a12345", "4"));

        ModuleSessionsTests.validateSessionEndRequest(4, null, TestUtils.commonDeviceId);
        TestUtils.validateRequest("merge_id", TestUtils.map("old_device_id", TestUtils.commonDeviceId), 5);

        ModuleEventsTests.validateEventInRQ("merge_id", "C", 1, 0.0d, 0.0d, 6, 0, 1, 9);

        validateUserDataRequest(7, 9, "4", "merge_id");

        TestUtils.validateRequest("merge_id", TestUtils.map("location", ""), 8);
    }

    /**
     * 9. 209_CR_CNG_M => same as 207 but no there should be no request related to user data
     */
    @Test
    public void UP_209_CR_CNG_M() {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().enableManualSessionControl().setRequiresConsent(true));

        countly.sessions().beginSession();
        countly.events().recordEvent("A");
        countly.events().recordEvent("B");
        sendSameData(countly);
        countly.sessions().endSession();
        countly.events().recordEvent("C");
        sendUserData(countly);
        countly.sessions().endSession();
        countly.deviceId().changeWithMerge("merge_id");
        sendSameData(countly);
        countly.deviceId().changeWithoutMerge("non_merge_id");
        sendSameData(countly);
        countly.events().recordEvent("D");

        TestUtils.assertRQSize(3);
        ModuleSessionsTests.validateSessionConsentRequest(0, false, TestUtils.commonDeviceId);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 1);
        TestUtils.validateRequest("merge_id", TestUtils.map("old_device_id", TestUtils.commonDeviceId), 2);
    }

    /**
     * 10. 210_CNR_M_duration
     * Init SDK with session update 5 secs
     * sendUserData
     * wait 6 secs
     * Check request queue:
     * 1. User property req with all data
     */
    @Test
    public void UP_210_CNR_M_duration() throws InterruptedException, JSONException {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().enableManualSessionControl().setUpdateSessionTimerDelay(5));

        sendUserData(countly);
        Thread.sleep(6000);

        validateUserDataRequest(0, 1, "My Property", TestUtils.commonDeviceId);
    }

    private void sendUserProperties(Countly countly) {
        Map<String, Object> userProperties = TestUtils.map(
            "name", "Nicola Tesla",
            "username", "nicola",
            "email", "info@nicola.tesla",
            "organization", "Trust Electric Ltd",
            "phone", "+90 822 140 2546",
            "picture", "http://images2.fanpop.com/images/photos/3300000/Nikola-Tesla-nikola-tesla-3365940-600-738.jpg",
            "picturePath", "",
            "gender", "M",
            "byear", 1919,
            "special_value", "something special",
            "not_special_value", "something special cooking"
        );

        countly.userProfile().setProperties(userProperties);
    }

    private void sendUserData(Countly countly) {
        countly.userProfile().setProperty("a12345", "My Property");
        countly.userProfile().increment("b12345");
        countly.userProfile().incrementBy("c12345", 10);
        countly.userProfile().multiply("d12345", 20);
        countly.userProfile().saveMax("e12345", 100);
        countly.userProfile().saveMin("f12345", 50);
        countly.userProfile().setOnce("g12345", "200");
        countly.userProfile().pushUnique("h12345", "morning");
        countly.userProfile().push("i12345", "morning");
        countly.userProfile().pull("k12345", "morning");
    }

    private void validateUserDataRequest(int idx, int rqSize, String expectedAa12345, String deviceId) throws JSONException {
        ModuleUserProfileTests.validateUserProfileRequest(deviceId, idx, rqSize, TestUtils.map(), TestUtils.map(
            "a12345", expectedAa12345,
            "b12345", TestUtils.json("$inc", 1),
            "c12345", TestUtils.json("$inc", 10),
            "d12345", TestUtils.json("$mul", 20),
            "e12345", TestUtils.json("$max", 100),
            "f12345", TestUtils.json("$min", 50),
            "g12345", TestUtils.json("$setOnce", "200"),
            "h12345", TestUtils.json("$addToSet", "morning"),
            "i12345", TestUtils.json("$push", "morning"),
            "k12345", TestUtils.json("$pull", "morning")
        ));
    }

    private void sendSameData(Countly countly) {
        countly.userProfile().setProperty("a12345", "1");
        countly.userProfile().setProperty("a12345", "2");
        countly.userProfile().setProperty("a12345", "3");
        countly.userProfile().setProperty("a12345", "4");
    }
}
