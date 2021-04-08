package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.UUID;

public class OpenUDIDAdapter {
    public final static String PREF_KEY = "openudid";
    public final static String PREFS_NAME = "openudid_prefs";

    public static String OpenUDID = null;

    public static void sync(final Context context) {
        if (OpenUDID != null) {
            return;
        }

        SharedPreferences mPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        //Try to get the openudid from local preferences
        OpenUDID = mPreferences.getString(PREF_KEY, null);
        if (OpenUDID == null) //Not found
        {
            generateOpenUDID(context);
            storeOpenUDID(context);//Store it locally
        }

        Countly.sharedInstance().L.d("[OpenUDID] ID: " + OpenUDID);
    }

    /*
     * Generate a new OpenUDID
     */
    @SuppressLint("HardwareIds")
    private static void generateOpenUDID(Context context) {
        Countly.sharedInstance().L.d("[OpenUDID] Generating openUDID");

        //Try to get the ANDROID_ID
        OpenUDID = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (OpenUDID == null || OpenUDID.equals("9774d56d682e549c") || OpenUDID.length() < 15) {
            //if ANDROID_ID is null, or it's equals to the GalaxyTab generic ANDROID_ID or bad, generates a new one
            OpenUDID = UUID.randomUUID().toString();
        }
    }

    public static String getOpenUDID() {
        return OpenUDID;
    }

    private static void storeOpenUDID(Context context) {
        SharedPreferences mPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = mPreferences.edit();
        e.putString(PREF_KEY, OpenUDID);
        e.apply();
    }
}
