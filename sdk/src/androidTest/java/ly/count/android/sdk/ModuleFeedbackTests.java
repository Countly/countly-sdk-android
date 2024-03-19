package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
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

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.survey;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, null);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.put("closed", "1");

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

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.survey;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("key1", "value1", "key2", "value2", "key3", "value3"));

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.putAll(TestUtils.map("key1", "value1", "key2", "value2", "key3", "value3"));

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

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.rating;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("rating", 10));

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.put("rating", 10);
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

        ModuleFeedback.CountlyFeedbackWidget widgetInfo = new ModuleFeedback.CountlyFeedbackWidget();
        widgetInfo.type = ModuleFeedback.FeedbackWidgetType.nps;
        widgetInfo.widgetId = "1234";
        widgetInfo.name = "someName";

        countly.feedback().reportFeedbackWidgetManually(widgetInfo, null, TestUtils.map("rating", 10, "comment", "huhu"));

        final Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("widget_id", widgetInfo.widgetId);
        segm.put("rating", 10);
        segm.put("comment", "huhu");

        ModuleEventsTests.validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, segm, 0);
    }
}
