package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * Tests for runtime custom header manipulation via Countly.addCustomNetworkRequestHeaders(Map<String,String>)
 */
@RunWith(AndroidJUnit4.class)
public class CustomHeaderRuntimeTests {

    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true));
    }

    @Test
    public void testRuntimeAddAndOverrideHeaders() throws Exception {
        // Add initial headers at runtime
        Map<String, String> initial = new HashMap<>();
        initial.put("X-First", "One");
        initial.put("X-Second", "Two");
        mCountly.requestQueue().addCustomNetworkRequestHeaders(initial);

        // Override one and add a new one
        Map<String, String> second = new HashMap<>();
        second.put("X-Second", "TwoOverride");
        second.put("X-Third", "Three");
        mCountly.requestQueue().addCustomNetworkRequestHeaders(second);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test.count.ly",
            mCountly.countlyStore,
            mCountly.config_.deviceIdProvider,
            mCountly.config_.configProvider,
            mCountly.connectionQueue_.requestInfoProvider,
            null,
            mCountly.requestHeaderCustomValues,
            mCountly.L,
            mCountly.config_.healthTracker,
            mock(Runnable.class),
            new ConcurrentHashMap<>()
        );

        URLConnection urlConnection = cp.urlConnectionForServerRequest("a=b", null);

        Assert.assertEquals("One", urlConnection.getRequestProperty("X-First"));
        Assert.assertEquals("TwoOverride", urlConnection.getRequestProperty("X-Second"));
        Assert.assertEquals("Three", urlConnection.getRequestProperty("X-Third"));
    }
}
