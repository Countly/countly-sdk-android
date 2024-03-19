package ly.count.android.sdk;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleUserProfileTests {
    CountlyStore store;

    @Before
    public void setUp() {
        Countly.sharedInstance().halt();
        store = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        store.clear();
    }

    @After
    public void tearDown() {
    }

    /**
     * Testing basic flow
     */
    @Test
    public void setAndSaveValues() {
        Countly mCountly = Countly.sharedInstance();//todo move away from static init after static user profile has been removed
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        HashMap<String, Object> userProperties = new HashMap<>();
        userProperties.put("name", "Test Test");
        userProperties.put("username", "test");
        userProperties.put("email", "test@gmail.com");
        userProperties.put("organization", "Tester");
        userProperties.put("phone", "+1234567890");
        userProperties.put("gender", "M");
        userProperties.put("picture", "http://domain.com/test.png");
        userProperties.put("byear", "2000");
        userProperties.put("key1", "value1");
        userProperties.put("key2", "value2");

        mCountly.userProfile().setProperties(userProperties);
        mCountly.userProfile().save();

        assertEquals(1, store.getRequests().length);
    }

    /**
     * When saving user profile changes, it empties EQ into RQ
     */
    @Test
    public void SavingWritesEQIntoRQ() {
        Countly mCountly = Countly.sharedInstance();//todo move away from static init after static user profile has been removed
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        assertEquals(0, store.getEvents().length);
        assertEquals(0, store.getRequests().length);

        mCountly.events().recordEvent("a");
        assertEquals(1, store.getEvents().length);
        assertEquals(0, store.getRequests().length);

        mCountly.userProfile().setProperty("name", "Test Test");
        mCountly.userProfile().save();

        String[] reqs = store.getRequests();
        assertEquals(0, store.getEvents().length);
        assertEquals(2, reqs.length);
        Assert.assertTrue(reqs[0].contains("events"));
        assertFalse(reqs[1].contains("events"));
    }

    // BELLOW TESTS THAT NEED TO BE REWORKED

    void assertAllValuesNull(ModuleUserProfile mup) {
        assertNull(mup.name);
        assertNull(mup.username);
        assertNull(mup.email);
        assertNull(mup.org);
        assertNull(mup.phone);
        assertNull(mup.gender);
        assertNull(mup.picture);
        assertEquals(0, mup.byear);
        assertNull(mup.custom);
        assertNull(mup.customMods);
    }

    void assertGivenCustomValues(Map<String, Object> data, ModuleUserProfile mup) {

        assertEquals(data.size(), mup.custom.size());

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();//todo rework to support more types

            assertEquals(value, mup.custom.get(key));
        }
    }

    void assertGivenValues(Map<String, Object> data, ModuleUserProfile mup) {

        if (data.containsKey("name")) {
            assertEquals(data.get("name"), mup.name);
        } else {
            assertNull(mup.name);
        }

        if (data.containsKey("username")) {
            assertEquals(data.get("username"), mup.username);
        } else {
            assertNull(mup.username);
        }

        if (data.containsKey("email")) {
            assertEquals(data.get("email"), mup.email);
        } else {
            assertNull(mup.email);
        }

        if (data.containsKey("organization")) {
            assertEquals(data.get("organization"), mup.org);
        } else {
            assertNull(mup.org);
        }

        if (data.containsKey("phone")) {
            assertEquals(data.get("phone"), mup.phone);
        } else {
            assertNull(mup.phone);
        }

        if (data.containsKey("picture")) {
            assertEquals(data.get("picture"), mup.picture);
        } else {
            assertNull(mup.picture);
        }

        if (data.containsKey("picturePath")) {
            assertEquals(data.get("picturePath"), mup.picturePath);
        } else {
            assertNull(mup.picturePath);
        }

        if (data.containsKey("gender")) {
            assertEquals(data.get("gender"), mup.gender);
        } else {
            assertNull(mup.gender);
        }

        if (data.containsKey("byear")) {
            assertEquals(Integer.parseInt((String) data.get("byear")), mup.byear);
        } else {
            assertEquals(0, mup.byear);
        }
    }

    HashMap<String, Object> createSetData_1() {
        Random rnd = new Random();
        HashMap<String, Object> data = new HashMap<>();
        data.put("name", "Test Test" + rnd.nextInt());
        data.put("username", "test" + rnd.nextInt());
        data.put("email", "test@gmail.com" + rnd.nextInt());
        data.put("organization", "Tester" + rnd.nextInt());
        data.put("phone", "+1234567890" + rnd.nextInt());
        data.put("gender", "M" + rnd.nextInt());
        data.put("picture", "http://domain.com/test.png" + rnd.nextInt());
        data.put("byear", "" + rnd.nextInt(100_000));

        return data;
    }

    HashMap<String, Object> createCustomSetData_1() {
        Random rnd = new Random();
        HashMap<String, Object> customdata = new HashMap<>();
        customdata.put("key" + rnd.nextInt(), "value" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());

        return customdata;
    }

    @Test
    public void testSetData() {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        HashMap<String, Object> data = new HashMap<>();
        data.put("name", "Test Test");
        data.put("username", "test");
        data.put("email", "test@gmail.com");
        data.put("organization", "Tester");
        data.put("phone", "+1234567890");
        data.put("gender", "M");
        data.put("picture", "http://domain.com/test.png");
        data.put("byear", "2000");
        data.put("key12", "value1");
        data.put("key22", "value2");
        mCountly.userProfile().setProperties(data);

        assertEquals("Test Test", mCountly.moduleUserProfile.name);
        assertEquals("test", mCountly.moduleUserProfile.username);
        assertEquals("test@gmail.com", mCountly.moduleUserProfile.email);
        assertEquals("Tester", mCountly.moduleUserProfile.org);
        assertEquals("+1234567890", mCountly.moduleUserProfile.phone);
        assertEquals("M", mCountly.moduleUserProfile.gender);
        assertEquals("http://domain.com/test.png", mCountly.moduleUserProfile.picture);
        assertEquals(2000, mCountly.moduleUserProfile.byear);
        assertEquals(false, mCountly.moduleUserProfile.isSynced);
        assertEquals(2, mCountly.moduleUserProfile.custom.size());
        assertEquals("value1", data.get("key12"));
        assertEquals("value2", data.get("key22"));
    }

    @Test
    public void testSetData_2() {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        assertAllValuesNull(mCountly.moduleUserProfile);

        HashMap<String, Object> data = createSetData_1();
        HashMap<String, Object> customData = createCustomSetData_1();

        mCountly.userProfile().setProperties(data);
        mCountly.userProfile().setProperties(customData);

        assertGivenValues(data, mCountly.moduleUserProfile);
        assertGivenCustomValues(customData, mCountly.moduleUserProfile);
    }

    @Test
    public void testCustomData() {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        HashMap<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");
        mCountly.userProfile().setProperties(data);
        mCountly.userProfile().setProperty("key_prop", "value_prop");

        assertEquals("value1", mCountly.moduleUserProfile.custom.get("key1"));
        assertEquals("value2", mCountly.moduleUserProfile.custom.get("key2"));
        assertEquals("value_prop", mCountly.moduleUserProfile.custom.get("key_prop"));
    }

    @Test
    public void testCustomData_2() {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        HashMap<String, Object> data = createCustomSetData_1();
        mCountly.userProfile().setProperties(data);

        assertGivenCustomValues(data, mCountly.moduleUserProfile);
    }

    @Test
    public void testCustomModifiers() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        mCountly.moduleUserProfile.modifyCustomData("key_inc", 1, "$inc");
        mCountly.moduleUserProfile.modifyCustomData("key_mul", 2, "$mul");
        mCountly.moduleUserProfile.modifyCustomData("key_set", "test1", "$addToSet");
        mCountly.moduleUserProfile.modifyCustomData("key_set", "test2", "$addToSet");

        assertEquals(1, mCountly.moduleUserProfile.customMods.get("key_inc").getInt("$inc"));
        assertEquals(2, mCountly.moduleUserProfile.customMods.get("key_mul").getInt("$mul"));
        assertEquals("test1", mCountly.moduleUserProfile.customMods.get("key_set").getJSONArray("$addToSet").getString(0));
        assertEquals("test2", mCountly.moduleUserProfile.customMods.get("key_set").getJSONArray("$addToSet").getString(1));
    }

    @Test
    public void testClear() {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        mCountly.userProfile().clear();
        assertAllValuesNull(mCountly.moduleUserProfile);

        HashMap<String, Object> data = createSetData_1();
        mCountly.userProfile().setProperties(data);
        assertGivenValues(data, mCountly.moduleUserProfile);

        mCountly.userProfile().clear();

        assertNull(mCountly.moduleUserProfile.name);
        assertNull(mCountly.moduleUserProfile.username);
        assertNull(mCountly.moduleUserProfile.email);
        assertNull(mCountly.moduleUserProfile.org);
        assertNull(mCountly.moduleUserProfile.phone);
        assertNull(mCountly.moduleUserProfile.gender);
        assertNull(mCountly.moduleUserProfile.picture);
        assertEquals(0, mCountly.moduleUserProfile.byear);
        assertNull(mCountly.moduleUserProfile.custom);
        assertNull(mCountly.moduleUserProfile.customMods);
    }

    @Test
    public void testJSON() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        HashMap<String, Object> data = new HashMap<>();
        data.put("name", "Test Test");
        data.put("username", "test");
        data.put("email", "test@gmail.com");
        data.put("organization", "Tester");
        data.put("phone", "+1234567890");
        data.put("gender", "M");
        data.put("picture", "http://domain.com/test.png");
        data.put("byear", "2000");
        mCountly.userProfile().setProperties(data);

        HashMap<String, Object> customdata = new HashMap<>();
        customdata.put("key1", "value1");
        customdata.put("key2", "value2");
        mCountly.userProfile().setProperties(customdata);

        mCountly.userProfile().setProperty("key_prop", "value_prop");
        mCountly.moduleUserProfile.modifyCustomData("key_inc", 1, "$inc");
        mCountly.moduleUserProfile.modifyCustomData("key_mul", 2, "$mul");
        mCountly.moduleUserProfile.modifyCustomData("key_set", "test1", "$addToSet");
        mCountly.moduleUserProfile.modifyCustomData("key_set", "test2", "$addToSet");

        JSONObject json = mCountly.moduleUserProfile.toJSON();
        assertEquals("Test Test", json.getString("name"));
        assertEquals("test", json.getString("username"));
        assertEquals("test@gmail.com", json.getString("email"));
        assertEquals("Tester", json.getString("organization"));
        assertEquals("+1234567890", json.getString("phone"));
        assertEquals("M", json.getString("gender"));
        assertEquals("http://domain.com/test.png", json.getString("picture"));
        assertEquals(2000, json.getInt("byear"));
        assertEquals("value1", json.getJSONObject("custom").getString("key1"));
        assertEquals("value2", json.getJSONObject("custom").getString("key2"));
        assertEquals("value_prop", json.getJSONObject("custom").getString("key_prop"));
        assertEquals(1, json.getJSONObject("custom").getJSONObject("key_inc").getInt("$inc"));
        assertEquals(2, json.getJSONObject("custom").getJSONObject("key_mul").getInt("$mul"));
        assertEquals("test1", json.getJSONObject("custom").getJSONObject("key_set").getJSONArray("$addToSet").getString(0));
        assertEquals("test2", json.getJSONObject("custom").getJSONObject("key_set").getJSONArray("$addToSet").getString(1));
    }

    @Test
    public void testJSON_2() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        assertAllValuesNull(mCountly.moduleUserProfile);

        HashMap<String, Object> data = createSetData_1();
        HashMap<String, Object> customData = createCustomSetData_1();

        mCountly.userProfile().setProperties(data);
        mCountly.userProfile().setProperties(customData);

        assertGivenValues(data, mCountly.moduleUserProfile);
        assertGivenCustomValues(customData, mCountly.moduleUserProfile);

        JSONObject json = mCountly.moduleUserProfile.toJSON();

        mCountly.userProfile().clear();
        assertAllValuesNull(mCountly.moduleUserProfile);

        mCountly.moduleUserProfile.fromJSON(json);

        assertGivenValues(data, mCountly.moduleUserProfile);
        assertGivenCustomValues(customData, mCountly.moduleUserProfile);
    }

    @Test
    public void testJSON_3() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        assertAllValuesNull(mCountly.moduleUserProfile);

        JSONObject json = mCountly.moduleUserProfile.toJSON();

        mCountly.userProfile().clear();
        assertAllValuesNull(mCountly.moduleUserProfile);

        mCountly.moduleUserProfile.fromJSON(json);
        assertAllValuesNull(mCountly.moduleUserProfile);
    }

    @Test
    public void testGetDataForRequest() {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        assertAllValuesNull(mCountly.moduleUserProfile);

        HashMap<String, Object> data = createSetData_1();
        HashMap<String, Object> customData = createCustomSetData_1();

        mCountly.userProfile().setProperties(data);
        mCountly.userProfile().setProperties(customData);

        String req = mCountly.moduleUserProfile.getDataForRequest();

        Assert.assertTrue(req.contains("&user_details="));
        Assert.assertTrue(req.contains("username"));
        Assert.assertTrue(req.contains("email"));
        Assert.assertTrue(req.contains("organization"));
        Assert.assertTrue(req.contains("picture"));
        Assert.assertTrue(req.contains("gender"));
        Assert.assertTrue(req.contains("custom"));
        Assert.assertTrue(req.contains("byear"));
    }

    /**
     * Test that custom data keys are truncated to the maximum allowed length (10)
     * Due to truncation, the keys "hair_color_id" and "hair_color_tone" will be merged into "hair_color"
     * The value of "hair_color" will be the value of "hair_color_tone" since it was set last
     * The value of "hair_skin_tone" will be truncated to "hair_skin_"
     * Tha last value of "hair_color" will be "black"
     * And predefined key "picturePath" is not truncated
     */
    @Test
    public void internalLimit_testCustomData() {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.sdkInternalLimits.setMaxKeyLength(10);
        mCountly.init(config);

        HashMap<String, Object> data = new HashMap<>();
        data.put("hair_color_id", 4567);
        data.put("hair_color_tone", "bold");
        mCountly.userProfile().setProperties(data);
        assertEquals(1, mCountly.moduleUserProfile.custom.size());
        assertEquals("bold", mCountly.moduleUserProfile.custom.get("hair_color"));

        mCountly.userProfile().setProperty("hair_color", "black");
        mCountly.userProfile().setProperty("hair_skin_tone", "yellow");
        mCountly.userProfile().setProperty("picturePath", "Test Test");
        assertEquals(2, mCountly.moduleUserProfile.custom.size());
        assertNull(ModuleUserProfile.picturePath);
        assertEquals("black", mCountly.moduleUserProfile.custom.get("hair_color"));
        assertEquals("yellow", mCountly.moduleUserProfile.custom.get("hair_skin_"));
    }

    /**
     * Test that custom data keys are truncated to the maximum allowed length (10)
     * Due to truncation, for push keys, the keys "reminder" and "rock" will be merged into same key
     */
    @Test
    public void internalLimit_testCustomModifiers() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.sdkInternalLimits.setMaxKeyLength(10);
        mCountly.init(config);

        mCountly.moduleUserProfile.modifyCustomData("key_inc_with", 1, "$inc");
        mCountly.moduleUserProfile.modifyCustomData("key_mul_width", 2, "$mul");
        mCountly.userProfile().push("key_push_reminder", "test1");
        mCountly.userProfile().push("key_push_rock", "test3");

        assertEquals(1, mCountly.moduleUserProfile.customMods.get("key_inc_wi").getInt("$inc"));
        assertEquals(2, mCountly.moduleUserProfile.customMods.get("key_mul_wi").getInt("$mul"));
        assertEquals(2, mCountly.moduleUserProfile.customMods.get("key_push_r").getJSONArray("$push").length());
        assertEquals("test1", mCountly.moduleUserProfile.customMods.get("key_push_r").getJSONArray("$push").getString(0));
        assertEquals("test3", mCountly.moduleUserProfile.customMods.get("key_push_r").getJSONArray("$push").getString(1));
    }

    /**
     * "setProperties" with both custom and predefined properties
     * custom properties should be truncated but predefined properties should not be truncated
     * validate that the predefined properties are not truncated
     */
    @Test
    public void internalLimit_setProperties() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.sdkInternalLimits.setMaxKeyLength(2);
        mCountly.init(config);

        Countly.sharedInstance().userProfile().setProperties(TestUtils.map(
            ModuleUserProfile.BYEAR_KEY, 2000,
            ModuleUserProfile.EMAIL_KEY, "email",
            ModuleUserProfile.GENDER_KEY, "Male",
            ModuleUserProfile.PHONE_KEY, "phone",
            ModuleUserProfile.ORG_KEY, "org",
            ModuleUserProfile.USERNAME_KEY, "username",
            ModuleUserProfile.NAME_KEY, "name",
            ModuleUserProfile.PICTURE_KEY, "picture",
            "custom1", "value1",
            "custom2", 23,
            "hair", "black"
        ));
        Countly.sharedInstance().userProfile().save();

        validateUserProfileRequest(mCountly, TestUtils.map(
                ModuleUserProfile.BYEAR_KEY, 2000,
                ModuleUserProfile.EMAIL_KEY, "email",
                ModuleUserProfile.GENDER_KEY, "Male",
                ModuleUserProfile.PHONE_KEY, "phone",
                ModuleUserProfile.ORG_KEY, "org",
                ModuleUserProfile.USERNAME_KEY, "username",
                ModuleUserProfile.NAME_KEY, "name",
                ModuleUserProfile.PICTURE_KEY, "picture"
            ), TestUtils.map(
                "cu", "23", // because in user profiles, all values are stored as strings
                "ha", "black")
        );
    }

    private void validateUserProfileRequest(Countly countly, Map<String, Object> predefined, Map<String, Object> custom) throws JSONException {
        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);
        JSONObject userDetails = new JSONObject(RQ[0].get("user_details"));
        Assert.assertEquals(userDetails.length(), predefined.size() + 1);
        JSONObject customData = userDetails.getJSONObject("custom");
        Assert.assertEquals(customData.length(), custom.size());
        userDetails.remove("custom");
        for (Map.Entry<String, Object> entry : predefined.entrySet()) {
            Assert.assertEquals(entry.getValue(), userDetails.get(entry.getKey()));
        }

        for (Map.Entry<String, Object> entry : custom.entrySet()) {
            Assert.assertEquals(entry.getValue(), customData.get(entry.getKey()));
        }
    }
}
