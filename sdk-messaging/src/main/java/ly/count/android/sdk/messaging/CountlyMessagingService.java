package ly.count.android.sdk.messaging;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.util.List;

import ly.count.android.sdk.Countly;

/**
* Created by artem on 14/10/14.
*/
public class CountlyMessagingService extends IntentService {
    public static final String TAG = "CountlyMessagingService";
    public static final int NOTIFICATION_ID = 736192;

    public CountlyMessagingService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent (final Intent intent) {
        Log.i(TAG, "Handling intent");
        Bundle extras = intent.getExtras();

        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
        String messageType = gcm.getMessageType(intent);

        if (extras != null && !extras.isEmpty()) {
            if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                final Message msg = new Message(extras);

                if (msg.isValid()) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.i(TAG, "Got a message from Countly Messaging: " + msg);
                    }

                    // Send broadcast
                    Intent broadcast = new Intent(CountlyMessaging.getBroadcastAction(getApplicationContext()));
                    broadcast.putExtra(CountlyMessaging.BROADCAST_RECEIVER_ACTION_MESSAGE, msg);
                    sendBroadcast(broadcast);

                    msg.prepare(new Runnable() {
                        @Override
                        public void run() {
                            // Init Countly in case app is not running
                            if (!Countly.sharedInstance().isInitialized()) {
                                if (!CountlyMessaging.initCountly(getApplicationContext())) {
                                    Log.e(TAG, "Cannot init Countly in background");
                                }
                            }

                            if (CountlyMessaging.isUIDisabled(CountlyMessagingService.this)) {
                                Log.i(TAG, "Won't do anything since Countly Messaging UI is disabled");
                                CountlyMessaging.completeWakefulIntent(intent);
                                return;
                            }

                            // Show message if not silent
                            if (!msg.isSilent() && msg.hasMessage()) {
                                // Go through proxy activity to be able to record message open & action performed events
                                Intent proxy = new Intent(getApplicationContext(), ProxyActivity.class);
                                proxy.putExtra(CountlyMessaging.EXTRA_MESSAGE, msg);
                                CountlyMessagingService.this.notify(proxy);
                            }
                        }
                    });
                }
            }
        }

        CountlyMessaging.completeWakefulIntent(intent);
    }

    protected void notify(Intent proxy) {
        Message msg = proxy.getParcelableExtra(CountlyMessaging.EXTRA_MESSAGE);

        if (isAppInForeground(this)) {
            // Go with dialog
            proxy.putExtra(CountlyMessaging.NOTIFICATION_SHOW_DIALOG, true);
            proxy.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(proxy);
        } else {
            // Notification case
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, proxy, PendingIntent.FLAG_UPDATE_CURRENT);

            // Get icon from application or use default one
            int smallIcon;
            int iconOverride = CountlyMessaging.getIconOverride(CountlyMessagingService.this);
            try {
                if(iconOverride > 0){
                    smallIcon = iconOverride;
                } else {
                    smallIcon = getPackageManager().getApplicationInfo(getPackageName(), 0).icon;
                }
            } catch (PackageManager.NameNotFoundException e) {
                smallIcon = android.R.drawable.ic_dialog_email;
            }

            int largeIcon = CountlyMessaging.getLargeIconId(CountlyMessagingService.this);
            int notificationColor = CountlyMessaging.getAccentColor(CountlyMessagingService.this);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                    .setAutoCancel(true)
                    .setSmallIcon(smallIcon)
                    .setTicker(msg.getNotificationMessage())
                    .setContentTitle(msg.getNotificationTitle(getApplicationContext()))
                    .setContentText(msg.getNotificationMessage())
                    .setContentIntent(contentIntent)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(msg.getNotificationMessage()).setBigContentTitle(msg.getNotificationTitle(getApplicationContext())));

            if(largeIcon > 0){
                Bitmap iconBitmap = BitmapFactory.decodeResource(getResources(), largeIcon);
                if(iconBitmap != null){
                    builder.setLargeIcon(iconBitmap);
                }
            }

            if(notificationColor > 0){
                builder.setColor(notificationColor);
            }

            if (msg.hasMedia() && Message.getFromStore(msg.getMedia()) != null) {
                builder.setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture((Bitmap)Message.getFromStore(msg.getMedia()))
                        .setBigContentTitle(msg.getNotificationTitle(getApplicationContext()))
                        .setSummaryText(msg.getNotificationMessage()));
            }

            for (Message.Button button : msg.getButtons()) {
                Log.d(Countly.TAG, button.index + " " + button.link);
                Intent actionIntent = (Intent) proxy.clone();
                actionIntent.putExtra(CountlyMessaging.EXTRA_ACTION_INDEX, button.index);
                builder.addAction(0, button.title, PendingIntent.getActivity(getApplicationContext(), button.index, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT));
            }

            if (msg.hasSoundDefault()) {
                builder.setDefaults(Notification.DEFAULT_SOUND);
            } else if (msg.hasSoundUri()) {
                builder.setSound(Uri.parse(msg.getSoundUri()));
            }

            manager.notify(msg.getId().hashCode(), builder.build());
        }
    }

    private static boolean isAppInForeground (Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
