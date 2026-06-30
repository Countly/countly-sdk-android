package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ModuleHealthCheckTests {

    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    @After
    public void tearDown() {
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    /**
     * A health check response that arrives after the SDK has been halted (e.g. an
     * init/halt/init reinit cycle on a slow network) must not crash with an NPE
     * when it tries to reset the now-null health counter.
     */
    @Test
    public void healthCheckCallback_afterHalt_doesNotCrash() throws JSONException {
        // Capture the callback the health check registers, instead of doing real networking.
        ImmediateRequestI requestMaker = mock(ImmediateRequestI.class);
        ImmediateRequestGenerator generator = mock(ImmediateRequestGenerator.class);
        when(generator.CreateImmediateRequestMaker()).thenReturn(requestMaker);

        CountlyConfig config = TestUtils.createBaseConfig();
        config.immediateRequestGenerator = generator;

        Countly countly = new Countly().init(config); // initFinished() -> sendHealthCheck()

        ArgumentCaptor<ImmediateRequestMaker.InternalImmediateRequestCallback> cb =
            ArgumentCaptor.forClass(ImmediateRequestMaker.InternalImmediateRequestCallback.class);
        Mockito.verify(requestMaker).doWork(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq("/i"),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyBoolean(),
            ArgumentMatchers.anyBoolean(),
            cb.capture(),
            ArgumentMatchers.any()
        );

        // Simulate the reinit race: SDK halted while the request was in flight.
        countly.halt();

        // Delivering a successful response must not throw (regression: NPE on hCounter).
        cb.getValue().callback(new JSONObject("{\"result\":\"Success\"}"));
    }
}