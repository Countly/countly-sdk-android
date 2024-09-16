package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.collections.Sets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

    String[] viewNames = { "a", "b", "c", "e", "f", "g", "h", "i" };

    int idx = 0;
    final String[] vals = TestUtils.viewIDVals;
    String base64Regex = "^[A-Za-z0-9+/]*={0,2}$";
    SafeIDGenerator safeViewIDGenerator;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
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
    public void onActivityStartedStopped() throws InterruptedException, JSONException {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("aa", "11");
        globalSegm.put("aagfg", "1133");
        globalSegm.put("1", 123);
        globalSegm.put("2", 234.0d);
        globalSegm.put("3", true);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, true, true, safeViewIDGenerator, globalSegm);
        cc.setEventQueueSizeToSend(1);
        Countly mCountly = new Countly().init(cc);
        @NonNull Activity act = mock(Activity.class);

        mCountly.moduleViews.onActivityStarted(act, 1);//activity count = 1
        Thread.sleep(1000);
        mCountly.moduleViews.onActivityStopped(0);//activity count = 0

        validateView(act.getClass().getSimpleName(), 0.0, 0, 2, true, true, TestUtils.map("aa", "11", "aagfg", "1133", "1", 123, "2", 234, "3", true), "idv1", "");
        validateView(act.getClass().getSimpleName(), 1.0, 1, 2, false, false, TestUtils.map("aa", "11", "aagfg", "1133", "1", 123, "2", 234, "3", true), "idv1", "");
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

        String[] viewNames = { "DSD", "32", "DSD" };

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
        cSegm3.put("biffg", 132_137);
        cSegm3.put("cannndy", 9_534.33d);
        cSegm3.put("calaaling", true);

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] viewNames = new String[] { "DSD", "32", "DSD" };
        final Map<String, Object> segm = new HashMap<>();

        mCountly.views().recordView(viewNames[0], cSegm1);

        ClearFillSegmentationViewStart(segm, viewNames[0], true, globalSegm);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);
        Thread.sleep(2000);

        mCountly.views().recordView(viewNames[1], cSegm2);
        ClearFillSegmentationViewEnd(segm, viewNames[0], globalSegm);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 2, segm, vals[0], 0, 2); // duration comes off sometimes

        ClearFillSegmentationViewStart(segm, viewNames[1], false, globalSegm);
        segm.put("donker", "mag");
        segm.put("big", 1337);
        segm.put("candy", 954.33d);
        segm.put("calling", false);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[1], 1, 2);
        clearInvocations(ep);

        Thread.sleep(1000);
        mCountly.views().recordView(viewNames[2], cSegm3);
        ClearFillSegmentationViewEnd(segm, viewNames[1], globalSegm);

        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 1, segm, vals[1], 0, 2);

        ClearFillSegmentationViewStart(segm, viewNames[2], false, globalSegm);
        segm.put("doddnker", "m123ag");
        segm.put("exit", "33");
        segm.put("view", "33");
        segm.put("domain", "33");
        segm.put("dur", "33");
        segm.put("biffg", 132_137);
        segm.put("cannndy", 9_534.33d);
        segm.put("calaaling", true);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[2], 1, 2);
    }

    /**
     * Validate that "addSegmentationToViewWithName" and "addSegmentationToViewWithID" does not break with wrong inputs
     * Both functions are called with "null" values and "empty string"
     * The SDK should not throw null pointer exceptions
     */
    @Test
    public void addSegmentationToRunningView() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        Countly mCountly = new Countly().init(cc);

        mCountly.views().addSegmentationToViewWithName(null, null);
        mCountly.views().addSegmentationToViewWithID(null, null);

        mCountly.views().addSegmentationToViewWithName("", null);
        mCountly.views().addSegmentationToViewWithID("", null);

        mCountly.views().startView("a");

        mCountly.views().addSegmentationToViewWithName(null, null);
        mCountly.views().addSegmentationToViewWithID(null, null);

        mCountly.views().addSegmentationToViewWithName("", null);
        mCountly.views().addSegmentationToViewWithID("", null);
    }

    /**
     * Test if autoStoppedView segmentation is updated correctly
     */
    @Test
    public void addSegmentationToView() throws InterruptedException {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("aa", "11");
        globalSegm.put("aagfg", "1133");

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        Countly mCountly = new Countly().init(cc.setLoggingEnabled(true));
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        Map<String, Object> segm = new HashMap<>();

        //set segmentation before starting to record the view
        segm.put("bb", "22");
        mCountly.views().addSegmentationToViewWithName(viewNames[0], segm);

        // Start autoStoppedView
        String viewID = mCountly.views().startAutoStoppedView(viewNames[0]);

        //make sure the first view event is recorded correctly
        ClearFillSegmentationViewStart(segm, viewNames[0], true, globalSegm);
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, segm, vals[0], 0, 1);
        clearInvocations(ep);

        // add segmentation to it by name
        segm = new HashMap<>();
        segm.put("cc1", "12");
        segm.put("cc2", "as");
        mCountly.views().addSegmentationToViewWithName(viewNames[0], segm);

        segm = new HashMap<>();
        segm.put("cc2", "as_new");//make sure we can override the previous value
        segm.put("dd", "qq");
        mCountly.views().addSegmentationToViewWithID(viewID, segm);

        Thread.sleep(2000);
        mCountly.views().stopViewWithName(viewNames[0]);

        //make sure the second view event is correctly recorded
        ClearFillSegmentationViewEnd(segm, viewNames[0], globalSegm);
        segm.put("cc1", "12");
        segm.put("cc2", "as_new");
        segm.put("dd", "qq");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY, 2, segm, vals[0], 0, 1); // duration comes off sometimes
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
     * This is explicitly with the manual onStart, onStop callbacks
     */
    @Test
    public void recordViewWithActivitiesAfterwardsAutoDisabled() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        //disable application class so the manual callbacks work
        cc.setApplication(null);
        cc.setContext(TestUtils.getContext());
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        //record a view manually and validate the it is recorded
        mCountly.views().startView("abcd");
        TestUtils.validateRecordEventInternalMock(ep, ModuleViews.VIEW_EVENT_KEY);
        mCountly.views().stopViewWithName("abcd");
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
    public void autoSessionFlow_1() throws InterruptedException, JSONException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, true, true, safeViewIDGenerator, null);
        cc.setEventQueueSizeToSend(1);
        Countly mCountly = new Countly().init(cc);

        @NonNull Activity act = mock(Activity.class);
        @NonNull Activity act2 = mock(TestUtils.Activity2.class);
        @NonNull Activity act3 = mock(TestUtils.Activity3.class);

        String[] viewNames = { act.getClass().getSimpleName(), act2.getClass().getSimpleName(), act3.getClass().getSimpleName() };
        final Map<String, Object> segm = new HashMap<>();

        TestUtils.getCountyStore().clear();
        TestUtils.assertRQSize(0);
        //go from one activity to another in the expected way and then "go to background"
        ///////// 1
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, "", "");
        mCountly.onStartInternal(act);
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[0], "");

        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        // there should be the first view start
        validateView(viewNames[0], 0.0, 1, 2, true, true, null, "idv1", "");

        ///////// 2
        Thread.sleep(1000);
        mCountly.onStartInternal(act2);
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[1], vals[0]);

        validateView(viewNames[0], 1.0, 2, 4, false, false, null, "idv1", "");// validate stop event of act1
        validateView(viewNames[1], 0.0, 3, 4, false, true, null, "idv2", "idv1"); // validate start event of act2

        mCountly.onStopInternal(); // it does not end the view with this call but will end it on next start
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[1], vals[0]);
        //WARN - Possible error for next view starting, this view will not end

        Thread.sleep(2000);
        mCountly.onStartInternal(act3);
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[2], vals[1]);

        validateView(viewNames[1], 2.0, 4, 6, false, false, null, "idv2", "idv1");// validate stop event of act2
        validateView(viewNames[2], 0.0, 5, 6, false, true, null, "idv3", "idv2"); // validate start event of act3

        mCountly.onStopInternal(); // onStop call will not end previous view
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[2], vals[1]);

        Thread.sleep(1000);
        mCountly.onStopInternal(); // but this will end
        TestUtils.verifyCurrentPreviousViewID(mCountly.moduleViews, vals[2], vals[1]);

        ModuleSessionsTests.validateSessionEndRequest(6, 4, TestUtils.commonDeviceId);
        validateView(viewNames[2], 1.0, 7, 8, false, false, null, "idv3", "idv2");// validate stop event of act3

        Assert.assertEquals(8, TestUtils.getCurrentRQ("events").length);
    }

    void ClearFillSegmentationViewStart(final Map<String, Object> segm, String viewName, boolean firstView) {
        ClearFillSegmentationViewStart(segm, viewName, firstView, null);
    }

    void ClearFillSegmentationViewStart(final Map<String, Object> segm, String viewName, boolean firstView, final Map<String, Object> globalSegmentation) {
        segm.clear();

        if (globalSegmentation != null) {
            segm.putAll(globalSegmentation);
        }

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
        ClearFillSegmentationViewStart(segm, viewName, firstView, globalSegm);

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
        globalSegm.put("long", Long.MAX_VALUE);

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

    public void clearFirstViewFlagSessionEndBase(boolean manualSessions) throws InterruptedException, JSONException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        cc.setEventQueueSizeToSend(1);
        if (manualSessions) {
            cc.enableManualSessionControl();
        }
        Countly mCountly = new Countly().init(cc);
        TestUtils.assertRQSize(0);

        mCountly.views().startView("a", null);
        validateView("a", 0.0, 0, 1, true, true, null, vals[0], "");

        if (manualSessions) {
            mCountly.sessions().beginSession();
        } else {
            mCountly.onStartInternal(mock(TestUtils.Activity2.class));
        }

        ModuleSessionsTests.validateSessionBeginRequest(1, TestUtils.commonDeviceId);

        mCountly.views().startView("b", null);
        validateView("b", 0.0, 2, 3, false, true, null, vals[1], vals[0]);
        Thread.sleep(1000);

        int lastViewIdx = 4;
        if (manualSessions) {
            mCountly.sessions().endSession();
        } else {
            mCountly.onStopInternal();
            lastViewIdx = 6;
            //in this situation we would stop all views
            validateView("b", 1.0, 4, 6, false, false, null, vals[1], vals[0]);
            validateView("a", 1.0, 5, 6, false, false, null, vals[0], vals[0]);
        }

        ModuleSessionsTests.validateSessionEndRequest(3, 1, TestUtils.commonDeviceId);
        mCountly.views().startView("c", null);
        validateView("c", 0.0, lastViewIdx, lastViewIdx + 1, true, true, null, vals[2], vals[1]);
    }

    @Test
    public void clearFirstViewFlagSessionEndManual() throws InterruptedException, JSONException {
        clearFirstViewFlagSessionEndBase(true);
    }

    @Test
    public void clearFirstViewFlagSessionEndAutomatic() throws InterruptedException, JSONException {
        clearFirstViewFlagSessionEndBase(false);
    }

    @Test
    public void clearFirstViewFlagSessionConsentRemoved() throws JSONException {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, null);
        cc.setRequiresConsent(true);
        cc.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.views });
        cc.setEventQueueSizeToSend(1);
        Countly mCountly = new Countly().init(cc);

        mCountly.views().startView("a", null);

        // 0 is consent request
        ModuleConsentTests.validateConsentRequest(TestUtils.commonDeviceId, 0, new boolean[] { false, false, false, false, false, false, false, false, false, false, false, false, false, true, false });
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 1);
        validateView("a", 0.0, 2, 3, true, true, null, vals[0], "");

        //nothing should happen when session consent is given
        mCountly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.sessions });
        mCountly.views().startView("b", null);

        ModuleConsentTests.validateConsentRequest(TestUtils.commonDeviceId, 3, new boolean[] { true, false, false, false, false, false, false, false, false, false, false, false, false, true, false });
        validateView("b", 0.0, 4, 5, false, true, null, vals[1], vals[0]);

        //internal flag should be reset whens session consent is removed
        mCountly.consent().removeConsent(new String[] { Countly.CountlyFeatureNames.sessions });
        ModuleConsentTests.validateConsentRequest(TestUtils.commonDeviceId, 5, new boolean[] { false, false, false, false, false, false, false, false, false, false, false, false, false, true, false });

        mCountly.views().startView("c", null);
        validateView("c", 0.0, 6, 7, true, true, null, vals[2], vals[1]);
    }

    /**
     * Validate that max segmentation values clips the last two values of the
     * global segmentation
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimits_setGlobalSegmentation_maxSegmentationValues() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxSegmentationValues(2);
        config.setEventQueueSizeToSend(1);
        config.setGlobalViewSegmentation(TestUtils.map("a", 1, "b", 2, "c", 3, "d", 4, "e", 5));

        Countly countly = new Countly().init(config);
        countly.views().startView("a");
        Map<String, Object> viewStartSegm = TestUtils.map();
        ClearFillSegmentationViewStart(viewStartSegm, "a", true, TestUtils.map("d", 4, "e", 5));
        ModuleEventsTests.validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, viewStartSegm, 1, 0.0d, 0.0d, 0);
    }

    /**
     * Validate that max segmentation values clips the last two values of the
     * global segmentation
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimits_startEvent_maxSegmentationValues() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxSegmentationValues(2);
        config.setEventQueueSizeToSend(1);

        Countly countly = new Countly().init(config);
        countly.views().startView("a", TestUtils.map("d", 4, "e", 5, "f", 6));
        Map<String, Object> viewStartSegm = TestUtils.map();
        ClearFillSegmentationViewStart(viewStartSegm, "a", true, TestUtils.map("f", 6, "e", 5));
        ModuleEventsTests.validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, viewStartSegm, 1, 0.0d, 0.0d, 0);
    }

    /**
     * Validate that max segmentation values clips the last two values of the
     * global segmentation
     * Also validate that the global segmentation is updated correctly
     * when the view is stopped
     * "setGlobalViewSegmentation" call from the views interface is used
     *
     * @throws JSONException if JSON parsing fails
     */
    @Test
    public void internalLimits_setGlobalSegmentation_maxSegmentationValues_interface() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxSegmentationValues(2);
        config.setEventQueueSizeToSend(1);

        Countly countly = new Countly().init(config);
        countly.views().startView("a");
        Map<String, Object> viewStartSegm = TestUtils.map();
        ClearFillSegmentationViewStart(viewStartSegm, "a", true);
        ModuleEventsTests.validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, viewStartSegm, 1, 0.0d, 0.0d, 0);

        countly.views().setGlobalViewSegmentation(TestUtils.map("a", 1, "b", 2, "c", 3, "d", 4, "e", 5));
        countly.views().stopViewWithName("a");
        Map<String, Object> viewEndSegm = TestUtils.map();
        ClearFillSegmentationViewEnd(viewEndSegm, "a", TestUtils.map("d", 4, "e", 5));
        ModuleEventsTests.validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, viewEndSegm, 1, 0.0d, 0.0d, 1);
    }
    //test for sessions when consent removed

    /**
     * global seg : avu=4, avi=v1 after truncation: av=v1
     * Test the truncation of view name and segmentation keys
     * key length: 2
     * Global view segmentation values will be truncated and merged to one because they have same start
     * View name also will be truncated to expected name "VI"
     * On stop view, global segmentation will not be overridden by the given segmentation after truncation
     * on stop seg: satalite=hoho, avu=25 after truncation: sa=hoho, av=v1
     * None of the countly view segmentation keys will be truncated nor overridden
     */
    @Test
    public void internalLimit_recordViewsWithSegmentation() throws JSONException {
        Map<String, Object> globalSegm = new ConcurrentHashMap<>();
        globalSegm.put("avu", 4);
        globalSegm.put("avi", "v1");

        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        cc.sdkInternalLimits.setMaxKeyLength(2);
        cc.setEventQueueSizeToSend(1);
        Countly mCountly = new Countly().init(cc);

        Map<String, Object> givenStartSegm = new ConcurrentHashMap<>();
        givenStartSegm.put("sop", 4);
        String viewID = mCountly.views().startView("VIEW", givenStartSegm);

        validateView("VI", 0.0, 0, 1, true, true, TestUtils.map("av", "v1", "so", 4), "idv1", "");

        mCountly.views().setGlobalViewSegmentation(TestUtils.map("sunburn", true, "sunflower", "huh"));

        Map<String, Object> endSegm = new ConcurrentHashMap<>();
        endSegm.put("satellite", "hoho");
        endSegm.put("avu", 25);
        mCountly.views().stopViewWithID(viewID, endSegm);
        validateView("VI", 0.0, 1, 2, false, false, TestUtils.map("av", 25, "sa", "hoho", "su", "huh"), "idv1", "");
    }

    /**
     * global seg : avu=4, avi=v1 after truncation: av=v1
     * Test the truncation of view name and segmentation keys and values
     * key length: 2
     * value size: 2
     * segment values: 4
     * Global view segmentation values will be truncated and merged to one because they have same start
     * View name also will be truncated to expected name "VI"
     * Because key-value deletion is not exact for the max segmentation values, expected segmentation for view start
     * taken from the first run of the test and it is "yo"="wo", "so"="ma", "av"="v1", "i_"="i_"
     * In here global segmentation values are not gone but in the end view global view segmentation values are gone due to
     * the max segmentation values
     */
    @Test
    public void internalLimit_recordViewsWithSegmentation_maxValueSize() throws JSONException {
        Map<String, Object> globalSegm = new HashMap<>();
        globalSegm.put("avu", 4);
        globalSegm.put("avi", "v1");

        CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, safeViewIDGenerator, globalSegm);
        cc.sdkInternalLimits.setMaxKeyLength(2).setMaxValueSize(2).setMaxSegmentationValues(4);
        cc.setEventQueueSizeToSend(1);
        Countly mCountly = new Countly().init(cc);

        Map<String, Object> givenStartSegm = new HashMap<>();
        givenStartSegm.put("sop", 4);
        givenStartSegm.put("sophie", "macaroni");
        givenStartSegm.put("dont", "give_up");
        givenStartSegm.put("i_wish", "i_could");
        givenStartSegm.put("you", "would");
        String viewID = mCountly.views().startView("VIEW", givenStartSegm);

        validateView("VI", 0.0, 0, 1, true, true, TestUtils.map("yo", "wo", "so", "ma", "av", "v1", "i_", "i_"), "idv1", "");

        mCountly.views().setGlobalViewSegmentation(TestUtils.map("go", 45, "gone", 567.78f));

        Map<String, Object> endSegm = new HashMap<>();
        endSegm.put("satellite", "hoho");
        endSegm.put("avu", 25);
        endSegm.put("hara", true);
        endSegm.put("happy_life", false);
        endSegm.put("nope", 123);
        mCountly.views().stopViewWithID(viewID, endSegm);

        validateView("VI", 0.0, 1, 2, false, false, TestUtils.map("av", 25, "no", 123, "sa", "ho", "ha", true), "idv1", "");
    }

    /**
     * "startView" with Array segmentations
     * Validate that all primitive types arrays are successfully recorded
     * And validate that Object arrays are not recorded
     * But Generic type of Object array which its values are only primitive types are recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void startView_validateSupportedArrays() throws JSONException {
        int[] arr = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        boolean[] arrB = { true, false, true, false, true, false, true, false, true, false };
        String[] arrS = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };
        long[] arrL = { Long.MAX_VALUE, Long.MIN_VALUE };
        double[] arrD = { Double.MAX_VALUE, Double.MIN_VALUE };
        Long[] arrLO = { Long.MAX_VALUE, Long.MIN_VALUE };
        Double[] arrDO = { Double.MAX_VALUE, Double.MIN_VALUE };
        Boolean[] arrBO = { Boolean.TRUE, Boolean.FALSE };
        Integer[] arrIO = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        Object[] arrObj = { "1", 1, 1.1d, true, 1.1f, Long.MAX_VALUE };
        Object[] arrObjStr = { "1", "1", "1.1d", "true", "1.1f", "Long.MAX_VALUE" };

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrLO", arrLO,
            "arrDO", arrDO,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj,
            "arrObjStr", arrObjStr
        );

        countly.views().startView("test", segmentation);

        Map<String, Object> expectedSegmentation = TestUtils.map();
        ClearFillSegmentationViewStart(expectedSegmentation, "test", true);
        expectedSegmentation.putAll(TestUtils.map(
            "arr", new JSONArray(arr),
            "arrB", new JSONArray(arrB),
            "arrS", new JSONArray(arrS),
            "arrL", new JSONArray(arrL),
            "arrD", new JSONArray(arrD),
            "arrLO", new JSONArray(arrLO),
            "arrDO", new JSONArray(arrDO),
            "arrBO", new JSONArray(arrBO),
            "arrIO", new JSONArray(arrIO)
        ));

        ModuleEventsTests.validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, expectedSegmentation, 0);
    }

    /**
     * "startView" with List segmentations
     * Validate that all primitive types Lists are successfully recorded
     * And validate that List of Objects is not recorded
     * But Generic type of Object list which its values are only primitive types are recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void startView_validateSupportedLists() throws JSONException {
        List<Integer> arr = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Boolean> arrB = Arrays.asList(true, false, true, false, true, false, true, false, true, false);
        List<String> arrS = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        List<Long> arrLO = Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE);
        List<Double> arrDO = Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE);
        List<Boolean> arrBO = Arrays.asList(Boolean.TRUE, Boolean.FALSE);
        List<Integer> arrIO = Arrays.asList(Integer.MAX_VALUE, Integer.MIN_VALUE);
        List<Object> arrObj = Arrays.asList("1", 1, 1.1d, true, Long.MAX_VALUE);
        List<Object> arrObjStr = Arrays.asList("1", "1", "1.1d", "true", "Long.MAX_VALUE");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        // Create segmentation using maps with lists
        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrLO", arrLO,
            "arrDO", arrDO,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj,
            "arrObjStr", arrObjStr
        );

        countly.views().startView("test", segmentation);

        Map<String, Object> expectedSegmentation = TestUtils.map();
        ClearFillSegmentationViewStart(expectedSegmentation, "test", true);

        // Prepare expected segmentation with JSONArrays
        expectedSegmentation.putAll(TestUtils.map(
            "arr", new JSONArray(arr),
            "arrB", new JSONArray(arrB),
            "arrS", new JSONArray(arrS),
            "arrLO", new JSONArray(arrLO),
            "arrDO", new JSONArray(arrDO),
            "arrBO", new JSONArray(arrBO),
            "arrIO", new JSONArray(arrIO),
            "arrObjStr", new JSONArray(arrObjStr),
            "arrObj", new JSONArray(arrObj)
        ));

        // Validate the recorded event with expected segmentation
        ModuleEventsTests.validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, expectedSegmentation, 0);
    }

    /**
     * "startView" with JSONArray segmentations
     * Validate that all primitive types JSONArrays are successfully recorded
     * And validate and JSONArray of Objects is not recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void startView_validateSupportedJSONArrays() throws JSONException {
        JSONArray arr = new JSONArray(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        JSONArray arrB = new JSONArray(Arrays.asList(true, false, true, false, true, false, true, false, true, false));
        JSONArray arrS = new JSONArray(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        JSONArray arrL = new JSONArray(Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE));
        JSONArray arrD = new JSONArray(Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE));
        JSONArray arrBO = new JSONArray(Arrays.asList(Boolean.TRUE, Boolean.FALSE));
        JSONArray arrIO = new JSONArray(Arrays.asList(Integer.MAX_VALUE, Integer.MIN_VALUE));
        JSONArray arrObj = new JSONArray(Arrays.asList("1", 1, 1.1d, true, Long.MAX_VALUE));

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        // Create segmentation using maps with lists
        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj
        );

        countly.views().startView("test", segmentation);

        Map<String, Object> expectedSegmentation = TestUtils.map();
        ClearFillSegmentationViewStart(expectedSegmentation, "test", true);

        // Prepare expected segmentation with JSONArrays
        expectedSegmentation.putAll(TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj
        ));

        // Validate the recorded event with expected segmentation
        ModuleEventsTests.validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, expectedSegmentation, 0);
    }

    /**
     * "startView" with invalid data types
     * Validate that unsupported data types are not recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void startView_unsupportedDataTypesSegmentation() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createScenarioEventIDConfig(TestUtils.incrementalViewIdGenerator(), TestUtils.incrementalEventIdGenerator());
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "a", TestUtils.map(),
            "b", TestUtils.json(),
            "c", new Object(),
            "d", Sets.newSet(),
            "e", Mockito.mock(ModuleLog.class)
        );

        countly.views().startView("test", segmentation);

        Map<String, Object> expectedSegmentation = TestUtils.map();
        ClearFillSegmentationViewStart(expectedSegmentation, "test", true);

        validateView("test", 0.0, 0, 1, true, false, expectedSegmentation, "idv1", "");
    }

    /**
     * "startView" with bg/fg switch case
     * - Validate that after an auto stopped view is started and app gone to background
     * running view should stop and send a stop view request
     * - After coming from the background to foreground the stopped view should start again
     * - When we stop the view it should be removed from the cached views
     *
     * @throws InterruptedException if the thread is interrupted
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void startView_restartAfterActivityComesFromForeground() throws InterruptedException, JSONException {
        CountlyConfig countlyConfig = TestUtils.createScenarioEventIDConfig(TestUtils.incrementalViewIdGenerator(), TestUtils.incrementalEventIdGenerator());
        countlyConfig.setApplication(null);
        countlyConfig.setContext(TestUtils.getContext());
        countlyConfig.setGlobalViewSegmentation(TestUtils.map("try", "this", "maybe", false));

        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "a", "a",
            "b", 55,
            "c", new JSONArray(Arrays.asList("aa", "asd", "ad8")),
            "d", true
        );

        Activity activity = mock(Activity.class);

        TestUtils.assertRQSize(0);

        countly.onStart(activity);
        countly.views().startView("test", segmentation);

        segmentation.put("try", "this");
        segmentation.put("maybe", false);

        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        ModuleEventsTests.validateEventInRQ(TestUtils.commonDeviceId, ModuleViews.ORIENTATION_EVENT_KEY, null, 1, 0.0, 0.0, "ide1", "_CLY_", "", "_CLY_", 1, 3, 0, 1);
        validateView("test", 0.0, 2, 3, true, true, segmentation, "idv1", "");

        Thread.sleep(1000);

        countly.onStop();
        ModuleSessionsTests.validateSessionEndRequest(3, 1, TestUtils.commonDeviceId);
        validateView("test", 1.0, 4, 5, false, false, TestUtils.map("try", "this", "maybe", false), "idv1", "");

        countly.onStart(activity);

        ModuleSessionsTests.validateSessionBeginRequest(5, TestUtils.commonDeviceId);
        ModuleEventsTests.validateEventInRQ(TestUtils.commonDeviceId, ModuleViews.ORIENTATION_EVENT_KEY, null, 1, 0.0, 0.0, "ide2", "_CLY_", "idv1", "_CLY_", 6, 8, 0, 1);
        validateView("test", 0.0, 7, 8, true, true, TestUtils.map("try", "this", "maybe", false), "idv2", "idv1");

        Thread.sleep(1000);

        countly.views().stopViewWithName("test");
        validateView("test", 1.0, 8, 9, false, false, TestUtils.map("try", "this", "maybe", false), "idv2", "idv1");
        countly.onStop();

        ModuleSessionsTests.validateSessionEndRequest(9, 1, TestUtils.commonDeviceId);
        TestUtils.assertRQSize(10);
    }

    /**
     * "startView" with bg/fg switch case
     * - Validate that after an auto stopped view is started and app gone to background
     * running view should stop and send a stop view request
     * - After coming from the background to foreground the stopped view should start again,
     * and it should contain the previous segmentation
     * - When we stop the view it should be removed from the cached views
     *
     * @throws InterruptedException if the thread is interrupted
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void startView_restartAfterActivityComesFromForeground_copyPreviousSegmentation() throws InterruptedException, JSONException {
        CountlyConfig countlyConfig = TestUtils.createScenarioEventIDConfig(TestUtils.incrementalViewIdGenerator(), TestUtils.incrementalEventIdGenerator());
        countlyConfig.setApplication(null);
        countlyConfig.setContext(TestUtils.getContext());
        countlyConfig.setGlobalViewSegmentation(TestUtils.map("try", "this", "maybe", false));

        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "a", "a",
            "b", 55,
            "c", new JSONArray(Arrays.asList("aa", "asd", "ad8")),
            "d", true
        );

        Activity activity = mock(Activity.class);

        TestUtils.assertRQSize(0);

        countly.onStart(activity);
        countly.views().startView("test", segmentation);

        countly.views().addSegmentationToViewWithName("test", TestUtils.map("yama", "de", "kuda", "sai"));

        segmentation.put("try", "this");
        segmentation.put("maybe", false);

        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        ModuleEventsTests.validateEventInRQ(TestUtils.commonDeviceId, ModuleViews.ORIENTATION_EVENT_KEY, null, 1, 0.0, 0.0, "ide1", "_CLY_", "", "_CLY_", 1, 3, 0, 1);
        validateView("test", 0.0, 2, 3, true, true, segmentation, "idv1", "");

        Thread.sleep(1000);

        countly.onStop();
        ModuleSessionsTests.validateSessionEndRequest(3, 1, TestUtils.commonDeviceId);
        validateView("test", 1.0, 4, 5, false, false, TestUtils.map("try", "this", "maybe", false, "yama", "de", "kuda", "sai"), "idv1", "");

        countly.onStart(activity);

        ModuleSessionsTests.validateSessionBeginRequest(5, TestUtils.commonDeviceId);
        ModuleEventsTests.validateEventInRQ(TestUtils.commonDeviceId, ModuleViews.ORIENTATION_EVENT_KEY, null, 1, 0.0, 0.0, "ide2", "_CLY_", "idv1", "_CLY_", 6, 8, 0, 1);
        validateView("test", 0.0, 7, 8, true, true, TestUtils.map("try", "this", "maybe", false, "yama", "de", "kuda", "sai"), "idv2", "idv1");
        TestUtils.assertRQSize(8);
    }

    /**
     * "startView" with enabled view name recording
     * Validate that the previous view name is recorded when a new view is started
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordView_previousViewName() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.experimental.enablePreviousNameRecording();
        countlyConfig.setEventQueueSizeToSend(1);

        Countly countly = new Countly().init(countlyConfig);

        countly.views().startView("test");
        validateView("test", 0.0, 0, 1, true, true, TestUtils.map(), "_CLY_", "_CLY_", "");

        countly.views().startView("test2");
        validateView("test2", 0.0, 1, 2, false, true, TestUtils.map(), "_CLY_", "_CLY_", "test");
    }

    static void validateView(String viewName, Double viewDuration, int idx, int size, boolean start, boolean visit, Map<String, Object> customSegmentation, String id, String pvid) throws JSONException {
        validateView(viewName, viewDuration, idx, size, start, visit, customSegmentation, id, pvid, null);
    }

    static void validateView(String viewName, Double viewDuration, int idx, int size, boolean start, boolean visit, Map<String, Object> customSegmentation, String id, String pvid, String pvn) throws JSONException {
        Map<String, Object> viewSegmentation = TestUtils.map("name", viewName, "segment", "Android");
        if (start) {
            viewSegmentation.put("start", "1");
        }
        if (visit) {
            viewSegmentation.put("visit", "1");
        }
        if (customSegmentation != null) {
            viewSegmentation.putAll(customSegmentation);
        }

        if (pvn != null) {
            viewSegmentation.put("cly_pvn", pvn);
        }

        ModuleEventsTests.validateEventInRQ(TestUtils.commonDeviceId, ModuleViews.VIEW_EVENT_KEY, viewSegmentation, 1, 0.0, viewDuration, id, pvid, "_CLY_", "_CLY_", idx, size, 0, 1);
    }

    //todo extract orientation tests
}
