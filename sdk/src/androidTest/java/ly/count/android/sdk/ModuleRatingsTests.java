package ly.count.android.sdk;

import android.app.Activity;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleRatingsTests {
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void recordManualRating() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] vals = { "aa", "bb", "cc" };
        mCountly.ratings().recordManualRating(vals[0], 3, vals[1], vals[2], true);

        final Map<String, Object> segm = new HashMap<>();

        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("rating", "" + 3);
        segm.put("widget_id", vals[0]);
        segm.put("email", vals[1]);
        segm.put("comment", vals[2]);
        segm.put("contactMe", true);

        verify(ep).recordEventInternal(ModuleFeedback.RATING_EVENT_KEY, segm, 1, 0, 0, null, null);

        //validate lower bound
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.ratings().recordManualRating(vals[0], -12, vals[1], vals[2], true);
        segm.put("rating", "" + 1);
        verify(ep).recordEventInternal(ModuleFeedback.RATING_EVENT_KEY, segm, 1, 0, 0, null, null);

        //validate upper bound
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.ratings().recordManualRating(vals[0], 12, vals[1], vals[2], true);
        segm.put("rating", "" + 5);
        verify(ep).recordEventInternal(ModuleFeedback.RATING_EVENT_KEY, segm, 1, 0, 0, null, null);

        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.moduleRatings.recordManualRatingInternal(null, 12, vals[1], vals[2], true);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        mCountly.moduleRatings.recordManualRatingInternal("", 12, vals[1], vals[2], true);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }

    @Test(expected = IllegalStateException.class)
    public void recordManualRatingNullID() {
        mCountly.ratings().recordManualRating(null, 3, "ss", "qq", true);
    }

    @Test(expected = IllegalStateException.class)
    public void recordManualRatingEmptyID() {
        mCountly.ratings().recordManualRating("", 3, "ss", "qq", true);
    }

    @Test
    public void getAutomaticSessionLimit() {
        Countly countly = new Countly();
        CountlyConfig config = (new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setStarRatingSessionLimit(44);
        countly.init(config);
        Assert.assertEquals(44, countly.ratings().getAutomaticStarRatingSessionLimit());
    }

    @Test
    public void getCurrentSessionCount() {
        mCountly.ratings().clearAutomaticStarRatingSessionCount();

        Assert.assertEquals(0, mCountly.ratings().getCurrentVersionsSessionCount());

        mCountly.moduleRatings.registerAppSession(TestUtils.getContext(), null);
        mCountly.moduleRatings.registerAppSession(TestUtils.getContext(), null);

        Assert.assertEquals(2, mCountly.ratings().getCurrentVersionsSessionCount());

        mCountly.ratings().clearAutomaticStarRatingSessionCount();

        Assert.assertEquals(0, mCountly.ratings().getCurrentVersionsSessionCount());
    }

    @Test
    public void setIfStarRatingShouldBeShownAutomatically() {
        Assert.assertFalse(mCountly.moduleRatings.getIfStarRatingShouldBeShownAutomatically());

        Countly countly = new Countly();
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setIfStarRatingShownAutomatically(true);
        countly.init(config);

        Assert.assertTrue(countly.moduleRatings.getIfStarRatingShouldBeShownAutomatically());
    }

    @Test
    public void loadRatingPreferencesBadJson() {
        StorageProvider cs = mCountly.connectionQueue_.getStorageProvider();
        cs.setStarRatingPreferences("./{}23[]d");
        Assert.assertEquals("./{}23[]d", cs.getStarRatingPreferences());
        ModuleRatings.StarRatingPreferences srp = ModuleRatings.loadStarRatingPreferences(cs, new ModuleLog());

        Assert.assertEquals("", srp.appVersion);
        Assert.assertEquals(5, srp.sessionLimit);
        Assert.assertEquals(0, srp.sessionAmount);
        Assert.assertFalse(srp.isShownForCurrentVersion);
        Assert.assertFalse(srp.automaticRatingShouldBeShown);
        Assert.assertFalse(srp.disabledAutomaticForNewVersions);
        Assert.assertFalse(srp.automaticHasBeenShown);
        Assert.assertTrue(srp.isDialogCancellable);
        Assert.assertEquals("App rating", srp.dialogTextTitle);
        Assert.assertEquals("Please rate this app", srp.dialogTextMessage);
        Assert.assertEquals("Cancel", srp.dialogTextDismiss);
    }

    /**
     * Value size limit is applied to the email and the comment of the manual rating
     * "recordManualRating" and "recordRatingWidgetWithID" methods are tested
     * Validate that events exist and contains the truncated values of the email and the comment
     */
    @Test
    public void internalLimits_recordManualRating_maxValueSize() throws JSONException {
        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.sdkInternalLimits.setMaxValueSize(1);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        countly.ratings().recordManualRating("A", 3, "email", "comment", true);

        Map<String, Object> ratingSegmentation = prepareRatingSegmentation("3", "A", "e", "c", true);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, ratingSegmentation, 0);

        countly.ratings().recordRatingWidgetWithID("B", 5, "aaa@bbb.com", "very_good", false);

        ratingSegmentation = prepareRatingSegmentation("5", "B", "a", "v", false);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, ratingSegmentation, 1);
    }

    /**
     * Value size limit is applied to the email and the comment of the manual rating with server config provided
     * "recordManualRating" and "recordRatingWidgetWithID" methods are tested
     * Validate that events exist and contains the truncated values of the email and the comment
     */
    @Test
    public void serverConfig_recordManualRating_maxValueSize() throws JSONException {
        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.immediateRequestGenerator = ModuleConfigurationTests.createIRGForSpecificResponse(new ServerConfigBuilder().valueSizeLimit(1).build());
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        countly.ratings().recordManualRating("A", 3, "email", "comment", true);

        Map<String, Object> ratingSegmentation = prepareRatingSegmentation("3", "A", "e", "c", true);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, ratingSegmentation, 0);

        countly.ratings().recordRatingWidgetWithID("B", 5, "aaa@bbb.com", "very_good", false);

        ratingSegmentation = prepareRatingSegmentation("5", "B", "a", "v", false);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, ratingSegmentation, 1);
    }

    /**
     * Reproduction + regression test for the temporary-device-ID leak.
     * The rating feedback popup performs an immediate request to "/o/feedback/widget" that carries
     * "device_id". While in temporary device ID mode this must not be sent, otherwise a
     * "CLYTemporaryDeviceID" user is created on the server.
     *
     * 1- Init with a capturing immediate-request generator and a real (custom) device ID
     * 2- Enter temporary device ID mode
     * 3- Call showFeedbackPopupInternal with valid widget id, consent and a (mock) Activity
     * 4- Verify no immediate request is issued (no endpoint captured)
     * 5- Verify the developer callback reports that temporary device ID mode blocked it
     */
    @Test
    public void showFeedbackPopup_blockedInTemporaryDeviceIDMode() {
        final List<String> capturedEndpoints = new ArrayList<>();

        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.disableHealthCheck();
        config.immediateRequestGenerator = new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> capturedEndpoints.add(customEndpoint);
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> capturedEndpoints.add(customEndpoint);
            }
        };

        Countly countly = new Countly().init(config);
        countly.deviceId().enableTemporaryIdMode();
        Assert.assertTrue(countly.moduleDeviceId.isTemporaryIdEnabled());

        // ignore anything that init / mode switch may have produced
        capturedEndpoints.clear();

        final String[] callbackMessage = { null };
        countly.moduleRatings.showFeedbackPopupInternal("widget123", "Close", mock(Activity.class), error -> callbackMessage[0] = error);

        Assert.assertEquals(0, capturedEndpoints.size());
        Assert.assertNotNull(callbackMessage[0]);
        Assert.assertTrue(callbackMessage[0].contains("temporary device ID mode"));
    }

    private Map<String, Object> prepareRatingSegmentation(String rating, String widgetId, String email, String comment, boolean userCanBeContacted) {
        Map<String, Object> segm = new ConcurrentHashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("rating", rating);
        segm.put("widget_id", widgetId);
        segm.put("contactMe", userCanBeContacted);

        if (email != null && !email.isEmpty()) {
            segm.put("email", email);
        }

        if (comment != null && !comment.isEmpty()) {
            segm.put("comment", comment);
        }

        return segm;
    }

    /**
     * Manually initialize the rating module and then make sure that the star rating preferences return the correct values
     */
    //@Test
    //public void setAllFieldsDuringInit() {
    //    CountlyConfig config = (new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setStarRatingSessionLimit(44);
    //    config.setStarRatingDisableAskingForEachAppVersion(true);
    //    config.setStarRatingSessionLimit(445);
    //    config.setIfStarRatingShownAutomatically(true);
    //    config.setIfStarRatingDialogIsCancellable(true);
    //    config.setStarRatingTextTitle("dffgg");
    //    config.setStarRatingTextMessage("qwe123");
    //    config.setStarRatingTextDismiss("666");
    //
    //    StarRatingCallback src = new StarRatingCallback() {
    //        @Override
    //        public void onRate(int rating) {
    //
    //        }
    //
    //        @Override
    //        public void onDismiss() {
    //
    //        }
    //    };
    //
    //    config.setStarRatingCallback(src);
    //    StorageProvider sp = mCountly.connectionQueue_.getStorageProvider();
    //
    //    ModuleRatings mr = new ModuleRatings(mCountly, config);
    //
    //    ModuleRatings.StarRatingPreferences srp = ModuleRatings.loadStarRatingPreferences(sp, new ModuleLog());
    //
    //    Assert.assertTrue(mr.getIfStarRatingShouldBeShownAutomatically());
    //    Assert.assertTrue(srp.automaticRatingShouldBeShown);
    //    Assert.assertEquals(mr.starRatingCallback_, src);
    //    Assert.assertEquals(srp.dialogTextMessage, "qwe123");
    //    Assert.assertEquals(srp.dialogTextTitle, "dffgg");
    //    Assert.assertEquals(srp.dialogTextDismiss, "666");
    //    Assert.assertTrue(srp.disabledAutomaticForNewVersions);
    //    Assert.assertTrue(srp.isDialogCancellable);
    //    Assert.assertEquals(445, srp.sessionLimit);
    //}
}
