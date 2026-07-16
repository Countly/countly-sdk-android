package ly.count.android.demo;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import ly.count.android.sdk.Countly;

public class ActivityExampleConsent extends AppCompatActivity {

    private static final String[][] FEATURES = {
        {"sessions", "switchSessions"},
        {"events", "switchEvents"},
        {"views", "switchViews"},
        {"crashes", "switchCrashes"},
        {"attribution", "switchAttribution"},
        {"users", "switchUsers"},
        {"push", "switchPush"},
        {"starRating", "switchStarRating"},
        {"remoteConfig", "switchRemoteConfig"},
        {"location", "switchLocation"},
        {"feedback", "switchFeedback"},
        {"apm", "switchApm"},
        {"content", "switchContent"},
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_consent);

        // Set initial switch states and listeners
        for (String[] feature : FEATURES) {
            String featureName = feature[0];
            int resId = getResources().getIdentifier(feature[1], "id", getPackageName());
            SwitchMaterial sw = findViewById(resId);
            if (sw != null) {
                sw.setChecked(Countly.sharedInstance().consent().getConsent(featureName));
                sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        Countly.sharedInstance().consent().giveConsent(new String[]{featureName});
                    } else {
                        Countly.sharedInstance().consent().removeConsent(new String[]{featureName});
                    }
                });
            }
        }

        findViewById(R.id.btnGiveAllConsent).setOnClickListener(v -> {
            Countly.sharedInstance().consent().giveConsentAll();
            refreshSwitches();
            Toast.makeText(this, "All consent given", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnRemoveAllConsent).setOnClickListener(v -> {
            Countly.sharedInstance().consent().removeConsentAll();
            refreshSwitches();
            Toast.makeText(this, "All consent removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshSwitches() {
        for (String[] feature : FEATURES) {
            int resId = getResources().getIdentifier(feature[1], "id", getPackageName());
            SwitchMaterial sw = findViewById(resId);
            if (sw != null) {
                sw.setChecked(Countly.sharedInstance().consent().getConsent(feature[0]));
            }
        }
    }
}
