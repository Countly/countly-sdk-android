package ly.count.android.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.net.URLDecoder;

/**
 * ADB Testing
 * adb shell
 * am broadcast -a com.android.vending.INSTALL_REFERRER --es "referrer" "countly_cid%3Dxxxxcidvaluexxxx%26countly_cuid%3Dxxxxcuidvaluexxxx"
 **/
//******************************************************************************
public class ReferrerReceiver extends BroadcastReceiver {
    private static final String key = "referrer";

    //--------------------------------------------------------------------------
    public static String getReferrer(Context context) {
        // Return any persisted referrer value or null if we don't have a referrer.
        return context.getSharedPreferences(key, Context.MODE_PRIVATE).getString(key, null);
    }

    public static void deleteReferrer(Context context) {
        // delete stored referrer.
        context.getSharedPreferences(key, Context.MODE_PRIVATE).edit().remove(key).apply();
    }

    //--------------------------------------------------------------------------
    public ReferrerReceiver() {
    }

    //--------------------------------------------------------------------------
    @Override public void onReceive(Context context, Intent intent) {
        try {
            // Make sure this is the intent we expect - it always should be.
            if ((null != intent) && (intent.getAction().equals("com.android.vending.INSTALL_REFERRER"))) {
                // This intent should have a referrer string attached to it.
                String rawReferrer = intent.getStringExtra(key);
                if (null != rawReferrer) {
                    // The string is usually URL Encoded, so we need to decode it.
                    String referrer = URLDecoder.decode(rawReferrer, "UTF-8");

                    // Log the referrer string.
                    if (Countly.sharedInstance().isInitialized()) {
                        Countly.sharedInstance().L.d("Referrer: " + referrer);
                    }

                    String[] parts = referrer.split("&");
                    String cid = null;
                    String uid = null;
                    for (String part : parts) {
                        if (part.startsWith("countly_cid")) {
                            cid = part.replace("countly_cid=", "").trim();
                        }
                        if (part.startsWith("countly_cuid")) {
                            uid = part.replace("countly_cuid=", "").trim();
                        }
                    }
                    String res = "";
                    if (cid != null) {
                        res += "&campaign_id=" + cid;
                    }
                    if (uid != null) {
                        res += "&campaign_user=" + uid;
                    }

                    if (Countly.sharedInstance().isInitialized()) {
                        Countly.sharedInstance().L.d("Processed: " + res);
                    }
                    // Persist the referrer string.
                    if (!res.equals("")) {
                        context.getSharedPreferences(key, Context.MODE_PRIVATE).edit().putString(key, res).apply();
                    }
                }
            }
        } catch (Exception e) {
            if (Countly.sharedInstance().isInitialized()) {
                Countly.sharedInstance().L.d(e.toString());
            }
        }
    }
}