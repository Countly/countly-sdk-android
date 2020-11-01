package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleFeedbackTests {
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
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
}
