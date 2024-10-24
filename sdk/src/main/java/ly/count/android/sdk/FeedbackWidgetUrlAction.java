package ly.count.android.sdk;

import android.util.Log;
import android.webkit.WebView;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

class FeedbackWidgetUrlAction implements WebViewUrlListener {
    Runnable closeAction;

    protected FeedbackWidgetUrlAction(Runnable closeAction) {
        this.closeAction = closeAction;
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

        Object clyEvent = query.get("?cly_widget_command");

        if (clyEvent == null || !clyEvent.equals("1")) {
            Log.w(Countly.TAG, "[TransparentActivity] contentUrlAction, event:[" + clyEvent + "] this is not a countly action event url");
            return false;
        }

        if (query.containsKey("close") && Objects.equals(query.get("close"), "1")) {
            closeAction.run();
            view.callback.closeActivity();
            return true;
        }

        return false;
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
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }

        return query_pairs;
    }
}
