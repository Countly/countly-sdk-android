package ly.count.android.sdk.internal;

import android.app.Activity;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.Map;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.Eve;
import ly.count.android.sdk.View;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleViewsTests extends BaseTests {
    private static final String firstViewName = "firstViewName";
    private static final String secondViewName = "secondViewName";

    @Override
    protected Config defaultConfig() throws MalformedURLException {
        return super.defaultConfig().addFeature(Config.Feature.AutomaticViewTracking);
    }

    @Test
    public void startStop() throws Exception {
        setUpApplication(null);

        SessionImpl session = Core.instance.session(ctx, null);
        View first = session.view(firstViewName);

        Assert.assertEquals(1, session.events.size());

        Eve start = session.events.get(0);
        validateView(first, start, firstViewName, true, true, false);

        first.start(false);
        Assert.assertEquals(true, Whitebox.getInternalState(first, "firstView"));
        Assert.assertEquals(false, Whitebox.getInternalState(first, "ended"));

        View second = session.view(secondViewName);
        Assert.assertEquals(3, session.events.size());

        Eve end = session.events.get(1);
        start = session.events.get(2);
        validateView(first, end, firstViewName, false, true, false);
        validateView(second, start, secondViewName, true, false, false);

        second.end(true);
        Assert.assertEquals(4, session.events.size());
        end = session.events.get(3);
        validateView(second, end, secondViewName, false, false, true);
    }

    @Test
    public void autoView() throws Exception {
        setUpApplication(null);

        ModuleViews moduleViews = module(ModuleViews.class, false);
        ModuleSessions moduleSessions = module(ModuleSessions.class, false);
        Assert.assertNotNull(moduleViews);
        Assert.assertNotNull(moduleSessions);

        Activity activity1 = mock(Activity.class), activity2 = mock(Activity.class);
        String firstViewName = activity1.getClass().getName(), secondViewName = activity2.getClass().getName();

        Assert.assertNull(Core.instance.getSession());
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Whitebox.invokeMethod(Core.instance, "onActivityStartedInternal", activity1);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));

        SessionImpl session = Core.instance.getSession();
        Assert.assertNotNull(session);
        Assert.assertEquals(1, session.events.size());

        Eve start = session.events.get(0);
        View first = Whitebox.<View>getInternalState(session, "currentView");
        Assert.assertNotNull(first);
        validateView(first, start, firstViewName, true, true, false);

        Whitebox.invokeMethod(Core.instance, "onActivityStartedInternal", activity2);
        Assert.assertEquals(2, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        session = Core.instance.getSession();
        Assert.assertNotNull(session);
        Assert.assertEquals(3, session.events.size());

        View second = Whitebox.<View>getInternalState(session, "currentView");
        Assert.assertNotNull(second);

        Eve end = session.events.get(1);
        start = session.events.get(2);
        validateView(first, end, firstViewName, false, true, false);
        validateView(second, start, secondViewName, true, false, false);

        Whitebox.invokeMethod(Core.instance, "onActivityStoppedInternal", activity1);
        Assert.assertEquals(1, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        session = Core.instance.getSession();
        Assert.assertNotNull(session);
        Assert.assertEquals(3, session.events.size());

        Whitebox.invokeMethod(Core.instance, "onActivityStoppedInternal", activity2);
        Assert.assertEquals(0, (int) Whitebox.<Integer>getInternalState(moduleSessions, "activityCount"));
        Assert.assertNull(Core.instance.getSession());
        Assert.assertEquals(4, session.events.size());
        end = session.events.get(3);
        validateView(second, end, secondViewName, false, false, true);
    }

    private void validateView(View view, Eve event, String name, boolean start, boolean firstView, boolean lastView) {
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
