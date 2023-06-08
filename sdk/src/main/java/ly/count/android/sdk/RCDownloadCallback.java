package ly.count.android.sdk;

import java.util.Map;
import org.json.JSONObject;

interface RCDownloadCallback {
    void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, Map<String, Object> downloadedValues);
}
