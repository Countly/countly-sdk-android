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
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
 *       of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
public class ConnectionQueue {
    private CountlyStore store_;
    private ExecutorService executor_;
    private String appKey_;
    private Context context_;
    private String serverURL_;
    private Future<?> connectionProcessorFuture_;
    private DeviceId deviceId_;
    private SSLContext sslContext_;

    private Map<String, String> requestHeaderCustomValues;

    // Getters are for unit testing
    String getAppKey() {
        return appKey_;
    }

    void setAppKey(final String appKey) {
        appKey_ = appKey;
    }

    Context getContext() {
        return context_;
    }

    void setContext(final Context context) {
        context_ = context;
    }

    String getServerURL() {
        return serverURL_;
    }

    void setServerURL(final String serverURL) {
        serverURL_ = serverURL;

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

    CountlyStore getCountlyStore() {
        return store_;
    }

    void setCountlyStore(final CountlyStore countlyStore) {
        store_ = countlyStore;
    }

    DeviceId getDeviceId() { return deviceId_; }

    public void setDeviceId(DeviceId deviceId) {
        this.deviceId_ = deviceId;
    }

    public void setRequestHeaderCustomValues(Map<String, String> headerCustomValues){
        requestHeaderCustomValues = headerCustomValues;
    }

    /**
     * Checks internal state and throws IllegalStateException if state is invalid to begin use.
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    void checkInternalState() {
        if (context_ == null) {
            throw new IllegalStateException("context has not been set");
        }
        if (appKey_ == null || appKey_.length() == 0) {
            throw new IllegalStateException("app key has not been set");
        }
        if (store_ == null) {
            throw new IllegalStateException("countly store has not been set");
        }
        if (serverURL_ == null || !Countly.isValidURL(serverURL_)) {
            throw new IllegalStateException("server URL is not valid");
        }
        if (Countly.publicKeyPinCertificates != null && !serverURL_.startsWith("https")) {
            throw new IllegalStateException("server must start with https once you specified public keys");
        }
    }

    /**
     * Records a session start event for the app and sends it to the server.
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    void beginSession() {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] beginSession");
        }

        boolean dataAvailable = false;//will only send data if there is something valuable to send
        String data = prepareCommonRequestData();

        if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.sessions)) {
            //add session data if consent given
            data += "&begin_session=1"
                    + "&metrics=" + DeviceInfo.getMetrics(context_);//can be only sent with begin session
            dataAvailable = true;
        }

        CountlyStore cs = getCountlyStore();
        String locationData = prepareLocationData(cs, true);

        if(!locationData.isEmpty()){
            data += locationData;
            dataAvailable = true;
        }

        if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.attribution)) {
            //add attribution data if consent given
            if (Countly.sharedInstance().isAttributionEnabled) {
                String cachedAdId = store_.getCachedAdvertisingId();

                if (!cachedAdId.isEmpty()) {
                    data += "&aid=" + ConnectionProcessor.urlEncodeString("{\"adid\":\"" + cachedAdId + "\"}");

                    dataAvailable = true;
                }
            }
        }

        Countly.sharedInstance().isBeginSessionSent = true;

        if(dataAvailable) {
            store_.addConnection(data);
            tick();
        }
    }

    /**
     * Records a session duration event for the app and sends it to the server. This method does nothing
     * if passed a negative or zero duration.
     * @param duration duration in seconds to extend the current app session, should be more than zero
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    void updateSession(final int duration) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] updateSession");
        }

        if (duration > 0) {
            boolean dataAvailable = false;//will only send data if there is something valuable to send
            String data = prepareCommonRequestData();

            if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.sessions)) {
                data += "&session_duration=" + duration;
                dataAvailable = true;
            }

            if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.attribution)) {
                if (Countly.sharedInstance().isAttributionEnabled) {
                    String cachedAdId = store_.getCachedAdvertisingId();

                    if (!cachedAdId.isEmpty()) {
                        data += "&aid=" + ConnectionProcessor.urlEncodeString("{\"adid\":\"" + cachedAdId + "\"}");
                        dataAvailable = true;
                    }
                }
            }

            if(dataAvailable) {
                store_.addConnection(data);
                tick();
            }
        }
    }

    public void changeDeviceId (String deviceId, final int duration) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] changeDeviceId");
        }

        if(!Countly.sharedInstance().anyConsentGiven()){
            //no consent set, aborting
            return;
        }

        String data = prepareCommonRequestData();

        if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.sessions)) {
            data += "&session_duration=" + duration;
        }

        // !!!!! THIS SHOULD ALWAYS BE ADDED AS THE LAST FIELD, OTHERWISE MERGING BREAKS !!!!!
        data += "&device_id=" + ConnectionProcessor.urlEncodeString(deviceId);

        store_.addConnection(data);
        tick();
    }

    public void tokenSession(String token, Countly.CountlyMessagingMode mode) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] tokenSession");
        }

        if(!Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.push)){
            return;
        }

        final String data = prepareCommonRequestData()
                + "&token_session=1"
                + "&android_token=" + token
                + "&test_mode=" + (mode == Countly.CountlyMessagingMode.TEST ? 2 : 0)
                + "&locale=" + DeviceInfo.getLocale();

        store_.addConnection(data);
        tick();
    }

    /**
     * Records a session end event for the app and sends it to the server. Duration is only included in
     * the session end event if it is more than zero.
     * @param duration duration in seconds to extend the current app session
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    void endSession(final int duration) {
        endSession(duration, null);
    }

    void endSession(final int duration, String deviceIdOverride) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] endSession");
        }

        boolean dataAvailable = false;//will only send data if there is something valuable to send
        String data = prepareCommonRequestData();

        if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.sessions)) {
            data += "&end_session=1";
            if (duration > 0) {
                data += "&session_duration=" + duration;
            }
            dataAvailable = true;
        }

        if (deviceIdOverride != null && Countly.sharedInstance().anyConsentGiven()) {
            //if no consent is given, device ID override is not sent
            data += "&override_id=" + ConnectionProcessor.urlEncodeString(deviceIdOverride);
            dataAvailable = true;
        }

        if(dataAvailable) {
            store_.addConnection(data);
            tick();
        }
    }

    /**
     * Send user location
     */
    void sendLocation() {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] sendLocation");
        }

        String data = prepareCommonRequestData();

        CountlyStore cs = getCountlyStore();
        data += prepareLocationData(cs, true);

        store_.addConnection(data);

        tick();
    }

    /**
     * Send user data to the server.
     * @throws java.lang.IllegalStateException if context, app key, store, or server URL have not been set
     */
    void sendUserData() {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] sendUserData");
        }

        if(!Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.users)){
            return;
        }

        String userdata = UserData.getDataForRequest();

        if(!userdata.equals("")){
            String data = prepareCommonRequestData()
                    + userdata;
            store_.addConnection(data);

            tick();
        }
    }

    /**
     * Attribute installation to Countly server.
     * @param referrer query parameters
     * @throws java.lang.IllegalStateException if context, app key, store, or server URL have not been set
     */
    void sendReferrerData(String referrer) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] checkInternalState");
        }

        if(!Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.attribution)) {
            return;
        }

        if(referrer != null){
            String data = prepareCommonRequestData()
                    + referrer;
            store_.addConnection(data);

            tick();
        }
    }

    /**
     * Reports a crash with device data to the server.
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    void sendCrashReport(String error, boolean nonfatal, boolean isNativeCrash) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] sendCrashReport");
        }

        if(!Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.crashes)){
            return;
        }

        //limit the size of the crash report to 10k characters
        if(!isNativeCrash) {
            error = error.substring(0, Math.min(10000, error.length()));
        }

        final String data = prepareCommonRequestData()
                          + "&crash=" + ConnectionProcessor.urlEncodeString(CrashDetails.getCrashData(context_, error, nonfatal, isNativeCrash));

        store_.addConnection(data);

        tick();
    }

    /**
     * Records the specified events and sends them to the server.
     * @param events URL-encoded JSON string of event data
     * @throws IllegalStateException if context, app key, store, or server URL have not been set
     */
    void recordEvents(final String events) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] sendConsentChanges");
        }

        ////////////////////////////////////////////////////
        ///CONSENT FOR EVENTS IS CHECKED ON EVENT CREATION//
        ////////////////////////////////////////////////////

        final String data = prepareCommonRequestData()
                          + "&events=" + events;

        store_.addConnection(data);
        tick();
    }

    void sendConsentChanges(String formattedConsentChanges) {
        checkInternalState();
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] sendConsentChanges");
        }

        final String data = prepareCommonRequestData()
                + "&consent=" + ConnectionProcessor.urlEncodeString(formattedConsentChanges);

        store_.addConnection(data);

        tick();
    }

    private String prepareCommonRequestData(){
        final Event.Instant instant = Countly.currentInstant();
        return "app_key=" + appKey_
                + "&timestamp=" + instant.timestamp
                + "&hour=" + instant.hour
                + "&dow=" + instant.dow
                + "&tz=" + DeviceInfo.getTimezoneOffset()
                + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                + "&sdk_name=" + Countly.COUNTLY_SDK_NAME;
    }

    private String prepareLocationData(CountlyStore cs, boolean canSendEmptyWithNoConsent){
        String data = "";

        if(canSendEmptyWithNoConsent && (cs.getLocationDisabled() || !Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.location))){
            //if location is disabled or consent not given, send empty location info
            //this way it is cleared server side and geoip is not used
            //do this only if allowed
            data += "&location=";
        } else {
            if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.location)) {
                //location should be send, add all the fields we have
                String location = cs.getLocation();
                String city = cs.getLocationCity();
                String country_code = cs.getLocationCountryCode();
                String ip = cs.getLocationIpAddress();

                if(location != null && !location.isEmpty()){
                    data += "&location=" + ConnectionProcessor.urlEncodeString(location);
                }

                if(city != null && !city.isEmpty()){
                    data += "&city=" + city;
                }

                if(country_code != null && !country_code.isEmpty()){
                    data += "&country_code=" + country_code;
                }

                if(ip != null && !ip.isEmpty()){
                    data += "&ip=" + ip;
                }
            }
        }
        return data;
    }

    protected String prepareRemoteConfigRequest(String keysInclude, String keysExclude){
        String data = prepareCommonRequestData()
                + "&method=fetch_remote_config"
                + "&device_id=" + ConnectionProcessor.urlEncodeString(deviceId_.getId());

        if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.sessions)) {
            //add session data if consent given
            data += "&metrics=" + DeviceInfo.getMetrics(context_);
        }

        CountlyStore cs = getCountlyStore();
        String locationData = prepareLocationData(cs, true);
        data += locationData;

        //add key filters
        if(keysInclude != null){
            data += "&keys=" +  ConnectionProcessor.urlEncodeString(keysInclude);
        } else if(keysExclude != null) {
            data += "&omit_keys=" + ConnectionProcessor.urlEncodeString(keysExclude);
        }

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
    void tick() {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Connection Queue] tick, [" + !store_.isEmptyConnections() + "] [" + (connectionProcessorFuture_ == null) + "] [" + (connectionProcessorFuture_ == null || connectionProcessorFuture_.isDone()) + "]");
        }

        if (!store_.isEmptyConnections() && (connectionProcessorFuture_ == null || connectionProcessorFuture_.isDone())) {
            ensureExecutor();
            connectionProcessorFuture_ = executor_.submit(createConnectionProcessor());
        }
    }

    public ConnectionProcessor createConnectionProcessor(){
        return new ConnectionProcessor(serverURL_, store_, deviceId_, sslContext_, requestHeaderCustomValues);
    }

    public boolean queueContainsTemporaryIdItems(){
        String[] storedRequests = getCountlyStore().connections();
        String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;

        for(int a = 0 ; a < storedRequests.length ; a++){
            if(storedRequests[a].contains(temporaryIdTag)){
                return true;
            }
        }

        return false;
    }

    // for unit testing
    ExecutorService getExecutor() { return executor_; }
    void setExecutor(final ExecutorService executor) { executor_ = executor; }
    Future<?> getConnectionProcessorFuture() { return connectionProcessorFuture_; }
    void setConnectionProcessorFuture(final Future<?> connectionProcessorFuture) { connectionProcessorFuture_ = connectionProcessorFuture; }

}
