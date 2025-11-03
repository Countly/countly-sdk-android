package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for the request queue cleaning behavior with and without the gradual cleaner disabled.
 */
@RunWith(AndroidJUnit4.class)
public class CountlyStoreRequestCleanerTests {
    CountlyStore store;

    @Before
    public void setUp() {
        store = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        store.clear();
    }

    private void fillRequests(int count) {
        for (int i = 0; i < count; i++) {
            store.addRequest("req_" + i, false);
        }
    }

    @Test
    public void testDefaultGradualCleanerRemovesUpToLoopLimit() {
        store.maxRequestQueueSize = 1000;
        fillRequests(200);
        assertEquals(200, store.getRequests().length);

        // Reduce max to force clean on next add
        store.maxRequestQueueSize = 50; // new limit
        store.addRequest("req_new_default", false);

        // With gradual cleaner: removed Math.min(100, overflow(150)) + 1 = 101 before adding new
        // Remaining after removal: 99, after adding new: 100
        String[] requests = store.getRequests();
        assertEquals(100, requests.length);
        // First remaining element should be the original index 101 (req_101)
        assertTrue("Expected first retained request to be req_101 but was " + requests[0], requests[0].equals("req_101"));
        // Last element should be the newly added one
        assertEquals("req_new_default", requests[requests.length - 1]);
    }

    @Test
    public void testDisableGradualRequestCleanerRemovesAllOverflow() {
        store.maxRequestQueueSize = 1000;
        fillRequests(200);
        assertEquals(200, store.getRequests().length);

        // Enable new mode
        store.setDisableGradualRequestCleaner(true);

        // Reduce max to force clean on next add
        store.maxRequestQueueSize = 50; // new limit
        store.addRequest("req_new_disabled", false);

        // With disabled gradual cleaner: removed overflow (150) + 1 = 151 before add
        // Remaining after removal: 49, after adding new: 50
        String[] requests = store.getRequests();
        assertEquals(50, requests.length);
        // First remaining element should be original index 151 (req_151)
        assertTrue("Expected first retained request to be req_151 but was " + requests[0], requests[0].equals("req_151"));
        // Last element should be the newly added one
        assertEquals("req_new_disabled", requests[requests.length - 1]);
    }
}
