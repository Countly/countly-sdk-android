package ly.count.android.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import ly.count.android.api.Countly;

public class CountlyActivity extends Activity {
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

    /** You should use cloud.count.ly instead of YOUR_SERVER for the line below if you are using Countly Cloud service */
//        Countly.sharedInstance().init(this, "https://YOUR_SERVER", "YOUR_APP_KEY");
        Countly.sharedInstance()
                .setLoggingEnabled(true)
                .init(this, "http://128.199.55.79", "60758257b5a8595a96648296f4e04c4f923e4f6f", null, Countly.CountlyIdMode.ADVERTISING_ID);

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
}
