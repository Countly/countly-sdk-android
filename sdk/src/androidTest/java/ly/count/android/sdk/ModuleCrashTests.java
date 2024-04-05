package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
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
        validateCrash(extractStackTrace(throwable), "Breadcrumb_1\nBreadcrumb_2\nBreadcrumb_3\n", true, false, new HashMap<>(), 0, new HashMap<>(), new ArrayList<>());
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
        validateCrash(extractStackTrace(throwable), "Breadcrumb_4\nBreadcrumb_5\nBreadcrumb_6\n", true, false, new HashMap<>(), 0, new HashMap<>(), new ArrayList<>());
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
        validateCrash(extractStackTrace(exception), "", false, false, TestUtils.map("e", "5", "f", "6"), 0, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    /**
     * "recordHandledException" with crash filter
     * Validate that first call to the "recordHandledException" is filtered out by the crash filter
     * Validate that second call to the "recordHandledException" is not filtered out by the crash filter
     * Validate second call creates a request in the queue and validate all crash data
     */
    @Test
    public void recordHandledException_crashFilter() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.setCrashFilterCallback(crash -> crash.contains("Secret"));
        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Secret message");

        countly.crashes().recordHandledException(exception);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception);
        validateCrash(extractStackTrace(exception), "", false, false, new ConcurrentHashMap<>(), 0, new ConcurrentHashMap<>(), new ArrayList<>());
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
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setCustomCrashSegmentation(TestUtils.map("secret", "Minato", "int", Integer.MAX_VALUE, "double", Double.MAX_VALUE, "bool", true, "long", Long.MAX_VALUE, "float", 1.1, "object", new Object(), "array", new int[] { 1, 2 }));
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            if (crash.getStackTrace().contains("Secret")) {
                return true;
            }
            crash.getCrashSegmentation().remove("secret");
            crash.setFatal(true);
            crash.getCrashMetrics().put("secret", "Minato");
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

        validateCrash(extractStackTrace(exception), "Breadcrumb_1\nBreadcrumb_2\n", true, false,
            TestUtils.map("int", Integer.MAX_VALUE,
                "double", Double.MAX_VALUE,
                "bool", true,
                "float", 1.1,
                "sphinx_no", 324), 11, TestUtils.map("secret", "Minato"), Collections.singletonList("_ram_total"));
    }

    /**
     * Validate that after clipping segmentation values, it will be same as the original segmentation values
     * but the flag is set to true because the developer added segmentation values to the crash data
     */
    @Test
    public void internalLimits_recordException_globalCrashFilter_maxSegmentationValues() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.sdkInternalLimits.setMaxSegmentationValues(2);
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setCustomCrashSegmentation(TestUtils.map("secret", "Minato", "int", Integer.MAX_VALUE, "double", Double.MAX_VALUE, "bool", true, "long", Long.MAX_VALUE, "float", 1.1, "object", new Object(), "array", new int[] { 1, 2 }));
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            Assert.assertEquals(TestUtils.map("int", Integer.MAX_VALUE, "sphinx_no", 324), crash.getCrashSegmentation());
            crash.getCrashSegmentation().put("secret", "Minato");
            return false;
        });
        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, TestUtils.map("sphinx_no", 324));

        validateCrash(extractStackTrace(exception), "", false, false,
            TestUtils.map("int", Integer.MAX_VALUE, "secret", "Minato"), 8, new HashMap<>(), new ArrayList<>());
    }

    /**
     * Validate that after clipping unsupported values from crash metrics, it will be same as the original segmentation values
     * but the flag is set to true because the developer added values to the crash metrics
     * Before filtering validate that crash metrics are same as the original crash metrics
     */
    @Test
    public void recordException_globalCrashFilter_unsupportedDataType_crashMetrics() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.sdkInternalLimits.setMaxSegmentationValues(2);
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            try {
                validateCrashMetrics(crash.getCrashMetricsJSON(), false, new HashMap<>(), new ArrayList<>());
            } catch (JSONException e) {
                Assert.fail(e.getMessage());
            }

            Assert.assertEquals(20, crash.getCrashMetrics().size());

            crash.getCrashMetrics().put("5", new Object());
            crash.getCrashMetrics().put("6", new int[] { 1, 2 });
            return false;
        });
        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception);

        validateCrash(extractStackTrace(exception), "", false, false, new HashMap<>(), 2, new HashMap<>(), new ArrayList<>());
    }

    /**
     * "recordHandledException" with global crash filter
     * Global crash filter is set to filter out crashes that contain "Secret" in the stack trace
     * and to set "fatal" to true for all crashes
     * and to add "secret" key to the crash metrics
     * and to remove "_ram_total" key from the crash metrics
     * and to remove "secret" key from the crash segmentation
     * and to remove crashes that contain "sphinx_no_1" in the crash segmentation
     * Validate that call to the "recordHandledException" is not filtered out by the global crash filter
     * Validate call creates a request in the queue and validate all crash data, fatal is set to true
     * Validate that crash segmentation contains all custom segmentation
     * Validate that crash logs contains all breadcrumbs
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordHandledException_basic() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setCustomCrashSegmentation(TestUtils.map("secret", "Minato", "int", Integer.MAX_VALUE, "double", Double.MAX_VALUE, "bool", true, "long", Long.MAX_VALUE, "float", 1.1, "object", new Object(), "array", new int[] { 1, 2 }));
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> false);
        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");

        countly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        countly.crashes().recordHandledException(exception, TestUtils.map("sphinx_no", 324));

        validateCrash(extractStackTrace(exception), "Breadcrumb_1\nBreadcrumb_2\n", false, false,
            TestUtils.map("int", Integer.MAX_VALUE,
                "secret", "Minato",
                "double", Double.MAX_VALUE,
                "bool", true,
                "float", 1.1,
                "sphinx_no", 324), 0, new HashMap<>(), new ArrayList<>());
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
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            crash.setStackTrace("");
            crash.setCrashSegmentation(new HashMap<>());
            crash.setCrashMetrics(new HashMap<>());
            crash.setBreadcrumbs(new ArrayList<>());
            crash.setFatal(!crash.getFatal());

            return false;
        });

        Countly countly = new Countly().init(cConfig);

        countly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        countly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, TestUtils.map("sphinx_no", 324));

        validateCrash("", "", true, false, TestUtils.map(), 31, new ConcurrentHashMap<>(),
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
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(CrashData::getFatal);

        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordUnhandledException(exception);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        exception = new Exception("Some message 2");
        countly.crashes().recordHandledException(exception);
        validateCrash(extractStackTrace(exception), "", false, false, new ConcurrentHashMap<>(), 0, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    /**
     * Global crash filter adds unsupported data types to the crash metrics
     * Unsupported data types are eliminated from the crash metrics while filtering out the crash
     * Validate that unsupported data types are eliminated from the crash metrics
     * And because one of the base metrics is overridden with unsupported data type, it is eliminated from the crash metrics
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordException_globalCrashFilter_eliminateUnsupportedTypesFromCrashMetrics() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            crash.getCrashMetrics().put("5", new Object());
            crash.getCrashMetrics().put("6", new int[] { 1, 2 });
            crash.getCrashMetrics().put("7", "7");
            crash.getCrashMetrics().put("8", 8);
            crash.getCrashMetrics().put("9", 9.9d);
            crash.getCrashMetrics().put("10", true);
            crash.getCrashMetrics().put("11", 11.1f);
            crash.getCrashMetrics().put("_device", new Object());

            return false;
        });

        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordUnhandledException(exception);
        validateCrash(extractStackTrace(exception), "", true, false, new ConcurrentHashMap<>(), 2, TestUtils.map(
            "7", "7",
            "8", 8,
            "9", 9.9,
            "10", true,
            "11", 11.1), Collections.singletonList("_device"));
    }

    /**
     * Global crash filter adds unsupported data types to the crash metrics
     * Unsupported data types are eliminated from the crash metrics while filtering out the crash
     * Validate that unsupported data types are eliminated from the crash metrics
     * And adding unsupported data types to the crash segmentation affects changed bits flag
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordException_globalCrashFilter_unsupportedTypesMetrics_noChange() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            crash.getCrashMetrics().put("5", new Object());
            crash.getCrashMetrics().put("6", new int[] { 1, 2 });

            return false;
        });

        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordUnhandledException(exception);
        validateCrash(extractStackTrace(exception), "", true, false, new ConcurrentHashMap<>(), 2, new ConcurrentHashMap<>(), new ArrayList<>());
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
        validateCrash(extractStackTrace(exception), "", false, false, TestUtils.map("b", "2", "c", "3"), 0, new ConcurrentHashMap<>(), new ArrayList<>());
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
        cConfig.metricProviderOverride = mmp;
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

        validateCrash(extractStackTrace(exception), "Breadcrumb_1\nBreadcrumb_2\n", true, false, TestUtils.map("sphinx_no", 324), 1, new ConcurrentHashMap<>(), new ArrayList<>());
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
        cConfig.metricProviderOverride = mmp;
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
        validateCrash(extractStackTrace(exception), "", false, false, TestUtils.map("secret", "secret"), 0, new ConcurrentHashMap<>(), new ArrayList<>());

        TestUtils.getCountyStore().clear();
        countly.crashes().recordUnhandledException(exception, TestUtils.map("secret", "secret"));
        validateCrash(extractStackTrace(exception), "", true, false, TestUtils.map("secret", "secret"), 0, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    /**
     * validate that adding breadcrumbs that exceeds max breadcrumb count greater than one should clip,
     * oldest breadcrumbs and keep the latest ones that is limited by the max breadcrumb count
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordException_globalCrashFilter_exceedMaxBreadcrumb() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.sdkInternalLimits.setMaxBreadcrumbCount(2);
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            crash.getBreadcrumbs().add("5");
            crash.getBreadcrumbs().add("6");
            crash.getBreadcrumbs().add("7");
            return false;
        });

        Countly countly = new Countly().init(cConfig);

        countly.crashes().addCrashBreadcrumb("1");
        countly.crashes().addCrashBreadcrumb("2");
        countly.crashes().addCrashBreadcrumb("3");

        Exception exception = new Exception("secret");
        countly.crashes().recordUnhandledException(exception);
        validateCrash(extractStackTrace(exception), "6\n7\n", true, false, new ConcurrentHashMap<>(), 4, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    /**
     * validate that adding invalid custom segmentation data while filtering out the crash
     * must be eliminated from the crash data
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void recordException_globalCrashFilter_invalidCustomSegmentations() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            crash.getCrashSegmentation().put("5", new Object());
            crash.getCrashSegmentation().put("6", new int[] { 1, 2 });
            crash.getCrashSegmentation().put("7", "7");
            return false;
        });

        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("secret");
        countly.crashes().recordUnhandledException(exception);
        validateCrash(extractStackTrace(exception), "", true, false, TestUtils.map("7", "7"), 8, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    /**
     * validate that native crash dumps are not sent when global crash filter is set to filter out all crashes
     * Validate RQ is empty after initialization of the SDK
     */
    @Test
    public void recordException_globalCrashFilter_nativeCrash_filterAll() {
        createNativeDumFiles();
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> true);

        new Countly().init(cConfig);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
    }

    /**
     * Validate that 2 native crash dumps are sent when the global crash filter set to be eliminated
     * only the crash that contains "secret" in the stack trace
     */
    @Test
    public void recordException_globalCrashFilter_nativeCrash() throws JSONException {
        createNativeDumFiles();
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.crashes.setGlobalCrashFilterCallback(crash -> crash.getStackTrace().contains(extractNativeCrash("secret")));

        new Countly().init(cConfig);
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        validateCrash(extractNativeCrash("dump1"), "", true, true, 2, 0, new ConcurrentHashMap<>(), 0, new ConcurrentHashMap<>(), new ArrayList<>());
        validateCrash(extractNativeCrash("dump2"), "", true, true, 2, 1, new ConcurrentHashMap<>(), 0, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    /**
     * Validate that deprecated crash filter, filters out all native crash dumps
     * Validate RQ is empty after initialization of the SDK
     */
    @Test
    public void recordException_crashFilter_nativeCrash_eliminateAll() {
        createNativeDumFiles();
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.setCrashFilterCallback(crash -> true);

        new Countly().init(cConfig);
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
    }

    /**
     * Validate that deprecated crash filter, filters out only the crash that contains "secret" in the stack trace
     * Validate that 2 native crash dumps are sent when the crash filter set to be eliminated
     */
    @Test
    public void recordException_crashFilter_nativeCrash() throws JSONException {
        createNativeDumFiles();
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.setCrashFilterCallback(crash -> crash.contains(extractNativeCrash("secret")));

        new Countly().init(cConfig);
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        validateCrash(extractNativeCrash("dump1"), "", true, true, 2, 0, new ConcurrentHashMap<>(), 0, new ConcurrentHashMap<>(), new ArrayList<>());
        validateCrash(extractNativeCrash("dump2"), "", true, true, 2, 1, new ConcurrentHashMap<>(), 0, new ConcurrentHashMap<>(), new ArrayList<>());
    }

    private void createNativeDumFiles() {
        TestUtils.getCountyStore().clear();

        String finalPath = TestUtils.getContext().getCacheDir().getAbsolutePath() + File.separator + "Countly" + File.separator + "CrashDumps";

        createFile(finalPath, File.separator + "dump1.dmp", "dump1");
        createFile(finalPath, File.separator + "dump2.dmp", "dump2");
        createFile(finalPath, File.separator + "dump3.dmp", "secret");
    }

    private void createFile(String filePath, String fileName, String data) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.mkdirs();
            }
            file = new File(filePath + fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data.getBytes());
            fos.close();
        } catch (IOException ignored) {
        }
    }

    private void validateCrash(@NonNull String error, @NonNull String breadcrumbs, boolean fatal, boolean nativeCrash,
        @NonNull Map<String, Object> customSegmentation, int changedBits, @NonNull Map<String, Object> customMetrics, @NonNull List<String> baseMetricsExclude) throws JSONException {
        validateCrash(error, breadcrumbs, fatal, nativeCrash, 1, 0, customSegmentation, changedBits, customMetrics, baseMetricsExclude);
    }

    private void validateCrash(@NonNull String error, @NonNull String breadcrumbs, boolean fatal, boolean nativeCrash, final int rqSize, final int idx,
        @NonNull Map<String, Object> customSegmentation, int changedBits, @NonNull Map<String, Object> customMetrics, @NonNull List<String> baseMetricsExclude) throws JSONException {
        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(rqSize, RQ.length);

        TestUtils.validateRequiredParams(RQ[idx]);

        JSONObject crash = new JSONObject(RQ[idx].get("crash"));

        int paramCount = validateCrashMetrics(crash, nativeCrash, customMetrics, baseMetricsExclude);

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

    private int validateCrashMetrics(@NonNull JSONObject crash, boolean nativeCrash, @NonNull Map<String, Object> customMetrics, @NonNull List<String> metricsToExclude) throws JSONException {
        int metricCount = 12 - metricsToExclude.size();

        assertMetricIfNotExcluded(metricsToExclude, "_device", "C", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_os", "A", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_os_version", "B", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_resolution", "E", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_app_version", Countly.DEFAULT_APP_VERSION, crash);
        assertMetricIfNotExcluded(metricsToExclude, "_manufacturer", "D", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_cpu", "N", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_opengl", "O", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_root", "T", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_has_hinge", "Z", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_ram_total", "48", crash);
        assertMetricIfNotExcluded(metricsToExclude, "_disk_total", "45", crash);

        if (!nativeCrash) {
            metricCount += 8;
            assertMetricIfNotExcluded(metricsToExclude, "_ram_current", "12", crash);
            assertMetricIfNotExcluded(metricsToExclude, "_disk_current", "23", crash);
            assertMetricIfNotExcluded(metricsToExclude, "_run", "88", crash);
            assertMetricIfNotExcluded(metricsToExclude, "_background", "true", crash);
            assertMetricIfNotExcluded(metricsToExclude, "_muted", "V", crash);
            assertMetricIfNotExcluded(metricsToExclude, "_orientation", "S", crash);
            assertMetricIfNotExcluded(metricsToExclude, "_online", "U", crash);
            assertMetricIfNotExcluded(metricsToExclude, "_bat", "6", crash);
        } else {
            metricCount++;
            assertMetricIfNotExcluded(metricsToExclude, "_native_cpp", true, crash);
        }

        for (Map.Entry<String, Object> entry : customMetrics.entrySet()) {
            Assert.assertEquals(entry.getValue(), crash.get(entry.getKey()));
        }
        metricCount += customMetrics.size();

        return metricCount;
    }

    private void assertMetricIfNotExcluded(List<String> metricsToExclude, String metric, Object value, JSONObject crash) throws JSONException {
        if (metricsToExclude.contains(metric)) {
            Assert.assertFalse(crash.has(metric));
        } else {
            Assert.assertEquals("assertEqualsMetricIfNotExcluded,  " + metric + " metric assertion failed in crashes expected:[" + value + "]" + "was:[" + crash.get(metric) + "]", value, crash.get(metric));
        }
    }

    private String extractStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private String extractNativeCrash(String crash) {
        return Base64.getEncoder().encodeToString(crash.getBytes());
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

    /**
     * Test that the segmentation given while initializing the SDK is truncated to the limit
     * And that the segmentation given during the crash recording is also truncated to the limit
     * One of the parameters are lost due to truncation because it has same key beginning as another parameter
     */
    @Test
    public void internalLimits_provideCustomCrashSegment_DuringInitAndCall() throws JSONException {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        cConfig.sdkInternalLimits.setMaxKeyLength(10);

        Map<String, Object> segm = new HashMap<>();
        segm.put("anr_log_id_key", "76atda76bsdtahs78dasyd8");
        segm.put("abr_log_id", "87abdb687astdna8s7dynas897ndaysnd");
        segm.put("arf_log_ver", 1_675_987);

        cConfig.setCustomCrashSegment(segm);

        countly.init(cConfig);
        requestQueueProvider = TestUtils.setRequestQueueProviderToMock(countly, mock(RequestQueueProvider.class));

        //validating values set by init
        Map<String, Object> segm2 = new HashMap<>();
        segm2.put("anr_log_id", "76atda76bsdtahs78dasyd8");
        segm2.put("abr_log_id", "87abdb687astdna8s7dynas897ndaysnd");
        segm2.put("arf_log_ve", 1_675_987);
        Assert.assertEquals(segm2, countly.moduleCrash.customCrashSegments);

        //prepare new segm to be provided during recording
        Map<String, Object> segm3 = new HashMap<>();
        segm3.put("anr_log_id_secret", "SECRET");
        segm3.put("battery_percentage", 1234.55d);
        segm3.put("ftl", true);

        Exception exception = new Exception("Some message");
        countly.crashes().recordHandledException(exception, segm3);
        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(requestQueueProvider).sendCrashReport(arg.capture(), any(Boolean.class));

        String argVal = arg.getValue();

        JSONObject jobj = new JSONObject(argVal);
        Assert.assertTrue(jobj.getString("_error").startsWith("java.lang.Exception: Some message"));
        JSONObject jCus = jobj.getJSONObject("_custom");
        Assert.assertEquals(5, jCus.length());
        Assert.assertEquals("SECRET", jCus.get("anr_log_id"));
        Assert.assertEquals("87abdb687astdna8s7dynas897ndaysnd", jCus.get("abr_log_id"));
        Assert.assertEquals(1_675_987, jCus.get("arf_log_ve"));
        Assert.assertEquals(1234.55d, jCus.get("battery_pe"));
        Assert.assertEquals(true, jCus.get("ftl"));
    }

    /**
     * Test that the segmentation given while initializing the SDK is truncated to the limit
     * And that the segmentation given during the crash recording is also truncated to the limit
     * Two of the parameters are lost due to truncation because it has same key beginning as another parameter
     */
    @Test
    public void internalLimits_provideCustomCrashSegment_recordUnhandledException() throws JSONException {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        cConfig.metricProviderOverride = mmp;
        cConfig.sdkInternalLimits.setMaxKeyLength(5);
        cConfig.setCustomCrashSegment(TestUtils.map("test_out_truncation", "1234", "test_mine", 1234, "below_zero", true));

        countly.init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordUnhandledException(exception, TestUtils.map("below_one", false, "go_for_it", "go"));

        validateCrash(extractStackTrace(exception), "", true, false, TestUtils.map("test_", 1234, "below", false, "go_fo", "go"), 0, new HashMap<>(), new ArrayList<>());
    }

    /**
     * Given max value size truncates the values of the:
     * - Crash segmentation
     * - Custom crash segmentation
     * - Breadcrumbs
     * Validate all values are truncated to the max value size that is 5
     * And validate non-String values are not clipped
     * This also includes clipping unsupported data types
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimits_recordException_maxValueSize() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.sdkInternalLimits.setMaxValueSize(5);
        cConfig.crashes.setCustomCrashSegmentation(TestUtils.map("test", "123456", "integer", Integer.MAX_VALUE, "arr", new int[] { 1, 2, 3, 4, 5 }, "double", Double.MAX_VALUE, "bool", true, "float", 1.1, "object", new Object()));
        Countly countly = new Countly().init(cConfig);

        countly.crashes().addCrashBreadcrumb("Surpass");
        countly.crashes().addCrashBreadcrumb("YourLimits");
        countly.crashes().addCrashBreadcrumb("RightNow");
        Exception exception = new Exception("Some message");
        countly.crashes().recordUnhandledException(exception, TestUtils.map("case", "o the great one", "have", "dinosaur", "int1", Integer.MIN_VALUE, "double1", Double.MIN_VALUE, "bool", false));

        validateCrash(extractStackTrace(exception), "Surpa\nYourL\nRight\n", true, false, TestUtils.map("test", "12345", "integer", Integer.MAX_VALUE, "int1", Integer.MIN_VALUE, "double", Double.MAX_VALUE, "double1", Double.MIN_VALUE, "bool", false, "float", 1.1, "have", "dinos", "case", "o the"),
            0, new HashMap<>(),
            new ArrayList<>());
    }

    /**
     * Validate all 4 limits are applied after crash filtering:
     * - Max value size 5
     * - Max key length 2
     * - Max segmentation values 5
     * - Max breadcrumb count 2
     * Validate that all values are truncated to their limits
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimits_globalCrashFilter_maxValueSize_maxSegmentationValues_maxKeyLength_maxBreadcrumbCount() throws JSONException {
        CountlyConfig cConfig = TestUtils.createBaseConfig();
        cConfig.metricProviderOverride = mmp;
        cConfig.sdkInternalLimits.setMaxValueSize(5).setMaxKeyLength(2).setMaxSegmentationValues(5).setMaxBreadcrumbCount(2);

        cConfig.crashes.setGlobalCrashFilterCallback(crash -> {
            Assert.assertTrue(crash.getCrashSegmentation().isEmpty());
            Assert.assertTrue(crash.getBreadcrumbs().isEmpty());

            crash.getCrashSegmentation().put("aftermath", "Snowrunner");
            crash.getCrashSegmentation().put("beforemath", "Mudrunner");
            crash.getCrashSegmentation().put("premath", "Spintires");
            crash.getCrashSegmentation().put("postmath", "DirtRally");
            crash.getCrashSegmentation().put("midmath", "WRCRally");
            crash.getCrashSegmentation().put("oldmath", "AssettoCorsa");
            crash.getCrashSegmentation().put("ancientmath", "EuroTruckSimulator2");

            crash.getBreadcrumbs().add("VolvoFH750");
            crash.getBreadcrumbs().add("ScaniaR730");
            crash.getBreadcrumbs().add("MercedesActros");
            crash.getBreadcrumbs().add("VVolvoV90");

            return false;
        });

        Countly countly = new Countly().init(cConfig);

        Exception exception = new Exception("Some message");
        countly.crashes().recordUnhandledException(exception);
        validateCrash(extractStackTrace(exception), "Merce\nVVolv\n", true, false, TestUtils.map("an", "EuroT", "ol", "Asset", "po", "DirtR", "af", "Snowr", "mi", "WRCRa"),
            12, new HashMap<>(), new ArrayList<>());
    }
}
