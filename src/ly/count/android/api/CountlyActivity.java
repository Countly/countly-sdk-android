package ly.count.android.api;

import android.app.Activity;
import android.os.Bundle;

public class CountlyActivity extends Activity {
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Countly.sharedInstance().init(this, "http://YOUR_SERVER/i", "YOUR_APP_KEY");
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
