package ly.count.sdk.android.internal;

import ly.count.sdk.Crash;
import ly.count.sdk.internal.Storable;

/**
 * Crash-encapsulating class
 */

public class CrashImpl extends ly.count.sdk.internal.CrashImpl implements Crash, Storable {

    protected CrashImpl() {
        super();
    }

    public CrashImpl(Long id) {
        super(id);
    }

    @Override
    protected CrashImpl add(String key, Object value) {
        return (CrashImpl) super.add(key, value);
    }

    public CrashImpl putMetrics(Ctx ctx, Long runningTime) {
        super.putMetrics(ctx, runningTime);
        return add("_device", Device.dev.getDevice())
                .add("_os", Device.dev.getOS())
                .add("_os_version", Device.dev.getOSVersion())
                .add("_resolution", Device.dev.getResolution(ctx.getContext()))
                .add("_app_version", Device.dev.getAppVersion(ctx))
                .add("_manufacture", Device.dev.getManufacturer())
                .add("_cpu", Device.dev.getCpu())
                .add("_opengl", Device.dev.getOpenGL(ctx.getContext()))
                .add("_ram_current", Device.dev.getRAMAvailable(ctx.getContext()))
                .add("_ram_total", Device.dev.getRAMTotal())
                .add("_disk_current", Device.dev.getDiskAvailable())
                .add("_disk_total", Device.dev.getDiskTotal())
                .add("_bat", Device.dev.getBatteryLevel(ctx.getContext()))
                .add("_run", runningTime)
                .add("_orientation", Device.dev.getOrientation(ctx.getContext()))
                .add("_root", Device.dev.isRooted())
                .add("_online", Device.dev.isOnline(ctx.getContext()))
                .add("_muted", Device.dev.isMuted(ctx.getContext()))
                .add("_background", !Device.dev.isAppRunningInForeground(ctx.getContext()));
    }
}
