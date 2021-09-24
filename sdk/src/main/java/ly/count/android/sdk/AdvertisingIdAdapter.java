package ly.count.android.sdk;

import android.content.Context;
import java.lang.reflect.Method;

public class AdvertisingIdAdapter {
    private final static String ADVERTISING_ID_CLIENT_CLASS_NAME = "com.google.android.gms.ads.identifier.AdvertisingIdClient";

    public static boolean isAdvertisingIdAvailable() {
        boolean advertisingIdAvailable = false;
        try {
            Class.forName(ADVERTISING_ID_CLIENT_CLASS_NAME);
            advertisingIdAvailable = true;
        } catch (ClassNotFoundException ignored) {
        }
        return advertisingIdAvailable;
    }

    private static String getAdvertisingId(final Context context) throws Throwable {
        final Class<?> cls = Class.forName(ADVERTISING_ID_CLIENT_CLASS_NAME);
        final Method getAdvertisingIdInfo = cls.getMethod("getAdvertisingIdInfo", Context.class);
        Object info = getAdvertisingIdInfo.invoke(null, context);
        if (info != null) {
            final Method getId = info.getClass().getMethod("getId");
            Object id = getId.invoke(info);
            return (String) id;
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isLimitAdTrackingEnabled(final Context context) {
        //noinspection CatchMayIgnoreException
        try {
            final Class<?> cls = Class.forName(ADVERTISING_ID_CLIENT_CLASS_NAME);
            final Method getAdvertisingIdInfo = cls.getMethod("getAdvertisingIdInfo", Context.class);
            Object info = getAdvertisingIdInfo.invoke(null, context);
            if (info != null) {
                final Method getId = info.getClass().getMethod("isLimitAdTrackingEnabled");
                Object id = getId.invoke(info);
                return (boolean) id;
            }
        } catch (Throwable t) {
            if (t.getCause() != null && t.getCause().getClass().toString().contains("java.lang.ClassNotFoundException") &&
                t.getCause().getMessage().contains("com.google.android.gms.ads.identifier.AdvertisingIdClient")) {
                Countly.sharedInstance().L.w("[AdvertisingIdAdapter] Play Services are not available, while checking if limited ad tracking enabled");
            }
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
                    if (!isLimitAdTrackingEnabled(context)) {
                        String adId = getAdvertisingId(context);
                        store.setCachedAdvertisingId(adId);
                    } else {
                        store.setCachedAdvertisingId("");
                    }
                } catch (Throwable t) {
                    if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesAvailabilityException")) {
                        Countly.sharedInstance().L.i("[AdvertisingIdAdapter] Advertising ID cannot be determined yet, while caching");
                    } else if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesNotAvailableException")) {
                        Countly.sharedInstance().L.w("[AdvertisingIdAdapter] Advertising ID cannot be determined because Play Services are not available, while caching");
                    } else if (t.getCause() != null && t.getCause().getClass().toString().contains("java.lang.ClassNotFoundException") &&
                        t.getCause().getMessage().contains("com.google.android.gms.ads.identifier.AdvertisingIdClient")) {
                        Countly.sharedInstance().L.w("[AdvertisingIdAdapter] Play Services are not available, while caching advertising id");
                    } else {
                        // unexpected
                        Countly.sharedInstance().L.e("[AdvertisingIdAdapter] Couldn't get advertising ID, while caching", t);
                    }
                }
            }
        }).start();
    }
}
