package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.FeedbackRatingCallback;
import ly.count.android.sdk.PersistentName;
import ly.count.android.sdk.StarRatingCallback;

@PersistentName("ActivityExampleRatings")
public class ActivityExampleRatings extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_ratings);
    }

    public void onClickViewOther02(View v) {
        //show star rating
        Countly.sharedInstance().ratings().showStarRating(this, new StarRatingCallback() {
            @Override
            public void onRate(int rating) {
                Toast.makeText(ActivityExampleRatings.this, "onRate called with rating: " + rating, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDismiss() {
                Toast.makeText(ActivityExampleRatings.this, "onDismiss called", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onClickViewOther07(View v) {
        //show rating widget
        String widgetId = "xxxxx";
        Countly.sharedInstance().ratings().showFeedbackPopup(widgetId, "Close", this, new FeedbackRatingCallback() {
            @Override
            public void callback(String error) {
                if(error != null){
                    Toast.makeText(ActivityExampleRatings.this, "Encountered error while showing feedback dialog: [" + error + "]", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void onClickSendManualRating(View v) {
        //record rating manually without showing any UI
        String widgetId = "5e4afa88967cc71e5b541f8c";
        Countly.sharedInstance().ratings().recordManualRating(widgetId, 3, "foo@bar.garr", "Ragnaros should watch out", true);
    }
}
