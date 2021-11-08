package ly.count.android.demo;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.util.Log;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.messaging.CountlyPush;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

public class DemoHuaweiMessagingService extends HmsMessageService {
    private static final String TAG = "DemoHuaweiMessagingService";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        CountlyPush.onTokenRefresh(token, Countly.CountlyMessagingProvider.HMS);
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "got new message: " + remoteMessage.getDataOfMap());

        // decode message data and extract meaningful information from it: title, body, badge, etc.
        CountlyPush.Message message = CountlyPush.decodeMessage(remoteMessage.getDataOfMap());
        if (message == null) {
            Log.d(TAG, "Not a Countly message");
            return;
        }

        if (message.has("typ")) {
            // custom handling only for messages with specific "typ" keys
            if (message.data("typ").equals("download")) {
                // Some bg download case.
                // We want to know how much devices started downloads after this particular message,
                // so we report Actioned metric back to server:

                // AppDownloadManager.initiateBackgroundDownload(message.link());
                message.recordAction(getApplicationContext());
                return;
            } else if (message.data("typ").equals("promo")) {
                // Now we want to override default Countly UI for a promo message type.
                // We know that it should contain 2 buttons, so we start Activity
                // which would handle UI and report Actioned metric back to the server.

                //                Intent intent = new Intent(this, PromoActivity.class);
                //                intent.putExtra("countly_message", message);
                //                startActivity(intent);
                //
                //                // ... and then in PromoActivity:
                //
                //                final CountlyPush.Message msg = intent.getParcelableExtra("countly_message");
                //                if (msg != null) {
                //                    Button btn1 = new Button(this);
                //                    btn1.setText(msg.buttons().get(0).title());
                //                    btn1.setOnClickListener(new View.OnClickListener() {
                //                        @Override
                //                        public void onClick(View v) {
                //                            msg.recordAction(getApplicationContext(), 1);
                //                        }
                //                    });
                //
                //                    Button btn2 = new Button(this);
                //                    btn2.setText(msg.buttons().get(1).title());
                //                    btn2.setOnClickListener(new View.OnClickListener() {
                //                        @Override
                //                        public void onClick(View v) {
                //                            msg.recordAction(getApplicationContext(), 2);
                //                        }
                //                    });
                //                }

                return;
            }
        }

        Intent intent = null;
        if (message.has("another")) {
            intent = new Intent(getApplicationContext(), ActivityExampleOthers.class);
        }
        Boolean result = CountlyPush.displayMessage(getApplicationContext(), message, R.drawable.ic_message, intent);
        if (result == null) {
            Log.i(TAG, "Message doesn't have anything to display or wasn't sent from Countly server, so it cannot be handled by Countly SDK");
        } else if (result) {
            Log.i(TAG, "Message was handled by Countly SDK");
        } else {
            Log.i(TAG, "Message wasn't handled by Countly SDK because API level is too low for Notification support or because currentActivity is null (not enough lifecycle method calls)");
        }
    }
}
