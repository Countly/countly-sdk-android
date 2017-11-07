package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import ly.count.android.sdk.Countly;

/**
 * Demo Activity explaining other features of Countly SDK:
 * <ul>
 *     <li>Sending custom parameters to the server, useful if you have some custom plugins</li>
 *     <li>Star Rating plugin allows you to gather feedback from your users simultaneously promoting your app in stores</li>
 * </ul>
 */

public class ActivityExampleOthers extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_others);
    }

    public void onClickCustomParameter(View v) {
        Countly.session(this).addParam("customParam", 1);
    }

    public void onClickStarRating(View v) {
//        Countly.sharedInstance().showStarRating(activity, new CountlyStarRating.RatingCallback() {
//            @Override
//            public void onRate(int rating) {
//                Toast.makeText(activity, "onRate called with rating: " + rating, Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onDismiss() {
//                Toast.makeText(activity, "onDismiss called", Toast.LENGTH_SHORT).show();
//            }
//        });
    }
}
