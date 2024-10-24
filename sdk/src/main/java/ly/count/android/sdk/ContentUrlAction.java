package ly.count.android.sdk;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class ContentUrlAction implements WebViewUrlListener {
    private final ContentCallback contentCallback;

    protected ContentUrlAction(ContentCallback contentCallback) {
        this.contentCallback = contentCallback;
    }

    @Override
    public boolean onUrl(String url, WebView webView) {
        if (!(webView instanceof CountlyWebView)) {
            return false;
        }

        if (url.startsWith(URL_START)) {
            CountlyWebView countlyWebView = (CountlyWebView) webView;
            return contentUrlAction(url, countlyWebView);
        }
        return false;
    }

    private boolean contentUrlAction(String url, CountlyWebView view) {
        Log.d(Countly.TAG, "[TransparentActivity] contentUrlAction, url: [" + url + "]");
        Map<String, Object> query = splitQuery(url);
        Log.v(Countly.TAG, "[TransparentActivity] contentUrlAction, query: [" + query + "]");

        Object clyEvent = query.get("?cly_x_action_event");

        if (clyEvent == null || !clyEvent.equals("1")) {
            Log.w(Countly.TAG, "[TransparentActivity] contentUrlAction, event:[" + clyEvent + "] this is not a countly action event url");
            return false;
        }

        Object clyAction = query.get("action");
        boolean result = false;
        if (clyAction instanceof String) {
            Log.d(Countly.TAG, "[TransparentActivity] contentUrlAction, action string:[" + clyAction + "]");
            String action = (String) clyAction;

            switch (action) {
                case "event":
                    eventAction(query);
                    break;
                case "link":
                    linkAction(query, view);
                    break;
                case "resize_me":
                    resizeMeAction(query, view);
                    break;
                default:
                    break;
            }
        }

        if (query.containsKey("close") && Objects.equals(query.get("close"), "1")) {
            if (contentCallback != null) { // TODO: verify this later
                contentCallback.onContentCallback(ContentStatus.CLOSED, query);
            }
            ModuleContent.waitForDelay = 2; // this is indicating that we will wait 1 min after closing the content and before fetching the next one
            view.callback.closeActivity();
            return true;
        }

        return result;
    }

    private boolean linkAction(Map<String, Object> query, WebView view) {
        Log.i(Countly.TAG, "[TransparentActivity] linkAction, link action detected");
        if (!query.containsKey("link")) {
            Log.w(Countly.TAG, "[TransparentActivity] linkAction, link action is missing link");
            return false;
        }
        Object link = query.get("link");
        if (!(link instanceof String)) {
            Log.w(Countly.TAG, "[TransparentActivity] linkAction, link action is not a string");
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.toString()));
        view.getContext().startActivity(intent);
        return true;
    }

    private void resizeMeAction(Map<String, Object> query, CountlyWebView webView) {
        Log.i(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me action detected");
        if (!query.containsKey("resize_me")) {
            Log.w(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me action is missing resize_me");
            return;
        }
        Object resizeMe = query.get("resize_me");
        if (!(resizeMe instanceof JSONObject)) {
            Log.w(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me action is not a JSON object");
            return;
        }

        JSONObject resizeMeJson = (JSONObject) resizeMe;
        Log.v(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me JSON: [" + resizeMeJson + "]");

        webView.callback.resizeActivity(resizeMeJson);
    }

    private void eventAction(Map<String, Object> query) {
        Log.i(Countly.TAG, "[TransparentActivity] eventAction, event action detected");
        if (query.containsKey("event")) {
            JSONArray event = (JSONArray) query.get("event");
            assert event != null; // this is already checked above
            for (int i = 0; i < Objects.requireNonNull(event).length(); i++) {
                try {
                    JSONObject eventJson = event.getJSONObject(i);
                    Log.v(Countly.TAG, "[TransparentActivity] eventAction, event JSON: [" + eventJson.toString() + "]");

                    if (!eventJson.has("sg")) {
                        Log.w(Countly.TAG, "[TransparentActivity] eventAction, event JSON is missing segmentation data event: [" + eventJson + "]");
                        continue;
                    }

                    Map<String, Object> segmentation = new ConcurrentHashMap<>();
                    JSONObject segmentationJson = eventJson.getJSONObject("sg");
                    assert segmentationJson != null; // this is already checked above

                    for (int j = 0; j < segmentationJson.names().length(); j++) {
                        String key = segmentationJson.names().getString(j);
                        Object value = segmentationJson.get(key);
                        segmentation.put(key, value);
                    }

                    Countly.sharedInstance().events().recordEvent(eventJson.get("key").toString(), segmentation);
                } catch (JSONException e) {
                    Log.e(Countly.TAG, "[TransparentActivity] eventAction, Failed to parse event JSON", e);
                }
            }

            Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
        } else {
            Log.w(Countly.TAG, "[TransparentActivity] eventAction, event action is missing event");
        }
    }

    private Map<String, Object> splitQuery(String url) {
        Map<String, Object> query_pairs = new ConcurrentHashMap<>();
        String[] pairs = url.split(URL_START);
        if (pairs.length != 2) {
            return query_pairs;
        }

        String[] pairs2 = pairs[1].split("&");
        for (String pair : pairs2) {
            int idx = pair.indexOf('=');
            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);

            try {
                if ("event".equals(key)) {
                    query_pairs.put(key, new JSONArray(value));
                } else if ("resize_me".equals(key)) {
                    query_pairs.put(key, new JSONObject(value));
                }
            } catch (JSONException e) {
                Log.e(Countly.TAG, "[TransparentActivity] splitQuery, Failed to parse event JSON", e);
            }
            query_pairs.put(key, value);
        }

        return query_pairs;
    }
}
