package ly.count.android.sdk;

import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    WebViewPageLoadedListener afterPageFinished;
    long pageLoadTime;
    private final AtomicBoolean webViewClosed = new AtomicBoolean(false);

    private static final Set<String> CRITICAL_RESOURCES = new HashSet<>(Arrays.asList(
        "js", "css", "png", "jpg", "jpeg", "webp"
    ));

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

    private static final long POLL_INTERVAL_MS = 100;
    private static final long TIMEOUT_MS = 60_000;

    // Checks all <link rel="stylesheet"> and <script src> elements have loaded.
    // CSS: .sheet is non-null when ready. Cross-origin sheets may throw SecurityError — treated as loaded.
    // JS: checks Performance API for completed resource entries matching each script's src.
    private static final String CHECK_CSS_JS_LOADED =
        "(function(){"
            + "var links=document.querySelectorAll('link[rel=\"stylesheet\"]');"
            + "for(var i=0;i<links.length;i++){"
            + "try{if(!links[i].sheet)return 'LOADING';}catch(e){}"
            + "}"
            + "var scripts=document.querySelectorAll('script[src]');"
            + "for(var i=0;i<scripts.length;i++){"
            + "try{if(performance.getEntriesByName(scripts[i].src).length===0)return 'LOADING';}catch(e){}"
            + "}"
            + "return 'READY';"
            + "})()";

    @Override
    public void onPageFinished(WebView view, String url) {
        Log.v(Countly.TAG, "[CountlyWebViewClient] onPageFinished, url: [" + url + "]");
        pollForCriticalResources(view);
    }

    private void pollForCriticalResources(WebView view) {
        if (webViewClosed.get()) {
            return;
        }

        view.evaluateJavascript(CHECK_CSS_JS_LOADED, result -> {
            if (webViewClosed.get()) {
                return;
            }

            if ("\"READY\"".equals(result)) {
                notifyPageLoaded(false);
            } else {
                long elapsed = System.currentTimeMillis() - pageLoadTime;
                if (elapsed >= TIMEOUT_MS) {
                    Log.w(Countly.TAG, "[CountlyWebViewClient] pollForCriticalResources, timed out waiting for CSS/JS");
                    notifyPageLoaded(true);
                } else {
                    pollHandler.postDelayed(() -> pollForCriticalResources(view), POLL_INTERVAL_MS);
                }
            }
        });
    }

    private void notifyPageLoaded(boolean timedOut) {
        if (webViewClosed.compareAndSet(false, true)) {
            pageLoadTime = System.currentTimeMillis() - pageLoadTime;
            Log.d(Countly.TAG, "[CountlyWebViewClient] notifyPageLoaded, pageLoadTime: " + pageLoadTime + " ms, timedOut: " + timedOut);
            if (afterPageFinished != null) {
                afterPageFinished.onPageLoaded(timedOut);
                afterPageFinished = null;
            }
        }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if ((request.isForMainFrame() || isCriticalResource(request.getUrl())) && webViewClosed.compareAndSet(false, true)) {
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
        if ((request.isForMainFrame() || isCriticalResource(request.getUrl())) && webViewClosed.compareAndSet(false, true)) {
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

    void cancel() {
        webViewClosed.set(true);
        pollHandler.removeCallbacksAndMessages(null);
        afterPageFinished = null;
        listeners.clear();
    }

    public void registerWebViewUrlListener(WebViewUrlListener listener) {
        this.listeners.add(listener);
    }
}
