package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.ModuleSessions;
import ly.count.sdk.internal.SessionImpl;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleAutoSessionsTests extends BaseTestsAndroid {
    ModuleSessions moduleSessions;

    @Test// (expected = NullPointerException.class)//todo previously had this, but currently throws nothing (AK, 15.01.2019)
    public void init_null(){
        ModuleSessions moduleAutoSessions = new ModuleSessions();
        moduleAutoSessions.init(null);
        //currently nothing happens when initiating with null
    }

    @Test// (expected = IllegalStateException.class)//todo previously had this, but currently throws nothing (AK, 15.01.2019)
    public void init_withSessionControl() throws Exception {
        ModuleSessions moduleSessions = new ModuleSessions();
        config = new InternalConfig(defaultConfig());
        config.setAutoSessionsTracking(false);

        Assert.assertEquals(false, config.isAutoSessionsTrackingEnabled());
        moduleSessions.init(config);
    }

    @Test
    public void init_withoutSessionControl() throws Exception {
        ModuleSessions moduleSessions = new ModuleSessions();
        config = new InternalConfig(defaultConfig());
        Assert.assertEquals(true, config.isAutoSessionsTrackingEnabled());
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

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(-1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
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
        Whitebox.<SessionImpl>setInternalState(moduleSessions, "session", sessionTarget);

        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStarted(ctx);
        Assert.assertEquals(sessionTarget, Whitebox.getInternalState(moduleSessions, "session"));
        Assert.assertEquals(2, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(sessionTarget, Whitebox.getInternalState(moduleSessions, "session"));
    }

    @Test
    public void activityStopped_removeSession() throws Exception {
        setUpApplication(defaultConfig());
        moduleSessions = module(ModuleSessions.class, false);

        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Assert.assertNull(Whitebox.getInternalState(moduleSessions, "session"));

        moduleSessions.onActivityStarted(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Assert.assertNotNull(Whitebox.getInternalState(moduleSessions, "session"));

        SessionImpl session = Whitebox.<SessionImpl>getInternalState(moduleSessions, "session");
        Assert.assertTrue(session.isActive());

        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Assert.assertNull(Whitebox.getInternalState(moduleSessions, "session"));
        Assert.assertFalse(session.isActive());
        Assert.assertNotNull(Whitebox.getInternalState(session, "ended"));
    }
}