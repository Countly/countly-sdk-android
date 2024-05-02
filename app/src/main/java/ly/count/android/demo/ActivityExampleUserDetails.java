package ly.count.android.demo;

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
        HashMap<String, Object> custom = new HashMap<>();
        custom.put("favoriteAnimal", "dog");

        //set multiple custom properties
        Countly.sharedInstance().userProfile().setProperties(custom);
        Countly.sharedInstance().userProfile().save();
    }

    public void onClickUserData03(View v) {
        //providing any custom key values to store with user
        HashMap<String, Object> custom = new HashMap<>();
        custom.put("leastFavoritePet", "cat");

        //set multiple custom properties
        Countly.sharedInstance().userProfile().setProperties(custom);
        Countly.sharedInstance().userProfile().save();
    }

    public void onClickUserData04(View v) {

    }

    public void onClickUserData05(View v) {

    }

    public void setUserData() {
        HashMap<String, Object> data = new HashMap<>();
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
        data.put("Top rated Country", "Turkey");
        data.put("Favourite city", "Istanbul");
        data.put("Favourite car", "VroomVroom");

        //set multiple custom properties
        Countly.sharedInstance().userProfile().setProperties(data);

        //set custom properties by one
        Countly.sharedInstance().userProfile().setProperty("test", "test");

        //increment used value by 1
        Countly.sharedInstance().userProfile().incrementBy("used", 1);

        //insert value to array of unique values
        Countly.sharedInstance().userProfile().pushUnique("type", "morning");

        //insert multiple values to same property
        Countly.sharedInstance().userProfile().pushUnique("skill", "fire");
        Countly.sharedInstance().userProfile().pushUnique("skill", "earth");

        Countly.sharedInstance().userProfile().save();
    }
}

