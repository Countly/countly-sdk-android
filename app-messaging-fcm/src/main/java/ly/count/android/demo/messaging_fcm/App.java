package ly.count.android.demo.messaging_fcm;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.UserData;
import ly.count.android.sdk.messaging.CountlyPush;

import static ly.count.android.sdk.Countly.TAG;

public class App extends Application {

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

        Countly.sharedInstance()
                .setRequiresConsent(true)
                .setLoggingEnabled(true)
                .setPushIntentAddMetadata(true)
                .init(this, "http://192.168.3.77:3001", "ef2cc6520fb2eadfd96050dfc7d3560b07a718a5");

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
