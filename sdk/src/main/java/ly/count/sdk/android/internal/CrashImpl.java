package ly.count.sdk.android.internal;

import ly.count.sdk.Crash;
import ly.count.sdk.internal.Storable;

/**
 * Crash-encapsulating class
 */

public class CrashImpl extends ly.count.sdk.internal.CrashImpl implements Crash, Storable {

    @Override
    protected CrashImpl add(String key, Object value) {
        return (CrashImpl) super.add(key, value);
    }

    public CrashImpl putMetrics(Ctx ctx, Long runningTime) {
        super.putMetrics(ctx, runningTime);
        return add("_device", Device.getDevice())
                .add("_os", Device.getOS())
                .add("_os_version", Device.getOSVersion())
                .add("_resolution", Device.getResolution(ctx.getContext()))
                .add("_app_version", Device.getAppVersion(ctx))
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
}
