package ly.count.android.sdk;

import org.json.JSONObject;

interface RemoteConfigDownloadCallback {
    void callback(RequestResult downloadResult, JSONObject downloadedValues);
}
