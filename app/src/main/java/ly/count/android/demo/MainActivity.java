package ly.count.android.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import ly.count.android.sdk.Countly;

public class MainActivity extends Activity {
    private boolean started = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClickButtonCustomEvents(View v) {
        startActivity(new Intent(this, ActivityExampleCustomEvents.class));
    }

    public void onClickButtonCrashReporting(View v) {
        startActivity(new Intent(this, ActivityExampleCrashReporting.class));
    }

    public void onClickButtonUserDetails(View v) {
        startActivity(new Intent(this, ActivityExampleUserDetails.class));
    }

    public void onClickButtonAPM(View v) {
        //
    }

    public void onClickButtonCohorts(View v) {
        startActivity(new Intent(this, ActivityExampleCohorts.class));
    }

    public void onClickButtonViewTracking(View v) {
        startActivity(new Intent(this, ActivityExampleViewTracking.class));
    }

    public void onClickButtonOthers(View v) {
        startActivity(new Intent(this, ActivityExampleOthers.class));
    }

    public void onClickButtonHalt(View v) {
        if (started) {
            Countly.stop(this, true);
            findViewById(R.id.btnEvents).setVisibility(View.GONE);
            findViewById(R.id.btnReporting).setVisibility(View.GONE);
            findViewById(R.id.btnUserDetails).setVisibility(View.GONE);
            findViewById(R.id.btnViews).setVisibility(View.GONE);
            findViewById(R.id.btnCohorts).setVisibility(View.GONE);
            findViewById(R.id.btnOthers).setVisibility(View.GONE);
            ((Button)findViewById(R.id.btnHalt)).setText(getText(R.string.btn_init));
        } else {
            Countly.init(getApplication(), App.getCountlyConfig());
            findViewById(R.id.btnEvents).setVisibility(View.VISIBLE);
            findViewById(R.id.btnReporting).setVisibility(View.VISIBLE);
            findViewById(R.id.btnUserDetails).setVisibility(View.VISIBLE);
            findViewById(R.id.btnViews).setVisibility(View.VISIBLE);
            findViewById(R.id.btnCohorts).setVisibility(View.VISIBLE);
            findViewById(R.id.btnOthers).setVisibility(View.VISIBLE);
            ((Button)findViewById(R.id.btnHalt)).setText(getText(R.string.btn_halt));
        }
        started = !started;
    }
}
