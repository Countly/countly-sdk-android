package ly.count.android.sdk;

import android.support.test.runner.AndroidJUnit4;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;


import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UserDataTests {

    @Test
	public void testSetData(){
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("name", "Test Test");
		data.put("username", "test");
		data.put("email", "test@gmail.com");
		data.put("organization", "Tester");
		data.put("phone", "+1234567890");
		data.put("gender", "M");
		data.put("picture", "http://domain.com/test.png");
		data.put("byear", "2000");
        UserData.setData(data);
        
        assertEquals("Test Test", UserData.name);
        assertEquals("test", UserData.username);
        assertEquals("test@gmail.com", UserData.email);
        assertEquals("Tester", UserData.org);
        assertEquals("+1234567890", UserData.phone);
        assertEquals("M", UserData.gender);
        assertEquals("http://domain.com/test.png", UserData.picture);
        assertEquals(2000, UserData.byear);
	}

    @Test
    public void testCustomData() {
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("key1", "value1");
        data.put("key2", "value2");
        UserData.setCustomData(data);
        UserData.setCustomProperty("key_prop", "value_prop");

        assertEquals("value1", UserData.custom.get("key1"));
        assertEquals("value2", UserData.custom.get("key2"));
        assertEquals("value_prop", UserData.custom.get("key_prop"));
    }

    @Test
    public void testCustomModifiers() throws JSONException {
        UserData.modifyCustomData("key_inc", 1, "$inc");
        UserData.modifyCustomData("key_mul", 2, "$mul");
        UserData.modifyCustomData("key_set", "test1", "$addToSet");
        UserData.modifyCustomData("key_set", "test2", "$addToSet");

        assertEquals(1, UserData.customMods.get("key_inc").getInt("$inc"));
        assertEquals(2, UserData.customMods.get("key_mul").getInt("$mul"));
        assertEquals("test1", UserData.customMods.get("key_set").getJSONArray("$addToSet").getString(0));
        assertEquals("test2", UserData.customMods.get("key_set").getJSONArray("$addToSet").getString(1));
    }

    @Test
    public void testClear() {
        UserData.clear();

        assertEquals(null, UserData.name);
        assertEquals(null, UserData.username);
        assertEquals(null, UserData.email);
        assertEquals(null, UserData.org);
        assertEquals(null, UserData.phone);
        assertEquals(null, UserData.gender);
        assertEquals(null, UserData.picture);
        assertEquals(0, UserData.byear);
        assertEquals(null, UserData.custom);
        assertEquals(null, UserData.customMods);
    }

    @Test
	public void testJSON() throws JSONException{
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("name", "Test Test");
        data.put("username", "test");
        data.put("email", "test@gmail.com");
        data.put("organization", "Tester");
        data.put("phone", "+1234567890");
        data.put("gender", "M");
        data.put("picture", "http://domain.com/test.png");
        data.put("byear", "2000");
        UserData.setData(data);

        HashMap<String, String> customdata = new HashMap<String, String>();
        customdata.put("key1", "value1");
        customdata.put("key2", "value2");
        UserData.setCustomData(customdata);

        UserData.setCustomProperty("key_prop", "value_prop");
        UserData.modifyCustomData("key_inc", 1, "$inc");
        UserData.modifyCustomData("key_mul", 2, "$mul");
        UserData.modifyCustomData("key_set", "test1", "$addToSet");
        UserData.modifyCustomData("key_set", "test2", "$addToSet");

		JSONObject json = UserData.toJSON();
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
	public void testPicturePath() throws MalformedURLException{
		String path = "http://test.com/?key1=val1&picturePath=%2Fmnt%2Fsdcard%2Fpic.jpg&key2=val2";
		String picturePath = UserData.getPicturePathFromQuery(new URL(path));
		assertEquals("/mnt/sdcard/pic.jpg", picturePath);
	}
}
