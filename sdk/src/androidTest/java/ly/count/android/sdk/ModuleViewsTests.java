package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentMatchers;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(AndroidJUnit4.class)
public class ModuleViewsTests {
    //Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        //mCountly = new Countly();
        //mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void onActivityStartedViewTrackingLongNames(){
        activityStartedViewTracking(false);
    }

    @Test
    public void onActivityStartedViewTrackingShortNames(){
        activityStartedViewTracking(true);
    }

    void activityStartedViewTracking(boolean shortNames){
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(shortNames));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        Activity act = mock(Activity.class);
        mCountly.moduleViews.onActivityStarted(act);

        final Map<String, String> segmS = new HashMap<>(4);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        segmS.put("segment", "Android");
        segmS.put("start", "1");
        segmS.put("visit", "1");

        if(shortNames) {
            segmS.put("name", act.getClass().getSimpleName());
        } else {
            segmS.put("name", act.getClass().getName());
        }

        verify(evQ).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);
    }

    @Test
    public void onActivityStartedViewTrackingLongNamesException(){
        activityStartedViewTrackingException(false);
    }

    @Test
    public void onActivityStartedViewTrackingShortNamesException(){
        activityStartedViewTrackingException(true);
    }

    class Activity2 extends Activity {}

    void activityStartedViewTrackingException(boolean shortNames){
        Activity act1 = mock(Activity.class);
        Activity act2 = mock(Activity2.class);

        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(shortNames).setAutoTrackingExceptions(new Class[]{act1.getClass()});
        mCountly.init(config);
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        mCountly.moduleViews.onActivityStarted(act1);

        verify(evQ, never()).recordEvent(anyString(), any(Map.class), any(Map.class), any(Map.class), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class));

        mCountly.moduleViews.onActivityStarted(act2);

        final Map<String, String> segmS = new HashMap<>(4);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        segmS.put("segment", "Android");
        segmS.put("start", "1");
        segmS.put("visit", "1");

        if(shortNames) {
            segmS.put("name", act2.getClass().getSimpleName());
        } else {
            segmS.put("name", act2.getClass().getName());
        }

        verify(evQ).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);
    }

    @Test
    public void onActivityStartedDisabledOrientationView(){
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        Activity act = mock(Activity.class);
        mCountly.moduleViews.onActivityStarted(act);

        verify(evQ, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }

    @Test
    public void onActivityStartedOrientation(){
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setTrackOrientationChanges(true);
        mCountly.init(config);

        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        Activity act = mock(Activity.class);

        ModuleViews mView = spy(new ModuleViews(mCountly, config));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_PORTRAIT).when(mView).getOrientationFromActivity(act);

        Assert.assertEquals(-1, mView.currentOrientation);

        mCountly.moduleViews.onActivityStarted(act);

        final Map<String, String> segmS = new HashMap<>(1);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        segmS.put("mode", "portrait");

        verify(evQ).recordEvent(ModuleViews.ORIENTATION_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);

        Assert.assertEquals(Configuration.ORIENTATION_PORTRAIT, mView.currentOrientation);
    }

    @Test
    public void onConfigurationChangedOrientationDisabled() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        Configuration conf = new Configuration();

        ModuleViews mView = spy(new ModuleViews(mCountly, config));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_LANDSCAPE).when(mView).getOrientationFromConfiguration(conf);

        Assert.assertEquals(-1, mView.currentOrientation);
        mCountly.moduleViews.onConfigurationChanged(conf);

        verify(evQ, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(-1, mView.currentOrientation);
    }

    @Test
    public void onConfigurationChangedOrientation() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setTrackOrientationChanges(true);
        mCountly.init(config);

        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        Configuration conf = new Configuration();

        ModuleViews mView = spy(new ModuleViews(mCountly, config));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_LANDSCAPE).when(mView).getOrientationFromConfiguration(conf);

        Assert.assertEquals(-1, mView.currentOrientation);
        mCountly.moduleViews.onConfigurationChanged(conf);

        final Map<String, String> segmS = new HashMap<>(1);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        segmS.put("mode", "landscape");

        verify(evQ).recordEvent(ModuleViews.ORIENTATION_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);

        Assert.assertEquals(Configuration.ORIENTATION_LANDSCAPE, mView.currentOrientation);
    }

    @Test
    public void onActivityStopped(){
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true));
        EventQueue evQ = mock(EventQueue.class);

        mCountly.setEventQueue(evQ);

        mCountly.moduleViews.onActivityStopped(mock(Activity.class));

        verify(evQ, never()).recordEvent(anyString(), any(Map.class), any(Map.class), any(Map.class), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class));
    }

    @Test
    public void onActivityStartedStopped() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true);

        Map<String, Object> segms = new HashMap<>();
        segms.put("aa", "11");
        segms.put("aagfg", "1133");
        segms.put("1", 123);
        segms.put("2", 234.0d);
        segms.put("3", true);

        config.setAutomaticViewSegmentation(segms);
        mCountly.init(config);

        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        Activity act = mock(Activity.class);

        int start = UtilsTime.currentTimestampSeconds();

        mCountly.moduleViews.onActivityStarted(act);

        Thread.sleep(100);

        mCountly.moduleViews.onActivityStopped(act);
        String dur = String.valueOf(UtilsTime.currentTimestampSeconds() - start);

        final Map<String, String> segmS = new HashMap<>(4);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        segmS.put("segment", "Android");
        segmS.put("start", "1");
        segmS.put("visit", "1");
        segmS.put("name", act.getClass().getSimpleName());
        segmS.put("aa", "11");
        segmS.put("aagfg", "1133");

        segmI.put("1", 123);
        segmD.put("2", 234.0d);
        segmB.put("3", true);


        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);

        segmS.clear();
        segmI.clear();
        segmD.clear();
        segmB.clear();

        segmS.put("dur", dur);
        segmS.put("segment", "Android");
        segmS.put("name", act.getClass().getSimpleName());

        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);
    }

    @Test
    public void recordViewNoSegm() throws InterruptedException {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        String[] viewNames = new String[] {"DSD", "32", "DSD"};

        final Map<String, String> segmS = new HashMap<>(4);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        segmS.put("segment", "Android");
        segmS.put("start", "1");
        segmS.put("visit", "1");
        segmS.put("name", viewNames[0]);

        mCountly.views().recordView(viewNames[0]);

        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);
        Thread.sleep(1000);

        mCountly.views().endViewRecording(viewNames[0]);
        mCountly.views().recordView(viewNames[1]);
        segmS.clear();
        segmS.put("dur", "1");
        segmS.put("segment", "Android");
        segmS.put("name", viewNames[0]);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);

        segmS.clear();
        segmS.put("segment", "Android");
        segmS.put("visit", "1");
        segmS.put("name", viewNames[1]);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);

        Thread.sleep(1000);
        mCountly.views().endViewRecording(viewNames[1]);
        mCountly.views().recordView(viewNames[2]);
        segmS.clear();
        segmS.put("dur", "1");
        segmS.put("segment", "Android");
        segmS.put("name", viewNames[1]);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);

        segmS.clear();
        segmS.put("segment", "Android");
        segmS.put("visit", "1");
        segmS.put("name", viewNames[2]);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);
    }

    @Test
    public void recordViewWithSegm() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true);

        Map<String, Object> segms = new HashMap<>();
        segms.put("aa", "11");
        segms.put("aagfg", "1133");
        segms.put("1", 123);
        segms.put("2", 234.0d);
        segms.put("3", true);

//{"name", "segment", "visit", "start", "bounce", "exit", "view", "domain", "dur"};
        Map<String, Object> cSegm1 = new HashMap<>();
        Map<String, Object> cSegm2 = new HashMap<>();
        cSegm2.put("name", "33");
        cSegm2.put("segment", "33");
        cSegm2.put("visit", "33");
        cSegm2.put("start", "33");
        cSegm2.put("donker", "mag");
        cSegm2.put("big", 1337);
        cSegm2.put("candy", 954.33d);
        cSegm2.put("calling", false);

        Map<String, Object> cSegm3 = new HashMap<>();
        cSegm3.put("aaaaaaaaaaaaaaaaaaaaCountly", "33");
        cSegm3.put("exit", "33");
        cSegm3.put("view", "33");
        cSegm3.put("domain", "33");
        cSegm3.put("dur", "33");
        cSegm3.put("doddnker", "m123ag");
        cSegm3.put("biffg", 132137);
        cSegm3.put("cannndy", 9534.33d);
        cSegm3.put("calaaling", true);

        config.setAutomaticViewSegmentation(segms);
        mCountly.init(config);
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        String[] viewNames = new String[] {"DSD", "32", "DSD"};

        final Map<String, String> segmS = new HashMap<>(4);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        mCountly.views().recordView(viewNames[0], cSegm1);

        segmS.put("segment", "Android");
        segmS.put("start", "1");
        segmS.put("visit", "1");
        segmS.put("name", viewNames[0]);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);
        Thread.sleep(1000);

        mCountly.views().endViewRecording(viewNames[0]);
        mCountly.views().recordView(viewNames[1], cSegm2);
        segmS.clear();
        segmI.clear();
        segmD.clear();
        segmB.clear();
        segmS.put("dur", "1");
        segmS.put("segment", "Android");
        segmS.put("name", viewNames[0]);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);

        segmS.clear();
        segmI.clear();
        segmD.clear();
        segmB.clear();
        segmS.put("segment", "Android");
        segmS.put("visit", "1");
        segmS.put("name", viewNames[1]);
        segmS.put("start", "33");
        segmS.put("donker", "mag");
        segmI.put("big", 1337);
        segmD.put("candy", 954.33d);
        segmB.put("calling", false);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);

        Thread.sleep(1000);
        mCountly.views().endViewRecording(viewNames[1]);
        mCountly.views().recordView(viewNames[2], cSegm3);
        segmS.clear();
        segmI.clear();
        segmD.clear();
        segmB.clear();
        segmS.put("dur", "1");
        segmS.put("segment", "Android");
        segmS.put("name", viewNames[1]);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);

        segmS.clear();
        segmI.clear();
        segmD.clear();
        segmB.clear();
        segmS.put("segment", "Android");
        segmS.put("visit", "1");
        segmS.put("name", viewNames[2]);
        segmS.put("doddnker", "m123ag");
        segmS.put("exit", "33");
        segmS.put("view", "33");
        segmS.put("domain", "33");
        segmS.put("dur", "33");
        segmI.put("biffg", 132137);
        segmD.put("cannndy", 9534.33d);
        segmB.put("calaaling", true);
        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0, 0, null);
    }

    @Test
    public void recordViewNullViewName()  {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        ModuleEvents mEvents = mock(ModuleEvents.class);
        mCountly.moduleEvents = mEvents;

        ArgumentCaptor<UtilsTime.Instant> argInst = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<String> argS = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argI = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> argD1 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> argD2 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Boolean> argB = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Map<String, Object>> argM = ArgumentCaptor.forClass(Map.class);

        mCountly.views().recordView(null);

        String mockInv = mockingDetails(mEvents).printInvocations();
        System.out.println(mockInv);

        System.out.println(mockingDetails(mEvents).getInvocations());

        verify(mEvents, times(0)).recordEventInternal(argS.capture(), argM.capture(), argI.capture(), argD1.capture(), argD2.capture(), argInst.capture(), argB.capture());
    }

    @Test
    public void recordViewEmptyViewName()  {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        ModuleEvents mEvents = mock(ModuleEvents.class);
        mCountly.moduleEvents = mEvents;

        ArgumentCaptor<UtilsTime.Instant> argInst = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<String> argS = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argI = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> argD1 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> argD2 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Boolean> argB = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Map<String, Object>> argM = ArgumentCaptor.forClass(Map.class);

        mCountly.views().recordView("");

        String mockInv = mockingDetails(mEvents).printInvocations();
        System.out.println(mockInv);

        System.out.println(mockingDetails(mEvents).getInvocations());

        verify(mEvents, times(0)).recordEventInternal(argS.capture(), argM.capture(), argI.capture(), argD1.capture(), argD2.capture(), argInst.capture(), argB.capture());
    }

    @Test
    public void recordViewWithoutConsent()  {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting()
            .setViewTracking(true).setAutoTrackingUseShortName(true).setRequiresConsent(true));

        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        ModuleEvents mEvents = mock(ModuleEvents.class);
        mCountly.moduleEvents = mEvents;

        ArgumentCaptor<UtilsTime.Instant> argInst = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<String> argS = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argI = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> argD1 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> argD2 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Boolean> argB = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Map<String, Object>> argM = ArgumentCaptor.forClass(Map.class);

        mCountly.views().recordView(null);

        String mockInv = mockingDetails(mEvents).printInvocations();
        System.out.println(mockInv);

        System.out.println(mockingDetails(mEvents).getInvocations());

        verify(mEvents, times(0)).recordEventInternal(argS.capture(), argM.capture(), argI.capture(), argD1.capture(), argD2.capture(), argInst.capture(), argB.capture());
    }

    @Test
    public void recordActivityByIdentity() {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(),
            "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting()
            .setViewTracking(true).setAutoTrackingUseShortName(true).setRequiresConsent(false));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);
        ModuleEvents mEvents = mock(ModuleEvents.class);
        mCountly.moduleEvents = mEvents;
        Object obj1 = new Object();
        Object obj2 = new Object();
        mCountly.views().recordView(obj1);
        Assert.assertEquals(1, mCountly.moduleViews.identityStartTimes.size());
        mCountly.views().recordView(obj2);
        Assert.assertEquals(2, mCountly.moduleViews.identityStartTimes.size());
        mCountly.views().endViewRecording(obj2);
        Assert.assertEquals(1, mCountly.moduleViews.identityStartTimes.size());
        mCountly.views().endViewRecording(obj1);
        Assert.assertEquals(0, mCountly.moduleViews.identityStartTimes.size());
        verify(mEvents, times(4)).recordEventInternal(anyString(), any(Map.class), anyInt(), anyDouble(), anyDouble(), ArgumentMatchers.<UtilsTime.Instant>isNull(), anyBoolean());
    }


    @PersistentName("Foo")
    private static class PersistentNameObject { }

    @Test
    public void usePersistentName() {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(),
            "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting()
            .setViewTracking(true).setAutoTrackingUseShortName(true).setRequiresConsent(false));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);
        ModuleEvents mEvents = mock(ModuleEvents.class);
        mCountly.moduleEvents = mEvents;
        PersistentNameObject obj = new PersistentNameObject();
        mCountly.views().recordView(obj);
        ArgumentCaptor<Map<String, Object>> argMap = ArgumentCaptor.forClass(Map.class);
        verify(mEvents, times(1)).recordEventInternal(anyString(), argMap.capture(), anyInt(), anyDouble(), anyDouble(), ArgumentMatchers.<UtilsTime.Instant>isNull(), anyBoolean());
        Map<String, Object> map = argMap.getValue();
        String name = (String) map.get("name");
        Assert.assertNotNull(name);
        //noinspection ConstantConditions
        Assert.assertEquals(PersistentNameObject.class.getAnnotation(PersistentName.class).value(), name);
    }

    @DoNotTrack
    private static class DoNotTrackObject { }

    @Test
    public void ignoreDoNotTrackObject() {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(),
            "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting()
            .setViewTracking(true).setAutoTrackingUseShortName(true).setRequiresConsent(false));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);
        ModuleEvents mEvents = mock(ModuleEvents.class);
        mCountly.moduleEvents = mEvents;
        DoNotTrackObject obj = new DoNotTrackObject();
        mCountly.views().recordView(obj);
        verifyZeroInteractions(mEvents);
    }
}
