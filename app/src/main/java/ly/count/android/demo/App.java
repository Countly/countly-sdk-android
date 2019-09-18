package ly.count.android.demo;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.HashMap;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.RemoteConfig;
import ly.count.android.sdk.messaging.CountlyPush;

import static ly.count.android.sdk.Countly.TAG;

public class App extends Application {
    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
    final String COUNTLY_SERVER_URL = "YOUR_SERVER";
    final String COUNTLY_APP_KEY = "YOUR_APP_KEY";

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                // Create the NotificationChannel
                NotificationChannel channel = new NotificationChannel(CountlyPush.CHANNEL_ID, getString(R.string.countly_hannel_name), NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(getString(R.string.countly_channel_description));
                notificationManager.createNotificationChannel(channel);
            }
        }



        Context appC = getApplicationContext();

        HashMap<String, String> customHeaderValues = new HashMap<>();
        customHeaderValues.put("foo", "bar");

        //Countly.sharedInstance().setConsent(new String[]{Countly.CountlyFeatureNames.push, Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.location, Countly.CountlyFeatureNames.attribution, Countly.CountlyFeatureNames.crashes, Countly.CountlyFeatureNames.events, Countly.CountlyFeatureNames.starRating, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.views}, false);
        //Log.i(demoTag, "Before calling init. This should return 'false', the value is:" + Countly.sharedInstance().isInitialized());
        CountlyConfig config = (new CountlyConfig()).setContext(appC).setServerURL(COUNTLY_SERVER_URL).setAppKey(COUNTLY_APP_KEY).setIdMode(DeviceId.Type.OPEN_UDID).enableTemporaryDeviceIdMode()
                .enableCrashReporting().setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true)
                .setRequiresConsent(true).setConsentEnabled(new String[]{Countly.CountlyFeatureNames.push, Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.location, Countly.CountlyFeatureNames.attribution, Countly.CountlyFeatureNames.crashes, Countly.CountlyFeatureNames.events, Countly.CountlyFeatureNames.starRating, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.views})
                .addCustomNetworkRequestHeaders(customHeaderValues).setPushIntentAddMetadata(true).setRemoteConfigAutomaticDownload(true, new RemoteConfig.RemoteConfigCallback() {
                    @Override
                    public void callback(String error) {
                        if(error == null) {
                            Log.d(Countly.TAG, "Automatic remote config download has completed");
                        } else {
                            Log.d(Countly.TAG, "Automatic remote config download encountered a problem, " + error);
                        }
                    }
                });
        Countly.sharedInstance().init(config);
        //Log.i(demoTag, "After calling init. This should return 'true', the value is:" + Countly.sharedInstance().isInitialized());



        CountlyPush.init(this, Countly.CountlyMessagingMode.PRODUCTION);





        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        String token = task.getResult().getToken();
                        CountlyPush.onTokenRefresh(token);
                    }
                });

    }
}
