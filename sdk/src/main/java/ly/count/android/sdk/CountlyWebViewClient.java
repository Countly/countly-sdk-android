package ly.count.android.sdk;

import android.os.Build;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

class CountlyWebViewClient extends WebViewClient {
    private final List<WebViewUrlListener> listeners;
    WebViewPageLoadedListener afterPageFinished;
    long pageLoadTime;

    public CountlyWebViewClient() {
        super();
        this.listeners = new ArrayList<>();
        this.pageLoadTime = System.currentTimeMillis();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        Log.v(Countly.TAG, "[CountlyWebViewClient] shouldOverrideUrlLoading, url: [" + url + "]");
        try {
            url = URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            Log.e(Countly.TAG, "[CountlyWebViewClient] shouldOverrideUrlLoading, Failed to decode url", e);
            return false;
        }

        Log.d(Countly.TAG, "[CountlyWebViewClient] shouldOverrideUrlLoading, urlDecoded: [" + url + "]");

        for (WebViewUrlListener listener : listeners) {
            if (listener.onUrl(url, view)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        Log.v(Countly.TAG, "[CountlyWebViewClient] onPageFinished, url: [" + url + "]");
        if (afterPageFinished != null) {
            pageLoadTime = System.currentTimeMillis() - pageLoadTime;
            boolean timeOut = (pageLoadTime / 1000L) >= 60;
            Log.d(Countly.TAG, "[CountlyWebViewClient] onPageFinished, pageLoadTime: " + pageLoadTime + " ms");

            afterPageFinished.onPageLoaded(timeOut);
            afterPageFinished = null;
        }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request.isForMainFrame() && afterPageFinished != null) {
            String errorString;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                errorString = error.getDescription() + " (code: " + error.getErrorCode() + ")";
            } else {
                errorString = error.toString();
            }
            Log.v(Countly.TAG, "[CountlyWebViewClient] onReceivedError, error: [" + errorString + "]");

            afterPageFinished.onPageLoaded(true);
            afterPageFinished = null;
        }
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        if (request.isForMainFrame() && afterPageFinished != null) {
            Log.v(Countly.TAG, "[CountlyWebViewClient] onReceivedHttpError, errorResponseCode: [" + errorResponse.getStatusCode() + "]");
            afterPageFinished.onPageLoaded(true);
            afterPageFinished = null;
        }
    }

    public void registerWebViewUrlListener(WebViewUrlListener listener) {
        this.listeners.add(listener);
    }
}
