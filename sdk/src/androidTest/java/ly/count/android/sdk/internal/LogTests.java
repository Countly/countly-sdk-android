package ly.count.android.sdk.internal;

import android.content.Context;
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

@RunWith(AndroidJUnit4.class)
public class LogTests {
    private Config config;

    @Before
    public void setupEveryTest() throws MalformedURLException{
        String serverUrl = "http://www.serverurl.com";
        String serverAppKey = "1234";
        config = new Config(serverUrl, serverAppKey);
    }

    @After
    public void cleanupEveryTests(){
        config = null;
    }

    @Test (expected = NullPointerException.class)
    public void logInit_null(){
        Log log = new Log();
        log.init(null);
    }

    @Test
    public void logInit_setLevelDebug() throws MalformedURLException {
        config.setLoggingLevel(DEBUG)
                .enableTestMode();
        InternalConfig internalConfig = new InternalConfig(config);

        Log log = new Log();
        log.init(internalConfig);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(0, (int)Whitebox.<Integer>getInternalState(log, "level"));
    }

    @Test
    public void logInit_setLevelInfo() throws MalformedURLException {
        config.setLoggingLevel(INFO)
                .enableTestMode();
        InternalConfig internalConfig = new InternalConfig(config);

        Log log = new Log();
        log.init(internalConfig);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(1, (int)Whitebox.<Integer>getInternalState(log, "level"));
    }

    @Test
    public void logInit_setLevelWarn() throws MalformedURLException {
        config.setLoggingLevel(WARN)
                .enableTestMode();
        InternalConfig internalConfig = new InternalConfig(config);

        Log log = new Log();
        log.init(internalConfig);

        Assert.assertEquals(true, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(2, (int)Whitebox.<Integer>getInternalState(log, "level"));
    }

    @Test
    public void logInit_setLevelError() throws MalformedURLException {
        config.setLoggingLevel(ERROR);
        InternalConfig internalConfig = new InternalConfig(config);

        Log log = new Log();
        log.init(internalConfig);

        Assert.assertEquals(false, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(3, (int)Whitebox.<Integer>getInternalState(log, "level"));
    }

    @Test
    public void logInit_setLevelOff() throws MalformedURLException {
        config.setLoggingLevel(OFF);
        InternalConfig internalConfig = new InternalConfig(config);

        Log log = new Log();
        log.init(internalConfig);

        Assert.assertEquals(false, (boolean)Whitebox.<Boolean>getInternalState(log, "testMode"));
        Assert.assertEquals(-1, (int)Whitebox.<Integer>getInternalState(log, "level"));
    }
}
