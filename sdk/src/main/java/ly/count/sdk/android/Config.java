package ly.count.sdk.android;

import java.util.HashSet;
import java.util.Set;

import ly.count.sdk.internal.CoreFeature;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.Utils;

public class Config extends ly.count.sdk.Config {
    /**
     * Strategy for device id generation
     */
    public enum DeviceIdStrategy {
        UUID(0),
        ANDROID_ID(1),
        INSTANCE_ID(2),
        ADVERTISING_ID(3),
        CUSTOM_ID(10);

        private final int index;

        DeviceIdStrategy(int level){
            this.index = level;
        }

        public int getIndex(){ return index; }

        public static DeviceIdStrategy fromIndex(int index){
            if (index == UUID.index) { return UUID; }
            if (index == ANDROID_ID.index) { return ANDROID_ID; }
            if (index == ADVERTISING_ID.index) { return ADVERTISING_ID; }
            if (index == INSTANCE_ID.index) { return INSTANCE_ID; }
            if (index == CUSTOM_ID.index) { return CUSTOM_ID; }
            return null;
        }
    }

    /**
     * What this device id is for
     */
    public enum DeviceIdRealm {
        DEVICE_ID(0),
        FCM_TOKEN(1),
        ADVERTISING_ID(2);

        private final int index;

        DeviceIdRealm(int level){
            this.index = level;
        }

        public int getIndex(){ return index; }

        public static DeviceIdRealm fromIndex(int index) {
            if (index == DEVICE_ID.index) { return DEVICE_ID; }
            if (index == FCM_TOKEN.index) { return FCM_TOKEN; }
            if (index == ADVERTISING_ID.index) { return ADVERTISING_ID; }
            return null;
        }
    }

    /**
     * Enumeration of Countly SDK features
     */
    public enum Feature {
        Events(CoreFeature.Events.getIndex()),
        Sessions(CoreFeature.Sessions.getIndex()),
        Views(CoreFeature.Views.getIndex()),
        CrashReporting(CoreFeature.CrashReporting.getIndex()),
        Location(CoreFeature.Location.getIndex()),
        UserProfiles(CoreFeature.UserProfiles.getIndex()),
        Push(1 << 10),
        Attribution(1 << 11),
        StarRating(1 << 12),
        RemoteConfig(1 << 13),
        PerformanceMonitoring(1 << 14);

        private final int index;

        Feature(int index){ this.index = index; }

        public int getIndex(){ return index; }

        public static Feature byIndex(int index) {
            if (index == Events.index) {
                return Events;
            } else if (index == Sessions.index) {
                return Sessions;
            } else if (index == Views.index) {
                return Views;
            } else if (index == CrashReporting.index) {
                return CrashReporting;
            } else if (index == Location.index) {
                return Location;
            } else if (index == UserProfiles.index) {
                return UserProfiles;
            } else if (index == Push.index) {
                return Push;
            } else if (index == Attribution.index) {
                return Attribution;
            } else if (index == StarRating.index) {
                return StarRating;
            } else if (index == RemoteConfig.index) {
                return RemoteConfig;
            } else if (index == PerformanceMonitoring.index) {
                return PerformanceMonitoring;
            } else {
                return null;
            }
        }
    }

    /**
     * The only Config constructor.
     *
     * @param serverURL    valid {@link java.net.URL} of Countly server
     * @param serverAppKey App Key from Management -> Applications section of your Countly Dashboard
     */
    public Config(String serverURL, String serverAppKey) {
        super(serverURL, serverAppKey);
    }

    /**
     * Set device id generation strategy:
     *
     * - {@link DeviceIdStrategy#INSTANCE_ID} to use InstanceID if available (requires Play Services).
     * Falls back to OpenUDID if no Play Services available, default.
     *
     * - {@link DeviceIdStrategy#ANDROID_ID} to use OpenUDID derivative - unique, semi-persistent
     * (stored in {@code SharedPreferences} in Android).
     *
     * - {@link DeviceIdStrategy#ADVERTISING_ID} to use com.google.android.gms.ads.identifier.AdvertisingIdClient
     * if available (requires Play Services). Falls back to OpenUDID if no Play Services available.
     *
     * - {@link DeviceIdStrategy#CUSTOM_ID} to use your own device id for Countly.
     *
     * @param strategy strategy to use instead of default OpenUDID
     * @param customDeviceId device id for use with {@link DeviceIdStrategy#CUSTOM_ID}
     * @return {@code this} instance for method chaining
     */
    public Config setDeviceIdStrategy(DeviceIdStrategy strategy, String customDeviceId) {
        if (strategy == null) {
            Log.wtf("DeviceIdStrategy cannot be null");
        } else {
            if (strategy == DeviceIdStrategy.CUSTOM_ID) {
                return setCustomDeviceId(customDeviceId);
            }
            this.deviceIdStrategy = strategy.index;
        }
        return this;
    }

    /**
     * Shorthand method for {@link #setDeviceIdStrategy(DeviceIdStrategy, String)}
     *
     * @param strategy strategy to use instead of default OpenUDID
     * @return {@code this} instance for method chaining
     */
    public Config setDeviceIdStrategy(DeviceIdStrategy strategy) {
        return setDeviceIdStrategy(strategy, null);
    }

    /**
     * Set device id to specific string and set generation strategy to {@link DeviceIdStrategy#CUSTOM_ID}.
     *
     * @param customDeviceId device id for use with {@link DeviceIdStrategy#CUSTOM_ID}
     * @return {@code this} instance for method chaining
     */
    public Config setCustomDeviceId(String customDeviceId) {
        if (Utils.isEmpty(customDeviceId)) {
            Log.wtf("DeviceIdStrategy.CUSTOM_ID strategy cannot be used without device id specified");
        } else {
            this.customDeviceId = customDeviceId;
            this.deviceIdStrategy = DeviceIdStrategy.CUSTOM_ID.index;
        }
        return this;
    }

    /**
     * Getter for {@link #deviceIdStrategy}
     * @return {@link #deviceIdStrategy} value as enum
     */
    public DeviceIdStrategy getDeviceIdStrategyEnum() {
        return DeviceIdStrategy.fromIndex(deviceIdStrategy);
    }

    /**
     * Enable one or many features of Countly SDK instead of {@link #setFeatures(Feature...)}.
     *
     * @param features features to enable
     * @return {@code this} instance for method chaining
     */
    public Config enableFeatures(Feature... features) {
        if (features == null) {
            Log.wtf("Features array cannot be null");
        } else {
            for (Feature f : features) {
                this.features = this.features | f.getIndex();
            }
        }
        return this;
    }

    /**
     * Disable one or many features of Countly SDK instead of {@link #setFeatures(Feature...)}.
     *
     * @param features features to disable
     * @return {@code this} instance for method chaining
     */
    public Config disableFeatures(Feature... features) {
        if (features == null) {
            Log.wtf("Features array cannot be null");
        } else {
            for (Feature f : features) {
                this.features = this.features & ~f.getIndex();
            }
        }
        return this;
    }

    /**
     * Set enabled features all at once instead of {@link #enableFeatures(Feature...)}.
     *
     * @param features variable args of features to enable
     * @return {@code this} instance for method chaining
     */
    public Config setFeatures(Feature... features) {
        this.features = 0;

        if (features != null && features.length > 0) {
            for (int i = 0; i < features.length; i++) {
                if (features[i] == null) {
                    Log.wtf(i + "-th feature is null in setFeatures");
                } else {
                    this.features = this.features | features[i].index;
                }
            }
        }
        return this;
    }


    /**
     * Override some {@link Module} functionality with your own class.
     *
     * @param feature {@link Feature} to override
     * @param cls {@link Class} to use instead of Countly SDK standard class
     * @return {@code this} instance for method chaining
     */
    public Config overrideModule(Feature feature, Class<? extends Module> cls) {
        if (feature == null || cls == null) {
            Log.wtf("Feature & class cannot be null");
        } else {
            super.overrideModule(feature.index, cls);
        }
        return this;
    }

    /**
     * Getter for {@link #features}
     * @return {@link #features} value
     */
    public Set<Feature> getFeatures() {
        Set<Feature> ftrs = new HashSet<>();
        for (Feature f : Feature.values()) {
            if ((f.index & features) > 0) {
                ftrs.add(f);
            }
        }
        return ftrs;
    }

    /**
     * Whether a feature is enabled in this config, that is exists in {@link #features}
     * @return {@code true} if {@link #features} contains supplied argument, {@code false} otherwise
     */
    public boolean isFeatureEnabled(Feature feature) {
        return (features & feature.index) > 0;
    }

    /**
     * Getter for {@link #moduleOverrides}
     * @return {@link #moduleOverrides} value for {@code Feature} specified
     */
    public Class<? extends Module> getModuleOverride(Feature feature) {
        return moduleOverrides == null ? null : moduleOverrides.get(feature.index);
    }

}
