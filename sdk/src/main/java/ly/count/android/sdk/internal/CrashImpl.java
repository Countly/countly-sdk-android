package ly.count.android.sdk.internal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import ly.count.android.sdk.CountlyPush;
import ly.count.android.sdk.Crash;

/**
 * Crash-encapsulating class
 */

public class CrashImpl implements Crash, Storable {
    private final Long id;
    private final JSONObject data;

    CrashImpl() {
        this(System.nanoTime());
    }

    CrashImpl(Long id) {
        this.id = id;
        this.data = new JSONObject();
        this.add("_nonfatal", true);
    }

    @Override
    public CrashImpl setThrowable(Throwable throwable) {
        if (throwable == null) {
            Log.wtf("Throwable cannot be null");
            return this;
        } else {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            return add("_error", sw.toString());
        }
    }

    @Override
    public CrashImpl setException(Exception e) {
        return setThrowable(e);
    }

    @Override
    public CrashImpl setFatal(boolean fatal) {
        return add("_nonfatal", !fatal);
    }

    @Override
    public CrashImpl setName(String name) {
        return add("_name", name);
    }

    @Override
    public CrashImpl setDetails(String details) {
        if (Utils.isNotEmpty(details)) {
            return add("_logs", details);
        }
        return this;
    }

    CrashImpl add(String key, Object value) {
        if (Utils.isNotEmpty(key) && value != null) {
            try {
                this.data.put(key, value);
            } catch (JSONException e) {
                Log.wtf("Couldn't add " + key + " to a crash", e);
            }
        }
        return this;
    }

    @Override
    public byte[] store() {
        try {
            return data.toString().getBytes(Utils.UTF8);
        } catch (UnsupportedEncodingException e) {
            Log.wtf("UTF is not supported", e);
            return null;
        }
    }

    public boolean restore(byte[] data) {
        try {
            String json = new String (data, Utils.UTF8);
            try {
                JSONObject obj = new JSONObject(json);
                Iterator<String> iterator = obj.keys();
                while (iterator.hasNext()) {
                    String k = iterator.next();
                    this.data.put(k, obj.get(k));
                }
            } catch (JSONException e) {
                Log.e("Couldn't decode crash data successfully", e);
            }
            return true;
        } catch (UnsupportedEncodingException e) {
            Log.wtf("Cannot deserialize crash", e);
        }

        return false;
    }

    @Override
    public Long storageId() {
        return id;
    }

    @Override
    public String storagePrefix() {
        return getStoragePrefix();
    }

    public static String getStoragePrefix() {
        return "crash";
    }

    public CrashImpl putMetrics(Context ctx, Long runningTime) {
        return add("_device", Device.getDevice())
                .add("_os", Device.getOS())
                .add("_os_version", Device.getOSVersion())
                .add("_resolution", Device.getResolution(ctx.getContext()))
                .add("_app_version", Device.getAppVersion(ctx.getContext()))
                .add("_manufacture", Device.getManufacturer())
                .add("_cpu", Device.getCpu())
                .add("_opengl", Device.getOpenGL(ctx.getContext()))
                .add("_ram_current", Device.getRAMAvailable(ctx.getContext()))
                .add("_ram_total", Device.getRAMTotal())
                .add("_disk_current", Device.getDiskAvailable())
                .add("_disk_total", Device.getDiskTotal())
                .add("_bat", Device.getBatteryLevel(ctx.getContext()))
                .add("_run", runningTime)
                .add("_orientation", Device.getOrientation(ctx.getContext()))
                .add("_root", Device.isRooted())
                .add("_online", Device.isOnline(ctx.getContext()))
                .add("_muted", Device.isMuted(ctx.getContext()))
                .add("_background", !Device.isAppRunningInForeground(ctx.getContext()));
    }

    public String getJSON() {
        return data.toString();
    }
}
