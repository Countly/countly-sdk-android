package ly.count.android.sdk;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ly.count.android.sdk.internal.Log;

/**
 * Countly configuration object.
 */
public class Config {

    /**
     * Enumeration of possible features of Countly SDK
     */
    public enum Feature {
        Crash,
        Push,
        PerformanceMonitoring
    }

    /**
     * Logging level for {@link Log} module
     */
    public enum LoggingLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        OFF
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
        if (features != null && features.length > 0) {
            this.features.addAll(Arrays.asList(features));
        }
        return this;
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
            throw new IllegalStateException("Logging tag cannot be empty");
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

}

