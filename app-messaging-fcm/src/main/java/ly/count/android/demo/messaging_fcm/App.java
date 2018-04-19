package ly.count.android.demo.messaging_fcm;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.messaging.CountlyPush;

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
    }
}
