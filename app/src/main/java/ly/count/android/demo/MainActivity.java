package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyStarRating;


public class MainActivity extends Activity {
    private Activity activity;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Countly.sharedInstance().setLoggingEnabled(true);

        Countly.onCreate(this);

        /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
        Countly.sharedInstance()
                //.init(this, "YOUR_SERVER", "YOUR_APP_KEY");
                .init(this, "http://kadikis.count.ly/", "8fad8ef86ace5bb44a590ea1f37e746fbbc01617");
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
                Countly.sharedInstance().crashTest(4);
            }
        });

        Button button2 = (Button) findViewById(R.id.nullpointer);
        button2.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 2 pressed");
                Countly.sharedInstance().crashTest(5);
            }
        });

        Button button3 = (Button) findViewById(R.id.division0);
        button3.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 3 pressed");
                Countly.sharedInstance().crashTest(2);
            }
        });


        Button button5 = (Button) findViewById(R.id.stackoverflow);
        button5.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 4 pressed");
                Countly.sharedInstance().crashTest(1);
            }
        });

        Button button6 = (Button) findViewById(R.id.handled);
        button6.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Countly.sharedInstance().addCrashLog("Button 5 pressed");
                try {
                    Countly.sharedInstance().crashTest(5);
                }
                catch(Exception e){
                    Countly.sharedInstance().logException(e);
                }
            }
        });

        Button button7 = (Button) findViewById(R.id.app_rating_default);
        button7.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Countly.sharedInstance().ShowStarRating(activity, new CountlyStarRating.RatingCallback() {
                    @Override
                    public void OnRate(int rating) {

                    }

                    @Override
                    public void OnDismiss() {

                    }
                });
            }
        });

        Button button8 = (Button) findViewById(R.id.app_rating_custom);
        button8.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

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

        //set multiple custom properties
        Countly.userData.setUserData(data, custom);

        //set custom properties by one
        Countly.userData.setProperty("test", "test");

        //increment used value by 1
        Countly.userData.incrementBy("used", 1);

        //insert value to array of unique values
        Countly.userData.pushUniqueValue("type", "morning");

        //insert multiple values to same property
        Countly.userData.pushUniqueValue("skill", "fire");
        Countly.userData.pushUniqueValue("skill", "earth");

        Countly.userData.save();
    }

    public void enableCrashTracking(){
        //add some custom segments, like dependency library versions
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("Facebook", "3.5");
        data.put("Admob", "6.5");
        Countly.sharedInstance().setCustomCrashSegments(data);
        Countly.sharedInstance().enableCrashReporting();
    }

    @Override
    public void onStart()
    {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop()
    {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

}
