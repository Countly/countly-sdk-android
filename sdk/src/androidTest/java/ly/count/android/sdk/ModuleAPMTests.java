package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleAPMTests {
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
    public void customMetricFilter_invlidFields() {
        Map<String, Integer> customMetrics = new HashMap<>();

        ModuleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(0, customMetrics.size());

        customMetrics.put("a11", 2);
        customMetrics.put(null, 1);
        customMetrics.put("2", null);
        customMetrics.put("", 44);
        customMetrics.put(null, null);

        ModuleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(1, customMetrics.size());
    }

    @Test
    public void customMetricFilter_reservedKeys() {
        Map<String, Integer> customMetrics = new HashMap<>();

        ModuleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(0, customMetrics.size());

        customMetrics.put("a11", 2);

        for(String key:ModuleAPM.reservedKeys) {
            customMetrics.put(key, 4);
        }

        ModuleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(1, customMetrics.size());
    }

    @Test
    public void customMetricFilter_validKeyName() {
        Map<String, Integer> customMetrics = new HashMap<>();

        ModuleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(0, customMetrics.size());

        customMetrics.put("a11", 2);
        customMetrics.put("a11111111111111111111111111111111111", 2);
        customMetrics.put("_a11", 2);
        customMetrics.put(" a11", 2);
        customMetrics.put("a11 ", 2);


        ModuleAPM.removeReservedInvalidKeys(customMetrics);
        Assert.assertEquals(1, customMetrics.size());
    }

    @Test
    public void customMetricToString() {
        Map<String, Integer> customMetrics = new HashMap<>();
        customMetrics.put("a11", 2);
        customMetrics.put("aaa", 23);
        customMetrics.put("a351", 22);
        customMetrics.put("a114", 21);
        customMetrics.put("a1__f1", 24);


        ModuleAPM.removeReservedInvalidKeys(customMetrics);

        Assert.assertEquals(5, customMetrics.size());

        String metricString = ModuleAPM.customMetricsToString(customMetrics);

        Assert.assertEquals(",\"a11\":2,\"aaa\":23,\"a351\":22,\"a1__f1\":24,\"a114\":21", metricString);
    }
}
