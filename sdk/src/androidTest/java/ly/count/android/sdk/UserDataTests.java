package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class UserDataTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @Test
    public void testSetData() {
        UserData.clear();

        HashMap<String, String> data = new HashMap<>();
        data.put("name", "Test Test");
        data.put("username", "test");
        data.put("email", "test@gmail.com");
        data.put("organization", "Tester");
        data.put("phone", "+1234567890");
        data.put("gender", "M");
        data.put("picture", "http://domain.com/test.png");
        data.put("byear", "2000");
        UserData.setData(data);

        HashMap<String, String> customdata = new HashMap<>();
        customdata.put("key12", "value1");
        customdata.put("key22", "value2");
        UserData.setCustomData(customdata);

        assertEquals("Test Test", UserData.name);
        assertEquals("test", UserData.username);
        assertEquals("test@gmail.com", UserData.email);
        assertEquals("Tester", UserData.org);
        assertEquals("+1234567890", UserData.phone);
        assertEquals("M", UserData.gender);
        assertEquals("http://domain.com/test.png", UserData.picture);
        assertEquals(2000, UserData.byear);
        assertEquals(false, UserData.isSynced);
        assertEquals(customdata, UserData.custom);
    }

    @Test
    public void testSetData_2() {
        UserData.clear();
        assertAllValuesNull();

        HashMap<String, String> data = createSetData_1();
        HashMap<String, String> customData = createCustomSetData_1();

        UserData.setData(data);
        UserData.setCustomData(customData);

        assertGivenValues(data);
        assertGivenCustomValues(customData);
    }

    @Test
    public void testCustomData() {
        UserData.clear();

        HashMap<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");
        UserData.setCustomData(data);
        UserData.setCustomProperty("key_prop", "value_prop");

        assertEquals("value1", UserData.custom.get("key1"));
        assertEquals("value2", UserData.custom.get("key2"));
        assertEquals("value_prop", UserData.custom.get("key_prop"));
    }

    @Test
    public void testCustomData_2() {
        UserData.clear();

        HashMap<String, String> data = createCustomSetData_1();
        UserData.setCustomData(data);

        assertGivenCustomValues(data);
    }

    @Test
    public void testCustomModifiers() throws JSONException {
        ModuleUserProfile.modifyCustomData("key_inc", 1, "$inc");
        ModuleUserProfile.modifyCustomData("key_mul", 2, "$mul");
        ModuleUserProfile.modifyCustomData("key_set", "test1", "$addToSet");
        ModuleUserProfile.modifyCustomData("key_set", "test2", "$addToSet");

        assertEquals(1, UserData.customMods.get("key_inc").getInt("$inc"));
        assertEquals(2, UserData.customMods.get("key_mul").getInt("$mul"));
        assertEquals("test1", UserData.customMods.get("key_set").getJSONArray("$addToSet").getString(0));
        assertEquals("test2", UserData.customMods.get("key_set").getJSONArray("$addToSet").getString(1));
    }

    @Test
    public void testClear() {
        UserData.clear();
        assertAllValuesNull();

        HashMap<String, String> data = createSetData_1();
        UserData.setData(data);
        assertGivenValues(data);

        UserData.clear();

        assertNull(UserData.name);
        assertNull(UserData.username);
        assertNull(UserData.email);
        assertNull(UserData.org);
        assertNull(UserData.phone);
        assertNull(UserData.gender);
        assertNull(UserData.picture);
        assertEquals(0, UserData.byear);
        assertNull(UserData.custom);
        assertNull(UserData.customMods);
    }

    @Test
    public void testJSON() throws JSONException {
        HashMap<String, String> data = new HashMap<>();
        data.put("name", "Test Test");
        data.put("username", "test");
        data.put("email", "test@gmail.com");
        data.put("organization", "Tester");
        data.put("phone", "+1234567890");
        data.put("gender", "M");
        data.put("picture", "http://domain.com/test.png");
        data.put("byear", "2000");
        UserData.setData(data);

        HashMap<String, String> customdata = new HashMap<>();
        customdata.put("key1", "value1");
        customdata.put("key2", "value2");
        UserData.setCustomData(customdata);

        UserData.setCustomProperty("key_prop", "value_prop");
        ModuleUserProfile.modifyCustomData("key_inc", 1, "$inc");
        ModuleUserProfile.modifyCustomData("key_mul", 2, "$mul");
        ModuleUserProfile.modifyCustomData("key_set", "test1", "$addToSet");
        ModuleUserProfile.modifyCustomData("key_set", "test2", "$addToSet");

        JSONObject json = ModuleUserProfile.toJSON();
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
        UserData.clear();
        assertAllValuesNull();

        HashMap<String, String> data = createSetData_1();
        HashMap<String, String> customData = createCustomSetData_1();

        UserData.setData(data);
        UserData.setCustomData(customData);

        assertGivenValues(data);
        assertGivenCustomValues(customData);

        JSONObject json = ModuleUserProfile.toJSON();

        UserData.clear();
        assertAllValuesNull();

        ModuleUserProfile.fromJSON(json);

        assertGivenValues(data);
        assertGivenCustomValues(customData);
    }

    @Test
    public void testJSON_3() throws JSONException {
        UserData.clear();
        assertAllValuesNull();

        JSONObject json = ModuleUserProfile.toJSON();

        UserData.clear();
        assertAllValuesNull();

        ModuleUserProfile.fromJSON(json);
        assertAllValuesNull();
    }

    @Test
    public void testPicturePath() throws MalformedURLException {
        String path = "http://test.com/?key1=val1&picturePath=%2Fmnt%2Fsdcard%2Fpic.jpg&key2=val2";
        String picturePath = ModuleUserProfile.getPicturePathFromQuery(new URL(path));
        assertEquals("/mnt/sdcard/pic.jpg", picturePath);
    }

    @Test
    public void testGetDataForRequest() {
        UserData.clear();
        assertAllValuesNull();

        HashMap<String, String> data = createSetData_1();
        HashMap<String, String> customData = createCustomSetData_1();

        UserData.setData(data);
        UserData.setCustomData(customData);

        String req = ModuleUserProfile.getDataForRequest();

        Assert.assertTrue(req.contains("&user_details="));
        Assert.assertTrue(req.contains("username"));
        Assert.assertTrue(req.contains("email"));
        Assert.assertTrue(req.contains("organization"));
        Assert.assertTrue(req.contains("picture"));
        Assert.assertTrue(req.contains("gender"));
        Assert.assertTrue(req.contains("custom"));
        Assert.assertTrue(req.contains("byear"));
    }

    HashMap<String, String> createSetData_1() {
        Random rnd = new Random();
        HashMap<String, String> data = new HashMap<>();
        data.put("name", "Test Test" + rnd.nextInt());
        data.put("username", "test" + rnd.nextInt());
        data.put("email", "test@gmail.com" + rnd.nextInt());
        data.put("organization", "Tester" + rnd.nextInt());
        data.put("phone", "+1234567890" + rnd.nextInt());
        data.put("gender", "M" + rnd.nextInt());
        data.put("picture", "http://domain.com/test.png" + rnd.nextInt());
        data.put("byear", "" + rnd.nextInt(100000));

        return data;
    }

    HashMap<String, String> createCustomSetData_1() {
        Random rnd = new Random();
        HashMap<String, String> customdata = new HashMap<>();
        customdata.put("key" + rnd.nextInt(), "value" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());
        customdata.put("key" + rnd.nextInt(), "value2" + rnd.nextInt());

        return customdata;
    }

    void assertGivenCustomValues(Map<String, String> data) {

        assertEquals(data.size(), UserData.custom.size());

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            assertEquals(value, UserData.custom.get(key));
        }
    }

    void assertGivenValues(Map<String, String> data) {

        if (data.containsKey("name")) {
            assertEquals(data.get("name"), UserData.name);
        } else {
            assertNull(UserData.name);
        }

        if (data.containsKey("username")) {
            assertEquals(data.get("username"), UserData.username);
        } else {
            assertNull(UserData.username);
        }

        if (data.containsKey("email")) {
            assertEquals(data.get("email"), UserData.email);
        } else {
            assertNull(UserData.email);
        }

        if (data.containsKey("organization")) {
            assertEquals(data.get("organization"), UserData.org);
        } else {
            assertNull(UserData.org);
        }

        if (data.containsKey("phone")) {
            assertEquals(data.get("phone"), UserData.phone);
        } else {
            assertNull(UserData.phone);
        }

        if (data.containsKey("picture")) {
            assertEquals(data.get("picture"), UserData.picture);
        } else {
            assertNull(UserData.picture);
        }

        if (data.containsKey("picturePath")) {
            assertEquals(data.get("picturePath"), UserData.picturePath);
        } else {
            assertNull(UserData.picturePath);
        }

        if (data.containsKey("gender")) {
            assertEquals(data.get("gender"), UserData.gender);
        } else {
            assertNull(UserData.gender);
        }

        if (data.containsKey("byear")) {
            assertEquals(Integer.parseInt(data.get("byear")), UserData.byear);
        } else {
            assertEquals(0, UserData.byear);
        }
    }

    void assertAllValuesNull() {
        assertNull(UserData.name);
        assertNull(UserData.username);
        assertNull(UserData.email);
        assertNull(UserData.org);
        assertNull(UserData.phone);
        assertNull(UserData.gender);
        assertNull(UserData.picture);
        assertEquals(0, UserData.byear);
        assertNull(UserData.custom);
        assertNull(UserData.customMods);
    }
}
