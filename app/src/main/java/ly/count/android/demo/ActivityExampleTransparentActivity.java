package ly.count.android.demo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.TransparentActivity;
import ly.count.android.sdk.TransparentActivityConfig;

public class ActivityExampleTransparentActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_transparent_activity);
    }

    public void onClickShowTransparentActivity(View v) {
        EditText editText1, editText2, editText3, editText4, editText5, editText6, editText7, editText8, editText9;

        editText1 = findViewById(R.id.buttonShowTransparent_text1);
        editText2 = findViewById(R.id.buttonShowTransparent_text2);
        editText3 = findViewById(R.id.buttonShowTransparent_text3);
        editText4 = findViewById(R.id.buttonShowTransparent_text4);
        editText5 = findViewById(R.id.buttonShowTransparent_text5);
        editText6 = findViewById(R.id.buttonShowTransparent_text6);
        editText7 = findViewById(R.id.buttonShowTransparent_text7);
        editText8 = findViewById(R.id.buttonShowTransparent_text8);
        editText9 = findViewById(R.id.buttonShowTransparent_text9);

        Integer xCoordinate = getOptionalNullInt(editText1.getText().toString());
        Integer yCoordinate = getOptionalNullInt(editText2.getText().toString());
        Double xPercentage = getOptionalNullDouble(editText3.getText().toString());
        Double yPercentage = getOptionalNullDouble(editText4.getText().toString());
        Integer widthPx = getOptionalNullInt(editText5.getText().toString());
        Integer heightPx = getOptionalNullInt(editText6.getText().toString());
        Double widthPercentage = getOptionalNullDouble(editText7.getText().toString());
        Double heightPercentage = getOptionalNullDouble(editText8.getText().toString());
        String webviewURL = editText9.getText().toString();
        if (webviewURL.isEmpty()) {
            webviewURL = "https://countly.com/";
        }

        Log.d("Countly", "ActivityExampleTransparentActivity, xCoordinate: " + xCoordinate);
        Log.d("Countly", "ActivityExampleTransparentActivity, yCoordinate: " + yCoordinate);
        Log.d("Countly", "ActivityExampleTransparentActivity, xPercentage: " + xPercentage);
        Log.d("Countly", "ActivityExampleTransparentActivity, yPercentage: " + yPercentage);
        Log.d("Countly", "ActivityExampleTransparentActivity, widthPx: " + widthPx);
        Log.d("Countly", "ActivityExampleTransparentActivity, heightPx: " + heightPx);
        Log.d("Countly", "ActivityExampleTransparentActivity, widthPercentage: " + widthPercentage);
        Log.d("Countly", "ActivityExampleTransparentActivity, heightPercentage: " + heightPercentage);
        Log.d("Countly", "ActivityExampleTransparentActivity, webviewURL: " + webviewURL);

        TransparentActivityConfig config = new TransparentActivityConfig(xCoordinate, yCoordinate, widthPx, heightPx);
        config.setUrl(webviewURL);
        config.setPercents(xPercentage, yPercentage, widthPercentage, heightPercentage);

        TransparentActivity.showActivity(this, config);
    }

    private Integer getOptionalNullInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Double getOptionalNullDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return null;
        }
    }
}
