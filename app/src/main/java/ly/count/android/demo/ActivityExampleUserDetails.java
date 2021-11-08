package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;

import ly.count.android.sdk.Countly;

@SuppressWarnings("UnusedParameters")
public class ActivityExampleUserDetails extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_user_details);
    }

    public void onClickUserData01(View v) {
        setUserData();
    }

    public void onClickUserData02(View v) {
        //providing any custom key values to store with user
        HashMap<String, String> custom = new HashMap<>();
        custom.put("favoriteAnimal", "dog");

        //set multiple custom properties
        Countly.userData.setCustomUserData(custom);
        Countly.userData.save();
    }

    public void onClickUserData03(View v) {
        //providing any custom key values to store with user
        HashMap<String, String> custom = new HashMap<>();
        custom.put("leastFavoritePet", "cat");

        //set multiple custom properties
        Countly.userData.setCustomUserData(custom);
        Countly.userData.save();
    }

    public void onClickUserData04(View v) {

    }

    public void onClickUserData05(View v) {

    }

    public void setUserData() {
        HashMap<String, String> data = new HashMap<>();
        data.put("name", "First name Last name");
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
        HashMap<String, String> custom = new HashMap<>();
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

    @Override
    public void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}

