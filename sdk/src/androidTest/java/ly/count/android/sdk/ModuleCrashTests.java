package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleCrashTests {
    Countly mCountly;
    CountlyConfig config;
    RequestQueueProvider requestQueueProvider;

    MockedMetricProvider mmp = new MockedMetricProvider();

    @Before
    public void setUp() {
        TestUtils.getCountyStore().clear();

        mCountly = new Countly();
        config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
    }

    @After
    public void tearDown() {
    }

    @Test
    public void setCrashFilters() {
        CrashFilterCallback callback = new CrashFilterCallback() {
            @Override
            public boolean filterCrash(String crash) {
                if (crash.contains("Secret")) {
                    return true;
                }
                return false;
            }
        };

        Countly countly = new Countly();
        CountlyConfig cConfig = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        cConfig.setCrashFilterCallback(callback);

        countly.init(cConfig);

        Assert.assertEquals(callback, countly.moduleCrash.crashFilterCallback);
    }

    @Test
    public void crashFilterTest() {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
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
        CountlyConfig cConfig = (new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

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
        segm2.put("4", 45.4f);

        Assert.assertEquals(segm2, countly.moduleCrash.customCrashSegments);
    }

    @Test
    public void provideCustomCrashSegment_DuringInitAndCall() throws JSONException {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

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
    public void addCrashBreadcrumb() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.metricProviderOverride = mmp;
        Countly countly = new Countly().init(config);

        countly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_3");

        Throwable throwable = new Throwable("Some message");
        countly.crashes().recordUnhandledException(throwable);

        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);
        validateCrash(countly.config_.deviceInfo, extractStackTrace(throwable), "Breadcrumb_1\nBreadcrumb_2\nBreadcrumb_3\n", true, false, new HashMap<>(), 0, new HashMap<>(), new ArrayList<>());
    }

    @Test
    public void addCrashBreadcrumbNullEmpty() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.metricProviderOverride = mmp;
        Countly countly = new Countly().init(config);

        countly.crashes().addCrashBreadcrumb("Breadcrumb_4");
        countly.crashes().addCrashBreadcrumb(null);
        countly.crashes().addCrashBreadcrumb("Breadcrumb_5");
        countly.crashes().addCrashBreadcrumb("");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_6");

        Throwable throwable = new Throwable("Some message");
        countly.crashes().recordUnhandledException(throwable);

        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);
        validateCrash(countly.config_.deviceInfo, extractStackTrace(throwable), "Breadcrumb_4\nBreadcrumb_5\nBreadcrumb_6\n", true, false, new HashMap<>(), 0, new HashMap<>(), new ArrayList<>());
    }

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

    /**
     * Validate that custom crash segmentation is truncated to the maximum allowed length
     * Because length is 2 all global crash segmentation values are dropped and only the last 2
     * of the custom segmentation values are kept
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimits_recordException_maxSegmentationValues() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.metricProviderOverride = mmp;
        config.sdkInternalLimits.setMaxSegmentationValues(2);
        config.crashes.setCustomCrashSegmentation(TestUtils.map("a", "1", "b", "2", "c", "3"));
        Countly countly = new Countly().init(config);

        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, TestUtils.map("d", "4", "e", "5", "f", "6"));
        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), "", false, false, TestUtils.map("e", "5", "f", "6"), 0, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    /**
     * Validate that custom crash segmentation is truncated to the maximum allowed length
     * Because length is 2 only last 2 of the global crash segmentation values are kept
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimits_recordException_maxSegmentationValues_global() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.metricProviderOverride = mmp;
        config.sdkInternalLimits.setMaxSegmentationValues(2);
        config.crashes.setCustomCrashSegmentation(TestUtils.map("a", "1", "b", "2", "c", "3"));
        Countly countly = new Countly().init(config);

        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception);
        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), "", false, false, TestUtils.map("b", "2", "c", "3"), 0, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    private void validateCrash(@NonNull DeviceInfo deviceInfo, @NonNull String error, @NonNull String breadcrumbs, boolean fatal, boolean nativeCrash,
        @NonNull Map<String, Object> customSegmentation, int changedBits, @NonNull Map<String, Object> customMetrics, @NonNull List<String> baseMetricsExclude) throws JSONException {
        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);

        TestUtils.validateRequiredParams(RQ[0]);

        JSONObject crash = new JSONObject(RQ[0].get("crash"));
        int paramCount = validateCrashMetrics(deviceInfo, crash, nativeCrash, customMetrics, baseMetricsExclude);

        if (!error.isEmpty()) {
            paramCount++;
            Assert.assertEquals(error, crash.getString("_error"));
        }

        paramCount += 2;//for nonFatal and ob
        Assert.assertEquals(!fatal, crash.getBoolean("_nonfatal"));
        Assert.assertEquals(changedBits, crash.getInt("_ob"));

        if (!customSegmentation.isEmpty()) {
            paramCount++;
            JSONObject custom = crash.getJSONObject("_custom");
            for (Map.Entry<String, Object> entry : customSegmentation.entrySet()) {
                Assert.assertEquals(entry.getValue(), custom.get(entry.getKey()));
            }
            Assert.assertEquals(custom.length(), customSegmentation.size());
        }

        if (!nativeCrash && !breadcrumbs.isEmpty()) {
            paramCount++;
            Assert.assertEquals(breadcrumbs, crash.getString("_logs"));
        }
        Assert.assertEquals(paramCount, crash.length());
    }

    private int validateCrashMetrics(@NonNull DeviceInfo di, @NonNull JSONObject crash, boolean nativeCrash, @NonNull Map<String, Object> customMetrics, @NonNull List<String> metricsToExclude) throws JSONException {
        int metricCount = 0;

        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_device", di.mp.getDevice(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_os", di.mp.getOS(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_os_version", di.mp.getOSVersion(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_resolution", di.mp.getResolution(TestUtils.getContext()), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_app_version", di.mp.getAppVersion(TestUtils.getContext()), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_manufacturer", di.mp.getManufacturer(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_cpu", di.mp.getCpu(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_opengl", di.mp.getOpenGL(TestUtils.getContext()), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_root", di.mp.isRooted(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_has_hinge", di.mp.hasHinge(TestUtils.getContext()), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_ram_total", di.mp.getRamTotal(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_disk_total", di.mp.getDiskTotal(), crash);

        if (!nativeCrash) {
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_ram_current", di.mp.getRamCurrent(TestUtils.getContext()), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_disk_current", di.mp.getDiskCurrent(), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_run", di.mp.getRunningTime(), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_background", di.isInBackground(), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_muted", di.mp.isMuted(TestUtils.getContext()), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_orientation", di.mp.getOrientation(TestUtils.getContext()), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_online", di.mp.isOnline(TestUtils.getContext()), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_bat", di.mp.getBatteryLevel(TestUtils.getContext()), crash);
        } else {
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_native_cpp", "true", crash);
        }

        for (Map.Entry<String, Object> entry : customMetrics.entrySet()) {
            Assert.assertEquals(entry.getValue(), crash.get(entry.getKey()));
        }
        metricCount += customMetrics.size();

        return metricCount;
    }

    private int assertEqualsMetricIfNotExcluded(List<String> metricsToExclude, String metric, Object value, JSONObject crash) throws JSONException {
        if (metricsToExclude.contains(metric)) {
            Assert.assertFalse(crash.has(metric));
            return 0;
        }
        Assert.assertEquals("assertEqualsMetricIfNotExcluded,  " + metric + " metric assertion failed in crashes expected:[" + value + "]" + "was:[" + crash.get(metric) + "]", value, crash.get(metric));
        return 1;
    }

    private String extractStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
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
