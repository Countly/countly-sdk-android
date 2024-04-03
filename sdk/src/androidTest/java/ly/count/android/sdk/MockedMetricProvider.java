package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;

public class MockedMetricProvider implements MetricProvider {

    public MockedMetricProvider() {

    }

    @Override public String getOS() {
        return "A";
    }

    @Override public String getOSVersion() {
        return "B";
    }

    @Override public String getDevice() {
        return "C";
    }

    @Override public String getManufacturer() {
        return "D";
    }

    @Override public String getResolution(Context context) {
        return "E";
    }

    @Override public String getDensity(Context context) {
        return "F";
    }

    @Override public String getCarrier(Context context) {
        return "G";
    }

    @Override public int getTimezoneOffset() {
        return 66;
    }

    @Override public String getLocale() {
        return "H";
    }

    @NonNull @Override public String getAppVersion(Context context) {
        return Countly.DEFAULT_APP_VERSION;
    }

    @Override public String getStore(Context context) {
        return "J";
    }

    @Override public String getDeviceType(Context context) {
        return "K";
    }

    @Override public long getTotalRAM() {
        return 42;
    }

    @Override public String getRamCurrent(Context context) {
        return "12";
    }

    @Override public String getRamTotal() {
        return "48";
    }

    @Override public String getCpu() {
        return "N";
    }

    @Override public String getOpenGL(Context context) {
        return "O";
    }

    @Override public String getDiskCurrent() {
        return "23";
    }

    @Override public String getDiskTotal() {
        return "45";
    }

    @Nullable @Override public String getBatteryLevel(Context context) {
        return "6";
    }

    @Nullable @Override public String getOrientation(Context context) {
        return "S";
    }

    @Override public String isRooted() {
        return "T";
    }

    @Nullable @Override public String isOnline(Context context) {
        return "U";
    }

    @Override public String isMuted(Context context) {
        return "V";
    }

    @Override public String hasHinge(Context context) {
        return "Z";
    }

    @Override public String getRunningTime() {
        return "88";
    }
}
