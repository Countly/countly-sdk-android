package ly.count.android.sdk;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleLogTests {
    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
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

    /**
     * Build a context whose reported debuggable state we control, so the production-build
     * detection can be exercised without an actual release build. The wrapper only overrides
     * the application-info flags; everything else (including getApplicationContext used during
     * init) delegates to the real instrumentation context.
     */
    private Context contextWithDebuggable(boolean debuggable) {
        Context base = TestUtils.getContext();
        final ApplicationInfo info = new ApplicationInfo(base.getApplicationInfo());
        if (debuggable) {
            info.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
        } else {
            info.flags &= ~ApplicationInfo.FLAG_DEBUGGABLE;
        }

        return new ContextWrapper(base) {
            @Override
            public ApplicationInfo getApplicationInfo() {
                return info;
            }
        };
    }

    private CountlyConfig configFor(Context context, boolean disableInProduction, boolean loggingEnabled) {
        CountlyConfig config = new CountlyConfig(context, "appkey", "http://test.count.ly")
            .setDeviceId("1234")
            .setLoggingEnabled(loggingEnabled);
        if (disableInProduction) {
            config.disableSDKLoggingInProduction();
        }
        return config;
    }

    /**
     * The helper that backs the production detection reports the host build correctly:
     * a debuggable context is reported debuggable, a release-flavor context is not.
     */
    @Test
    public void isAppInDebuggableMode_reflectsApplicationFlags() {
        assertTrue(Utils.isAppInDebuggableMode(contextWithDebuggable(true)));
        assertFalse(Utils.isAppInDebuggableMode(contextWithDebuggable(false)));
        // the raw instrumentation context is a debuggable test build
        assertTrue(Utils.isAppInDebuggableMode(TestUtils.getContext()));
    }

    /**
     * Production build + flag enabled: console logging is forced off even though it was
     * requested at init, and a later runtime setLoggingEnabled(true) can not turn it back on.
     */
    @Test
    public void productionBuild_flagOn_forcesConsoleLoggingOff() {
        Countly countly = new Countly();
        countly.init(configFor(contextWithDebuggable(false), true, true));

        assertFalse("logging requested at init must stay off in production", countly.isLoggingEnabled());

        // runtime attempt to re-enable is also blocked (single chokepoint)
        countly.setLoggingEnabled(true);
        assertFalse("runtime re-enable must stay off in production", countly.isLoggingEnabled());

        // explicitly disabling still works and is idempotent
        countly.setLoggingEnabled(false);
        assertFalse(countly.isLoggingEnabled());
    }

    /**
     * Production build but the flag is left at its safe default: logging behaves normally,
     * proving the suppression is opt-in only and does not change existing behavior.
     */
    @Test
    public void productionBuild_flagOff_loggingUnaffected() {
        Countly countly = new Countly();
        countly.init(configFor(contextWithDebuggable(false), false, true));

        assertTrue("default flag must not suppress logging", countly.isLoggingEnabled());

        countly.setLoggingEnabled(false);
        assertFalse(countly.isLoggingEnabled());
        countly.setLoggingEnabled(true);
        assertTrue("runtime re-enable must work when flag is off", countly.isLoggingEnabled());
    }

    /**
     * Debug build + flag enabled: the flag only targets production, so a debuggable build
     * keeps logging fully functional at init and at runtime.
     */
    @Test
    public void debugBuild_flagOn_loggingStaysEnabled() {
        Countly countly = new Countly();
        countly.init(configFor(contextWithDebuggable(true), true, true));

        assertTrue("debug build must keep logging on despite the flag", countly.isLoggingEnabled());

        countly.setLoggingEnabled(false);
        assertFalse(countly.isLoggingEnabled());
        countly.setLoggingEnabled(true);
        assertTrue("runtime re-enable must work in debug builds", countly.isLoggingEnabled());
    }

    /**
     * Production suppression targets console (logcat) output only: a developer-provided
     * log listener keeps receiving SDK logs even while console logging is forced off.
     */
    @Test
    public void productionBuild_flagOn_logListenerStillReceivesLogs() {
        // The SDK logs from background threads too, so the listener can be invoked concurrently with
        // the assertion loop below; a plain ArrayList would throw ConcurrentModificationException.
        final List<String> received = new CopyOnWriteArrayList<>();
        ModuleLog.LogCallback listener = (logMessage, logLevel) -> received.add(logMessage);

        Countly countly = new Countly();
        countly.init(configFor(contextWithDebuggable(false), true, true).setLogListener(listener));

        assertFalse(countly.isLoggingEnabled());

        // the SDK also logs asynchronously, so match a unique marker rather than an exact count
        String marker = "a production error marker that must still reach the listener";
        countly.L.e(marker);

        boolean delivered = false;
        for (String message : received) {
            if (message.contains(marker)) {
                delivered = true;
                break;
            }
        }
        assertTrue("listener must receive the log even with console output off", delivered);
    }
}
