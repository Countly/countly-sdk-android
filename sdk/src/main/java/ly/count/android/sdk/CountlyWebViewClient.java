package ly.count.android.sdk;

import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.ArrayList;
import java.util.List;

class CountlyWebViewClient extends WebViewClient {
    private final List<WebViewUrlListener> listeners;

    public CountlyWebViewClient() {
        super();
        this.listeners = new ArrayList<>();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        for (WebViewUrlListener listener : listeners) {
            if (listener.onUrl(url, view)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) { return null ; }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        Log.d(Countly.TAG, "[WebClient] Intercepted request URL: [" + url + "]");

        // Call listeners for specific actions
        for (WebViewUrlListener listener : listeners) {
            boolean handled = listener.onUrl(url, view);
            if (handled) {
                Log.d(Countly.TAG, "Request handled by listener: " + url);
                break;
            }
        }

        return super.shouldInterceptRequest(view, request);
    }

    public void registerWebViewUrlListeners(List<WebViewUrlListener> listener) {
        this.listeners.addAll(listener);
    }
}
