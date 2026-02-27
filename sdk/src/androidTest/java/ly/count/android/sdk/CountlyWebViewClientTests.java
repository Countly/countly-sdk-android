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
import java.util.HashMap;
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
     * "onPageFinished" with page load exceeding 60 seconds
     * should report timeout (failed=true)
     */
    @Test
    public void pageLoadTimeout_over60Seconds_reportsTimeout() throws InterruptedException {
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
        Assert.assertTrue(callbackResults.get(0));
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
}
