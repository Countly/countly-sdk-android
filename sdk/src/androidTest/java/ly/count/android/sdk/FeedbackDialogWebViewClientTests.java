package ly.count.android.sdk;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Locks in the security hardening of the rating/feedback-dialog WebView client (the twin of
 * {@link CountlyWebViewClient}, which is covered by CountlyWebViewClientTests). Without these, a
 * regression that drops the scheme blocking or the allow-list threading would ship silently.
 */
@RunWith(AndroidJUnit4.class)
public class FeedbackDialogWebViewClientTests {

    private WebResourceRequest fakeRequest(String url) {
        Uri uri = Uri.parse(url);
        return new WebResourceRequest() {
            @Override public Uri getUrl() {
                return uri;
            }

            @Override public boolean isForMainFrame() {
                return false;
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

    private void assertBlocked(WebResourceResponse response) {
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getData());
    }

    /** Dangerous local/script sub-resource schemes are blocked; https/http load (default denylist). */
    @Test
    public void shouldInterceptRequest_defaultDenylist() {
        ModuleRatings.FeedbackDialogWebViewClient client = new ModuleRatings.FeedbackDialogWebViewClient(null, new ModuleLog());
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("https://example.com/a.png")));
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("http://example.com/a.js")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("file:///data/data/ly.count.android.sdk/shared_prefs/secret.xml")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("content://com.app.provider/private")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("javascript:alert(document.cookie)")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("jar:file:///x.apk!/a.html")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("zip://archive/x.html")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("intent://x/y#Intent;scheme=https;end")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("data:image/png;base64,iVBORw0KGgo=")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("data:image/svg+xml;utf8,<svg/>")));
        // blob: is a runtime-generated asset of the page itself -> not denylisted
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("blob:https://example.com/uuid")));
    }

    /** With a configured allow-list, https still loads but http and other unlisted schemes are blocked. */
    @Test
    public void shouldInterceptRequest_allowlistThreaded() {
        ModuleRatings.FeedbackDialogWebViewClient client =
            new ModuleRatings.FeedbackDialogWebViewClient(new HashSet<>(Arrays.asList("myapp")), new ModuleLog());
        // https always loads (serves the widget itself)
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("https://example.com/a.png")));
        // a listed non-web scheme loads
        Assert.assertNull(client.shouldInterceptRequest(null, fakeRequest("myapp://x")));
        // http is not auto-allowed in allow-list mode unless listed
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("http://example.com/a.png")));
        // unlisted non-web schemes blocked
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("market://details?id=x")));
        assertBlocked(client.shouldInterceptRequest(null, fakeRequest("file:///etc/hosts")));
    }

    /** The deprecated String overload must not NPE on a null url (a null scheme is blocked, fail-secure). */
    @Test
    public void shouldInterceptRequest_stringOverload_nullSafe() {
        ModuleRatings.FeedbackDialogWebViewClient client = new ModuleRatings.FeedbackDialogWebViewClient(null, new ModuleLog());
        assertBlocked(client.shouldInterceptRequest(null, (String) null)); // null url -> null scheme -> blocked, no NPE
        assertBlocked(client.shouldInterceptRequest(null, "file:///etc/hosts"));
        Assert.assertNull(client.shouldInterceptRequest(null, "https://example.com/a.png"));
    }
}
