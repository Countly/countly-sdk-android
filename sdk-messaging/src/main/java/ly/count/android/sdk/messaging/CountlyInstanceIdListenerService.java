package ly.count.android.sdk.messaging;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * A service that handles Instance ID service notifications on token refresh.
 *
 * Any app using Instance ID or GCM must include a class extending InstanceIDListenerService and implement onTokenRefresh().
 *
 * Created by Lajos.Erdosi on 2016.07.05..
 */
public class CountlyInstanceIdListenerService extends InstanceIDListenerService {

    /**
     * Called when the system determines that the tokens need to be refreshed.
     */
    @Override
    public void onTokenRefresh() {
        // Get a reference to the application context.
        Context context = getApplicationContext();
        // Get the stored sender ID
        String senderId = CountlyMessaging.getGCMPreferences(context).getString(CountlyMessaging.PROPERTY_REGISTRATION_SENDER, "");
        // If there is no sender ID simply return
        if (TextUtils.isEmpty(senderId)) return;
        // Start registration to get the new token
        CountlyMessaging.registerInBackground(context, senderId);
    }
}
