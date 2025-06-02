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
import java.nio.charset.StandardCharsets;
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
    private static final int CONNECT_TIMEOUT_IN_MILLISECONDS = 30_000;
    // used in backoff mechanism to accept half of the CONNECT_TIMEOUT_IN_MILLISECONDS
    private static final int READ_TIMEOUT_IN_MILLISECONDS = 30_000;

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
    private final Runnable backoffCallback_;

    static String endPointOverrideTag = "&new_end_point=";

    ModuleLog L;

    public PerformanceCounterCollector pcc;

    private enum RequestResult {
        OK,         // success
        RETRY       // retry MAX_RETRIES_BEFORE_SLEEP before switching to SLEEP
    }

    ConnectionProcessor(final String serverURL, final StorageProvider storageProvider, final DeviceIdProvider deviceIdProvider, final ConfigurationProvider configProvider,
        final RequestInfoProvider requestInfoProvider, final SSLContext sslContext, final Map<String, String> requestHeaderCustomValues, ModuleLog logModule,
        HealthTracker healthTracker, Runnable backoffCallback) {
        serverURL_ = serverURL;
        storageProvider_ = storageProvider;
        deviceIdProvider_ = deviceIdProvider;
        configProvider_ = configProvider;
        sslContext_ = sslContext;
        requestHeaderCustomValues_ = requestHeaderCustomValues;
        requestInfoProvider_ = requestInfoProvider;
        backoffCallback_ = backoffCallback;
        L = logModule;
        this.healthTracker = healthTracker;
    }

    synchronized public @NonNull URLConnection urlConnectionForServerRequest(@NonNull String requestData, @Nullable final String customEndpoint) throws IOException {
        String urlEndpoint = "/i";
        if (customEndpoint != null) {
            urlEndpoint = customEndpoint;
        }
        // determine whether or not request has a binary image file, if it has request will be sent as POST request
        boolean hasPicturePath = requestData.contains(ModuleUserProfile.PICTURE_PATH_KEY);
        boolean usingHttpPost = requestData.contains("&crash=") || requestData.length() >= 2048 || requestInfoProvider_.isHttpPostForced() || hasPicturePath;

        long approximateDateSize = 0L;
        String urlStr = serverURL_ + urlEndpoint;

        if (usingHttpPost) {
            // for binary images, checksum will be calculated without url encoded value of the requestData
            // because they sent as form-data and server calculates it that way
            if (!hasPicturePath) {
                String checksum = UtilsNetworking.sha256Hash(requestData + requestInfoProvider_.getRequestSalt());
                requestData += "&checksum256=" + checksum;
                L.v("[ConnectionProcessor] The following checksum was added:[" + checksum + "]");
                approximateDateSize += requestData.length(); // add request data to the estimated data size
            }
        } else {
            urlStr += "?" + requestData;
            String checksum = UtilsNetworking.sha256Hash(requestData + requestInfoProvider_.getRequestSalt());
            urlStr += "&checksum256=" + checksum;
            L.v("[ConnectionProcessor] The following checksum was added:[" + checksum + "]");
        }
        approximateDateSize += urlStr.length();

        final URL url = new URL(urlStr);
        final HttpURLConnection conn;

        long pccTsOpenURLConnection = 0L;
        long pccTsConfigureConnection = 0L;
        long pccTsStartHeaderFieldSize = 0L;
        if (pcc != null) {
            pccTsOpenURLConnection = UtilsTime.getNanoTime();
        }

        if (Countly.publicKeyPinCertificates == null && Countly.certificatePinCertificates == null) {
            conn = (HttpURLConnection) url.openConnection();
        } else {
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            c.setSSLSocketFactory(sslContext_.getSocketFactory());
            conn = c;
        }

        if (pcc != null) {
            long openUrlConnectionTime = UtilsTime.getNanoTime() - pccTsOpenURLConnection;
            pcc.TrackCounterTimeNs("ConnectionProcessorUrlConnectionForServerRequest_01_OpenURLConnection", openUrlConnectionTime);
            pccTsConfigureConnection = UtilsTime.getNanoTime();
        }

        conn.setConnectTimeout(CONNECT_TIMEOUT_IN_MILLISECONDS);
        conn.setReadTimeout(READ_TIMEOUT_IN_MILLISECONDS);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setRequestMethod("GET");

        if (requestHeaderCustomValues_ != null) {
            //if there are custom header values, add them
            L.v("[ConnectionProcessor] Adding [" + requestHeaderCustomValues_.size() + "] custom header fields");
            for (Map.Entry<String, String> entry : requestHeaderCustomValues_.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null && !key.isEmpty()) {
                    conn.addRequestProperty(key, value);
                }
            }
        }

        L.v("[ConnectionProcessor] Has picturePath [" + hasPicturePath + "]");

        if (hasPicturePath) {
            String boundary = Long.toHexString(System.currentTimeMillis());// Just generate some unique random value as the boundary
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);// Line separator required by multipart/form-data.

            OutputStream output = conn.getOutputStream(); // setup streams for form-data writing
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);

            String[] params = requestData.split("&"); // split request data by key value pairs
            for (String key : params) {
                String[] kv = key.split("=");
                approximateDateSize += 4 + boundary.length(); // 4 is the length of the static parts of the entry

                String param = kv[0];
                String value = UtilsNetworking.urlDecodeString(kv[1]);
                if (param.equals(ModuleUserProfile.PICTURE_PATH_KEY)) {
                    approximateDateSize += addFileMultipart(output, writer, value, boundary);
                }

                approximateDateSize += addTextMultipart(writer, param, value, boundary);
            }

            approximateDateSize += 4 + boundary.length(); // 4 is the length of the static parts of the entry
            approximateDateSize += addTextMultipart(writer, "checksum256", UtilsNetworking.sha256Hash(UtilsNetworking.urlDecodeString(requestData) + requestInfoProvider_.getRequestSalt()), boundary);

            // End of multipart/form-data.
            writer.append("--").append(boundary).append("--").append(CRLF).flush();
            approximateDateSize += 6 + boundary.length(); // 6 is the length of the static parts of the entry
        } else {
            if (usingHttpPost) {
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, charset));
                writer.write(requestData);
                writer.flush();
                writer.close();
                os.close();
            } else {
                L.v("[ConnectionProcessor] Using HTTP GET");
                conn.setDoOutput(false);
            }
        }

        if (pcc != null) {
            pcc.TrackCounterTimeNs("ConnectionProcessorUrlConnectionForServerRequest_02_ConfigureConnection", UtilsTime.getNanoTime() - pccTsConfigureConnection);
            pccTsStartHeaderFieldSize = UtilsTime.getNanoTime();
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
                approximateDateSize += key.getBytes(StandardCharsets.US_ASCII).length + value.getBytes(StandardCharsets.US_ASCII).length + 2L;
            }
        } catch (Exception e) {
            L.e("[Connection Processor] urlConnectionForServerRequest, exception while calculating header field size: " + e);
        }

        if (pcc != null) {
            pcc.TrackCounterTimeNs("ConnectionProcessorUrlConnectionForServerRequest_03_HeaderFieldSize", UtilsTime.getNanoTime() - pccTsStartHeaderFieldSize);
        }

        L.v("[ConnectionProcessor] Using HTTP POST: [" + usingHttpPost + "] forced:[" + requestInfoProvider_.isHttpPostForced()
            + "] length:[" + (requestData.length() >= 2048) + "] crash:[" + requestData.contains("&crash=") + "] | Approx data size: [" + approximateDateSize + " B]");
        return conn;
    }

    /**
     * Return the size of the text multipart entry
     *
     * @param writer to write to
     * @param name of the entry
     * @param value of the entry
     * @return size of the entry
     */
    int addTextMultipart(PrintWriter writer, final String name, final String value, final String boundary) {
        writer.append("--").append(boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(CRLF);
        writer.append(CRLF).append(value).append(CRLF).flush();
        return 49 + boundary.length() + name.length() + value.length(); // 45 is the length of the static parts of the entry
    }

    /**
     * Return the size of the file multipart entry
     *
     * @param output stream to write to
     * @param writer to write to
     * @param filePath of the file
     * @return size of the entry
     * @throws IOException if there is an error while reading the file
     */
    int addFileMultipart(OutputStream output, PrintWriter writer, final String filePath, final String boundary) throws IOException {
        if (Utils.isNullOrEmpty(filePath)) {
            return 0;
        }
        writer.append("--").append(boundary).append(CRLF);
        File file = new File(filePath);
        String contentType = URLConnection.guessContentTypeFromName(file.getName());
        int approximateDataSize = 0;

        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"").append(CRLF);
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
        return 82 + boundary.length() + approximateDataSize + file.getName().length() + contentType.length(); // 78 is the length of the static parts of the entry
    }

    @Override
    public void run() {
        long wholeQueueStart = UtilsTime.getNanoTime();
        while (true) {
            long pccTsStartWholeQueue = 0L;
            long pccTsStartOnlyInternet = 0L;
            long pccTsStartTempIdCheck = 0L;
            long pccTsStartEndpointCheck = 0L;
            long pccTsStartOldRCheck = 0L;
            long pccTsStartGetURLConnection;
            long pccTsStartRemainingRequests = 0L;
            long pccTsReadingStream;
            long pccTsStartHandlingResponse;

            if (!configProvider_.getNetworkingEnabled()) {
                L.w("[ConnectionProcessor] run, Networking config is disabled, request queue skipped");
                break;
            }

            //------------------------
            // get stored requests
            final String[] storedRequests = storageProvider_.getRequests();
            int storedRequestCount = storedRequests == null ? 0 : storedRequests.length;

            String msg = "[Connection Processor] Starting to run, there are [" + storedRequestCount + "] requests stored";
            if (storedRequestCount == 0) {
                L.v(msg);
            } else {
                L.i(msg);
            }

            if (storedRequests == null || storedRequestCount == 0) {
                L.i("[ConnectionProcessor] No requests in the queue, request queue skipped");
                // currently no data to send, we are done for now
                break;
            }

            if (deviceIdProvider_.getDeviceId() == null) {
                // This might not be the case anymore, check it out TODO
                L.i("[ConnectionProcessor] No Device ID available yet, skipping request " + storedRequests[0]);
                break;
            }

            // get first request in a separate variable to modify and keep the original intact
            final String originalRequest = storedRequests[0];
            String requestData = originalRequest;//todo rework to another param approach

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_01_GetRequest", UtilsTime.getNanoTime() - pccTsStartWholeQueue);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartOldRCheck = UtilsTime.getNanoTime();
            }

            L.i("[ConnectionProcessor] Checking if the request is older than:[" + requestInfoProvider_.getRequestDropAgeHours() + "] hours");
            boolean isRequestOld = Utils.isRequestTooOld(requestData, requestInfoProvider_.getRequestDropAgeHours(), "[ConnectionProcessor]", L);

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_02_NetworkOldReq", UtilsTime.getNanoTime() - pccTsStartOldRCheck);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartTempIdCheck = UtilsTime.getNanoTime();
            }
            // temp ID checks
            String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;
            boolean containsTemporaryId = requestData.contains(temporaryIdTag);
            if (containsTemporaryId || deviceIdProvider_.isTemporaryIdEnabled()) {
                //we are about to change ID to the temporary one or
                //the internally set id is the temporary one

                //abort and wait for exiting temporary mode
                L.i("[ConnectionProcessor] Temporary ID detected, stalling requests. tmp id tag:[" + containsTemporaryId + "], temp ID set:[" + deviceIdProvider_.isTemporaryIdEnabled() + "]");
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
            String[] extractionResult = Utils.extractValueFromString(requestData, endPointOverrideTag, "&");
            if (extractionResult[1] != null) {
                requestData = extractionResult[0];

                if (!extractionResult[1].isEmpty()) {
                    customEndpoint = extractionResult[1];
                }
                L.v("[ConnectionProcessor] Custom end point detected for the request:[" + customEndpoint + "]");
            }

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_04_NetworkCustomEndpoint", UtilsTime.getNanoTime() - pccTsStartEndpointCheck);
            }

            //------------------------

            if (pcc != null) {
                pccTsStartRemainingRequests = UtilsTime.getNanoTime();
            }

            // add the remaining request count
            requestData = requestData + "&rr=" + (storedRequestCount - 1);

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_06_remainingRequests", UtilsTime.getNanoTime() - pccTsStartRemainingRequests);
            }

            //------------------------

            if (!(requestInfoProvider_.isDeviceAppCrawler() && requestInfoProvider_.ifShouldIgnoreCrawlers()) && !isRequestOld) {
                //continue with sending the request to the server
                URLConnection conn = null;
                InputStream connInputStream = null;
                try {
                    pccTsStartGetURLConnection = UtilsTime.getNanoTime();

                    // initialize and open connection
                    conn = urlConnectionForServerRequest(requestData, customEndpoint);
                    long setupServerRequestTime = UtilsTime.getNanoTime() - pccTsStartGetURLConnection;
                    L.d("[ConnectionProcessor] run, TIMING Setup server request took:[" + setupServerRequestTime / 1000000.0d + "] ms");

                    if (pcc != null) {
                        pcc.TrackCounterTimeNs("ConnectionProcessorRun_07_SetupServerRequest", setupServerRequestTime);
                        pccTsStartOnlyInternet = UtilsTime.getNanoTime();
                    }
                    conn.connect();

                    if (pcc != null) {
                        pcc.TrackCounterTimeNs("ConnectionProcessorRun_08_NetworkOnlyInternet", UtilsTime.getNanoTime() - pccTsStartOnlyInternet);
                    }

                    pccTsStartHandlingResponse = UtilsTime.getNanoTime();
                    pccTsReadingStream = UtilsTime.getNanoTime();

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

                    long readingStreamTime = UtilsTime.getNanoTime() - pccTsReadingStream;
                    L.d("[Connection Processor] code:[" + responseCode + "], response:[" + responseString + "], response size:[" + responseString.length() + " B], request: " + requestData + ", url: " + serverURL_ + ", Reading stream took:[" + readingStreamTime / 1000000.0d + "] ms");

                    if (pcc != null) {
                        pcc.TrackCounterTimeNs("ConnectionProcessorRun_13_ReadingStream", readingStreamTime);
                    }

                    final RequestResult rRes;

                    if (responseCode >= 200 && responseCode < 300) {

                        if (responseString.isEmpty()) {
                            L.v("[ConnectionProcessor] Response was empty, will retry");
                            rRes = RequestResult.RETRY;
                        } else {
                            JSONObject jsonObject;
                            try {
                                jsonObject = new JSONObject(responseString);
                            } catch (JSONException ex) {
                                //failed to parse, so not a valid json
                                jsonObject = null;
                                L.e("[ConnectionProcessor] Failed to parse response [" + responseString + "].");
                            }

                            if (jsonObject == null) {
                                //received unparseable response, retrying
                                L.v("[ConnectionProcessor] Response was a unknown, will retry");
                                rRes = RequestResult.RETRY;
                            } else {
                                if (jsonObject.has("result")) {
                                    //contains result entry
                                    L.v("[ConnectionProcessor] Response was a success");
                                    rRes = RequestResult.OK;
                                } else {
                                    L.v("[ConnectionProcessor] Response does not contain 'result', will retry");
                                    rRes = RequestResult.RETRY;
                                }
                            }
                        }
                    } else if (responseCode >= 300 && responseCode < 400) {
                        //assume redirect
                        L.d("[ConnectionProcessor] Encountered redirect, will retry");
                        rRes = RequestResult.RETRY;
                    } else if (responseCode == 400 || responseCode == 404) {
                        L.w("[ConnectionProcessor] Bad request, will still retry");
                        rRes = RequestResult.RETRY;
                    } else if (responseCode > 400) {
                        //server down, try again later
                        L.d("[ConnectionProcessor] Server is down, will retry");
                        rRes = RequestResult.RETRY;
                    } else {
                        L.d("[ConnectionProcessor] Bad response code, will retry");
                        rRes = RequestResult.RETRY;
                    }

                    // an 'if' needs to be used here so that a 'switch' statement does not 'eat' the 'break' call
                    // that is used to get out of the request loop
                    if (rRes == RequestResult.OK) {
                        // successfully submitted event data to Count.ly server, so remove
                        // this one from the stored events collection
                        storageProvider_.removeRequest(originalRequest);

                        if (configProvider_.getBOMEnabled() && backoff(setupServerRequestTime, storedRequestCount, requestData)) {
                            backoffCallback_.run();
                            break;
                        }
                    } else {
                        // will retry later
                        // warning was logged above, stop processing, let next tick take care of retrying
                        healthTracker.logFailedNetworkRequest(responseCode, responseString);//notify the health tracker of the issue
                        healthTracker.saveState();

                        if (pcc != null) {
                            pcc.TrackCounterTimeNs("ConnectionProcessorRun_12_FailedRequest", UtilsTime.getNanoTime() - pccTsStartWholeQueue);
                        }

                        break;
                    }
                } catch (Exception e) {
                    L.d("[ConnectionProcessor] Got exception while trying to submit request data: [" + requestData + "] [" + e + "]");
                    // if exception occurred, stop processing, let next tick take care of retrying
                    if (pcc != null) {
                        pcc.TrackCounterTimeNs("ConnectionProcessorRun_11_NetworkWholeQueueException", UtilsTime.getNanoTime() - pccTsStartWholeQueue);
                    }
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
                long handlingResponseTime = UtilsTime.getNanoTime() - pccTsStartHandlingResponse;
                L.d("[ConnectionProcessor] run, TIMING Handling response took:[" + handlingResponseTime / 1000000.0d + "] ms");
                if (pcc != null) {
                    pcc.TrackCounterTimeNs("ConnectionProcessorRun_09_HandlingResponse", handlingResponseTime);
                }
            } else {
                //device is identified as a app crawler and nothing is sent to the server
                if (isRequestOld) {
                    L.i("[ConnectionProcessor] request is too old, removing request " + originalRequest);
                } else {
                    L.i("[ConnectionProcessor] Device identified as an app crawler, removing request " + originalRequest);
                }

                //remove stored data
                storageProvider_.removeRequest(originalRequest);
            }

            if (pcc != null) {
                pcc.TrackCounterTimeNs("ConnectionProcessorRun_10_NetworkWholeQueue", UtilsTime.getNanoTime() - pccTsStartWholeQueue);
            }
        }
        long wholeQueueTime = UtilsTime.getNanoTime() - wholeQueueStart;
        L.v("[ConnectionProcessor] run, TIMING Whole queue took:[" + wholeQueueTime / 1000000.0d + "] ms");
    }

    /**
     * Backoff mechanism to prevent flooding the server with requests when server is not able to respond
     * Needs 3 conditions to met:
     * - Request has a timestamp younger than 12 hrs
     * - The number of requests inside the queue is less than 10% of the max queue size
     * - The response time from the server is greater than or equal to ACCEPTED_TIMEOUT_SECONDS
     *
     * @param responseTimeMillis response time  in milliseconds
     * @param storedRequestCount number of requests in the queue
     * @param requestData request data
     * @return true if the backoff mechanism is triggered
     */
    private boolean backoff(long responseTimeMillis, int storedRequestCount, String requestData) {
        long responseTimeSeconds = responseTimeMillis / 1_000_000_000L;
        boolean result = false;

        if (responseTimeSeconds >= configProvider_.getBOMAcceptedTimeoutSeconds()) {
            // FLAG 1
            if (storedRequestCount <= storageProvider_.getMaxRequestQueueSize() * configProvider_.getBOMRQPercentage()) {
                // FLAG 2
                if (!Utils.isRequestTooOld(requestData, configProvider_.getBOMRequestAge(), "[ConnectionProcessor] backoff", L)) {
                    // FLAG 3
                    result = true;
                    healthTracker.logBackoffRequest();
                }
            }
        }

        return result;
    }

    String getServerURL() {
        return serverURL_;
    }

    // for unit testing
    StorageProvider getCountlyStore() {
        return storageProvider_;
    }
}
