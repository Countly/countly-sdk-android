package ly.count.android.sdk;

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

import static androidx.test.InstrumentationRegistry.getContext;
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
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
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
    public void parseFeedbackList() throws JSONException {
        String requestJson =
            "{\"result\":[{\"_id\":\"5f8c6f959627f99e8e7de746\",\"type\":\"survey\",\"exitPolicy\":\"onAbandon\",\"appearance\":{\"show\":\"uSubmit\",\"position\":\"bLeft\",\"color\":\"#2eb52b\"},\"name\":\"sdfsdfdsf\"},{\"_id\":\"5f8c6fd81ac8659e8846acf4\",\"type\":\"nps\",\"name\":\"fdsfsd\"},{\"_id\":\"5f97284635935cc338e78200\",\"type\":\"nps\",\"name\":\"fsdfsdf\"}]}";

        JSONObject jObj = new JSONObject(requestJson);

        List<ModuleFeedback.CountlyFeedbackWidget> ret = ModuleFeedback.parseFeedbackList(jObj);
        Assert.assertNotNull(ret);
        Assert.assertEquals(3, ret.size());

        Assert.assertEquals(ModuleFeedback.FeedbackWidgetType.survey, ret.get(0).type);
        Assert.assertEquals(ModuleFeedback.FeedbackWidgetType.nps, ret.get(1).type);
        Assert.assertEquals(ModuleFeedback.FeedbackWidgetType.nps, ret.get(2).type);

        Assert.assertEquals("sdfsdfdsf", ret.get(0).name);
        Assert.assertEquals("fdsfsd", ret.get(1).name);
        Assert.assertEquals("fsdfsdf", ret.get(2).name);

        Assert.assertEquals("5f8c6f959627f99e8e7de746", ret.get(0).widgetId);
        Assert.assertEquals("5f8c6fd81ac8659e8846acf4", ret.get(1).widgetId);
        Assert.assertEquals("5f97284635935cc338e78200", ret.get(2).widgetId);
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

        verify(ep).recordEventInternal(ModuleFeedback.NPS_EVENT_KEY, segm, 1, 0, 0, null);

        //report without a "null" comment
        mCountly.moduleEvents.eventQueueProvider = mock(EventQueueProvider.class);

        segmRes.put("rating", 10);
        segmRes.put("comment", null);
        segm.put("rating", 10);
        segm.remove("comment");

        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep).recordEventInternal(ModuleFeedback.NPS_EVENT_KEY, segm, 1, 0, 0, null);
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

        verify(ep).recordEventInternal(ModuleFeedback.NPS_EVENT_KEY, segm, 1, 0, 0, null);
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
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        //result map with unrelated fields
        JSONObject emptyJObj = new JSONObject();
        segmRes.put("bla", "gg");
        segmRes.put("11", null);
        segmRes.put(null, "gf");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
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
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        segmRes.put("rating", "");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        segmRes.put("rating", null);
        segmRes.put("comment", "123456");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, null, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        segmRes.put("rating", "5.5");
        JSONObject emptyJObj = new JSONObject();
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        segmRes.put("rating", "6.0");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        segmRes.put("rating", "0.0");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        segmRes.put("rating", "10.0f");
        mCountly.feedback().reportFeedbackWidgetManually(widgetInfo, emptyJObj, segmRes);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
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

        verify(ep).recordEventInternal(ModuleFeedback.SURVEY_EVENT_KEY, segm, 1, 0, 0, null);
    }
}
