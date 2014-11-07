package ly.count.android.api;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.Method;

public class AdvertisingIdAdapter {
    private static final String TAG = "AdvertisingIdAdapter";
    private final static String ADVERTISING_ID_CLIENT_CLASS_NAME = "com.google.android.gms.ads.identifier.AdvertisingIdClient";
    private static Handler handler;

    public static boolean isAdvertisingIdAvailable() {
        boolean advertisingIdAvailable = false;
        try {
            Class.forName(ADVERTISING_ID_CLIENT_CLASS_NAME);
            advertisingIdAvailable = true;
        }
        catch (ClassNotFoundException ignored) {}
        return advertisingIdAvailable;
    }

    public static void setAdvertisingId(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String id = getAdvertisingId(context);
                DeviceInfo.setDeviceID(id);
            }
        }).start();
    }

    public static String getAdvertisingId(Context context) {
        try {
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
        catch (Throwable logged) {
            Log.e(TAG, "Couldn't get advertising ID", logged);
            return null;
        }
    }
}
