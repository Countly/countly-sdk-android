package ly.count.android.example;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import java.util.HashMap;

import ly.count.android.api.Countly;
import ly.count.android.api.DeviceId;

public class CountlyActivity extends Activity {
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

    /** You should use cloud.count.ly instead of YOUR_SERVER for the line below if you are using Countly Cloud service */
        Countly.sharedInstance().init(this, "https://YOUR_SERVER", "YOUR_APP_KEY");
		
		/*********
		 * Providing user data
		 *********/
		setUserData();

        Countly.sharedInstance().recordEvent("test", 1);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Countly.sharedInstance().recordEvent("test2", 1, 2);
            }
        }, 5000);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Countly.sharedInstance().recordEvent("test3");
            }
        }, 10000);
    }
    
    @Override
    public void onStart()
    {
    	super.onStart();
        Countly.sharedInstance().onStart();
    }

    @Override
    public void onStop()
    {
        Countly.sharedInstance().onStop();
    	super.onStop();
    }
	
	public void setUserData(){
		HashMap<String, String> data = new HashMap<String, String>();
		data.put("name", "Firstname Lastname");
		data.put("username", "nickname");
		data.put("email", "test@test.com");
		data.put("organization", "Tester");
		data.put("phone", "+123456789");
		data.put("gender", "M");
		//provide url to picture
		//data.put("picture", "http://example.com/pictures/profile_pic.png");
		//or locally from device
		//data.put("picturePath", "/mnt/sdcard/portrait.jpg");
		data.put("byear", "1987");
		
		//providing any custom key values to store with user
		HashMap<String, String> custom = new HashMap<String, String>();
		custom.put("country", "Turkey");
		custom.put("city", "Istanbul");
		custom.put("address", "My house 11");
		Countly.sharedInstance().setUserData(data, custom);
	}
}
