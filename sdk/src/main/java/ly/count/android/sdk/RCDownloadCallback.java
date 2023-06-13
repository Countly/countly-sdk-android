package ly.count.android.sdk;

import java.util.Map;
import org.json.JSONObject;

public interface RCDownloadCallback {
    void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, Map<String, RCData> downloadedValues);
}
