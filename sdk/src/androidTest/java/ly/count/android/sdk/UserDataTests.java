package ly.count.android.sdk;

import android.test.AndroidTestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;


public class UserDataTests extends AndroidTestCase {
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
        assertEquals("2000", UserData.byear);
	}
	
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

		JSONObject json = UserData.toJSON();
		assertEquals("Test Test", json.getString("name"));
        assertEquals("test", json.getString("username"));
        assertEquals("test@gmail.com", json.getString("email"));
        assertEquals("Tester", json.getString("organization"));
        assertEquals("+1234567890", json.getString("phone"));
        assertEquals("M", json.getString("gender"));
        assertEquals("http://domain.com/test.png", json.getString("picture"));
        assertEquals("2000", json.getInt("byear"));
	}
	
	public void testPicturePath() throws MalformedURLException{
		String path = "http://test.com/?key1=val1&picturePath=%2Fmnt%2Fsdcard%2Fpic.jpg&key2=val2";
		String picturePath = UserData.getPicturePathFromQuery(new URL(path));
		assertEquals("/mnt/sdcard/pic.jpg", picturePath);
	}
}
