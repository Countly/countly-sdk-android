package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class sc_MV_ManualViewTests {

    @Test
    public void MV_100_badValues_null() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);

        mCountly.views().recordView(null);
        mCountly.views().recordView(null, null);

        mCountly.views().startAutoStoppedView(null);
        mCountly.views().startAutoStoppedView(null, null);

        mCountly.views().startView(null);
        mCountly.views().startView(null, null);

        mCountly.views().resumeViewWithID(null);
        mCountly.views().pauseViewWithID(null);

        mCountly.views().stopViewWithID(null);
        mCountly.views().stopViewWithID(null, null);

        mCountly.views().stopViewWithName(null);
        mCountly.views().stopViewWithName(null, null);

        mCountly.views().addSegmentationToViewWithName(null, null);
        mCountly.views().addSegmentationToViewWithID(null, null);

        mCountly.views().setGlobalViewSegmentation(null);
        mCountly.views().updateGlobalViewSegmentation(null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);
    }

    @Test
    public void MV_101_badValues_emptyString() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);

        mCountly.views().recordView("");
        mCountly.views().recordView("", null);

        mCountly.views().startAutoStoppedView("");
        mCountly.views().startAutoStoppedView("", null);

        mCountly.views().startView("");
        mCountly.views().startView("", null);

        mCountly.views().resumeViewWithID("");
        mCountly.views().pauseViewWithID("");

        mCountly.views().stopViewWithID("");
        mCountly.views().stopViewWithID("", null);

        mCountly.views().stopViewWithName("");
        mCountly.views().stopViewWithName("", null);

        mCountly.views().addSegmentationToViewWithName("", null);
        mCountly.views().addSegmentationToViewWithID("", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);
    }

    @Test
    public void MV_102_badValues_nonExistingViews() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, false, null, null);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);

        mCountly.views().resumeViewWithID("a");
        mCountly.views().pauseViewWithID("b");

        mCountly.views().stopViewWithID("c");
        mCountly.views().stopViewWithID("d", null);

        mCountly.views().stopViewWithName("e");
        mCountly.views().stopViewWithName("f", null);

        mCountly.views().addSegmentationToViewWithName("g", null);
        mCountly.views().addSegmentationToViewWithID("h", null);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);
    }

    /**
     * Make sure that no view event is created when recording an event with no consent
     */
    @Test
    public void MV_300A_callingWithNoConsent() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, true, null, null).setRequiresConsent(false);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);

        Map<String, Object> segm = new HashMap<>();
        segm.put("xxx", "33");
        segm.put("rtt", 2);

        mCountly.views().recordView("aa");
        mCountly.views().recordView("aa", segm);

        mCountly.views().startView("aa");
        mCountly.views().startView("aa", segm);

        mCountly.views().startAutoStoppedView("aa");
        mCountly.views().startAutoStoppedView("aa", segm);

        mCountly.views().pauseViewWithID("aa");

        mCountly.views().resumeViewWithID("aa");

        mCountly.views().stopViewWithName("aa");
        mCountly.views().stopViewWithName("aa", segm);

        mCountly.views().stopViewWithID("aa");
        mCountly.views().stopViewWithID("aa", segm);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);
    }

    @Test
    public void MV_300B_callingWithNoConsent() {
        @NonNull CountlyConfig cc = TestUtils.createViewCountlyConfig(false, false, true, null, null).setRequiresConsent(false);
        Countly mCountly = new Countly().init(cc);
        @NonNull EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);

        Map<String, Object> segm = new HashMap<>();
        segm.put("xxx", "33");
        segm.put("rtt", 2);

        mCountly.views().startView("aa");
        mCountly.views().startView("aa", segm);

        mCountly.views().startAutoStoppedView("aa");
        mCountly.views().startAutoStoppedView("aa", segm);

        mCountly.views().pauseViewWithID("aa");

        mCountly.views().resumeViewWithID("aa");

        mCountly.views().stopViewWithName("aa");
        mCountly.views().stopViewWithName("aa", segm);

        mCountly.views().stopViewWithID("aa");
        mCountly.views().stopViewWithID("aa", segm);

        TestUtils.validateRecordEventInternalMockInteractions(ep, 0);
        Assert.assertEquals(0, mCountly.countlyStore.getRequests().length);
    }
}
