package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.FeedbackRatingCallback;
import ly.count.android.sdk.StarRatingCallback;
import ly.count.android.sdk.SurveyCallback;

public class ActivityExampleFeedback extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_feedback);
        Countly.onCreate(this);
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

    public void onClickViewOther02(View v) {
        //show star rating
        Countly.sharedInstance().ratings().showStarRating(this, new StarRatingCallback() {
            @Override
            public void onRate(int rating) {
                Toast.makeText(ActivityExampleFeedback.this, "onRate called with rating: " + rating, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDismiss() {
                Toast.makeText(ActivityExampleFeedback.this, "onDismiss called", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onClickViewOther07(View v) {
        //show rating widget
        String widgetId = "5f15c01425f83c169c33cb65";
        Countly.sharedInstance().ratings().showFeedbackPopup(widgetId, "Close", ActivityExampleFeedback.this, new FeedbackRatingCallback() {
            @Override
            public void callback(String error) {
                if(error != null){
                    Toast.makeText(ActivityExampleFeedback.this, "Encountered error while showing feedback dialog: [" + error + "]", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void onClickSendManualRating(View v) {
        //record rating manually without showing any UI
        String widgetId = "5f15c01425f83c169c33cb65";
        Countly.sharedInstance().ratings().recordManualRating(widgetId, 3, "foo@bar.garr", "Ragnaros should watch out", true);
    }

    public void onClickShowSurvey(View v) {
        Countly.sharedInstance().surveys().showSurvey(ActivityExampleFeedback.this, new SurveyCallback() {
            @Override public void onCompleted(boolean surveyWasAvailable, String error) {
                Log.d(Countly.TAG, "'showSurvey' callback, was available:[" + surveyWasAvailable + "], error:[" + error + "]");
                Toast.makeText(ActivityExampleFeedback.this, "'showSurvey' callback, was available:[ + " + surveyWasAvailable + "], error:[" + error + "]", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onClickShowNPS(View v) {
        Countly.sharedInstance().surveys().showNps(ActivityExampleFeedback.this, new SurveyCallback() {
            @Override public void onCompleted(boolean surveyWasAvailable, String error) {
                Log.d(Countly.TAG, "'showNps' callback, was available:[" + surveyWasAvailable + "], error:[" + error + "]");
                Toast.makeText(ActivityExampleFeedback.this, "'showNps' callback, was available:[ + " + surveyWasAvailable + "], error:[" + error + "]", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}
