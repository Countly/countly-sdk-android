package ly.count.android.sdk;

import android.app.Activity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class scAP_AppPerfomanceMonit {
    CountlyStore countlyStore;

    Activity act;
    Activity act2;

    CountlyConfig createAPMConfig() {
        CountlyConfig config = (new CountlyConfig(getContext(), TestUtils.commonAppKey, TestUtils.commonURL)).setDeviceId(TestUtils.commonDeviceId).setLoggingEnabled(true).enableCrashReporting();
        config.setRequiresConsent(true);
        config.setConsentEnabled(new String[] { Countly.CountlyFeatureNames.apm, Countly.CountlyFeatureNames.location });
        return config;
    }

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        act = mock(TestUtils.Activity2.class);
        act2 = mock(TestUtils.Activity3.class);
    }

    @Test
    public void AP_200_notEnabledNothingWorking() {
        CountlyConfig config = createAPMConfig();
        config.apm.appStartTimestampOverride = 10_000L;

        Assert.assertEquals(0, countlyStore.getRequests().length);

        Countly countly = (new Countly()).init(config);

        //enter foreground
        countly.apm().setAppIsLoaded();
        countly.onStart(act);

        //go to background and back
        countly.onStop();
        countly.onStart(act2);

        //there is only the consent request
        String[] req = countlyStore.getRequests();
        Assert.assertEquals(1, req.length);
        Assert.assertTrue(TestUtils.getParamValueFromRequest(req[0], "consent").length() > 0);
    }

    @Test
    public void AP_201A_automaticAppStart() {
        AP_201_automaticAppStart_base(false);
    }

    @Test
    public void AP_201B_automaticAppStart() {
        AP_201_automaticAppStart_base(true);
    }

    public void AP_201_automaticAppStart_base(boolean sdkInitsBeforeFirstScreen) {
        CountlyConfig config = createAPMConfig();
        config.apm.appStartTimestampOverride = 10000L;
        config.apm.enableAppStartTimeTracking();

        if (!sdkInitsBeforeFirstScreen) {
            config.lifecycleObserver = () -> true;
        }

        Assert.assertEquals(0, countlyStore.getRequests().length);

        Countly countly = (new Countly()).init(config);
        String[] req;

        if (sdkInitsBeforeFirstScreen) {
            //enter foreground
            req = countlyStore.getRequests();
            Assert.assertEquals(1, req.length);
            Assert.assertTrue(TestUtils.getParamValueFromRequest(req[0], "consent").length() > 0);
            countly.onStart(act);
            req = countlyStore.getRequests();
        } else {
            req = countlyStore.getRequests();
        }

        //there is only the consent request
        Assert.assertEquals(2, req.length);
        Assert.assertTrue(TestUtils.getParamValueFromRequest(req[0], "consent").length() > 0);
        String apmContent = TestUtils.getParamValueFromRequest(req[1], "apm");
        Assert.assertTrue(apmContent.startsWith("%7B%22type%22%3A%22device%22%2C%22name%22%3A%22app_start%22%2C+%22apm_metrics%22%3A%7B%22duration%22%3A+"));
        Assert.assertTrue(apmContent.contains("%7D%2C+%22stz%22%3A+10000%2C+%22etz%22%3A"));

        //go to background and back
        countly.onStop();
        countly.onStart(act2);

        countly.apm().setAppIsLoaded();
        countly.apm().setAppIsLoaded();

        Assert.assertEquals(2, req.length);
    }

    @Test
    public void AP_202_manualAppStartTrigger_notUsed() {
    }

    @Test
    public void AP_203_manualAppStartTrigger_correct() {
    }

    @Test
    public void AP_204_FBTrackingEnabled_working() {
    }

    @Test
    public void AP_205_AppStatOverride_automatic() {
    }

    @Test
    public void AP_206_AppStartOverride_manual() {
    }
}
