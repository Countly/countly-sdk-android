package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ScenarioRemoteConfigTests {
    CountlyStore store;
    StorageProvider sp;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class), false);
        sp = store;
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    @Test
    public void bump() {

    }
}
