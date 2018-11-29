package ly.count.android.demo;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import ly.count.sdk.android.CountlyPush;

/**
 * How-to module for listening for InstanceId changes
 */

public class DemoFirebaseInstanceIdService extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();

        Log.d("DemoInstanceIdService", "got new token: " + FirebaseInstanceId.getInstance().getToken());

        CountlyPush.onTokenRefresh(this, FirebaseInstanceId.getInstance().getToken());
    }
}
