package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import java.util.HashMap;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.User;
import ly.count.android.sdk.UserEditor;

/**
 * Demo Activity for User Profile handling:
 * <ul>
 *     <li>Setting standard and custom properties.</li>
 *     <li>Using modifier commands.</li>
 *     <li>Setting profile picture from file path, URL or binary data.</li>
 *     <li>User login & logout.</li>
 * </ul>
 */

public class ActivityExampleUserDetails extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_user_details);
    }

    public void onClickRecordUserData(View v) {
        UserEditor editor = Countly.user().edit()
                .setName("Firstname Lastname")
                .setUsername("nickname")
                .setEmail("test@test.com")
                .setOrg("Tester")
                .setPhone("+123456789")
                .setGender(User.Gender.MALE)
                .setBirthyear(1987)
                .setPicturePath("http://i.pravatar.cc/300");
        // provide url to picture
        //.set("picturePath", "http://example.com/pictures/profile_pic.png");
        // or local path
        //.set("picturePath", "/mnt/sdcard/portrait.jpg");
        // or upload profile picture to Countly server
        //.set("picturePath", new byte[]{...});

        // providing any custom key values to store with user
        editor.set("country", "Jamaica")
                .set("city", "Kingston")
                .set("address", "6, 56 Hope Rd, Kingston, Jamaica");

        editor.commit();
    }

    public void onClickSetCustom1(View v) {
        //providing any custom key values to store with user
        UserEditor editor = Countly.user().edit();

        editor.set("mostFavoritePet", "dog");
        editor.inc("phoneCalls", 1);
        editor.pushUnique("tags", "fan")
                .pushUnique("skill", "singer");

        editor.commit();
    }

    public void onClickSetCustom2(View v) {
        //providing any custom key values to store with user
        Countly.user().edit().set("leastFavoritePet", "cat")
                .inc("phoneCalls", -1)
                .max("phoneCalls", 5)
                .commit();
    }

    public void onClickLogin(View v) {
        Countly.login(this, "XXX");
    }

    public void onClickLogout(View v) {
        Countly.logout(this);
    }
}

