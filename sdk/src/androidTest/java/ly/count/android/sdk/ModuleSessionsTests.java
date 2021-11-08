package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleSessionsTests {
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void manualSessionBegin() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().enableManualSessionControl();

        mCountly.init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.sessions().beginSession();

        verify(requestQueueProvider, times(1)).beginSession(false, null, null, null, null);
    }

    @Test
    public void manualSessionBeginUpdateEnd() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().enableManualSessionControl();

        mCountly.init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.sessions().beginSession();
        verify(requestQueueProvider, times(1)).beginSession(false, null, null, null, null);

        Thread.sleep(1000);
        mCountly.sessions().updateSession();

        verify(requestQueueProvider, times(1)).updateSession(1);
        Thread.sleep(2000);
        mCountly.sessions().endSession();
        verify(requestQueueProvider, times(1)).endSession(2, null);
    }

    @Test
    public void manualSessionBeginUpdateEndManualDisabled() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

        mCountly.init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.sessions().beginSession();
        verify(requestQueueProvider, never()).beginSession(false, null, null, null, null);

        Thread.sleep(1000);
        mCountly.sessions().updateSession();

        verify(requestQueueProvider, never()).updateSession(1);
        Thread.sleep(2000);
        mCountly.sessions().endSession();
        verify(requestQueueProvider, never()).endSession(2, null);
    }

    @Test
    public void automaticSessionBeginEndWithManualEnabled() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().enableManualSessionControl();

        mCountly.init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStart(null);

        verify(requestQueueProvider, never()).beginSession(false, null, null, null, null);
        Thread.sleep(1000);

        mCountly.onStop();

        verify(requestQueueProvider, never()).endSession(1, null);
    }

    @Test
    public void automaticSessionBeginEndWithManualDisabled() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

        mCountly.init(config);
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStart(null);

        verify(requestQueueProvider, times(1)).beginSession(false, null, null, null, null);
        Thread.sleep(1000);

        mCountly.onStop();

        verify(requestQueueProvider, times(1)).endSession(1, null);
    }
}
