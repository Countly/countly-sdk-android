package ly.count.android.demo;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import ly.count.android.sdk.Countly;

public class ActivityExampleLocation extends AppCompatActivity {

    private TextInputEditText inputCountryCode, inputCity, inputLatitude, inputLongitude, inputIpAddress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_location);

        inputCountryCode = findViewById(R.id.inputCountryCode);
        inputCity = findViewById(R.id.inputCity);
        inputLatitude = findViewById(R.id.inputLatitude);
        inputLongitude = findViewById(R.id.inputLongitude);
        inputIpAddress = findViewById(R.id.inputIpAddress);

        findViewById(R.id.btnSetLocation).setOnClickListener(v -> setLocation());
        findViewById(R.id.btnDisableLocation).setOnClickListener(v -> {
            Countly.sharedInstance().location().disableLocation();
            Toast.makeText(this, "Location disabled", Toast.LENGTH_SHORT).show();
        });
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void setLocation() {
        String countryCode = getText(inputCountryCode);
        String city = getText(inputCity);
        String lat = getText(inputLatitude);
        String lng = getText(inputLongitude);
        String ip = getText(inputIpAddress);

        String gpsCoords = null;
        if (!lat.isEmpty() && !lng.isEmpty()) {
            gpsCoords = lat + "," + lng;
        }

        Countly.sharedInstance().location().setLocation(
            countryCode.isEmpty() ? null : countryCode,
            city.isEmpty() ? null : city,
            gpsCoords,
            ip.isEmpty() ? null : ip
        );

        Toast.makeText(this, "Location set", Toast.LENGTH_SHORT).show();
    }
}
