package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.collections.Sets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleFeedbackTests {
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void parseFeedbackList_null() throws JSONException {
        List<ModuleFeedback.CountlyFeedbackWidget> ret = ModuleFeedback.parseFeedbackList(null);
        Assert.assertNotNull(ret);
        Assert.assertEquals(0, ret.size());
    }

    @Test
    public void parseFeedbackList_oneGoodWithGarbage() throws JSONException {
        String requestJson =
            "{\"result\":[{\"_id\":\"asd\",\"type\":\"qwe\",\"name\":\"zxc\",\"tg\":[]},{\"_id\":\"5f97284635935cc338e78200\",\"type\":\"nps\",\"name\":\"fsdfsdf\",\"tg\":[\"/\"]},{\"g4id\":\"asd1\",\"t4type\":\"432\",\"nagdfgme\":\"zxct\",\"tgm\":[\"/\"]}]}";

        JSONObject jObj = new JSONObject(requestJson);

        List<ModuleFeedback.CountlyFeedbackWidget> ret = ModuleFeedback.parseFeedbackList(jObj);
        Assert.assertNotNull(ret);
        Assert.assertEquals(1, ret.size());
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps, "fsdfsdf", "5f97284635935cc338e78200", new String[] { "/" }, ret.get(0));
    }

    @Test
    public void parseFeedbackList() throws JSONException {
        String requestJson =
            "{\"result\":[{\"_id\":\"5f8c6f959627f99e8e7de746\",\"type\":\"survey\",\"exitPolicy\":\"onAbandon\",\"appearance\":{\"show\":\"uSubmit\",\"position\":\"bLeft\",\"color\":\"#2eb52b\"},\"name\":\"sdfsdfdsf\",\"tg\":[\"/\"]},{\"_id\":\"5f8c6fd81ac8659e8846acf4\",\"type\":\"nps\",\"name\":\"fdsfsd\",\"tg\":[\"a\",\"0\"]},{\"_id\":\"5f97284635935cc338e78200\",\"type\":\"nps\",\"name\":\"fsdfsdf\",\"tg\":[]},{\"_id\":\"614871419f030e44be07d82f\",\"type\":\"rating\",\"appearance\":{\"position\":\"mleft\",\"bg_color\":\"#fff\",\"text_color\":\"#ddd\",\"text\":\"Feedback\"},\"tg\":[\"\\/\"],\"name\":\"ratingName1\"}]}";

        JSONObject jObj = new JSONObject(requestJson);

        List<ModuleFeedback.CountlyFeedbackWidget> ret = ModuleFeedback.parseFeedbackList(jObj);
        Assert.assertNotNull(ret);
        Assert.assertEquals(4, ret.size());

        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.survey, "sdfsdfdsf", "5f8c6f959627f99e8e7de746", new String[] { "/" }, ret.get(0));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps, "fdsfsd", "5f8c6fd81ac8659e8846acf4", new String[] { "a", "0" }, ret.get(1));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps, "fsdfsdf", "5f97284635935cc338e78200", new String[] {}, ret.get(2));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.rating, "ratingName1", "614871419f030e44be07d82f", new String[] { "/" }, ret.get(3));
    }

    @Test
    public void parseFaultyFeedbackList() throws JSONException {
        // 9 widgets (3 from each)
        // First variation => no 'tg' key
        // Second variation => no 'name' key
        // First variation => no '_id' key
        String requestJson =
            "{\"result\":["
                + "{\"_id\":\"survID1\",\"type\":\"survey\",\"exitPolicy\":\"onAbandon\",\"appearance\":{\"show\":\"uSubmit\",\"position\":\"bLeft\",\"color\":\"#2eb52b\"},\"name\":\"surv1\"},"
                + "{\"_id\":\"survID2\",\"type\":\"survey\",\"exitPolicy\":\"onAbandon\",\"appearance\":{\"show\":\"uSubmit\",\"position\":\"bLeft\",\"color\":\"#2eb52b\"},\"tg\":[\"/\"]},"
                + "{\"type\":\"survey\",\"exitPolicy\":\"onAbandon\",\"appearance\":{\"show\":\"uSubmit\",\"position\":\"bLeft\",\"color\":\"#2eb52b\"},\"name\":\"surv3\",\"tg\":[\"/\"]},"
                + "{\"_id\":\"npsID1\",\"type\":\"nps\",\"name\":\"nps1\"},"
                + "{\"_id\":\"npsID2\",\"type\":\"nps\",\"tg\":[]},"
                + "{\"type\":\"nps\",\"name\":\"nps3\",\"tg\":[]},"
                + "{\"_id\":\"ratingID1\",\"type\":\"rating\",\"appearance\":{\"position\":\"mleft\",\"bg_color\":\"#fff\",\"text_color\":\"#ddd\",\"text\":\"Feedback\"},\"name\":\"rating1\"},"
                + "{\"_id\":\"ratingID2\",\"type\":\"rating\",\"appearance\":{\"position\":\"mleft\",\"bg_color\":\"#fff\",\"text_color\":\"#ddd\",\"text\":\"Feedback\"},\"tg\":[\"\\/\"]},"
                + "{\"type\":\"rating\",\"appearance\":{\"position\":\"mleft\",\"bg_color\":\"#fff\",\"text_color\":\"#ddd\",\"text\":\"Feedback\"},\"tg\":[\"\\/\"],\"name\":\"rating3\"}"
                + "]}";

        JSONObject jObj = new JSONObject(requestJson);

        List<ModuleFeedback.CountlyFeedbackWidget> ret = ModuleFeedback.parseFeedbackList(jObj);
        Assert.assertNotNull(ret);
        Assert.assertEquals(6, ret.size());

        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.survey, "surv1", "survID1", new String[] {}, ret.get(0));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.survey, "", "survID2", new String[] { "/" }, ret.get(1));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps, "nps1", "npsID1", new String[] {}, ret.get(2));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps, "", "npsID2", new String[] {}, ret.get(3));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.rating, "rating1", "ratingID1", new String[] {}, ret.get(4));
        ValidateReturnedFeedbackWidget(ModuleFeedback.FeedbackWidgetType.rating, "", "ratingID2", new String[] { "/" }, ret.get(5));
    }

    void ValidateReturnedFeedbackWidget(@NonNull ModuleFeedback.FeedbackWidgetType type, @NonNull String wName, @NonNull String wId, @NonNull String[] wTags, @NonNull ModuleFeedback.CountlyFeedbackWidget fWidget) {
        Assert.assertEquals(type, fWidget.type);
        Assert.assertEquals(wName, fWidget.name);
        Assert.assertEquals(wId, fWidget.widgetId);
        Assert.assertArrayEquals(wTags, fWidget.tags);
    }

    @Test
    public void reportFeedbackWidgetManuallyNPSReported() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.nps;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        final Map<String, Object> segmRes = new HashMap<>();
        segmRes.put("rating", 4);
        segmRes.put("comment", "123456");

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.put("rating", 4);
        segm.put("comment", "123456");

        verify(ep).recordEventInternal(ModuleFeedback.NPS_EVENT_KEY, segm, 1, 0, 0, null, null);

        //report without a "null" comment
        mCountly.moduleEvents.eventQueueProvider = mock(EventQueueProvider.class);

        segmRes.put("rating", 10);
        segmRes.put("comment", null);
        segm.put("rating", 10);
        segm.remove("comment");

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep).recordEventInternal(ModuleFeedback.NPS_EVENT_KEY, segm, 1, 0, 0, null, null);
    }

    @Test
    public void reportFeedbackWidgetManuallyRatingReported() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.rating;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        final Map<String, Object> segmRes = new HashMap<>();
        segmRes.put("rating", 4);
        segmRes.put("comment", "123456");
        segmRes.put("email", "123456");
        segmRes.put("contactMe", true);

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.put("rating", 4);
        segm.put("comment", "123456");
        segm.put("email", "123456");
        segm.put("contactMe", true);

        verify(ep).recordEventInternal(ModuleFeedback.RATING_EVENT_KEY, segm, 1, 0, 0, null, null);

        //report without a "null" comment
        mCountly.moduleEvents.eventQueueProvider = mock(EventQueueProvider.class);

        segmRes.put("rating", 2);
        segmRes.put("comment", null);
        segm.put("rating", 2);
        segm.remove("comment");

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep).recordEventInternal(ModuleFeedback.RATING_EVENT_KEY, segm, 1, 0, 0, null, null);
    }

    @Test
    public void reportFeedbackWidgetManuallyNPSClosed() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.nps;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, null);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.put("closed", "1");

        verify(ep).recordEventInternal(ModuleFeedback.NPS_EVENT_KEY, segm, 1, 0, 0, null, null);
    }

    @Test
    public void reportFeedbackWidgetManuallyRatingClosed() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.rating;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, null);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.put("closed", "1");

        verify(ep).recordEventInternal(ModuleFeedback.RATING_EVENT_KEY, segm, 1, 0, 0, null, null);
    }

    @Test
    public void reportFeedbackWidgetManuallyNPSBadResult_1() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.nps;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        final Map<String, Object> segmRes = new HashMap<>();

        //just an empty result map
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        //result map with unrelated fields
        JSONObject emptyJObj = new JSONObject();
        segmRes.put("bla", "gg");
        segmRes.put("11", null);
        segmRes.put(null, "gf");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }

    @Test
    public void reportFeedbackWidgetManuallyRatingBadResult_1() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.rating;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        final Map<String, Object> segmRes = new HashMap<>();

        //just an empty result map
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        //result map with unrelated fields
        JSONObject emptyJObj = new JSONObject();
        segmRes.put("bla", "gg");
        segmRes.put("11", null);
        segmRes.put(null, "gf");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }

    @Test
    public void reportFeedbackWidgetManuallyNPSBadResult_2() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.nps;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        final Map<String, Object> segmRes = new HashMap<>();

        //result map with unrelated fields
        segmRes.put("rating", "gg");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", null);
        segmRes.put("comment", "123456");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "5.5");
        JSONObject emptyJObj = new JSONObject();
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "6.0");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "0.0");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "10.0f");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }

    @Test
    public void reportFeedbackWidgetManuallyRatingBadResult_2() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.rating;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        final Map<String, Object> segmRes = new HashMap<>();

        //result map with unrelated fields
        segmRes.put("rating", "gg");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", null);
        segmRes.put("comment", "123456");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "5.5");
        JSONObject emptyJObj = new JSONObject();
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "6.0");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "0.0");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        segmRes.put("rating", "10.0f");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }

    @Test
    public void reportFeedbackWidgetManuallySurveyClosed() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.survey);

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, null);

        final Map<String, Object> segm = TestUtils.map("closed", "1");
        fillFeedbackWidgetSegmentationParams(segm, widgetInfo.widgetId);

        verify(ep).recordEventInternal(ModuleFeedback.SURVEY_EVENT_KEY, segm, 1, 0, 0, null, null);
    }

    /**
     * Test that the internal limit for key length is working
     * And validate while reporting a survey widget manually, key and segmentation is not truncated
     */
    @Test
    public void internalLimit_reportFeedbackWidgetManuallySurvey() throws JSONException {
        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.sdkInternalLimits.setMaxKeyLength(2);
        Countly countly = new Countly().init(config);

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.survey);

        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("key1", "value1", "key2", "value2", "key3", "value3"));

        final Map<String, Object> segm = TestUtils.map("key1", "value1", "key2", "value2", "key3", "value3");
        fillFeedbackWidgetSegmentationParams(segm, widgetInfo.widgetId);

        ModuleEventsTests.validateEventInRQ(ModuleFeedback.SURVEY_EVENT_KEY, segm, 0);
    }

    /**
     * Test that the internal limit for key length is working
     * And validate while reporting a rating widget manually, key and segmentation is not truncated
     */
    @Test
    public void internalLimit_reportFeedbackWidgetManuallyRating() throws JSONException {
        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.sdkInternalLimits.setMaxKeyLength(2);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.rating);

        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("rating", 10));

        final Map<String, Object> segm = TestUtils.map("rating", 10);
        fillFeedbackWidgetSegmentationParams(segm, widgetInfo.widgetId);

        ModuleEventsTests.validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, segm, 0);
    }

    /**
     * Test that the internal limit for key length is working
     * And validate while reporting a nps widget manually, key and segmentation is not truncated
     */
    @Test
    public void internalLimit_reportFeedbackWidgetManuallyNPS() throws JSONException {
        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.sdkInternalLimits.setMaxKeyLength(2);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps);

        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("rating", 10, "comment", "huhu"));

        final Map<String, Object> segm = TestUtils.map("rating", 10, "comment", "huhu");
        fillFeedbackWidgetSegmentationParams(segm, widgetInfo.widgetId);

        ModuleEventsTests.validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, segm, 0);
    }

    /**
     * Value size limit is applied to the all string values of widget results
     * And validate while reporting a survey widget manually, value is truncated to the limit
     * And unsupported types are removed
     * All types of feedback widgets are tested NPS, RATING, SURVEY
     */
    @Test
    public void internalLimit_reportFeedbackWidgetManually_setMaxValueSize() throws JSONException {
        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true);
        config.sdkInternalLimits.setMaxValueSize(2);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        //NPS
        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps);
        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("rating", 10, "comment", "huhu", "extras", "sure_go_on", "map", TestUtils.map("key1", "value1", "key2", "value2"), "omg", Double.MAX_VALUE));

        Map<String, Object> segm = TestUtils.map("rating", 10, "comment", "hu", "extras", "su", "omg", Double.MAX_VALUE);
        fillFeedbackWidgetSegmentationParams(segm, widgetInfo.widgetId);

        ModuleEventsTests.validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, segm, 0);

        //RATING
        widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.rating);
        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("rating", 10, "comment", "zoomzoom", "map", TestUtils.map("key1", "value1", "key2", "value2"), "omg", Double.MIN_VALUE));

        segm = TestUtils.map("rating", 10, "comment", "zo", "omg", Double.MIN_VALUE);
        fillFeedbackWidgetSegmentationParams(segm, widgetInfo.widgetId);

        ModuleEventsTests.validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, segm, 1);

        //SURVEY
        widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.survey);
        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("key1", "value1", "key2", "value2", "key3", "value3", "map", TestUtils.map("key1", "value1", "key2", "value2"), "int", Integer.MAX_VALUE));

        segm = TestUtils.map("key1", "va", "key2", "va", "key3", "va", "int", Integer.MAX_VALUE);
        fillFeedbackWidgetSegmentationParams(segm, widgetInfo.widgetId);

        ModuleEventsTests.validateEventInRQ(ModuleFeedback.SURVEY_EVENT_KEY, segm, 2);
    }

    /**
     * "reportFeedbackWidgetManually" with Array segmentations
     * Validate that all primitive types arrays are successfully recorded
     * And validate that Object arrays are not recorded
     * But Generic type of Object array which its values are only primitive types are recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void reportFeedbackWidgetManually_validateSupportedArrays() throws JSONException {
        int[] arr = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        boolean[] arrB = { true, false, true, false, true, false, true, false, true, false };
        String[] arrS = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };
        long[] arrL = { Long.MAX_VALUE, Long.MIN_VALUE };
        double[] arrD = { Double.MAX_VALUE, Double.MIN_VALUE };
        Long[] arrLO = { Long.MAX_VALUE, Long.MIN_VALUE };
        Double[] arrDO = { Double.MAX_VALUE, Double.MIN_VALUE };
        Boolean[] arrBO = { Boolean.TRUE, Boolean.FALSE };
        Integer[] arrIO = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        Object[] arrObj = { "1", 1, 1.1d, true, 1.1f, Long.MAX_VALUE };
        Object[] arrObjStr = { "1", "1", "1.1d", "true", "1.1f", "Long.MAX_VALUE" };

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrLO", arrLO,
            "arrDO", arrDO,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj,
            "arrObjStr", arrObjStr, "rating", 10, "comment", "huhu"
        );

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps);
        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmentation);

        Map<String, Object> expectedSegmentation = TestUtils.map(
            "arr", new JSONArray(arr),
            "arrB", new JSONArray(arrB),
            "arrS", new JSONArray(arrS),
            "arrL", new JSONArray(arrL),
            "arrD", new JSONArray(arrD),
            "arrLO", new JSONArray(arrLO),
            "arrDO", new JSONArray(arrDO),
            "arrBO", new JSONArray(arrBO),
            "arrIO", new JSONArray(arrIO), "rating", 10, "comment", "huhu"
        );

        fillFeedbackWidgetSegmentationParams(expectedSegmentation, widgetInfo.widgetId);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, expectedSegmentation, 0);
    }

    /**
     * "reportFeedbackWidgetManually" with List segmentations
     * Validate that all primitive types Lists are successfully recorded
     * And validate that List of Objects is not recorded
     * But Generic type of Object list which its values are only primitive types are recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void reportFeedbackWidgetManually_validateSupportedLists() throws JSONException {
        List<Integer> arr = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Boolean> arrB = Arrays.asList(true, false, true, false, true, false, true, false, true, false);
        List<String> arrS = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        List<Long> arrLO = Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE);
        List<Double> arrDO = Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE);
        List<Boolean> arrBO = Arrays.asList(Boolean.TRUE, Boolean.FALSE);
        List<Integer> arrIO = Arrays.asList(Integer.MAX_VALUE, Integer.MIN_VALUE);
        List<Object> arrObj = Arrays.asList("1", 1, 1.1d, true, Long.MAX_VALUE);
        List<Object> arrObjStr = Arrays.asList("1", "1", "1.1d", "true", "Long.MAX_VALUE");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        // Create segmentation using maps with lists
        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrLO", arrLO,
            "arrDO", arrDO,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj,
            "arrObjStr", arrObjStr, "rating", 10, "comment", "huhu"
        );

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps);
        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmentation);

        Map<String, Object> expectedSegmentation = TestUtils.map(
            "arr", new JSONArray(arr),
            "arrB", new JSONArray(arrB),
            "arrS", new JSONArray(arrS),
            "arrLO", new JSONArray(arrLO),
            "arrDO", new JSONArray(arrDO),
            "arrBO", new JSONArray(arrBO),
            "arrIO", new JSONArray(arrIO),
            "arrObjStr", new JSONArray(arrObjStr), "rating", 10, "comment", "huhu"
        );

        fillFeedbackWidgetSegmentationParams(expectedSegmentation, widgetInfo.widgetId);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, expectedSegmentation, 0);
    }

    /**
     * "reportFeedbackWidgetManually" with JSONArray segmentations
     * Validate that all primitive types JSONArrays are successfully recorded
     * And validate and JSONArray of Objects is not recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void reportFeedbackWidgetManually_validateSupportedJSONArrays() throws JSONException {
        JSONArray arr = new JSONArray(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        JSONArray arrB = new JSONArray(Arrays.asList(true, false, true, false, true, false, true, false, true, false));
        JSONArray arrS = new JSONArray(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        JSONArray arrL = new JSONArray(Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE));
        JSONArray arrD = new JSONArray(Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE));
        JSONArray arrBO = new JSONArray(Arrays.asList(Boolean.TRUE, Boolean.FALSE));
        JSONArray arrIO = new JSONArray(Arrays.asList(Integer.MAX_VALUE, Integer.MIN_VALUE));
        JSONArray arrObj = new JSONArray(Arrays.asList("1", 1, 1.1d, true, Long.MAX_VALUE));

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        // Create segmentation using maps with lists
        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj, "rating", 10, "comment", "huhu"
        );

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps);
        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmentation);

        // Prepare expected segmentation with JSONArrays
        Map<String, Object> expectedSegmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrBO", arrBO,
            "arrIO", arrIO, "rating", 10, "comment", "huhu"
        );

        fillFeedbackWidgetSegmentationParams(expectedSegmentation, widgetInfo.widgetId);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, expectedSegmentation, 0);
    }

    /**
     * "reportFeedbackWidgetManually" with invalid data types
     * Validate that unsupported data types are not recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void reportFeedbackWidgetManually_unsupportedDataTypesSegmentation() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "a", TestUtils.map(),
            "b", TestUtils.json(),
            "c", new Object(),
            "d", Sets.newSet(),
            "e", Mockito.mock(ModuleLog.class), "rating", 10, "comment", "huhu"
        );

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = createFeedbackWidget(ModuleFeedback.FeedbackWidgetType.nps);
        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmentation);
        Map<String, Object> expectedSegmentation = TestUtils.map("rating", 10, "comment", "huhu");

        fillFeedbackWidgetSegmentationParams(expectedSegmentation, widgetInfo.widgetId);
        ModuleEventsTests.validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, expectedSegmentation, 0);
    }

    private ModuleFeedback.CountlyFeedbackWidget createFeedbackWidget(ModuleFeedback.FeedbackWidgetType type) {
        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = type;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";
        return widgetInfo;
    }

    private void fillFeedbackWidgetSegmentationParams(Map<String, Object> segmentation, String widgetId) {
        segmentation.put("platform", "android");
        segmentation.put("app_version", "1.0");
        segmentation.put("widget_id", widgetId);
    }
}
