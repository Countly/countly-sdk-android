package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;
import static ly.count.android.sdk.Config.LoggingLevel.*;
import static org.mockito.Mockito.*;

@RunWith(AndroidJUnit4.class)
public class LogTests {
    private static final String message = "message";
    private static final Throwable exception = new IllegalStateException("IAS");
    private InternalConfig config;

    @Before
    public void setupEveryTest() throws MalformedURLException{
        String serverUrl = "http://www.serverurl.com";
        String serverAppKey = "1234";
        config = new InternalConfig(new Config(serverUrl, serverAppKey));
    }

    @After
    public void cleanupEveryTests(){
        config = null;
    }

    @Test(expected = NullPointerException.class)
    public void logInit_null(){
        Log log = new Log();
        log.init(null);
    }

    @Test
    public void logInit_enableTestMode() throws MalformedURLException {
        config.enableTestMode();

        Log log = new Log();
        log.init(config);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(INFO, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message, exception);
        Log.i(message, exception);
        Log.w(message, exception);
        Log.e(message, exception);

        verify(logger, never()).d(message, exception);
        verify(logger, times(1)).i(message, exception);
        verify(logger, times(1)).w(message, exception);
        verify(logger, times(1)).e(message, exception);
    }

    @Test
    public void logInit_setLevelDebug() throws MalformedURLException {
        config.setLoggingLevel(DEBUG)
                .enableTestMode();

        Log log = new Log();
        log.init(config);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(DEBUG, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message);
        Log.i(message);
        Log.w(message);
        Log.e(message);

        verify(logger, times(1)).d(message);
        verify(logger, times(1)).i(message);
        verify(logger, times(1)).w(message);
        verify(logger, times(1)).e(message);
    }

    @Test
    public void logInit_setLevelInfo() throws MalformedURLException {
        config.setLoggingLevel(INFO)
                .enableTestMode();

        Log log = new Log();
        log.init(config);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(INFO, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message);
        Log.i(message);
        Log.w(message);
        Log.e(message);

        verify(logger, never()).d(message);
        verify(logger, times(1)).i(message);
        verify(logger, times(1)).w(message);
        verify(logger, times(1)).e(message);
    }

    @Test
    public void logInit_setLevelWarn() throws MalformedURLException {
        config.setLoggingLevel(WARN)
                .enableTestMode();

        Log log = new Log();
        log.init(config);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(WARN, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message, exception);
        Log.i(message, exception);
        Log.w(message, exception);
        Log.e(message, exception);

        verify(logger, never()).d(message, exception);
        verify(logger, never()).i(message, exception);
        verify(logger, times(1)).w(message, exception);
        verify(logger, times(1)).e(message, exception);
    }

    @Test
    public void logInit_setLevelError() throws MalformedURLException {
        config.setLoggingLevel(ERROR);

        Log log = new Log();
        log.init(config);

        Assert.assertEquals(false, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(ERROR, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message, exception);
        Log.i(message, exception);
        Log.w(message, exception);
        Log.e(message, exception);

        verify(logger, never()).d(message, exception);
        verify(logger, never()).i(message, exception);
        verify(logger, never()).w(message, exception);
        verify(logger, times(1)).e(message, exception);
    }

    @Test
    public void logInit_setLevelOff() throws MalformedURLException {
        config.setLoggingLevel(OFF);

        Log log = new Log();
        log.init(config);

        Assert.assertEquals(false, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(OFF, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message, exception);
        Log.i(message, exception);
        Log.w(message, exception);
        Log.e(message, exception);

        verify(logger, never()).d(message, exception);
        verify(logger, never()).i(message, exception);
        verify(logger, never()).w(message, exception);
        verify(logger, never()).e(message, exception);
    }

    @Test
    public void logInit_noLevel() throws MalformedURLException {
        Log log = new Log();
        log.init(config);

        Assert.assertEquals(false, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(OFF, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message, exception);
        Log.i(message, exception);
        Log.w(message, exception);
        Log.e(message, exception);

        verify(logger, never()).d(message, exception);
        verify(logger, never()).i(message, exception);
        verify(logger, never()).w(message, exception);
        verify(logger, never()).e(message, exception);
    }

    @Test
    public void logInit_wtf_noLevel() throws MalformedURLException {
        Log log = new Log();
        log.init(config);

        Assert.assertEquals(false, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(OFF, Whitebox.getInternalState(log, "level"));

        Log.Logger logger = mock(Log.Logger.class);
        Whitebox.setInternalState(Log.class, "logger", logger);

        Log.d(message, exception);
        Log.i(message, exception);
        Log.w(message, exception);
        Log.e(message, exception);
        Log.wtf(message, exception);

        verify(logger, never()).d(message, exception);
        verify(logger, never()).i(message, exception);
        verify(logger, never()).w(message, exception);
        verify(logger, never()).e(message, exception);
        verify(logger, times(1)).wtf(message, exception);
    }

    @Test(expected = IllegalStateException.class)
    public void logInit_wtf_testMode() throws MalformedURLException {
        config.enableTestMode();

        Log log = new Log();
        log.init(config);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(INFO, Whitebox.getInternalState(log, "level"));

        Log.wtf(message, exception);
    }
}
