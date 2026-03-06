package ly.count.android.sdk;

import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class CountlyWebViewClient extends WebViewClient {
    private final List<WebViewUrlListener> listeners;
    WebViewPageLoadedListener afterPageFinished;
    long pageLoadTime;
    private final AtomicBoolean webViewClosed = new AtomicBoolean(false);
    private final AtomicBoolean pageFinishedCalled = new AtomicBoolean(false);
    private boolean jsBridgeAdded = false;

    private static final Set<String> CRITICAL_RESOURCES = new HashSet<>(Arrays.asList(
        "js", "css"
    ));

    private static final String JS_BRIDGE_NAME = "CountlyPageReady";

    public CountlyWebViewClient() {
        super();
        this.listeners = new ArrayList<>();
        this.pageLoadTime = System.currentTimeMillis();
    }

    private class PageReadyBridge {
        @JavascriptInterface
        public void onReady() {
            new Handler(Looper.getMainLooper()).post(() -> notifyPageReady());
        }
    }

    private void notifyPageReady() {
        if (webViewClosed.compareAndSet(false, true)) {
            pageLoadTime = System.currentTimeMillis() - pageLoadTime;
            boolean timeOut = (pageLoadTime / 1000L) >= 60;
            Log.d(Countly.TAG, "[CountlyWebViewClient] page ready, pageLoadTime: " + pageLoadTime + " ms");
            if (afterPageFinished != null) {
                afterPageFinished.onPageLoaded(timeOut);
                afterPageFinished = null;
            }
        }
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
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Log.v(Countly.TAG, "[CountlyWebViewClient] onPageStarted, url: [" + url + "]");
        webViewClosed.set(false);
        pageFinishedCalled.set(false);
        pageLoadTime = System.currentTimeMillis();

        if (!jsBridgeAdded) {
            view.addJavascriptInterface(new PageReadyBridge(), JS_BRIDGE_NAME);
            jsBridgeAdded = true;
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        // onPageFinished fires when the main frame is loaded, but resources may still be loading.
        // We use a JavascriptInterface bridge to get a reliable callback when the document is fully ready.
        Log.v(Countly.TAG, "[CountlyWebViewClient] onPageFinished, url: [" + url + "]");
        pageFinishedCalled.set(true);

        if (!jsBridgeAdded) {
            view.addJavascriptInterface(new PageReadyBridge(), JS_BRIDGE_NAME);
            jsBridgeAdded = true;
        }

        view.evaluateJavascript(
            "(function() {" +
            "  if (document.readyState === 'complete') {" +
            "    " + JS_BRIDGE_NAME + ".onReady();" +
            "  } else {" +
            "    window.addEventListener('load', function() {" +
            "      " + JS_BRIDGE_NAME + ".onReady();" +
            "    });" +
            "  }" +
            "})();", null);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        boolean shouldAbort;
        if (request.isForMainFrame()) {
            shouldAbort = true;
        } else {
            shouldAbort = isCriticalResource(request.getUrl()) && !pageFinishedCalled.get();
        }

        if (shouldAbort && webViewClosed.compareAndSet(false, true)) {
            String errorString;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                errorString = error.getDescription() + " (code: " + error.getErrorCode() + ")";
            } else {
                errorString = error.toString();
            }
            Log.v(Countly.TAG, "[CountlyWebViewClient] onReceivedError, error: [" + errorString + "]");

            if (afterPageFinished != null) {
                afterPageFinished.onPageLoaded(true);
                afterPageFinished = null;
            }
        }
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        boolean shouldAbort;
        if (request.isForMainFrame()) {
            shouldAbort = true;
        } else {
            shouldAbort = isCriticalResource(request.getUrl()) && !pageFinishedCalled.get();
        }

        if (shouldAbort && webViewClosed.compareAndSet(false, true)) {
            Log.v(Countly.TAG, "[CountlyWebViewClient] onReceivedHttpError, url: [" + request.getUrl() + "], errorResponseCode: [" + errorResponse.getStatusCode() + "]");
            if (afterPageFinished != null) {
                afterPageFinished.onPageLoaded(true);
                afterPageFinished = null;
            }
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.v(Countly.TAG, "[CountlyWebViewClient] onReceivedSslError, SSL error. url:[" + error.getUrl() + "]");

        if (webViewClosed.compareAndSet(false, true) && afterPageFinished != null) {
            afterPageFinished.onPageLoaded(true);
            afterPageFinished = null;
        }

        handler.cancel();
    }

    private boolean isCriticalResource(Uri uri) {
        String path = uri.getPath();
        if (path == null) {
            return false;
        }

        int dot = path.lastIndexOf('.');
        if (dot == -1) {
            return false;
        }

        String ext = path.substring(dot + 1).toLowerCase();
        return CRITICAL_RESOURCES.contains(ext);
    }

    public void registerWebViewUrlListener(WebViewUrlListener listener) {
        this.listeners.add(listener);
    }
}
