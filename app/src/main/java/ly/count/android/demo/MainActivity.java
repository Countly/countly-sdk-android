package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;

import ly.count.android.sdk.Countly;


public class MainActivity extends Activity {
    private Activity activity;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Countly.sharedInstance().setLoggingEnabled(true);

        /** You should use cloud.count.ly instead of YOUR_SERVER for the line below if you are using Countly Cloud service */
        Countly.sharedInstance()
                .init(this, "YOUR_SERVER", "YOUR_APP_KEY");
//                .setLocation(LATITUDE, LONGITUDE);
//                .setLoggingEnabled(true);
//        setUserData(); // If UserData plugin is enabled on your server
//        enableCrashTracking();


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

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Countly.sharedInstance().setLocation(44.5888300, 33.5224000);
            }
        }, 11000);

        Button button1 = (Button) findViewById(R.id.runtime);
        button1.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 1 pressed");
                throw new RuntimeException("This is a crash");
            }
        });

        Button button2 = (Button) findViewById(R.id.nullpointer);
        button2.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 2 pressed");
                String test = null;
                test.charAt(1);
            }
        });

        Button button3 = (Button) findViewById(R.id.division0);
        button3.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 3 pressed");
                int test = 100/0;
            }
        });

        Button button4 = (Button) findViewById(R.id.uithread);
        button4.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 4 pressed");
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "Hello", Toast.LENGTH_SHORT).show();
                    }
                };

                thread.start();
            }
        });

        Button button5 = (Button) findViewById(R.id.stackoverflow);
        button5.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 5 pressed");
                stackOverflow();
            }
        });

        Button button6 = (Button) findViewById(R.id.handled);
        button6.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 6 pressed");
                String test = null;
                try {
                    test.charAt(1);
                }
                catch(Exception e){
                    Countly.sharedInstance().logException(e);
                }
            }
        });
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

    public void enableCrashTracking(){
        //add some custom segments, like dependency library versions
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("Facebook", "3.5");
        data.put("Admob", "6.5");
        Countly.sharedInstance().setCustomCrashSegments(data);
        Countly.sharedInstance().enableCrashReporting();
    }

    public void stackOverflow(){
        this.stackOverflow();
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
