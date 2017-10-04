package ly.count.android.demo;

import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import ly.count.android.sdk.CountlyPush;

/**
 * Demo service explaining Firebase Messaging notifications handling:
 * - how to decode Countly messages;
 * - how to handle other notifications sent from other tools (FCM console, for example);
 * - how to override message handling based on message content;
 * - how to report Actioned metric back to Countly server.
 */

public class DemoFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "DemoMessagingService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d("DemoFirebaseService", "got new message: " + remoteMessage);

        // decode message data and extract meaningful information from it: title, body, badge, etc.
        CountlyPush.Message message = CountlyPush.decodeMessage(remoteMessage.getData());

        if (message != null && message.has("typ")) {
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

                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("countly_message", message);
                startActivity(intent);

                // ... and then in PromoActivity:

//                CountlyPush.Message msg = intent.getParcelableExtra("message");
//
//                Button btn1 = new Button(this);
//                btn1.setText(msg.buttons().get(0).title());
//                btn1.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        msg.recordAction(getApplicationContext(), 1);
//                    }
//                });
//
//                Button btn2 = new Button(this);
//                btn2.setText(msg.buttons().get(1).title());
//                btn2.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        msg.recordAction(getApplicationContext(), 2);
//                    }
//                });
                return;
            }
        }

        Boolean result = CountlyPush.displayMessage(this, remoteMessage.getData());
        if (result == null) {
            Log.i(TAG, "Message wasn't sent from Countly server, so it cannot be handled by Countly SDK");
        } else if (result) {
            Log.i(TAG, "Message was handled by Countly SDK");
        } else {
            Log.i(TAG, "Message wasn't handled by Countly SDK because API level is too low for Notification support or because currentActivity is null (not enough lifecycle method calls)");
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
    }
}
