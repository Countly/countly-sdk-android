package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleCrashTests {
    Countly mCountly;
    CountlyConfig config;
    RequestQueueProvider requestQueueProvider;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        config = new CountlyConfig(getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
    }

    @After
    public void tearDown() {
    }

    @Test
    public void setCrashFilters() {
        GlobalCrashFilterCallback callback = new GlobalCrashFilterCallback() {
            @Override
            public boolean filterCrash(CrashData crash) {
                if (crash.stackTrace.contains("Secret")) {
                    return true;
                }
                return false;
            }
        };

        Countly countly = new Countly();
        CountlyConfig cConfig = new CountlyConfig(getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        cConfig.crashes.setGlobalCrashFilterCallback(callback);

        countly.init(cConfig);

        Assert.assertEquals(callback, countly.moduleCrash.globalCrashFilterCallback);
    }

    @Test
    public void crashFilterTest() {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        cConfig.setCrashFilterCallback(new CrashFilterCallback() {
            @Override
            public boolean filterCrash(String crash) {
                if (crash.contains("Secret")) {
                    return true;
                }
                return false;
            }
        });

        countly.init(cConfig);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(countly, mock(RequestQueueProvider.class));

        Exception exception = new Exception("Secret message");

        countly.crashes().recordHandledException(exception);

        verify(requestQueueProvider, never()).sendCrashReport(any(String.class), any(Boolean.class));

        Throwable throwable = new Throwable("Secret message");

        countly.crashes().recordUnhandledException(throwable);

        verify(requestQueueProvider, never()).sendCrashReport(any(String.class), any(Boolean.class));

        exception = new Exception("Reasonable message");

        countly.crashes().recordHandledException(exception);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(requestQueueProvider).sendCrashReport(arg.capture(), any(Boolean.class));

        //todo improve this
        Assert.assertTrue(arg.getValue().contains("java.lang.Exception: Reasonable message\\n" +
            "\\tat ly.count.android.sdk.ModuleCrashTests.crashFilterTest(ModuleCrashTests.java:"));
    }

    @Test
    public void provideCustomCrashSegment_DuringInit() {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

        Map<String, Object> segm = new HashMap<>();
        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);
        segm.put("4", 45.4f);
        segm.put("41", new Object());
        segm.put("42", new int[] { 1, 2 });

        cConfig.setCustomCrashSegment(segm);

        countly.init(cConfig);

        Map<String, Object> segm2 = new HashMap<>();
        segm2.put("aa", "dd");
        segm2.put("aa1", "dda");
        segm2.put("1", 1234);
        segm2.put("2", 1234.55d);
        segm2.put("3", true);
        segm2.put("4", (double) 45.4f);

        Assert.assertEquals(segm2, countly.moduleCrash.customCrashSegments);
    }

    @Test
    public void provideCustomCrashSegment_DuringInitAndCall() throws JSONException {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

        Map<String, Object> segm = new HashMap<>();
        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);

        cConfig.setCustomCrashSegment(segm);

        countly.init(cConfig);
        requestQueueProvider = TestUtils.setRequestQueueProviderToMock(countly, mock(RequestQueueProvider.class));

        //validating values set by init
        Map<String, Object> segm2 = new HashMap<>();
        segm2.put("aa", "dd");
        segm2.put("aa1", "dda");
        segm2.put("1", 1234);
        Assert.assertEquals(segm2, countly.moduleCrash.customCrashSegments);

        //prepare new segm to be provided during recording
        Map<String, Object> segm3 = new HashMap<>();
        segm3.put("1", 54);
        segm3.put("2", 1234.55d);
        segm3.put("3", true);

        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, segm3);
        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(requestQueueProvider).sendCrashReport(arg.capture(), any(Boolean.class));

        String argVal = arg.getValue();

        JSONObject jobj = new JSONObject(argVal);
        Assert.assertTrue(jobj.getString("_error").startsWith("java.lang.Exception: Some message"));
        JSONObject jCus = jobj.getJSONObject("_custom");
        Assert.assertEquals(5, jCus.length());
        Assert.assertEquals("dd", jCus.get("aa"));
        Assert.assertEquals("dda", jCus.get("aa1"));
        Assert.assertEquals(54, jCus.get("1"));
        Assert.assertEquals(1234.55d, jCus.get("2"));
        Assert.assertEquals(true, jCus.get("3"));
    }

    @Test
    public void addCrashBreadcrumb() {
        mCountly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        mCountly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        mCountly.crashes().addCrashBreadcrumb("Breadcrumb_3");

        String logs = DeviceInfo.getLogs();

        Assert.assertEquals("Breadcrumb_1\nBreadcrumb_2\nBreadcrumb_3\n", logs);
    }

    /**
     * Make sure that breadcrumbs are controlled by the count limit and the value length limit
     */
    //@Test
    //public void addCrashBreadcrumbLimits() {
    //    Countly countly = new Countly();
    //    config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting()
    //        .setMaxBreadcrumbCount(2).setMaxValueSize(5);
    //    countly.init(config);
    //
    //    countly.crashes().addCrashBreadcrumb("Brc_1_aaaa");
    //    countly.crashes().addCrashBreadcrumb("Brc_2_aaaa");
    //    countly.crashes().addCrashBreadcrumb("Brc_3_aaaa");
    //    countly.crashes().addCrashBreadcrumb("Brc_4_aaaa");
    //
    //    String logs = CrashDetails.getLogs();
    //
    //    Assert.assertEquals("Brc_3\nBrc_4\n", logs);
    //}
    @Test
    public void recordHandledExceptionException() {
        Exception exception = new Exception("Some message");

        mCountly.crashes().recordHandledException(exception);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(requestQueueProvider).sendCrashReport(arg.capture(), any(Boolean.class));

        //todo improve this
        Assert.assertTrue(arg.getValue().contains("java.lang.Exception: Some message\\n" +
            "\\tat ly.count.android.sdk.ModuleCrashTests.recordHandledExceptionException(ModuleCrashTests.java:"));
    }

    @Test
    public void recordHandledExceptionThrowable() {
        Throwable throwable = new Throwable("Some message");

        mCountly.crashes().recordHandledException(throwable);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(requestQueueProvider).sendCrashReport(arg.capture(), any(Boolean.class));

        String crash = arg.getValue();

        //todo improve this
        Assert.assertTrue(crash.contains("java.lang.Throwable: Some message\\n" +
            "\\tat ly.count.android.sdk.ModuleCrashTests.recordHandledExceptionThrowable(ModuleCrashTests.java:"));
    }

    @Test
    public void recordUnhandledExceptionException() {
        Exception exception = new Exception("Some message");

        mCountly.crashes().recordUnhandledException(exception);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(requestQueueProvider).sendCrashReport(arg.capture(), any(Boolean.class));

        String crash = arg.getValue();

        //todo improve this
        Assert.assertTrue(crash.contains("java.lang.Exception: Some message\\n" +
            "\\tat ly.count.android.sdk.ModuleCrashTests.recordUnhandledExceptionException(ModuleCrashTests.java:"));
    }

    @Test
    public void recordUnhandledExceptionThrowable() {
        Throwable throwable = new Throwable("Some message");

        mCountly.crashes().recordUnhandledException(throwable);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(requestQueueProvider).sendCrashReport(arg.capture(), any(Boolean.class));

        String crash = arg.getValue();

        //todo improve this
        Assert.assertTrue(crash.contains("java.lang.Throwable: Some message\\n" +
            "\\tat ly.count.android.sdk.ModuleCrashTests.recordUnhandledExceptionThrowable(ModuleCrashTests.java:"));
    }

    @Test(expected = StackOverflowError.class)
    public void crashTest_1() {
        TestUtils.crashTest(1);
    }

    @Test(expected = ArithmeticException.class)
    public void crashTest_2() {
        TestUtils.crashTest(2);
    }

    @Test(expected = RuntimeException.class)
    public void crashTest_4() {
        TestUtils.crashTest(3);
    }

    @Test(expected = NullPointerException.class)
    public void crashTest_5() {
        TestUtils.crashTest(4);
    }
}