package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleRatingsTests {
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void recordManualRating() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));

        String[] vals = new String[] { "aa", "bb", "cc" };
        mCountly.ratings().recordManualRating(vals[0], 3, vals[1], vals[2], true);

        final Map<String, Object> segm = new HashMap<>();

        segm.put("platform", "android");
        segm.put("app_version", "1.0");
        segm.put("rating", "" + 3);
        segm.put("widget_id", vals[0]);
        segm.put("email", vals[1]);
        segm.put("comment", vals[2]);
        segm.put("contactMe", true);

        verify(ep).recordEventInternal(ModuleRatings.STAR_RATING_EVENT_KEY, segm, 1, 0, 0, null);

        //validate lower bound
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.ratings().recordManualRating(vals[0], -12, vals[1], vals[2], true);
        segm.put("rating", "" + 1);
        verify(ep).recordEventInternal(ModuleRatings.STAR_RATING_EVENT_KEY, segm, 1, 0, 0, null);

        //validate upper bound
        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.ratings().recordManualRating(vals[0], 12, vals[1], vals[2], true);
        segm.put("rating", "" + 5);
        verify(ep).recordEventInternal(ModuleRatings.STAR_RATING_EVENT_KEY, segm, 1, 0, 0, null);

        ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.moduleRatings.recordManualRatingInternal(null, 12, vals[1], vals[2], true);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        mCountly.moduleRatings.recordManualRatingInternal("", 12, vals[1], vals[2], true);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }

    @Test(expected = IllegalStateException.class)
    public void recordManualRatingNullID() {
        mCountly.ratings().recordManualRating(null, 3, "ss", "qq", true);
    }

    @Test(expected = IllegalStateException.class)
    public void recordManualRatingEmptyID() {
        mCountly.ratings().recordManualRating("", 3, "ss", "qq", true);
    }

    @Test
    public void getAutomaticSessionLimit() {
        Countly countly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setStarRatingSessionLimit(44);
        countly.init(config);
        Assert.assertEquals(44, countly.ratings().getAutomaticStarRatingSessionLimit());
    }

    @Test
    public void getCurrentSessionCount() {
        mCountly.ratings().clearAutomaticStarRatingSessionCount();

        Assert.assertEquals(0, mCountly.ratings().getCurrentVersionsSessionCount());

        mCountly.moduleRatings.registerAppSession(getContext(), null);
        mCountly.moduleRatings.registerAppSession(getContext(), null);

        Assert.assertEquals(2, mCountly.ratings().getCurrentVersionsSessionCount());

        mCountly.ratings().clearAutomaticStarRatingSessionCount();

        Assert.assertEquals(0, mCountly.ratings().getCurrentVersionsSessionCount());
    }

    @Test
    public void setIfStarRatingShouldBeShownAutomatically() {
        Assert.assertFalse(mCountly.moduleRatings.getIfStarRatingShouldBeShownAutomatically());

        Countly countly = new Countly();
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setIfStarRatingShownAutomatically(true);
        countly.init(config);

        Assert.assertTrue(countly.moduleRatings.getIfStarRatingShouldBeShownAutomatically());
    }

    @Test
    public void loadRatingPreferencesBadJson() {
        StorageProvider cs = mCountly.connectionQueue_.getStorageProvider();
        cs.setStarRatingPreferences("./{}23[]d");
        Assert.assertEquals("./{}23[]d", cs.getStarRatingPreferences());
        ModuleRatings.StarRatingPreferences srp = ModuleRatings.loadStarRatingPreferences(cs);

        Assert.assertEquals("", srp.appVersion);
        Assert.assertEquals(5, srp.sessionLimit);
        Assert.assertEquals(0, srp.sessionAmount);
        Assert.assertFalse(srp.isShownForCurrentVersion);
        Assert.assertFalse(srp.automaticRatingShouldBeShown);
        Assert.assertFalse(srp.disabledAutomaticForNewVersions);
        Assert.assertFalse(srp.automaticHasBeenShown);
        Assert.assertTrue(srp.isDialogCancellable);
        Assert.assertEquals("App rating", srp.dialogTextTitle);
        Assert.assertEquals("Please rate this app", srp.dialogTextMessage);
        Assert.assertEquals("Cancel", srp.dialogTextDismiss);
    }

    /**
     * Manually initialize the rating module and then make sure that the star rating preferences return the correct values
     */
    //@Test
    //public void setAllFieldsDuringInit() {
    //    CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting().setStarRatingSessionLimit(44);
    //    config.setStarRatingDisableAskingForEachAppVersion(true);
    //    config.setStarRatingSessionLimit(445);
    //    config.setIfStarRatingShownAutomatically(true);
    //    config.setIfStarRatingDialogIsCancellable(true);
    //    config.setStarRatingTextTitle("dffgg");
    //    config.setStarRatingTextMessage("qwe123");
    //    config.setStarRatingTextDismiss("666");
    //
    //    StarRatingCallback src = new StarRatingCallback() {
    //        @Override
    //        public void onRate(int rating) {
    //
    //        }
    //
    //        @Override
    //        public void onDismiss() {
    //
    //        }
    //    };
    //
    //    config.setStarRatingCallback(src);
    //    StorageProvider sp = mCountly.connectionQueue_.getStorageProvider();
    //
    //    ModuleRatings mr = new ModuleRatings(mCountly, config);
    //
    //    ModuleRatings.StarRatingPreferences srp = ModuleRatings.loadStarRatingPreferences(sp);
    //
    //    Assert.assertTrue(mr.getIfStarRatingShouldBeShownAutomatically());
    //    Assert.assertTrue(srp.automaticRatingShouldBeShown);
    //    Assert.assertEquals(mr.starRatingCallback_, src);
    //    Assert.assertEquals(srp.dialogTextMessage, "qwe123");
    //    Assert.assertEquals(srp.dialogTextTitle, "dffgg");
    //    Assert.assertEquals(srp.dialogTextDismiss, "666");
    //    Assert.assertTrue(srp.disabledAutomaticForNewVersions);
    //    Assert.assertTrue(srp.isDialogCancellable);
    //    Assert.assertEquals(445, srp.sessionLimit);
    //}
}
