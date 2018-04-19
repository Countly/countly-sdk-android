package ly.count.android.demo.messaging_fcm;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.messaging.CountlyPush;

/**
 * How-to module for listening for InstanceId changes
 */

public class DemoFirebaseInstanceIdService extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();

        Log.d("DemoInstanceIdService", "got new token: " + FirebaseInstanceId.getInstance().getToken());

        CountlyPush.onTokenRefresh(Countly.CountlyMessagingMode.PRODUCTION, FirebaseInstanceId.getInstance().getToken());
    }
}
