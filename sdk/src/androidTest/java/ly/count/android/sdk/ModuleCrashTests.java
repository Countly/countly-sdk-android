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
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleCrashTests {
    Countly mCountly;
    CountlyConfig config;
    ConnectionQueue connectionQueue;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        mCountly = new Countly();
        config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        connectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(connectionQueue);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void setCrashFilters() {

    }

    @Test
    public void crashFilterTest() {

    }

    @Test
    public void setCustomCrashSegment() {
        Countly countly = new Countly();
        CountlyConfig cConfig = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

        Assert.fail();
        //Assert.assertEquals(CrashDetails.setCustomSegments(););

        Map<String, Object> segm = new HashMap<>();
        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);
        segm.put("4", 45.4f);
        segm.put("41", new Object());
        segm.put("42", new int[]{1, 2});

        cConfig.setCustomCrashSegment(segm);

        countly.init(cConfig);


    }

    @Test
    public void addCrashBreadcrumb() {
        mCountly.crashes().addCrashBreadcrumb("Breadcrumb_1");
        mCountly.crashes().addCrashBreadcrumb("Breadcrumb_2");
        mCountly.crashes().addCrashBreadcrumb("Breadcrumb_3");

        String logs = CrashDetails.getLogs();

        Assert.assertEquals("Breadcrumb_1\nBreadcrumb_2\nBreadcrumb_3\n", logs);
    }

    @Test
    public void recordHandledExceptionException() {
        Exception exception = new Exception("Some message");

        mCountly.crashes().recordHandledException(exception);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(connectionQueue).sendCrashReport(arg.capture(), eq(true), eq(false));

        String crash = arg.getValue();

        Assert.assertTrue(crash.startsWith("java.lang.Exception: Some message\n" +
                "\tat ly.count.android.sdk.ModuleCrashTests.recordHandledExceptionException(ModuleCrashTests.java:"));
    }

    @Test
    public void recordHandledExceptionThrowable() {
        Throwable throwable = new Throwable("Some message");

        mCountly.crashes().recordHandledException(throwable);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(connectionQueue).sendCrashReport(arg.capture(), eq(true), eq(false));

        String crash = arg.getValue();

        Assert.assertTrue(crash.startsWith("java.lang.Throwable: Some message\n" +
                "\tat ly.count.android.sdk.ModuleCrashTests.recordHandledExceptionThrowable(ModuleCrashTests.java:"));
    }

    @Test
    public void recordUnhandledExceptionException() {
        Exception exception = new Exception("Some message");

        mCountly.crashes().recordUnhandledException(exception);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(connectionQueue).sendCrashReport(arg.capture(), eq(false), eq(false));

        String crash = arg.getValue();

        Assert.assertTrue(crash.startsWith("java.lang.Exception: Some message\n" +
                "\tat ly.count.android.sdk.ModuleCrashTests.recordUnhandledExceptionException(ModuleCrashTests.java:"));
    }

    @Test
    public void recordUnhandledExceptionThrowable() {
        Throwable throwable = new Throwable("Some message");

        mCountly.crashes().recordUnhandledException(throwable);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(connectionQueue).sendCrashReport(arg.capture(), eq(false), eq(false));

        String crash = arg.getValue();

        Assert.assertTrue(crash.startsWith("java.lang.Throwable: Some message\n" +
                "\tat ly.count.android.sdk.ModuleCrashTests.recordUnhandledExceptionThrowable(ModuleCrashTests.java:"));
    }

    //setCrashFiltersInternal

    //creash test 1-5
}