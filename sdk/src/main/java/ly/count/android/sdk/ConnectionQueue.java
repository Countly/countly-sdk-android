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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.json.JSONException;
import org.json.JSONObject;

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
    private DeviceIdProvider deviceIdProvider_;
    private SSLContext sslContext_;
    BaseInfoProvider baseInfoProvider;

    HealthTracker healthTracker;

    public PerformanceCounterCollector pcc;

    private Map<String, String> requestHeaderCustomValues;
    Map<String, String> metricOverride = null;

    protected ModuleLog L;
    protected ConsentProvider consentProvider;//link to the consent module
    protected ModuleRequestQueue moduleRequestQueue = null;//todo remove in the future
    protected DeviceInfo deviceInfo = null;//todo ?remove in the future?
    StorageProvider storageProvider;
    ConfigurationProvider configProvider;

    RequestInfoProvider requestInfoProvider;

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

    public void setDeviceId(DeviceIdProvider deviceIdProvider) {
        this.deviceIdProvider_ = deviceIdProvider;
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
    boolean checkInternalState() {
        //todo enable later
        //assert context_ != null;
        //assert baseInfoProvider.getAppKey() != null;
        //assert baseInfoProvider.getAppKey().length() != 0;
        //assert baseInfoProvider.getServerURL() != null;
        //assert UtilsNetworking.isValidURL(baseInfoProvider.getServerURL());
        //assert storageProvider != null;
        //assert Countly.publicKeyPinCertificates != null && baseInfoProvider.getServerURL().startsWith("https");

        if (context_ == null) {
            if (L != null) {
                L.e("[Connection Queue] context has not been set");
            }
            return false;
        }
        if (baseInfoProvider.getAppKey() == null || baseInfoProvider.getAppKey().length() == 0) {
            if (L != null) {
                L.e("[Connection Queue] app key has not been set");
            }
            return false;
        }
        if (storageProvider == null) {
            if (L != null) {
                L.e("[Connection Queue] countly storage provider has not been set");
            }
            return false;
        }
        if (baseInfoProvider.getServerURL() == null || !UtilsNetworking.isValidURL(baseInfoProvider.getServerURL())) {
            if (L != null) {
                L.e("[Connection Queue] server URL is not valid");
            }
            return false;
        }
        if (Countly.publicKeyPinCertificates != null && !baseInfoProvider.getServerURL().startsWith("https")) {
            if (L != null) {
                L.e("[Connection Queue] server must start with https once you specified public keys");
            }
            return false;
        }

        return true;
    }

    /**
     * Records a session start event for the app and sends it to the server.
     *
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void beginSession(boolean locationDisabled, @Nullable String locationCountryCode, @Nullable String locationCity, @Nullable String locationGpsCoordinates, @Nullable String locationIpAddress, @NonNull String preparedMetrics) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] beginSession");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            L.d("[Connection Queue] request ignored, 'sessions' consent not given");
            return;
        }

        String data = prepareCommonRequestData();

        //add session data if consent given
        data += "&begin_session=1"
            + "&metrics=" + preparedMetrics;//can be only sent with begin session

        String locationData = prepareLocationData(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);
        if (!locationData.isEmpty()) {
            data += locationData;
        }

        Countly.sharedInstance().isBeginSessionSent = true;

        addRequestToQueue(data, false);
        tick();
    }

    /**
     * Enrolls the user for given keys
     *
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void enrollToKeys(@NonNull String[] keys) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] enrollToKeys");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
            L.d("[Connection Queue] request ignored, 'remoteConfig' consent not given");
            return;
        }

        String data = prepareCommonRequestDataShort()
            + "&method=ab"
            + "&keys=" + UtilsNetworking.encodedArrayBuilder(keys)
            + "&new_end_point=/o/sdk";

        addRequestToQueue(data, false);
        tick();
    }

    /**
     * Exits/removes the user for given keys from A/B tests
     *
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void exitForKeys(@NonNull String[] keys) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] exitForKeys");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
            L.d("[Connection Queue] request ignored, 'remoteConfig' consent not given");
            return;
        }

        String data = prepareCommonRequestDataShort()
            + "&method=ab_opt_out";

        if (keys.length > 0) { // exits all otherwise
            data += "&keys=" + UtilsNetworking.encodedArrayBuilder(keys);
        }

        addRequestToQueue(data, false);
        tick();
    }

    /**
     * Records a session duration event for the app and sends it to the server. This method does nothing
     * if passed a negative or zero duration.
     *
     * @param duration duration in seconds to extend the current app session, should be more than zero
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void updateSession(final int duration) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] updateSession");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            L.d("[Connection Queue] request ignored, 'sessions' consent not given");
            return;
        }

        if (duration > 0) {
            String data = prepareCommonRequestData();
            data += "&session_duration=" + duration;

            addRequestToQueue(data, false);
            tick();
        }
    }

    public void changeDeviceId(String deviceId, String oldDeviceId) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] changeDeviceId");

        String data = prepareCommonRequestData(deviceId);

        data += "&old_device_id=" + UtilsNetworking.urlEncodeString(oldDeviceId);

        addRequestToQueue(data, false);
        tick();
    }

    public void tokenSession(String token, Countly.CountlyMessagingProvider provider) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] tokenSession");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.push)) {
            L.d("[Connection Queue] request ignored, 'push' consent not given");
            return;
        }

        final String data = prepareCommonRequestData()
            + "&token_session=1"
            + "&android_token=" + UtilsNetworking.urlEncodeString(token)
            + "&token_provider=" + provider
            + "&locale=" + UtilsNetworking.urlEncodeString(deviceInfo.mp.getLocale());

        L.d("[Connection Queue] Waiting for 10 seconds before adding token request to queue");

        // To ensure begin_session will be fully processed by the server before token_session
        final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        worker.schedule(new Runnable() {
            @Override
            public void run() {
                L.d("[Connection Queue] Finished waiting 10 seconds adding token request");
                addRequestToQueue(data, false);
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
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] endSession");

        String data = prepareCommonRequestData();

        data += "&end_session=1";
        if (duration > 0) {
            data += "&session_duration=" + duration;
        }

        addRequestToQueue(data, false);
        tick();
    }

    /**
     * Send user location
     */
    public void sendLocation(boolean locationDisabled, String locationCountryCode, String locationCity, String locationGpsCoordinates, String locationIpAddress) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendLocation");

        String data = prepareCommonRequestData();

        data += prepareLocationData(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);

        addRequestToQueue(data, false);

        tick();
    }

    /**
     * Send user data to the server.
     *
     * @throws java.lang.IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void sendUserData(String userdata) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendUserData");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.users)) {
            L.d("[Connection Queue] request ignored, 'users' consent not given");
            return;
        }

        String data = prepareCommonRequestData() + userdata;
        addRequestToQueue(data, false);
        tick();
    }

    public void sendIndirectAttribution(@NonNull String attributionObj) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendIndirectAttribution");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
            L.d("[Connection Queue] request ignored, 'attribution' consent not given");
            return;
        }

        if (attributionObj.isEmpty()) {
            L.e("[Connection Queue] provided attribution ID is not valid, aborting");
            return;
        }

        String param = "&aid=" + UtilsNetworking.urlEncodeString(attributionObj);

        String data = prepareCommonRequestData() + param;
        addRequestToQueue(data, false);

        tick();
    }

    public void sendDirectAttributionTest(@NonNull String attributionData) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendDirectAttributionTest");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
            L.d("[Connection Queue] request ignored, 'attribution' consent not given");
            return;
        }

        if (attributionData.isEmpty()) {
            L.w("[Connection Queue] sendDirectAttributionTest, attribution not sent, data is empty");
            return;
        }

        String res = "&attribution_data=" + UtilsNetworking.urlEncodeString(attributionData);

        String data = prepareCommonRequestData() + res;
        addRequestToQueue(data, false);

        tick();
    }

    public void sendDirectAttributionLegacy(@NonNull String campaignID, @Nullable String userID) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendDirectAttributionLegacy");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
            L.d("[Connection Queue] request ignored, 'attribution' consent not given");
            return;
        }

        String res = "";

        if (!campaignID.isEmpty()) {
            res += "&campaign_id=" + UtilsNetworking.urlEncodeString(campaignID);
        }
        if (userID != null && !userID.isEmpty()) {
            res += "&campaign_user=" + UtilsNetworking.urlEncodeString(userID);
        }

        if (res.length() == 0) {
            L.w("[Connection Queue] sendDirectAttributionLegacy, attribution not sent, both campaign ID and user ID are either null or empty");
            return;
        }

        String data = prepareCommonRequestData() + res;
        addRequestToQueue(data, false);

        tick();
    }

    /**
     * Reports a crash with device data to the server.
     *
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void sendCrashReport(@NonNull final String crashData, final boolean nonFatalCrash) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendCrashReport");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
            L.d("[Connection Queue] request ignored, 'crashes' consent not given");
            return;
        }

        final String data = prepareCommonRequestData()
            + "&crash=" + UtilsNetworking.urlEncodeString(crashData);

        //in case of a fatal crash, write it in sync to shared preferences
        addRequestToQueue(data, !nonFatalCrash);

        tick();
    }

    /**
     * Send a direct request to server
     * We have encoded each key and value as http url encoded.
     * You need to check the required consents by yourself before this call, we are just checking that if any consent is given.
     *
     * @param requestData key value pair for direct request
     */
    public void sendDirectRequest(@NonNull final Map<String, String> requestData) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendDirectRequest");

        if (!consentProvider.anyConsentGiven()) {
            L.d("[Connection Queue] request ignored, no consent given");
            return;
        }

        StringBuilder data = new StringBuilder(prepareCommonRequestData());

        data.append("&dr=1");

        for (Map.Entry<String, String> entry : requestData.entrySet()) {
            if (data.length() > 0) {
                data.append("&");
            }
            data.append(String.format("%s=%s",
                UtilsNetworking.urlEncodeString(entry.getKey()),
                UtilsNetworking.urlEncodeString(entry.getValue())
            ));
        }

        addRequestToQueue(data.toString(), false);
        tick();
    }

    /**
     * Records the specified events and sends them to the server.
     *
     * @param events URL-encoded JSON string of event data
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    public void recordEvents(final String events) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendConsentChanges");

        ////////////////////////////////////////////////////
        ///CONSENT FOR EVENTS IS CHECKED ON EVENT CREATION//
        ////////////////////////////////////////////////////

        final String data = prepareCommonRequestData()
            + "&events=" + events;

        addRequestToQueue(data, false);
        tick();
    }

    public void sendConsentChanges(String formattedConsentChanges) {
        if (!checkInternalState()) {
            return;
        }
        L.d("[Connection Queue] sendConsentChanges");

        final String data = prepareCommonRequestData()
            + "&consent=" + UtilsNetworking.urlEncodeString(formattedConsentChanges);

        addRequestToQueue(data, false);

        tick();
    }

    public void sendAPMCustomTrace(String key, Long durationMs, Long startMs, Long endMs, String customMetrics) {
        if (!checkInternalState()) {
            return;
        }

        L.d("[Connection Queue] sendAPMCustomTrace");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, 'apm' consent not given");
            return;
        }

        // https://abc.count.ly/i?app_key=xyz&device_id=pts911
        // &apm={"type":"device","name":"forLoopProfiling_1","apm_metrics":{"duration": 10, "memory": 200}, "stz": 1584698900, "etz": 1584699900}
        // &timestamp=1584698900&count=1

        String apmData = "{\"type\":\"device\",\"name\":\"" + key + "\", \"apm_metrics\":{\"duration\": " + durationMs + customMetrics + "}, \"stz\": " + startMs + ", \"etz\": " + endMs + "}";

        final String data = prepareCommonRequestData()
            + "&count=1"
            + "&apm=" + UtilsNetworking.urlEncodeString(apmData);

        addRequestToQueue(data, false);

        tick();
    }

    public void sendAPMNetworkTrace(String networkTraceKey, Long responseTimeMs, int responseCode, int requestPayloadSize, int responsePayloadSize, Long startMs, Long endMs) {
        if (!checkInternalState()) {
            return;
        }

        L.d("[Connection Queue] sendAPMNetworkTrace");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, 'apm' consent not given");
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

        addRequestToQueue(data, false);

        tick();
    }

    public void sendAPMAppStart(long durationMs, Long startMs, Long endMs) {
        if (!checkInternalState()) {
            return;
        }

        L.d("[Connection Queue] sendAPMAppStart");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, 'apm' consent not given");
            return;
        }
        //https://abc.count.ly/i?app_key=xyz&device_id=pts911&apm={"type":"device","name":"app_start","apm_metrics":{"duration": 15000}, "stz": 1584698900, "etz": 1584699900}
        // &timestamp=1584698900&count=1

        String apmData = "{\"type\":\"device\",\"name\":\"app_start\", \"apm_metrics\":{\"duration\": " + durationMs + "}, \"stz\": " + startMs + ", \"etz\": " + endMs + "}";

        final String data = prepareCommonRequestData()
            + "&count=1"
            + "&apm=" + UtilsNetworking.urlEncodeString(apmData);

        addRequestToQueue(data, false);

        tick();
    }

    public void sendAPMScreenTime(boolean recordForegroundTime, long durationMs, Long startMs, Long endMs) {
        if (!checkInternalState()) {
            return;
        }

        L.d("[Connection Queue] sendAPMScreenTime, recording foreground time: [" + recordForegroundTime + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.apm)) {
            L.d("[Connection Queue] request ignored, 'apm' consent not given");
            return;
        }

        final String eventName = recordForegroundTime ? "app_in_foreground" : "app_in_background";

        String apmData = "{\"type\":\"device\",\"name\":\"" + eventName + "\", \"apm_metrics\":{\"duration\": " + durationMs + "}, \"stz\": " + startMs + ", \"etz\": " + endMs + "}";

        final String data = prepareCommonRequestData()
            + "&count=1"
            + "&apm=" + UtilsNetworking.urlEncodeString(apmData);

        addRequestToQueue(data, false);

        tick();
    }

    @NonNull
    String prepareCommonRequestData() {
        return prepareCommonRequestData(deviceIdProvider_.getDeviceId());
    }

    @NonNull
    String prepareCommonRequestData(@NonNull String deviceId) {
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();

        return prepareCommonRequestDataShort(instant, deviceId)
            + "&hour=" + instant.hour
            + "&dow=" + instant.dow
            + "&tz=" + deviceInfo.mp.getTimezoneOffset();
    }

    @NonNull
    String prepareCommonRequestDataShort() {
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        return prepareCommonRequestDataShort(instant);
    }

    @NonNull
    String prepareCommonRequestDataShort(@NonNull UtilsTime.Instant instant) {
        return prepareCommonRequestDataShort(instant, deviceIdProvider_.getDeviceId());
    }

    @NonNull
    String prepareCommonRequestDataShort(@NonNull UtilsTime.Instant instant, @NonNull String deviceId) {
        return "app_key=" + UtilsNetworking.urlEncodeString(baseInfoProvider.getAppKey())
            + "&device_id=" + UtilsNetworking.urlEncodeString(deviceId)
            + "&timestamp=" + instant.timestampMs
            + "&sdk_version=" + Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING
            + "&sdk_name=" + Countly.sharedInstance().COUNTLY_SDK_NAME
            + "&av=" + UtilsNetworking.urlEncodeString(deviceInfo.getAppVersionWithOverride(context_, metricOverride));
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

    public String prepareRemoteConfigRequestLegacy(@Nullable String keysInclude, @Nullable String keysExclude, @NonNull String preparedMetrics) {
        String data = prepareCommonRequestData() + "&method=fetch_remote_config";
        if (consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            //add session data if consent given
            data += "&metrics=" + preparedMetrics;
        }

        //add key filters
        if (keysInclude != null) {
            data += "&keys=" + UtilsNetworking.urlEncodeString(keysInclude);
        } else if (keysExclude != null) {
            data += "&omit_keys=" + UtilsNetworking.urlEncodeString(keysExclude);
        }

        return data;
    }

    public String prepareRemoteConfigRequest(@Nullable String keysInclude, @Nullable String keysExclude, @NonNull String preparedMetrics, boolean autoEnroll) {
        String data = prepareCommonRequestData() + "&method=rc";
        if (consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            //add session data if consent given
            data += "&metrics=" + preparedMetrics;
        }

        //add key filters
        if (keysInclude != null) {
            data += "&keys=" + UtilsNetworking.urlEncodeString(keysInclude);
        } else if (keysExclude != null) {
            data += "&omit_keys=" + UtilsNetworking.urlEncodeString(keysExclude);
        }

        // if auto enroll was enabled add oi=1 to the request
        if (autoEnroll) {
            data += "&oi=1";
        }

        return data;
    }

    /**
     * To fetch all variants from the server. Something like this should be formed: method=ab_fetch_variants&app_key="APP_KEY"&device_id=DEVICE_ID
     * API end point for this is /i/sdk (also o/sdk)
     *
     * @return
     */
    public String prepareFetchAllVariants() {
        return prepareCommonRequestDataShort() + "&method=ab_fetch_variants";
    }

    /**
     * To fetch all experiments from the server. Something like this should be formed: method=ab_fetch_experiments&app_key="APP_KEY"&device_id=DEVICE_ID
     * Provides detailed experiment and variant information
     * API end point for this is /o/sdk
     *
     * @return
     */
    public String prepareFetchAllExperiments() {
        return prepareCommonRequestDataShort() + "&method=ab_fetch_experiments";
    }

    public String prepareEnrollVariant(String key, String variant) {
        String data = prepareCommonRequestDataShort()
            + "&method=ab_enroll_variant"
            + "&key=" + UtilsNetworking.urlEncodeString(key)
            + "&variant=" + UtilsNetworking.urlEncodeString(variant);

        return data;
    }

    public String prepareRatingWidgetRequest(String widgetId) {
        return prepareCommonRequestData() + "&widget_id=" + UtilsNetworking.urlEncodeString(widgetId);
    }

    public String prepareFeedbackListRequest() {
        return prepareCommonRequestData() + "&method=feedback";
    }

    public String prepareHealthCheckRequest(String preparedMetrics) {
        return prepareCommonRequestData() + "&metrics=" + preparedMetrics;
    }

    public String prepareFetchContents(int portraitWidth, int portraitHeight, int landscapeWidth, int landscapeHeight) {

        JSONObject json = new JSONObject();
        try {
            JSONObject landscapeJson = new JSONObject();
            landscapeJson.put("width", landscapeWidth);
            landscapeJson.put("height", landscapeHeight);

            JSONObject portraitJson = new JSONObject();
            portraitJson.put("width", portraitWidth);
            portraitJson.put("height", portraitHeight);

            json.put("landscape", landscapeJson);
            json.put("portrait", portraitJson);
        } catch (JSONException e) {
            L.e("Error while preparing fetch contents request");
        }

        return prepareCommonRequestData() + "&resolution=" + UtilsNetworking.urlEncodeString(json.toString());
    }

    @Override
    public String prepareServerConfigRequest() {
        return prepareCommonRequestDataShort() + "&method=sc";
    }

    /**
     * Ensures that an executor has been created for ConnectionProcessor instances to be submitted to.
     */
    void ensureExecutor() {
        if (executor_ == null) {
            if (L != null) {
                L.v("[ConnectionQueue] ensureExecutor, Creating new executor");
            }
            executor_ = Executors.newSingleThreadExecutor();
        }
    }

    /**
     * Starts ConnectionProcessor instances running in the background to
     * process the local connection queue data.
     * Does nothing if there is connection queue data or if a ConnectionProcessor
     * is already running.
     * <br>
     * Should only be called if SDK is initialized
     */
    public void tick() {
        //todo enable later
        //assert storageProvider != null;

        boolean rqEmpty = isRequestQueueEmpty(); // this is a heavy operation, do it only once. Why heavy? reading storage
        boolean cpDoneIfOngoing = connectionProcessorFuture_ != null && connectionProcessorFuture_.isDone();
        L.v("[ConnectionQueue] tick, IsRQEmpty:[" + rqEmpty + "], HasOngoingProcess:[" + (connectionProcessorFuture_ == null) + "], OngoingProcess_Done:[" + cpDoneIfOngoing + "]");

        if (!Countly.sharedInstance().isInitialized()) {
            L.e("[ConnectionQueue] tick, SDK is not initialized");
            //attempting to tick when the SDK is not initialized
            return;
        }

        if (!rqEmpty && (connectionProcessorFuture_ == null || cpDoneIfOngoing)) {
            L.d("[ConnectionQueue] tick, Starting ConnectionProcessor");
            ensureExecutor();
            connectionProcessorFuture_ = executor_.submit(createConnectionProcessor());
        }
    }

    public ConnectionProcessor createConnectionProcessor() {

        ConnectionProcessor cp = new ConnectionProcessor(baseInfoProvider.getServerURL(), storageProvider, deviceIdProvider_, configProvider, requestInfoProvider, sslContext_, requestHeaderCustomValues, L, healthTracker);
        cp.pcc = pcc;
        return cp;
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

    void addRequestToQueue(final @NonNull String requestData, final boolean writeInSync) {
        storageProvider.addRequest(requestData, writeInSync);
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
