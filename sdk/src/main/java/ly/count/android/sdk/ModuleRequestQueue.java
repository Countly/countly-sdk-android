package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModuleRequestQueue extends ModuleBase implements BaseInfoProvider {
    RequestQueue requestQueueInterface;

    @NonNull String appKey;
    @NonNull String serverURL;

    //app crawlers
    private boolean shouldIgnoreCrawlers = true;//ignore app crawlers by default
    private boolean deviceIsAppCrawler = false;//by default assume that device is not a app crawler
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private final List<String> appCrawlerNames = new ArrayList<>(Arrays.asList("Calypso AppCrawler"));//List against which device name is checked to determine if device is app crawler

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

            if (storedRequests == null || targetAppKey == null) {
                //early abort
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

                if (found) {
                    //recombine and add
                    StringBuilder stringBuilder = new StringBuilder(storedRequest.length());

                    for (int c = 0; c < parts.length; c++) {
                        if (c != 0) {
                            stringBuilder.append("&");
                        }
                        stringBuilder.append(parts[c]);
                    }
                    filteredRequests.add(stringBuilder.toString());
                } else {
                    //pass through the old one
                    filteredRequests.add(storedRequest);
                }
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
                L.d("[ModuleRequestQueue], requestQueueEraseAppKeysRequests, Found a entry to remove: [" + storedRequest + "]");
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
        L.v("[Countly] forceSendingEvents, forced:[" + forceSendingEvents + "], event count:[" + eventsInEventQueue + "]");

        if ((forceSendingEvents && eventsInEventQueue > 0) || eventsInEventQueue >= Countly.EVENT_QUEUE_SIZE_THRESHOLD) {
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
        String deviceName = DeviceInfo.getDevice();

        for (int a = 0; a < appCrawlerNames.size(); a++) {
            if (deviceName.equals(appCrawlerNames.get(a))) {
                deviceIsAppCrawler = true;
                return;
            }
        }
    }

    public void flushQueuesInternal() {
        CountlyStore store = _cly.countlyStore;

        int count = 0;

        while (true) {
            final String[] storedEvents = store.getRequests();
            if (storedEvents == null || storedEvents.length == 0) {
                // currently no data to send, we are done for now
                break;
            }
            //remove stored data
            store.removeRequest(storedEvents[0]);
            count++;
        }

        L.d("[ModuleRequestQueue] flushRequestQueues removed [" + count + "] requests");
    }

    /**
     * Combine all events in event queue into a request and
     * attempt to process stored requests on demand
     */
    public void attemptToSendStoredRequestsInternal() {
        L.i("[ModuleRequestQueue] Calling attemptToSendStoredRequests");

        //combine all available events into a request
        sendEventsIfNeeded(true);

        //trigger the processing of the request queue
        requestQueueProvider.tick();
    }

    /**
     * Go through the request queue and replace the appKey of all requests with the current appKey
     */
    synchronized public void requestQueueOverwriteAppKeysInternal() {
        L.i("[Countly] Calling requestQueueOverwriteAppKeys");

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
        L.i("[Countly] Calling requestQueueEraseAppKeysRequests");

        List<String> filteredRequests = requestQueueRemoveWithoutAppKey(storageProvider.getRequests(), baseInfoProvider.getAppKey());
        storageProvider.replaceRequestList(filteredRequests);
        attemptToSendStoredRequestsInternal();
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
                L.i("[RequestQueue] Calling 'isHttpPostForced'");

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
                L.i("[RequestQueue] Calling 'isDeviceAppCrawler'");
                return isDeviceAppCrawlerInternal();
            }
        }

        /**
         * Return if the countly sdk should ignore app crawlers
         */
        public boolean ifShouldIgnoreCrawlers() {
            synchronized (_cly) {
                L.i("[RequestQueue] Calling 'ifShouldIgnoreCrawlers'");
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
                L.i("[RequestQueue] Calling 'flushQueues'");
                flushQueuesInternal();
            }
        }

        /**
         * Combine all events in event queue into a request and
         * attempt to process stored requests on demand
         */
        public void attemptToSendStoredRequests() {
            synchronized (_cly) {
                L.i("[RequestQueue] Calling 'attemptToSendStoredRequestsInternal'");
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
    }
}
