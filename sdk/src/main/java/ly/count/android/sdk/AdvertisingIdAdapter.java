package ly.count.android.sdk;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

public class AdvertisingIdAdapter {
    private static final String TAG = "AdvertisingIdAdapter";
    private final static String ADVERTISING_ID_CLIENT_CLASS_NAME = "com.google.android.gms.ads.identifier.AdvertisingIdClient";

    public static boolean isAdvertisingIdAvailable() {
        boolean advertisingIdAvailable = false;
        try {
            Class.forName(ADVERTISING_ID_CLIENT_CLASS_NAME);
            advertisingIdAvailable = true;
        }
        catch (ClassNotFoundException ignored) {}
        return advertisingIdAvailable;
    }

    public static void setAdvertisingId(final Context context, final CountlyStore store, final DeviceId deviceId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    deviceId.setId(DeviceId.Type.ADVERTISING_ID, getAdvertisingId(context));
                } catch (Throwable t) {
                    if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesAvailabilityException")) {
                        // recoverable, let device ID be null, which will result in storing all requests to Countly server
                        // and rerunning them whenever Advertising ID becomes available
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.i(TAG, "Advertising ID cannot be determined yet");
                        }
                    } else if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesNotAvailableException")) {
                        // non-recoverable, fallback to OpenUDID
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.w(TAG, "Advertising ID cannot be determined because Play Services are not available");
                        }
                        deviceId.switchToIdType(DeviceId.Type.OPEN_UDID, context, store);
                    } else {
                        // unexpected
                        Log.e(TAG, "Couldn't get advertising ID", t);
                    }
                }
            }
        }).start();
    }

    private static String getAdvertisingId(final Context context) throws Throwable{
        final Class<?> cls = Class.forName(ADVERTISING_ID_CLIENT_CLASS_NAME);
        final Method getAdvertisingIdInfo = cls.getMethod("getAdvertisingIdInfo", Context.class);
        Object info = getAdvertisingIdInfo.invoke(null, context);
        if (info != null) {
            final Method getId = info.getClass().getMethod("getId");
            Object id = getId.invoke(info);
            return (String)id;
        }
        return null;
    }

    private static boolean isLimitAdTrackingEnabled(final Context context) throws Throwable{
        final Class<?> cls = Class.forName(ADVERTISING_ID_CLIENT_CLASS_NAME);
        final Method getAdvertisingIdInfo = cls.getMethod("getAdvertisingIdInfo", Context.class);
        Object info = getAdvertisingIdInfo.invoke(null, context);
        if (info != null) {
            final Method getId = info.getClass().getMethod("isLimitAdTrackingEnabled");
            Object id = getId.invoke(info);
            return (boolean)id;
        }
        return false;
    }

    /**
     * Cache advertising ID for attribution
     */
    protected static void cacheAdvertisingID(final Context context, final CountlyStore store) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(!isLimitAdTrackingEnabled(context)){
                        String aa = getAdvertisingId(context);
                        store.setCachedAdvertisingId(aa);
                    }
                } catch (Throwable t) {
                    if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesAvailabilityException")) {
                        // recoverable, let device ID be null, which will result in storing all requests to Countly server
                        // and rerunning them whenever Advertising ID becomes available
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.i(TAG, "Advertising ID cannot be determined yet");
                        }
                    } else if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesNotAvailableException")) {
                        // non-recoverable, fallback to OpenUDID
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.w(TAG, "Advertising ID cannot be determined because Play Services are not available");
                        }
                    } else {
                        // unexpected
                        Log.e(TAG, "Couldn't get advertising ID", t);
                    }
                }
            }
        }).start();
    }
}
