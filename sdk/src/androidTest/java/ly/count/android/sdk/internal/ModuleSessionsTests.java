package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.util.List;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleSessionsTests extends BaseTests {
    ModuleSessions moduleSessions;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test (expected = NullPointerException.class)
    public void init_null(){
        ModuleSessions moduleSessions = new ModuleSessions();
        moduleSessions.init(null);
    }

    @Test (expected = IllegalStateException.class)
    public void init_withSessionControl() throws Exception {
        ModuleSessions moduleSessions = new ModuleSessions();
        config = new InternalConfig(defaultConfig());
        config.enableProgrammaticSessionsControl();

        Assert.assertEquals(true, config.isProgrammaticSessionsControl());
        moduleSessions.init(config);
    }

    @Test
    public void init_withoutSessionControl() throws Exception {
        ModuleSessions moduleSessions = new ModuleSessions();
        config = new InternalConfig(defaultConfig());
        Assert.assertEquals(false, config.isProgrammaticSessionsControl());
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

        List<SessionImpl> sessions = Whitebox.<List<SessionImpl>>getInternalState(core, "sessions");
        SessionImpl sessionTarget = new SessionImpl(ctx, 123L);
        sessionTarget.begin().end();
        sessions.add(sessionTarget);

        moduleSessions.onActivityStarted(ctx);
        moduleSessions.onActivityStarted(ctx);
        Assert.assertEquals(true, (boolean)sessionTarget.isLeading());
        Assert.assertEquals(2, sessions.size());
        Assert.assertEquals(2, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        Assert.assertEquals(true, (boolean)sessionTarget.isLeading());
        Assert.assertEquals(2, sessions.size());
    }

    @Test
    public void activityStopped_removeSession() throws Exception {
        setUpApplication(defaultConfig());
        moduleSessions = module(ModuleSessions.class, false);

        List<SessionImpl> sessions = Whitebox.<List<SessionImpl>>getInternalState(core, "sessions");
        SessionImpl sessionTarget = new SessionImpl(ctx, 123L);
        sessionTarget.begin(234L);
//        sessionTarget.end(456L, null, null);

        Assert.assertEquals(0, sessions.size());
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        moduleSessions.onActivityStarted(ctx);
        sessions.clear();
        sessions.add(sessionTarget);
        Assert.assertEquals(true, (boolean)sessionTarget.isLeading());
        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        moduleSessions.onActivityStopped(ctx);
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        Assert.assertEquals(false, (boolean)sessionTarget.isLeading());
        Assert.assertEquals(0, sessions.size());
    }
}