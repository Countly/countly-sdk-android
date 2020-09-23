package ly.count.android.demo.crash;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ly.count.android.sdk.Countly;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "CountlyDemoNative";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Countly.onCreate(this);

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sampleText);
        tv.setText(stringFromJNI());

        final Button button = findViewById(R.id.crashButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG,"crash button clicked");
                // CountlyNative.crash();
                testCrash();
            }
        });
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

    // defined in native-lib.cpp
    public native String stringFromJNI();
    public native int testCrash();
}
