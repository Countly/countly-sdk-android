package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModuleRequestQueue extends ModuleBase implements BaseInfoProvider {
    RequestQueue requestQueueInterface;

    @NonNull String appKey;
    @NonNull String serverURL;

    //app crawlers
    private boolean shouldIgnoreCrawlers = true;//ignore app crawlers by default
    private boolean deviceIsAppCrawler = false;//by default assume that device is not a app crawler
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private final List<String> appCrawlerNames = new ArrayList<>(Arrays.asList("Calypso AppCrawler"));//List against which device name is checked to determine if device is app crawler
    static final String APP_KEY_KEY = "app_key";
    static final String HOUR_KEY = "hour";
    static final String DOW_KEY = "dow";
    static final String TZ_KEY = "tz";
    static final String SDK_VERSION_KEY = "sdk_version";
    static final String SDK_NAME_KEY = "sdk_name";
    static final String DEVICE_ID_KEY = "device_id";
    static final String OLD_DEVICE_ID_KEY = "old_device_id";
    static final String CHECKSUM_KEY = "checksum";
    static final String CHECKSUM_256_KEY = "checksum256";

    String[] preDefinedKeys = { APP_KEY_KEY, HOUR_KEY, DOW_KEY, TZ_KEY, SDK_VERSION_KEY, SDK_NAME_KEY, DEVICE_ID_KEY, OLD_DEVICE_ID_KEY, CHECKSUM_KEY, CHECKSUM_256_KEY };

    ModuleRequestQueue(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleRequestQueue] Initialising");

        config.baseInfoProvider = this;
        baseInfoProvider = this;

        appKey = config.appKey;
        serverURL = config.serverURL;

        //app crawler check
        if (config.shouldIgnoreAppCrawlers) {
            L.d("[ModuleRequestQueue] Ignoring app crawlers");
            shouldIgnoreCrawlers = config.shouldIgnoreAppCrawlers;
        }

        if (config.appCrawlerNames != null) {
            L.d("[ModuleRequestQueue] Adding app crawlers names");
            appCrawlerNames.addAll(Arrays.asList(config.appCrawlerNames));
        }

        checkIfDeviceIsAppCrawler();

        requestQueueInterface = new RequestQueue();
    }

    @Override public @NonNull String getAppKey() {
        return appKey;
    }

    @Override public @NonNull String getServerURL() {
        return serverURL;
    }

    synchronized List<String> requestQueueReplaceWithAppKey(String[] storedRequests, String targetAppKey) {
        try {
            List<String> filteredRequests = new ArrayList<>();

            if (storedRequests == null) {
                //early abort
                L.w("[ModuleRequestQueue] requestQueueReplaceWithAppKey, stopping replacing due to stored requests being 'null'");
                return filteredRequests;
            }

            if (targetAppKey == null || targetAppKey.isEmpty()) {
                //early abort
                L.w("[ModuleRequestQueue] requestQueueReplaceWithAppKey, stopping replacing due to target app key being 'null' or empty string");
                return filteredRequests;
            }

            String replacementPart = "app_key=" + UtilsNetworking.urlEncodeString(targetAppKey);

            for (String storedRequest : storedRequests) {
                if (storedRequest == null) {
                    continue;
                }

                boolean found = false;
                String[] parts = storedRequest.split("&");

                for (int b = 0; b < parts.length; b++) {
                    if (parts[b].contains("app_key=")) {
                        parts[b] = replacementPart;
                        found = true;
                        break;
                    }
                }

                //recombine and add
                StringBuilder stringBuilder = new StringBuilder(storedRequest.length());//todo make the lenght larger to take into account the app key size increase

                for (int c = 0; c < parts.length; c++) {
                    if (c != 0) {
                        stringBuilder.append("&");
                    }
                    stringBuilder.append(parts[c]);
                }
                filteredRequests.add(stringBuilder.toString());
            }

            return filteredRequests;
        } catch (Exception ex) {
            //in case of failure, abort
            L.e("[ModuleRequestQueue] Failed while overwriting appKeys, " + ex.toString());

            return null;
        }
    }

    synchronized List<String> requestQueueRemoveWithoutAppKey(String[] storedRequests, String targetAppKey) {
        List<String> filteredRequests = new ArrayList<>();

        if (storedRequests == null || targetAppKey == null) {
            //early abort
            return filteredRequests;
        }

        String searchablePart = "app_key=" + targetAppKey;

        for (String storedRequest : storedRequests) {
            if (storedRequest == null) {
                continue;
            }

            if (!storedRequest.contains(searchablePart)) {
                L.d("[ModuleRequestQueue] requestQueueEraseAppKeysRequests, Found a entry to remove: [" + storedRequest + "]");
            } else {
                filteredRequests.add(storedRequest);
            }
        }

        return filteredRequests;
    }

    /**
     * Check if events from event queue need to be added to the request queue
     * They will be sent either if the exceed the Threshold size or if their sending is forced
     */
    protected void sendEventsIfNeeded(boolean forceSendingEvents) {
        int eventsInEventQueue = storageProvider.getEventQueueSize();
        L.v("[ModuleRequestQueue] forceSendingEvents, forced:[" + forceSendingEvents + "], event count:[" + eventsInEventQueue + "]");

        if ((forceSendingEvents && eventsInEventQueue > 0) || eventsInEventQueue >= _cly.EVENT_QUEUE_SIZE_THRESHOLD) {
            requestQueueProvider.recordEvents(storageProvider.getEventsForRequestAndEmptyEventQueue());
        }
    }

    boolean isHttpPostForcedInternal() {
        return _cly.isHttpPostForced;
    }

    boolean isDeviceAppCrawlerInternal() {
        return deviceIsAppCrawler;
    }

    boolean ifShouldIgnoreCrawlersInternal() {
        return shouldIgnoreCrawlers;
    }

    private void checkIfDeviceIsAppCrawler() {
        String deviceName = deviceInfo.mp.getDevice();

        for (int a = 0; a < appCrawlerNames.size(); a++) {
            if (deviceName.equals(appCrawlerNames.get(a))) {
                deviceIsAppCrawler = true;
                return;
            }
        }
    }

    public void flushQueuesInternal() {
        CountlyStore store = _cly.countlyStore;

        final String[] storedRequests = store.getRequests();
        store.replaceRequests(new String[] {});

        L.d("[ModuleRequestQueue] flushRequestQueues removed [" + storedRequests.length + "] requests");
    }

    /**
     * Combine all events in event queue into a request and
     * attempt to process stored requests on demand
     */
    public void attemptToSendStoredRequestsInternal() {
        L.i("[ModuleRequestQueue] Calling attemptToSendStoredRequests");

        //combine all available events into a request
        sendEventsIfNeeded(true);

        //save the user profile changes if any
        _cly.moduleUserProfile.saveInternal();

        //trigger the processing of the request queue
        requestQueueProvider.tick();
    }

    /**
     * Go through the request queue and replace the appKey of all requests with the current appKey
     */
    synchronized public void requestQueueOverwriteAppKeysInternal() {
        L.i("[ModuleRequestQueue] Calling requestQueueOverwriteAppKeys");

        List<String> filteredRequests = requestQueueReplaceWithAppKey(storageProvider.getRequests(), baseInfoProvider.getAppKey());
        if (filteredRequests != null) {
            storageProvider.replaceRequestList(filteredRequests);
            attemptToSendStoredRequestsInternal();
        }
    }

    /**
     * Go through the request queue and delete all requests that don't have the current application key
     */
    synchronized public void requestQueueEraseAppKeysRequestsInternal() {
        L.i("[ModuleRequestQueue] Calling requestQueueEraseAppKeysRequests");

        List<String> filteredRequests = requestQueueRemoveWithoutAppKey(storageProvider.getRequests(), baseInfoProvider.getAppKey());
        storageProvider.replaceRequestList(filteredRequests);
        attemptToSendStoredRequestsInternal();
    }

    /**
     * Send request data after removing the predefined keys
     */
    synchronized public void addDirectRequestInternal(@NonNull Map<String, String> requestMap) {
        long pccTsStartAddDirectRequest = 0L;
        if (pcc != null) {
            pccTsStartAddDirectRequest = UtilsTime.getNanoTime();
        }

        L.i("[ModuleRequestQueue] Calling addDirectRequestInternal");
        if (!_cly.isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before adding direct request, returning");
            return;
        }

        if (!consentProvider.anyConsentGiven()) {
            L.e("[ModuleRequestQueue] addDirectRequest, no consent is given, returning");
            return;
        }

        if (requestMap == null || requestMap.isEmpty()) {
            L.e("[ModuleRequestQueue] addDirectRequest, provided requestMap was null or empty, returning");
            return;
        }

        // Filtering and removing predefined/restricted keys
        Map<String, String> filteredRequestMap = new HashMap<>();
        for (Map.Entry<String, String> entry : requestMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean isPreDefinedKey = false;

            for (String preDefinedKey : preDefinedKeys) {
                if (preDefinedKey.equals(key)) {
                    //if it's a predefined field
                    isPreDefinedKey = true;
                    L.w("[ModuleRequestQueue] addDirectRequest, removing provided key: [" + key + "] is a restricted key.");
                    break;
                }
            }

            if (!isPreDefinedKey) {
                filteredRequestMap.put(key, value.toString());
            }
        }

        if (filteredRequestMap.isEmpty()) {
            L.e("[ModuleRequestQueue] addDirectRequest, filteredRequestMap was null or empty, returning");
            return;
        }

        int requestDataCount = requestMap.size();
        int filteredDataCount = filteredRequestMap.size();
        int delta = requestDataCount - filteredDataCount;
        if (delta > 0) {
            L.w("[ModuleRequestQueue] addDirectRequest, [" + delta + "] restricted keys are removed");
        }
        requestQueueProvider.sendDirectRequest(filteredRequestMap);

        if (pcc != null) {
            pcc.TrackCounterTimeNs("ModuleRequestQueue_addDirectRequestInternal", UtilsTime.getNanoTime() - pccTsStartAddDirectRequest);
        }
    }

    private void recordMetricsInternal(@NonNull Map<String, String> metricsOverride) {
        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.metrics)) {
            L.d("[ModuleRequestQueue] recordMetricsInternal, no consent given for metrics");
            return;
        }

        String preparedMetrics = deviceInfo.getMetrics(_cly.context_, metricsOverride, L);
        requestQueueProvider.sendMetricsRequest(preparedMetrics);
    }

    void esWriteCachesToPersistenceInternal(@Nullable ExplicitStorageCallback callback) {
        L.i("[ModuleRequestQueue] Calling esWriteCachesToPersistenceInternal");
        storageProvider.esWriteCacheToStorage(callback);
    }

    boolean doesBelongToCurrentAppKeyOrDeviceId(@NonNull String request) {
        return request.contains(APP_KEY_KEY + "=" + baseInfoProvider.getAppKey()) && request.contains(DEVICE_ID_KEY + "=" + deviceIdProvider.getDeviceId());
    }

    @Override
    void halt() {
        requestQueueInterface = null;
    }

    public class RequestQueue {
        /**
         * Get the status of the override for HTTP POST
         *
         * @return return "true" if HTTP POST ir forced
         */
        public boolean isHttpPostForced() {
            synchronized (_cly) {
                L.v("[RequestQueue] Calling 'isHttpPostForced'");

                return isHttpPostForcedInternal();
            }
        }

        /**
         * Return if current device is detected as a app crawler
         *
         * @return returns if devices is detected as a app crawler
         */
        public boolean isDeviceAppCrawler() {
            synchronized (_cly) {
                L.v("[RequestQueue] Calling 'isDeviceAppCrawler'");
                return isDeviceAppCrawlerInternal();
            }
        }

        /**
         * Return if the countly sdk should ignore app crawlers
         */
        public boolean ifShouldIgnoreCrawlers() {
            synchronized (_cly) {
                L.v("[RequestQueue] Calling 'ifShouldIgnoreCrawlers'");
                return ifShouldIgnoreCrawlersInternal();
            }
        }

        /**
         * Deletes all stored requests to server.
         * This includes events, crashes, views, sessions, etc
         * Call only if you don't need that information
         */
        public void flushQueues() {
            synchronized (_cly) {
                L.v("[RequestQueue] Calling 'flushQueues'");
                flushQueuesInternal();
            }
        }

        /**
         * Combine all events in event queue into a request and
         * attempt to process stored requests on demand
         */
        public void attemptToSendStoredRequests() {
            synchronized (_cly) {
                L.v("[RequestQueue] Calling 'attemptToSendStoredRequestsInternal'");
                attemptToSendStoredRequestsInternal();
            }
        }

        /**
         * Go through the request queue and replace the appKey of all requests with the current appKey
         */
        public void overwriteAppKeys() {
            synchronized (_cly) {
                L.i("[Countly] Calling overwriteAppKeys");
                requestQueueOverwriteAppKeysInternal();
            }
        }

        /**
         * Go through the request queue and delete all requests that don't have the current application key
         */
        public void eraseWrongAppKeyRequests() {
            synchronized (_cly) {
                L.i("[Countly] Calling eraseWrongAppKeyRequests");
                requestQueueEraseAppKeysRequestsInternal();
            }
        }

        /**
         * This call is for creating custom manual requests that should be sent to the server.
         * The SDK will add base parameters like "device id", "app key", timestamps, checksums etc.
         * It is not possible to override those protected values.
         *
         * The SDK will take the provided request map and add those as key value pairs to the request.
         * The provided pairs will be html encoded before they are added to the request, therefore you
         * have to be sure not to encode them.
         *
         * If consent is being required, this call will check if any consent is given, but will not check further.
         * It is up to this calls user to make sure that they have the required consent to record the things they are trying to record.
         *
         * This call should not be used lightly and should only be used if the SDK misses some specific functionality.
         */
        public void addDirectRequest(@NonNull Map<String, String> requestMap) {
            synchronized (_cly) {
                L.i("[Countly] Calling addDirectRequest");
                addDirectRequestInternal(requestMap);
            }
        }

        /**
         * Call for the explicit storage mode.
         * Writes temporary memory caches to persistent storage.
         * This involves the Request queue and event queue.
         */
        public void esWriteCachesToPersistence() {
            synchronized (_cly) {
                L.i("[Countly] Calling esWriteCachesToStorage");
                esWriteCachesToPersistenceInternal(null);
            }
        }

        public void esWriteCachesToPersistence(@Nullable ExplicitStorageCallback callback) {
            synchronized (_cly) {
                L.i("[Countly] Calling esWriteCachesToStorage");
                esWriteCachesToPersistenceInternal(callback);
            }
        }

        /**
         * Record device metrics manually as a standalone call
         *
         * @param metricsOverride map of key value pairs to override the default metrics
         */
        public void recordMetrics(@Nullable Map<String, String> metricsOverride) {
            synchronized (_cly) {
                L.i("[RequestQueue] recordMetrics, Calling recordMetrics");
                Map<String, String> tempMetricsOverride = metricsOverride;
                if (tempMetricsOverride == null) {
                    tempMetricsOverride = new ConcurrentHashMap<>();
                }

                recordMetricsInternal(tempMetricsOverride);
            }
        }

        /**
         * To add new header key/value pairs or override existing ones.
         * A null or empty map is ignored. Null or empty keys, as well as null values,
         * are ignored.
         * Subsequent requests (including those created after overriding) will contain
         * the updated header set.
         *
         * @param customHeaderValues header key/value pairs to add or override
         */
        public void addCustomNetworkRequestHeaders(@Nullable Map<String, String> customHeaderValues) {
            synchronized (_cly) {
                L.i("[RequestQueue] addCustomNetworkRequestHeaders, Calling addCustomNetworkRequestHeaders");
                _cly.addCustomNetworkRequestHeaders(customHeaderValues);
            }
        }
    }
}
