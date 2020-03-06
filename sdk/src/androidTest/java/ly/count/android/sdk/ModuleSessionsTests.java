package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void manualSessionBegin(){
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().enableManualSessionControl();

        mCountly.init(config);
        ConnectionQueue connectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(connectionQueue);

        mCountly.sessions().beginSession();

        verify(connectionQueue, times(1)).beginSession();
    }

    @Test
    public void manualSessionBeginUpdateEnd() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().enableManualSessionControl();

        mCountly.init(config);
        ConnectionQueue connectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(connectionQueue);

        mCountly.sessions().beginSession();
        verify(connectionQueue, times(1)).beginSession();

        Thread.sleep(1000);
        mCountly.sessions().updateSession();

        verify(connectionQueue, times(1)).updateSession(1);
        Thread.sleep(2000);
        mCountly.sessions().endSession();
        verify(connectionQueue, times(1)).endSession(2, null);

    }

    @Test
    public void automaticSessionBeginEndWithManualEnabled() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().enableManualSessionControl();

        mCountly.init(config);
        ConnectionQueue connectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(connectionQueue);

        mCountly.onStart(null);

        verify(connectionQueue, never()).beginSession();
        Thread.sleep(1000);

        mCountly.onStop();

        verify(connectionQueue, never()).endSession(1, null);
    }

    @Test
    public void automaticSessionBeginEndWithManualDisabled() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();

        mCountly.init(config);
        ConnectionQueue connectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(connectionQueue);

        mCountly.onStart(null);

        verify(connectionQueue, times(1)).beginSession();
        Thread.sleep(1000);

        mCountly.onStop();

        verify(connectionQueue, times(1)).endSession(1, null);
    }
}
