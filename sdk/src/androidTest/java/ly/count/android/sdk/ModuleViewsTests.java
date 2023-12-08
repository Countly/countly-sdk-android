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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
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

    String[] viewNames = new String[] { "a", "b", "c", "e", "f", "g", "h", "i" };

    int idx = 0;
    final String[] vals = TestUtils.viewIDVals;
    String base64Regex = "^[A-Za-z0-9+/]*={0,2}$";
    SafeIDGenerator safeViewIDGenerator;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
        idx = 0;//reset the index for the view ID generator
        safeViewIDGenerator = new SafeIDGenerator() {
            @NonNull @Override public String GenerateValue() {
                return vals[idx++];
            }
        };
    }

    @After
    public void tearDown() {
    }

    /**
     * Make sure the random value generator matches the required pattern
     */
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

    /**
     * Make sure that long names for Activities are recorded when required
     */
    @Test
    public void onActivityStartedViewTrackingLongNames() {
        activityStartedViewTrackingBase(false);
    }

    /**
     * Make sure that short names for Activities are recorded when required
     */
    @Test
    public void onActivityStartedViewTrackingShortNames() {
        activityStartedViewTrackingBase(true);
    }

    void activityStartedViewTrackingBase(boolean shortNames) {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, shortNames, true, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        mCountly.moduleViews.onActivityStarted(act, 1);

        final Map<String, Object> segm = new HashMap<>();
        if (shortNames) {
            ClearFillSegmentationViewStart(segm, act.getClass().getSimpleName(), true);
        } else {
            ClearFillSegmentationViewStart(segm, act.getClass().getName(), true);
        }

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
    }

    /**
     * Make sure that the Activity view exception works with the long view name mode
     */
    @Test
    public void onActivityStartedViewTrackingLongNamesException() {
        activityStartedViewTrackingExceptionBase(false);
    }

    /**
     * Make sure that the Activity view exception works with the short view name mode
     */
    @Test
    public void onActivityStartedViewTrackingShortNamesException() {
        activityStartedViewTrackingExceptionBase(true);
    }

    void activityStartedViewTrackingExceptionBase(boolean shortNames) {
        @NonNull Activity act1 = mock(Activity.class);
        @NonNull Activity act2 = mock(TestUtils.Activity2.class);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, shortNames, true, safeViewIDGenerator, null).setAutoTrackingExceptions(new Class[] { act1.getClass() });
        Countly mCountly = new Countly().init(cc);

        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.moduleViews.onActivityStarted(act1, 1);

        verify(ep, never()).recordEventInternal(anyString(), any(Map.class), anyInt(), anyDouble(), anyDouble(), any(UtilsTime.Instant.class), any(String.class));

        mCountly.moduleViews.onActivityStarted(act2, 2);

        final Map<String, Object> segm = new HashMap<>();
        if (shortNames) {
            ClearFillSegmentationViewStart(segm, act2.getClass().getSimpleName(), true);
        } else {
            ClearFillSegmentationViewStart(segm, act2.getClass().getName(), true);
        }

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
    }

    /**
     * Make sure that no orientation events are recorded when the feature is not enabled
     */
    @Test
    public void onActivityStartedDisabledOrientationView() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        mCountly.moduleViews.onActivityStarted(act, 1);

        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(UtilsTime.Instant.class), any(String.class));
    }

    /**
     * Validate that the orientation event is recorded correctly when the activity starts
     */
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

        mCountly.moduleViews.onActivityStarted(act, 1);

        final Map<String, Object> segm = new HashMap<>();
        segm.put("mode", "portrait");

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.ORIENTATION_EVENT_KEY, segm, null, 0, 1);

        Assert.assertEquals(Configuration.ORIENTATION_PORTRAIT, mView.currentOrientation);
    }

    /**
     * Validate that no orientation event is recorded with the "onConfigurationChanged" call when orientation tracking is not enabled
     */
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

    /**
     * Validate that the correct orientation event is recorded with the "onConfigurationChanged" call when orientation tracking is enabled
     */
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
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("aa", "11");
        globalSegm.put("aagfg", "1133");
        globalSegm.put("1", 123);
        globalSegm.put("2", 234.0d);
        globalSegm.put("3", true);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, true, true, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc);

        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);

        int start = UtilsTime.currentTimestampSeconds();
        mCountly.moduleViews.onActivityStarted(act, 1);//activity count = 1
        Thread.sleep(100);
        mCountly.moduleViews.onActivityStopped(0);//activity count = 0
        double viewDuration = UtilsTime.currentTimestampSeconds() - start;

        final Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, act.getClass().getSimpleName(), true);
        segm.put("aa", "11");
        segm.put("aagfg", "1133");
        segm.put("1", 123);
        segm.put("2", 234.0d);
        segm.put("3", true);
        segm.putAll(globalSegm);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 2);

        ClearFillSegmentationViewEnd(segm, act.getClass().getSimpleName(), globalSegm);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, viewDuration, segm, vals[0], 1, 2);
    }

    /**
     * Validate pure "recordView" flow without using segmentation
     *
     * @throws InterruptedException
     */
    @Test
    public void recordViewNoSegm() throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(true, true, false, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };

        final Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, viewNames[0], true);

        mCountly.views().recordView(viewNames[0]);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);
        Thread.sleep(1000);

        mCountly.views().recordView(viewNames[1]);
        ClearFillSegmentationViewEnd(segm, viewNames[0], null);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[0], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[1], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 1, 2);
        clearInvocations(ep);

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2]);
        ClearFillSegmentationViewEnd(segm, viewNames[1], null);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[1], 0, 2);//todo this test has issues sometimes

        ClearFillSegmentationViewStart(segm, viewNames[2], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 1, 2);
    }

    /**
     * Validate pure "recordView" flow with using segmentation
     *
     * @throws InterruptedException
     */
    @Test
    public void recordViewWithSegm() throws InterruptedException {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("aa", "11");
        globalSegm.put("aagfg", "1133");
        globalSegm.put("1", 123);
        globalSegm.put("2", 234.0d);
        globalSegm.put("3", true);

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

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };
        final Map<String, Object> segm = new HashMap<>();

        mCountly.views().recordView(viewNames[0], cSegm1);

        ClearFillSegmentationViewStart(segm, viewNames[0], true);
        segm.putAll(globalSegm);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);
        Thread.sleep(2000);

        mCountly.views().recordView(viewNames[1], cSegm2);
        ClearFillSegmentationViewEnd(segm, viewNames[0], globalSegm);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 2, segm, vals[0], 0, 2); // duration comes off sometimes

        ClearFillSegmentationViewStart(segm, viewNames[1], false);
        segm.put("donker", "mag");
        segm.put("big", 1337);
        segm.put("candy", 954.33d);
        segm.put("calling", false);
        segm.putAll(globalSegm);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 1, 2);
        clearInvocations(ep);

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2], cSegm3);
        ClearFillSegmentationViewEnd(segm, viewNames[1], globalSegm);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[1], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[2], false);
        segm.put("doddnker", "m123ag");
        segm.put("exit", "33");
        segm.put("view", "33");
        segm.put("domain", "33");
        segm.put("dur", "33");
        segm.put("biffg", 132137);
        segm.put("cannndy", 9534.33d);
        segm.put("calaaling", true);
        segm.putAll(globalSegm);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 1, 2);
    }

    /**
     * Test if autoStoppedView segmentation is updated correctly
     *
     */
    @Test
    public void recordAutoStoppedViewWithSegments(){
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("aa", "11");
        globalSegm.put("aagfg", "1133");
        globalSegm.put("1", 123);
        globalSegm.put("2", 234.0d);
        globalSegm.put("3", true);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc.setLoggingEnabled(true));
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        // Start autoStoppedView
        Map<String, Object> segm = new HashMap<>();
        String viewID = mCountly.views().startAutoStoppedView("aView");

        // add segmentation to it
        segm.put("aa", "12");
        mCountly.views().addSegmentationToAutoStoppedView(viewID, segm);
        Assert.assertEquals(mCountly.views().getCurrentAutoStoppedViewSegmentation(), segm);

        // override the key
        segm.clear();
        segm.put("aa", "13");
        mCountly.views().addSegmentationToAutoStoppedView(viewID, segm);
        Assert.assertEquals(mCountly.views().getCurrentAutoStoppedViewSegmentation(), segm);

        // add a new key/value
        segm.clear();
        segm.put("bb", "11");
        mCountly.views().addSegmentationToAutoStoppedView(viewID, segm);
        // add null segmentation
        mCountly.views().addSegmentationToAutoStoppedView(viewID, null);
        segm.put("aa", "13");
        Assert.assertEquals(mCountly.views().getCurrentAutoStoppedViewSegmentation(), segm);

        // stop the view and check if segmentation was reset
        mCountly.views().stopAllViews(globalSegm);
        segm.clear();
        Assert.assertEquals(mCountly.views().getCurrentAutoStoppedViewSegmentation(), segm);
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

        Map<String, Object> segm = new HashMap<>();
        segm.put("xxx", "33");
        segm.put("rtt", 2);
        mCountly.views().startView("aa");
        mCountly.views().startView("aa", segm);

        mCountly.views().stopViewWithName("aa");
        mCountly.views().stopViewWithName("aa", segm);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
    }

    /**
     * Automatic view tracking is not enabled.
     * Changing activities should not record view events
     */
    @Test
    public void noViewRecordedWithAutomaticTurnedOffActChange() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        Activity act2 = mock(TestUtils.Activity2.class);

        //go from one activity to another in the expected way and then "go to background"
        mCountly.onStart(act);
        mCountly.onStart(act2);
        mCountly.onStop();
        mCountly.onStop();

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
    }

    /**
     * Make sure automatic session related calls don't do anything if automatic view tracking is disabled
     */
    @Test
    public void recordViewWithActivitiesAfterwardsAutoDisabled() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().recordView("abcd");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY);
        clearInvocations(ep);

        @NonNull Activity act = mock(Activity.class);
        @NonNull Activity act2 = mock(TestUtils.Activity2.class);

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

    /**
     * Validating that automatic session flow works as expected
     *
     * @throws InterruptedException
     */
    @Test
    public void autoSessionFlow_1() throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, true, true, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep;

        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        @NonNull Activity act = mock(Activity.class);
        @NonNull Activity act2 = mock(TestUtils.Activity2.class);
        @NonNull Activity act3 = mock(TestUtils.Activity3.class);

        String viewNames[] = new String[] { act.getClass().getSimpleName(), act2.getClass().getSimpleName(), act3.getClass().getSimpleName() };
        final Map<String, Object> segm = new HashMap<>();

        //go from one activity to another in the expected way and then "go to background"
        ///////// 1
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, "", "");
        mCountly.onStartInternal(act);
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[0], "");

        // there should be the first view start
        ClearFillSegmentationViewStart(segm, viewNames[0], true);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);

        ///////// 2
        Thread.sleep(1000);
        mCountly.onStartInternal(act2);
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[1], vals[0]);
        mCountly.onStopInternal();
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[1], vals[0]);

        //we are transitioning to the next view
        //first the next activities 'onStart' is called
        //we would report the duration of the first view and then start the next one
        ClearFillSegmentationViewEnd(segm, viewNames[0], null);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[0], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[1], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 1, 2);
        clearInvocations(ep);

        Thread.sleep(2000);
        mCountly.onStartInternal(act3);
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[2], vals[1]);
        mCountly.onStopInternal();
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[2], vals[1]);

        ClearFillSegmentationViewEnd(segm, viewNames[1], null);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 2, segm, vals[1], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[2], false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 1, 2);
        clearInvocations(ep);

        Thread.sleep(1000);
        mCountly.onStopInternal();
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[2], vals[1]);

        ClearFillSegmentationViewEnd(segm, viewNames[2], null);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[2], 0, 1);
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

    void ClearFillSegmentationViewEnd(final Map<String, Object> segm, String viewName, final Map<String, Object> globalSegmentation) {
        segm.clear();
        if (globalSegmentation != null) {
            segm.putAll(globalSegmentation);
        }
        segm.put("segment", "Android");
        segm.put("name", viewName);
    }

    /**
     * Making sure that manual view recording calls are ignored when automatic view tracking is enabled
     */
    @Test
    public void manualViewCallsBlocked_autoViewEnabled() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, true, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Map<String, Object> segm = new HashMap<>();
        segm.put("xxx", "33");
        segm.put("rtt", 2);

        mCountly.views().recordView("abcd");
        mCountly.views().startView("aa");
        mCountly.views().startView("aa", segm);
        mCountly.views().startAutoStoppedView("bb");
        mCountly.views().startAutoStoppedView("bb", segm);

        mCountly.views().stopViewWithName("aa");
        mCountly.views().stopViewWithName("aa", segm);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
    }

    /**
     * Only single view. Making sure all ways of ending a view work
     */
    @Test
    public void validatingEventCountFromViewCalls() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Map<String, Object> segm = new HashMap<>();
        segm.put("xxx", "33");
        segm.put("rtt", 2);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        mCountly.views().recordView("a");//starting a view
        TestUtils.validateRecordEventInternalMockInteractions(ep, 1);

        mCountly.views().recordView("b", segm);//starting a new view, stopping the previous one
        TestUtils.validateRecordEventInternalMockInteractions(ep, 3);

        mCountly.views().startView("c");//starting a new view, stopping the previous one
        TestUtils.validateRecordEventInternalMockInteractions(ep, 5);

        String id = mCountly.views().startView("d", segm);//starting a new view
        TestUtils.validateRecordEventInternalMockInteractions(ep, 6);

        mCountly.views().stopViewWithID(id);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 7);

        id = mCountly.views().startView("e", segm);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 8);

        mCountly.views().stopViewWithID(id, segm);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 9);

        mCountly.views().startView("f");
        TestUtils.validateRecordEventInternalMockInteractions(ep, 10);

        mCountly.views().stopViewWithName("f");
        TestUtils.validateRecordEventInternalMockInteractions(ep, 11);

        mCountly.views().startView("g");
        TestUtils.validateRecordEventInternalMockInteractions(ep, 12);

        mCountly.views().stopViewWithName("g", segm);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 13);
    }

    /**
     * Passing bad values and making sure it doesn't crash
     */
    @Test
    public void viewCallsWithBadValues() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);

        mCountly.views().startView(null);
        mCountly.views().startView("");
        mCountly.views().startView(null, null);
        mCountly.views().startView("", null);

        mCountly.views().startAutoStoppedView(null);
        mCountly.views().startAutoStoppedView("");
        mCountly.views().startAutoStoppedView(null, null);
        mCountly.views().startAutoStoppedView("", null);

        mCountly.views().resumeViewWithID(null);
        mCountly.views().resumeViewWithID("");
        mCountly.views().resumeViewWithID("xx");

        mCountly.views().pauseViewWithID(null);
        mCountly.views().pauseViewWithID("");
        mCountly.views().pauseViewWithID("zz");

        mCountly.views().stopViewWithID(null);
        mCountly.views().stopViewWithID("");
        mCountly.views().stopViewWithID("cc");

        mCountly.views().stopViewWithName(null);
        mCountly.views().stopViewWithName("");
        mCountly.views().stopViewWithName("vv");

        mCountly.views().setGlobalViewSegmentation(null);
        mCountly.views().updateGlobalViewSegmentation(null);
    }

    /**
     * Testing the start,pause,resume, stop flow
     * use ID to stop view: yes
     * start view as auto close view: no
     *
     * @throws InterruptedException
     */
    @Test
    public void performFullViewFlowStopWithId_regularStart() throws InterruptedException {
        performFullViewFlowBase(true, false);
    }

    /**
     * Testing the start,pause,resume, stop flow
     * use ID to stop view: no
     * start view as auto close view: no
     *
     * @throws InterruptedException
     */
    @Test
    public void performFullViewFlowStopWithName_regularStart() throws InterruptedException {
        performFullViewFlowBase(false, false);
    }

    /**
     * Testing the start,pause,resume, stop flow
     * use ID to stop view: yes
     * start view as auto close view: yes
     *
     * @throws InterruptedException
     */
    @Test
    public void performFullViewFlowStopWithId_autoCloseStart() throws InterruptedException {
        performFullViewFlowBase(true, true);
    }

    /**
     * Testing the start,pause,resume, stop flow
     * use ID to stop view: no
     * start view as auto close view: yes
     *
     * @throws InterruptedException
     */
    @Test
    public void performFullViewFlowStopWithName_autoCloseStart() throws InterruptedException {
        performFullViewFlowBase(false, true);
    }

    public void performFullViewFlowBase(boolean useID, boolean startAutoCloseView) throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Map<String, Object> segm = new HashMap<>();

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        String viewId;
        if (startAutoCloseView) {
            viewId = mCountly.views().startAutoStoppedView(viewNames[0]);
        } else {
            viewId = mCountly.views().startView(viewNames[0]);
        }
        Assert.assertEquals(viewId, vals[0]);

        ClearFillSegmentationViewStart(segm, viewNames[0], true);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);

        Thread.sleep(1000);

        mCountly.views().pauseViewWithID(viewId);
        validatePausedViewInFlow(viewNames[0], vals[0], null, 1, ep);

        Thread.sleep(1000);

        //double pause to make sure nothing happens
        mCountly.views().pauseViewWithID(viewId);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        mCountly.views().resumeViewWithID(viewId);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        Thread.sleep(2000);

        //double resume to make sure nothing changes
        mCountly.views().resumeViewWithID(viewId);
        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        ClearFillSegmentationViewEnd(segm, viewNames[0], null);
        if (useID) {
            mCountly.views().stopViewWithID(viewId);
        } else {
            mCountly.views().stopViewWithName(viewNames[0]);
        }
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 2, segm, vals[0], 0, 1);
    }

    /**
     * Test flow when having 3 views in paralell
     * use ID to stop views: no
     *
     * @throws InterruptedException
     */
    @Test
    public void tripleViewWithName() throws InterruptedException {
        tripleViewBase(false);
    }

    /**
     * Test flow when having 3 views in paralell
     * use ID to stop views: yes
     *
     * @throws InterruptedException
     */
    @Test
    public void trippleViewWithId() throws InterruptedException {
        tripleViewBase(true);
    }

    public void tripleViewBase(boolean useID) throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewID = new String[viewNames.length];

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        viewID[0] = startViewInFlow(viewNames[0], vals[0], null, null, true, mCountly, ep);

        Thread.sleep(1000);

        //start second view
        viewID[1] = startViewInFlow(viewNames[1], vals[1], null, null, false, mCountly, ep);

        Thread.sleep(1000);

        //start third view
        viewID[2] = startViewInFlow(viewNames[2], vals[2], null, null, false, mCountly, ep);

        Thread.sleep(1000);

        //stop second view
        stopViewInFlow(viewNames[1], viewID[1], null, null, 2, useID, mCountly, ep);

        //stop first view
        stopViewInFlow(viewNames[0], viewID[0], null, null, 3, useID, mCountly, ep);

        //stop third view
        stopViewInFlow(viewNames[2], viewID[2], null, null, 1, useID, mCountly, ep);
    }

    String startViewInFlow(String viewName, String plannedViewID, Map<String, Object> givenSegm, Map<String, Object> globalSegm, boolean firstView, Countly mCountly, EventProvider ep) {
        String returnedID;

        if (givenSegm != null) {
            returnedID = mCountly.views().startView(viewName, givenSegm);
        } else {
            returnedID = mCountly.views().startView(viewName);
        }
        Assert.assertEquals(returnedID, plannedViewID);

        Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, viewName, firstView);

        if (globalSegm != null) {
            segm.putAll(globalSegm);
        }

        if (givenSegm != null) {
            segm.putAll(givenSegm);
        }

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, plannedViewID, 0, 1);
        clearInvocations(ep);
        return returnedID;
    }

    void validatePausedViewInFlow(String viewName, String plannedViewID, Map<String, Object> globalSegm, double duration, EventProvider ep) {
        Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewEnd(segm, viewName, null);

        if (globalSegm != null) {
            segm.putAll(globalSegm);
        }

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, duration, segm, plannedViewID, 0, 1);
        clearInvocations(ep);
    }

    void stopViewInFlow(String viewName, String plannedViewID, Map<String, Object> givenSegm, Map<String, Object> globalSegm, double duration, boolean stopWithID, Countly mCountly, EventProvider ep) {
        Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewEnd(segm, viewName, null);

        if (globalSegm != null) {
            segm.putAll(globalSegm);
        }

        if (givenSegm != null) {
            segm.putAll(givenSegm);
        }

        if (stopWithID) {
            if (givenSegm != null) {
                mCountly.views().stopViewWithID(plannedViewID, givenSegm);
            } else {
                mCountly.views().stopViewWithID(plannedViewID);
            }
        } else {
            if (givenSegm != null) {
                mCountly.views().stopViewWithName(viewName, givenSegm);
            } else {
                mCountly.views().stopViewWithName(viewName);
            }
        }
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, duration, segm, plannedViewID, 0, 1);
        clearInvocations(ep);
    }

    /**
     * Stop a view that has been paused
     * Use ID to stop view: yes
     *
     * @throws InterruptedException
     */
    @Test
    public void stopPausedViewWithID() throws InterruptedException {
        stopPausedViewBase(true);
    }

    /**
     * Stop a view that has been paused
     * Use ID to stop view: no
     *
     * @throws InterruptedException
     */
    @Test
    public void stopPausedViewWithName() throws InterruptedException {
        stopPausedViewBase(false);
    }

    public void stopPausedViewBase(boolean useID) throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewID = new String[viewNames.length];

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        viewID[0] = startViewInFlow(viewNames[0], vals[0], null, null, true, mCountly, ep);

        Thread.sleep(1000);

        mCountly.views().pauseViewWithID(viewID[0]);
        validatePausedViewInFlow(viewNames[0], viewID[0], null, 1, ep);

        Thread.sleep(1000);

        stopViewInFlow(viewNames[0], viewID[0], null, null, 0, useID, mCountly, ep);
    }

    /**
     * Validate that segmentation can be provided as expected
     * Use ID to stop view: yes
     * Use the "set" and not "update" segmentation call: yes
     *
     * @throws InterruptedException
     */
    @Test
    public void recordViewsWithSegmentationWithID_setSegmentation() throws InterruptedException {
        recordViewsWithSegmentationBase(true, true);
    }

    /**
     * Validate that segmentation can be provided as expected
     * Use ID to stop view: no
     * Use the "set" and not "update" segmentation call: yes
     *
     * @throws InterruptedException
     */
    @Test
    public void recordViewsWithSegmentationWithName_setSegmentation() throws InterruptedException {
        recordViewsWithSegmentationBase(false, true);
    }

    /**
     * Validate that segmentation can be provided as expected
     * Use ID to stop view: yes
     * Use the "set" and not "update" segmentation call: no
     *
     * @throws InterruptedException
     */
    @Test
    public void recordViewsWithSegmentationWithID_updateSegmentation() throws InterruptedException {
        recordViewsWithSegmentationBase(true, false);
    }

    /**
     * Validate that segmentation can be provided as expected
     * Use ID to stop view: no
     * Use the "set" and not "update" segmentation call: no
     *
     * @throws InterruptedException
     */
    @Test
    public void recordViewsWithSegmentationWithName_updateSegmentation() throws InterruptedException {
        recordViewsWithSegmentationBase(false, false);
    }

    public void recordViewsWithSegmentationBase(boolean useID, boolean setSegmentation) throws InterruptedException {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("0", 4);
        globalSegm.put("1", "v1");

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewID = new String[viewNames.length];

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        Map<String, Object> givenStartSegm = new HashMap<>();
        givenStartSegm.put("2", "v2");
        viewID[0] = startViewInFlow(viewNames[0], vals[0], givenStartSegm, globalSegm, true, mCountly, ep);

        Thread.sleep(1000);

        mCountly.views().pauseViewWithID(viewID[0]);

        Map<String, Object> expectedPauseSegm = new HashMap<>();
        expectedPauseSegm.putAll(globalSegm);

        mockingDetails(ep).printInvocations();

        validatePausedViewInFlow(viewNames[0], viewID[0], expectedPauseSegm, 1, ep);

        Thread.sleep(1000);

        Map<String, Object> expectedEndSegm = new HashMap<>();

        if (!setSegmentation) {
            expectedEndSegm.putAll(globalSegm);
        }

        globalSegm = new HashMap<>();
        globalSegm.put("1", false);
        globalSegm.put("3", 3);

        expectedEndSegm.putAll(globalSegm);

        if (setSegmentation) {
            mCountly.views().setGlobalViewSegmentation(globalSegm);
        } else {
            mCountly.views().updateGlobalViewSegmentation(globalSegm);
        }

        Map<String, Object> givenEndSegm = new HashMap<>();
        givenEndSegm.put("4", false);

        stopViewInFlow(viewNames[0], viewID[0], givenEndSegm, expectedEndSegm, 0, useID, mCountly, ep);
    }

    /**
     * Make sure that an "auto closed view" is closed correctly when starting another view
     */
    @Test
    public void validateStartAutoClosedView() {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("0", 4);
        globalSegm.put("1", "v1");

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        mCountly.views().startView("a", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 1);

        mCountly.views().startAutoStoppedView("b", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 2);

        mCountly.views().startAutoStoppedView("c", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 4);

        mCountly.views().startView("d", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 6);
    }

    /**
     * Validating that the "stopAllViews" stops started views
     */
    @Test
    public void validateStopAllViews() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);

        mCountly.views().startView("a", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 1);

        mCountly.views().stopAllViews(null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 2);

        mCountly.views().startAutoStoppedView("b", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 3);

        mCountly.views().stopAllViews(null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 4);

        mCountly.views().startView("c", null);
        mCountly.views().startAutoStoppedView("d", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 6);

        mCountly.views().stopAllViews(null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 8);
    }

    @Test
    public void validateSegmentationPrecedence() {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("xx", 1);
        globalSegm.put("yy", 2);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Map<String, Object> viewSegm = new HashMap<>();
        viewSegm.put("yy", 3);
        viewSegm.put("zz", 4);

        mCountly.views().startView("a", viewSegm);

        Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, "a", true);
        segm.put("xx", 1);
        segm.put("yy", 3);
        segm.put("zz", 4);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);

        mCountly.views().stopViewWithID(vals[0], viewSegm);
        ClearFillSegmentationViewEnd(segm, "a", null);
        segm.put("xx", 1);
        segm.put("yy", 3);
        segm.put("zz", 4);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
    }

    @Test
    public void overridingViewProtectedSegmentation() {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("name", 999);
        globalSegm.put("visit", 999);
        globalSegm.put("start", 999);
        globalSegm.put("segment", 999);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().startView("a", globalSegm);

        Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, "a", true);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);

        mCountly.views().startView("b", globalSegm);
        ClearFillSegmentationViewStart(segm, "b", false);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 0, 1);
    }

    public void clearFirstViewFlagSessionEndBase(boolean manualSessions) throws InterruptedException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        if (manualSessions) {
            cc.enableManualSessionControl();
        }
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().startView("a", null);
        Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, "a", true);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);

        if (manualSessions) {
            mCountly.sessions().beginSession();
        } else {
            mCountly.onStartInternal(mock(TestUtils.Activity2.class));
        }

        mCountly.views().startView("b", null);
        ClearFillSegmentationViewStart(segm, "b", false);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 0, 1);
        clearInvocations(ep);

        Thread.sleep(1000);

        if (manualSessions) {
            mCountly.sessions().endSession();
        } else {
            mCountly.onStopInternal();

            //in this situation we would pause all views
            ClearFillSegmentationViewEnd(segm, "a", null);
            TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[0], 1, 2);

            ClearFillSegmentationViewEnd(segm, "b", null);
            TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[1], 0, 2);

            clearInvocations(ep);
        }

        mCountly.views().startView("c", null);
        ClearFillSegmentationViewStart(segm, "c", true);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 0, 1);
        clearInvocations(ep);
    }

    @Test
    public void clearFirstViewFlagSessionEndManual() throws InterruptedException {
        clearFirstViewFlagSessionEndBase(true);
    }

    @Test
    public void clearFirstViewFlagSessionEndAutomatic() throws InterruptedException {
        clearFirstViewFlagSessionEndBase(false);
    }

    @Test
    public void clearFirstViewFlagSessionConsentRemoved() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        cc.setRequiresConsent(true);
        cc.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.views });
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        mCountly.views().startView("a", null);
        Map<String, Object> segm = new HashMap<>();
        ClearFillSegmentationViewStart(segm, "a", true);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);

        //nothing should happen when session consent is given
        mCountly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.sessions });

        mCountly.views().startView("b", null);
        ClearFillSegmentationViewStart(segm, "b", false);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 0, 1);
        clearInvocations(ep);

        //internal flag should be reset whens session consent is removed
        mCountly.consent().removeConsent(new String[] { Countly.CountlyFeatureNames.sessions });

        mCountly.views().startView("c", null);
        ClearFillSegmentationViewStart(segm, "c", true);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 0, 1);
        clearInvocations(ep);
    }

    //test for sessions when consent removed

    //todo extract orientation tests
}
