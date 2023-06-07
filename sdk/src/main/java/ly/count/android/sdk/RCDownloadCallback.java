package ly.count.android.sdk;

import org.json.JSONObject;

interface RCDownloadCallback {
    void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, JSONObject downloadedValues);
}
