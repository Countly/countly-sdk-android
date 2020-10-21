package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.content.Context;

import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.UUID;

public class OpenUDIDAdapter {
    public final static String PREF_KEY = "openudid";
    public final static String PREFS_NAME = "openudid_prefs";
    public final static String TAG = "OpenUDID";

    private final static boolean LOG = true; //Display or not debug message

    private static String OpenUDID = null;
    private static boolean mInitialized = false;

    public static boolean isOpenUDIDAvailable() {
        return true;
    }

    public static boolean isInitialized() {
        return mInitialized;
    }

    public static void sync(final Context context) {
        SharedPreferences mPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        //Try to get the openudid from local preferences
        OpenUDID = mPreferences.getString(PREF_KEY, null);
        if (OpenUDID == null) //Not found
        {
            generateOpenUDID(context);

            if (LOG) Log.d(TAG, "OpenUDID: " + OpenUDID);

            storeOpenUDID(context);//Store it locally
            mInitialized = true;

        } else {//Got it, you can now call getOpenUDID()
            if (LOG) Log.d(TAG, "OpenUDID: " + OpenUDID);
            mInitialized = true;
        }
    }

    /*
     * Generate a new OpenUDID
     */
    @SuppressLint("HardwareIds")
    private static void generateOpenUDID(Context context) {
        if (LOG) Log.d(TAG, "Generating openUDID");
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
