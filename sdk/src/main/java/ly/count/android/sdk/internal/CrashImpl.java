package ly.count.android.sdk.internal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ly.count.sdk.Crash;
import ly.count.sdk.internal.Storable;

/**
 * Crash-encapsulating class
 */

public class CrashImpl implements Crash, Storable {
    private static final Log.Module L = Log.module("CrashImpl");

    private final Long id;
    private final JSONObject data;
    private Throwable throwable;

    CrashImpl() {
        this(Device.uniformTimestamp());
    }

    CrashImpl(Long id) {
        this.id = id;
        this.data = new JSONObject();
        this.add("_nonfatal", true);
    }

    @Override
    public CrashImpl setThrowable(Throwable throwable) {
        if (throwable == null) {
            L.wtf("Throwable cannot be null");
            return this;
        } else {
            this.throwable = throwable;
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
    public CrashImpl setSegments(Map<String, String> segments) {
        if (segments != null && segments.size() > 0) {
            return add("_custom", new JSONObject(segments));
        }
        return this;
    }

    @Override
    public CrashImpl setLogs(String[] logs) {
        if (logs != null && logs.length > 0) {
            return add("_logs", Utils.join(Arrays.asList(logs), "\n"));
        }
        return this;
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public boolean isFatal() {
        try {
            return !this.data.has("_nonfatal") || !this.data.getBoolean("_nonfatal");
        } catch (JSONException e) {
            return true;
        }
    }

    @Override
    public String getName() {
        try {
            return this.data.has("_name") ? this.data.getString("_name") : null;
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public Map<String, String> getSegments() {
        try {
            if (!this.data.has("_custom")) {
                return null;
            }
            JSONObject object = this.data.getJSONObject("_custom");
            Map<String, String> map = new HashMap<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                map.put(key, object.getString(key));
            }
            return map;
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public List<String> getLogs() {
        try {
            String logs = this.data.getString("_logs");
            return Utils.isEmpty(logs) ? null : Arrays.asList(logs.split("\n"));
        } catch (JSONException e) {
            return null;
        }
    }

    CrashImpl add(String key, Object value) {
        if (Utils.isNotEmpty(key) && value != null) {
            try {
                this.data.put(key, value);
            } catch (JSONException e) {
                L.wtf("Couldn't add " + key + " to a crash", e);
            }
        }
        return this;
    }

    @Override
    public byte[] store() {
        try {
            return data.toString().getBytes(Utils.UTF8);
        } catch (UnsupportedEncodingException e) {
            L.wtf("UTF is not supported", e);
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
                L.e("Couldn't decode crash data successfully", e);
            }
            return true;
        } catch (UnsupportedEncodingException e) {
            L.wtf("Cannot deserialize crash", e);
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
