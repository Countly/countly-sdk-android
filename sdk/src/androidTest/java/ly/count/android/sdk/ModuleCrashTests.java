package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();

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
        TestUtils.getCountyStore().clear();

        Countly countly = new Countly().init(TestUtils.createBaseConfig());

        countly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_3");

        Throwable throwable = new Throwable("Some message");
        countly.crashes().recordUnhandledException(throwable);

        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);
        validateCrash(countly.config_.deviceInfo, extractStackTrace(throwable), "Breadcrumb_1\nBreadcrumb_2\nBreadcrumb_3\n", true, false, null, 0, null, null);
    }

    @Test
    public void addCrashBreadcrumbNullEmpty() throws JSONException {
        TestUtils.getCountyStore().clear();

        Countly countly = new Countly().init(TestUtils.createBaseConfig());

        countly.crashes().addCrashBreadcrumb("Breadcrumb_4");
        countly.crashes().addCrashBreadcrumb(null);
        countly.crashes().addCrashBreadcrumb("Breadcrumb_5");
        countly.crashes().addCrashBreadcrumb("");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_6");

        Throwable throwable = new Throwable("Some message");
        countly.crashes().recordUnhandledException(throwable);

        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);
        validateCrash(countly.config_.deviceInfo, extractStackTrace(throwable), "Breadcrumb_4\nBreadcrumb_5\nBreadcrumb_6\n", true, false, null, 0, null, null);
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
     * "recordHandledException" with crash filter
     * Validate that first call to the "recordHandledException" is filtered out by the crash filter
     * Validate that second call to the "recordHandledException" is not filtered out by the crash filter
     * Validate second call creates a request in the queue and validate all crash data
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordHandledException_crashFilter() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.setCrashFilterCallback(crash -> crash.contains("Secret"));
        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Secret message");

        countly.crashes().recordHandledException(exception);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception);
        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), null, false, false, null, 0, null, null);
    }

    /**
     * "recordHandledException" with global crash filter
     * Global crash filter is set to filter out crashes that contain "Secret" in the stack trace
     * and to set "fatal" to true for all crashes
     * and to add "secret" key to the crash metrics
     * and to remove "_ram_total" key from the crash metrics
     * and to remove "secret" key from the crash segmentation
     * and to remove crashes that contain "sphinx_no_1" in the crash segmentation
     * Validate that first call to the "recordHandledException" is filtered out by the global crash filter because contains "Secret" in the stack trace
     * Validate that second call to the "recordHandledException" is filtered out by the global crash filter because contains "sphinx_no_1" in the crash segmentation
     * Validate that third call to the "recordHandledException" is not filtered out by the global crash filter
     * Validate third call creates a request in the queue and validate all crash data, fatal is set to true
     * Validate that crash segmentation contains all custom segmentation except "secret"
     * Validate that crash metrics contains all custom metrics except "_ram_total" plus "secret"
     * Validate that crash logs contains all breadcrumbs
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordHandledException_globalCrashFilter() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.crashes.setCustomCrashSegmentation(TestUtils.map("secret", "Minato", "int", Integer.MAX_VALUE, "double", Double.MAX_VALUE, "bool", true, "long", Long.MAX_VALUE, "float", 1.1, "object", new Object(), "array", new int[] { 1, 2 }));
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            if (crash.getStackTrace().contains("Secret")) {
                return true;
            }
            crash.getCrashSegmentation().remove("secret");
            crash.setFatal(true);
            TestUtils.put(crash.getCrashMetrics(), "secret", "Minato");
            crash.getCrashMetrics().remove("_ram_total");

            return crash.getCrashSegmentation().containsKey("sphinx_no_1");
        });
        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Secret message");

        countly.crashes().recordHandledException(exception);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        countly.crashes().recordHandledException(new Exception("Some message"), TestUtils.map("sphinx_no_1", "secret"));
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        countly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, TestUtils.map("sphinx_no", 324));

        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), "Breadcrumb_1\nBreadcrumb_2\n", true, false,
            TestUtils.map("int", Integer.MAX_VALUE,
                "double", Double.MAX_VALUE,
                "bool", true,
                "float", 1.1,
                "sphinx_no", 324), 1011, TestUtils.map("secret", "Minato"), Collections.singletonList("_ram_total"));
    }

    /**
     * "recordHandledException" with global crash filter setting all fields empty
     * Validate that after filtering out the crash, all fields are empty
     * and saved as empty in the request
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordHandledException_globalCrashFilter_allFieldsEmpty() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            crash.setStackTrace("");
            crash.setCrashSegmentation(new HashMap<>());
            crash.setCrashMetrics(new JSONObject());
            crash.setBreadcrumbs(new ArrayList<>());
            crash.setFatal(!crash.getFatal());

            return false;
        });

        Countly countly = new Countly().init(cConfig);

        countly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, TestUtils.map("sphinx_no", 324));

        validateCrash(countly.config_.deviceInfo, null, null, true, false, TestUtils.map(), 11111, null,
            Arrays.asList("_device", "_os", "_os_version", "_resolution", "_app_version", "_manufacturer", "_cpu", "_opengl", "_root", "_has_hinge", "_ram_total", "_disk_total", "_ram_current", "_disk_current", "_run", "_background", "_muted", "_orientation", "_online", "_bat"));
    }

    /**
     * Global crash filter filters out the unhandled crash
     * "recordUnhandledException" calls will be ignored
     * Validate that "recordUnhandledException" calls are ignored
     * and "recordHandledException" calls created requests in the RQ
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordException_globalCrashFilter_dropFatal() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.crashes.setGlobalCrashFilterCallback(CrashData::getFatal);

        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordUnhandledException(exception);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        exception = new Exception("Some message 2");
        countly.crashes().recordHandledException(exception);
        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), null, false, false, null, 0, null, null);
    }

    /**
     * "recordHandledException" with global crash filter setting all fields null
     * Setting null does not have effects on the crash data,
     * Crash data has null protection
     * Validate that after filtering out the crash, all fields should be changed except fatal
     * because we are negating it
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordHandledException_globalCrashFilter_allFieldsNull() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            crash.setStackTrace(null);
            crash.setCrashSegmentation(null);
            crash.setCrashMetrics(null);
            crash.setBreadcrumbs(null);
            crash.setFatal(!crash.getFatal());

            return false;
        });

        Countly countly = new Countly().init(cConfig);

        countly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, TestUtils.map("sphinx_no", 324));

        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), "Breadcrumb_1\nBreadcrumb_2\n", true, false, TestUtils.map("sphinx_no", 324), 1, null, null);
    }

    /**
     * Two crash filter is registered, deprecated and global, because deprecated is registered, global crash filter will not work
     * First crash filter is set to filter out crashes that contain "secret" in the stack trace
     * Global crash filter is set to filter out crashes that contain "secret" in the crash segmentation
     * Validate that first call to the "recordHandledException" and "recordUnhandledException" is filtered out by the crash filter
     * Validate that second call to the "recordHandledException" and "recordUnhandledException" is not filtered out by the global crash filter
     * because we have registered the deprecated crash filter first
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordException_crashFilter_globalCrashFilter() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.setCrashFilterCallback(crash -> crash.contains("secret"));
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> crash.getCrashSegmentation().containsKey("secret"));

        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("secret");
        countly.crashes().recordHandledException(exception);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        countly.crashes().recordUnhandledException(exception);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, TestUtils.map("secret", "secret"));
        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), null, false, false, TestUtils.map("secret", "secret"), 0, null, null);

        TestUtils.getCountyStore().clear();
        countly.crashes().recordUnhandledException(exception, TestUtils.map("secret", "secret"));
        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), null, true, false, TestUtils.map("secret", "secret"), 0, null, null);
    }

    private void validateCrash(DeviceInfo deviceInfo, String error, String breadcrumbs, boolean fatal, boolean nativeCrash, Map<String, Object> customSegmentation, int changedBits, Map<String, Object> customMetrics, List<String> baseMetricsExclude) throws JSONException {
        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);

        TestUtils.validateRequiredParams(RQ[0]);

        JSONObject crash = new JSONObject(RQ[0].get("crash"));
        int paramCount = validateCrashMetrics(deviceInfo, crash, nativeCrash, customMetrics, baseMetricsExclude);

        paramCount += 2;
        if (!Utils.isNullOrEmpty(error)) {
            paramCount++;
            Assert.assertEquals(error, crash.getString("_error"));
        }
        Assert.assertEquals(!fatal, crash.getBoolean("_nonfatal"));
        Assert.assertEquals(changedBits, crash.getInt("_bits"));
        if (customSegmentation != null && !customSegmentation.isEmpty()) {
            paramCount++;
            JSONObject custom = crash.getJSONObject("_custom");
            for (Map.Entry<String, Object> entry : customSegmentation.entrySet()) {
                Assert.assertEquals(entry.getValue(), custom.get(entry.getKey()));
            }
            Assert.assertEquals(custom.length(), customSegmentation.size());
        }
        if (!nativeCrash) {
            if (breadcrumbs != null) {
                paramCount++;
                Assert.assertEquals(breadcrumbs, crash.getString("_logs"));
            }
        }
        Assert.assertEquals(paramCount, crash.length());
    }

    private int validateCrashMetrics(DeviceInfo di, JSONObject crash, boolean nativeCrash, Map<String, Object> customMetrics, List<String> metricsToExclude) throws JSONException {
        int metricCount = 0;
        if (metricsToExclude == null) {
            metricsToExclude = Collections.emptyList();
        }
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_device", di.mp.getDevice(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_os", di.mp.getOS(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_os_version", di.mp.getOSVersion(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_resolution", di.mp.getResolution(TestUtils.getContext()), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_app_version", di.mp.getAppVersion(TestUtils.getContext()), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_manufacturer", di.mp.getManufacturer(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_cpu", di.mp.getCpu(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_opengl", di.mp.getOpenGL(TestUtils.getContext()), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_root", di.mp.isRooted(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_has_hinge", di.mp.isRooted(), crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_ram_total", null, crash);
        metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_disk_total", null, crash);

        if (!nativeCrash) {
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_ram_current", null, crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_disk_current", null, crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_run", null, crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_background", di.isInBackground(), crash);
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_muted", di.mp.isMuted(TestUtils.getContext()), crash);
            if (di.mp.getOrientation(TestUtils.getContext()) != null) {
                metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_orientation", di.mp.getOrientation(TestUtils.getContext()), crash);
            }
            if (di.mp.isOnline(TestUtils.getContext()) != null) {
                metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_online", di.mp.isOnline(TestUtils.getContext()), crash);
            }
            if (di.mp.getBatteryLevel(TestUtils.getContext()) != null) {
                metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_bat", null, crash);
            }
        } else {
            metricCount += assertEqualsMetricIfNotExcluded(metricsToExclude, "_native_cpp", "true", crash);
        }
        if (customMetrics != null) {
            for (Map.Entry<String, Object> entry : customMetrics.entrySet()) {
                Assert.assertEquals(entry.getValue(), crash.get(entry.getKey()));
            }
            metricCount += customMetrics.size();
        }
        return metricCount;
    }

    private int assertEqualsMetricIfNotExcluded(List<String> metricsToExclude, String metric, Object value, JSONObject crash) throws JSONException {
        if (!metricsToExclude.contains(metric)) {
            String message = "assertEqualsMetricIfNotExcluded,  " + metric + " metric assertion failed in crashes expected:[" + value + "]" + "was:[" + crash.get(metric) + "]";
            if (value == null) {
                Assert.assertTrue(message, crash.getDouble(metric) >= 0);
            } else {
                Assert.assertEquals(message, value, crash.get(metric));
            }
            return 1;
        }
        return 0;
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
