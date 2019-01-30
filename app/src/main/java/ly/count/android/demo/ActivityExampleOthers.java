package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import ly.count.sdk.android.Countly;
import ly.count.sdk.android.internal.ModuleRating;
import ly.count.sdk.internal.ModuleRatingCore;

/**
 * Demo Activity explaining other features of Countly SDK:
 * <ul>
 *     <li>Sending custom parameters to the server, useful if you have some custom plugins</li>
 *     <li>Star Rating plugin allows you to gather feedback from your users simultaneously promoting your app in stores</li>
 * </ul>
 */

public class ActivityExampleOthers extends Activity {

    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_others);
        activity = this;
    }

    public void onClickCustomParameter(View v) {
        Countly.session(this).addParam("customParam", 1);
    }

    public void onClickStarRating(View v) {
        Toast.makeText(this, "onClickStarRating called", Toast.LENGTH_SHORT).show();
        Countly.Ratings().showStarRating(activity, new ModuleRatingCore.RatingCallback() {
            @Override
            public void onRate(int rating) {
                Toast.makeText(activity, "onRate called with rating: " + rating, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDismiss() {
                Toast.makeText(activity, "onDismiss called", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onClickRatingWidget(View v) {
        //Toast.makeText(this, "onClickRatingWidget called", Toast.LENGTH_SHORT).show();
        String widgetId = "5c4a041c8f5ec579bc794a49";

        Countly.Ratings().showFeedbackPopup(widgetId, "close",this, new ModuleRating.FeedbackRatingCallback() {
            @Override
            public void callback(String error) {
                if(error != null) {
                    Toast.makeText(activity, "callback after onClickRatingWidget called, " + error, Toast.LENGTH_LONG).show();
                }
            }
        });

    }
}
