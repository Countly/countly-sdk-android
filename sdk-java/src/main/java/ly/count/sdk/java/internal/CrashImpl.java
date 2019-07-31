package ly.count.sdk.java.internal;

import ly.count.sdk.Crash;
import ly.count.sdk.internal.CrashImplCore;
import ly.count.sdk.internal.Storable;

public class CrashImpl extends CrashImplCore implements Crash, Storable {

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
        super.putMetricsCore(ctx, runningTime);
        return add("_device", Device.dev.getDevice())
                .add("_os", Device.dev.getOS())
                .add("_os_version", Device.dev.getOSVersion())
                .add("_resolution", Device.dev.getResolution())
                .add("_app_version", Device.dev.getAppVersion())
                .add("_manufacture", Device.dev.getManufacturer())
                .add("_cpu", Device.dev.getCpu())
                .add("_opengl", Device.dev.getOpenGL())
                .add("_ram_current", Device.dev.getRAMAvailable())
                .add("_ram_total", Device.dev.getRAMTotal())
                .add("_disk_current", Device.dev.getDiskAvailable())
                .add("_disk_total", Device.dev.getDiskTotal())
                .add("_bat", Device.dev.getBatteryLevel())
                .add("_run", runningTime)
                .add("_orientation", Device.dev.getOrientation())
                .add("_online", Device.dev.isOnline())
                .add("_muted", Device.dev.isMuted());
    }
}
