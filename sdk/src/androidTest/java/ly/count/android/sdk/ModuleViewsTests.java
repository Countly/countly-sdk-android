package ly.count.android.sdk;

import android.app.assist.AssistStructure;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
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
        if (shortNames) {
            ClearFillSegmentationViewStart(segm, act.getClass().getSimpleName(), true);
        } else {
            ClearFillSegmentationViewStart(segm, act.getClass().getName(), true);
        }

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
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

    class Activity3 extends Activity {
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
        if (shortNames) {
            ClearFillSegmentationViewStart(segm, act2.getClass().getSimpleName(), true);
        } else {
            ClearFillSegmentationViewStart(segm, act2.getClass().getName(), true);
        }

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
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

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.ORIENTATION_EVENT_KEY, segm, null, 0, 1);

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

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.ORIENTATION_EVENT_KEY, segm, null, 0, 1);

        Assert.assertEquals(Configuration.ORIENTATION_LANDSCAPE, mView.currentOrientation);
    }

    /**
     * Verify that when calling "onStop" without calling "onStart", no event is created
     * In either of the degenerate cases, there should be no view duration recorded
     */
    @Test
    public void onActivityStopped() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, true, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.moduleViews.onActivityStopped(0);
        mCountly.moduleViews.onActivityStopped(-1);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
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
        mCountly.moduleViews.onActivityStarted(act);//activity count = 1
        Thread.sleep(100);
        mCountly.moduleViews.onActivityStopped(0);//activity count = 0
        String dur = String.valueOf(UtilsTime.currentTimestampSeconds() - start);

        final Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, act.getClass().getSimpleName(), true);
        segm.put("aa", "11");
        segm.put("aagfg", "1133");
        segm.put("1", 123);
        segm.put("2", 234.0d);
        segm.put("3", true);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 2);
        ClearFillSegmentationViewEnd(segm, act.getClass().getSimpleName(), dur);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 1, 2);
    }

    @Test
    public void recordViewNoSegm() throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, true, false, safeIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };

        final Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, viewNames[0], true);

        mCountly.views().recordView(viewNames[0]);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        Thread.sleep(1000);

        mCountly.views().recordView(viewNames[1]);
        ClearFillSegmentationViewEnd(segm, viewNames[0], "1");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[1], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 1, 2);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2]);
        ClearFillSegmentationViewEnd(segm, viewNames[1], "1");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 0, 2);//todo this test has issues sometimes

        ClearFillSegmentationViewStart(segm, viewNames[2], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 1, 2);
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

        ClearFillSegmentationViewStart(segm, viewNames[0], true);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        Thread.sleep(2000);

        mCountly.views().recordView(viewNames[1], cSegm2);
        ClearFillSegmentationViewEnd(segm, viewNames[0], "2");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 2); // duration comes off sometimes

        ClearFillSegmentationViewStart(segm, viewNames[1], false);
        segm.put("start", "33");
        segm.put("donker", "mag");
        segm.put("big", 1337);
        segm.put("candy", 954.33d);
        segm.put("calling", false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 1, 2);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2], cSegm3);
        ClearFillSegmentationViewEnd(segm, viewNames[1], "1");

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[2], false);
        segm.put("doddnker", "m123ag");
        segm.put("exit", "33");
        segm.put("view", "33");
        segm.put("domain", "33");
        segm.put("dur", "33");
        segm.put("biffg", 132137);
        segm.put("cannndy", 9534.33d);
        segm.put("calaaling", true);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 1, 2);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
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
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
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
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
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

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
    }

    @Test
    public void recordViewWithActivitiesAfterwardsAutoDisabled() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null).setEventQueueSizeToSend(100);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView("abcd");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        @NonNull Activity act2 = mock(Activity2.class);

        final Map<String, Object> segm = new HashMap<>();

        //make sure nothing was happening here before
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        //go from one activity to another in the expected way and then "go to background"
        mCountly.onStart(act);

        mCountly.onStart(act2);

        mCountly.onStop();

        mCountly.onStop();

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
    }

    @Test
    public void autoSessionFlow_1() throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, true, true, safeIDGenerator, null).setEventQueueSizeToSend(100);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        //mCountly.views().recordView("abcd");
        //TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        @NonNull Activity act2 = mock(Activity2.class);
        @NonNull Activity act3 = mock(Activity3.class);

        String viewNames[] = new String[] { act.getClass().getSimpleName(), act2.getClass().getSimpleName(), act3.getClass().getSimpleName() };
        final Map<String, Object> segm = new HashMap<>();

        //go from one activity to another in the expected way and then "go to background"
        ///////// 1
        mCountly.onStart(act);

        // there should be the first view start
        ClearFillSegmentationViewStart(segm, viewNames[0], true);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        ///////// 2
        Thread.sleep(1000);
        mCountly.onStart(act2);
        mCountly.onStop();

        //we are transitioning to the next view
        //first the next activities 'onStart' is called
        //we would report the duration of the first view and then start the next one
        ClearFillSegmentationViewEnd(segm, viewNames[0], "1");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[1], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 1, 2);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Thread.sleep(2000);
        mCountly.onStart(act3);
        mCountly.onStop();

        ClearFillSegmentationViewEnd(segm, viewNames[1], "2");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[2], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 1, 2);
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Thread.sleep(1000);
        mCountly.onStop();

        ClearFillSegmentationViewEnd(segm, viewNames[2], "1");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 0, 1);
    }

    void ClearFillSegmentationViewStart(final Map<String, Object> segm, String viewName, boolean firstView) {
        segm.clear();
        segm.put("segment", "Android");
        if (firstView) {
            segm.put("start", "1");
        }
        segm.put("visit", "1");
        segm.put("name", viewName);
    }

    void ClearFillSegmentationViewEnd(final Map<String, Object> segm, String viewName, String duration) {
        segm.clear();
        segm.put("dur", duration);
        segm.put("segment", "Android");
        segm.put("name", viewName);
    }
}
