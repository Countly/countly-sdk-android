package ly.count.android.sdk;

import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.app.Activity;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleViewsTests {
    //Countly mCountly;
    CountlyStore countlyStore;

    int idx = 0;
    String[] vals = new String[] { "idv1", "idv2", "idv3", "idv4", "idv5", "idv6", "idv7" };
    String base64Regex = "^[A-Za-z0-9+/]*={0,2}$";
    SafeIDGenerator safeIDGenerator;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
        idx = 0;
        safeIDGenerator = new SafeIDGenerator() {
            @NonNull @Override public String GenerateValue() {
                return vals[idx++];
            }
        };
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testSafeRandomVal() {
        @NonNull String result1 = Utils.safeRandomVal();
        @NonNull String result2 = Utils.safeRandomVal();

        Assert.assertNotNull(result1);
        Assert.assertNotNull(result2);
        Assert.assertTrue(result1.matches(base64Regex));
        Assert.assertTrue(result2.matches(base64Regex));
        Assert.assertEquals(21, result1.length(), result2.length());
        Assert.assertNotEquals(result1, result2);
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
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, shortNames, true, safeIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
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

        verify(ep).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null, null);
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
        @NonNull Activity act1 = mock(Activity.class);
        @NonNull Activity act2 = mock(Activity2.class);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, shortNames, true, safeIDGenerator, null).setAutoTrackingExceptions(new Class[] { act1.getClass() });
        Countly mCountly = new Countly().init(cc);

        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.moduleViews.onActivityStarted(act1);

        verify(ep, never()).recordEventInternal(anyString(), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class), any(String.class));

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

        verify(ep).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null, null);
    }

    @Test
    public void onActivityStartedDisabledOrientationView() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        mCountly.moduleViews.onActivityStarted(act);

        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(UtilsTime.Instant.class), any(String.class));
    }

    @Test
    public void onActivityStartedOrientation() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);

        ModuleViews mView = spy(new ModuleViews(mCountly, cc));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_PORTRAIT).when(mView).getOrientationFromActivity(act);

        Assert.assertEquals(-1, mView.currentOrientation);

        mCountly.moduleViews.onActivityStarted(act);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("mode", "portrait");

        verify(ep).recordEventInternal(ModuleViews.ORIENTATION_EVENT_KEY, segm, 1, 0.0, 0.0, null, null);

        Assert.assertEquals(Configuration.ORIENTATION_PORTRAIT, mView.currentOrientation);
    }

    @Test
    public void onConfigurationChangedOrientationDisabled() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Configuration conf = new Configuration();

        ModuleViews mView = spy(new ModuleViews(mCountly, cc));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_LANDSCAPE).when(mView).getOrientationFromConfiguration(conf);

        Assert.assertEquals(-1, mView.currentOrientation);
        mCountly.moduleViews.onConfigurationChanged(conf);

        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(UtilsTime.Instant.class), any(String.class));

        Assert.assertEquals(-1, mView.currentOrientation);
    }

    @Test
    public void onConfigurationChangedOrientation() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, true, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Configuration conf = new Configuration();

        ModuleViews mView = spy(new ModuleViews(mCountly, cc));
        mCountly.moduleViews = mView;
        doReturn(Configuration.ORIENTATION_LANDSCAPE).when(mView).getOrientationFromConfiguration(conf);

        Assert.assertEquals(-1, mView.currentOrientation);
        mCountly.moduleViews.onConfigurationChanged(conf);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("mode", "landscape");

        verify(ep).recordEventInternal(ModuleViews.ORIENTATION_EVENT_KEY, segm, 1, 0.0, 0.0, null, null);

        Assert.assertEquals(Configuration.ORIENTATION_LANDSCAPE, mView.currentOrientation);
    }

    /**
     * Verify that when calling "onStop" without calling "onStart", no event is created
     */
    @Test
    public void onActivityStopped() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, true, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.moduleViews.onActivityStopped();

        verify(ep, never()).recordEventInternal(anyString(), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class), null);
    }

    @Test
    public void onActivityStartedStopped() throws InterruptedException {
        Map<String, Object> segms = new HashMap<>();
        segms.put("aa", "11");
        segms.put("aagfg", "1133");
        segms.put("1", 123);
        segms.put("2", 234.0d);
        segms.put("3", true);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, true, true, safeIDGenerator, segms);
        Countly mCountly = new Countly().init(cc);

        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);

        int start = UtilsTime.currentTimestampSeconds();
        mCountly.moduleViews.onActivityStarted(act);
        Thread.sleep(100);
        mCountly.moduleViews.onActivityStopped();
        String dur = String.valueOf(UtilsTime.currentTimestampSeconds() - start);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("segment", "Android");
        segm.put("start", "1");
        segm.put("visit", "1");
        segm.put("_idv", vals[0]);
        segm.put("name", act.getClass().getSimpleName());
        segm.put("aa", "11");
        segm.put("aagfg", "1133");
        segm.put("1", 123);
        segm.put("2", 234.0d);
        segm.put("3", true);

        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null, null);

        segm.clear();
        segm.put("dur", dur);
        segm.put("segment", "Android");
        segm.put("_idv", vals[0]);
        segm.put("name", act.getClass().getSimpleName());

        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0.0, 0.0, null, null);
    }

    @Test
    public void recordViewNoSegm() throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, true, false, safeIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };

        final Map<String, Object> segm = new HashMap<>();
        segm.put("segment", "Android");
        segm.put("start", "1");
        segm.put("visit", "1");
        segm.put("_idv", vals[0]);
        segm.put("name", viewNames[0]);

        mCountly.views().recordView(viewNames[0]);

        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, null);
        Thread.sleep(1000);

        mCountly.views().recordView(viewNames[1]);
        segm.clear();
        segm.put("dur", "1");//todo rework to verify duration better
        segm.put("segment", "Android");
        segm.put("_idv", vals[0]);
        segm.put("name", viewNames[0]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, null);

        segm.clear();
        segm.put("segment", "Android");
        segm.put("_idv", vals[1]);
        segm.put("visit", "1");
        segm.put("name", viewNames[1]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, null);

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2]);
        segm.clear();
        segm.put("dur", "1");//todo rework to verify duration better
        segm.put("segment", "Android");
        segm.put("_idv", vals[1]);
        segm.put("name", viewNames[1]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, null);//todo this test has issues sometimes

        segm.clear();
        segm.put("segment", "Android");
        segm.put("_idv", vals[2]);
        segm.put("visit", "1");
        segm.put("name", viewNames[2]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, null);
    }

    @Test
    public void recordViewWithSegm() throws InterruptedException {
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
        cSegm2.put("_idv", "33");
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

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeIDGenerator, segms);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };
        final Map<String, Object> segm = new HashMap<>();

        mCountly.views().recordView(viewNames[0], cSegm1);

        segm.put("segment", "Android");
        segm.put("_idv", vals[0]);
        segm.put("start", "1");
        segm.put("visit", "1");
        segm.put("name", viewNames[0]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, "asd");
        Thread.sleep(2000);

        mCountly.views().recordView(viewNames[1], cSegm2);
        segm.clear();
        segm.put("dur", "1");
        segm.put("segment", "Android");
        segm.put("_idv", vals[0]);
        segm.put("name", viewNames[0]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, any(String.class));

        segm.clear();
        segm.put("segment", "Android");
        segm.put("_idv", vals[1]);
        segm.put("visit", "1");
        segm.put("name", viewNames[1]);
        segm.put("start", "33");
        segm.put("donker", "mag");
        segm.put("big", 1337);
        segm.put("candy", 954.33d);
        segm.put("calling", false);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, any(String.class));

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2], cSegm3);
        segm.clear();
        segm.put("dur", "1");
        segm.put("segment", "Android");
        segm.put("_idv", vals[1]);
        segm.put("name", viewNames[1]);
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, any(String.class));

        segm.clear();
        segm.put("segment", "Android");
        segm.put("_idv", vals[2]);
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
        verify(ep, times(1)).recordEventInternal(ModuleViews.VIEW_EVENT_KEY, segm, 1, 0, 0, null, any(String.class));
    }

    /**
     * Make sure that, when recording an event with an empty string key, that no event is creted
     */
    @Test
    public void recordViewEmptyViewName() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, true, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView("");
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(UtilsTime.Instant.class), any(String.class));
    }

    /**
     * Make sure that no view event is created when recording an event with no consent
     */
    @Test
    public void recordViewWithoutConsent() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, true, null, null).setRequiresConsent(false);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView(null);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }

    /**
     * Automatic view tracking is not enabled.
     * Changing activities should not record view events
     */
    @Test
    public void noViewRecordedWithAutomaticTurnedOffActChange() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null).setEventQueueSizeToSend(100);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        Activity2 act2 = mock(Activity2.class);

        //go from one activity to another in the expected way and then "go to background"
        mCountly.onStart(act);
        mCountly.onStart(act2);
        mCountly.onStop();
        mCountly.onStop();

        verify(ep, never()).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }

    @Test
    public void recordViewWithActivitiesAfterwardsAutoDisabled() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null).setEventQueueSizeToSend(100);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView("abcd");
        verify(ep, times(1)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class),any(String.class));
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        @NonNull Activity act2 = mock(Activity2.class);

        //go from one activity to another in the expected way and then "go to background"
        mCountly.onStart(act);
        mCountly.onStart(act2);
        mCountly.onStop();
        mCountly.onStop();

        verify(ep, never()).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));
    }
}
