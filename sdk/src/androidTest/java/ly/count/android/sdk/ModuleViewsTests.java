package ly.count.android.sdk;

import android.content.res.Configuration;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.app.Activity;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleViewsTests {
    //Countly mCountly;
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void onActivityStartedViewTrackingLongNames() {
        activityStartedViewTracking(false);
    }

    @Test
    public void onActivityStartedViewTrackingShortNames() {
        activityStartedViewTracking(true);
    }

    void activityStartedViewTracking(boolean shortNames) {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(shortNames));
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Activity act = mock(Activity.class);
        mCountly.moduleViews.onActivityStarted(act);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("segment", "Android");
        segm.put("start", "1");
        segm.put("visit", "1");

        if (shortNames) {
            segm.put("name", act.getClass().getSimpleName());
        } else {
            segm.put("name", act.getClass().getName());
        }

        verify(ep).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null);
    }

    @Test
    public void onActivityStartedViewTrackingLongNamesException() {
        activityStartedViewTrackingException(false);
    }

    @Test
    public void onActivityStartedViewTrackingShortNamesException() {
        activityStartedViewTrackingException(true);
    }

    class Activity2 extends Activity {
    }

    void activityStartedViewTrackingException(boolean shortNames) {
        Activity act1 = mock(Activity.class);
        Activity act2 = mock(Activity2.class);

        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(shortNames).setAutoTrackingExceptions(new Class[] { act1.getClass() });
        mCountly.init(config);
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.moduleViews.onActivityStarted(act1);

        verify(ep, never()).recordEventInternal(anyString(), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class));

        mCountly.moduleViews.onActivityStarted(act2);

        final Map<String, Object> segm = new HashMap<>();

        segm.put("segment", "Android");
        segm.put("start", "1");
        segm.put("visit", "1");

        if (shortNames) {
            segm.put("name", act2.getClass().getSimpleName());
        } else {
            segm.put("name", act2.getClass().getName());
        }

        verify(ep).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null);
    }

    @Test
    public void onActivityStartedDisabledOrientationView() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Activity act = mock(Activity.class);
        mCountly.moduleViews.onActivityStarted(act);

        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(UtilsTime.Instant.class));
    }

    @Test
    public void onActivityStartedOrientation() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setTrackOrientationChanges(true);
        mCountly.init(config);
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Activity act = mock(Activity.class);

        ModuleViews mView = spy(new ModuleViews(mCountly, config));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_PORTRAIT).when(mView).getOrientationFromActivity(act);

        Assert.assertEquals(-1, mView.currentOrientation);

        mCountly.moduleViews.onActivityStarted(act);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("mode", "portrait");

        verify(ep).recordEventInternal(ModuleViews.ORIENTATION_EVENT_KEY, segm, 1, 0.0, 0.0, null);

        Assert.assertEquals(Configuration.ORIENTATION_PORTRAIT, mView.currentOrientation);
    }

    @Test
    public void onConfigurationChangedOrientationDisabled() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setTrackOrientationChanges(false);
        mCountly.init(config);
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Configuration conf = new Configuration();

        ModuleViews mView = spy(new ModuleViews(mCountly, config));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_LANDSCAPE).when(mView).getOrientationFromConfiguration(conf);

        Assert.assertEquals(-1, mView.currentOrientation);
        mCountly.moduleViews.onConfigurationChanged(conf);

        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(UtilsTime.Instant.class));

        Assert.assertEquals(-1, mView.currentOrientation);
    }

    @Test
    public void onConfigurationChangedOrientation() {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setTrackOrientationChanges(true);
        mCountly.init(config);
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Configuration conf = new Configuration();

        ModuleViews mView = spy(new ModuleViews(mCountly, config));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_LANDSCAPE).when(mView).getOrientationFromConfiguration(conf);

        Assert.assertEquals(-1, mView.currentOrientation);
        mCountly.moduleViews.onConfigurationChanged(conf);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("mode", "landscape");

        verify(ep).recordEventInternal(ModuleViews.ORIENTATION_EVENT_KEY, segm, 1, 0.0, 0.0, null);

        Assert.assertEquals(Configuration.ORIENTATION_LANDSCAPE, mView.currentOrientation);
    }

    /**
     * Verify that when calling "onStop" without calling "onStart", no event is created
     */
    @Test
    public void onActivityStopped() {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true));
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.moduleViews.onActivityStopped();

        verify(ep, never()).recordEventInternal(anyString(), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class));
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
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Activity act = mock(Activity.class);

        int start = UtilsTime.currentTimestampSeconds();
        mCountly.moduleViews.onActivityStarted(act);
        Thread.sleep(100);
        mCountly.moduleViews.onActivityStopped();
        String dur = String.valueOf(UtilsTime.currentTimestampSeconds() - start);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("segment", "Android");
        segm.put("start", "1");
        segm.put("visit", "1");
        segm.put("name", act.getClass().getSimpleName());
        segm.put("aa", "11");
        segm.put("aagfg", "1133");
        segm.put("1", 123);
        segm.put("2", 234.0d);
        segm.put("3", true);

        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null);

        segm.clear();
        segm.put("dur", dur);
        segm.put("segment", "Android");
        segm.put("name", act.getClass().getSimpleName());

        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null);
    }

    @Test
    public void recordViewNoSegm() throws InterruptedException {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true));
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };

        final Map<String, Object> segm = new HashMap<>();
        segm.put("segment", "Android");
        segm.put("start", "1");
        segm.put("visit", "1");
        segm.put("name", viewNames[0]);

        mCountly.views().recordView(viewNames[0]);

        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);
        Thread.sleep(1000);

        mCountly.views().recordView(viewNames[1]);
        segm.clear();
        segm.put("dur", "1");//todo rework to verify duration better
        segm.put("segment", "Android");
        segm.put("name", viewNames[0]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);

        segm.clear();
        segm.put("segment", "Android");
        segm.put("visit", "1");
        segm.put("name", viewNames[1]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2]);
        segm.clear();
        segm.put("dur", "1");//todo rework to verify duration better
        segm.put("segment", "Android");
        segm.put("name", viewNames[1]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);//todo this test has issues sometimes

        segm.clear();
        segm.put("segment", "Android");
        segm.put("visit", "1");
        segm.put("name", viewNames[2]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);
    }

    @Test
    public void recordViewWithSegm() throws InterruptedException {
        Countly mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setViewTracking(true);

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
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };
        final Map<String, Object> segm = new HashMap<>();

        mCountly.views().recordView(viewNames[0], cSegm1);

        segm.put("segment", "Android");
        segm.put("start", "1");
        segm.put("visit", "1");
        segm.put("name", viewNames[0]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);
        Thread.sleep(1000);

        mCountly.views().recordView(viewNames[1], cSegm2);
        segm.clear();
        segm.put("dur", "1");
        segm.put("segment", "Android");
        segm.put("name", viewNames[0]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);

        segm.clear();
        segm.put("segment", "Android");
        segm.put("visit", "1");
        segm.put("name", viewNames[1]);
        segm.put("start", "33");
        segm.put("donker", "mag");
        segm.put("big", 1337);
        segm.put("candy", 954.33d);
        segm.put("calling", false);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2], cSegm3);
        segm.clear();
        segm.put("dur", "1");
        segm.put("segment", "Android");
        segm.put("name", viewNames[1]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);

        segm.clear();
        segm.put("segment", "Android");
        segm.put("visit", "1");
        segm.put("name", viewNames[2]);
        segm.put("doddnker", "m123ag");
        segm.put("exit", "33");
        segm.put("view", "33");
        segm.put("domain", "33");
        segm.put("dur", "33");
        segm.put("biffg", 132137);
        segm.put("cannndy", 9534.33d);
        segm.put("calaaling", true);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null);
    }

    /**
     * Make sure that, when recording an event with an empty string key, that no event is creted
     */
    @Test
    public void recordViewEmptyViewName() {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).setViewTracking(true));
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView("");
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(UtilsTime.Instant.class));
    }

    /**
     * Make sure that no view event is created when recording an event with no consent
     */
    @Test
    public void recordViewWithoutConsent() {
        Countly mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true)
            .setViewTracking(true).setRequiresConsent(true));
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView(null);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }

    /**
     * Automatic view tracking is not enabled.
     * Changing activities should not record view events
     */
    @Test
    public void noViewRecordedWithAutomaticTurnedOffActChange() {
        CountlyConfig cc = new CountlyConfig(getContext(), "appkey", "http://test.count.ly");
        cc.setDeviceId("1234").setLoggingEnabled(true).setViewTracking(false).setEventQueueSizeToSend(100);

        Countly mCountly = new Countly();
        mCountly.init(cc);
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Activity act = mock(Activity.class);
        Activity2 act2 = mock(Activity2.class);

        //go from one activity to another in the expected way and then "go to background"
        mCountly.onStart(act);
        mCountly.onStart(act2);
        mCountly.onStop();
        mCountly.onStop();

        verify(ep, never()).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }

    @Test
    public void recordViewWithActivitiesAfterwardsAutoDisabled() {
        CountlyConfig cc = new CountlyConfig(getContext(), "appkey", "http://test.count.ly");
        cc.setDeviceId("1234").setLoggingEnabled(true).setViewTracking(false).setEventQueueSizeToSend(100);

        Countly mCountly = new Countly();
        mCountly.init(cc);
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView("abcd");
        verify(ep, times(1)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Activity act = mock(Activity.class);
        Activity act2 = mock(Activity2.class);

        //go from one activity to another in the expected way and then "go to background"
        mCountly.onStart(act);
        mCountly.onStart(act2);
        mCountly.onStop();
        mCountly.onStop();

        verify(ep, never()).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }
}
