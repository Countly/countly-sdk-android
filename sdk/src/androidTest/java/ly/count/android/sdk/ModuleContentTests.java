package ly.count.android.sdk;

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
}
