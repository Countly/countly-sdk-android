package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.util.List;

import ly.count.android.sdk.Config;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleSessionsTests extends BaseTests {
    ModuleSessions moduleSessions;

    @Test (expected = NullPointerException.class)
    public void init_null(){
        ModuleSessions moduleSessions = new ModuleSessions();
        moduleSessions.init(null);
    }

    @Test (expected = IllegalStateException.class)
    public void init_withSessionControl() throws Exception {
        ModuleSessions moduleSessions = new ModuleSessions();
        config = new InternalConfig(defaultConfig());
        config.disableFeatures(Config.Feature.AutoSessionTracking);

        Assert.assertEquals(false, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
        moduleSessions.init(config);
    }

    @Test
    public void init_withoutSessionControl() throws Exception {
        ModuleSessions moduleSessions = new ModuleSessions();
        config = new InternalConfig(defaultConfig());
        Assert.assertEquals(true, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
        moduleSessions.init(config);
    }

    @Test
    public void single_start() throws Exception {
        setUpApplication(defaultConfig());
        moduleSessions = module(ModuleSessions.class, false);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        moduleSessions.onActivityStarted(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
    }

    @Test
    public void single_stop() throws Exception {
        config = new InternalConfig(defaultConfig());
        ModuleSessions moduleSessions = new ModuleSessions();
        moduleSessions.init(config);
        Core.instance = mock(Core.class);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(-1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Core.deinit();
    }

    @Test
    public void multiple_startStop() throws Exception  {
        setUpApplication(defaultConfig());
        moduleSessions = module(ModuleSessions.class, false);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStopped(ctx);
        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStopped(ctx);
        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStopped(ctx);
        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
    }

    @Test
    public void activityStopped_withSessions() throws Exception {
        setUpApplication(defaultConfig());
        moduleSessions = module(ModuleSessions.class, false);

        SessionImpl sessionTarget = new SessionImpl(ctx, 123L);
        sessionTarget.begin().end();
        Whitebox.<SessionImpl>setInternalState(core, "session", sessionTarget);

        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStarted(ctx);
        Assert.assertEquals(sessionTarget, Whitebox.getInternalState(core, "session"));
        Assert.assertEquals(2, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(sessionTarget, Whitebox.getInternalState(core, "session"));
    }

    @Test
    public void activityStopped_removeSession() throws Exception {
        setUpApplication(defaultConfig());
        moduleSessions = module(ModuleSessions.class, false);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Assert.assertNull(Whitebox.getInternalState(core, "session"));

        moduleSessions.onActivityStarted(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Assert.assertNotNull(Whitebox.getInternalState(core, "session"));

        SessionImpl session = Whitebox.<SessionImpl>getInternalState(core, "session");
        Assert.assertTrue(session.isActive());

        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Assert.assertNull(Whitebox.getInternalState(core, "session"));
        Assert.assertFalse(session.isActive());
        Assert.assertNotNull(session.ended);
    }
}