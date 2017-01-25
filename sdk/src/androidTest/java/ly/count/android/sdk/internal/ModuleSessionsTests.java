package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.matchers.Any;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.validateMockitoUsage;

@RunWith(AndroidJUnit4.class)
public class ModuleSessionsTests {
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";

    Config config;
    InternalConfig internalConfig;

    android.content.Context context;
    ContextImpl contextImpl;

    ModuleSessions moduleSessions;
    Core core;

    @Before
    public void setupEveryTest() throws MalformedURLException{
        config = new Config(serverUrl, serverAppKey);
        internalConfig = new InternalConfig(config);
        context = getContext();
        contextImpl = new ContextImpl(context);
        moduleSessions = new ModuleSessions();

        core = new Core(config);
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }

    @Test (expected = NullPointerException.class)
    public void init_null(){

        moduleSessions.init(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void init_withSessionControl() {
        internalConfig.enableProgrammaticSessionsControl();

        Assert.assertEquals(true, internalConfig.isProgrammaticSessionsControl());
        moduleSessions.init(internalConfig);
    }

    @Test
    public void init_withoutSessionControl() {
        Assert.assertEquals(false, internalConfig.isProgrammaticSessionsControl());
        moduleSessions.init(internalConfig);
    }

    @Test
    public void single_start() {
        moduleSessions.init(internalConfig);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        moduleSessions.onActivityStarted(contextImpl);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
    }

    @Test
    public void single_stop() {
        moduleSessions.init(internalConfig);
        Core coreSpy = mock(Core.class);

        Core.instance = coreSpy;

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        moduleSessions.onActivityStopped(contextImpl);
        Assert.assertEquals(-1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
    }

    @Test
    public void multiple_startStop() {
        moduleSessions.init(internalConfig);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        moduleSessions.onActivityStarted(contextImpl);
        moduleSessions.onActivityStarted(contextImpl);
        moduleSessions.onActivityStopped(contextImpl);
        moduleSessions.onActivityStarted(contextImpl);
        moduleSessions.onActivityStopped(contextImpl);
        moduleSessions.onActivityStarted(contextImpl);
        moduleSessions.onActivityStopped(contextImpl);
        moduleSessions.onActivityStarted(contextImpl);
        moduleSessions.onActivityStopped(contextImpl);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
    }
}