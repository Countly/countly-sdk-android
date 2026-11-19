package ly.count.android.demo;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;

/**
 * Demonstrates running several independent Countly instances alongside the default (shared) one.
 *
 * Each named instance keeps its own request queue, event queue, device ID, consent state, logging
 * state, and stored configuration, fully isolated from {@code Countly.sharedInstance()}. For demo
 * simplicity the named instances are pointed at the same server and app key as the default instance
 * (each with a distinct device ID); a real integration would use a separate Countly application's
 * credentials.
 */
public class ActivityExampleMultiInstance extends AppCompatActivity {
    private static final String ANALYTICS = "analytics";
    private static final String BILLING = "billing";
    private boolean analyticsLogging = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_multi_instance);

        // --- analytics instance ---
        findViewById(R.id.btnCreateAnalytics).setOnClickListener(v -> createAndInit(ANALYTICS, "analytics-device"));

        findViewById(R.id.btnRecordEventAnalytics).setOnClickListener(v -> {
            Countly analytics = requireInitialized(ANALYTICS);
            if (analytics == null) {
                return;
            }
            analytics.events().recordEvent("analytics_event");
            toast("Recorded 'analytics_event' on '" + ANALYTICS + "'");
        });

        findViewById(R.id.btnRecordViewAnalytics).setOnClickListener(v -> {
            Countly analytics = requireInitialized(ANALYTICS);
            if (analytics == null) {
                return;
            }
            analytics.views().startAutoStoppedView("AnalyticsScreen");
            toast("Started view 'AnalyticsScreen' on '" + ANALYTICS + "'");
        });

        findViewById(R.id.btnToggleLogAnalytics).setOnClickListener(v -> {
            Countly analytics = requireInitialized(ANALYTICS);
            if (analytics == null) {
                return;
            }
            analyticsLogging = !analyticsLogging;
            analytics.setLoggingEnabled(analyticsLogging);
            toast("'" + ANALYTICS + "' logging " + (analyticsLogging ? "ENABLED" : "DISABLED") + " (default instance logging unaffected)");
        });

        findViewById(R.id.btnRemoveAnalytics).setOnClickListener(v -> {
            if (Countly.getInstance(ANALYTICS) == null) {
                toast("'" + ANALYTICS + "' is not registered, nothing to remove");
                return;
            }
            Countly.removeInstance(ANALYTICS);
            toast("Removed the '" + ANALYTICS + "' instance");
        });

        // --- billing instance (second named instance) ---
        findViewById(R.id.btnCreateBilling).setOnClickListener(v -> createAndInit(BILLING, "billing-device"));

        findViewById(R.id.btnRecordEventBilling).setOnClickListener(v -> {
            Countly billing = requireInitialized(BILLING);
            if (billing == null) {
                return;
            }
            billing.events().recordEvent("billing_event");
            toast("Recorded 'billing_event' on '" + BILLING + "'");
        });

        // --- default instance (contrast) ---
        findViewById(R.id.btnRecordEventDefault).setOnClickListener(v -> {
            //the default instance can be halted from this very screen ("Halt All Instances"), and the
            //module accessors return null when an instance is not initialised
            if (!Countly.sharedInstance().isInitialized()) {
                toast("The default instance is not initialized (halt all resets it too). Restart the app to init it again.");
                return;
            }
            Countly.sharedInstance().events().recordEvent("default_event");
            toast("Recorded 'default_event' on the default (shared) instance");
        });

        // --- registry ---
        findViewById(R.id.btnList).setOnClickListener(v -> {
            List<String> names = Countly.listInstances();
            toast("Registered instances: " + (names.isEmpty() ? "(none)" : names));
        });

        findViewById(R.id.btnGetAnalytics).setOnClickListener(v -> {
            Countly existing = Countly.getInstance(ANALYTICS);
            if (existing == null) {
                toast("'" + ANALYTICS + "' is not registered");
            } else {
                toast("'" + ANALYTICS + "' exists, initialized: " + existing.isInitialized());
            }
        });

        findViewById(R.id.btnHaltAll).setOnClickListener(v -> {
            Countly.haltAllInstances();
            toast("Halted every instance, the default one included. Stored data was ERASED: device IDs, consent, unsent requests and push preferences");
        });
    }

    private void createAndInit(String name, String deviceId) {
        Countly instance = Countly.instance(name);
        if (instance.isInitialized()) {
            toast("'" + name + "' is already initialized");
            return;
        }

        // The name passed to Countly.instance(name) is what isolates this instance's storage. The
        // distinct device ID keeps its identity separate. No Application class is given, so the SDK
        // cannot observe the activity lifecycle for this instance - session control is switched to
        // manual, otherwise an automatic session would begin at init (the app is in the foreground)
        // that nothing could ever end, updating forever even in the background.
        CountlyConfig config = new CountlyConfig(getApplicationContext(), App.getAppKey(), App.getServerUrl())
            .setDeviceId(deviceId)
            .enableManualSessionControl()
            .setLoggingEnabled(true);

        instance.init(config);
        toast("Initialized '" + name + "' (device id: " + deviceId + ")");
    }

    /**
     * Returns the initialized instance registered under the name, or null (with a hint toast). The
     * returned handle - not a fresh {@code Countly.instance(name)} lookup - must be used for the
     * follow-up call: re-fetching through instance(name) would silently re-create a fresh,
     * uninitialized instance if removeInstance(name) ran in between, and its module accessors
     * (events(), views(), ...) would then return null.
     */
    private Countly requireInitialized(String name) {
        Countly instance = Countly.getInstance(name);
        if (instance == null || !instance.isInitialized()) {
            toast("Create and initialize the '" + name + "' instance first");
            return null;
        }
        return instance;
    }

    private void toast(String message) {
        Log.d(Countly.TAG, "[MultiInstanceDemo] " + message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
