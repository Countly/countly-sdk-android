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

    // Scheme policy for sub-resources: empty/null -> default denylist; non-empty -> allow-list mode.
    private final Set<String> allowedSchemes;

    public CountlyWebViewClient() {
        this(null);
    }

    public CountlyWebViewClient(Set<String> allowedSchemes) {
        super();
        this.listeners = new ArrayList<>();
        this.pageLoadTime = System.currentTimeMillis();
        this.allowedSchemes = allowedSchemes;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        Log.v(Countly.TAG, "[CountlyWebViewClient] shouldOverrideUrlLoading, url: [" + url + "]");

        // Percent-decode with Uri.decode, NOT URLDecoder: URLDecoder applies form semantics and turns
        // a literal '+' into a space, which corrupts links and deeplinks (e.g. "tel:+1..." or a base64
        // query value). Uri.decode decodes %XX, leaves '+' intact, and is lenient on a malformed
        // escape (so the action is not dropped) - matching the iOS behavior.
        String decoded = Uri.decode(url);
        if (decoded != null) {
            url = decoded;
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
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri url = request == null ? null : request.getUrl();
        String scheme = url == null ? null : url.getScheme();
        // Sub-resources (images, frames, scripts) follow the shared scheme policy: https always loads
        // because it serves the content itself, and the dangerous schemes (file, content, javascript,
        // jar, zip, intent, data) are blocked. When an allow-list is configured, only those schemes
        // (plus https) load. A null scheme (e.g. a malformed request) is blocked, fail-secure.
        if (!Utils.isWebContentSchemeAllowed(scheme, allowedSchemes)) {
            Log.v(Countly.TAG, "[CountlyWebViewClient] shouldInterceptRequest, blocked sub-resource with disallowed scheme: [" + url + "]");
            return Utils.blankWebResourceResponse();
        }
        return null;
    }

    private static final long POLL_INTERVAL_MS = 100;
    private static final long TIMEOUT_MS = 60_000;

    // Readiness gate: all stylesheets loaded, and all scripts THIS browser actually fetches have a timing
    // entry. Skips scripts the browser won't fetch — [nomodule] on module-capable WebViews, type=module
    // on legacy ones — which have no entry and would otherwise hang the gate. Waits for CSS/JS, not images.
    private static final String CHECK_CSS_JS_READY =
        "(function(){"
            + "var pending=[];"
            + "var links=document.querySelectorAll('link[rel=\"stylesheet\"]');"
            + "for(var i=0;i<links.length;i++){"
            + "try{if(!links[i].sheet)pending.push('css:'+links[i].href);}catch(e){}"
            + "}"
            + "var mod='noModule' in HTMLScriptElement.prototype;"
            + "var scripts=document.querySelectorAll('script[src]');"
            + "for(var i=0;i<scripts.length;i++){"
            + "var s=scripts[i];"
            + "if(mod&&s.hasAttribute('nomodule'))continue;"
            + "if(!mod&&s.type==='module')continue;"
            + "try{if(performance.getEntriesByName(s.src).length===0)pending.push('js:'+s.src);}catch(e){}"
            + "}"
            + "return pending.length===0?'READY':('LOADING '+pending.join(' | '));"
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

        view.evaluateJavascript(CHECK_CSS_JS_READY, result -> {
            if (webViewClosed.get()) {
                return;
            }

            if ("\"READY\"".equals(result)) {
                notifyPageLoaded(false);
            } else {
                long elapsed = System.currentTimeMillis() - pageLoadTime;
                if (elapsed >= TIMEOUT_MS) {
                    // Fail-open: show anyway rather than discard a possibly-rendered widget. Only hard
                    // errors (SSL / main-frame / critical sub-resource) close the overlay.
                    Log.w(Countly.TAG, "[CountlyWebViewClient] pollForCriticalResources, timed out; showing content anyway (fail-open). state: " + result);
                    notifyPageLoaded(false);
                } else {
                    pollHandler.postDelayed(() -> pollForCriticalResources(view), POLL_INTERVAL_MS);
                }
            }
        });
    }

    private void notifyPageLoaded(boolean failed) {
        if (webViewClosed.compareAndSet(false, true)) {
            pageLoadTime = System.currentTimeMillis() - pageLoadTime;
            Log.d(Countly.TAG, "[CountlyWebViewClient] notifyPageLoaded, pageLoadTime: " + pageLoadTime + " ms, failed: " + failed);
            if (afterPageFinished != null) {
                afterPageFinished.onPageLoaded(failed);
                afterPageFinished = null;
            }
        }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        String errorString;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            errorString = error.getDescription() + " (code: " + error.getErrorCode() + ")";
        } else {
            errorString = error.toString();
        }
        boolean mainFrame = request.isForMainFrame();
        boolean critical = isCriticalResource(request.getUrl());
        // Log every failure to help trace a blank widget; only main-frame/critical failures close it.
        Log.w(Countly.TAG, "[CountlyWebViewClient] onReceivedError, [" + (mainFrame ? "main" : "sub") + (critical ? "/critical" : "") + "] url=[" + request.getUrl() + "] error=[" + errorString + "]");

        if ((mainFrame || critical) && webViewClosed.compareAndSet(false, true)) {
            if (afterPageFinished != null) {
                afterPageFinished.onPageLoaded(true);
                afterPageFinished = null;
            }
        }
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        boolean mainFrame = request.isForMainFrame();
        boolean critical = isCriticalResource(request.getUrl());
        Log.w(Countly.TAG, "[CountlyWebViewClient] onReceivedHttpError, [" + (mainFrame ? "main" : "sub") + (critical ? "/critical" : "") + "] url=[" + request.getUrl() + "] status=[" + errorResponse.getStatusCode() + "]");

        if ((mainFrame || critical) && webViewClosed.compareAndSet(false, true)) {
            if (afterPageFinished != null) {
                afterPageFinished.onPageLoaded(true);
                afterPageFinished = null;
            }
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.w(Countly.TAG, "[CountlyWebViewClient] onReceivedSslError, url:[" + error.getUrl() + "] primaryError=[" + error.getPrimaryError() + "]");

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
