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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ConnectionProcessor is a Runnable that is executed on a background
 * thread to submit session &amp; event data to a Count.ly server.
 *
 * NOTE: This class is only public to facilitate unit testing, because
 * of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
public class ConnectionProcessor implements Runnable {
    private static final int CONNECT_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final int READ_TIMEOUT_IN_MILLISECONDS = 30000;

    private static final String CRLF = "\r\n";
    private static final String charset = "UTF-8";

    private final StorageProvider storageProvider_;
    private final DeviceIdProvider deviceIdProvider_;
    final ConfigurationProvider configProvider_;

    HealthTracker healthTracker;

    final RequestInfoProvider requestInfoProvider_;
    private final String serverURL_;
    private final SSLContext sslContext_;

    private final Map<String, String> requestHeaderCustomValues_;

    static String endPointOverrideTag = "&new_end_point=";

    ModuleLog L;

    public PerformanceCounterCollector pcc;

    private enum RequestResult {
        OK,         // success
        RETRY       // retry MAX_RETRIES_BEFORE_SLEEP before switching to SLEEP
    }

    ConnectionProcessor(final String serverURL, final StorageProvider storageProvider, final DeviceIdProvider deviceIdProvider, final ConfigurationProvider configProvider,
        final RequestInfoProvider requestInfoProvider, final SSLContext sslContext, final Map<String, String> requestHeaderCustomValues, ModuleLog logModule,
        HealthTracker healthTracker) {
        serverURL_ = serverURL;
        storageProvider_ = storageProvider;
        deviceIdProvider_ = deviceIdProvider;
        configProvider_ = configProvider;
        sslContext_ = sslContext;
        requestHeaderCustomValues_ = requestHeaderCustomValues;
        requestInfoProvider_ = requestInfoProvider;
        L = logModule;
        this.healthTracker = healthTracker;
    }

    synchronized public @NonNull URLConnection urlConnectionForServerRequest(@NonNull String requestData, @Nullable final String customEndpoint) throws IOException {
        String urlEndpoint = "/i";
        if (customEndpoint != null) {
            urlEndpoint = customEndpoint;
        }

        // determine whether or not request has a binary image file, if it has request will be sent as POST request
        boolean hasPicturePath = hasPicturePath(requestData);
        boolean usingHttpPost = (requestData.contains("&crash=") || requestData.length() >= 2048 || requestInfoProvider_.isHttpPostForced()) || hasPicturePath;

        long approximateDateSize = 0L;
        String urlStr = serverURL_ + urlEndpoint;
        approximateDateSize += urlStr.length();

        if (usingHttpPost && !hasPicturePath) { 
            // for binary images, checksum will be calculated without url encoded value of the requestData
            // because they sent as form-data and server calculates it that way
            requestData = addChecksum(requestData, requestData);
        } else {
            urlStr += "?" + requestData;
            urlStr = addChecksum(urlStr, requestData);
        }

        approximateDateSize += requestData.length(); // add request data to the estimated data size

        final URL url = new URL(urlStr);
        final HttpURLConnection conn;
        if (Countly.publicKeyPinCertificates == null && Countly.certificatePinCertificates == null) {
            conn = (HttpURLConnection) url.openConnection();
        } else {
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            c.setSSLSocketFactory(sslContext_.getSocketFactory());
            conn = c;
        }

        if (requestHeaderCustomValues_ != null) {
            //if there are custom header values, add them
            L.v("[Connection Processor] Adding [" + requestHeaderCustomValues_.size() + "] custom header fields");
            for (Map.Entry<String, String> entry : requestHeaderCustomValues_.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null && !key.isEmpty()) {
                    conn.addRequestProperty(key, value);
                }
            }
        }

        if (hasPicturePath) {
            requestData = addChecksum(requestData, UtilsNetworking.urlDecodeString(requestData)); // add checksum with url decoded version of request data
            L.v("[Connection Processor] Has picturePath,  if (hasPicturePath(requestData))");
            String boundary = Long.toHexString(System.currentTimeMillis());
            setupConnection(conn, usingHttpPost, "multipart/form-data; boundary=" + boundary); // setup url connection by POST and content-type

            OutputStream output = conn.getOutputStream(); // setup streams for form-data writing
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);

            // Send binary file.
            String[] map = requestData.split("&"); // split request data by key value pairs
            for (String key : map) {
                String[] kv = key.split("=");
                String value = UtilsNetworking.urlDecodeString(kv[1]);
                if (kv[0].equals(ModuleUserProfile.PICTURE_PATH_KEY)) {
                    File binaryFile = new File(value); // add picture to the form-data
                    approximateDateSize += addMultipart(output, writer, boundary, URLConnection.guessContentTypeFromName(binaryFile.getName()), "file", binaryFile.getName(), binaryFile);
                } else {  // ad key value pair as form-data entry and add decoded value of it, estimated data size not added because it is already added above in requestData.lenghth()
                    addMultipart(output, writer, boundary, "text/plain", kv[0], UtilsNetworking.urlDecodeString(kv[1]), null);
                }
            }

            // End of multipart/form-data.
            writer.append("--").append(boundary).append("--").append(CRLF).flush();
        } else {
            if (usingHttpPost) { 
                // setup connection for post
                setupConnection(conn, true, "application/x-www-form-urlencoded");

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, charset));
                writer.write(requestData);
                writer.flush();
                writer.close();
                os.close();
            } else {
                // setup connection for get
                setupConnection(conn, false, null);
            }
        }

        //calculating header field size
        try {
            //just after init, because of background operations, this might fail
            // HttpUrl::Builder of okhttp might give null pointer error because it may not be initialized yet
            int headerIndex = 0;

            while (true) {
                String key = conn.getHeaderFieldKey(headerIndex);
                if (key == null) {
                    break;
                }
                String value = conn.getHeaderField(headerIndex++);
                approximateDateSize += key.getBytes("US-ASCII").length + value.getBytes("US-ASCII").length + 2L;
            }
        } catch (Exception e) {
            L.e("[ConnectionProcessor] urlConnectionForServerRequest, exception while calculating header field size: " + e);
        }

        L.v("[Connection Processor] Using HTTP POST: [" + usingHttpPost + "] forced:[" + requestInfoProvider_.isHttpPostForced()
            + "] length:[" + (requestData.length() >= 2048) + "] crash:[" + requestData.contains("&crash=") + "] | Approx data size: [" + approximateDateSize + " B]");
        return conn;
    }

    boolean hasPicturePath(String requestData) {
        return requestData.contains(ModuleUserProfile.PICTURE_PATH_KEY);
    }

    String addChecksum(String gonnaAdd, String gonnaCalculate) {
        String checksum = UtilsNetworking.sha256Hash(gonnaCalculate + requestInfoProvider_.getRequestSalt());
        gonnaAdd += "&checksum256=" + checksum;
        L.v("[Connection Processor] The following checksum was added:[" + checksum + "]");

        return gonnaAdd;
    }

    /**
    * Setup connection for HTTP method, first 4 option is same for every method.
    * Only method and doOutput changes, if it is POST request contentType is also added
    */
    void setupConnection(HttpURLConnection conn, boolean usingHttpPost, String contentType) throws IOException {
        conn.setConnectTimeout(CONNECT_TIMEOUT_IN_MILLISECONDS);
        conn.setReadTimeout(READ_TIMEOUT_IN_MILLISECONDS);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        if (usingHttpPost) {
            L.v("[Connection Processor] Using HTTP POST");
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", contentType);
        } else {
            L.v("[Connection Processor] Using HTTP GET");
            conn.setDoOutput(false);
            conn.setRequestMethod("GET");
        }
    }

    /**
    * Adds a form-data entry to the stream
    * @return estimated data size for the file entries
    */
    int addMultipart(OutputStream output, PrintWriter writer, final String boundary, final String contentType, final String name, final String value, final File file) throws IOException {
        int approximateDataSize = 0;
        writer.append("--").append(boundary).append(CRLF);
        if (file != null) {
            // entry for file
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"; filename=\"").append(value).append("\"").append(CRLF);
            writer.append("Content-Type: ").append(contentType).append(CRLF);
            writer.append(CRLF).flush();
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                // write file to the buffer and stream
                byte[] buffer = new byte[1024];
                int len;
                try {
                    while ((len = fileInputStream.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                        approximateDataSize += len;
                    }
                } catch (IOException ex) {
                    for (StackTraceElement e : ex.getStackTrace()) {
                        L.e("[ConnectionProcessor] addMultipart, error: " + e);
                    }
                }
            }
            output.flush();
            writer.append(CRLF).flush();
        } else {
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(CRLF);
            writer.append(CRLF).append(value).append(CRLF).flush();
        }

        return approximateDataSize;
    }

    @Override
    public void run() {
        while (true) {
            Long pccTsStartWholeQueue = 0L;
            Long pccTsStartOnlyInternet = 0L;
            Long pccTsStartTempIdCheck = 0L;
            Long pccTsStartEndpointCheck = 0L;
            Long pccTsStartOldRCheck = 0L;
            Long pccTsStartGetURLConnection = 0L;
            Long pccTsStartDeviceIDOverride = 0L;
            Long pccTsStartRemainingRequests = 0L;
            Long pccTsStartHandlingResponse = 0L;

            if (!configProvider_.getNetworkingEnabled()) {
                L.w("[Connection Processor] run, Networking config is disabled, request queue skipped");
                break;
            }

            //------------------------

            if (pcc != null) {
                pccTsStartWholeQueue = UtilsTime.getNanoTime();
            }

            // get stored requests
            final String[] storedRequests = storageProvider_.getRequests();
            int storedRequestCount = storedRequests == null ? 0 : storedRequests.length;

            if (L.logEnabled()) {
                String msg = "[Connection Processor] Starting to run, there are [" + storedRequestCount + "] requests stored";
                if (storedRequestCount == 0) {
                    L.v(msg);
                } else {
                    L.i(msg);
                }
            }

            if (storedRequests == null || storedRequestCount == 0) {
                L.i("[Connection Processor] No requests in the queue, request queue skipped");
                // currently no data to send, we are done for now
                break;
            }

            if (deviceIdProvider_.getDeviceId() == null) {
                // When device ID is supplied by OpenUDID or by Google Advertising ID.
                // In some cases it might take time for them to initialize. So, just wait for it.
                L.i("[Connection Processor] No Device ID available yet, skipping request " + storedRequests[0]);
                break;
            }

            // get first request in a separate variable to modify and keep the original intact
            String eventData = storedRequests[0];//todo rework to another param approach

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_01_GetRequest", UtilsTime.getNanoTime() - pccTsStartWholeQueue);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartOldRCheck = UtilsTime.getNanoTime();
            }

            L.i("[Connection Processor] Checking if the request is older than:[" + requestInfoProvider_.getRequestDropAgeHours() + "] hours");
            boolean isRequestOld = Utils.isRequestTooOld(eventData, requestInfoProvider_.getRequestDropAgeHours(), "[Connection Processor]", L);

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_02_NetworkOldReq", UtilsTime.getNanoTime() - pccTsStartOldRCheck);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartTempIdCheck = UtilsTime.getNanoTime();
            }
            // temp ID checks
            String temporaryIdOverrideTag = "&override_id=" + DeviceId.temporaryCountlyDeviceId;
            String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;
            boolean containsTemporaryIdOverride = eventData.contains(temporaryIdOverrideTag);
            boolean containsTemporaryId = eventData.contains(temporaryIdTag);
            if (containsTemporaryIdOverride || containsTemporaryId || deviceIdProvider_.isTemporaryIdEnabled()) {
                //we are about to change ID to the temporary one or
                //the internally set id is the temporary one

                //abort and wait for exiting temporary mode
                L.i("[Connection Processor] Temporary ID detected, stalling requests. Id override:[" + containsTemporaryIdOverride + "], tmp id tag:[" + containsTemporaryId + "], temp ID set:[" + deviceIdProvider_.isTemporaryIdEnabled() + "]");
                break;
            }
            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_03_NetworkTempID", UtilsTime.getNanoTime() - pccTsStartTempIdCheck);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartEndpointCheck = UtilsTime.getNanoTime();
            }

            String customEndpoint = null;

            // checks if endPointOverrideTag exists in the eventData, and if so, extracts the endpoint and removes the tag from the evenData
            String[] extractionResult = Utils.extractValueFromString(eventData, endPointOverrideTag, "&");
            if (extractionResult[1] != null) {
                eventData = extractionResult[0];

                if (!extractionResult[1].equals("")) {
                    customEndpoint = extractionResult[1];
                }
                L.v("[Connection Processor] Custom end point detected for the request:[" + customEndpoint + "]");
            }

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_04_NetworkCustomEndpoint", UtilsTime.getNanoTime() - pccTsStartEndpointCheck);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartDeviceIDOverride = UtilsTime.getNanoTime();
            }

            //add the device_id to the created request
            boolean deviceIdOverride = eventData.contains("&override_id="); //if the sendable data contains a override tag
            boolean deviceIdChange = eventData.contains("&device_id="); //if the sendable data contains a device_id tag. In this case it means that we will have to change the stored device ID

            final String newId;

            if (deviceIdOverride) {
                // if the override tag is used, it means that the device_id will be changed
                // to finish the session of the previous device_id, we have cache it into the request
                // this is indicated by having the "override_id" tag. This just means that we
                // don't use the id provided in the deviceId variable as this might have changed already.

                eventData = eventData.replace("&override_id=", "&device_id=");
                newId = null;
            } else {
                if (deviceIdChange) {
                    // this branch will be used if a new device_id is provided
                    // and a device_id merge on server has to be performed

                    final int endOfDeviceIdTag = eventData.indexOf("&device_id=") + "&device_id=".length();
                    newId = UtilsNetworking.urlDecodeString(eventData.substring(endOfDeviceIdTag));

                    if (newId.equals(deviceIdProvider_.getDeviceId())) {
                        // If the new device_id is the same as previous,
                        // we don't do anything to change it

                        deviceIdChange = false;

                        L.d("[Connection Processor] Provided device_id is the same as the previous one used, nothing will be merged");
                    } else {
                        //new device_id provided, make sure it will be merged
                        eventData = eventData + "&old_device_id=" + UtilsNetworking.urlEncodeString(deviceIdProvider_.getDeviceId());
                    }
                } else {
                    // this branch will be used in almost all requests.
                    // This just adds the device_id to them

                    newId = null;
                    eventData = eventData + "&device_id=" + UtilsNetworking.urlEncodeString(deviceIdProvider_.getDeviceId());
                }
            }

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_05_NetworkCustomEndpoint", UtilsTime.getNanoTime() - pccTsStartDeviceIDOverride);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartRemainingRequests = UtilsTime.getNanoTime();
            }

            // add the remaining request count
            eventData = eventData + "&rr=" + (storedRequestCount - 1);

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_06_remainingRequests", UtilsTime.getNanoTime() - pccTsStartRemainingRequests);
            }

            //------------------------

            if (!(requestInfoProvider_.isDeviceAppCrawler() && requestInfoProvider_.ifShouldIgnoreCrawlers()) && !isRequestOld) {
                //continue with sending the request to the server
                URLConnection conn = null;
                InputStream connInputStream = null;
                try {
                    if (pcc != null) {
                        pccTsStartGetURLConnection = UtilsTime.getNanoTime();
                    }

                    // initialize and open connection
                    conn = urlConnectionForServerRequest(eventData, customEndpoint);
                    if (pcc != null) {
                        pcc.TrackCounterTimeNs("ConnectionProcessorRun_07_SetupServerRequest", UtilsTime.getNanoTime() - pccTsStartGetURLConnection);
                        pccTsStartOnlyInternet = UtilsTime.getNanoTime();
                    }
                    conn.connect();

                    if (pcc != null) {
                        pcc.TrackCounterTimeNs("ConnectionProcessorRun_08_NetworkOnlyInternet", UtilsTime.getNanoTime() - pccTsStartOnlyInternet);
                        pccTsStartHandlingResponse = UtilsTime.getNanoTime();
                    }

                    int responseCode = 0;
                    String responseString = "";
                    if (conn instanceof HttpURLConnection) {
                        final HttpURLConnection httpConn = (HttpURLConnection) conn;

                        try {
                            //assume there will be no error
                            connInputStream = httpConn.getInputStream();
                        } catch (Exception ex) {
                            //in case of exception, assume there was a error in the request and change streams
                            connInputStream = httpConn.getErrorStream();
                        }

                        responseCode = httpConn.getResponseCode();
                        responseString = Utils.inputStreamToString(connInputStream);
                    }

                    L.d("[Connection Processor] code:[" + responseCode + "], response:[" + responseString + "], response size:[" + responseString.length() + " B], request: " + eventData + ", url: " + serverURL_);

                    final RequestResult rRes;

                    if (responseCode >= 200 && responseCode < 300) {

                        if (responseString.isEmpty()) {
                            L.v("[Connection Processor] Response was empty, will retry");
                            rRes = RequestResult.RETRY;
                        } else {
                            JSONObject jsonObject;
                            try {
                                jsonObject = new JSONObject(responseString);
                            } catch (JSONException ex) {
                                //failed to parse, so not a valid json
                                jsonObject = null;
                                L.e("[Connection Processor] Failed to parse response [" + responseString + "].");
                            }

                            if (jsonObject == null) {
                                //received unparseable response, retrying
                                L.v("[Connection Processor] Response was a unknown, will retry");
                                rRes = RequestResult.RETRY;
                            } else {
                                if (jsonObject.has("result")) {
                                    //contains result entry
                                    L.v("[Connection Processor] Response was a success");
                                    rRes = RequestResult.OK;
                                } else {
                                    L.v("[Connection Processor] Response does not contain 'result', will retry");
                                    rRes = RequestResult.RETRY;
                                }
                            }
                        }
                    } else if (responseCode >= 300 && responseCode < 400) {
                        //assume redirect
                        L.d("[Connection Processor] Encountered redirect, will retry");
                        rRes = RequestResult.RETRY;
                    } else if (responseCode == 400 || responseCode == 404) {
                        L.w("[Connection Processor] Bad request, will still retry");
                        rRes = RequestResult.RETRY;
                    } else if (responseCode > 400) {
                        //server down, try again later
                        L.d("[Connection Processor] Server is down, will retry");
                        rRes = RequestResult.RETRY;
                    } else {
                        L.d("[Connection Processor] Bad response code, will retry");
                        rRes = RequestResult.RETRY;
                    }

                    // an 'if' needs to be used here so that a 'switch' statement does not 'eat' the 'break' call
                    // that is used to get out of the request loop
                    if (rRes == RequestResult.OK) {
                        // successfully submitted event data to Count.ly server, so remove
                        // this one from the stored events collection
                        storageProvider_.removeRequest(storedRequests[0]);

                        if (deviceIdChange) {
                            if (newId != null && !newId.isEmpty()) {
                                deviceIdProvider_.getDeviceIdInstance().changeToCustomId(newId);//todo needs to be refactored
                            } else {
                                L.e("[Connection Processor] Failed to change device ID with merging because the new ID was empty or null. [" + newId + "]");
                            }
                        }

                        if (deviceIdChange || deviceIdOverride) {
                            L.v("[Connection Processor] Device ID changed, change:[" + deviceIdChange + "] | override:[" + deviceIdOverride + "]");
                            Countly.sharedInstance().notifyDeviceIdChange();//todo needs to be removed at some point
                        }
                    } else {
                        // will retry later
                        // warning was logged above, stop processing, let next tick take care of retrying
                        healthTracker.logFailedNetworkRequest(responseCode, responseString);//notify the health tracker of the issue
                        break;
                    }
                } catch (Exception e) {
                    L.d("[Connection Processor] Got exception while trying to submit request data: [" + eventData + "] [" + e + "]");
                    // if exception occurred, stop processing, let next tick take care of retrying
                    break;
                } finally {
                    // free connection resources
                    if (conn instanceof HttpURLConnection) {
                        try {
                            if (connInputStream != null) {
                                connInputStream.close();
                            }
                        } catch (Throwable ignored) {
                        }

                        ((HttpURLConnection) conn).disconnect();
                    }
                }
                if (pcc != null) {
                    pcc.TrackCounterTimeNs("ConnectionProcessorRun_09_HandlingResponse", UtilsTime.getNanoTime() - pccTsStartHandlingResponse);
                }
            } else {
                //device is identified as a app crawler and nothing is sent to the server
                if (isRequestOld) {
                    L.i("[Connection Processor] request is too old, removing request " + storedRequests[0]);
                } else {
                    L.i("[Connection Processor] Device identified as an app crawler, removing request " + storedRequests[0]);
                }

                //remove stored data
                storageProvider_.removeRequest(storedRequests[0]);
            }

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_10_NetworkWholeQueue", UtilsTime.getNanoTime() - pccTsStartWholeQueue);
            }
        }
    }

    String getServerURL() {
        return serverURL_;
    }

    // for unit testing
    StorageProvider getCountlyStore() {
        return storageProvider_;
    }
}
