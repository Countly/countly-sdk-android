package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class scAP_AppPerfomanceMonit {
    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @Test
    public void AP_200_notEnabledNothingWorking() {
    }

    @Test
    public void AP_201_automaticAppStart() {
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
