package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import ly.count.sdk.Config;
import ly.count.sdk.internal.InternalConfig;
//import ly.count.sdk.internal.SessionImpl;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleAutoSessionsTests extends BaseTests {

    @Test
    public void filler(){

    }

    /*
    ModuleAutoSessions moduleAutoSessions;

    @Test (expected = NullPointerException.class)
    public void init_null(){
        ModuleAutoSessions moduleAutoSessions = new ModuleAutoSessions();
        moduleAutoSessions.init(null);
    }

    @Test (expected = IllegalStateException.class)
    public void init_withSessionControl() throws Exception {
        ModuleAutoSessions moduleAutoSessions = new ModuleAutoSessions();
        config = new InternalConfig(defaultConfig());
        config.disableFeatures(Config.Feature.AutoSessionTracking);

        Assert.assertEquals(false, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
        moduleAutoSessions.init(config);
    }

    @Test
    public void init_withoutSessionControl() throws Exception {
        ModuleAutoSessions moduleAutoSessions = new ModuleAutoSessions();
        config = new InternalConfig(defaultConfig());
        Assert.assertEquals(true, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
        moduleAutoSessions.init(config);
    }

    @Test
    public void single_start() throws Exception {
        setUpApplication(defaultConfig());
        moduleAutoSessions = module(ModuleAutoSessions.class, false);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        moduleAutoSessions.onActivityStarted(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
    }

    @Test
    public void single_stop() throws Exception {
        config = new InternalConfig(defaultConfig());
        ModuleAutoSessions moduleAutoSessions = new ModuleAutoSessions();
        moduleAutoSessions.init(config);
        Core.instance = mock(Core.class);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        moduleAutoSessions.onActivityStopped(ctx);
        Assert.assertEquals(-1, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        Core.deinit();
    }

    @Test
    public void multiple_startStop() throws Exception  {
        setUpApplication(defaultConfig());
        moduleAutoSessions = module(ModuleAutoSessions.class, false);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        moduleAutoSessions.onActivityStarted(ctx);
        moduleAutoSessions.onActivityStarted(ctx);
        moduleAutoSessions.onActivityStopped(ctx);
        moduleAutoSessions.onActivityStarted(ctx);
        moduleAutoSessions.onActivityStopped(ctx);
        moduleAutoSessions.onActivityStarted(ctx);
        moduleAutoSessions.onActivityStopped(ctx);
        moduleAutoSessions.onActivityStarted(ctx);
        moduleAutoSessions.onActivityStopped(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
    }

    @Test
    public void activityStopped_withSessions() throws Exception {
        setUpApplication(defaultConfig());
        moduleAutoSessions = module(ModuleAutoSessions.class, false);

        SessionImpl sessionTarget = new SessionImpl(ctx, 123L);
        sessionTarget.begin().end();
        Whitebox.<SessionImpl>setInternalState(sdk, "session", sessionTarget);

        moduleAutoSessions.onActivityStarted(ctx);
        moduleAutoSessions.onActivityStarted(ctx);
        Assert.assertEquals(sessionTarget, Whitebox.getInternalState(sdk, "session"));
        Assert.assertEquals(2, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));

        moduleAutoSessions.onActivityStopped(ctx);
        Assert.assertEquals(sessionTarget, Whitebox.getInternalState(sdk, "session"));
    }

    @Test
    public void activityStopped_removeSession() throws Exception {
        setUpApplication(defaultConfig());
        moduleAutoSessions = module(ModuleAutoSessions.class, false);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        Assert.assertNull(Whitebox.getInternalState(sdk, "session"));

        moduleAutoSessions.onActivityStarted(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        Assert.assertNotNull(Whitebox.getInternalState(sdk, "session"));

        SessionImpl session = Whitebox.<SessionImpl>getInternalState(sdk, "session");
        Assert.assertTrue(session.isActive());

        moduleAutoSessions.onActivityStopped(ctx);
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        Assert.assertNull(Whitebox.getInternalState(sdk, "session"));
        Assert.assertFalse(session.isActive());
        Assert.assertNotNull(session.ended);
    }
    */
}