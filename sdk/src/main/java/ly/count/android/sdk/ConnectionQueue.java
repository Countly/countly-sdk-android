/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.content.Context;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * ConnectionQueue queues session and event data and periodically sends that data to
 * a Count.ly server on a background thread.
 *
 * None of the methods in this class are synchronized because access to this class is
 * controlled by the Countly singleton, which is synchronized.
 *
 * NOTE: This class is only public to facilitate unit testing, because
 * of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
class ConnectionQueue implements RequestQueueProvider {
    private ExecutorService executor_;
    private Context context_;
    private Future<?> connectionProcessorFuture_;
    private DeviceId deviceId_;
    private SSLContext sslContext_;
    BaseInfoProvider baseInfoProvider;

    private Map<String, String> requestHeaderCustomValues;
    Map<String, String> metricOverride = null;

    protected ModuleLog L;
    protected ConsentProvider consentProvider;//link to the consent module
    StorageProvider storageProvider;

    void setBaseInfoProvider(BaseInfoProvider bip) {
        baseInfoProvider = bip;
    }

    void setStorageProvider(StorageProvider sp) {
        storageProvider = sp;
    }

    StorageProvider getStorageProvider() {
        return storageProvider;
    }

    Context getContext() {
        return context_;
    }

    void setContext(final Context context) {
        context_ = context;
    }

    void setupSSLContext() {
        if (Countly.publicKeyPinCertificates == null && Countly.certificatePinCertificates == null) {
            sslContext_ = null;
        } else {
            try {
                TrustManager[] tm = { new CertificateTrustManager(Countly.publicKeyPinCertificates, Countly.certificatePinCertificates) };
                sslContext_ = SSLContext.getInstance("TLS");
                sslContext_.init(null, tm, null);
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }
    }

    DeviceId getDeviceId() {
        return deviceId_;
    }

    public void setDeviceId(DeviceId deviceId) {
        this.deviceId_ = deviceId;
    }

    protected void setRequestHeaderCustomValues(Map<String, String> headerCustomValues) {
        requestHeaderCustomValues = headerCustomValues;
    }

    protected void setMetricOverride(Map<String, String> metricOverride) {
        if (L.logEnabled()) {
            if (metricOverride != null) {
                L.d("[Connection Queue] The following metric overrides are set:");

                for (String k : metricOverride.keySet()) {
                    L.d("[Connection Queue] key[" + k + "] val[" + metricOverride.get(k) + "]");
                }
            } else {
                L.d("[Connection Queue] No metric override is provided");
            }
        }
        this.metricOverride = metricOverride;
    }

    /**
     * Checks internal state and throws IllegalStateException if state is invalid to begin use.
     *
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    void checkInternalState() {
        if (context_ == null) {
            throw new IllegalStateException("context has not been set");
        }
        if (baseInfoProvider.getAppKey() == null || baseInfoProvider.getAppKey().length() == 0) {
            throw new IllegalStateException("app key has not been set");
        }
        if (storageProvider == null) {
            throw new IllegalStateException("countly storage provider has not been set");
        }
        if (baseInfoProvider.getServerURL() == null || !UtilsNetworking.isValidURL(baseInfoProvider.getServerURL())) {
            throw new IllegalStateException("server URL is not valid");
        }
        if (Countly.publicKeyPinCertificates != null && !baseInfoProvider.getServerURL().startsWith("https")) {
            throw new IllegalStateException("server must start with https once you specified public keys");
        }
    }

    /**
     * Records a session start event for the app and sends it to the server.
     *
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void beginSession(boolean locationDisabled, String locationCountryCode, String locationCity, String locationGpsCoordinates, String locationIpAddress) {
        checkInternalState();
        L.d("[Connection Queue] beginSession");

        boolean dataAvailable = false;//will only send data if there is something valuable to send
        String data = prepareCommonRequestData();

        if (consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            //add session data if consent given
            data += "&begin_session=1"
                + "&metrics=" + DeviceInfo.getMetrics(context_, metricOverride);//can be only sent with begin session

            String locationData = prepareLocationData(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);
            if (!locationData.isEmpty()) {
                data += locationData;
            }

            dataAvailable = true;
        }

        if (consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
            //add attribution data if consent given
            if (Countly.sharedInstance().isAttributionEnabled) {
                String cachedAdId = storageProvider.getCachedAdvertisingId();

                if (!cachedAdId.isEmpty()) {
                    data += "&aid=" + UtilsNetworking.urlEncodeString("{\"adid\":\"" + cachedAdId + "\"}");

                    dataAvailable = true;
                }
            }
        }

        Countly.sharedInstance().isBeginSessionSent = true;

        if (dataAvailable) {
            addRequestToQueue(data);
            tick();
        }
    }

    /**
     * Records a session duration event for the app and sends it to the server. This method does nothing
     * if passed a negative or zero duration.
     *
     * @param duration duration in seconds to extend the current app session, should be more than zero
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void updateSession(final int duration) {
        checkInternalState();
        L.d("[Connection Queue] updateSession");

        if (duration > 0) {
            boolean dataAvailable = false;//will only send data if there is something valuable to send
            String data = prepareCommonRequestData();

            if (consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
                data += "&session_duration=" + duration;
                dataAvailable = true;
            }

            if (consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
                if (Countly.sharedInstance().isAttributionEnabled) {
                    String cachedAdId = storageProvider.getCachedAdvertisingId();

                    if (!cachedAdId.isEmpty()) {
                        data += "&aid=" + UtilsNetworking.urlEncodeString("{\"adid\":\"" + cachedAdId + "\"}");
                        dataAvailable = true;
                    }
                }
            }

            if (dataAvailable) {
                addRequestToQueue(data);
                tick();
            }
        }
    }

    public void changeDeviceId(String deviceId, final int duration) {
        checkInternalState();
        L.d("[Connection Queue] changeDeviceId");

        if (!consentProvider.anyConsentGiven()) {
            L.d("[Connection Queue] request ignored, consent not given");
            //no consent set, aborting
            return;
        }

        String data = prepareCommonRequestData();

        if (consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            data += "&session_duration=" + duration;
        }

        // !!!!! THIS SHOULD ALWAYS BE ADDED AS THE LAST FIELD, OTHERWISE MERGING BREAKS !!!!!
        data += "&device_id=" + UtilsNetworking.urlEncodeString(deviceId);

        addRequestToQueue(data);
        tick();
    }

    public void tokenSession(String token, Countly.CountlyMessagingMode mode, Countly.CountlyMessagingProvider provider) {
        checkInternalState();
        L.d("[Connection Queue] tokenSession");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.push)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        final String data = prepareCommonRequestData()
            + "&token_session=1"
            + "&android_token=" + token
            + "&token_provider=" + provider
            + "&test_mode=" + (mode == Countly.CountlyMessagingMode.TEST ? 2 : 0)
            + "&locale=" + DeviceInfo.getLocale();

        L.d("[Connection Queue] Waiting for 10 seconds before adding token request to queue");

        // To ensure begin_session will be fully processed by the server before token_session
        final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        worker.schedule(new Runnable() {
            @Override
            public void run() {
                L.d("[Connection Queue] Finished waiting 10 seconds adding token request");
                addRequestToQueue(data);
                tick();
            }
        }, 10, TimeUnit.SECONDS);
    }

    /**
     * Records a session end event for the app and sends it to the server. Duration is only included in
     * the session end event if it is more than zero.
     *
     * @param duration duration in seconds to extend the current app session
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void endSession(final int duration) {
        endSession(duration, null);
    }

    public void endSession(final int duration, String deviceIdOverride) {
        checkInternalState();
        L.d("[Connection Queue] endSession");

        boolean dataAvailable = false;//will only send data if there is something valuable to send
        String data = prepareCommonRequestData();

        if (consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            data += "&end_session=1";
            if (duration > 0) {
                data += "&session_duration=" + duration;
            }
            dataAvailable = true;
        }

        if (deviceIdOverride != null && consentProvider.anyConsentGiven()) {
            //if no consent is given, device ID override is not sent
            data += "&override_id=" + UtilsNetworking.urlEncodeString(deviceIdOverride);
            dataAvailable = true;
        }

        if (dataAvailable) {
            addRequestToQueue(data);
            tick();
        }
    }

    /**
     * Send user location
     */
    public void sendLocation(boolean locationDisabled, String locationCountryCode, String locationCity, String locationGpsCoordinates, String locationIpAddress) {
        checkInternalState();
        L.d("[Connection Queue] sendLocation");

        String data = prepareCommonRequestData();

        data += prepareLocationData(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);

        addRequestToQueue(data);

        tick();
    }

    /**
     * Send user data to the server.
     *
     * @throws java.lang.IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void sendUserData(String userdata) {
        checkInternalState();
        L.d("[Connection Queue] sendUserData");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.users)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        if (userdata.equals("")) {
            L.d("[Connection Queue] No user data to send, skipping");
            return;
        }

        String data = prepareCommonRequestData() + userdata;
        addRequestToQueue(data);
        tick();
    }

    /**
     * Attribute installation to Countly server.
     *
     * @param referrer query parameters
     * @throws java.lang.IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void sendReferrerData(String referrer) {
        checkInternalState();
        L.d("[Connection Queue] sendReferrerData");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        if (referrer == null) {
            L.d("[Connection Queue] No referrer data to send, skipping");
            return;
        }

        String data = prepareCommonRequestData() + referrer;
        addRequestToQueue(data);
        tick();
    }

    public void sendReferrerDataManual(String campaignID, String userID) {
        checkInternalState();
        L.d("[Connection Queue] sendReferrerDataManual");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        String res = "";

        if (campaignID != null) {
            res += "&campaign_id=" + UtilsNetworking.urlEncodeString(campaignID);
        }
        if (userID != null) {
            res += "&campaign_user=" + UtilsNetworking.urlEncodeString(userID);
        }

        if (res.length() == 0) {
            L.w("[Connection Queue] sendReferrerDataManual, attribution not sent, both campaign ID and user ID are either null or empty");
            return;
        }

        String data = prepareCommonRequestData() + res;
        addRequestToQueue(data);

        tick();
    }

    /**
     * Reports a crash with device data to the server.
     *
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void sendCrashReport(String error, boolean nonfatal, boolean isNativeCrash, final Map<String, Object> customSegmentation) {
        checkInternalState();
        L.d("[Connection Queue] sendCrashReport");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        //limit the size of the crash report to 20k characters
        if (!isNativeCrash) {
            error = error.substring(0, Math.min(20000, error.length()));
        }

        final String data = prepareCommonRequestData()
            + "&crash=" + UtilsNetworking.urlEncodeString(CrashDetails.getCrashData(context_, error, nonfatal, isNativeCrash, customSegmentation));

        addRequestToQueue(data);

        tick();
    }

    /**
     * Records the specified events and sends them to the server.
     *
     * @param events URL-encoded JSON string of event data
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void recordEvents(final String events) {
        checkInternalState();
        L.d("[Connection Queue] sendConsentChanges");

        ////////////////////////////////////////////////////
        ///CONSENT FOR EVENTS IS CHECKED ON EVENT CREATION//
        ////////////////////////////////////////////////////

        final String data = prepareCommonRequestData()
            + "&events=" + events;

        addRequestToQueue(data);
        tick();
    }

    public void sendConsentChanges(String formattedConsentChanges) {
        checkInternalState();
        L.d("[Connection Queue] sendConsentChanges");

        final String data = prepareCommonRequestData()
            + "&consent=" + UtilsNetworking.urlEncodeString(formattedConsentChanges);

        addRequestToQueue(data);

        tick();
    }

    public void sendAPMCustomTrace(String key, Long durationMs, Long startMs, Long endMs, String customMetrics) {
        checkInternalState();

        L.d("[Connection Queue] sendAPMCustomTrace");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        // https://abc.count.ly/i?app_key=xyz&device_id=pts911
        // &apm={"type":"device","name":"forLoopProfiling_1","apm_metrics":{"duration": 10, "memory": 200}, "stz": 1584698900, "etz": 1584699900}
        // &timestamp=1584698900&count=1

        String apmData = "{\"type\":\"device\",\"name\":\"" + key + "\", \"apm_metrics\":{\"duration\": " + durationMs + customMetrics + "}, \"stz\": " + startMs + ", \"etz\": " + endMs + "}";

        final String data = prepareCommonRequestData()
            + "&count=1"
            + "&apm=" + UtilsNetworking.urlEncodeString(apmData);

        addRequestToQueue(data);

        tick();
    }

    public void sendAPMNetworkTrace(String networkTraceKey, Long responseTimeMs, int responseCode, int requestPayloadSize, int responsePayloadSize, Long startMs, Long endMs) {
        checkInternalState();

        L.d("[Connection Queue] sendAPMNetworkTrace");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        // https://abc.count.ly/i?app_key=xyz&device_id=pts911
        // &apm={"type":"network","name":"/count.ly/about","apm_metrics":{"response_time":1330,"response_payload_size":120, "response_code": 300, "request_payload_size": 70}, "stz": 1584698900, "etz": 1584699900}
        // &timestamp=1584698900&count=1

        String apmMetrics = "{\"response_time\": " + responseTimeMs + ", \"response_payload_size\":" + responsePayloadSize + ", \"response_code\":" + responseCode + ", \"request_payload_size\":" + requestPayloadSize + "}";
        String apmData = "{\"type\":\"network\",\"name\":\"" + networkTraceKey + "\", \"apm_metrics\":" + apmMetrics + ", \"stz\": " + startMs + ", \"etz\": " + endMs + "}";

        final String data = prepareCommonRequestData()
            + "&count=1"
            + "&apm=" + UtilsNetworking.urlEncodeString(apmData);

        addRequestToQueue(data);

        tick();
    }

    public void sendAPMAppStart(long durationMs, Long startMs, Long endMs) {
        checkInternalState();

        L.d("[Connection Queue] sendAPMAppStart");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }
        //https://abc.count.ly/i?app_key=xyz&device_id=pts911&apm={"type":"device","name":"app_start","apm_metrics":{"duration": 15000}, "stz": 1584698900, "etz": 1584699900}
        // &timestamp=1584698900&count=1

        String apmData = "{\"type\":\"device\",\"name\":\"app_start\", \"apm_metrics\":{\"duration\": " + durationMs + "}, \"stz\": " + startMs + ", \"etz\": " + endMs + "}";

        final String data = prepareCommonRequestData()
            + "&count=1"
            + "&apm=" + UtilsNetworking.urlEncodeString(apmData);

        addRequestToQueue(data);

        tick();
    }

    public void sendAPMScreenTime(boolean recordForegroundTime, long durationMs, Long startMs, Long endMs) {
        checkInternalState();

        L.d("[Connection Queue] sendAPMScreenTime, recording foreground time: [" + recordForegroundTime + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, consent not given");
            return;
        }

        final String eventName = recordForegroundTime ? "app_in_foreground" : "app_in_background";

        String apmData = "{\"type\":\"device\",\"name\":\"" + eventName + "\", \"apm_metrics\":{\"duration\": " + durationMs + "}, \"stz\": " + startMs + ", \"etz\": " + endMs + "}";

        final String data = prepareCommonRequestData()
            + "&count=1"
            + "&apm=" + UtilsNetworking.urlEncodeString(apmData);

        addRequestToQueue(data);

        tick();
    }

    String prepareCommonRequestData() {
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();

        return "app_key=" + UtilsNetworking.urlEncodeString(baseInfoProvider.getAppKey())
            + "&timestamp=" + instant.timestampMs
            + "&hour=" + instant.hour
            + "&dow=" + instant.dow
            + "&tz=" + DeviceInfo.getTimezoneOffset()
            + "&sdk_version=" + Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING
            + "&sdk_name=" + Countly.sharedInstance().COUNTLY_SDK_NAME;
    }

    private String prepareLocationData(boolean locationDisabled, String locationCountryCode, String locationCity, String locationGpsCoordinates, String locationIpAddress) {
        String data = "";

        if (locationDisabled || !consentProvider.getConsent(Countly.CountlyFeatureNames.location)) {
            //if location is disabled or consent not given, send empty location info
            //this way it is cleared server side and geoip is not used
            //do this only if allowed
            data += "&location=";
        } else {
            //if we get here, location consent was given
            //location should be sent, add all the fields we have

            if (locationGpsCoordinates != null && !locationGpsCoordinates.isEmpty()) {
                data += "&location=" + UtilsNetworking.urlEncodeString(locationGpsCoordinates);
            }

            if (locationCity != null && !locationCity.isEmpty()) {
                data += "&city=" + UtilsNetworking.urlEncodeString(locationCity);
            }

            if (locationCountryCode != null && !locationCountryCode.isEmpty()) {
                data += "&country_code=" + UtilsNetworking.urlEncodeString(locationCountryCode);
            }

            if (locationIpAddress != null && !locationIpAddress.isEmpty()) {
                data += "&ip=" + UtilsNetworking.urlEncodeString(locationIpAddress);
            }
        }
        return data;
    }

    public String prepareRemoteConfigRequest(String keysInclude, String keysExclude) {
        String data = prepareCommonRequestData()
            + "&method=fetch_remote_config"
            + "&device_id=" + UtilsNetworking.urlEncodeString(deviceId_.getCurrentId());

        if (consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            //add session data if consent given
            data += "&metrics=" + DeviceInfo.getMetrics(context_, metricOverride);
        }

        //add key filters
        if (keysInclude != null) {
            data += "&keys=" + UtilsNetworking.urlEncodeString(keysInclude);
        } else if (keysExclude != null) {
            data += "&omit_keys=" + UtilsNetworking.urlEncodeString(keysExclude);
        }

        return data;
    }

    public String prepareRatingWidgetRequest(String widgetId) {
        String data = prepareCommonRequestData()
            + "&widget_id=" + UtilsNetworking.urlEncodeString(widgetId)
            + "&device_id=" + UtilsNetworking.urlEncodeString(deviceId_.getCurrentId());
        return data;
    }

    public String prepareFeedbackListRequest() {
        String data = prepareCommonRequestData()
            + "&method=feedback"
            + "&device_id=" + UtilsNetworking.urlEncodeString(deviceId_.getCurrentId());

        return data;
    }

    /**
     * Ensures that an executor has been created for ConnectionProcessor instances to be submitted to.
     */
    void ensureExecutor() {
        if (executor_ == null) {
            executor_ = Executors.newSingleThreadExecutor();
        }
    }

    /**
     * Starts ConnectionProcessor instances running in the background to
     * process the local connection queue data.
     * Does nothing if there is connection queue data or if a ConnectionProcessor
     * is already running.
     */
    public void tick() {
        L.v("[Connection Queue] tick, Not empty:[" + !isRequestQueueEmpty() + "], Has processor:[" + (connectionProcessorFuture_ == null) + "], Done or null:[" + (connectionProcessorFuture_ == null
            || connectionProcessorFuture_.isDone()) + "]");

        if(!Countly.sharedInstance().isInitialized()) {
            //attempting to tick when the SDK is not initialized
            return;
        }

        if (!isRequestQueueEmpty() && (connectionProcessorFuture_ == null || connectionProcessorFuture_.isDone())) {
            ensureExecutor();
            connectionProcessorFuture_ = executor_.submit(createConnectionProcessor());
        }
    }

    public ConnectionProcessor createConnectionProcessor() {
        return new ConnectionProcessor(baseInfoProvider.getServerURL(), storageProvider, deviceId_, sslContext_, requestHeaderCustomValues, L);
    }

    public boolean queueContainsTemporaryIdItems() {
        String[] storedRequests = storageProvider.getRequests();
        String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;

        for (String storedRequest : storedRequests) {
            if (storedRequest.contains(temporaryIdTag)) {
                return true;
            }
        }

        return false;
    }

    void addRequestToQueue(String requestData) {
        storageProvider.addRequest(requestData);
    }

    /**
     * Returns true if no requests are current stored, false otherwise.
     */
    boolean isRequestQueueEmpty() {
        String rawRequestQueue = storageProvider.getRequestQueueRaw();
        if (rawRequestQueue.length() > 0) {
            return false;
        } else {
            return true;
        }
    }

    // for unit testing
    ExecutorService getExecutor() {
        return executor_;
    }

    void setExecutor(final ExecutorService executor) {
        executor_ = executor;
    }

    Future<?> getConnectionProcessorFuture() {
        return connectionProcessorFuture_;
    }

    void setConnectionProcessorFuture(final Future<?> connectionProcessorFuture) {
        connectionProcessorFuture_ = connectionProcessorFuture;
    }
}
