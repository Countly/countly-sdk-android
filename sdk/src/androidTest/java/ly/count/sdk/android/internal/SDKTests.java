package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;


import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.util.List;

import ly.count.sdk.android.Config;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Module;

@RunWith(AndroidJUnit4.class)
public class SDKTests extends BaseTests {
    @Before
    public void setUp() throws Exception {
        super.setUp();
        config = new InternalConfig(defaultConfig());
    }

    @Test
    public void filler(){

    }
/*
    @Test
    public void constructor_basic(){
        Assert.assertEquals(true, config.isFeatureEnabled(Config.Feature.Sessions));
        Assert.assertEquals(Config.LoggingLevel.DEBUG, config.getLoggingLevel());

        Core core = Core.init(config, application());

        Assert.assertTrue(config == Whitebox.<InternalConfig>getInternalState(core, "config"));
        Assert.assertEquals(4, Whitebox.<List<Module>>getInternalState(core, "modules").size());
        Assert.assertNull(Whitebox.getInternalState(core, "session"));
    }

    @Test
    public void constructor_basicWithLogging(){
        config.setLoggingLevel(Config.LoggingLevel.WARN);
        Assert.assertEquals(Config.LoggingLevel.WARN, config.getLoggingLevel());

        Core core = Core.init(config, application());

        Assert.assertTrue(config == Whitebox.<InternalConfig>getInternalState(core, "config"));
        Assert.assertEquals(4, Whitebox.<List<Module>>getInternalState(core, "modules").size());
        Assert.assertNull(Whitebox.getInternalState(core, "session"));
    }
    */
}