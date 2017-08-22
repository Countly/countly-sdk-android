package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;


import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.List;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.validateMockitoUsage;

@RunWith(AndroidJUnit4.class)
public class CoreTests {
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";
    Config config;

    @Before
    public void setupEveryTest()throws MalformedURLException{
        android.content.Context context = getContext();
        config = new Config(serverUrl, serverAppKey);
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }


    public static void assertConfirmConfig(Config config, Core core){
        TestingUtilityInternal.assertConfigsContainSameData(config, Whitebox.<InternalConfig>getInternalState(core, "config"));
    }

    @Test
    public void constructor_basic(){
        Assert.assertEquals(false, config.isProgrammaticSessionsControl());
        Assert.assertEquals(Config.LoggingLevel.OFF, config.getLoggingLevel());

        Core core = new Core();
        core.init(config, getContext());
        TestingUtilityInternal.assertConfigsContainSameData(config, Whitebox.<InternalConfig>getInternalState(core, "config"));

        Assert.assertEquals(2, Whitebox.<List<Module>>getInternalState(core, "modules").size());
        Assert.assertEquals(0, Whitebox.<List<SessionImpl>>getInternalState(core, "sessions").size());
    }

    @Test
    public void constructor_basicWithLogging(){
        config.setLoggingLevel(Config.LoggingLevel.WARN);
        Assert.assertEquals(false, config.isProgrammaticSessionsControl());
        Assert.assertEquals(Config.LoggingLevel.WARN, config.getLoggingLevel());

        Core core = new Core();
        core.init(config, getContext());
        TestingUtilityInternal.assertConfigsContainSameData(config, Whitebox.<InternalConfig>getInternalState(core, "config"));

        Assert.assertEquals(3, Whitebox.<List<Module>>getInternalState(core, "modules").size());
        Assert.assertEquals(0, Whitebox.<List<SessionImpl>>getInternalState(core, "sessions").size());
    }
}