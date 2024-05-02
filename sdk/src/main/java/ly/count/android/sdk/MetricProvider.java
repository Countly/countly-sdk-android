package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

interface MetricProvider {
    String getOS();

    String getOSVersion();

    String getDevice();

    String getManufacturer();

    String getResolution(final Context context);

    String getDensity(final Context context);

    String getCarrier(final Context context);

    int getTimezoneOffset();

    String getLocale();

    @NonNull
    String getAppVersion(final Context context);

    String getStore(final Context context);

    String getDeviceType(final Context context);

    long getTotalRAM();

    String getRamCurrent(Context context);

    String getRamTotal();

    String getCpu();

    String getOpenGL(Context context);

    String getDiskCurrent();

    String getDiskTotal();

    @Nullable String getBatteryLevel(Context context);

    @Nullable String getOrientation(Context context);

    String isRooted();

    @Nullable String isOnline(Context context);

    String isMuted(Context context);

    String hasHinge(Context context);

    String getRunningTime();
}
