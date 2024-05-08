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
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class ModuleUserProfileTests {
    CountlyStore store;

    @Before
    public void setUp() {
        Countly.sharedInstance().halt();
        store = new CountlyStore(TestUtils.getContext(), Mockito.mock(ModuleLog.class));
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

        Assert.assertEquals(1, store.getRequests().length);
    }

    /**
     * When saving user profile changes, it empties EQ into RQ
     */
    @Test
    public void SavingWritesEQIntoRQ() {
        Countly mCountly = Countly.sharedInstance();//todo move away from static init after static user profile has been removed
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        Assert.assertEquals(0, store.getEvents().length);
        Assert.assertEquals(0, store.getRequests().length);

        mCountly.events().recordEvent("a");
        Assert.assertEquals(1, store.getEvents().length);//todo test fails with this being 0
        Assert.assertEquals(0, store.getRequests().length);

        mCountly.userProfile().setProperty("name", "Test Test");
        mCountly.userProfile().save();

        String[] reqs = store.getRequests();
        Assert.assertEquals(0, store.getEvents().length);
        Assert.assertEquals(2, reqs.length);
        Assert.assertTrue(reqs[0].contains("events"));
        Assert.assertFalse(reqs[1].contains("events"));
    }

    // BELLOW TESTS THAT NEED TO BE REWORKED

    void assertAllValuesNull(ModuleUserProfile mup) {
        Assert.assertNull(mup.name);
        Assert.assertNull(mup.username);
        Assert.assertNull(mup.email);
        Assert.assertNull(mup.org);
        Assert.assertNull(mup.phone);
        Assert.assertNull(mup.gender);
        Assert.assertNull(mup.picture);
        Assert.assertEquals(0, mup.byear);
        Assert.assertNull(mup.custom);
        Assert.assertNull(mup.customMods);
    }

    void assertGivenCustomValues(Map<String, Object> data, ModuleUserProfile mup) {

        Assert.assertEquals(data.size(), mup.custom.size());

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();//todo rework to support more types

            Assert.assertEquals(value, mup.custom.get(key));
        }
    }

    void assertGivenValues(Map<String, Object> data, ModuleUserProfile mup) {

        if (data.containsKey("name")) {
            Assert.assertEquals(data.get("name"), mup.name);
        } else {
            Assert.assertNull(mup.name);
        }

        if (data.containsKey("username")) {
            Assert.assertEquals(data.get("username"), mup.username);
        } else {
            Assert.assertNull(mup.username);
        }

        if (data.containsKey("email")) {
            Assert.assertEquals(data.get("email"), mup.email);
        } else {
            Assert.assertNull(mup.email);
        }

        if (data.containsKey("organization")) {
            Assert.assertEquals(data.get("organization"), mup.org);
        } else {
            Assert.assertNull(mup.org);
        }

        if (data.containsKey("phone")) {
            Assert.assertEquals(data.get("phone"), mup.phone);
        } else {
            Assert.assertNull(mup.phone);
        }

        if (data.containsKey("picture")) {
            Assert.assertEquals(data.get("picture"), mup.picture);
        } else {
            Assert.assertNull(mup.picture);
        }

        if (data.containsKey("picturePath")) {
            Assert.assertEquals(data.get("picturePath"), mup.picturePath);
        } else {
            Assert.assertNull(mup.picturePath);
        }

        if (data.containsKey("gender")) {
            Assert.assertEquals(data.get("gender"), mup.gender);
        } else {
            Assert.assertNull(mup.gender);
        }

        if (data.containsKey("byear")) {
            Assert.assertEquals(Integer.parseInt((String) data.get("byear")), mup.byear);
        } else {
            Assert.assertEquals(0, mup.byear);
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

        Assert.assertEquals("Test Test", mCountly.moduleUserProfile.name);
        Assert.assertEquals("test", mCountly.moduleUserProfile.username);
        Assert.assertEquals("test@gmail.com", mCountly.moduleUserProfile.email);
        Assert.assertEquals("Tester", mCountly.moduleUserProfile.org);
        Assert.assertEquals("+1234567890", mCountly.moduleUserProfile.phone);
        Assert.assertEquals("M", mCountly.moduleUserProfile.gender);
        Assert.assertEquals("http://domain.com/test.png", mCountly.moduleUserProfile.picture);
        Assert.assertEquals(2000, mCountly.moduleUserProfile.byear);
        Assert.assertEquals(false, mCountly.moduleUserProfile.isSynced);
        Assert.assertEquals(2, mCountly.moduleUserProfile.custom.size());
        Assert.assertEquals("value1", data.get("key12"));
        Assert.assertEquals("value2", data.get("key22"));
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

        Assert.assertEquals("value1", mCountly.moduleUserProfile.custom.get("key1"));
        Assert.assertEquals("value2", mCountly.moduleUserProfile.custom.get("key2"));
        Assert.assertEquals("value_prop", mCountly.moduleUserProfile.custom.get("key_prop"));
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

        Assert.assertEquals(1, mCountly.moduleUserProfile.customMods.get("key_inc").getInt("$inc"));
        Assert.assertEquals(2, mCountly.moduleUserProfile.customMods.get("key_mul").getInt("$mul"));
        Assert.assertEquals("test1", mCountly.moduleUserProfile.customMods.get("key_set").getJSONArray("$addToSet").getString(0));
        Assert.assertEquals("test2", mCountly.moduleUserProfile.customMods.get("key_set").getJSONArray("$addToSet").getString(1));
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

        Assert.assertNull(mCountly.moduleUserProfile.name);
        Assert.assertNull(mCountly.moduleUserProfile.username);
        Assert.assertNull(mCountly.moduleUserProfile.email);
        Assert.assertNull(mCountly.moduleUserProfile.org);
        Assert.assertNull(mCountly.moduleUserProfile.phone);
        Assert.assertNull(mCountly.moduleUserProfile.gender);
        Assert.assertNull(mCountly.moduleUserProfile.picture);
        Assert.assertEquals(0, mCountly.moduleUserProfile.byear);
        Assert.assertNull(mCountly.moduleUserProfile.custom);
        Assert.assertNull(mCountly.moduleUserProfile.customMods);
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
        Assert.assertEquals("Test Test", json.getString("name"));
        Assert.assertEquals("test", json.getString("username"));
        Assert.assertEquals("test@gmail.com", json.getString("email"));
        Assert.assertEquals("Tester", json.getString("organization"));
        Assert.assertEquals("+1234567890", json.getString("phone"));
        Assert.assertEquals("M", json.getString("gender"));
        Assert.assertEquals("http://domain.com/test.png", json.getString("picture"));
        Assert.assertEquals(2000, json.getInt("byear"));
        Assert.assertEquals("value1", json.getJSONObject("custom").getString("key1"));
        Assert.assertEquals("value2", json.getJSONObject("custom").getString("key2"));
        Assert.assertEquals("value_prop", json.getJSONObject("custom").getString("key_prop"));
        Assert.assertEquals(1, json.getJSONObject("custom").getJSONObject("key_inc").getInt("$inc"));
        Assert.assertEquals(2, json.getJSONObject("custom").getJSONObject("key_mul").getInt("$mul"));
        Assert.assertEquals("test1", json.getJSONObject("custom").getJSONObject("key_set").getJSONArray("$addToSet").getString(0));
        Assert.assertEquals("test2", json.getJSONObject("custom").getJSONObject("key_set").getJSONArray("$addToSet").getString(1));
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
        Assert.assertEquals(1, mCountly.moduleUserProfile.custom.size());
        Assert.assertEquals("bold", mCountly.moduleUserProfile.custom.get("hair_color"));

        mCountly.userProfile().setProperty("hair_color", "black");
        mCountly.userProfile().setProperty("hair_skin_tone", "yellow");
        mCountly.userProfile().setProperty("picturePath", "Test Test");
        Assert.assertEquals(2, mCountly.moduleUserProfile.custom.size());
        Assert.assertNull(ModuleUserProfile.picturePath);
        Assert.assertEquals("black", mCountly.moduleUserProfile.custom.get("hair_color"));
        Assert.assertEquals("yellow", mCountly.moduleUserProfile.custom.get("hair_skin_"));
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

        Assert.assertEquals(1, mCountly.moduleUserProfile.customMods.get("key_inc_wi").getInt("$inc"));
        Assert.assertEquals(2, mCountly.moduleUserProfile.customMods.get("key_mul_wi").getInt("$mul"));
        Assert.assertEquals(2, mCountly.moduleUserProfile.customMods.get("key_push_r").getJSONArray("$push").length());
        Assert.assertEquals("test1", mCountly.moduleUserProfile.customMods.get("key_push_r").getJSONArray("$push").getString(0));
        Assert.assertEquals("test3", mCountly.moduleUserProfile.customMods.get("key_push_r").getJSONArray("$push").getString(1));
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

        validateUserProfileRequest(TestUtils.map(
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

    /**
     * Given max value size truncates the values of the:
     * - Custom user property values
     * - user property values except picture
     * Validate all values are truncated to the max value size that is 2
     * And validate non-String values are not clipped
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimit_setProperties_maxValueSize() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxValueSize(2);
        mCountly.init(config);

        Object obj = new Object();
        Countly.sharedInstance().userProfile().setProperties(TestUtils.map(
            ModuleUserProfile.BYEAR_KEY, 2000,
            ModuleUserProfile.EMAIL_KEY, "email",
            ModuleUserProfile.GENDER_KEY, "Male",
            ModuleUserProfile.PHONE_KEY, "phone",
            ModuleUserProfile.ORG_KEY, "org",
            ModuleUserProfile.USERNAME_KEY, "username",
            ModuleUserProfile.NAME_KEY, "name",
            ModuleUserProfile.PICTURE_KEY, "picture",
            ModuleUserProfile.PICTURE_PATH_KEY, "TestTest",
            "custom1", "value1",
            "custom2", 23,
            "hair", "black",
            "custom3", 1234,
            "custom4", 1234.5,
            "custom5", true,
            "custom6", obj
        ));
        Countly.sharedInstance().userProfile().save();

        validateUserProfileRequest(TestUtils.map(
                ModuleUserProfile.BYEAR_KEY, 2000,
                ModuleUserProfile.EMAIL_KEY, "em",
                ModuleUserProfile.GENDER_KEY, "Ma",
                ModuleUserProfile.PHONE_KEY, "ph",
                ModuleUserProfile.ORG_KEY, "or",
                ModuleUserProfile.USERNAME_KEY, "us",
                ModuleUserProfile.NAME_KEY, "na",
                ModuleUserProfile.PICTURE_KEY, "picture"
            ), TestUtils.map(
                "custom1", "va", // because in user profiles, all values are stored as strings
                "custom2", "23",
                "hair", "bl",
                "custom3", "1234",
                "custom4", "1234.5",
                "custom5", "true",
                "custom6", obj.toString()) // toString() is called on non-String values
        );
    }

    /**
     * Given max value size for pictures 4096 truncates the value of the picture
     * and is not affected by the general max value size
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimit_setProperties_maxValueSizePicture() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxValueSize(2);
        mCountly.init(config);

        Object obj = new Object();
        Countly.sharedInstance().userProfile().setProperties(TestUtils.map(
            ModuleUserProfile.PICTURE_KEY,
            "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"
        ));
        Countly.sharedInstance().userProfile().save();

        validateUserProfileRequest(TestUtils.map(
                ModuleUserProfile.PICTURE_KEY,
                "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"
            ), TestUtils.map()
        );
    }

    /**
     * Given max value size truncates the values of the:
     * - Custom user property values
     * - user property values
     * Validate all values are truncated to the max value size that is 2
     * And validate non-String values are not clipped
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimit_testCustomModifiers_setMaxValueSize() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxValueSize(2);
        mCountly.init(config);

        mCountly.userProfile().incrementBy("inc", 1);
        mCountly.userProfile().multiply("mul", 2_456_789);
        mCountly.userProfile().push("rem", "ORIELY");
        mCountly.userProfile().push("rem", "HUH");
        mCountly.userProfile().pull("pll", "PULL");
        mCountly.userProfile().pushUnique("pshu", "PUSH");
        mCountly.userProfile().saveMax("sm", 455);
        mCountly.userProfile().saveMin("smi", 6789);
        mCountly.userProfile().setOnce("stc", "ONCE");

        Assert.assertEquals(1, mCountly.moduleUserProfile.customMods.get("inc").getInt("$inc"));
        Assert.assertEquals(2_456_789, mCountly.moduleUserProfile.customMods.get("mul").getInt("$mul"));
        Assert.assertEquals(2, mCountly.moduleUserProfile.customMods.get("rem").getJSONArray("$push").length());
        Assert.assertEquals("OR", mCountly.moduleUserProfile.customMods.get("rem").getJSONArray("$push").getString(0));
        Assert.assertEquals("HU", mCountly.moduleUserProfile.customMods.get("rem").getJSONArray("$push").getString(1));
        Assert.assertEquals("PU", mCountly.moduleUserProfile.customMods.get("pll").getString("$pull"));
        Assert.assertEquals("PU", mCountly.moduleUserProfile.customMods.get("pshu").getString("$addToSet"));
        Assert.assertEquals("455", mCountly.moduleUserProfile.customMods.get("sm").getString("$max"));
        Assert.assertEquals("6789", mCountly.moduleUserProfile.customMods.get("smi").getString("$min"));
        Assert.assertEquals("ON", mCountly.moduleUserProfile.customMods.get("stc").getString("$setOnce"));
    }

    /**
     * Validate that null value is eliminated from the user profile data
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void setUserProperties_null() throws JSONException {
        Countly mCountly = Countly.sharedInstance();
        mCountly.init(TestUtils.createBaseConfig());

        HashMap<String, Object> data = new HashMap<>();
        data.put("null", null);

        mCountly.userProfile().setProperties(data);
        mCountly.userProfile().save();

        validateUserProfileRequest(new HashMap<>(), new HashMap<>());
    }

    private void validateUserProfileRequest(Map<String, Object> predefined, Map<String, Object> custom) throws JSONException {
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
