package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.RCVariantCallback;
import ly.count.android.sdk.RequestResult;

public class ActivityExampleAutoViewTracking extends AppCompatActivity {

    String viewID = "random_1";
    String viewID2 = "random_2";
    String viewName_1 = "view_1";
    String viewName_2 = "view_2";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_auto_views);
    }

    public void onClickRecordView(View v) {
        Countly.sharedInstance().views().recordView("recordView");
        Toast.makeText(getApplicationContext(), "Clicked recordView", Toast.LENGTH_SHORT).show();
    }

    public void onClickStartAutoStoppedView(View v) {
        viewID = Countly.sharedInstance().views().startAutoStoppedView("startAutoStoppedView");
        Toast.makeText(getApplicationContext(), "Clicked startAutoStoppedView", Toast.LENGTH_SHORT).show();
    }

    public void onClickStartView(View v) {
        viewID = Countly.sharedInstance().views().startView(viewName_1);
        Toast.makeText(getApplicationContext(), "Clicked startView 1", Toast.LENGTH_SHORT).show();
    }

    public void onClickStartView2(View v) {
        viewID2 = Countly.sharedInstance().views().startView(viewName_2);
        Toast.makeText(getApplicationContext(), "Clicked startView 2", Toast.LENGTH_SHORT).show();
    }


    public void onClickPauseViewWithID(View v) {
        Countly.sharedInstance().views().pauseViewWithID(viewID);
        Toast.makeText(getApplicationContext(), "Clicked pauseViewWithID 1", Toast.LENGTH_SHORT).show();
    }

    public void onClickPauseViewWithI2(View v) {
        Countly.sharedInstance().views().pauseViewWithID(viewID2);
        Toast.makeText(getApplicationContext(), "Clicked pauseViewWithID 2", Toast.LENGTH_SHORT).show();
    }

    public void onClickResumeViewWithID(View v) {
        Countly.sharedInstance().views().resumeViewWithID(viewID);
        Toast.makeText(getApplicationContext(), "Clicked resumeViewWithID 1", Toast.LENGTH_SHORT).show();
    }

    public void onClickResumeViewWithID2(View v) {
        Countly.sharedInstance().views().resumeViewWithID(viewID2);
        Toast.makeText(getApplicationContext(), "Clicked resumeViewWithID 2", Toast.LENGTH_SHORT).show();
    }

    public void onClickStopViewWithName(View v) {
        Countly.sharedInstance().views().stopViewWithName(viewName_1);
        Toast.makeText(getApplicationContext(), "Clicked stopViewWithName 1", Toast.LENGTH_SHORT).show();
    }

    public void onClickStopViewWithName2(View v) {
        Countly.sharedInstance().views().stopViewWithName(viewName_2);
        Toast.makeText(getApplicationContext(), "Clicked stopViewWithName 2", Toast.LENGTH_SHORT).show();
    }

    public void onClickStopViewWithID(View v) {
        Countly.sharedInstance().views().stopViewWithID(viewID);
        Toast.makeText(getApplicationContext(), "Clicked stopViewWithID 1", Toast.LENGTH_SHORT).show();
    }

    public void onClickStopViewWithID2(View v) {
        Countly.sharedInstance().views().stopViewWithID(viewID2);
        Toast.makeText(getApplicationContext(), "Clicked stopViewWithID 2", Toast.LENGTH_SHORT).show();
    }

    public void onClickStopAllViews(View v) {
        Countly.sharedInstance().views().stopAllViews(null);
        Toast.makeText(getApplicationContext(), "Clicked stopAllViews", Toast.LENGTH_SHORT).show();
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
