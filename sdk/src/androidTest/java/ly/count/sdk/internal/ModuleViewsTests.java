package ly.count.sdk.internal;

import android.app.Activity;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;


import java.util.Map;

import ly.count.sdk.Event;
import ly.count.sdk.View;
import ly.count.sdk.android.Config;
import ly.count.sdk.android.internal.BaseTests;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleViewsTests extends BaseTests {
    private static final String firstViewName = "firstViewName";
    private static final String secondViewName = "secondViewName";

    @Test
    public void filler(){

    }

    @Override
    protected Config defaultConfig() throws Exception {
        return super.defaultConfig().enableFeatures(Config.Feature.Views);
    }

    @Test
    public void startStop() throws Exception {
        setUpApplication(null);

        SessionImpl session = SDKCore.instance.session(ctx, null);
        View first = session.view(firstViewName);

        Assert.assertEquals(1, session.events.size());

        Event start = session.events.get(0);
        validateView(first, start, firstViewName, true, true, false);

        first.start(false);
        Assert.assertEquals(true, Whitebox.getInternalState(first, "firstView"));
        Assert.assertEquals(false, Whitebox.getInternalState(first, "ended"));

        View second = session.view(secondViewName);
        Assert.assertEquals(3, session.events.size());

        Event end = session.events.get(1);
        start = session.events.get(2);
        validateView(first, end, firstViewName, false, true, false);
        validateView(second, start, secondViewName, true, false, false);

        second.stop(true);
        Assert.assertEquals(4, session.events.size());
        end = session.events.get(3);
        validateView(second, end, secondViewName, false, false, true);
    }

    @Test
    public void autoView() throws Exception {
        setUpApplication(null);

        ModuleViews moduleViews = module(ModuleViews.class, false);
        ModuleSessions moduleAutoSessions = module(ModuleSessions.class, false);
        Assert.assertNotNull(moduleViews);
        Assert.assertNotNull(moduleAutoSessions);

        Activity activity1 = mock(Activity.class), activity2 = mock(Activity.class);
        String firstViewName = activity1.getClass().getName(), secondViewName = activity2.getClass().getName();

        Assert.assertNull(SDKCore.instance.getSession());
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        Whitebox.invokeMethod(SDKCore.instance, "onActivityStartedInternal", activity1);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));

        SessionImpl session = SDKCore.instance.getSession();
        Assert.assertNotNull(session);
        Assert.assertEquals(1, session.events.size());

        Event start = session.events.get(0);
        View first = Whitebox.<View>getInternalState(session, "currentView");
        Assert.assertNotNull(first);
        validateView(first, start, firstViewName, true, true, false);

        Whitebox.invokeMethod(SDKCore.instance, "onActivityStartedInternal", activity2);
        Assert.assertEquals(2, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        session = SDKCore.instance.getSession();
        Assert.assertNotNull(session);
        Assert.assertEquals(3, session.events.size());

        View second = Whitebox.<View>getInternalState(session, "currentView");
        Assert.assertNotNull(second);

        Event end = session.events.get(1);
        start = session.events.get(2);
        validateView(first, end, firstViewName, false, true, false);
        validateView(second, start, secondViewName, true, false, false);

        Whitebox.invokeMethod(SDKCore.instance, "onActivityStoppedInternal", activity1);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        session = SDKCore.instance.getSession();
        Assert.assertNotNull(session);
        Assert.assertEquals(3, session.events.size());

        Whitebox.invokeMethod(SDKCore.instance, "onActivityStoppedInternal", activity2);
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleAutoSessions, "activityCount"));
        Assert.assertNull(SDKCore.instance.getSession());
        Assert.assertEquals(0, session.events.size());
    }

    private void validateView(View view, Event event, String name, boolean start, boolean firstView, boolean lastView) {
        if (start) {
            Assert.assertNull(Whitebox.<Double>getInternalState(event, "duration"));
        } else {
            Assert.assertTrue(Whitebox.<Double>getInternalState(event, "duration") > 0);
        }

        Assert.assertEquals(ViewImpl.EVENT, Whitebox.getInternalState(event, "key"));

        Assert.assertEquals(name, Whitebox.getInternalState(view, "name"));
        Assert.assertEquals(name, Whitebox.<Map<String, String>>getInternalState(event, "segmentation").get(ViewImpl.NAME));

        Assert.assertEquals(true, Whitebox.getInternalState(view, "started"));
        Assert.assertEquals(!start, Whitebox.getInternalState(view, "ended"));

        Assert.assertEquals(firstView, Whitebox.getInternalState(view, "firstView"));
        Assert.assertEquals(start ? ViewImpl.VISIT_VALUE : null, Whitebox.<Map<String, String>>getInternalState(event, "segmentation").get(ViewImpl.VISIT));

        Assert.assertEquals(lastView ? ViewImpl.EXIT_VALUE : null, Whitebox.<Map<String, String>>getInternalState(event, "segmentation").get(ViewImpl.EXIT));

        Assert.assertEquals(ViewImpl.SEGMENT_VALUE, Whitebox.<Map<String, String>>getInternalState(event, "segmentation").get(ViewImpl.SEGMENT));
    }
}
