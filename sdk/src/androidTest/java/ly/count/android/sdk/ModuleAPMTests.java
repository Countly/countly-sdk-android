package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleAPMTests {
    Countly mCountly;

    RequestQueueProvider requestQueueProvider;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());

        requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
    }

    @After
    public void tearDown() {
    }

    @Test
    public void customMetricFilter_invlidFields() {
        Map<String, Integer> customMetrics = new HashMap<>();

        mCountly.moduleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(0, customMetrics.size());

        customMetrics.put("a11", 2);
        customMetrics.put(null, 1);
        customMetrics.put("2", null);
        customMetrics.put("", 44);
        customMetrics.put(null, null);

        mCountly.moduleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(1, customMetrics.size());
    }

    @Test
    public void customMetricFilter_reservedKeys() {
        Map<String, Integer> customMetrics = new HashMap<>();

        mCountly.moduleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(0, customMetrics.size());

        customMetrics.put("a11", 2);

        for (String key : ModuleAPM.reservedKeys) {
            customMetrics.put(key, 4);
        }

        mCountly.moduleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(1, customMetrics.size());
    }

    @Test
    public void customMetricFilter_validKeyName() {
        Map<String, Integer> customMetrics = new HashMap<>();

        mCountly.moduleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(0, customMetrics.size());

        customMetrics.put("a11", 2);
        customMetrics.put("a11111111111111111111111111111111111", 2);
        customMetrics.put("_a11", 2);
        customMetrics.put(" a11", 2);
        customMetrics.put("a11 ", 2);

        mCountly.moduleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(4, customMetrics.size());
    }

    @Test
    public void customMetricToString() {
        Map<String, Integer> customMetrics = new HashMap<>();
        customMetrics.put("a11", 2);
        customMetrics.put("aaa", 23);
        customMetrics.put("a351", 22);
        customMetrics.put("a114", 21);
        customMetrics.put("a1__f1", 24);

        mCountly.moduleAPM.removeReservedInvalidKeys(customMetrics);

        Assert.assertEquals(5, customMetrics.size());

        String metricString = ModuleAPM.customMetricsToString(customMetrics);

        Assert.assertEquals(",\"a11\":2,\"aaa\":23,\"a351\":22,\"a1__f1\":24,\"a114\":21", metricString);
    }

    @Test
    public void recordNetworkTraceBasic() {
        //ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        mCountly.apm().recordNetworkTrace("aaa", 234, 123, 456, 7654, 8765);
        verify(requestQueueProvider).sendAPMNetworkTrace("aaa", (8765L - 7654L), 234, 123, 456, 7654L, 8765L);
    }

    @Test
    public void recordNetworkTraceFalseValues_1() {
        mCountly.apm().recordNetworkTrace("aaa", -100, -123, 456, 7654, 8765);
        verify(requestQueueProvider).sendAPMNetworkTrace("aaa", (8765L - 7654L), 0, 0, 456, 7654L, 8765L);
    }

    @Test
    public void recordNetworkTraceFalseValues_2() {
        mCountly.apm().recordNetworkTrace("aaa", 999, 123, -456, 8765, 7654);
        verify(requestQueueProvider).sendAPMNetworkTrace("aaa", (8765L - 7654L), 0, 123, 0, 7654L, 8765L);
    }

    @Test
    public void recordNetworkTraceStartStop() {
        ArgumentCaptor<Long> duration = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> start = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> stop = ArgumentCaptor.forClass(Long.class);

        String internalTraceKey = "abc|111";

        mCountly.apm().startNetworkRequest("abc", "111");
        Assert.assertTrue(mCountly.moduleAPM.networkTraces.containsKey(internalTraceKey));
        Long starVal = mCountly.moduleAPM.networkTraces.get(internalTraceKey);

        mCountly.apm().endNetworkRequest("abc", "111", 345, 222, 555);

        Assert.assertFalse(mCountly.moduleAPM.networkTraces.containsKey(internalTraceKey));

        verify(requestQueueProvider).sendAPMNetworkTrace(eq("abc"), duration.capture(), eq(345), eq(222), eq(555), start.capture(), stop.capture());

        Assert.assertEquals(starVal, start.getValue());
        Assert.assertTrue(stop.getValue() > start.getValue());
        Assert.assertTrue((stop.getValue() - start.getValue()) < 100);
    }

    @Test
    public void customTrace() {
        String key = "ddd";
        Long ts1 = UtilsTime.currentTimestampMs();
        mCountly.apm().startTrace(key);
        Long ts2 = UtilsTime.currentTimestampMs();

        Assert.assertTrue(mCountly.moduleAPM.codeTraces.containsKey(key));
        Long keyTs = mCountly.moduleAPM.codeTraces.get(key);
        Assert.assertTrue(ts1 < keyTs && keyTs < ts2);

        mCountly.apm().endTrace(key, null);

        ArgumentCaptor<Long> duration = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> start = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> stop = ArgumentCaptor.forClass(Long.class);

        verify(requestQueueProvider).sendAPMCustomTrace(eq(key), duration.capture(), start.capture(), stop.capture(), eq(""));

        Assert.assertEquals(keyTs, start.getValue());
        Assert.assertTrue(stop.getValue() > start.getValue());
        Assert.assertTrue((stop.getValue() - start.getValue()) < 100);
    }

    @Test
    public void cancelTrace() {
        mCountly.apm().startTrace("11");
        mCountly.apm().startTrace("112");
        mCountly.apm().startTrace("113");
        mCountly.apm().startTrace("114");
        mCountly.apm().startTrace("115");

        Assert.assertEquals(5, mCountly.moduleAPM.codeTraces.size());

        mCountly.apm().cancelTrace("113");
        Assert.assertEquals(4, mCountly.moduleAPM.codeTraces.size());
        mCountly.apm().cancelTrace("115");
        Assert.assertEquals(3, mCountly.moduleAPM.codeTraces.size());
        mCountly.apm().cancelTrace("113");
        Assert.assertEquals(3, mCountly.moduleAPM.codeTraces.size());
        mCountly.apm().cancelTrace("11");
        Assert.assertEquals(2, mCountly.moduleAPM.codeTraces.size());
        mCountly.apm().cancelTrace("114");
        Assert.assertEquals(1, mCountly.moduleAPM.codeTraces.size());
        mCountly.apm().cancelTrace("112");
        Assert.assertEquals(0, mCountly.moduleAPM.codeTraces.size());
    }

    @Test
    public void cancelAllTraces() {
        Assert.assertEquals(0, mCountly.moduleAPM.codeTraces.size());

        mCountly.apm().startTrace("11");
        mCountly.apm().startTrace("112");
        mCountly.apm().startTrace("113");
        mCountly.apm().startTrace("114");
        mCountly.apm().startTrace("115");
        mCountly.apm().startNetworkRequest("aa", "11");

        Assert.assertEquals(6, mCountly.moduleAPM.codeTraces.size() + mCountly.moduleAPM.networkTraces.size());
        mCountly.apm().cancelAllTraces();
        Assert.assertEquals(0, mCountly.moduleAPM.codeTraces.size() + mCountly.moduleAPM.networkTraces.size());

        mCountly.apm().cancelTrace("115");
        mCountly.apm().cancelTrace("113");
        mCountly.apm().cancelTrace("11");
        mCountly.apm().cancelTrace("114");
        mCountly.apm().cancelTrace("112");
        Assert.assertEquals(0, mCountly.moduleAPM.codeTraces.size() + mCountly.moduleAPM.networkTraces.size());
    }

    @Test
    public void clearNetworkTraces() {
        Assert.assertEquals(0, mCountly.moduleAPM.networkTraces.size());

        mCountly.apm().startNetworkRequest("aa", "11");
        mCountly.apm().startNetworkRequest("aa", "12");
        mCountly.apm().startNetworkRequest("aa", "13");
        mCountly.apm().startNetworkRequest("aa", "14");

        mCountly.apm().startNetworkRequest("aa2", "13");
        mCountly.apm().startNetworkRequest("aa23", "14");

        Assert.assertEquals(6, mCountly.moduleAPM.networkTraces.size());

        mCountly.moduleAPM.clearNetworkTraces();

        Assert.assertEquals(0, mCountly.moduleAPM.networkTraces.size());
    }
}
