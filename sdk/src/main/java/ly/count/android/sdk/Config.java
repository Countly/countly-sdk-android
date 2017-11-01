package ly.count.android.sdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ly.count.android.sdk.internal.Byteable;
import ly.count.android.sdk.internal.Log;
import ly.count.android.sdk.internal.Utils;

/**
 * Countly configuration object.
 */
public class Config {

    /**
     * Enumeration of possible features of Countly SDK
     */
    public enum Feature {
        Crash(1 << 1),
        Push(1 << 2),
        Attribution(1 << 3),
        StarRating(1 << 4),
        AutomaticViewTracking(1 << 5),
        PerformanceMonitoring(1 << 6);

        private final int index;

        Feature(int index){ this.index = index; }

        public int getIndex(){ return index; }
    }

    /**
     * Logging level for {@link Log} module
     */
    public enum LoggingLevel {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3),
        OFF(4);

        private final int level;

        LoggingLevel(int level){ this.level = level; }

        public int getLevel(){ return level; }

        public boolean prints(LoggingLevel l) {
            return level <= l.level;
        }
    }

    /**
     * Holder class for various ids metadata and id itself. Final, unmodifiable.
     */
    public static final class DID implements Byteable {
        public final DeviceIdRealm realm;
        public final DeviceIdStrategy strategy;
        public final String id;

        public DID(DeviceIdRealm realm, DeviceIdStrategy strategy, String id) {
            this.realm = realm;
            this.strategy = strategy;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof DID)) { return false; }
            DID did = (DID) obj;
            return did.realm == realm && did.strategy == strategy &&
                    (did.id == null ? id == null : did.id.equals(id));
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "DID " + id + " (" + realm + ", " + strategy + ")";
        }

        @Override
        public byte[] store() {
            ByteArrayOutputStream bytes = null;
            ObjectOutputStream stream = null;
            try {
                bytes = new ByteArrayOutputStream();
                stream = new ObjectOutputStream(bytes);
                stream.writeInt(realm.getIndex());
                stream.writeInt(strategy.getIndex());
                stream.writeObject(id);
                stream.close();
                return bytes.toByteArray();
            } catch (IOException e) {
                Log.wtf("Cannot serialize config", e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        Log.wtf("Cannot happen", e);
                    }
                }
                if (bytes != null) {
                    try {
                        bytes.close();
                    } catch (IOException e) {
                        Log.wtf("Cannot happen", e);
                    }
                }
            }
            return null;
        }

        @Override
        public boolean restore(byte[] data) {
            ByteArrayInputStream bytes = null;
            ObjectInputStream stream = null;

            try {
                bytes = new ByteArrayInputStream(data);
                stream = new ObjectInputStream(bytes);

                Utils.reflectiveSetField(this, "realm", DeviceIdRealm.fromIndex(stream.readInt()));
                Utils.reflectiveSetField(this, "strategy", DeviceIdStrategy.fromIndex(stream.readInt()));
                Utils.reflectiveSetField(this, "id", stream.readObject());

                return true;
            } catch (IOException | ClassNotFoundException e) {
                Log.wtf("Cannot deserialize config", e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        Log.wtf("Cannot happen", e);
                    }
                }
                if (bytes != null) {
                    try {
                        bytes.close();
                    } catch (IOException e) {
                        Log.wtf("Cannot happen", e);
                    }
                }
            }

            return false;
        }
    }

    /**
     * Strategy for device id generation
     */
    public enum DeviceIdStrategy {
        OPEN_UDID(0),
        ADVERTISING_ID(1),
        INSTANCE_ID(2),
        CUSTOM_ID(10);

        private final int index;

        DeviceIdStrategy(int level){
            this.index = level;
        }

        public int getIndex(){ return index; }

        public static DeviceIdStrategy fromIndex(int index){
            if (index == OPEN_UDID.index) { return OPEN_UDID; }
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
     * URL of Countly server
     */
    protected final URL serverURL;

    /**
     * Application key of Countly server
     */
    protected final String serverAppKey;

    /**
     * Set of Countly SDK features enabled
     */
    protected final Set<Feature> features;

    /**
     * Device id generation strategy
     */
    protected DeviceIdStrategy deviceIdStrategy = DeviceIdStrategy.OPEN_UDID;

    /**
     * Allow fallback from specified device id strategy to any other available strategy
     */
    protected boolean deviceIdFallbackAllowed = true;

    /**
     * Developer specified device id
     */
    protected String customDeviceId;

    /**
     * Tag used for logging
     */
    protected String loggingTag = "Countly";

    /**
     * Logging level
     */
    protected LoggingLevel loggingLevel = LoggingLevel.OFF;

    /**
     * Countly SDK name to be sent in HTTP requests
     */
    protected String sdkName = "java-native-android";

    /**
     * Countly SDK version to be sent in HTTP requests
     */
    protected String sdkVersion = "17.04";

    /**
     * Force usage of POST method for all requests
     */
    protected boolean usePOST = false;

    /**
     * Salt string for parameter tampering protection
     */
    protected String salt = null;

    /**
     * Connection timeout in seconds for HTTP requests SDK sends to Countly server. Defaults to 30.
     */
    protected int networkConnectionTimeout = 30;

    /**
     * Read timeout in seconds for HTTP requests SDK sends to Countly server. Defaults to 30.
     */
    protected int networkReadTimeout = 30;

    /**
     * Enable SSL public key pinning. Improves HTTPS security by not allowing MiM attacks
     * based on SSL certificate replacing somewhere between Android device and Countly server.
     * Here you can set one or more PEM-encoded public keys which Countly SDK verifies against
     * public keys provided by Countly's web server for each HTTPS connection. At least one match
     * results in connection being established, no matches result in request not being sent stored for next try.
     *
     * NOTE: Public key pinning is preferred over certificate pinning due to the fact
     * that public keys are usually not changed when certificate expires and you generate new one.
     * This ensures pinning continues to work after certificate prolongation.
     * Certificates ({@link #certificatePins}) on the other hand have specific expiry date.
     * In case you chose this way of pinning, you MUST ensure that ALL installs of your app
     * have both certificates (old & new) until expiry date.
     */
    protected Set<String> publicKeyPins = null;

    /**
     * Enable SSL certificate pinning. Improves HTTPS security by not allowing MiM attacks
     * based on SSL certificate replacing somewhere between Android device and Countly server.
     * Here you can set one or more PEM-encoded certificates which Countly SDK verifies against
     * certificates provided by Countly's web server for each HTTPS connection. At least one match
     * results in connection being established, no matches result in request not being sent stored for next try.
     *
     * NOTE: Public key pinning ({@link #publicKeyPins}) is preferred over certificate pinning due to the fact
     * that public keys are usually not changed when certificate expires and you generate new one.
     * This ensures pinning continues to work after certificate prolongation.
     * Certificates on the other hand have specific expiry date.
     * In case you chose this way of pinning, you MUST ensure that ALL installs of your app
     * have both certificates (old & new) until expiry date.
     */
    protected Set<String> certificatePins = null;

    /**
     * Maximum amount of time in seconds between two update requests to the server
     * reporting session duration and other parameters if any added between update requests.
     *
     * Update request is also sent when number of unsent events reached {@link #sendUpdateEachEvents}.
     *
     * Set to 0 to disable update requests based on time.
     */
    protected int sendUpdateEachSeconds = 30;

    /**
     * Maximum number of events to hold until forcing update request to be sent to the server
     *
     * Update request is also sent when last update request was sent more than {@link #sendUpdateEachSeconds} seconds ago.
     *
     * Set to 0 to disable update requests based on amount of events stored.
     */
    protected int sendUpdateEachEvents = 10;

    /**
     * Minimal amount of time between sessions in seconds.
     * For now used only when recovering from a crash as a session extension period.
     */
    protected int sessionCooldownPeriod = 30;

    /**
     * Take control of the way Countly detects sessions and turn off default
     * {@link android.app.Activity}-based mechanism (first activity start starts session,
     * last activity stop stops it).
     */
    protected boolean programmaticSessionsControl = false;

    /**
     * Enable test mode:
     * <ul>
     *     <li>Raise exceptions when SDK is in inconsistent state as opposed to silently
     *     trying to ignore it when testMode is off</li>
     *     <li>Put Firebase token under {@code test} devices if {@link Feature#Push} is enabled.</li>
     * </ul>
     */
    protected boolean testMode = false;

    /**
     * When not {@code null}, more than {@code 0} and {@link Feature#Crash} is enabled,
     * Countly watches main thread for unresponsiveness.
     * When main thread doesn't respond for time more than this property in seconds,
     * SDK reports ANR crash back to Countly server.
     */
    protected int crashReportingANRTimeout = 5;

    /**
     * Activity class to be launched on {@link android.app.Notification} tap, defaults
     * to main app activity.
     */
    protected String pushActivityClass = null;

    /**
     * The only Config constructor.
     *
     * @param serverURL valid {@link URL} of Countly server
     * @param serverAppKey App Key from Management -> Applications section of your Countly Dashboard
     * @throws MalformedURLException in case {@code serverURL} is not a valid URL
     */
    public Config(String serverURL, String serverAppKey) throws MalformedURLException {
        //the last '/' should be deleted
        if(serverURL != null && serverURL.length() > 0 && serverURL.charAt(serverURL.length() - 1) == '/') {
            serverURL = serverURL.substring(0, serverURL.length() - 1);
        }

        this.serverURL = new URL(serverURL);
        this.serverAppKey = serverAppKey;
        this.features = new HashSet<>();
    }

    /**
     * Enable one feature of Countly SDK instead of {@link #setFeatures(Feature...)}.
     *
     * @param feature feature to enable
     * @return {@code this} instance for method chaining
     */
    public Config addFeature(Feature feature) {
        this.features.add(feature);
        return this;
    }

    /**
     * Set enabled features all at once instead of {@link #addFeature(Feature)}.
     *
     * @param features variable args of features to enable
     * @return {@code this} instance for method chaining
     */
    public Config setFeatures(Feature... features) {
        this.features.clear();

        if (features != null && features.length > 0) {
            this.features.addAll(Arrays.asList(features));
        }
        return this;
    }

    /**
     * Set device id generation strategy:
     *
     * - {@link DeviceIdStrategy#INSTANCE_ID} to use InstanceID if available (requires Play Services).
     * Falls back to OpenUDID if no Play Services available, default.
     *
     * - {@link DeviceIdStrategy#OPEN_UDID} to use OpenUDID derivative - unique, semi-persistent
     * (stored in {@link android.content.SharedPreferences}).
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
        }
        if (strategy == DeviceIdStrategy.CUSTOM_ID) {
            return setCustomDeviceId(customDeviceId);
        }
        this.deviceIdStrategy = strategy;
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
        }
        this.customDeviceId = customDeviceId;
        this.deviceIdStrategy = DeviceIdStrategy.CUSTOM_ID;
        return this;
    }

    /**
     * Whether to allow fallback from unavailable device id strategy to Countly OpenUDID derivative.
     *
     * @param deviceIdFallbackAllowed true if fallback is allowed
     */
    public void setDeviceIdFallbackAllowed(boolean deviceIdFallbackAllowed) {
        this.deviceIdFallbackAllowed = deviceIdFallbackAllowed;
    }

    /**
     * Force usage of POST method for all requests
     *
     * @return {@code this} instance for method chaining
     */
    public Config enableUsePOST() {
        this.usePOST = true;
        return this;
    }

    /**
     * Force usage of POST method for all requests.
     *
     * @param usePOST whether to force using POST method for all requests or not
     * @return {@code this} instance for method chaining
     */
    public Config setUsePOST(boolean usePOST) {
        this.usePOST = usePOST;
        return this;
    }

    /**
     * Enable parameter tampering protection
     *
     * @param salt String to add to each request bebfore calculating checksum
     * @return {@code this} instance for method chaining
     */
    public Config enableParameterTamperingProtection(String salt) {
        this.salt = salt;
        return this;
    }

    /**
     * Tag used for logging
     *
     * @param loggingTag tag string to use
     * @return {@code this} instance for method chaining
     */
    public Config setLoggingTag(String loggingTag) {
        if (loggingTag == null || loggingTag.equals("")) {
            Log.wtf("Logging tag cannot be empty");
        }
        this.loggingTag = loggingTag;
        return this;
    }

    /**
     * Logging level for Countly SDK
     *
     * @param loggingLevel log level to use
     * @return {@code this} instance for method chaining
     */
    public Config setLoggingLevel(LoggingLevel loggingLevel) {
        //todo double check, logging level can be set null, is that ok?
        this.loggingLevel = loggingLevel;
        return this;
    }

    /**
     * Enable test mode:
     * <ul>
     *     <li>Raise exceptions when SDK is in inconsistent state as opposed to silently
     *     trying to ignore them when testMode is off</li>
     *     <li>Put Firebase token under {@code test} devices if {@link Feature#Push} is enabled.</li>
     * </ul>
     * Note: this method automatically sets {@link #loggingLevel} to {@link LoggingLevel#INFO} in
     * case it was {@link LoggingLevel#OFF} (default).
     *
     * @return {@code this} instance for method chaining
     */
    public Config enableTestMode() {
        this.testMode = true;
        this.loggingLevel = this.loggingLevel == LoggingLevel.OFF ? LoggingLevel.INFO : this.loggingLevel;
        return this;
    }

    /**
     * Set maximum amount of time in seconds between two update requests to the server
     * reporting session duration and other parameters if any added between update requests.
     *
     * Update request is also sent when number of unsent events reached {@link #setSendUpdateEachEvents(int)}.
     *
     * @param sendUpdateEachSeconds max time interval between two update requests, set to 0 to disable update requests based on time.
     * @return {@code this} instance for method chaining
     */
    public Config setSendUpdateEachSeconds(int sendUpdateEachSeconds) {
        this.sendUpdateEachSeconds = sendUpdateEachSeconds;
        return this;
    }

    /**
     * Sets maximum number of events to hold until forcing update request to be sent to the server
     *
     * Update request is also sent when last update request was sent more than {@link #setSendUpdateEachSeconds(int)} seconds ago.
     *
     * @param sendUpdateEachEvents max number of events between two update requests, set to 0 to disable update requests based on events.
     * @return {@code this} instance for method chaining
     */
    public Config setSendUpdateEachEvents(int sendUpdateEachEvents) {
        this.sendUpdateEachEvents = sendUpdateEachEvents;
        return this;
    }

    /**
     * Disable update requests completely. Only begin & end requests will be sent + some special
     * cases if applicable like User Profile change or Push token updated.
     *
     * @see #setSendUpdateEachSeconds(int)
     * @see #setSendUpdateEachEvents(int)
     * @return {@code this} instance for method chaining
     */
    public Config disableUpdateRequests() {
        this.sendUpdateEachSeconds = this.sendUpdateEachEvents = 0;
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
        this.sessionCooldownPeriod = sessionCooldownPeriod;
        return this;
    }

    /**
     * Take control of the way Countly detects sessions and turn off default
     * {@link android.app.Activity}-based mechanism (first activity start starts session,
     * last activity stop stops it).
     *
     * @return {@code this} instance for method chaining
     */
    public Config enableProgrammaticSessionsControl() {
        this.programmaticSessionsControl = true;
        return this;
    }

    /**
     * Take control of the way Countly detects sessions and turn off default
     * {@link android.app.Activity}-based mechanism (first activity start starts session,
     * last activity stop stops it).
     *
     * @param programmaticSessionsControl whether to turn off Countly way of session handling or not
     * @return {@code this} instance for method chaining
     */
    public Config setProgrammaticSessionsControl(boolean programmaticSessionsControl) {
        this.programmaticSessionsControl = programmaticSessionsControl;
        return this;
    }

    /**
     * Change name of SDK used in HTTP requests
     *
     * @param sdkName new name of SDK
     * @return {@code this} instance for method chaining
     */
    public Config setSdkName(String sdkName) {
        this.sdkName = sdkName;
        return this;
    }

    /**
     * Change version of SDK used in HTTP requests
     *
     * @param sdkVersion new version of SDK
     * @return {@code this} instance for method chaining
     */
    public Config setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
        return this;
    }

    /**
     * Set connection timeout in seconds for HTTP requests SDK sends to Countly server. Defaults to 30.
     *
     * @param seconds network timeout in seconds
     * @return {@code this} instance for method chaining
     */
    public Config setNetworkConnectTimeout(int seconds) {
        if (seconds <= 0 || seconds > 300) {
            Log.wtf("Connection timeout must be between 0 and 300");
        } else {
            networkConnectionTimeout = seconds;
        }
        return this;
    }

    /**
     * Set read timeout in seconds for HTTP requests SDK sends to Countly server. Defaults to 30.
     *
     * @param seconds read timeout in seconds
     * @return {@code this} instance for method chaining
     */
    public Config setNetworkReadTimeout(int seconds) {
        if (seconds <= 0 || seconds > 300) {
            Log.wtf("Read timeout must be between 0 and 300");
        } else {
            networkReadTimeout = seconds;
        }
        return this;
    }

    /**
     * Enable SSL public key pinning. Improves HTTPS security by not allowing MiM attacks
     * based on SSL certificate replacing somewhere between Android device and Countly server.
     * Here you can set one or more PEM-encoded public keys which Countly SDK verifies against
     * public keys provided by Countly's web server for each HTTPS connection. At least one match
     * results in connection being established, no matches result in request not being sent stored for next try.
     *
     * NOTE: Public key pinning is preferred over certificate pinning due to the fact
     * that public keys are usually not changed when certificate expires and you generate new one.
     * This ensures pinning continues to work after certificate prolongation.
     * Certificates ({@link #certificatePins}) on the other hand have specific expiry date.
     * In case you chose this way of pinning, you MUST ensure that ALL installs of your app
     * have both certificates (old & new) until expiry date.
     *
     * NOTE: when {@link #serverURL} doesn't have {@code "https://"} public key pinning doesn't work
     *
     * @param pemEncodedPublicKey PEM-encoded SSL public key string to add
     * @return {@code this} instance for method chaining
     */
    public Config addPublicKeyPin(String pemEncodedPublicKey) {
        if (publicKeyPins == null) {
            publicKeyPins = new HashSet<>();
        }

        publicKeyPins.add(pemEncodedPublicKey);
        return this;
    }

    /**
     * Enable SSL certificate pinning. Improves HTTPS security by not allowing MiM attacks
     * based on SSL certificate replacing somewhere between Android device and Countly server.
     * Here you can set one or more PEM-encoded certificates which Countly SDK verifies against
     * certificates provided by Countly's web server for each HTTPS connection. At least one match
     * results in connection being established, no matches result in request not being sent stored for next try.
     *
     * NOTE: Public key pinning ({@link #publicKeyPins}) is preferred over certificate pinning due to the fact
     * that public keys are usually not changed when certificate expires and you generate new one.
     * This ensures pinning continues to work after certificate prolongation.
     * Certificates on the other hand have specific expiry date.
     * In case you chose this way of pinning, you MUST ensure that ALL installs of your app
     * have both certificates (old & new) until expiry date.
     *
     * NOTE: when {@link #serverURL} doesn't have {@code "https://"} certificate pinning doesn't work
     *
     * @param pemEncodedCertificate PEM-encoded SSL certificate string to add
     * @return {@code this} instance for method chaining
     */
    public Config addCertificatePin(String pemEncodedCertificate) {
        if (certificatePins == null) {
            certificatePins = new HashSet<>();
        }

        certificatePins.add(pemEncodedCertificate);
        return this;
    }

    /**
     * Change timeout when ANR is detected. ANR reporting is enabled by default once you enable {@link Feature#Crash}.
     * Default timeout is 5 seconds.
     * To disable ANR reporting, use {@link #disableANRCrashReporting()}.
     *
     * @param timeoutInSeconds how much time main thread must be blocked before ANR is detected
     * @return {@code this} instance for method chaining
     */
    public Config setCrashReportingANRTimeout(int timeoutInSeconds) {
        if (timeoutInSeconds < 0) {
            Log.wtf("ANR timeout less than zero doesn't make sense");
        }
        this.crashReportingANRTimeout = timeoutInSeconds;
        return this;
    }

    /**
     * Disable ANR detection and thus reporting to Countly server.
     *
     * @return {@code this} instance for method chaining
     */
    public Config disableANRCrashReporting() {
        this.crashReportingANRTimeout = 0;
        return this;
    }

    /**
     * Set push activity class which is to be launched when user taps on a {@link android.app.Notification}.
     * Defaults automatically to main activity class.
     *
     * @param pushActivityClass activity class
     * @return {@code this} instance for method chaining
     */
    public Config setPushActivityClass(Class pushActivityClass) {
        this.pushActivityClass = pushActivityClass.getName();
        return this;
    }

    /**
     * Getter for {@link #serverURL}
     * @return {@link #serverURL} value
     */
    public URL getServerURL() {
        return serverURL;
    }

    /**
     * Getter for {@link #serverAppKey}
     * @return {@link #serverAppKey} value
     */
    public String getServerAppKey() {
        return serverAppKey;
    }

    /**
     * Getter for {@link #features}
     * @return {@link #features} value
     */
    public Set<Feature> getFeatures() {
        return features;
    }

    /**
     * Getter for {@link #deviceIdStrategy}
     * @return {@link #deviceIdStrategy} value
     */
    public DeviceIdStrategy getDeviceIdStrategy() {
        return deviceIdStrategy;
    }

    /**
     * Whether to allow fallback from unavailable device id strategy to any other available.
     *
     * @return true if fallback is allowed
     */
    public boolean isDeviceIdFallbackAllowed() {
        return deviceIdFallbackAllowed;
    }

    /**
     * Getter for {@link #customDeviceId}
     * @return {@link #customDeviceId} value
     */
    public String getCustomDeviceId() {
        return customDeviceId;
    }

    /**
     * Getter for {@link #usePOST}
     * @return {@link #usePOST} value
     */
    public boolean isUsePOST() {
        return usePOST;
    }

    /**
     * Getter for {@link #salt}
     * @return {@link #salt} value
     */
    public String getParameterTamperingProtectionSalt() {
        return salt;
    }

    /**
     * Getter for {@link #sdkName}
     * @return {@link #sdkName} value
     */
    public String getSdkName() {
        return sdkName;
    }

    /**
     * Getter for {@link #sdkVersion}
     * @return {@link #sdkVersion} value
     */
    public String getSdkVersion() {
        return sdkVersion;
    }

    /**
     * Getter for {@link #loggingTag}
     * @return {@link #loggingTag} value
     */
    public String getLoggingTag() {
        return loggingTag;
    }

    /**
     * Getter for {@link #loggingLevel}
     * @return {@link #loggingLevel} value
     */
    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Getter for {@link #programmaticSessionsControl}
     * @return {@link #programmaticSessionsControl} value
     */
    public boolean isProgrammaticSessionsControl() {
        return programmaticSessionsControl;
    }

    /**
     * Getter for {@link #testMode}
     * @return {@link #testMode} value
     */
    public boolean isTestModeEnabled() {
        return testMode;
    }

    /**
     * Getter for {@link #sendUpdateEachSeconds}
     * @return {@link #sendUpdateEachSeconds} value
     */
    public int getSendUpdateEachSeconds() {
        return sendUpdateEachSeconds;
    }

    /**
     * Getter for {@link #sessionCooldownPeriod}
     * @return {@link #sessionCooldownPeriod} value
     */
    public int getSessionCooldownPeriod() {
        return sessionCooldownPeriod;
    }

    /**
     * Getter for {@link #sendUpdateEachEvents}
     * @return {@link #sendUpdateEachEvents} value
     */
    public int getSendUpdateEachEvents() {
        return sendUpdateEachEvents;
    }

    /**
     * Getter for {@link #networkConnectionTimeout}
     * @return {@link #networkConnectionTimeout} value
     */
    public int getNetworkConnectionTimeout() {
        return networkConnectionTimeout;
    }

    /**
     * Getter for {@link #networkReadTimeout}
     * @return {@link #networkReadTimeout} value
     */
    public int getNetworkReadTimeout() {
        return networkReadTimeout;
    }

    /**
     * Getter for {@link #publicKeyPins}
     * @return {@link #publicKeyPins} value
     */
    public Set<String> getPublicKeyPins() { return publicKeyPins; }

    /**
     * Getter for {@link #certificatePins}
     * @return {@link #certificatePins} value
     */
    public Set<String> getCertificatePins() { return certificatePins; }

    /**
     * Getter for {@link #crashReportingANRTimeout}
     * @return {@link #crashReportingANRTimeout} value
     */
    public int getCrashReportingANRTimeout() {
        return crashReportingANRTimeout;
    }

    /**
     * Getter for {@link #pushActivityClass}
     * @return {@link #pushActivityClass} value
     */
    public String getPushActivityClass() {
        return pushActivityClass;
    }
}

