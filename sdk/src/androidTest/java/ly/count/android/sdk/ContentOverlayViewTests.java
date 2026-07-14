package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for ContentOverlayView.
 *
 * These tests cover logic that is hard or impossible to verify manually:
 * - URL parsing (splitQuery) via contentUrlAction/widgetUrlAction
 * - Action routing (event, resize_me, close, widget commands)
 * - Close/destroy lifecycle and cleanup verification
 * - WindowManager.LayoutParams creation (type, flags, offsets)
 * - Initial state and configuration selection
 *
 * Maps to test plan sections: 10 (Token/WM), 12 (Actions/Communication), 13 (Cleanup/Memory)
 */
@RunWith(AndroidJUnit4.class)
public class ContentOverlayViewTests {

    private ContentOverlayView overlay;
    private ActivityScenario<OverlayTestActivity> scenario;

    /**
     * Bare activity used as a host for ContentOverlayView in tests.
     * Declared in sdk/src/androidTest/AndroidManifest.xml.
     */
    public static class OverlayTestActivity extends Activity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }
    }

    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();

        CountlyConfig config = TestUtils.createBaseConfig();
        Countly.sharedInstance().init(config);
    }

    @After
    public void tearDown() {
        if (overlay != null && scenario != null) {
            try {
                scenario.onActivity(activity -> overlay.destroy());
            } catch (Exception ignored) {
            }
            overlay = null;
        }
        if (scenario != null) {
            try {
                scenario.close();
            } catch (Exception ignored) {
            }
            scenario = null;
        }
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    // ===================== Helpers =====================

    private ContentOverlayView createOverlay(Activity activity) {
        return createOverlay(activity, null, null);
    }

    private ContentOverlayView createOverlay(Activity activity,
        @Nullable ContentCallback callback, @Nullable Runnable onClose) {
        return createOverlay(activity, callback, onClose, null);
    }

    private ContentOverlayView createOverlay(Activity activity,
        @Nullable ContentCallback callback, @Nullable Runnable onClose,
        @Nullable ContentUrlHandler contentUrlHandler) {
        TransparentActivityConfig portrait = new TransparentActivityConfig(0, 0, 300, 500);
        portrait.url = "about:blank";
        portrait.useSafeArea = false;

        TransparentActivityConfig landscape = new TransparentActivityConfig(0, 0, 500, 300);
        landscape.url = "about:blank";
        landscape.useSafeArea = false;

        return new ContentOverlayView(
            activity, portrait, landscape,
            activity.getResources().getConfiguration().orientation,
            callback,
            onClose != null ? onClose : () -> {
            },
            null,
            contentUrlHandler
        );
    }

    private Object getField(String fieldName) throws Exception {
        Field field = ContentOverlayView.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(overlay);
    }

    private WindowManager.LayoutParams invokeCreateWindowParams(
        Activity activity, TransparentActivityConfig config) throws Exception {
        Method method = ContentOverlayView.class.getDeclaredMethod(
            "createWindowParams", Activity.class, TransparentActivityConfig.class);
        method.setAccessible(true);
        return (WindowManager.LayoutParams) method.invoke(overlay, activity, config);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeSplitQuery(String url) throws Exception {
        Method method = ContentOverlayView.class.getDeclaredMethod("splitQuery", String.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(overlay, url);
    }

    /**
     * Launches the test activity, runs the given action on the main thread,
     * and stores the scenario for cleanup.
     */
    private void withActivity(ActivityAction action) {
        scenario = ActivityScenario.launch(OverlayTestActivity.class);
        scenario.onActivity(action::run);
    }

    @FunctionalInterface
    interface ActivityAction {
        void run(Activity activity);
    }

    // ===================== contentUrlHandler =====================

    /**
     * When a content URL handler is set and returns true, startSafeExternalIntent hands the URL to
     * it and short-circuits before dispatching an ACTION_VIEW intent (so the app can route its own
     * deep link).
     */
    @Test
    public void startSafeExternalIntent_contentUrlHandler_takesOverAndSkipsIntent() {
        withActivity(activity -> {
            final String[] captured = { null };
            ContentUrlHandler handler = url -> {
                captured[0] = url;
                return true; // app handled it -> SDK must not dispatch an intent
            };
            overlay = createOverlay(activity, null, null, handler);
            try {
                Method m = ContentOverlayView.class.getDeclaredMethod("startSafeExternalIntent", String.class);
                m.setAccessible(true);
                m.invoke(overlay, "myapp://deeplink/screen?id=42");
            } catch (Exception e) {
                Assert.fail("startSafeExternalIntent invoke failed: " + e);
            }
            // Handler received the URL; returning true short-circuits before the ACTION_VIEW intent.
            Assert.assertEquals("myapp://deeplink/screen?id=42", captured[0]);
        });
    }

    // ===================== contentUrlAction — URL Parsing & Routing =====================

    /**
     * Valid event action URL is recognized and returns true.
     * Tests splitQuery parsing of JSON array in "event" parameter.
     */
    @Test
    public void contentUrlAction_eventAction_returnsTrue() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event"
                + "&event=[{\"key\":\"test_key\",\"sg\":{\"color\":\"blue\"}}]";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Event with "segmentation" field (alternative to "sg") is parsed.
     */
    @Test
    public void contentUrlAction_eventWithSegmentationField_returnsTrue() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event"
                + "&event=[{\"key\":\"click\",\"segmentation\":{\"button\":\"submit\"}}]";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Multiple events in a single JSON array are all processed without error.
     */
    @Test
    public void contentUrlAction_multipleEvents_returnsTrue() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event"
                + "&event=[{\"key\":\"e1\",\"sg\":{\"k\":\"v1\"}},{\"key\":\"e2\",\"sg\":{\"k\":\"v2\"}}]";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Malformed "event" JSON must not crash. When the payload fails to validate, splitQuery stores
     * it as a raw String (not a JSONArray) and still routes action=event to eventAction. eventAction
     * must guard the cast instead of letting a ClassCastException propagate out of
     * shouldOverrideUrlLoading. The URL is still a consumed countly action URL (returns true) and
     * simply records nothing. Regression test for the unguarded (JSONArray) cast.
     */
    @Test
    public void contentUrlAction_malformedEventJson_doesNotThrow() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            // Truncated JSON array: new JSONArray(...) throws, so "event" is not stored as a JSONArray.
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event&event=[{\"key\":\"oops\"";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Same guard with reversed param order: a valid action=event verb paired with a malformed event
     * payload that appears before it. The event value lands in splitQuery's fallback prefix as a
     * String, so eventAction must not cast-crash.
     */
    @Test
    public void contentUrlAction_malformedEventJson_reversedOrder_doesNotThrow() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&event=[oops&action=event";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * resize_me action parses JSON and updates portrait/landscape configs.
     */
    @Test
    public void contentUrlAction_resizeMe_updatesConfig() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            float density = activity.getResources().getDisplayMetrics().density;

            String resizeJson = "{\"p\":{\"x\":5,\"y\":10,\"w\":200,\"h\":400},\"l\":{\"x\":10,\"y\":5,\"w\":400,\"h\":200}}";
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=resize_me&resize_me=" + resizeJson;
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));

            // Verify portrait config was updated with density scaling
            Assert.assertEquals((int) Math.ceil(5 * density), (int) overlay.configPortrait.x);
            Assert.assertEquals((int) Math.ceil(10 * density), (int) overlay.configPortrait.y);
            Assert.assertEquals((int) Math.ceil(200 * density), (int) overlay.configPortrait.width);
            Assert.assertEquals((int) Math.ceil(400 * density), (int) overlay.configPortrait.height);

            // Verify landscape config was updated with density scaling
            Assert.assertEquals((int) Math.ceil(10 * density), (int) overlay.configLandscape.x);
            Assert.assertEquals((int) Math.ceil(5 * density), (int) overlay.configLandscape.y);
            Assert.assertEquals((int) Math.ceil(400 * density), (int) overlay.configLandscape.width);
            Assert.assertEquals((int) Math.ceil(200 * density), (int) overlay.configLandscape.height);
        });
    }

    /**
     * close=1 in the URL triggers overlay close.
     */
    @Test
    public void contentUrlAction_closeAction_closesOverlay() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&close=1";
            overlay.contentUrlAction(url, overlay.webView);

            try {
                Assert.assertTrue("isClosed should be true", (Boolean) getField("isClosed"));
            } catch (Exception e) {
                Assert.fail("Failed to read isClosed: " + e);
            }
        });
    }

    /**
     * Combined action + close=1: both the event action and the close are executed.
     */
    @Test
    public void contentUrlAction_combinedEventAndClose_bothExecute() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        withActivity(activity -> {
            overlay = createOverlay(activity, null, () -> closeCalled.set(true));
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event"
                + "&event=[{\"key\":\"t\",\"sg\":{\"k\":\"v\"}}]&close=1";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });

        Assert.assertTrue("onClose should have been called", closeCalled.get());
    }

    /**
     * Unknown action type still returns true (it IS a valid countly action URL).
     */
    @Test
    public void contentUrlAction_unknownAction_returnsTrue() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=unknown_xyz";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * URL with wrong prefix (not COMM_URL) is not recognized — returns false.
     */
    @Test
    public void contentUrlAction_wrongPrefix_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = "https://example.com/?cly_x_action_event=1&action=event";
            Assert.assertFalse(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Missing cly_x_action_event parameter — returns false.
     */
    @Test
    public void contentUrlAction_missingActionFlag_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?action=event";
            Assert.assertFalse(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * cly_x_action_event=0 (not "1") — returns false.
     */
    @Test
    public void contentUrlAction_actionFlagNotOne_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=0&action=event";
            Assert.assertFalse(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    @Test
    public void contentUrlAction_nullUrl_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            Assert.assertFalse(overlay.contentUrlAction(null, overlay.webView));
        });
    }

    @Test
    public void contentUrlAction_nullWebView_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event";
            Assert.assertFalse(overlay.contentUrlAction(url, null));
        });
    }

    /**
     * After close(), contentUrlAction returns false (isClosed guard).
     */
    @Test
    public void contentUrlAction_whenClosed_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            // Save webView reference before close destroys it
            android.webkit.WebView wv = overlay.webView;
            overlay.close(new HashMap<>());

            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event";
            Assert.assertFalse(overlay.contentUrlAction(url, wv));
        });
    }

    // ===================== widgetUrlAction =====================

    /**
     * Widget close command triggers both close and the cancel runnable.
     */
    @Test
    public void widgetUrlAction_closeCommand_closesAndRunsCancel() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean cancelCalled = new AtomicBoolean(false);

        withActivity(activity -> {
            overlay = createOverlay(activity, null, () -> closeCalled.set(true));
            overlay.setOnWidgetCancelRunnable(() -> cancelCalled.set(true));

            String url = Utils.COMM_URL + "/?cly_widget_command=1&close=1";
            Assert.assertTrue(overlay.widgetUrlAction(url, overlay.webView));
        });

        Assert.assertTrue("onClose should have been called", closeCalled.get());
        Assert.assertTrue("cancel runnable should have been called", cancelCalled.get());
    }

    /**
     * Non-widget URL returns false.
     */
    @Test
    public void widgetUrlAction_nonWidgetUrl_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?action=event";
            Assert.assertFalse(overlay.widgetUrlAction(url, overlay.webView));
        });
    }

    @Test
    public void widgetUrlAction_nullUrl_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            Assert.assertFalse(overlay.widgetUrlAction(null, overlay.webView));
        });
    }

    @Test
    public void widgetUrlAction_nullWebView_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            Assert.assertFalse(overlay.widgetUrlAction(null, null));
        });
    }

    // ===================== link parsing — query param preservation =====================

    /**
     * A link with a single query param is preserved intact (the raw, unencoded form the server
     * sends). Regression guard: previously the naive '&' split truncated it at the first '&'.
     */
    @Test
    public void splitQuery_linkWithSingleQueryParam_preserved() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/path?foo=bar";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertEquals("link", q.get("action"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A link whose own query string has MULTIPLE '&'-separated params keeps all of them. This is
     * the core fix: everything after "link=" is captured verbatim instead of being split on '&'.
     */
    @Test
    public void splitQuery_linkWithMultipleQueryParams_preserved() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/path?foo=bar&baz=qux&n=42";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                // The link's own params must NOT leak in as separate top-level entries.
                Assert.assertFalse(q.containsKey("baz"));
                Assert.assertFalse(q.containsKey("n"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A deeplink with query params (custom scheme) is preserved intact.
     */
    @Test
    public void splitQuery_deeplinkWithQueryParams_preserved() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "myapp://open?screen=home&id=42&ref=push";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * "event" appearing AFTER the link is separated out: the reserved marker is validated as JSON,
     * so it is peeled from the tail while the link (with its own query params) stays intact.
     */
    @Test
    public void splitQuery_eventAfterLink_separatedFromLink() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://x.com/p?a=b&c=d";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link
                    + "&event=[{\"key\":\"e\",\"sg\":{\"x\":\"y\"}}]";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertTrue(q.get("event") instanceof org.json.JSONArray);
                Assert.assertEquals("e", ((org.json.JSONArray) q.get("event")).getJSONObject(0).getString("key"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A "&event=" that appears inside the link but does NOT validate as JSON is treated as ordinary
     * link text (not a real param) and stays part of the link.
     */
    @Test
    public void splitQuery_invalidReservedMarkerInLink_staysInLink() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://x.com/p?a=b&event=notjson";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertFalse(q.containsKey("event"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A decoded "event" JSON whose segmentation value literally contains "&close=1" is parsed whole:
     * the inner "&close=" fails close validation, so it is absorbed into the JSON value rather than
     * being mistaken for a real close param.
     */
    @Test
    public void splitQuery_eventJsonContainingReservedText_parsedWhole() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event"
                    + "&event=[{\"key\":\"k\",\"sg\":{\"u\":\"a&close=1\"}}]";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertTrue(q.get("event") instanceof org.json.JSONArray);
                Assert.assertEquals("a&close=1",
                    ((org.json.JSONArray) q.get("event")).getJSONObject(0).getJSONObject("sg").getString("u"));
                Assert.assertFalse(q.containsKey("close"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A link with no query params still works (no "link=" trailing content edge case).
     */
    @Test
    public void splitQuery_linkWithoutQueryParams_preserved() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/landing";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Non-regression: an "event" JSON value (already decoded by the WebView client) is parsed into a
     * JSONArray. The event does not co-occur with a link, so the whole query splits cleanly.
     */
    @Test
    public void splitQuery_eventValue_parsed() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event"
                    + "&event=[{\"key\":\"test_key\",\"sg\":{\"color\":\"blue\"}}]";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertTrue(q.get("event") instanceof org.json.JSONArray);
                org.json.JSONArray arr = (org.json.JSONArray) q.get("event");
                Assert.assertEquals("test_key", arr.getJSONObject(0).getString("key"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * When a "close" flag is appended after a link (with the link carrying its own query params),
     * the link is preserved intact AND the trailing close flag is peeled off and parsed separately
     * rather than being swallowed into the link value.
     */
    @Test
    public void splitQuery_linkWithTrailingClose_separatesLinkAndClose() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/path?foo=bar&baz=qux";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link + "&close=1";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertEquals("1", q.get("close"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * "close" may also precede the link. In that case it stays in the head and is parsed normally,
     * while the link (with its own query params) is still captured intact.
     */
    @Test
    public void splitQuery_closeBeforeLink_separatesLinkAndClose() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/path?foo=bar&baz=qux";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&close=1&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertEquals("1", q.get("close"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Documented reserved-name limitation: because "close" is a reserved param, a link that itself
     * ends with "&close=1" (a valid close value) has that segment consumed as the close flag. The
     * link keeps everything up to it. Integrators are told these param names are reserved.
     */
    @Test
    public void splitQuery_linkEndingInReservedClose_consumedAsFlag() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=https://x.com?a=b&c=d&close=1&close=1";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals("https://x.com?a=b&c=d", q.get("link"));
                Assert.assertEquals("1", q.get("close"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A trailing "close=0" after a link is also peeled off (link kept open).
     */
    @Test
    public void splitQuery_linkWithTrailingCloseZero_separatesLinkAndClose() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/path?a=1&b=2";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link + "&close=0";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertEquals("0", q.get("close"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * End-to-end: a link action with query params AND a trailing close is recognized (returns true),
     * dispatches the external intent, and closes the overlay.
     */
    @Test
    public void contentUrlAction_linkWithQueryParamsAndClose_closesOverlay() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        withActivity(activity -> {
            overlay = createOverlay(activity, null, () -> closeCalled.set(true));
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link"
                + "&link=https://example.com/path?foo=bar&baz=qux&close=1";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
        Assert.assertTrue("overlay should close when close=1 trails the link", closeCalled.get());
    }

    /**
     * End-to-end: a link action with query params (no close) is recognized and handled (returns
     * true), and does not throw while dispatching the external intent.
     */
    @Test
    public void contentUrlAction_linkWithQueryParams_returnsTrue() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link"
                + "&link=https://example.com/path?foo=bar&baz=qux";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * A link fragment ("#...") is preserved verbatim.
     */
    @Test
    public void splitQuery_linkWithFragment_preserved() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/path?a=b#section";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A link containing more than one "?" is preserved verbatim (the "?" characters after the first
     * are part of the link value, not new query separators).
     */
    @Test
    public void splitQuery_linkWithRepeatedQuestionMark_preserved() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://example.com/p?a=b?c=d";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * All three of link, event and close in one URL are each separated correctly, with the link
     * (carrying its own query params) kept intact.
     */
    @Test
    public void splitQuery_linkEventAndClose_allSeparated() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://x.com/p?a=b&c=d";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link
                    + "&event=[{\"key\":\"e\"}]&close=1";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertEquals("1", q.get("close"));
                Assert.assertTrue(q.get("event") instanceof org.json.JSONArray);
                Assert.assertEquals("e", ((org.json.JSONArray) q.get("event")).getJSONObject(0).getString("key"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A "close" whose value is not "0"/"1" (e.g. "close=2") fails validation, so it is treated as
     * ordinary link text and absorbed into the link rather than parsed as the close flag.
     */
    @Test
    public void splitQuery_invalidCloseValue_staysInLink() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String link = "https://x.com/p?a=b&close=2";
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=" + link;
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals(link, q.get("link"));
                Assert.assertFalse(q.containsKey("close"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Documents the fallback: a schemeless link fails link validation (no URI scheme), so the whole
     * query falls back to the plain '&' split, which truncates a multi-param link. The server always
     * prepends "https://", so this is an edge case; the test pins the current behavior.
     */
    @Test
    public void splitQuery_schemelessLink_fallbackTruncates() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            try {
                String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=link&link=example.com/p?a=b&c=d";
                Map<String, Object> q = invokeSplitQuery(url);
                Assert.assertEquals("example.com/p?a=b", q.get("link"));
                Assert.assertEquals("d", q.get("c"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ===================== Close & Destroy Lifecycle =====================

    /**
     * close() fires both the ContentCallback (with CLOSED status) and the onClose runnable.
     */
    @Test
    public void close_runsCallbacksAndCloseRunnable() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        AtomicReference<ContentStatus> callbackStatus = new AtomicReference<>();

        withActivity(activity -> {
            ContentCallback callback = (status, data) -> {
                callbackCalled.set(true);
                callbackStatus.set(status);
            };
            overlay = createOverlay(activity, callback, () -> closeCalled.set(true));
            overlay.close(new HashMap<>());
        });

        Assert.assertTrue("onClose should have been called", closeCalled.get());
        Assert.assertTrue("contentCallback should have been called", callbackCalled.get());
        Assert.assertEquals(ContentStatus.CLOSED, callbackStatus.get());
    }

    /**
     * Calling close() twice does not run callbacks a second time.
     */
    @Test
    public void close_isIdempotent() {
        AtomicInteger closeCount = new AtomicInteger(0);

        withActivity(activity -> {
            overlay = createOverlay(activity, null, closeCount::incrementAndGet);
            overlay.close(new HashMap<>());
            overlay.close(new HashMap<>()); // second call — should be no-op
        });

        Assert.assertEquals("onClose should be called exactly once", 1, closeCount.get());
    }

    /**
     * close() destroys the WebView (sets it to null).
     */
    @Test
    public void close_destroysWebView() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            Assert.assertNotNull("webView should exist before close", overlay.webView);
            overlay.close(new HashMap<>());
            Assert.assertNull("webView should be null after close", overlay.webView);
        });
    }

    /**
     * close() passes content data to the callback.
     */
    @Test
    public void close_passesContentDataToCallback() {
        AtomicReference<Map<String, Object>> receivedData = new AtomicReference<>();

        withActivity(activity -> {
            ContentCallback callback = (status, data) -> receivedData.set(data);
            overlay = createOverlay(activity, callback, () -> {
            });

            Map<String, Object> closeData = new HashMap<>();
            closeData.put("close", "1");
            closeData.put("action", "event");
            overlay.close(closeData);
        });

        Assert.assertNotNull("data should not be null", receivedData.get());
        Assert.assertEquals("1", receivedData.get().get("close"));
        Assert.assertEquals("event", receivedData.get().get("action"));
    }

    /**
     * destroy() nulls all internal references (memory leak prevention).
     * Verifies: webView, contentCallback, onCloseRunnable, onWidgetCancelRunnable,
     * currentHostActivity, windowManager, orientationCallback are all null.
     */
    @Test
    public void destroy_clearsAllReferences() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            overlay.setOnWidgetCancelRunnable(() -> {
            });
            overlay.destroy();

            Assert.assertNull("webView should be null", overlay.webView);
            try {
                Assert.assertNull("contentCallback", getField("contentCallback"));
                Assert.assertNull("onCloseRunnable", getField("onCloseRunnable"));
                Assert.assertNull("onWidgetCancelRunnable", getField("onWidgetCancelRunnable"));
                Assert.assertNull("currentHostActivity", getField("currentHostActivity"));
                Assert.assertNull("windowManager", getField("windowManager"));
                Assert.assertNull("orientationCallback", getField("orientationCallback"));
                Assert.assertTrue("isClosed should be true", (Boolean) getField("isClosed"));
            } catch (Exception e) {
                Assert.fail("Failed to verify destroy cleanup: " + e);
            }
        });
    }

    /**
     * destroy() does not crash when overlay was never attached to WindowManager.
     */
    @Test
    public void destroy_safeWhenNotAttached() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            // Don't attach — just destroy. Should not crash.
            overlay.destroy();
        });
    }

    // ===================== Configuration Selection =====================

    /**
     * Configs are stored correctly and match constructor inputs.
     */
    @Test
    public void configs_storedCorrectly() {
        withActivity(activity -> {
            TransparentActivityConfig portrait = new TransparentActivityConfig(10, 20, 300, 500);
            portrait.url = "about:blank";
            portrait.useSafeArea = false;

            TransparentActivityConfig landscape = new TransparentActivityConfig(30, 40, 500, 300);
            landscape.url = "about:blank";
            landscape.useSafeArea = false;

            overlay = new ContentOverlayView(
                activity, portrait, landscape,
                Configuration.ORIENTATION_PORTRAIT, null, () -> {
            }, null, null);

            // Note: setupConfig may modify width/height if < 1, but ours are > 0
            Assert.assertEquals(10, (int) overlay.configPortrait.x);
            Assert.assertEquals(20, (int) overlay.configPortrait.y);
            Assert.assertEquals(300, (int) overlay.configPortrait.width);
            Assert.assertEquals(500, (int) overlay.configPortrait.height);

            Assert.assertEquals(30, (int) overlay.configLandscape.x);
            Assert.assertEquals(40, (int) overlay.configLandscape.y);
            Assert.assertEquals(500, (int) overlay.configLandscape.width);
            Assert.assertEquals(300, (int) overlay.configLandscape.height);
        });
    }

    // ===================== WindowManager.LayoutParams =====================

    /**
     * Verifies TYPE_APPLICATION, correct flags, gravity, format, and dimensions.
     */
    @Test
    public void createWindowParams_correctTypeAndFlags() {
        withActivity(activity -> {
            overlay = createOverlay(activity);

            TransparentActivityConfig config = new TransparentActivityConfig(10, 20, 300, 500);
            config.useSafeArea = false;

            try {
                WindowManager.LayoutParams params = invokeCreateWindowParams(activity, config);

                Assert.assertEquals("Type should be TYPE_APPLICATION",
                    WindowManager.LayoutParams.TYPE_APPLICATION, params.type);

                // Expected base flags match the production set in createWindowParams:
                //   FLAG_NOT_FOCUSABLE + FLAG_WATCH_OUTSIDE_TOUCH let the host
                //   activity keep IME focus while still receiving outside-touch
                //   events the overlay routes back via dispatchTouchEvent.
                // FLAG_NOT_TOUCHABLE is added only while content is still loading
                // (gates touches until the WebView is visible). The test
                // constructs the overlay with about:blank and never waits for
                // afterPageFinished, so isContentLoaded stays false here.
                int expectedFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                Assert.assertEquals("Flags should match", expectedFlags, params.flags);

                Assert.assertEquals("Gravity should be TOP|START",
                    Gravity.TOP | Gravity.START, params.gravity);
                Assert.assertEquals("Format should be TRANSLUCENT",
                    PixelFormat.TRANSLUCENT, params.format);
                Assert.assertEquals("Width should match", 300, params.width);
                Assert.assertEquals("Height should match", 500, params.height);
                Assert.assertEquals("X should match", 10, params.x);
                Assert.assertEquals("Y should match", 20, params.y);
            } catch (Exception e) {
                Assert.fail("createWindowParams failed: " + e);
            }
        });
    }

    /**
     * When useSafeArea is true, x and y are adjusted by leftOffset and topOffset.
     */
    @Test
    public void createWindowParams_safeAreaOffsets_applied() {
        withActivity(activity -> {
            overlay = createOverlay(activity);

            TransparentActivityConfig config = new TransparentActivityConfig(10, 20, 300, 500);
            config.useSafeArea = true;
            config.topOffset = 50;
            config.leftOffset = 30;

            try {
                WindowManager.LayoutParams params = invokeCreateWindowParams(activity, config);
                Assert.assertEquals("X should include leftOffset", 10 + 30, params.x);
                Assert.assertEquals("Y should include topOffset", 20 + 50, params.y);
            } catch (Exception e) {
                Assert.fail("createWindowParams failed: " + e);
            }
        });
    }

    /**
     * When useSafeArea is false, offsets are NOT applied even if set.
     */
    @Test
    public void createWindowParams_noOffset_whenNotSafeArea() {
        withActivity(activity -> {
            overlay = createOverlay(activity);

            TransparentActivityConfig config = new TransparentActivityConfig(10, 20, 300, 500);
            config.useSafeArea = false;
            config.topOffset = 50;
            config.leftOffset = 30;

            try {
                WindowManager.LayoutParams params = invokeCreateWindowParams(activity, config);
                Assert.assertEquals("X should NOT include leftOffset", 10, params.x);
                Assert.assertEquals("Y should NOT include topOffset", 20, params.y);
            } catch (Exception e) {
                Assert.fail("createWindowParams failed: " + e);
            }
        });
    }

    /**
     * Zero offsets produce no adjustment even with useSafeArea=true.
     */
    @Test
    public void createWindowParams_zeroOffsets_noAdjustment() {
        withActivity(activity -> {
            overlay = createOverlay(activity);

            TransparentActivityConfig config = new TransparentActivityConfig(15, 25, 300, 500);
            config.useSafeArea = true;
            config.topOffset = 0;
            config.leftOffset = 0;

            try {
                WindowManager.LayoutParams params = invokeCreateWindowParams(activity, config);
                Assert.assertEquals("X unchanged with zero offset", 15, params.x);
                Assert.assertEquals("Y unchanged with zero offset", 25, params.y);
            } catch (Exception e) {
                Assert.fail("createWindowParams failed: " + e);
            }
        });
    }

    // ===================== Initial State =====================

    /**
     * After construction: webView exists, isClosed=false, orientationCallback registered.
     */
    @Test
    public void creation_setsCorrectInitialState() {
        withActivity(activity -> {
            overlay = createOverlay(activity);

            Assert.assertNotNull("webView should exist", overlay.webView);
            Assert.assertNotNull("configPortrait should exist", overlay.configPortrait);
            Assert.assertNotNull("configLandscape should exist", overlay.configLandscape);

            try {
                Assert.assertFalse("isClosed should be false", (Boolean) getField("isClosed"));
                Assert.assertNotNull("orientationCallback should be registered",
                    getField("orientationCallback"));
            } catch (Exception e) {
                Assert.fail("Failed to verify initial state: " + e);
            }
        });
    }

    /**
     * WebView starts INVISIBLE (becomes visible only after page load).
     */
    @Test
    public void creation_webViewStartsInvisible() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            Assert.assertEquals("WebView should start invisible",
                View.INVISIBLE, overlay.webView.getVisibility());
        });
    }

    // ===================== attachToActivity =====================

    /**
     * attachToActivity is a no-op after close() — does not add to window.
     */
    @Test
    public void attachToActivity_skipsWhenClosed() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            overlay.close(new HashMap<>());
            overlay.attachToActivity(activity);

            try {
                Assert.assertFalse("should not be added to window",
                    (Boolean) getField("isAddedToWindow"));
            } catch (Exception e) {
                Assert.fail("Failed to verify: " + e);
            }
        });
    }

    /**
     * attachToActivity adds the overlay to WindowManager.
     */
    @Test
    public void attachToActivity_addsToWindow() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            overlay.attachToActivity(activity);

            try {
                Assert.assertTrue("should be added to window",
                    (Boolean) getField("isAddedToWindow"));
                Assert.assertNotNull("windowManager should be set",
                    getField("windowManager"));
            } catch (Exception e) {
                Assert.fail("Failed to verify: " + e);
            }
        });
    }

    /**
     * Calling attachToActivity with the same activity twice is safe (idempotent).
     */
    @Test
    public void attachToActivity_sameActivity_idempotent() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            overlay.attachToActivity(activity);
            // Second call with same activity should not crash
            overlay.attachToActivity(activity);

            try {
                Assert.assertTrue("should still be added to window",
                    (Boolean) getField("isAddedToWindow"));
            } catch (Exception e) {
                Assert.fail("Failed to verify: " + e);
            }
        });
    }

    // ===================== Edge Cases =====================

    /**
     * Malformed event JSON does not crash — action still returns true
     * (the URL is recognized as a countly action even if JSON parsing fails).
     */
    @Test
    public void contentUrlAction_malformedEventJson_doesNotCrash() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event&event=not_valid_json";
            // Should return true (valid countly URL) but log a JSON parse error
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Malformed resize_me JSON does not crash.
     */
    @Test
    public void contentUrlAction_malformedResizeJson_doesNotCrash() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=resize_me&resize_me={broken}";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Empty event array is handled gracefully.
     */
    @Test
    public void contentUrlAction_emptyEventArray_doesNotCrash() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event&event=[]";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * Event with missing segmentation data is skipped gracefully (no crash).
     */
    @Test
    public void contentUrlAction_eventMissingSegmentation_doesNotCrash() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/?cly_x_action_event=1&action=event"
                + "&event=[{\"key\":\"test_key\"}]";
            Assert.assertTrue(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    /**
     * URL with no query parameters after the prefix returns false
     * (splitQuery returns empty map, no cly_x_action_event key).
     */
    @Test
    public void contentUrlAction_noQueryParams_returnsFalse() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            String url = Utils.COMM_URL + "/";
            Assert.assertFalse(overlay.contentUrlAction(url, overlay.webView));
        });
    }

    // ===================== Memory leak prevention (issue #556) =====================

    /**
     * Structural invariant: the overlay's View.mContext must not pin the
     * constructing Activity. The overlay outlives activity transitions, and
     * View.mContext can never be swapped after construction — if it's the
     * Activity, that Activity stays GC-pinned for the overlay's full lifetime.
     *
     * The exact context type is API-dependent (see ContentOverlayView#resolveOverlayContext):
     *   - Pre-API 31: Application context.
     *   - API 31+: createConfigurationContext from the Activity — a ContextImpl
     *     wrapper that holds an IBinder token, not the Activity instance, so
     *     GC isn't blocked. Required for StrictMode#detectIncorrectContextUse.
     *
     * In both cases, getApplicationContext() resolves to the same Application.
     * The test asserts both that the context is NOT the Activity and that it
     * routes back to the right Application — which is the actual leak-avoidance
     * contract independent of API level.
     */
    @Test
    public void constructor_usesApplicationContext_notActivity() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            Assert.assertNotSame(
                "ContentOverlayView.mContext must not be the constructing Activity — "
                    + "View.mContext can never be swapped, so binding it to an Activity leaks "
                    + "that Activity for the lifetime of the overlay.",
                activity, overlay.getContext());
            Assert.assertSame(
                "ContentOverlayView.mContext must resolve to the same Application as the "
                    + "constructing Activity (Application directly on <API 31, "
                    + "ConfigurationContext-of-Activity on API 31+).",
                activity.getApplicationContext(),
                overlay.getContext().getApplicationContext());
        });
    }

    /**
     * Same invariant for the embedded WebView. Even with the wrapper View not
     * pinning the Activity, a WebView constructed with the Activity directly
     * would still pin it via its own mContext. See
     * constructor_usesApplicationContext_notActivity for the API-level rationale.
     */
    @Test
    public void webView_usesApplicationContext_notActivity() {
        withActivity(activity -> {
            overlay = createOverlay(activity);
            Assert.assertNotNull("WebView should be created during construction", overlay.webView);
            Assert.assertNotSame(
                "ContentOverlayView's WebView.mContext must not be the constructing Activity.",
                activity, overlay.webView.getContext());
            Assert.assertSame(
                "ContentOverlayView's WebView.mContext must resolve to the same Application "
                    + "as the constructing Activity.",
                activity.getApplicationContext(),
                overlay.webView.getContext().getApplicationContext());
        });
    }
}
