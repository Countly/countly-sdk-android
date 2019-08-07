package ly.count.sdk.java;

import java.util.HashSet;
import java.util.Set;

import ly.count.sdk.CrashProcessor;
import ly.count.sdk.internal.CoreFeature;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.Utils;

public class Config extends ly.count.sdk.ConfigCore {
    /**
     * Strategy for device id generation
     */
    public enum DeviceIdStrategy {
        UUID(0),
        CUSTOM_ID(10);

        private final int index;

        DeviceIdStrategy(int level) {
            this.index = level;
        }

        public int getIndex() {
            return index;
        }

        public static DeviceIdStrategy fromIndex(int index) {
            if (index == UUID.index) {
                return UUID;
            }
            if (index == CUSTOM_ID.index) {
                return CUSTOM_ID;
            }
            return null;
        }
    }

    /**
     * What this device id is for
     */
    public enum DeviceIdRealm {
        DEVICE_ID(0);

        private final int index;

        DeviceIdRealm(int level) {
            this.index = level;
        }

        public int getIndex() {
            return index;
        }

        public static DeviceIdRealm fromIndex(int index) {
            if (index == DEVICE_ID.index) {
                return DEVICE_ID;
            }
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
        UserProfiles(CoreFeature.UserProfiles.getIndex());
//        StarRating(1 << 12),
//        RemoteConfig(1 << 13),
//        PerformanceMonitoring(1 << 14);

        private final int index;

        Feature(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

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
//            } else if (index == StarRating.index) {
//                return StarRating;
//            } else if (index == RemoteConfig.index) {
//                return RemoteConfig;
//            } else if (index == PerformanceMonitoring.index) {
//                return PerformanceMonitoring;
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
        setSdkName("java-native");
    }

    /**
     * Set device id generation strategy:
     * - {@link DeviceIdStrategy#UUID} to use standard java random UUID. Default.
     * - {@link DeviceIdStrategy#CUSTOM_ID} to use your own device id for Countly.
     *
     * @param strategy       strategy to use instead of default OpenUDID
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
     *
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
                if (f == null) {
                    Log.wtf("Feature cannot be null");
                } else {
                    this.features = this.features | f.getIndex();
                }
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
                if (f == null) {
                    Log.wtf("Feature cannot be null");
                } else {
                    this.features = this.features & ~f.getIndex();
                }
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
     * @param cls     {@link Class} to use instead of Countly SDK standard class
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
     *
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

    public int getFeaturesMap() { return features; }

    /**
     * Whether a feature is enabled in this config, that is exists in {@link #features}
     *
     * @return {@code true} if {@link #features} contains supplied argument, {@code false} otherwise
     */
    public boolean isFeatureEnabled(Feature feature) {
        return (features & feature.index) > 0;
    }

    /**
     * Getter for {@link #moduleOverrides}
     *
     * @return {@link #moduleOverrides} value for {@code Feature} specified
     */
    public Class<? extends Module> getModuleOverride(Feature feature) {
        return moduleOverrides == null ? null : moduleOverrides.get(feature.index);
    }

    public Config setDeviceIdFallbackAllowed(boolean deviceIdFallbackAllowed) {
        super.setDeviceIdFallbackAllowed(deviceIdFallbackAllowed);
        return this;
    }

    /**
     * Force usage of POST method for all requests
     *
     * @return {@code this} instance for method chaining
     */
    public Config enableUsePOST() {
        super.enableUsePOST();
        return this;
    }

    /**
     * Force usage of POST method for all requests.
     *
     * @param usePOST whether to force using POST method for all requests or not
     * @return {@code this} instance for method chaining
     */
    public Config setUsePOST(boolean usePOST) {
        super.setUsePOST(usePOST);
        return this;
    }

    /**
     * Enable parameter tampering protection
     *
     * @param salt String to add to each request bebfore calculating checksum
     * @return {@code this} instance for method chaining
     */
    public Config enableParameterTamperingProtection(String salt) {
        super.enableParameterTamperingProtection(salt);
        return this;
    }

    /**
     * Tag used for logging
     *
     * @param loggingTag tag string to use
     * @return {@code this} instance for method chaining
     */
    public Config setLoggingTag(String loggingTag) {
        super.setLoggingTag(loggingTag);
        return this;
    }

    /**
     * Logging level for Countly SDK
     *
     * @param loggingLevel log level to use
     * @return {@code this} instance for method chaining
     */
    public Config setLoggingLevel(LoggingLevel loggingLevel) {
        super.setLoggingLevel(loggingLevel);
        return this;
    }

    /**
     * Enable test mode:
     * <ul>
     * <li>Raise exceptions when SDK is in inconsistent state as opposed to silently
     * trying to ignore them when testMode is off</li>
     * <li>Put Firebase token under {@code test} devices if {@code Feature.Push} is enabled.</li>
     * </ul>
     * Note: this method automatically sets {@link #loggingLevel} to {@link ly.count.sdk.ConfigCore.LoggingLevel#INFO} in
     * case it was {@link ly.count.sdk.ConfigCore.LoggingLevel#OFF} (default).
     *
     * @return {@code this} instance for method chaining
     */
    public Config enableTestMode() {
        super.enableTestMode();
        return this;
    }

    /**
     * Disable test mode, so SDK will silently avoid raising exceptions whenever possible.
     * Test mode is disabled by default.
     *
     * @return {@code this} instance for method chaining
     */
    public Config disableTestMode() {
        super.disableTestMode();
        return this;
    }

    /**
     * Set maximum amount of time in seconds between two update requests to the server
     * reporting session duration and other parameters if any added between update requests.
     * <p>
     * Update request is also sent when number of unsent events reached {@link #setEventsBufferSize(int)}.
     *
     * @param sendUpdateEachSeconds max time interval between two update requests, set to 0 to disable update requests based on time.
     * @return {@code this} instance for method chaining
     */
    public Config setSendUpdateEachSeconds(int sendUpdateEachSeconds) {
        super.setSendUpdateEachSeconds(sendUpdateEachSeconds);
        return this;
    }

    /**
     * Sets maximum number of events to hold until forcing update request to be sent to the server
     * <p>
     * Update request is also sent when last update request was sent more than {@link #setSendUpdateEachSeconds(int)} seconds ago.
     *
     * @param eventsBufferSize max number of events between two update requests, set to 0 to disable update requests based on events.
     * @return {@code this} instance for method chaining
     */
    public Config setEventsBufferSize(int eventsBufferSize) {
        super.setEventsBufferSize(eventsBufferSize);
        return this;
    }

    /**
     * Disable update requests completely. Only begin & end requests will be sent + some special
     * cases if applicable like User Profile change or Push token updated.
     *
     * @return {@code this} instance for method chaining
     * @see #setSendUpdateEachSeconds(int)
     * @see #setEventsBufferSize(int)
     */
    public Config disableUpdateRequests() {
        super.disableUpdateRequests();
        return this;
    }

    /**
     * !!! Not available for Java SDK !!!
     *
     * @see #autoViewsTracking
     */
    public Config setCrashReportingANRCheckingPeriod(int periodInSeconds) {
        Log.wtf("ANR tracking is not available for Java-native SDK");
        super.setCrashReportingANRCheckingPeriod(0);
        return this;
    }

    /**
     * !!! Not available for Java SDK !!!
     *
     * @return {@code this} instance for method chaining
     */
    public Config disableANRCrashReporting() {
        Log.wtf("ANR tracking is not available for Java-native SDK");
        super.disableANRCrashReporting();
        return this;
    }

    /**
     * Set crash processor class responsible .
     * Defaults automatically to main activity class.
     *
     * @param crashProcessorClass {@link CrashProcessor}-implementing class
     * @return {@code this} instance for method chaining
     */
    public Config setCrashProcessorClass(Class<? extends CrashProcessor> crashProcessorClass) {
        super.setCrashProcessorClass(crashProcessorClass);
        return this;
    }

    /**
     * Override some {@link Module} functionality with your own class.
     *
     * @param feature feature index to override
     * @param cls     {@link Class} to use instead of Countly SDK standard class
     * @return {@code this} instance for method chaining
     */
    protected Config overrideModule(Integer feature, Class<? extends Module> cls) {
        super.overrideModule(feature, cls);
        return this;
    }

    /**
     * Enable GDPR compliance by disallowing SDK to record any data until corresponding consent
     * calls are made.
     *
     * @param requiresConsent {@code true} to enable GDPR compliance
     * @return {@code this} instance for method chaining
     */
    public Config setRequiresConsent(boolean requiresConsent) {
        super.setRequiresConsent(requiresConsent);
        return this;
    }

    /**
     * !!! Not available for Java SDK !!!
     */
    public Config setAutoViewsTracking(boolean autoViewsTracking) {
        if (autoViewsTracking) {
            Log.wtf("Auto views tracking is not available for Java-native SDK");
        }
        super.setAutoViewsTracking(false);
        return this;
    }

    /**
     * !!! Not available for Java SDK !!!
     */
    public Config setAutoSessionsTracking(boolean autoSessionsTracking) {
        if (autoSessionsTracking) {
            Log.wtf("Auto sessions tracking is not available for Java-native SDK");
        }
        super.setAutoSessionsTracking(false);
        return this;
    }

    /**
     * !!! Not available for Java SDK !!!
     */
    public Config setSessionAutoCloseAfter(int sessionAutoCloseAfter) {
        if (sessionAutoCloseAfter != 0) {
            Log.wtf("Auto sessions tracking is not available for Java-native SDK");
        }
        super.setSessionAutoCloseAfter(0);
        return this;
    }
}