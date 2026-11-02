package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class ModuleConfiguration extends ModuleBase implements ConfigurationProvider {
    ImmediateRequestGenerator immediateRequestGenerator;
    CountlyTimer serverConfigUpdateTimer;

    JSONObject latestRetrievedConfigurationFull = null;
    JSONObject latestRetrievedConfiguration = null;

    //config keys
    final static String keyRTracking = "tracking";
    final static String keyRNetworking = "networking";

    //request keys
    final static String keyRTimestamp = "t";
    final static String keyRVersion = "v";
    final static String keyRConfig = "c";
    final static String keyRReqQueueSize = "rqs";
    final static String keyREventQueueSize = "eqs";
    final static String keyRLogging = "log";
    final static String keyRSessionUpdateInterval = "sui";
    final static String keyRSessionTracking = "st";
    final static String keyRViewTracking = "vt";
    final static String keyRLocationTracking = "lt";
    final static String keyRRefreshContentZone = "rcz";
    final static String keyRLimitKeyLength = "lkl";
    final static String keyRLimitValueSize = "lvs";
    final static String keyRLimitSegValues = "lsv";
    final static String keyRLimitBreadcrumb = "lbc";
    final static String keyRLimitTraceLine = "ltlpt";
    final static String keyRLimitTraceLength = "ltl";
    final static String keyRCustomEventTracking = "cet";
    final static String keyREnterContentZone = "ecz";
    final static String keyRContentZoneInterval = "czi";
    final static String keyRConsentRequired = "cr";
    final static String keyRDropOldRequestTime = "dort";
    final static String keyRCrashReporting = "crt";
    final static String keyRAutomaticSessionTracking = "ast";
    final static String keyRAutomaticViewTracking = "avt";
    final static String keyRAutomaticCrashReporting = "acr";
    final static String keyRServerConfigUpdateInterval = "scui";
    final static String keyRBackoffMechanism = "bom";
    final static String keyRBOMAcceptedTimeout = "bom_at";
    final static String keyRBOMRQPercentage = "bom_rqp";
    final static String keyRBOMRequestAge = "bom_ra";
    final static String keyRBOMDuration = "bom_d";
    final static String keyRUserPropertyCacheLimit = "upcl";
    final static String keyREventBlacklist = "eb";
    final static String keyRUserPropertyBlacklist = "upb";
    final static String keyRSegmentationBlacklist = "sb";
    final static String keyREventSegmentationBlacklist = "esb"; // json
    final static String keyREventWhitelist = "ew";
    final static String keyRUserPropertyWhitelist = "upw";
    final static String keyRSegmentationWhitelist = "sw";
    final static String keyREventSegmentationWhitelist = "esw"; // json
    final static String keyRJourneyTriggerEvents = "jte";
    final static String keyRJourneyTriggerViews = "jtv";
    final static String keyRLogGathering = "lg"; //top level key, a sibling of 'c', not a member of it
    final static String keyRConnectionTest = "ct"; //top level key, a sibling of 'c', read from a live response and never stored

    //top level keys of the response that this SDK supports. Anything else that arrives next to them is ignored
    final static Set<String> supportedTopLevelKeys = new HashSet<>(Arrays.asList(keyRVersion, keyRTimestamp, keyRConfig, keyRLogGathering));

    //keys inside the 'lg' log gathering directive
    final static String keyLGEnabled = "e";
    final static String keyLGId = "i";
    final static String keyLGLevels = "l";
    final static String keyLGBatchSize = "b";

    //log gathering bounds and defaults
    final static String logGatheringAllLevels = "ewidv";
    final static int logGatheringDefaultBatchSize = 100;
    final static int logGatheringMinBatchSize = 10;
    //buffer ceiling, and so the largest batch the server may ask for: a bigger batch would never fill
    final static int logGatheringMaxBufferedLines = 500;

    // FLAGS
    boolean currentVTracking = true;
    boolean currentVNetworking = true;
    boolean currentVSessionTracking = true;
    boolean currentVViewTracking = true;
    boolean currentVCustomEventTracking = true;
    boolean currentVContentZone = false;
    boolean currentVCrashReporting = true;
    // automatic tracking flags are seeded from the local config in the constructor and then overridden by the SBS layers; they are the single source of truth for whether automatic session/view/crash tracking is active
    boolean currentVAutomaticSessionTracking = true;
    boolean currentVAutomaticViewTracking = false;
    boolean currentVAutomaticCrashReporting = false;
    boolean currentVLocationTracking = true;
    boolean currentVRefreshContentZone = true;
    boolean currentVBackoffMechanism = true;

    //defaults of the settings that no CountlyConfig option sets, a setting the server does not send goes back to these
    final static int bomAcceptedTimeoutSecondsDefault = 10;
    final static double bomRQPercentageDefault = 0.5;
    final static int bomRequestAgeDefault = 24; // in hours
    final static int bomDurationDefault = 60; // in seconds
    final static int userPropertyCacheLimitDefault = 100;
    final static int serverConfigUpdateIntervalDefault = 4; // in hours

    // PROPERTIES
    int currentVBOMAcceptedTimeoutSeconds = bomAcceptedTimeoutSecondsDefault;
    double currentVBOMRQPercentage = bomRQPercentageDefault;
    int currentVBOMRequestAge = bomRequestAgeDefault; // in hours
    int currentVBOMDuration = bomDurationDefault; // in seconds
    int currentVUserPropertyCacheLimit = userPropertyCacheLimitDefault;

    // FILTERS
    FilterList<Set<String>> currentVEventFilterList = new FilterList<>(new HashSet<>(), false);
    FilterList<Set<String>> currentVUserPropertyFilterList = new FilterList<>(new HashSet<>(), false);
    FilterList<Set<String>> currentVSegmentationFilterList = new FilterList<>(new HashSet<>(), false);
    FilterList<Map<String, Set<String>>> currentVEventSegmentationFilterList = new FilterList<>(new ConcurrentHashMap<>(), false);
    Set<String> currentVJourneyTriggerEvents = new HashSet<>();
    Set<String> currentVJourneyTriggerViews = new HashSet<>();

    // Settings the SBS layers resolve that used to be written back onto the CountlyConfig. Per instance, so
    // a config shared between instances can not carry one instance's resolved settings into another.
    // Consumers read these (Countly#onSdkConfigurationChanged, ModuleConsent, ModuleContent, the request
    // drop-age provider) instead of reading the config.
    int currentVMaxRequestQueueSize;
    Integer currentVEventQueueSizeThreshold;
    boolean currentVLoggingEnabled;
    Integer currentVSessionUpdateTimerDelay;
    int currentVDropAgeHours;
    boolean currentVRequiresConsent;
    int currentVZoneTimerInterval;
    /**
     * Tri state of the per user log gathering directive, the top level 'lg' key.
     * UNDECIDED - no directive has been seen yet, so it is not known whether this device should gather logs.
     * Only a stored (or developer provided) configuration without an 'lg' key leaves the SDK here.
     * GATHERING - a directive with 'e' true and a usable gather id was received.
     * NOT_GATHERING - a server response decided against gathering: it carried no 'lg', an unusable one, or one with
     * 'e' false. Disabled configuration requests land here too, because then no response can ever arrive.
     */
    enum LogGatheringState {
        UNDECIDED,
        GATHERING,
        NOT_GATHERING
    }

    // LOG GATHERING ('lg', parsed from the top level object, never from the inner 'c')
    @NonNull LogGatheringState currentVLogGatheringState = LogGatheringState.UNDECIDED;
    @Nullable String currentVLogGatheringId = null;
    @NonNull String currentVLogGatheringLevels = logGatheringAllLevels;
    int currentVLogGatheringBatchSize = logGatheringDefaultBatchSize;

    // SERVER CONFIGURATION PARAMS
    Integer serverConfigUpdateInterval; // in hours
    int currentServerConfigUpdateInterval = serverConfigUpdateIntervalDefault;
    long lastServerConfigFetchTimestamp = -1;
    private final boolean serverConfigRequestsDisabled;

    ModuleConfiguration(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleConfiguration] Initialising");
        //Publish ourselves on the instance before resolving anything. updateConfigVariables below can call
        //Countly#onSdkConfigurationChanged, which reads this instance's resolved settings through
        //_cly.moduleConfiguration - and init only assigns that field after this constructor returns.
        cly.moduleConfiguration = this;
        config.configProvider = this;
        configProvider = this;

        immediateRequestGenerator = config.immediateRequestGenerator;
        serverConfigUpdateTimer = new CountlyTimer();
        serverConfigUpdateInterval = currentServerConfigUpdateInterval;
        serverConfigRequestsDisabled = config.sdkBehaviorSettingsRequestsDisabled;

        config.countlyStore.setConfigurationProvider(this);

        //Seed the settings the SBS layers resolve from the developer's config. These live here, per instance,
        //rather than being written back onto the CountlyConfig: the config object may be shared by several
        //instances, and writing our resolved values onto it would both hand them to the other instance and
        //poison the "provided" layer of its next resolve - which for shouldRequireConsent means silently
        //switching consent gating off for an instance whose developer required it.
        currentVMaxRequestQueueSize = config.maxRequestQueueSize;
        currentVEventQueueSizeThreshold = config.eventQueueSizeThreshold;
        currentVLoggingEnabled = config.loggingEnabled;
        currentVSessionUpdateTimerDelay = config.sessionUpdateTimerDelay;
        currentVDropAgeHours = config.dropAgeHours;
        currentVRequiresConsent = config.shouldRequireConsent;
        currentVZoneTimerInterval = config.content.zoneTimerInterval;
        currentVBackoffMechanism = config.backOffMechanismEnabled;

        //seed the automatic tracking flags from the local config: it is the lowest-precedence layer.
        //the SBS layers (provided -> stored -> server) override these in updateConfigVariables, giving the precedence
        //server SBS > stored SBS > provided SBS > developer config
        currentVAutomaticSessionTracking = !config.manualSessionControlEnabled;
        currentVAutomaticViewTracking = config.enableAutomaticViewTracking;
        currentVAutomaticCrashReporting = config.crashes.enableUnhandledCrashReporting;

        //load the previously saved configuration
        loadConfigFromStorage(config.sdkBehaviorSettings);

        //update the config variables according to the new state. No server response yet, so log gathering is only
        //decided here when requests are disabled and no response can ever come
        updateConfigVariables(config, null, true);
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        //once the SDK has loaded, init fetching the server config
        L.d("[ModuleConfiguration] initFinished");
        if (!serverConfigRequestsDisabled) {
            fetchConfigFromServer(config);
            startServerConfigUpdateTimer();
        }
    }

    @Override
    void halt() {
        serverConfigUpdateTimer.stopTimer(L);
    }

    @Override
    void onSdkConfigurationChanged(@NonNull CountlyConfig config) {
        if (currentServerConfigUpdateInterval != serverConfigUpdateInterval) {
            currentServerConfigUpdateInterval = serverConfigUpdateInterval;
            startServerConfigUpdateTimer();
        }
    }

    private void startServerConfigUpdateTimer() {
        //the period is in seconds and the initial delay in milliseconds, see CountlyTimer#startTimer
        final long intervalSeconds = (long) currentServerConfigUpdateInterval * 60 * 60;
        serverConfigUpdateTimer.startTimer(intervalSeconds, intervalSeconds * 1000, new Runnable() {
            @Override
            public void run() {
                fetchConfigFromServer(_cly.config_);
            }
        }, L);
    }

    /**
     * The value a setting goes back to when the configuration does not carry it
     *
     * @param developerValue the value set in CountlyConfig, null when the developer set none
     * @param minimum the smallest value the SDK uses, the same minimum it applies to the resolved value
     * @param defaultValue the SDK default
     * @return the developer's value raised to the minimum, or the SDK default when the developer set none
     */
    private static int developerValueOr(@Nullable Integer developerValue, int minimum, int defaultValue) {
        return developerValue == null ? defaultValue : Math.max(developerValue, minimum);
    }

    /**
     * Reads from storage to local json objects
     */
    void loadConfigFromStorage(@Nullable String sdkBehaviorSettings) {
        String sConfig = storageProvider.getServerConfig();

        if (Utils.isNullOrEmpty(sConfig) && !Utils.isNullOrEmpty(sdkBehaviorSettings)) {
            sConfig = sdkBehaviorSettings;
        }

        L.v("[ModuleConfiguration] loadConfigFromStorage, [" + sConfig + "]");

        if (sConfig == null || sConfig.isEmpty()) {
            L.d("[ModuleConfiguration] loadStoredConfig, no configs persistently stored");
            return;
        }

        try {
            latestRetrievedConfigurationFull = new JSONObject(sConfig);
            latestRetrievedConfiguration = latestRetrievedConfigurationFull.getJSONObject(keyRConfig);
            L.d("[ModuleConfiguration] loadStoredConfig, stored config loaded [" + sConfig + "]");
            saveAndStoreDownloadedConfig(latestRetrievedConfigurationFull);
        } catch (JSONException e) {
            L.w("[ModuleConfiguration] loadStoredConfig, failed to parse, " + e);

            latestRetrievedConfigurationFull = null;
            latestRetrievedConfiguration = null;
        }
    }

    /**
     * Resolves one setting: the configuration's value when it carries a usable one, otherwise fallbackValue, which is
     * the developer's value or the SDK default. So a setting the server stops sending, or sends unusable, does not
     * keep its last server value. A changed value is added to sb.
     *
     * @param key setting key
     * @param sb collects the changed settings
     * @param currentValue the value in use, null when it was not resolved yet
     * @param fallbackValue the value set in CountlyConfig, or the SDK default when the developer set none
     * @param clazz type of the value
     * @param validator decides whether the configuration's value is usable, null accepts any value of the type
     * @return the value to use
     */
    private <T> T extractValue(String key, StringBuilder sb, @Nullable T currentValue, @NonNull T fallbackValue, Class<T> clazz, @Nullable ConfigurationValueValidator<T> validator) {
        T resolvedValue = fallbackValue;
        boolean fromConfiguration = false;

        if (latestRetrievedConfiguration.has(key)) {
            try {
                T extractedValue = clazz.cast(latestRetrievedConfiguration.get(key));
                if (validator != null && !validator.validate(extractedValue)) {
                    L.w("[ModuleConfiguration] updateConfigs, value for '" + key + "' is not valid according to the validator, value: [" + extractedValue + "]");
                } else {
                    resolvedValue = extractedValue;
                    fromConfiguration = true;
                }
            } catch (Exception e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load '" + key + "', " + e.getMessage());
            }
        }

        //an unset value that resolves to the developer's value or the default is what the SDK already uses, not a change
        if (currentValue == null && !fromConfiguration) {
            return resolvedValue;
        }

        if (!resolvedValue.equals(currentValue)) {
            sb.append(key).append(":[").append(resolvedValue).append("], ");
        }

        return resolvedValue;
    }

    private Boolean extractValue(String key, StringBuilder sb, Boolean currentValue, Boolean fallbackValue) {
        return extractValue(key, sb, currentValue, fallbackValue, Boolean.class, null);
    }

    /**
     * Update the config variables according to the current config obj state
     *
     * @param clyConfig config of the SDK
     * @param serverResponse the freshly downloaded response, or null when the values were restored from storage or
     * provided at init. Only a server response decides the log gathering directive, see {@link #updateLogGatheringDirective(JSONObject)}.
     * @param decidesLogGathering false for a response requested for another device ID, which decides nothing about gathering
     */
    private void updateConfigVariables(@NonNull final CountlyConfig clyConfig, @Nullable final JSONObject serverResponse, final boolean decidesLogGathering) {
        L.v("[ModuleConfiguration] updateConfigVariables, from server response:[" + (serverResponse != null) + "]");

        //the directive is a top level key read off the live response itself, so it applies even when the inner 'c'
        //object was rejected and nothing was stored
        if (decidesLogGathering) {
            updateLogGatheringDirective(serverResponse);
        }

        if (latestRetrievedConfiguration == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        //A setting the configuration does not carry goes back to the developer's value in clyConfig, which the SDK
        //never writes resolved values onto, or to the SDK default when no CountlyConfig option sets it
        currentVNetworking = extractValue(keyRNetworking, sb, currentVNetworking, true);
        currentVTracking = extractValue(keyRTracking, sb, currentVTracking, true);
        currentVSessionTracking = extractValue(keyRSessionTracking, sb, currentVSessionTracking, true);
        currentVCrashReporting = extractValue(keyRCrashReporting, sb, currentVCrashReporting, true);
        currentVAutomaticSessionTracking = extractValue(keyRAutomaticSessionTracking, sb, currentVAutomaticSessionTracking, !clyConfig.manualSessionControlEnabled);
        currentVAutomaticViewTracking = extractValue(keyRAutomaticViewTracking, sb, currentVAutomaticViewTracking, clyConfig.enableAutomaticViewTracking);
        currentVAutomaticCrashReporting = extractValue(keyRAutomaticCrashReporting, sb, currentVAutomaticCrashReporting, clyConfig.crashes.enableUnhandledCrashReporting);
        currentVViewTracking = extractValue(keyRViewTracking, sb, currentVViewTracking, true);
        currentVCustomEventTracking = extractValue(keyRCustomEventTracking, sb, currentVCustomEventTracking, true);
        currentVLocationTracking = extractValue(keyRLocationTracking, sb, currentVLocationTracking, true);
        currentVContentZone = extractValue(keyREnterContentZone, sb, currentVContentZone, false);
        serverConfigUpdateInterval = extractValue(keyRServerConfigUpdateInterval, sb, serverConfigUpdateInterval, serverConfigUpdateIntervalDefault, Integer.class, (Integer value) -> value > 0);
        currentVRefreshContentZone = extractValue(keyRRefreshContentZone, sb, currentVRefreshContentZone, true);
        currentVBackoffMechanism = extractValue(keyRBackoffMechanism, sb, currentVBackoffMechanism, clyConfig.backOffMechanismEnabled);
        currentVBOMAcceptedTimeoutSeconds = extractValue(keyRBOMAcceptedTimeout, sb, currentVBOMAcceptedTimeoutSeconds, bomAcceptedTimeoutSecondsDefault, Integer.class, (Integer value) -> value > 0);
        currentVBOMRQPercentage = extractValue(keyRBOMRQPercentage, sb, currentVBOMRQPercentage, bomRQPercentageDefault, Double.class, (Double value) -> value > 0.0 && value < 1.0);
        currentVBOMRequestAge = extractValue(keyRBOMRequestAge, sb, currentVBOMRequestAge, bomRequestAgeDefault, Integer.class, (Integer value) -> value > 0);
        currentVBOMDuration = extractValue(keyRBOMDuration, sb, currentVBOMDuration, bomDurationDefault, Integer.class, (Integer value) -> value > 0);
        currentVUserPropertyCacheLimit = extractValue(keyRUserPropertyCacheLimit, sb, currentVUserPropertyCacheLimit, userPropertyCacheLimitDefault, Integer.class, (Integer value) -> value > 0);

        //Resolved onto this instance, never back onto clyConfig - see the field declarations. The fallbacks carry the
        //minimums Countly#onSdkConfigurationChanged applies, so a developer value below one is not a change on every fetch
        currentVMaxRequestQueueSize = extractValue(keyRReqQueueSize, sb, currentVMaxRequestQueueSize, Math.max(clyConfig.maxRequestQueueSize, 1), Integer.class, (Integer value) -> value > 0);
        currentVEventQueueSizeThreshold = extractValue(keyREventQueueSize, sb, currentVEventQueueSizeThreshold, developerValueOr(clyConfig.eventQueueSizeThreshold, 1, Countly.EVENT_QUEUE_SIZE_THRESHOLD_DEFAULT), Integer.class, (Integer value) -> value > 0);
        currentVLoggingEnabled = extractValue(keyRLogging, sb, currentVLoggingEnabled, clyConfig.loggingEnabled);
        currentVSessionUpdateTimerDelay = extractValue(keyRSessionUpdateInterval, sb, currentVSessionUpdateTimerDelay, clyConfig.sessionUpdateTimerDelay != null ? clyConfig.sessionUpdateTimerDelay : Long.valueOf(Countly.TIMER_DELAY_IN_SECONDS).intValue(), Integer.class, (Integer value) -> value > 0);
        //Internal limits are resolved onto THIS instance's limits, not onto the shared CountlyConfig: they
        //are read live on every event, view, crash and user property, so writing them back onto a config
        //that a second instance may also hold would let this instance's /o/sdk response retruncate that
        //instance's data. A limit the configuration does not carry goes back to the developer's limit in
        //clyConfig, which Countly#init has already raised to the minimum or filled with the default.
        ConfigSdkInternalLimits limits = _cly.sdkInternalLimits_;
        ConfigSdkInternalLimits developerLimits = clyConfig.sdkInternalLimits;
        limits.setMaxKeyLength(extractValue(keyRLimitKeyLength, sb, limits.maxKeyLength, developerValueOr(developerLimits.maxKeyLength, 1, Countly.maxKeyLengthDefault), Integer.class, (Integer value) -> value > 0));
        limits.setMaxValueSize(extractValue(keyRLimitValueSize, sb, limits.maxValueSize, developerValueOr(developerLimits.maxValueSize, 1, Countly.maxValueSizeDefault), Integer.class, (Integer value) -> value > 0));
        limits.setMaxSegmentationValues(extractValue(keyRLimitSegValues, sb, limits.maxSegmentationValues, developerValueOr(developerLimits.maxSegmentationValues, 1, Countly.maxSegmentationValuesDefault), Integer.class, (Integer value) -> value > 0));
        limits.setMaxBreadcrumbCount(extractValue(keyRLimitBreadcrumb, sb, limits.maxBreadcrumbCount, developerValueOr(developerLimits.maxBreadcrumbCount, 1, Countly.maxBreadcrumbCountDefault), Integer.class, (Integer value) -> value > 0));
        limits.setMaxStackTraceLinesPerThread(extractValue(keyRLimitTraceLine, sb, limits.maxStackTraceLinesPerThread, developerValueOr(developerLimits.maxStackTraceLinesPerThread, 1, Countly.maxStackTraceLinesPerThreadDefault), Integer.class, (Integer value) -> value > 0));
        limits.setMaxStackTraceLineLength(extractValue(keyRLimitTraceLength, sb, limits.maxStackTraceLineLength, developerValueOr(developerLimits.maxStackTraceLineLength, 1, Countly.maxStackTraceLineLengthDefault), Integer.class, (Integer value) -> value > 0));
        currentVZoneTimerInterval = extractValue(keyRContentZoneInterval, sb, currentVZoneTimerInterval, clyConfig.content.zoneTimerInterval, Integer.class, (Integer value) -> value >= 16);
        currentVRequiresConsent = extractValue(keyRConsentRequired, sb, currentVRequiresConsent, clyConfig.shouldRequireConsent);
        currentVDropAgeHours = extractValue(keyRDropOldRequestTime, sb, currentVDropAgeHours, Math.max(clyConfig.dropAgeHours, 0), Integer.class, (Integer value) -> value >= 0);

        updateListingFilters();

        notifyIfConfigurationChanged(sb, clyConfig);
    }

    /**
     * Broadcast the change to every module through the single existing notification path, if anything changed at all
     */
    private void notifyIfConfigurationChanged(@NonNull final StringBuilder changedValues, @NonNull final CountlyConfig clyConfig) {
        String updatedValues = changedValues.toString();
        if (!updatedValues.isEmpty()) {
            L.i("[ModuleConfiguration] updateConfigVariables, SDK configuration has changed, notifying the SDK, new values: [" + updatedValues + "]");
            _cly.onSdkConfigurationChanged(clyConfig);
        }
    }

    /**
     * Applies the top level 'lg' directive of a live response, shapes {"e":false} or {"e":true,"i":id,"l":"ewidv","b":100}.
     * Only a live response decides: a stored directive leaves the SDK undecided, a response without a usable 'lg' or
     * disabled config requests decide off. serverResponse is null on the storage/init path.
     */
    private void updateLogGatheringDirective(@Nullable final JSONObject serverResponse) {
        readLogGatheringDirective(serverResponse);
        L.applyLogGatheringDirective(serverResponse == null ? "init" : "server response");
    }

    /** Decides the log gathering state, see {@link #updateLogGatheringDirective(JSONObject)} for the rules. */
    private void readLogGatheringDirective(@Nullable final JSONObject serverResponse) {
        if (serverConfigRequestsDisabled) {
            //without configuration requests no directive can ever arrive or be renewed, so nothing can ever be
            //gathered. That is a decision, and it spares the logger a buffer that would never be adopted
            L.d("[ModuleConfiguration] readLogGatheringDirective, SDK behaviour settings requests are disabled, log gathering can never be armed");
            setLogGatheringOff();
            return;
        }

        if (serverResponse == null) {
            //a stored directive decides nothing, a run only ever uploads the lines it gathered itself
            L.d("[ModuleConfiguration] readLogGatheringDirective, not a server response, log gathering stays undecided");
            return;
        }

        JSONObject directive = serverResponse.optJSONObject(keyRLogGathering);
        if (directive == null) {
            //the server has spoken and it sent no usable directive, that decides gathering off
            L.d("[ModuleConfiguration] readLogGatheringDirective, server response had no usable '" + keyRLogGathering + "', log gathering is off");
            setLogGatheringOff();
            return;
        }

        if (!Boolean.TRUE.equals(directive.opt(keyLGEnabled))) {
            L.d("[ModuleConfiguration] readLogGatheringDirective, directive says log gathering is off");
            setLogGatheringOff();
            return;
        }

        Object gatherIdRaw = directive.opt(keyLGId);
        String gatherId = gatherIdRaw instanceof String ? ((String) gatherIdRaw).trim() : "";
        if (gatherId.isEmpty()) {
            //without the id the server would reject every uploaded batch, so this is not a usable directive
            L.d("[ModuleConfiguration] readLogGatheringDirective, directive enables log gathering but carries no usable '" + keyLGId + "', log gathering is off");
            setLogGatheringOff();
            return;
        }

        currentVLogGatheringState = LogGatheringState.GATHERING;
        currentVLogGatheringId = gatherId;
        currentVLogGatheringLevels = sanitizeLogGatheringLevels(directive.opt(keyLGLevels));
        currentVLogGatheringBatchSize = sanitizeLogGatheringBatchSize(directive.opt(keyLGBatchSize));
        L.d("[ModuleConfiguration] readLogGatheringDirective, log gathering is on, id:[" + currentVLogGatheringId + "], levels:[" + currentVLogGatheringLevels + "], batch size:[" + currentVLogGatheringBatchSize + "]");
    }

    /**
     * A gather belongs to the user it was armed for, so a device ID change without merge ends it: what is gathered goes
     * out under the current device ID, the rest is dropped, and only a later server response can arm the next user.
     * Lines captured while still undecided are dropped as well, a directive arriving later must not adopt them.
     */
    void stopLogGatheringForDeviceIdChange() {
        if (currentVLogGatheringState == LogGatheringState.NOT_GATHERING) {
            return;
        }

        L.flushGatheredLogsBeforeDeviceIdChange();
        setLogGatheringOff();
        L.applyLogGatheringDirective("device ID changed without merge");
    }

    /** No response can arrive this run (failed fetch, temporary device ID), which decides against gathering. */
    private void decideLogGatheringOffIfUndecided(@NonNull final String reason) {
        if (currentVLogGatheringState == LogGatheringState.UNDECIDED) {
            L.d("[ModuleConfiguration] decideLogGatheringOffIfUndecided, " + reason + ", log gathering is off");
            setLogGatheringOff();
            L.applyLogGatheringDirective(reason);
        }
    }

    private void setLogGatheringOff() {
        currentVLogGatheringState = LogGatheringState.NOT_GATHERING;
        currentVLogGatheringId = null;
        currentVLogGatheringLevels = logGatheringAllLevels;
        currentVLogGatheringBatchSize = logGatheringDefaultBatchSize;
    }

    /**
     * Keeps only the level characters this SDK knows, in the order the server sent them, without duplicates.
     * Falls back to every level if the value is missing, not a string, or has nothing usable left after filtering.
     */
    @NonNull private String sanitizeLogGatheringLevels(@Nullable final Object levelsRaw) {
        if (!(levelsRaw instanceof String)) {
            return logGatheringAllLevels;
        }

        String levels = (String) levelsRaw;
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < levels.length(); i++) {
            char level = Character.toLowerCase(levels.charAt(i));
            if (logGatheringAllLevels.indexOf(level) > -1 && filtered.indexOf(String.valueOf(level)) < 0) {
                filtered.append(level);
            }
        }

        if (filtered.length() == 0) {
            L.d("[ModuleConfiguration] sanitizeLogGatheringLevels, no usable level in [" + levels + "], falling back to [" + logGatheringAllLevels + "]");
            return logGatheringAllLevels;
        }

        return filtered.toString();
    }

    /**
     * Clamps the batch size into [logGatheringMinBatchSize, logGatheringMaxBufferedLines],
     * falling back to logGatheringDefaultBatchSize if the value is missing or not a number.
     */
    private int sanitizeLogGatheringBatchSize(@Nullable final Object batchSizeRaw) {
        if (!(batchSizeRaw instanceof Number)) {
            return logGatheringDefaultBatchSize;
        }

        int batchSize = ((Number) batchSizeRaw).intValue();
        return Math.min(logGatheringMaxBufferedLines, Math.max(logGatheringMinBatchSize, batchSize));
    }

    private void updateListingFilters() {
        L.d("[ModuleConfiguration] updateListingFilters, current listing filters before updating: \n" +
            "Event Filter List: " + currentVEventFilterList.filterList + ", isWhitelist: " + currentVEventFilterList.isWhitelist + "\n" +
            "User Property Filter List: " + currentVUserPropertyFilterList.filterList + ", isWhitelist: " + currentVUserPropertyFilterList.isWhitelist + "\n" +
            "Segmentation Filter List: " + currentVSegmentationFilterList.filterList + ", isWhitelist: " + currentVSegmentationFilterList.isWhitelist + "\n" +
            "Event Segmentation Filter List: " + currentVEventSegmentationFilterList.filterList + ", isWhitelist: " + currentVEventSegmentationFilterList.isWhitelist + "\n" +
            "Journey Trigger Events: " + currentVJourneyTriggerEvents + "\n" +
            "Journey Trigger Views: " + currentVJourneyTriggerViews);
        JSONArray eventBlacklistJSARR = latestRetrievedConfiguration.optJSONArray(keyREventBlacklist);
        JSONArray eventWhitelistJSARR = latestRetrievedConfiguration.optJSONArray(keyREventWhitelist);
        JSONArray userPropertyBlacklistJSARR = latestRetrievedConfiguration.optJSONArray(keyRUserPropertyBlacklist);
        JSONArray userPropertyWhitelistJSARR = latestRetrievedConfiguration.optJSONArray(keyRUserPropertyWhitelist);
        JSONArray segmentationBlacklistJSARR = latestRetrievedConfiguration.optJSONArray(keyRSegmentationBlacklist);
        JSONArray segmentationWhitelistJSARR = latestRetrievedConfiguration.optJSONArray(keyRSegmentationWhitelist);
        JSONObject eventSegmentationBlacklistJSOBJ = latestRetrievedConfiguration.optJSONObject(keyREventSegmentationBlacklist);
        JSONObject eventSegmentationWhitelistJSOBJ = latestRetrievedConfiguration.optJSONObject(keyREventSegmentationWhitelist);
        JSONArray journeyTriggerEventsJSARR = latestRetrievedConfiguration.optJSONArray(keyRJourneyTriggerEvents);
        JSONArray journeyTriggerViewsJSARR = latestRetrievedConfiguration.optJSONArray(keyRJourneyTriggerViews);

        if (eventBlacklistJSARR != null) {
            extractFilterSetFromJSONArray(eventBlacklistJSARR, currentVEventFilterList.filterList);
            currentVEventFilterList.isWhitelist = false;
        } else if (eventWhitelistJSARR != null) {
            extractFilterSetFromJSONArray(eventWhitelistJSARR, currentVEventFilterList.filterList);
            currentVEventFilterList.isWhitelist = true;
        } else {
            clearFilter(currentVEventFilterList);
        }

        if (userPropertyBlacklistJSARR != null) {
            extractFilterSetFromJSONArray(userPropertyBlacklistJSARR, currentVUserPropertyFilterList.filterList);
            currentVUserPropertyFilterList.isWhitelist = false;
        } else if (userPropertyWhitelistJSARR != null) {
            extractFilterSetFromJSONArray(userPropertyWhitelistJSARR, currentVUserPropertyFilterList.filterList);
            currentVUserPropertyFilterList.isWhitelist = true;
        } else {
            clearFilter(currentVUserPropertyFilterList);
        }

        if (segmentationBlacklistJSARR != null) {
            extractFilterSetFromJSONArray(segmentationBlacklistJSARR, currentVSegmentationFilterList.filterList);
            currentVSegmentationFilterList.isWhitelist = false;
        } else if (segmentationWhitelistJSARR != null) {
            extractFilterSetFromJSONArray(segmentationWhitelistJSARR, currentVSegmentationFilterList.filterList);
            currentVSegmentationFilterList.isWhitelist = true;
        } else {
            clearFilter(currentVSegmentationFilterList);
        }

        if (eventSegmentationBlacklistJSOBJ != null) {
            currentVEventSegmentationFilterList.filterList.clear();
            currentVEventSegmentationFilterList.isWhitelist = false;
            Iterator<String> keys = eventSegmentationBlacklistJSOBJ.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray jsonArray = eventSegmentationBlacklistJSOBJ.optJSONArray(key);
                if (jsonArray != null) {
                    Set<String> filterSet = new HashSet<>();
                    extractFilterSetFromJSONArray(jsonArray, filterSet);
                    currentVEventSegmentationFilterList.filterList.put(key, filterSet);
                }
            }
        } else if (eventSegmentationWhitelistJSOBJ != null) {
            currentVEventSegmentationFilterList.filterList.clear();
            currentVEventSegmentationFilterList.isWhitelist = true;
            Iterator<String> keys = eventSegmentationWhitelistJSOBJ.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray jsonArray = eventSegmentationWhitelistJSOBJ.optJSONArray(key);
                if (jsonArray != null) {
                    Set<String> filterSet = new HashSet<>();
                    extractFilterSetFromJSONArray(jsonArray, filterSet);
                    currentVEventSegmentationFilterList.filterList.put(key, filterSet);
                }
            }
        } else {
            currentVEventSegmentationFilterList.filterList.clear();
            currentVEventSegmentationFilterList.isWhitelist = false;
        }

        if (journeyTriggerEventsJSARR != null) {
            extractFilterSetFromJSONArray(journeyTriggerEventsJSARR, currentVJourneyTriggerEvents);
        } else {
            currentVJourneyTriggerEvents.clear();
        }

        if (journeyTriggerViewsJSARR != null) {
            extractFilterSetFromJSONArray(journeyTriggerViewsJSARR, currentVJourneyTriggerViews);
        } else {
            currentVJourneyTriggerViews.clear();
        }

        L.d("[ModuleConfiguration] updateListingFilters, current listing filters after updating: \n" +
            "Event Filter List: " + currentVEventFilterList.filterList + ", isWhitelist: " + currentVEventFilterList.isWhitelist + "\n" +
            "User Property Filter List: " + currentVUserPropertyFilterList.filterList + ", isWhitelist: " + currentVUserPropertyFilterList.isWhitelist + "\n" +
            "Segmentation Filter List: " + currentVSegmentationFilterList.filterList + ", isWhitelist: " + currentVSegmentationFilterList.isWhitelist + "\n" +
            "Event Segmentation Filter List: " + currentVEventSegmentationFilterList.filterList + ", isWhitelist: " + currentVEventSegmentationFilterList.isWhitelist + "\n" +
            "Journey Trigger Events: " + currentVJourneyTriggerEvents + "\n" +
            "Journey Trigger Views: " + currentVJourneyTriggerViews);
    }

    /**
     * Turns a filter off: an empty blacklist lets everything through
     *
     * @param filter the filter the configuration no longer carries
     */
    private void clearFilter(@NonNull FilterList<Set<String>> filter) {
        filter.filterList.clear();
        filter.isWhitelist = false;
    }

    private void extractFilterSetFromJSONArray(@Nullable JSONArray jsonArray, @NonNull Set<String> targetSet) {
        if (jsonArray == null) {
            return;
        }
        targetSet.clear();
        for (int i = 0; i < jsonArray.length(); i++) {
            String item = jsonArray.optString(i, null);
            if (item != null) {
                targetSet.add(item);
            }
        }
    }

    /**
     * Requires 'v', 't' and an object 'c'. The key count is not checked: unknown top level keys are dropped in
     * {@link #saveAndStoreDownloadedConfig(JSONObject)} instead of rejecting the whole payload.
     */
    boolean validateServerConfig(@NonNull JSONObject config) {
        JSONObject newInner = config.optJSONObject(keyRConfig);

        L.v("[ModuleConfiguration] validateServerConfig");
        if (!config.has(keyRVersion)) {
            L.w("[ModuleConfiguration] validateServerConfig, Retrieved configuration does not has a 'version' field. Config will be ignored.");
            return false;
        } else if (!config.has(keyRTimestamp)) {
            L.w("[ModuleConfiguration] validateServerConfig, Retrieved configuration does not has a 'timestamp' field. Config will be ignored.");
            return false;
        } else if (!config.has(keyRConfig)) {
            L.w("[ModuleConfiguration] validateServerConfig, Retrieved configuration does not has a 'configuration' field. Config will be ignored.");
            return false;
        } else if (newInner == null) {
            L.d("[ModuleConfiguration] validateServerConfig, Config rejected: inner 'c' is not an object.");
            return false;
        }

        removeUnsupportedKeys(newInner);
        return true;
    }

    private void removeUnsupportedKeys(@NonNull JSONObject newInner) {
        Iterator<String> keys = newInner.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = newInner.opt(key);

            boolean isValid = false;

            // --- Boolean keys ---
            switch (key) {
                case keyRNetworking:
                case keyRTracking:
                case keyRSessionTracking:
                case keyRCrashReporting:
                case keyRAutomaticSessionTracking:
                case keyRAutomaticViewTracking:
                case keyRAutomaticCrashReporting:
                case keyRViewTracking:
                case keyRCustomEventTracking:
                case keyRLocationTracking:
                case keyREnterContentZone:
                case keyRRefreshContentZone:
                case keyRBackoffMechanism:
                case keyRLogging:
                case keyRConsentRequired:
                    isValid = value instanceof Boolean;
                    break;

                // --- Positive Integer keys (> 0) ---
                case keyRServerConfigUpdateInterval:
                case keyRBOMAcceptedTimeout:
                case keyRBOMRequestAge:
                case keyRBOMDuration:
                case keyRReqQueueSize:
                case keyREventQueueSize:
                case keyRSessionUpdateInterval:
                case keyRLimitKeyLength:
                case keyRLimitValueSize:
                case keyRLimitSegValues:
                case keyRLimitBreadcrumb:
                case keyRLimitTraceLine:
                case keyRLimitTraceLength:
                case keyRUserPropertyCacheLimit:
                    isValid = value instanceof Integer && ((Integer) value) > 0;
                    break;

                // --- Integer >= 0 ---
                case keyRDropOldRequestTime:
                    isValid = value instanceof Integer && ((Integer) value) >= 0;
                    break;

                // --- Integer >= 16 ---
                case keyRContentZoneInterval:
                    isValid = value instanceof Integer && ((Integer) value) >= 16;
                    break;

                // --- Double between 0.0 and 1.0 ---
                case keyRBOMRQPercentage:
                    isValid = value instanceof Double && ((Double) value > 0.0 && (Double) value < 1.0);
                    break;

                // --- Filtering keys ---
                case keyREventBlacklist:
                case keyRSegmentationBlacklist:
                case keyRUserPropertyBlacklist:
                case keyREventWhitelist:
                case keyRSegmentationWhitelist:
                case keyRUserPropertyWhitelist:
                case keyRJourneyTriggerEvents:
                case keyRJourneyTriggerViews:
                    isValid = value instanceof JSONArray;
                    break;
                case keyREventSegmentationBlacklist:
                case keyREventSegmentationWhitelist:
                    isValid = value instanceof JSONObject;
                    break;
                // --- Unknown keys ---
                default:
                    L.w("[ModuleConfiguration] removeUnsupportedKeys, Unknown key: [" + key + "], removing it. value: [" + value + "]");
                    break;
            }

            // --- If not valid or not known, remove it ---
            if (!isValid) {
                L.w("[ModuleConfiguration] removeUnsupportedKeys, Invalid or unknown key: [" + key + "], removing it. value: [" + value + "]");
                keys.remove();
            }
        }
    }

    void saveAndStoreDownloadedConfig(@NonNull JSONObject config) {
        L.v("[ModuleConfiguration] saveAndStoreDownloadedConfig");
        boolean validConfig = validateServerConfig(config);
        if (!validConfig) {
            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Retrieved configuration is not valid, ignoring it.");
            latestRetrievedConfigurationFull = null;
            latestRetrievedConfiguration = null;
            return;
        }

        JSONObject newInner = config.optJSONObject(keyRConfig);
        assert newInner != null;

        // Each valid configuration replaces the stored one as a whole, so a setting the server no longer sends is
        // gone from it and goes back to its developer value in updateConfigVariables.
        JSONObject replacementInner = new JSONObject();
        Iterator<String> keys = newInner.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = newInner.opt(key);
            if (value != null && !JSONObject.NULL.equals(value)) {
                try {
                    replacementInner.put(key, value);
                } catch (JSONException e) {
                    L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Failed to copy inner config key: " + key, e);
                }
            }
        }

        // Only the supported top level keys are kept: version, timestamp and the log gathering directive. Everything
        // else that sits next to them is left behind on purpose, and a key the new config does not carry is not kept
        // from the previous one, so an old gather id is never used against a directive that no longer exists.
        JSONObject replacementFull = new JSONObject();
        for (String key : supportedTopLevelKeys) {
            if (keyRConfig.equals(key) || !config.has(key)) {
                continue;
            }

            try {
                replacementFull.put(key, config.get(key));
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Failed to copy top level key: " + key, e);
            }
        }

        try {
            replacementFull.put(keyRConfig, replacementInner);
        } catch (JSONException e) {
            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Failed to store the inner config", e);
        }

        // unsupported top level keys are dropped quietly, never stored ('ct' is already stripped before validation)
        List<String> unsupportedTopLevelKeys = new ArrayList<>();
        Iterator<String> topLevelKeys = config.keys();
        while (topLevelKeys.hasNext()) {
            String key = topLevelKeys.next();
            if (!supportedTopLevelKeys.contains(key)) {
                unsupportedTopLevelKeys.add(key);
            }
        }

        if (!unsupportedTopLevelKeys.isEmpty()) {
            L.d("[ModuleConfiguration] saveAndStoreDownloadedConfig, ignoring unsupported top level keys: " + unsupportedTopLevelKeys);
        }

        latestRetrievedConfigurationFull = replacementFull;
        latestRetrievedConfiguration = replacementInner;

        // Save updated config
        storageProvider.setServerConfig(latestRetrievedConfigurationFull.toString());
    }

    /**
     * Perform network request for retrieving latest config
     * If valid config is downloaded, save it, and update the values
     * Example response:
     * {
     * "v":1,
     * "t":1681808287464,
     * "c":{
     * "tracking":false,
     * "networking":false,
     * "crashes":false,
     * "views":false,
     * "heartbeat":61,
     * "event_queue":11,
     * "request_queue":1001
     * },
     * "lg":{"e":true,"i":"gatherId","l":"ewidv","b":100}
     * }
     */
    void fetchConfigFromServer(@NonNull CountlyConfig config) {
        L.v("[ModuleConfiguration] fetchConfigFromServer");
        if (serverConfigRequestsDisabled) {
            L.v("[ModuleConfiguration] fetchConfigFromServer, fetch config from the server is aborted, server config requests are disabled");
            return;
        }

        // this module is constructed before ModuleDeviceId, so its own deviceIdProvider is filled in by
        // the provider wiring block in Countly#init rather than by the ModuleBase constructor
        if (deviceIdProvider.isTemporaryIdEnabled()) {
            //temporary id mode enabled, abort
            L.d("[ModuleConfiguration] fetchConfigFromServer, fetch config from the server is aborted, temporary device ID mode is set");
            decideLogGatheringOffIfUndecided("temporary device ID mode, no server response this run");
            return;
        }

        lastServerConfigFetchTimestamp = UtilsTime.currentTimestampMs();
        String requestData = requestQueueProvider.prepareServerConfigRequest();
        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final long fetchStartNs = System.nanoTime();
        final String requestedForDeviceId = deviceIdProvider.getDeviceId();
        final int mergesAtRequest = connectionTestMergesSoFar();

        immediateRequestGenerator.CreateImmediateRequestMaker().doWork(requestData, "/o/sdk", cp, false, true, checkResponse -> {
            if (checkResponse == null) {
                L.w("[ModuleConfiguration] Not possible to retrieve configuration data. Probably due to lack of connection to the server");
                decideLogGatheringOffIfUndecided("server config fetch failed");
                return;
            }

            L.d("[ModuleConfiguration] Retrieved configuration response: [" + checkResponse + "]");

            //read only here, from a live response, and stripped before the config is cached
            long fetchLatencyMs = (System.nanoTime() - fetchStartNs) / 1_000_000L;
            boolean connectionTestArmed = extractConnectionTestFlag(checkResponse);

            //a gather is armed for the device the request was made for, not for whoever the device ID is by now
            final boolean sameDevice = Objects.equals(requestedForDeviceId, deviceIdProvider.getDeviceId());
            if (!sameDevice) {
                L.d("[ModuleConfiguration] fetchConfigFromServer, the device ID changed since the request, its log gathering directive is ignored");
            }

            saveAndStoreDownloadedConfig(checkResponse);
            updateConfigVariables(config, checkResponse, sameDevice);

            //the report goes under the fetch-time ID, so after a merge, which folds that user away, the test does not run
            if (connectionTestArmed && connectionTestMergesSoFar() == mergesAtRequest) {
                notifyConnectionTestArmed(fetchLatencyMs, requestedForDeviceId, mergesAtRequest);
            } else if (connectionTestArmed) {
                L.d("[ModuleConfiguration] fetchConfigFromServer, the device ID was changed with merge since the request, skipping the connection test armed for it");
            }

            if (!sameDevice && currentVLogGatheringState == LogGatheringState.UNDECIDED) {
                L.d("[ModuleConfiguration] fetchConfigFromServer, log gathering is still undecided, fetching again for the current device ID");
                fetchConfigFromServer(config);
            }
        }, L);
    }

    /** Reads and removes the 'ct' flag from a live response, so it is never cached and can never re-arm from storage. */
    static boolean extractConnectionTestFlag(@Nullable JSONObject serverConfigResponse) {
        if (serverConfigResponse == null || !serverConfigResponse.has(keyRConnectionTest)) {
            return false;
        }

        Object value = serverConfigResponse.remove(keyRConnectionTest);
        if (value == null || JSONObject.NULL.equals(value)) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            return !s.isEmpty() && !s.equals("0") && !s.equalsIgnoreCase("false");
        }
        return true;
    }

    /**
     * Starts the probe battery the server armed.
     *
     * @param fetchLatencyMs latency of the fetch that delivered the flag
     * @param armedForDeviceId the device ID the fetch was made for, which the server armed the test for
     */
    private void notifyConnectionTestArmed(final long fetchLatencyMs, @Nullable final String armedForDeviceId, final int mergesAtRequest) {
        ModuleConnectionTest connectionTest = _cly.moduleConnectionTest;
        if (connectionTest == null) {
            L.d("[ModuleConfiguration] notifyConnectionTestArmed, instance torn down before the response arrived, ignoring");
            return;
        }
        connectionTest.startBattery(fetchLatencyMs, armedForDeviceId, mergesAtRequest);
    }

    /** @return the device ID changes with merge so far, as the connection test counts them; 0 once it is torn down */
    private int connectionTestMergesSoFar() {
        ModuleConnectionTest connectionTest = _cly.moduleConnectionTest;
        return connectionTest == null ? 0 : connectionTest.mergesSoFar();
    }

    void fetchIfTimeIsUpForFetchingServerConfig() {
        if (serverConfigRequestsDisabled) {
            return;
        }

        if (lastServerConfigFetchTimestamp > 0) {
            long currentTime = UtilsTime.currentTimestampMs();
            long timePassed = currentTime - lastServerConfigFetchTimestamp;

            if (timePassed > (long) currentServerConfigUpdateInterval * 60 * 60 * 1000) {
                fetchConfigFromServer(_cly.config_);
            }
        }
    }

    // configuration getters

    @Override
    public boolean getNetworkingEnabled() {
        return currentVNetworking;
    }

    @Override
    public boolean getTrackingEnabled() {
        return currentVTracking;
    }

    @Override public boolean getSessionTrackingEnabled() {
        return currentVSessionTracking;
    }

    @Override public boolean getViewTrackingEnabled() {
        return currentVViewTracking;
    }

    @Override public boolean getCustomEventTrackingEnabled() {
        return currentVCustomEventTracking;
    }

    @Override public boolean getContentZoneEnabled() {
        return currentVContentZone;
    }

    @Override public boolean getCrashReportingEnabled() {
        return currentVCrashReporting;
    }

    @Override public boolean getAutomaticSessionTrackingEnabled() {
        return currentVAutomaticSessionTracking;
    }

    @Override public boolean getAutomaticViewTrackingEnabled() {
        return currentVAutomaticViewTracking;
    }

    @Override public boolean getAutomaticCrashReportingEnabled() {
        return currentVAutomaticCrashReporting;
    }

    @Override public boolean getLocationTrackingEnabled() {
        return currentVLocationTracking;
    }

    @Override public boolean getRefreshContentZoneEnabled() {
        return currentVRefreshContentZone;
    }

    @Override public boolean getBOMEnabled() {
        return currentVBackoffMechanism;
    }

    @Override public int getBOMAcceptedTimeoutSeconds() {
        return currentVBOMAcceptedTimeoutSeconds;
    }

    @Override public double getBOMRQPercentage() {
        return currentVBOMRQPercentage;
    }

    @Override public int getBOMRequestAge() {
        return currentVBOMRequestAge;
    }

    @Override public int getBOMDuration() {
        return currentVBOMDuration;
    }

    @Override public int getRequestTimeoutDurationMillis() {
        return _cly.config_.requestTimeoutDuration * 1000;
    }

    @Override public int getUserPropertyCacheLimit() {
        return currentVUserPropertyCacheLimit;
    }

    @Override public FilterList<Set<String>> getEventFilterList() {
        return currentVEventFilterList;
    }

    @Override public FilterList<Set<String>> getUserPropertyFilterList() {
        return currentVUserPropertyFilterList;
    }

    @Override public FilterList<Set<String>> getSegmentationFilterList() {
        return currentVSegmentationFilterList;
    }

    @Override public FilterList<Map<String, Set<String>>> getEventSegmentationFilterList() {
        return currentVEventSegmentationFilterList;
    }

    @Override public Set<String> getJourneyTriggerEvents() {
        return currentVJourneyTriggerEvents;
    }

    @Override public Set<String> getJourneyTriggerViews() {
        return currentVJourneyTriggerViews;
    }

    @Override @NonNull public LogGatheringState getLogGatheringState() {
        return currentVLogGatheringState;
    }

    @Override @Nullable public String getLogGatheringId() {
        return currentVLogGatheringId;
    }

    @Override @NonNull public String getLogGatheringLevels() {
        return currentVLogGatheringLevels;
    }

    @Override public int getLogGatheringBatchSize() {
        return currentVLogGatheringBatchSize;
    }
}
