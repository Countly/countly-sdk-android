package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleUserProfileTests {
    CountlyStore store;

    @Before
    public void setUp() {
        Countly.sharedInstance().halt();
        store = new CountlyStore(getContext(), mock(ModuleLog.class));
        store.clear();
    }

    @After
    public void tearDown() {
    }

    /**
     * Testing basic flow
     */
    @Test
    public void setAndSaveValues() {
        Countly mCountly = Countly.sharedInstance();//todo move away from static init after static user profile has been removed
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        HashMap<String, Object> userProperties = new HashMap<>();
        userProperties.put("name", "Test Test");
        userProperties.put("username", "test");
        userProperties.put("email", "test@gmail.com");
        userProperties.put("organization", "Tester");
        userProperties.put("phone", "+1234567890");
        userProperties.put("gender", "M");
        userProperties.put("picture", "http://domain.com/test.png");
        userProperties.put("byear", "2000");
        userProperties.put("key1", "value1");
        userProperties.put("key2", "value2");

        mCountly.userProfile().setProperties(userProperties);
        mCountly.userProfile().save();

        assertEquals(1, store.getRequests().length);
    }

    /**
     * When saving user profile changes, it empties EQ into RQ
     */
    @Test
    public void SavingWritesEQIntoRQ() {
        Countly mCountly = Countly.sharedInstance();//todo move away from static init after static user profile has been removed
        CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        assertEquals(0, store.getEvents().length);
        assertEquals(0, store.getRequests().length);

        mCountly.events().recordEvent("a");
        assertEquals(1, store.getEvents().length);
        assertEquals(0, store.getRequests().length);

        mCountly.userProfile().setProperty("name", "Test Test");
        mCountly.userProfile().save();

        String[] reqs = store.getRequests();
        assertEquals(0, store.getEvents().length);
        assertEquals(2, reqs.length);
        assertTrue(reqs[0].contains("events"));
        assertFalse(reqs[1].contains("events"));
    }
}
