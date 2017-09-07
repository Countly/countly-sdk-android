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
        PerformanceMonitoring(1 << 5);

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
        public final String entity;
        public final String scope;
        public final String id;

        public DID(DeviceIdRealm realm, DeviceIdStrategy strategy, String id) {
            this.realm = realm;
            this.strategy = strategy;
            this.id = id;
            this.entity = null;
            this.scope = null;
        }

        public DID(DeviceIdRealm realm, DeviceIdStrategy strategy, String id, String entity, String scope) {
            this.realm = realm;
            this.strategy = strategy;
            this.id = id;
            this.entity = entity;
            this.scope = scope;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof DID)) { return false; }
            DID did = (DID) obj;
            return did.realm == realm && did.strategy == strategy &&
                    (did.entity == null ? entity == null : did.entity.equals(entity)) &&
                    (did.scope == null ? scope == null : did.scope.equals(scope)) &&
                    (did.id == null ? id == null : did.id.equals(id));
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "DID " + id + " (" + realm + ", " + strategy + ")" + (entity == null ? "" : " " + entity + ", " + scope);
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

                if (strategy == DeviceIdStrategy.INSTANCE_ID && realm != DeviceIdRealm.DEVICE_ID) {
                    stream.writeUTF(id);
                    stream.writeUTF(entity);
                    stream.writeUTF(scope);
                } else {
                    stream.writeUTF(id);
                }
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
                if (strategy == DeviceIdStrategy.INSTANCE_ID && realm != DeviceIdRealm.DEVICE_ID) {
                    Utils.reflectiveSetField(this, "id", stream.readUTF());
                    Utils.reflectiveSetField(this, "entity", stream.readUTF());
                    Utils.reflectiveSetField(this, "scope", stream.readUTF());
                } else {
                    Utils.reflectiveSetField(this, "id", stream.readUTF());
                }

                return true;
            } catch (IOException e) {
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
     *     trying to ignore it when testMode is off</li>
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
     * Getter for {@link #sendUpdateEachEvents}
     * @return {@link #sendUpdateEachEvents} value
     */
    public int getSendUpdateEachEvents() {
        return sendUpdateEachEvents;
    }

}

