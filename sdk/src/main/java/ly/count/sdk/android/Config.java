package ly.count.sdk.android;

import java.util.HashSet;
import java.util.Set;

import ly.count.sdk.CrashProcessor;
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
            if (index == ANDROID_ID.index) {
                return ANDROID_ID;
            }
            if (index == ADVERTISING_ID.index) {
                return ADVERTISING_ID;
            }
            if (index == INSTANCE_ID.index) {
                return INSTANCE_ID;
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
        DEVICE_ID(0),
        FCM_TOKEN(1),
        ADVERTISING_ID(2);

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
            if (index == FCM_TOKEN.index) {
                return FCM_TOKEN;
            }
            if (index == ADVERTISING_ID.index) {
                return ADVERTISING_ID;
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
        UserProfiles(CoreFeature.UserProfiles.getIndex()),
        StarRating(CoreFeature.StarRating.getIndex()),
        Push(1 << 10),
        Attribution(1 << 11),
        RemoteConfig(1 << 13),
        PerformanceMonitoring(1 << 14);

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
        setSdkName("java-native-android");
        setSdkVersion(ly.count.sdk.android.sdk.BuildConfig.VERSION_NAME);
        enableFeatures(Feature.Events, Feature.Sessions, Feature.CrashReporting, Feature.Location, Feature.UserProfiles);
    }

    /**
     * Set device id generation strategy:
     * - {@link DeviceIdStrategy#UUID} to use standard java random UUID. Default.
     * - {@link DeviceIdStrategy#INSTANCE_ID} to use InstanceID if available (requires Play Services).
     * Falls back to UUID if no Play Services available.
     * - {@link DeviceIdStrategy#ANDROID_ID} to use OpenUDID derivative - unique, semi-persistent
     * (stored in {@code SharedPreferences} in Android). Falls back to INSTANCE_ID and then to UUID.
     * - {@link DeviceIdStrategy#ADVERTISING_ID} to use com.google.android.gms.ads.identifier.AdvertisingIdClient
     * if available (requires Play Services). Falls back to ANDROID_ID, INSTANCE_ID and then to UUID.
     * - {@link DeviceIdStrategy#CUSTOM_ID} to use your own device id for Countly.
     *
     * @param strategy       strategy to use instead of default UUID
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
     * @param strategy strategy to use instead of default UUID
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
                if (f == null) {
                    Log.wtf("Feature cannot be null");
                }
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
     * Note: this method automatically sets {@link #loggingLevel} to {@link LoggingLevel#INFO} in
     * case it was {@link LoggingLevel#OFF} (default).
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
     * Set minimal amount of time between sessions in seconds.
     * For now used only when recovering from a crash as a session extension period.
     *
     * @param sessionCooldownPeriod min time interval between two sessions
     * @return {@code this} instance for method chaining
     */
    public Config setSessionCooldownPeriod(int sessionCooldownPeriod) {
        super.setSessionCooldownPeriod(sessionCooldownPeriod);
        return this;
    }

    /**
     * Change name of SDK used in HTTP requests
     *
     * @param sdkName new name of SDK
     * @return {@code this} instance for method chaining
     */
    public Config setSdkName(String sdkName) {
        super.setSdkName(sdkName);
        return this;
    }

    /**
     * Change version of SDK used in HTTP requests
     *
     * @param sdkVersion new version of SDK
     * @return {@code this} instance for method chaining
     */
    public Config setSdkVersion(String sdkVersion) {
        super.setSdkVersion(sdkVersion);
        return this;
    }

    /**
     * Change application name reported to Countly server
     *
     * @param name new name
     * @return {@code this} instance for method chaining
     */
    public Config setApplicationName(String name) {
        super.setApplicationName(name);
        return this;
    }

    /**
     * Change application version reported to Countly server
     *
     * @param version new version
     * @return {@code this} instance for method chaining
     */
    public Config setApplicationVersion(String version) {
        super.setApplicationVersion(version);
        return this;
    }

    /**
     * Set connection timeout in seconds for HTTP requests SDK sends to Countly server. Defaults to 30.
     *
     * @param seconds network timeout in seconds
     * @return {@code this} instance for method chaining
     */
    public Config setNetworkConnectTimeout(int seconds) {
        super.setNetworkConnectTimeout(seconds);
        return this;
    }

    /**
     * Set read timeout in seconds for HTTP requests SDK sends to Countly server. Defaults to 30.
     *
     * @param seconds read timeout in seconds
     * @return {@code this} instance for method chaining
     */
    public Config setNetworkReadTimeout(int seconds) {
        super.setNetworkReadTimeout(seconds);
        return this;
    }

    /**
     * Enable SSL public key pinning. Improves HTTPS security by not allowing MiM attacks
     * based on SSL certificate replacing somewhere between Android device and Countly server.
     * Here you can set one or more PEM-encoded public keys which Countly SDK verifies against
     * public keys provided by Countly's web server for each HTTPS connection. At least one match
     * results in connection being established, no matches result in request not being sent stored for next try.
     * <p>
     * NOTE: Public key pinning is preferred over certificate pinning due to the fact
     * that public keys are usually not changed when certificate expires and you generate new one.
     * This ensures pinning continues to work after certificate prolongation.
     * Certificates ({@link #certificatePins}) on the other hand have specific expiry date.
     * In case you chose this way of pinning, you MUST ensure that ALL installs of your app
     * have both certificates (old & new) until expiry date.
     * <p>
     * NOTE: when {@link #serverURL} doesn't have {@code "https://"} public key pinning doesn't work
     *
     * @param pemEncodedPublicKey PEM-encoded SSL public key string to add
     * @return {@code this} instance for method chaining
     */
    public Config addPublicKeyPin(String pemEncodedPublicKey) {
        super.addPublicKeyPin(pemEncodedPublicKey);
        return this;
    }

    /**
     * Enable SSL certificate pinning. Improves HTTPS security by not allowing MiM attacks
     * based on SSL certificate replacing somewhere between Android device and Countly server.
     * Here you can set one or more PEM-encoded certificates which Countly SDK verifies against
     * certificates provided by Countly's web server for each HTTPS connection. At least one match
     * results in connection being established, no matches result in request not being sent stored for next try.
     * <p>
     * NOTE: Public key pinning ({@link #publicKeyPins}) is preferred over certificate pinning due to the fact
     * that public keys are usually not changed when certificate expires and you generate new one.
     * This ensures pinning continues to work after certificate prolongation.
     * Certificates on the other hand have specific expiry date.
     * In case you chose this way of pinning, you MUST ensure that ALL installs of your app
     * have both certificates (old & new) until expiry date.
     * <p>
     * NOTE: when {@link #serverURL} doesn't have {@code "https://"} certificate pinning doesn't work
     *
     * @param pemEncodedCertificate PEM-encoded SSL certificate string to add
     * @return {@code this} instance for method chaining
     */
    public Config addCertificatePin(String pemEncodedCertificate) {
        super.addCertificatePin(pemEncodedCertificate);
        return this;
    }

    /**
     * Change period when a check for ANR is made. ANR reporting is enabled by default once you enable {@code Feature.CrashReporting}.
     * Default period is 5 seconds. This is *NOT* a timeout for any possible time frame within app running time, it's a checking period.
     * Meaning *SOME* ANRs will be recorded if main thread is blocked for slightly more than {@link #crashReportingANRCheckingPeriod}.
     * *MORE* ANRs will be recorded if main thread is blocked for {@code 1.5 * crashReportingANRCheckingPeriod}. Almost all ANRs
     * is going to be recorded once main thread is blocked for {@link #crashReportingANRCheckingPeriod} or more seconds.
     *
     * To disable ANR reporting, use {@link #disableANRCrashReporting()}.
     *
     * @param periodInSeconds how much time the SDK waits between individual ANR checks
     * @return {@code this} instance for method chaining
     */
    public Config setCrashReportingANRCheckingPeriod(int periodInSeconds) {
        super.setCrashReportingANRCheckingPeriod(periodInSeconds);
        return this;
    }

    /**
     * Disable ANR detection and thus reporting to Countly server.
     *
     * @return {@code this} instance for method chaining
     */
    public Config disableANRCrashReporting() {
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
     * Enable auto views tracking
     *
     * @param autoViewsTracking whether to enable it or disable
     * @return {@code this} instance for method chaining
     * @see #autoViewsTracking
     */
    public Config setAutoViewsTracking(boolean autoViewsTracking) {
        super.setAutoViewsTracking(autoViewsTracking);
        return this;
    }

    /**
     * Enable auto sessions tracking
     *
     * @param autoSessionsTracking whether to enable it or disable
     * @return {@code this} instance for method chaining
     * @see #autoSessionsTracking
     */
    public Config setAutoSessionsTracking(boolean autoSessionsTracking) {
        super.setAutoSessionsTracking(autoSessionsTracking);
        return this;
    }

    /**
     * Wait this much time before ending session in auto session tracking mode
     *
     * @param sessionAutoCloseAfter time in seconds
     * @return {@code this} instance for method chaining
     * @see #autoSessionsTracking
     */
    public Config setSessionAutoCloseAfter(int sessionAutoCloseAfter) {
        super.setSessionAutoCloseAfter(sessionAutoCloseAfter);
        return this;
    }
}