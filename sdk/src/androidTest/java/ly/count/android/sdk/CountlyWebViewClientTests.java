package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CountlyWebViewClientTests {

    private CountlyWebViewClient client;
    private final List<Boolean> callbackResults = new ArrayList<>();
    private WebView webView;

    @Before
    public void setUp() {
        client = new CountlyWebViewClient();
        callbackResults.clear();
        client.afterPageFinished = callbackResults::add;
    }

    @After
    public void tearDown() {
        if (webView != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                webView.destroy();
                webView = null;
            });
        }
    }

    // =====================================
    // Helper methods
    // =====================================

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        final WebView[] holder = new WebView[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            holder[0] = new WebView(ApplicationProvider.getApplicationContext());
            holder[0].getSettings().setJavaScriptEnabled(true);
        });
        webView = holder[0];
        return webView;
    }

    private void runOnMainSync(Runnable r) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }

    private WebResourceRequest fakeRequest(String url, boolean isForMainFrame) {
        Uri uri = Uri.parse(url);
        return new WebResourceRequest() {
            @Override public Uri getUrl() {
                return uri;
            }

            @Override public boolean isForMainFrame() {
                return isForMainFrame;
            }

            @Override public boolean isRedirect() {
                return false;
            }

            @Override public boolean hasGesture() {
                return false;
            }

            @Override public String getMethod() {
                return "GET";
            }

            @Override public Map<String, String> getRequestHeaders() {
                return new HashMap<>();
            }
        };
    }

    private WebResourceResponse fakeHttpErrorResponse(int statusCode) {
        return new WebResourceResponse("text/html", "utf-8", null) {
            @Override public int getStatusCode() {
                return statusCode;
            }
        };
    }

    // =====================================
    // shouldOverrideUrlLoading - URL decoding + listener delivery
    // =====================================

    /**
     * The URL is percent-decoded once before being handed to listeners, so encoded delimiters in
     * an "event" JSON value (e.g. "%26" -> "&", "%5B" -> "[") arrive in plain form for parsing.
     */
    @Test
    public void shouldOverrideUrlLoading_decodesUrlForListener() {
        final String[] received = new String[1];
        client.registerWebViewUrlListener((url, view) -> {
            received[0] = url;
            return true;
        });

        String encoded = Utils.COMM_URL + "/?cly_x_action_event=1&action=event&event=%5B%7B%22k%22%3A%22a%26b%22%7D%5D";
        String decoded = Utils.COMM_URL + "/?cly_x_action_event=1&action=event&event=[{\"k\":\"a&b\"}]";
        Assert.assertTrue(client.shouldOverrideUrlLoading(null, fakeRequest(encoded, true)));
        Assert.assertEquals("listener must receive the decoded URL", decoded, received[0]);
    }

    /**
     * A malformed percent-escape (possible inside an unencoded link) must not drop the action: the
     * listener still receives the URL (raw fallback) rather than the call returning false silently.
     */
    @Test
    public void shouldOverrideUrlLoading_malformedEscape_fallsBackToRaw() {
        final String[] received = new String[1];
        client.registerWebViewUrlListener((url, view) -> {
            received[0] = url;
            return true;
        });

        String raw = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=https://x.com?d=50%off";
        Assert.assertTrue(client.shouldOverrideUrlLoading(null, fakeRequest(raw, true)));
        Assert.assertNotNull("listener must still be invoked on malformed escape", received[0]);
    }

    // =====================================
    // onReceivedHttpError - abort logic
    // =====================================

    /**
     * "onReceivedHttpError" with main frame error
     * should abort and fire callback with failed=true
     */
    @Test
    public void onReceivedHttpError_mainFrame_abortsPage() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com", true), fakeHttpErrorResponse(404));
        Assert.assertEquals(1, callbackResults.size());
        Assert.assertTrue(callbackResults.get(0));
    }

    /**
     * "onReceivedHttpError" with critical sub-resource error (js, css, png, jpg, jpeg, webp)
     * should abort immediately and fire callback with failed=true
     */
    @Test
    public void onReceivedHttpError_criticalSubResource_abortsImmediately() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com/app.js", false), fakeHttpErrorResponse(404));
        Assert.assertEquals(1, callbackResults.size());
        Assert.assertTrue(callbackResults.get(0));
    }

    /**
     * "onReceivedHttpError" with non-critical sub-resource (no matching extension)
     * should not abort
     */
    @Test
    public void onReceivedHttpError_nonCriticalSubResource_doesNotAbort() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com/api/data", false), fakeHttpErrorResponse(500));
        Assert.assertEquals(0, callbackResults.size());
    }

    // =====================================
    // Single-fire guarantee
    // =====================================

    /**
     * "onReceivedHttpError" called twice (main frame + critical sub-resource)
     * should fire callback only once
     */
    @Test
    public void singleFire_multipleErrors_onlyFirstFires() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com", true), fakeHttpErrorResponse(404));
        client.onReceivedHttpError(null, fakeRequest("https://example.com/app.js", false), fakeHttpErrorResponse(500));

        Assert.assertEquals(1, callbackResults.size());
        Assert.assertTrue(callbackResults.get(0));
    }

    // =====================================
    // Null listener safety
    // =====================================

    /**
     * "onReceivedHttpError" with null afterPageFinished listener
     * should not crash
     */
    @Test
    public void onReceivedHttpError_nullListener_noCrash() {
        client.afterPageFinished = null;
        client.onReceivedHttpError(null, fakeRequest("https://example.com", true), fakeHttpErrorResponse(404));
        Assert.assertEquals(0, callbackResults.size());
    }

    // =====================================
    // onPageFinished callback behavior
    // =====================================

    /**
     * "onPageFinished" should fire callback via evaluateJavascript with failed=false
     * when page loads within timeout
     */
    @Test
    public void onPageFinished_firesCallback() throws InterruptedException {
        WebView wv = createWebView();
        CountDownLatch latch = new CountDownLatch(1);
        client.afterPageFinished = (failed) -> {
            callbackResults.add(failed);
            latch.countDown();
        };
        runOnMainSync(() -> {
            client.onPageFinished(wv, "https://example.com");
        });
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));

        Assert.assertEquals(1, callbackResults.size());
        Assert.assertFalse(callbackResults.get(0));
    }

    /**
     * "onPageFinished" called multiple times
     * should fire callback only once
     */
    @Test
    public void onPageFinished_firesOnlyOnce() throws InterruptedException {
        WebView wv = createWebView();
        CountDownLatch latch = new CountDownLatch(1);
        client.afterPageFinished = (failed) -> {
            callbackResults.add(failed);
            latch.countDown();
        };
        runOnMainSync(() -> {
            client.onPageFinished(wv, "https://example.com");
        });
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Second call - callback should not fire again (webViewClosed is true)
        runOnMainSync(() -> {
            client.onPageFinished(wv, "https://example.com");
        });
        Thread.sleep(500);

        Assert.assertEquals(1, callbackResults.size());
    }

    /**
     * "onPageFinished" callback followed by main frame error
     * should fire callback only once via onPageFinished
     */
    @Test
    public void onPageFinished_thenError_onlyOneFires() throws InterruptedException {
        WebView wv = createWebView();
        CountDownLatch latch = new CountDownLatch(1);
        client.afterPageFinished = (failed) -> {
            callbackResults.add(failed);
            latch.countDown();
        };
        runOnMainSync(() -> {
            client.onPageFinished(wv, "https://example.com");
        });
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Error after page finished should not produce second callback
        client.onReceivedHttpError(null, fakeRequest("https://example.com", true), fakeHttpErrorResponse(500));

        Assert.assertEquals(1, callbackResults.size());
        Assert.assertFalse(callbackResults.get(0)); // from onPageFinished, not error
    }

    // =====================================
    // Timeout detection
    // =====================================

    /**
     * "onPageFinished" with page load exceeding 60 seconds but no pending CSS
     * should report success (failed=false) since all resources are ready
     */
    @Test
    public void pageLoadTimeout_over60Seconds_noPendingCss_reportsSuccess() throws InterruptedException {
        WebView wv = createWebView();
        CountDownLatch latch = new CountDownLatch(1);
        client.afterPageFinished = (failed) -> {
            callbackResults.add(failed);
            latch.countDown();
        };
        runOnMainSync(() -> {
            // Simulate a page load that took 61 seconds by backdating pageLoadTime
            client.pageLoadTime = System.currentTimeMillis() - 61_000;
            client.onPageFinished(wv, "https://example.com");
        });
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));

        Assert.assertEquals(1, callbackResults.size());
        Assert.assertFalse(callbackResults.get(0));
    }

    // =====================================
    // Critical resource detection edge cases
    // =====================================

    /**
     * "onReceivedHttpError" with URL that has query params after .js extension
     * should still detect as critical JS resource
     */
    @Test
    public void criticalResource_jsWithQueryParams_detected() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com/app.js?v=123", false), fakeHttpErrorResponse(404));
        Assert.assertEquals(1, callbackResults.size());
        Assert.assertTrue(callbackResults.get(0));
    }

    /**
     * "onReceivedHttpError" with URL that has uppercase extension
     * should still detect as critical resource (case insensitive)
     */
    @Test
    public void criticalResource_uppercaseExtension_detected() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com/app.JS", false), fakeHttpErrorResponse(404));
        Assert.assertEquals(1, callbackResults.size());
    }

    /**
     * "onReceivedHttpError" with URL that has no path
     * should not crash and not abort
     */
    @Test
    public void criticalResource_noPath_doesNotCrash() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com", false), fakeHttpErrorResponse(404));
        Assert.assertEquals(0, callbackResults.size());
    }

    /**
     * "onReceivedHttpError" with image sub-resource (png)
     * should abort because png is a critical resource
     */
    @Test
    public void criticalResource_imageExtensions_detected() {
        client.onReceivedHttpError(null, fakeRequest("https://example.com/photo.png", false), fakeHttpErrorResponse(404));
        Assert.assertEquals(1, callbackResults.size());
        Assert.assertTrue(callbackResults.get(0));
    }

    // =====================================
    // shouldInterceptRequest - sub-resource scheme blocking
    // =====================================

    /**
     * "shouldInterceptRequest" with http(s) sub-resources should return null so they load normally.
     */
    @Test
    public void shouldInterceptRequest_httpAndHttps_allowed() {
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("https://example.com/photo.png", false)));
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("http://example.com/app.js", false)));
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("HTTPS://EXAMPLE.COM/x.css", true)));
    }

    /**
     * "shouldInterceptRequest" must block every local-data / script scheme an attacker could use to
     * read local data or run script from a content sub-resource (img/iframe/script/xhr): file://,
     * content://, javascript:, jar:file://, plus a file:// pointing at app-private storage. "data:"
     * and "blob:" are NOT blocked here — they are inline / runtime-generated assets widgets embed
     * (covered by shouldInterceptRequest_inlineAssetSchemes_allowed).
     */
    @Test
    public void shouldInterceptRequest_nonWebSchemes_blocked() {
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("file:///data/data/ly.count.android.sdk/shared_prefs/secret.xml", false)));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("file:///etc/hosts", false)));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("content://com.app.provider/private", false)));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("content://media/external/images/media/1", false)));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("javascript:alert(document.cookie)", false)));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("jar:file:///data/app/x.apk!/a.html", false)));
        // also blocked for a main-frame request, not just sub-resources
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("file:///data/data/ly.count.android.sdk/databases/countly.db", true)));
    }

    /**
     * "data:" and "blob:" sub-resources (inline images/fonts/CSS and runtime-generated assets that
     * widgets legitimately embed) load in the default (no allow-list) mode, but like any other
     * non-https scheme they are blocked in allow-list mode unless the integrator lists them.
     */
    @Test
    public void shouldInterceptRequest_inlineAssetSchemes_defaultAllowedAllowlistGoverned() {
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("data:image/png;base64,iVBORw0KGgo=", false)));
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("blob:https://example.com/uuid", false)));
        // allow-list mode governs data/blob (not force-allowed): blocked unless listed
        CountlyWebViewClient allowlisted = new CountlyWebViewClient(new HashSet<>(Arrays.asList("myapp")));
        assertBlocked(allowlisted.shouldInterceptRequest(null, fakeRequest("data:image/png;base64,iVBORw0KGgo=", false)));
        assertBlocked(allowlisted.shouldInterceptRequest(null, fakeRequest("blob:https://example.com/uuid", false)));
        // https still always loads, and an explicitly listed inline scheme loads
        Assert.assertNull(allowlisted.shouldInterceptRequest(null, fakeRequest("https://example.com/a.png", false)));
        CountlyWebViewClient dataAllowed = new CountlyWebViewClient(new HashSet<>(Arrays.asList("data")));
        Assert.assertNull(dataAllowed.shouldInterceptRequest(null, fakeRequest("data:image/png;base64,iVBORw0KGgo=", false)));
    }

    private void assertBlocked(WebResourceResponse response) {
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getData());
    }

    /**
     * With a configured scheme allow-list, sub-resources follow allow-list mode: listed schemes load
     * and everything else is blocked, EXCEPT https which always loads because it serves the content
     * itself (so an outbound-link allow-list does not break the page's own https assets). Plain http
     * is NOT auto-allowed — it must be listed explicitly, so the integrator decides whether to permit it.
     */
    @Test
    public void shouldInterceptRequest_allowlistMode() {
        CountlyWebViewClient allowlisted = new CountlyWebViewClient(new HashSet<>(Arrays.asList("myapp")));
        // https always loads regardless of the allow-list
        Assert.assertNull(allowlisted.shouldInterceptRequest(null, fakeRequest("https://example.com/a.png", false)));
        // a listed non-web scheme loads
        Assert.assertNull(allowlisted.shouldInterceptRequest(null, fakeRequest("myapp://x", false)));
        // http is not auto-allowed: blocked unless explicitly listed
        assertBlocked(allowlisted.shouldInterceptRequest(null, fakeRequest("http://example.com/a.png", false)));
        // other unlisted non-web schemes are blocked
        assertBlocked(allowlisted.shouldInterceptRequest(null, fakeRequest("market://details?id=x", false)));
        assertBlocked(allowlisted.shouldInterceptRequest(null, fakeRequest("file:///etc/hosts", false)));
        // http loads when explicitly listed
        CountlyWebViewClient httpAllowed = new CountlyWebViewClient(new HashSet<>(Arrays.asList("http")));
        Assert.assertNull(httpAllowed.shouldInterceptRequest(null, fakeRequest("http://example.com/a.png", false)));
    }
}
