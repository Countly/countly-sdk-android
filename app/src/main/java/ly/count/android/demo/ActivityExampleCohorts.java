package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import ly.count.android.sdk.Countly;

public class ActivityExampleCohorts extends Activity {
    private static final String COHORT_NAME = "ManualCohort";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_cohorts);
        findViewById(R.id.btnEnterCustomCohort).setEnabled(!Countly.user().cohorts().contains(COHORT_NAME));
        findViewById(R.id.btnExitCustomCohort).setEnabled(Countly.user().cohorts().contains(COHORT_NAME));
    }

    public void onClickEnterCustomCohort(View v) {
        Countly.user().edit().addToCohort(COHORT_NAME).commit();
        findViewById(R.id.btnEnterCustomCohort).setEnabled(false);
        findViewById(R.id.btnExitCustomCohort).setEnabled(true);
    }

    public void onClickExitCustomCohort(View v) {
        Countly.user().edit().removeFromCohort(COHORT_NAME).commit();
        findViewById(R.id.btnEnterCustomCohort).setEnabled(true);
        findViewById(R.id.btnExitCustomCohort).setEnabled(false);
    }
}
