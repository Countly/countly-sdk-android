package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.bytebuddy.asm.Advice;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ModuleViewsTests {
    //Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        //mCountly = new Countly();
        //mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
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
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(shortNames));
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
    public void onActivityStartedDisabledOrientationView(){
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
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
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setTrackOrientationChanges(true);
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
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
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
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setTrackOrientationChanges(true);
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
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true));
        EventQueue evQ = mock(EventQueue.class);

        mCountly.setEventQueue(evQ);

        mCountly.moduleViews.onActivityStopped();

        verify(evQ, never()).recordEvent(anyString(), any(Map.class), any(Map.class), any(Map.class), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class));
    }

    @Test
    public void onActivityStartedStopped() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true);

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

        mCountly.moduleViews.onActivityStopped();
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
        segmS.put("aa", "11");
        segmS.put("aagfg", "1133");

        segmI.put("1", 123);
        segmD.put("2", 234.0d);
        segmB.put("3", true);

        verify(evQ, times(1)).recordEvent(ModuleViews.VIEW_EVENT_KEY, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);
    }

    @Test
    public void recordViewNoSegm(){
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true));
        EventQueue evQ = mock(EventQueue.class);
        mCountly.setEventQueue(evQ);

        //mCountly.views().recordView()
    }
}
