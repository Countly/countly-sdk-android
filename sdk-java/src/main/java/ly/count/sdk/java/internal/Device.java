package ly.count.sdk.java.internal;

import ly.count.sdk.internal.CtxCore;
import ly.count.sdk.internal.DeviceCore;
import ly.count.sdk.internal.Params;

/**
 * Class encapsulating most of device-specific logic: metrics, info, etc.
 * In Java we don't have access to the metrics, so the SDK leaves this up to developer to set these if needed.
 */
public class Device extends DeviceCore {
    public static Device dev = new Device();

    private String device;
    private String resolution;
    private String appVersion;
    private String manufacturer;
    private String cpu;
    private String openGL;
    private Float batteryLevel;
    private String orientation;
    private Boolean online;
    private Boolean muted;

    private Device() {
        DeviceCore.dev = dev = this;
    }

    /**
     * Return device name stored by {@link #setDevice(String)}
     * @return String
     */
    public String getDevice() {
        return device;
    }

    /**
     * Set device name
     * @return this instance for method chaining
     */
    public Device setDevice(String device) {
        this.device = device;
        return this;
    }

    /**
     * Return resolution stored by {@link #setResolution(String)}
     * @return String
     */
    public String getResolution() {
        return resolution;
    }

    /**
     * Set device resolution
     * @return this instance for method chaining
     */
    public Device setResolution(String resolution) {
        this.resolution = resolution;
        return this;
    }

    /**
     * Return app version stored by {@link #setAppVersion(String)}
     * @return String
     */
    public String getAppVersion() {
        return appVersion;
    }

    /**
     * Set app version
     * @return this instance for method chaining
     */
    public Device setAppVersion(String appVersion) {
        this.appVersion = appVersion;
        return this;
    }

    /**
     * Return device manufacturer stored by {@link #setManufacturer(String)}
     * @return String
     */
    public String getManufacturer() {
        return manufacturer;
    }

    /**
     * Set device manufacturer
     * @return this instance for method chaining
     */
    public Device setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
        return this;
    }

    /**
     * Return CPU name stored by {@link #setCpu(String)}
     * @return String
     */
    public String getCpu() {
        return cpu;
    }

    /**
     * Set CPU name
     * @return this instance for method chaining
     */
    public Device setCpu(String cpu) {
        this.cpu = cpu;
        return this;
    }

    /**
     * Return OpenGL version stored by {@link #setOpenGL(String)}
     * @return String
     */
    public String getOpenGL() {
        return openGL;
    }

    /**
     * Set OpenGL version
     * @return this instance for method chaining
     */
    public Device setOpenGL(String openGL) {
        this.openGL = openGL;
        return this;
    }

    /**
     * Return battery level stored by {@link #setBatteryLevel(Float)}
     * @return Float
     */
    public Float getBatteryLevel() {
        return batteryLevel;
    }

    /**
     * Set battery level (0 .. 1)
     * @return this instance for method chaining
     */
    public Device setBatteryLevel(Float batteryLevel) {
        this.batteryLevel = batteryLevel;
        return this;
    }

    /**
     * Return device orientation stored by {@link #setOrientation(String)}
     * @return String
     */
    public String getOrientation() {
        return orientation;
    }

    /**
     * Set device orientation
     * @return this instance for method chaining
     */
    public Device setOrientation(String orientation) {
        this.orientation = orientation;
        return this;
    }

    /**
     * Return whether the device is online stored by {@link #setDevice(String)}
     * @return Boolean
     */
    public Boolean isOnline() {
        return online;
    }

    /**
     * Set whether the device is online
     * @return this instance for method chaining
     */
    public Device setOnline(Boolean online) {
        this.online = online;
        return this;
    }

    /**
     * Return whether the device is muted stored by {@link #setDevice(String)}
     * @return Boolean
     */
    public Boolean isMuted() {
        return muted;
    }

    /**
     * Set whether the device is muted
     * @return this instance for method chaining
     */
    public Device setMuted(Boolean muted) {
        this.muted = muted;
        return this;
    }

    /**
     * Build metrics {@link Params} object as required by Countly server
     *
     * @param sdkctx Ctx in which to request metrics
     */
    @Override
    public Params buildMetrics(final CtxCore sdkctx) {
        Params params = new Params();
        params.obj("metrics")
                .put("_device", getDevice())
                .put("_os", getOS())
                .put("_os_version", getOSVersion())
                .put("_resolution", getResolution())
                .put("_locale", getLocale())
                .put("_app_version", getAppVersion())
                .add();

        return params;
    }

}
