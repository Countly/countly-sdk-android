package ly.count.android.sdk.messaging;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.DeviceId;

/**
 * Countly Messaging
 *
 */
public class CountlyMessaging extends WakefulBroadcastReceiver {
    private static final String TAG = "CountlyMessaging";

    protected static final String NOTIFICATION_SHOW_DIALOG = "ly.count.android.api.messaging.dialog";
    protected static final String EXTRA_MESSAGE = "ly.count.android.api.messaging.message";
    protected static final String EXTRA_ACTION_INDEX = "ly.count.android.api.messaging.action.index";

    protected static final String EVENT_OPEN    = "[CLY]_push_open";
    protected static final String EVENT_ACTION  = "[CLY]_push_action";

    protected static String[] buttonNames = new String[]{"Open", "Review"};

    protected static final int NOTIFICATION_TYPE_UNKNOWN  = 0;
    protected static final int NOTIFICATION_TYPE_MESSAGE  = 1;
    protected static final int NOTIFICATION_TYPE_URL      = 1 << 1;
//    protected static final int NOTIFICATION_TYPE_REVIEW   = 1 << 2;

    protected static final int NOTIFICATION_TYPE_SILENT           = 1 << 3;

    protected static final int NOTIFICATION_TYPE_SOUND_DEFAULT    = 1 << 4;
    protected static final int NOTIFICATION_TYPE_SOUND_URI        = 1 << 5;
    protected static final int NOTIFICATION_TYPE_TITLE            = 1 << 6;
    protected static final int NOTIFICATION_TYPE_MEDIA            = 1 << 7;
    protected static final int NOTIFICATION_TYPE_BUTTONS          = 1 << 8;

    /**
     * Action for Countly Messaging BroadcastReceiver.
     * Once message is arrived, Countly Messaging will send a broadcast notification with action "APP_PACKAGE_NAME.countly.messaging"
     * to which you can subscribe via BroadcastReceiver.
     */
    public static String BROADCAST_RECEIVER_ACTION_MESSAGE = "ly.count.android.api.messaging.broadcast.message";
    public static String getBroadcastAction (Context context) {
        try {
            ComponentName name = new ComponentName(context, CountlyMessagingService.class);
            Bundle data = context.getPackageManager().getServiceInfo(name, PackageManager.GET_META_DATA).metaData;
            return data.getString("broadcast_action");
        } catch (PackageManager.NameNotFoundException ignored) {
            Log.w(TAG, "Set broadcast_action metadata for CountlyMessagingService in AndroidManifest.xml to receive broadcasts about received messages.");
            return null;
        }
    }


    private static Context context;

    /**
     * Activity used for messages displaying.
     * When message arrives, Countly displays it either as a Notification, or as a AlertDialog. In any case,
     * this activity is used as a final destination.
     */
    private static Class<? extends Activity> activityClass;
    private static boolean disableUI;

    public static void setActivity(Activity activity) {
        setActivity(activity, activity.getClass());
    }

    public static void setActivity(Activity activity, Class<? extends Activity> claz) {
        context = activity.getApplicationContext();
        activityClass = claz == null ? activity.getClass() : claz;
    }
    protected static Context getContext() { return context; }
    protected static Class<? extends Activity> getActivityClass(Context context) {
        return activityClass == null ? getMainActivityClass(context) : activityClass;
    }
    @SuppressWarnings("unchecked")

    protected static Class<? extends Activity> getMainActivityClass(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return (Class<? extends Activity>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public void onReceive (Context context, Intent intent) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(TAG, "Starting service @ " + SystemClock.elapsedRealtime());
        }

        ComponentName comp = new ComponentName(context.getPackageName(), CountlyMessagingService.class.getName());
        startWakefulService(context, intent.setComponent(comp));
        setResultCode(Activity.RESULT_OK);
    }


    private static final String PREFERENCES_NAME = "ly.count.android.api.messaging";
    private static final String PROPERTY_REGISTRATION_ID = "ly.count.android.api.messaging.registration.id";
    private static final String PROPERTY_REGISTRATION_VERSION = "ly.count.android.api.messaging.version";
    private static final String PROPERTY_REGISTRATION_SENDER = "ly.count.android.api.messaging.sender";
    private static final String PROPERTY_APPLICATION_TITLE = "ly.count.android.api.messaging.app.title";
    private static final String PROPERTY_SERVER_URL = "ly.count.android.api.messaging.server.url";
    private static final String PROPERTY_APP_KEY = "ly.count.android.api.messaging.app.key";
    private static final String PROPERTY_DEVICE_ID = "ly.count.android.api.messaging.device.id";
    private static final String PROPERTY_DEVICE_ID_MODE = "ly.count.android.api.messaging.device.id.mode";
    private static final String PROPERTY_DISABLE_UI = "ly.count.android.api.messaging.disable.ui";
    private static final String PROPERTY_ACTIVITY_CLASS = "ly.count.android.api.messaging.activity.class";
    private static final String PROPERTY_ICON_OVERRIDE_ID = "ly.count.android.api.messaging.icon.override.id";
    protected static final String PROPERTY_ADD_METADATA_TO_PUSH_INTENTS = "ly.count.android.api.messaging.add.intent.metadata";

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static GoogleCloudMessaging gcm;


    public static void init(Activity activity, Class<? extends Activity> activityClass, String sender, String[] buttonNames, Boolean disableUI, Integer customIconResId, Boolean addMetadataToPushIntents) {
        setActivity(activity, activityClass);

        if (gcm != null) {
            return;
        }

        if (buttonNames != null) {
            CountlyMessaging.buttonNames = buttonNames;
        }

        CountlyMessaging.disableUI = disableUI;
        getGCMPreferences(context).edit().putBoolean(PROPERTY_DISABLE_UI, disableUI).commit();
        getGCMPreferences(context).edit().putInt(PROPERTY_ICON_OVERRIDE_ID, customIconResId).commit();
        getGCMPreferences(context).edit().putBoolean(PROPERTY_ADD_METADATA_TO_PUSH_INTENTS, addMetadataToPushIntents).commit();

        if (checkPlayServices(activity) ) {
            gcm = GoogleCloudMessaging.getInstance(activity);
            String registrationId = getRegistrationId(activity, sender);
            if (registrationId.isEmpty()) {
                registerInBackground(activity, sender);
            } else {
                Countly.sharedInstance().onRegistrationId(registrationId);
            }
        } else {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(TAG, "No valid Google Play Services APK found.");
            }
        }
    }

    @SuppressLint("CommitPrefEdits")
    public static void storeConfiguration(Context context, String serverURL, String appKey, String deviceID, DeviceId.Type idMode) {
        String label = "App";
        try {
            label = context.getString(context.getApplicationInfo().labelRes);
        } catch (Throwable t) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.wtf(TAG, "Couldn't find android:label='@string/app_name' resource, please set it in AndroidManifest.xml", t);
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(TAG, "Storing configuration: " + label + ", " + serverURL + ", " + appKey + ", " + deviceID + ", " + idMode);
        }

        SharedPreferences.Editor editor = getGCMPreferences(context).edit()
                .putString(PROPERTY_APPLICATION_TITLE, label)
                .putString(PROPERTY_SERVER_URL, serverURL)
                .putString(PROPERTY_APP_KEY, appKey)
                .putString(PROPERTY_DEVICE_ID, deviceID)
                .putInt(PROPERTY_DEVICE_ID_MODE, idMode == null ? -1 : idMode.ordinal());

        if (activityClass != null) {
            editor.putString(PROPERTY_ACTIVITY_CLASS, activityClass.getName());
        }

        editor.commit();
    }

    @SuppressWarnings("unchecked")
    protected static boolean initCountly(Context context) {
        String serverURL = getGCMPreferences(context).getString(PROPERTY_SERVER_URL, null);
        String appKey = getGCMPreferences(context).getString(PROPERTY_APP_KEY, null);
        String deviceID = getGCMPreferences(context).getString(PROPERTY_DEVICE_ID, null);
        String activityClassName = getGCMPreferences(context).getString(PROPERTY_ACTIVITY_CLASS, null);

        DeviceId.Type idMode = null;
        int mode = getGCMPreferences(context).getInt(PROPERTY_DEVICE_ID_MODE, -1);
        if (mode != -1) idMode = DeviceId.Type.values()[mode];

        disableUI = getGCMPreferences(context).getBoolean(PROPERTY_DISABLE_UI, false);

        if (serverURL == null || appKey == null) {
            return false;
        } else {
            Countly.sharedInstance().init(context, serverURL, appKey, deviceID, idMode);
            try {
                activityClass = (Class<? extends Activity>) Class.forName(activityClassName);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Could not find class " + activityClassName, e);
                activityClass = getMainActivityClass(context);
            }
            return true;
        }
    }

    private static void registerInBackground(final Context context, final String sender) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    String registrationId = gcm.register(sender);
                    Countly.sharedInstance().onRegistrationId(registrationId);
                    storeRegistrationId(context, registrationId, sender);
                } catch (IOException ex) {
                    Log.e(TAG, "Failed to register for GCM identificator: " + ex.getMessage());
                }
                return null;
            }
        }.execute(null, null, null);
    }

    private static void storeRegistrationId(Context context, String regId, String sender) {
        int appVersion = getAppVersion(context);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(TAG, "Saving regId " + regId + " for sender ID " + sender + " for app version " + appVersion);
        }
        getGCMPreferences(context).edit()
                .putString(PROPERTY_REGISTRATION_ID, regId)
                .putString(PROPERTY_REGISTRATION_SENDER, sender)
                .putInt(PROPERTY_REGISTRATION_VERSION, appVersion).commit();
    }


    protected static SharedPreferences getGCMPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static boolean checkPlayServices(Activity activity) {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
        return resultCode == ConnectionResult.SUCCESS || resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED;
//        if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
//            GooglePlayServicesUtil.getErrorDialog(resultCode, activity, PLAY_SERVICES_RESOLUTION_REQUEST).show();
//        } else {
//            Log.w(TAG, "Unable to install Play Services.");
//        }
    }

    private static String getRegistrationId(Activity activity, String sender) {
        final SharedPreferences preferences = getGCMPreferences(activity);
        String registrationId = preferences.getString(PROPERTY_REGISTRATION_ID, "");

        if (registrationId.isEmpty()) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(TAG, "Registration not found.");
            }
            return "";
        }

        int registeredVersion = preferences.getInt(PROPERTY_REGISTRATION_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(activity.getApplicationContext());
        if (registeredVersion != currentVersion) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(TAG, "App version changed.");
            }
            return "";
        }

        String registeredSender = preferences.getString(PROPERTY_REGISTRATION_SENDER, "");
        if (!registeredSender.equals(sender)) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(TAG, "Sender ID changed.");
            }
            return "";
        }

        return registrationId;
    }

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    protected static String getAppTitle(Context context) {
        return getGCMPreferences(context).getString(PROPERTY_APPLICATION_TITLE, "");
    }

    public static void recordMessageAction(Context context, String messageId) {
        CountlyMessaging.context = context;
        recordMessageAction(messageId);
    }

    public static void recordMessageAction(String messageId) {
        if (!Countly.sharedInstance().isInitialized()) {
            CountlyMessaging.initCountly(getContext());
        }
        Map<String, String> segmentation = new HashMap<String, String>();
        segmentation.put("i", messageId);
        Countly.sharedInstance().recordEvent(EVENT_ACTION, segmentation, 1);
    }

    public static void recordMessageAction(String messageId, int btnIndex) {
        if (!Countly.sharedInstance().isInitialized()) {
            CountlyMessaging.initCountly(getContext());
        }
        Map<String, String> segmentation = new HashMap<String, String>();
        segmentation.put("i", messageId);
        segmentation.put("b", "" + btnIndex);
        Countly.sharedInstance().recordEvent(EVENT_ACTION, segmentation, 1);
    }

    public static boolean isUIDisabled (Context context) {
        return getGCMPreferences(context).getBoolean(PROPERTY_DISABLE_UI, false);
    }

    public static int getIconOverride (Context context) {
        return getGCMPreferences(context).getInt(PROPERTY_ICON_OVERRIDE_ID, -1);
    }
}
