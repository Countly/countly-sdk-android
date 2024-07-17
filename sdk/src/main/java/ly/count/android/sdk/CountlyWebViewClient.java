package ly.count.android.sdk;

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
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return null;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return null;
    }

    public void registerWebViewUrlListeners(List<WebViewUrlListener> listener) {
        this.listeners.addAll(listener);
    }
}
