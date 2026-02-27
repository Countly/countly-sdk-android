package ly.count.android.sdk;

import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
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

    @Override
    public void onPageFinished(WebView view, String url) {
        // This function is only called when the main frame is loaded.
        // However, the page might still be loading resources (images, scripts, etc.).
        // To ensure the page is fully loaded, we use JavaScript to check the document's ready state.
        Log.v(Countly.TAG, "[CountlyWebViewClient] onPageFinished, url: [" + url + "]");
        view.evaluateJavascript("(function() {" +
            "  if (document.readyState === 'complete') {" +
            "    return 'READY';" +
            "  }" +
            "  return new Promise(function(resolve) {" +
            "    window.addEventListener('load', function() {" +
            "      resolve('READY');" +
            "    });" +
            "  });" +
            "})();", result -> {
            if (result.equals("\"READY\"") && webViewClosed.compareAndSet(false, true)) {
                pageLoadTime = System.currentTimeMillis() - pageLoadTime;
                boolean timeOut = (pageLoadTime / 1000L) >= 60;
                Log.d(Countly.TAG, "[CountlyWebViewClient] onPageFinished, pageLoadTime: " + pageLoadTime + " ms");
                if (afterPageFinished != null) {
                    afterPageFinished.onPageLoaded(timeOut);
                    afterPageFinished = null;
                }
            }
        });
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

    public void registerWebViewUrlListener(WebViewUrlListener listener) {
        this.listeners.add(listener);
    }
}
