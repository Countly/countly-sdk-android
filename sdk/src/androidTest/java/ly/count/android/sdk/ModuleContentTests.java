package ly.count.android.sdk;

import android.app.Activity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleContentTests {

    Countly mCountly;
    List<String> capturedRequests;
    List<String> capturedEndpoints;

    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
        capturedRequests = new ArrayList<>();
        capturedEndpoints = new ArrayList<>();
    }

    @After
    public void tearDown() {
    }

    private ImmediateRequestGenerator createCapturingIRGenerator() {
        return new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                    capturedRequests.add(requestData);
                    capturedEndpoints.add(customEndpoint);
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                };
            }
        };
    }

    private Countly initWithConsent(boolean contentConsent) {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.setRequiresConsent(true);
        if (contentConsent) {
            config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.content });
        }
        config.disableHealthCheck();
        config.immediateRequestGenerator = createCapturingIRGenerator();

        mCountly = new Countly();
        mCountly.init(config);
        mCountly.moduleContent.countlyTimer = null;
        capturedRequests.clear();
        capturedEndpoints.clear();
        return mCountly;
    }

    private void setIsCurrentlyInContentZone(ModuleContent module, boolean value) throws Exception {
        java.lang.reflect.Field field = ModuleContent.class.getDeclaredField("isCurrentlyInContentZone");
        field.setAccessible(true);
        field.set(module, value);
    }

    private Activity getCurrentActivity(ModuleContent module) throws Exception {
        java.lang.reflect.Field field = ModuleContent.class.getDeclaredField("currentActivity");
        field.setAccessible(true);
        return (Activity) field.get(module);
    }

    // ======== previewContent public API tests ========

    /**
     * Null and empty contentId should be rejected at the public API level.
     * No request should be made.
     */
    @Test
    public void previewContent_invalidContentId() {
        Countly countly = initWithConsent(true);

        countly.contents().previewContent(null);
        Assert.assertEquals(0, capturedRequests.size());

        countly.contents().previewContent("");
        Assert.assertEquals(0, capturedRequests.size());
    }

    /**
     * Valid contentId with consent should make a request to /o/sdk/content
     * containing content_id and preview=true parameters
     */
    @Test
    public void previewContent_validContentId() {
        Countly countly = initWithConsent(true);

        countly.contents().previewContent("test_content_123");

        Assert.assertEquals(1, capturedRequests.size());
        Assert.assertEquals("/o/sdk/content", capturedEndpoints.get(0));

        String request = capturedRequests.get(0);
        Assert.assertTrue(request.contains("content_id=test_content_123"));
        Assert.assertTrue(request.contains("preview=true"));
    }

    /**
     * Without content consent, no request should be made
     */
    @Test
    public void previewContent_noConsent() {
        Countly countly = initWithConsent(false);

        countly.contents().previewContent("test_content_id");

        Assert.assertEquals(0, capturedRequests.size());
    }

    /**
     * When content is already being displayed, no new request should be made
     */
    @Test
    public void previewContent_alreadyInContentZone() throws Exception {
        Countly countly = initWithConsent(true);
        setIsCurrentlyInContentZone(countly.moduleContent, true);

        countly.contents().previewContent("test_content_id");

        Assert.assertEquals(0, capturedRequests.size());
    }

    // ======== validateResponse tests ========

    /**
     * validateResponse returns true only when both "geo" and "html" are present,
     * false for missing geo, missing html, or empty response
     */
    @Test
    public void validateResponse() throws JSONException {
        Countly countly = initWithConsent(true);
        ModuleContent mc = countly.moduleContent;

        // empty
        Assert.assertFalse(mc.validateResponse(new JSONObject()));

        // missing geo
        JSONObject noGeo = new JSONObject();
        noGeo.put("html", "<html></html>");
        Assert.assertFalse(mc.validateResponse(noGeo));

        // missing html
        JSONObject noHtml = new JSONObject();
        noHtml.put("geo", new JSONObject());
        Assert.assertFalse(mc.validateResponse(noHtml));

        // valid
        JSONObject valid = new JSONObject();
        valid.put("geo", new JSONObject());
        valid.put("html", "<html></html>");
        Assert.assertTrue(mc.validateResponse(valid));
    }

    // ======== Activity reference / leak prevention tests (issue #556) ========

    /**
     * onActivityDestroyed must null out currentActivity when the destroyed activity
     * is the one currently tracked. This is the core leak fix.
     */
    @Test
    public void onActivityDestroyed_clearsCurrentActivity_whenIdentityMatches() throws Exception {
        Countly countly = initWithConsent(true);
        ModuleContent mc = countly.moduleContent;

        Activity act = mock(Activity.class);
        mc.onActivityStarted(act, 1);
        Assert.assertSame(act, getCurrentActivity(mc));

        mc.onActivityDestroyed(act);
        Assert.assertNull(getCurrentActivity(mc));
    }

    /**
     * Destroying an activity other than the currently tracked one must NOT clear the field.
     * This protects against losing the active activity reference when an old, already-replaced
     * activity is finally destroyed.
     */
    @Test
    public void onActivityDestroyed_doesNotClear_whenDifferentActivity() throws Exception {
        Countly countly = initWithConsent(true);
        ModuleContent mc = countly.moduleContent;

        Activity tracked = mock(Activity.class);
        Activity unrelated = mock(Activity.class);
        mc.onActivityStarted(tracked, 1);

        mc.onActivityDestroyed(unrelated);
        Assert.assertSame(tracked, getCurrentActivity(mc));
    }

    /**
     * Rotation race regression: when onActivityStarted for the new activity fires before
     * onActivityDestroyed for the old one, destroying the old activity must not wipe out
     * the new tracked activity.
     */
    @Test
    public void onActivityDestroyed_doesNotClearNewerActivity_afterRotationRace() throws Exception {
        Countly countly = initWithConsent(true);
        ModuleContent mc = countly.moduleContent;

        Activity oldAct = mock(Activity.class);
        Activity newAct = mock(Activity.class);

        mc.onActivityStarted(oldAct, 1);
        mc.onActivityStarted(newAct, 2);
        Assert.assertSame(newAct, getCurrentActivity(mc));

        // Old activity is finally destroyed after the new one has already taken over.
        mc.onActivityDestroyed(oldAct);
        Assert.assertSame(newAct, getCurrentActivity(mc));
    }

    /**
     * onActivityDestroyed must not throw when no activity has been tracked yet.
     */
    @Test
    public void onActivityDestroyed_isSafe_whenNoActivityTracked() throws Exception {
        Countly countly = initWithConsent(true);
        ModuleContent mc = countly.moduleContent;

        Activity stray = mock(Activity.class);
        mc.onActivityDestroyed(stray);
        Assert.assertNull(getCurrentActivity(mc));
    }

    /**
     * The seeded activity path (onInitialActivitySeeded) must also be cleared on destroy.
     */
    @Test
    public void onActivityDestroyed_clearsSeededActivity() throws Exception {
        Countly countly = initWithConsent(true);
        ModuleContent mc = countly.moduleContent;

        Activity seeded = mock(Activity.class);
        mc.onInitialActivitySeeded(seeded);
        Assert.assertSame(seeded, getCurrentActivity(mc));

        mc.onActivityDestroyed(seeded);
        Assert.assertNull(getCurrentActivity(mc));
    }

    private boolean readShouldFetchContents(ModuleContent module) throws Exception {
        java.lang.reflect.Field field = ModuleContent.class.getDeclaredField("shouldFetchContents");
        field.setAccessible(true);
        return (boolean) field.get(module);
    }

    /**
     * Validation: the server-driven content zone (ecz) keeps working across a temporary-device-ID
     * toggle. When the SDK enters temporary mode the content zone is torn down, and when a real
     * device ID is assigned the server config is re-fetched and the content zone resumes.
     *
     * 1- Init with an immediate-request generator that returns ecz=true for the server config (/o/sdk)
     * 2- Verify the content zone is armed after the ecz=true server config is applied
     * 3- Enter temporary device ID mode and verify the content zone is torn down
     * 4- Leave temporary mode with a real device ID and verify the content zone resumes
     *    (the deferred-on-exit server config re-fetch re-applies ecz=true)
     */
    @Test
    public void contentZone_resumesAfterTemporaryDeviceIDToggle() throws Exception {
        final String serverConfigWithEcz = new ServerConfigBuilder().contentZone(true).build();

        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.disableHealthCheck();
        config.immediateRequestGenerator = new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                    if ("/o/sdk".equals(customEndpoint)) {
                        try {
                            callback.callback(new JSONObject(serverConfigWithEcz));
                        } catch (JSONException e) {
                            callback.callback(null);
                        }
                    } else {
                        // content and any other immediate requests: no payload needed for this test
                        callback.callback(null);
                    }
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> callback.callback(null);
            }
        };

        mCountly = new Countly().init(config);

        // ecz=true was applied at init, so the content zone should be armed
        Assert.assertTrue(readShouldFetchContents(mCountly.moduleContent));

        // entering temporary device ID mode tears the content zone down
        mCountly.deviceId().enableTemporaryIdMode();
        Assert.assertFalse(readShouldFetchContents(mCountly.moduleContent));

        // leaving temporary mode with a real ID re-fetches the server config (ecz=true) and resumes the zone
        mCountly.deviceId().changeWithoutMerge("real_user_after_temp");
        Assert.assertTrue(readShouldFetchContents(mCountly.moduleContent));
    }

    @Test
    public void contentZone_doesNotResumeAfterExplicitExit() throws Exception {
        final String serverConfigWithEcz = new ServerConfigBuilder().contentZone(true).build();

        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.disableHealthCheck();
        config.immediateRequestGenerator = new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                    if ("/o/sdk".equals(customEndpoint)) {
                        try {
                            callback.callback(new JSONObject(serverConfigWithEcz));
                        } catch (JSONException e) {
                            callback.callback(null);
                        }
                    } else {
                        callback.callback(null);
                    }
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> callback.callback(null);
            }
        };

        mCountly = new Countly().init(config);

        // ecz=true armed the content zone at init
        Assert.assertTrue(readShouldFetchContents(mCountly.moduleContent));

        // the developer explicitly exits the content zone
        mCountly.contents().exitContentZone();
        Assert.assertFalse(readShouldFetchContents(mCountly.moduleContent));

        // a device ID change must NOT silently resume a zone the developer turned off
        mCountly.deviceId().changeWithoutMerge("real_user_after_exit");
        Assert.assertFalse(readShouldFetchContents(mCountly.moduleContent));
    }
}
