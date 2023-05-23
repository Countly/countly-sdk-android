package ly.count.android.sdk;

import org.json.JSONObject;

interface RemoteConfigDownloadCallback {
    void callback(RCDownloadResult downloadResult, JSONObject downloadedValues);
}
