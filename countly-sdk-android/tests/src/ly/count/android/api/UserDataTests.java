import ly.count.android.api.UserData;

import org.json.JSONObject;

import android.os.Bundle;
import android.test.AndroidTestCase;


public class UserDataTests extends AndroidTestCase {
	public void testSetData(){
		Bundle data = new Bundle();
		data.putString("name", "Test Test");
		data.putString("username", "test");
		data.putString("email", "test@gmail.com");
		data.putString("organization", "Tester");
		data.putString("phone", "+1234567890");
		data.putString("gender", "M");
		data.putString("picture", "http://domain.com/test.png");
		data.putInt("byear", 2000);
		
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
	
	public void testJSON(){
		JSONObject json = UserData.toJSON();
		assertEquals("Test Test", json.getString("name"));
        assertEquals("test", json.getString("username"));
        assertEquals("test@gmail.com", json.getString("email"));
        assertEquals("Tester", json.getString("organization"));
        assertEquals("+1234567890", json.getString("phone"));
        assertEquals("M", json.getString("gender"));
        assertEquals("http://domain.com/test.png", json.getString("picture"));
        assertEquals(2000, json.getString("byear"));
	}
	
	public void testPicturePath(){
		String path = "http://test.com/?key1=val1&picturePath=%2Fmnt%2Fsdcard%2Fpic.jpg&key2=val2";
		String picturePath = UserData.getPicturePathFromQuery(new URL(path));
		assertEquals("/mnt/sdcard/pic.jpg", picturePath);
	}
}
