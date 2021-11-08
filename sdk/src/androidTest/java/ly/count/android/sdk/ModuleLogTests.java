package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleLogTests {
    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @After
    public void tearDown() {
    }

    /**
     * Just making sure that nothing is crashing while printing logs while being enabled
     */
    @Test
    public void runAllLogCallsWhileEnabled() {
        Countly.sharedInstance().setLoggingEnabled(true);
        ModuleLog log = new ModuleLog();

        log.v("aa");
        log.d("bb");
        log.i("cc");
        log.w("dd");
        log.e("ee");
    }

    /**
     * Just making sure that nothing is crashing while printing logs while not being enabled
     */
    @Test
    public void runAllLogCallsWhileDisabled() {
        ModuleLog log = new ModuleLog();

        log.v("aa");
        log.d("bb");
        log.i("cc");
        log.w("dd");
        log.e("ee");
    }

    /**
     * Validate that the log listener is working during simple operation
     */
    @Test
    public void checkListenerSimple() {

    }
}
