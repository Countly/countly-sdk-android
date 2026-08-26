package ly.count.android.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import java.io.File;
import java.util.List;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;

/**
 * Debug-only, intent-driven driver for multi-instance manual testing. No UI, finishes immediately.
 * Lives in src/debug so no tracked app file changes and the staging/multi-instance comparison stays valid.
 *
 * Every op logs under tag MIH so logcat is the single source of evidence.
 */
public class MultiInstanceHarness extends Activity {
    private static final String T = "MIH";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String op = str("op", "stats");
        Log.i(T, "---- op=" + op + " begin");
        long t0 = System.currentTimeMillis();
        try {
            run(op);
        } catch (Throwable t) {
            Log.e(T, "op=" + op + " THREW " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
        Log.i(T, "---- op=" + op + " end took_ms=" + (System.currentTimeMillis() - t0));
        stats("after-" + op);
        finish();
    }

    private void run(String op) {
        switch (op) {
            case "init":        initOne(str("name", null), str("appkey", null), str("salt", null), str("deviceid", null)); break;
            case "event":       inst().events().recordEvent(str("key", "harness_event")); break;
            case "view":        inst().views().startAutoStoppedView(str("viewname", "harness_view")); break;
            case "userprop":    inst().userProfile().setProperty("harness_prop", str("val", "v")); inst().userProfile().save(); break;
            case "beginsession":inst().sessions().beginSession(); break;
            case "endsession":  inst().sessions().endSession(); break;
            case "flush":       inst().requestQueue().attemptToSendStoredRequests(); break;
            case "stop":        inst().stop(); Log.i(T, "stopped " + str("name", "?")); break;
            case "halt":        inst().halt(); Log.i(T, "halted " + str("name", "?")); break;
            case "remove":      Countly.removeInstance(str("name", null)); Log.i(T, "removed " + str("name", "?")); break;
            case "haltall":     Countly.haltAllInstances(); Log.i(T, "halted all"); break;
            case "list":        break;   // stats() prints the registry
            case "bulk":        bulk(getIntent().getIntExtra("count", 10), str("appkey", null)); break;
            default:            Log.w(T, "unknown op " + op);
        }
    }

    /** Initialises one named instance. A null/empty name means the default (shared) instance. */
    private void initOne(String name, String appKey, String salt, String deviceId) {
        String key = appKey != null ? appKey : App.getAppKey();
        CountlyConfig cfg = new CountlyConfig(getApplication(), key, App.getServerUrl())
            .setLoggingEnabled(true)
            .setEventQueueSizeToSend(1);            // send immediately so requests are observable
        if (deviceId != null) {
            cfg.setDeviceId(deviceId);
        }
        if (salt != null) {
            cfg.setParameterTamperingProtectionSalt(salt);
        }
        Countly c = (name == null || name.isEmpty()) ? Countly.sharedInstance() : Countly.instance(name);
        c.init(cfg);
        Log.i(T, "init name=[" + name + "] appkey=[" + key + "] salt=[" + salt + "] deviceid=[" + deviceId
            + "] initialised=" + c.isInitialized());
    }

    /** Phase E: create and initialise N instances as fast as possible and report what breaks. */
    private void bulk(int count, String appKey) {
        String key = appKey != null ? appKey : App.getAppKey();
        int ok = 0;
        String firstFailure = null;
        for (int i = 0; i < count; i++) {
            String name = "bulk_" + i;
            try {
                Countly c = Countly.instance(name);
                c.init(new CountlyConfig(getApplication(), key, App.getServerUrl())
                    .setLoggingEnabled(false)                 // keep logcat survivable at scale
                    .setDeviceId("dev_" + name));
                if (c.isInitialized()) {
                    ok++;
                }
            } catch (Throwable t) {
                if (firstFailure == null) {
                    firstFailure = "at i=" + i + " " + t.getClass().getName() + ": " + t.getMessage();
                    Log.e(T, "bulk FIRST FAILURE " + firstFailure, t);
                }
            }
            if (i % 25 == 0) {
                Log.i(T, "bulk progress i=" + i + " ok=" + ok + " " + resources());
            }
        }
        Log.i(T, "bulk DONE requested=" + count + " initialised_ok=" + ok
            + " first_failure=" + (firstFailure == null ? "none" : firstFailure));
    }

    private Countly inst() {
        String name = str("name", null);
        Countly c = (name == null || name.isEmpty()) ? Countly.sharedInstance() : Countly.getInstance(name);
        if (c == null) {
            throw new IllegalStateException("no instance registered under [" + name + "]");
        }
        return c;
    }

    private String resources() {
        Runtime r = Runtime.getRuntime();
        File prefs = new File(getApplicationInfo().dataDir, "shared_prefs");
        String[] files = prefs.list();
        return "threads=" + Thread.activeCount()
            + " heap_used_mb=" + ((r.totalMemory() - r.freeMemory()) / 1048576)
            + " heap_max_mb=" + (r.maxMemory() / 1048576)
            + " native_mb=" + (Debug.getNativeHeapAllocatedSize() / 1048576)
            + " prefs_files=" + (files == null ? -1 : files.length);
    }

    private void stats(String when) {
        List<String> named = Countly.listInstances();
        Log.i(T, "STATS[" + when + "] registered_named=" + named.size() + " " + resources());
        Log.i(T, "STATS[" + when + "] names=" + named);
    }

    private String str(String k, String dflt) {
        String v = getIntent().getStringExtra(k);
        return (v == null || v.isEmpty()) ? dflt : v;
    }
}
