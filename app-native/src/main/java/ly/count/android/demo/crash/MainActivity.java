package ly.count.android.demo.crash;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ly.count.android.sdk.PersistentName;
import ly.count.android.sdknative.CountlyNative;

@PersistentName("MainActivity")
public class MainActivity extends AppCompatActivity {

    private static String TAG = "CountlyDemoNative";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private boolean initCrashReporting() {
        return CountlyNative.initNative(getApplicationContext());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sampleText);
        tv.setText(stringFromJNI());
        initCrashReporting();
        final Button button = findViewById(R.id.crashButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG,"crash button clicked");
                // CountlyNative.crash();
                testCrash();
            }
        });
    }

    // defined in native-lib.cpp
    public native String stringFromJNI();
    public native int testCrash();
}
