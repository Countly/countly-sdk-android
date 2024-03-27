package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.PrintWriter;
import java.io.StringWriter;
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
        validateCrash(countly.config_.deviceInfo, extractStackTrace(throwable), "Breadcrumb_1\nBreadcrumb_2\nBreadcrumb_3\n", false, false, null, 0);
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
        validateCrash(countly.config_.deviceInfo, extractStackTrace(throwable), "Breadcrumb_4\nBreadcrumb_5\nBreadcrumb_6\n", false, false, null, 0);
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
        validateCrash(countly.config_.deviceInfo, extractStackTrace(exception), "", true, false, null, 0);
    }

    private void validateCrash(DeviceInfo deviceInfo, String error, String breadcrumbs, boolean fatal, boolean nativeCrash, Map<String, Object> customSegmentation, int changedBits) throws JSONException {
        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, RQ.length);

        //TestUtils.validateRequiredParams(RQ[0]);

        JSONObject crash = new JSONObject(RQ[0].get("crash"));
        int paramCount = validateCrashMetrics(deviceInfo, crash, nativeCrash);

        paramCount += 3;
        Assert.assertEquals(error, crash.getString("_error"));
        Assert.assertEquals(!fatal, crash.getBoolean("_nonfatal"));
        Assert.assertEquals(changedBits, crash.getInt("_bits"));
        if (customSegmentation != null) {
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

    private int validateCrashMetrics(DeviceInfo di, JSONObject crash, boolean nativeCrash) throws JSONException {
        int metricCount = 12;
        Assert.assertEquals(di.mp.getDevice(), crash.get("_device"));
        Assert.assertEquals(di.mp.getOS(), crash.get("_os"));
        Assert.assertEquals(di.mp.getOSVersion(), crash.get("_os_version"));
        Assert.assertEquals(di.mp.getResolution(TestUtils.getContext()), crash.get("_resolution"));
        Assert.assertEquals(di.mp.getAppVersion(TestUtils.getContext()), crash.get("_app_version"));
        Assert.assertEquals(di.mp.getManufacturer(), crash.get("_manufacturer"));
        Assert.assertEquals(di.mp.hasHinge(TestUtils.getContext()), crash.get("_has_hinge"));
        Assert.assertEquals(di.mp.getCpu(), crash.get("_cpu"));
        Assert.assertEquals(di.mp.getOpenGL(TestUtils.getContext()), crash.get("_opengl"));
        Assert.assertEquals(di.mp.isRooted(), crash.get("_root"));
        Assert.assertTrue(crash.getInt("_ram_total") >= 0);
        Assert.assertTrue(crash.getInt("_disk_total") >= 0);
        if (!nativeCrash) {
            metricCount += 5;
            Assert.assertTrue(crash.getInt("_ram_current") >= 0);
            Assert.assertTrue(crash.getInt("_disk_current") >= 0);
            Assert.assertTrue(crash.getDouble("_run") >= 0);
            Assert.assertEquals(di.mp.isMuted(TestUtils.getContext()), crash.get("_muted"));
            Assert.assertEquals(di.isInBackground(), crash.get("_background"));
            if (di.mp.getOrientation(TestUtils.getContext()) != null) {
                Assert.assertEquals(di.mp.getOrientation(TestUtils.getContext()), crash.get("_orientation"));
                metricCount++;
            }
            if (di.mp.isOnline(TestUtils.getContext()) != null) {
                Assert.assertEquals(di.mp.isOnline(TestUtils.getContext()), crash.get("_online"));
                metricCount++;
            }
            if (di.mp.getBatteryLevel(TestUtils.getContext()) != null) {
                Assert.assertTrue(crash.getDouble("_bat") >= 0);
                metricCount++;
            }
        } else {
            Assert.assertEquals("true", crash.get("_native_cpp"));
            metricCount++;
        }
        return metricCount;
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