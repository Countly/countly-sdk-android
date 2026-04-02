package ly.count.android.sdk;

import android.content.Context;
import android.util.DisplayMetrics;
import androidx.annotation.Nullable;

public interface MetricProvider {
    default String getOS() {
        return null;
    }

    default String getOSVersion() {
        return null;
    }

    default String getDevice() {
        return null;
    }

    default String getManufacturer() {
        return null;
    }

    default String getResolution(final Context context) {
        return null;
    }

    default String getDensity(final Context context) {
        return null;
    }

    default String getCarrier(final Context context) {
        return null;
    }

    default String getTimezoneOffset() {
        return null;
    }

    default String getLocale() {
        return null;
    }

    default String getAppVersion(final Context context) {
        return null;
    }

    default String getStore(final Context context) {
        return null;
    }

    default String getDeviceType(final Context context) {
        return null;
    }

    default String getTotalRAM() {
        return null;
    }

    default String getRamCurrent(Context context) {
        return null;
    }

    default String getRamTotal() {
        return null;
    }

    default String getCpu() {
        return null;
    }

    default String getOpenGL(Context context) {
        return null;
    }

    @Nullable default String getBatteryLevel(Context context) {
        return null;
    }

    @Nullable default String getOrientation(Context context) {
        return null;
    }

    default String isRooted() {
        return null;
    }

    @Nullable default String isOnline(Context context) {
        return null;
    }

    default String isMuted(Context context) {
        return null;
    }

    default String hasHinge(Context context) {
        return null;
    }

    default String getRunningTime() {
        return null;
    }

    default DisplayMetrics getDisplayMetrics(Context context) {
        return null;
    }

    default DiskMetric getDiskSpaces(Context context) {
        return null;
    }
}
