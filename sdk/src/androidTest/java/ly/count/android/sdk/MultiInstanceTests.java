/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import ly.count.android.sdk.messaging.ModulePush;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * Integration tests for multi-instance support: independent Countly instances obtained via
 * {@link Countly#instance(String)}, each with isolated storage, request queue, device id, logging,
 * and timed-event state, while {@link Countly#sharedInstance()} stays a drop-in default that keeps
 * the legacy storage location.
 */
@RunWith(AndroidJUnit4.class)
public class MultiInstanceTests {
    // Names this suite creates. They are halted and their namespaced storage cleared between tests
    // so nothing leaks across tests or across test classes.
    private static final String[] NAMES = { "instB", "instLog", "instLoud", "instTimed", "instFile", "instCreateA", "instCreateB", "ignoredName", "instRemove", "instFresh", "instCfgA", "instCfgB", "instPushBcast" };

    private static final String PUSH_PREFS_FILE = "ly.count.android.api.messaging";

    private static final String APP_KEY_A = "appKeyA";
    private static final String APP_KEY_B = "appKeyB";
    private static final String DEVICE_A = "deviceA";
    private static final String DEVICE_B = "deviceB";

    @Before
    public void setUp() {
        resetAll();
    }

    @After
    public void tearDown() {
        resetAll();
    }

    // A CountlyStore bound to a given storage namespace, using a real (silent) logger so cleanup and
    // verification work on Android runtimes where Mockito cannot inject mocks.
    private static CountlyStore store(String namespace) {
        return new CountlyStore(TestUtils.getContext(), new ModuleLog(), false, namespace);
    }

    private void resetAll() {
        Countly.sharedInstance().halt();
        store("").clear();
        for (String name : NAMES) {
            // Use getInstance (never creates) so cleanup does not itself register names - the
            // registry never removes instances, so creating here would defeat the getInstance-is-null
            // expectation that the parity-API test relies on.
            Countly existing = Countly.getInstance(name);
            if (existing != null) {
                existing.halt();
            }
            // Clear via an explicitly namespaced store so on-disk state is cleaned even for a name
            // that has never been initialised (storageNamespace_ is only resolved at init time).
            store(CountlyStore.sanitizeNamespace(name)).clear();
            clearOpenUdid(CountlyStore.sanitizeNamespace(name));
        }
        clearOpenUdid("");
        clearNativeDumps();
        // the dispatcher is process-wide: drop instances a test (or another suite) left behind and
        // zero the simulated activity count so lifecycle simulations start from a known state
        CountlyLifecycleDispatcher.getInstance().resetForTests();
    }

    // The single process-wide folder sdk-native hands to breakpad. It has no instance concept, so only
    // the default instance may consume it - these helpers let the tests prove that.
    private static File nativeDumpFolder() {
        return new File(TestUtils.getContext().getCacheDir().getAbsolutePath() + File.separator + "Countly" + File.separator + "CrashDumps");
    }

    private static File writeFakeNativeDump(String name) throws IOException {
        File folder = nativeDumpFolder();
        folder.mkdirs();
        File dump = new File(folder, name);
        //Files.write rather than a FileOutputStream: it closes the handle itself, so an assertion failing
        //mid-test cannot leak one
        Files.write(dump.toPath(), new byte[] { 1, 2, 3, 4 });
        return dump;
    }

    private static void clearNativeDumps() {
        File[] files = nativeDumpFolder().listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    private static String openUdid(String namespace) {
        return TestUtils.getContext().getSharedPreferences(
            CountlyStore.namespacedName(ModuleDeviceId.PREFS_NAME, namespace), Context.MODE_PRIVATE)
            .getString(ModuleDeviceId.PREF_KEY, null);
    }

    private static void clearOpenUdid(String namespace) {
        TestUtils.getContext().getSharedPreferences(
            CountlyStore.namespacedName(ModuleDeviceId.PREFS_NAME, namespace), Context.MODE_PRIVATE)
            .edit().clear().apply();
    }

    private CountlyConfig baseConfig(String appKey, String deviceId) {
        return new CountlyConfig(TestUtils.getContext(), appKey, TestUtils.commonURL)
            .setDeviceId(deviceId)
            .setLoggingEnabled(true)
            .enableManualSessionControl();
    }

    //Deliberately returns null, not an empty map: callers assert notNull to mean "this request was sent",
    //and an empty map would make every one of those assertions pass whether the request existed or not.
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    private static Map<String, String> firstRequestWithKey(Map<String, String>[] rq, String key) {
        for (Map<String, String> request : rq) {
            if (request != null && request.containsKey(key)) {
                return request;
            }
        }
        return null;
    }

    private static void assertAllRequestsCarryAppKey(Map<String, String>[] rq, String expectedAppKey) {
        for (Map<String, String> request : rq) {
            if (request != null) {
                Assert.assertEquals("a request leaked from/into another instance", expectedAppKey, request.get("app_key"));
            }
        }
    }

    /**
     * The core guarantee: the default instance and a named instance, initialised with different app
     * keys and device ids, keep completely separate request queues and device identities. Neither
     * instance's data ever appears in the other's storage.
     */
    @Test
    public void namedAndDefaultInstances_isolateRequestQueuesAndDeviceId() {
        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));

        Countly named = Countly.instance("instB");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));

        def.sessions().beginSession();
        named.sessions().beginSession();

        Map<String, String>[] rqDefault = TestUtils.getCurrentRQ(def);
        Map<String, String>[] rqNamed = TestUtils.getCurrentRQ(named);

        // each instance produced its own begin_session on the wire, tagged with its own identity
        Map<String, String> beginDefault = firstRequestWithKey(rqDefault, "begin_session");
        Map<String, String> beginNamed = firstRequestWithKey(rqNamed, "begin_session");
        Assert.assertNotNull("default instance must have a begin_session request", beginDefault);
        Assert.assertNotNull("named instance must have a begin_session request", beginNamed);
        Assert.assertEquals(APP_KEY_A, beginDefault.get("app_key"));
        Assert.assertEquals(DEVICE_A, beginDefault.get("device_id"));
        Assert.assertEquals(APP_KEY_B, beginNamed.get("app_key"));
        Assert.assertEquals(DEVICE_B, beginNamed.get("device_id"));

        // no cross-talk: every request in each queue belongs only to that instance
        assertAllRequestsCarryAppKey(rqDefault, APP_KEY_A);
        assertAllRequestsCarryAppKey(rqNamed, APP_KEY_B);

        // device id is persisted per-instance in each instance's own storage
        Assert.assertEquals(DEVICE_A, TestUtils.getCountlyStore(def).getDeviceID());
        Assert.assertEquals(DEVICE_B, TestUtils.getCountlyStore(named).getDeviceID());

        // the default instance keeps the legacy (un-namespaced) storage; the named one does not
        Assert.assertEquals("", def.storageNamespace_);
        Assert.assertNotEquals("", named.storageNamespace_);
        Assert.assertTrue(named.storageNamespace_.startsWith("instB"));
    }

    /**
     * Regression: a brand-new named instance must honor its config's device ID even when the shared,
     * primary-owned push preferences file already has data (as it does in a real app once the default
     * instance has cached a push provider). Before the fix, anythingSetInStorage() counted the shared
     * push file, so a fresh named store was misdetected as a legacy install, ran a schema migration,
     * and that migration replaced the developer-supplied device ID with a generated OPEN_UDID.
     */
    @Test
    public void freshNamedInstance_honorsSuppliedDeviceId_whenSharedPushPrefsExist() {
        // Simulate the primary instance having cached a push provider into the shared push file.
        TestUtils.getContext().getSharedPreferences(PUSH_PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putInt("PUSH_MESSAGING_PROVIDER", 1).apply();

        Countly named = Countly.instance("instFresh");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));

        // The mechanism under test: a fresh named store must judge its own freshness by its own file, so
        // it starts at the latest schema and runs no migration at all. Asserting the schema (not only the
        // resulting device ID) is what separates the gated path from the un-gated one - the legacy path
        // reaches the same device ID through MigrationHelper, so the ID alone proves nothing.
        Assert.assertEquals("a fresh named store must not be treated as a legacy install",
            MigrationHelper.DATA_SCHEMA_VERSIONS, TestUtils.getCountlyStore(named).getDataSchemaVersion());

        // and the supplied device ID is kept rather than replaced by a generated OPEN_UDID
        Assert.assertEquals(DEVICE_B, TestUtils.getCountlyStore(named).getDeviceID());
        Assert.assertEquals("DEVELOPER_SUPPLIED", TestUtils.getCountlyStore(named).getDeviceIDType());

        // the default instance owns the push file, so its own freshness check still consults it
        Assert.assertTrue(store("").anythingSetInStorage());
    }

    /**
     * Push ownership has two halves: the store write, and the process-global {@code CONSENT_BROADCAST}
     * that CountlyPush reacts to by registering the DEFAULT instance's token. A named instance must
     * suppress the broadcast as well, otherwise granting push consent on it would drive the default
     * instance's push registration.
     */
    @Test
    public void namedInstance_doesNotFireTheProcessGlobalPushConsentBroadcast() {
        //Its OWN instance name. Sharing a name with another test made this flaky: the registry keeps an
        //instance across tests, so if the other user of that name ran first this instance already had push
        //consent, giveConsent below became a no-op, doPushConsentSpecialAction never ran and the log line
        //this test looks for was never produced. JUnit does not guarantee method order, hence intermittent.
        Countly named = Countly.instance("instPushBcast");
        named.init(baseConfig(APP_KEY_B, DEVICE_B).setRequiresConsent(true));

        Assert.assertFalse("precondition: push consent must start ungranted, otherwise giveConsent below is a no-op and this test proves nothing",
            named.consent().getConsent(Countly.CountlyFeatureNames.push));

        //Copy-on-write, not ArrayList: this instance is initialised, so its logger is called from the SDK's
        //background threads (network, timer) while the assertions below read the list. With a plain
        //ArrayList this threw ConcurrentModificationException out of AbstractCollection.toString while
        //building the "Log was:" message - the read side has to be snapshot-based.
        final List<String> namedLog = new CopyOnWriteArrayList<>();
        named.L.SetListener((logMessage, logLevel) -> namedLog.add(logMessage));

        named.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.push });

        //stop capturing before asserting, so nothing further arrives while the list is being read
        named.L.SetListener(null);

        boolean broadcastSuppressed = false;
        for (String message : namedLog) {
            if (message.contains("named instance does not own push, skipping the process-global consent broadcast")) {
                broadcastSuppressed = true;
                break;
            }
        }
        Assert.assertTrue("push consent must actually have been granted, otherwise the broadcast path was never reached",
            named.consent().getConsent(Countly.CountlyFeatureNames.push));
        Assert.assertTrue("a named instance must suppress the process-global push consent broadcast. Log was:" + namedLog, broadcastSuppressed);

        // and it must not have written the owner's push consent either
        Assert.assertFalse("a named instance must not grant push consent in the shared push file",
            store("").getConsentPush());
    }

    /**
     * The half of {@code ModuleCrash.halt()} that cannot unlink: once the host app (or another instance)
     * installs a handler on top, ours is stuck in the middle of the process-global chain. Halting must
     * then leave the foreign handler alone and simply stop recording, rather than restoring over it.
     */
    @Test
    public void haltingInstance_keepsAForeignCrashHandlerAndStopsRecording() {
        Thread.UncaughtExceptionHandler originalDefault = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Countly named = Countly.instance("instCfgB");
            CountlyConfig config = baseConfig(APP_KEY_B, DEVICE_B);
            config.crashes.enableCrashReporting();
            named.init(config);

            ModuleCrash moduleCrash = named.moduleCrash;
            Assert.assertTrue("the instance must have installed its crash handler", moduleCrash.unhandledCrashHandlerInstalled);
            Thread.UncaughtExceptionHandler countlyHandler = Thread.getDefaultUncaughtExceptionHandler();

            // the host app installs its own handler on top of ours
            Thread.UncaughtExceptionHandler foreign = (thread, throwable) -> countlyHandler.uncaughtException(thread, throwable);
            Thread.setDefaultUncaughtExceptionHandler(foreign);

            named.halt();

            // there is no way to remove a link from the middle of the chain, so the app's handler stays
            Assert.assertSame("halt must not restore over a handler the app installed later",
                foreign, Thread.getDefaultUncaughtExceptionHandler());
            // and ours is neutralised rather than left recording into torn-down queues
            Assert.assertFalse("a halted instance must no longer consider its crash handler installed",
                moduleCrash.unhandledCrashHandlerInstalled);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalDefault);
        }
    }

    /**
     * Storage isolation has two halves, and the dangerous half is the one that deletes. Every other
     * isolation test here only checks what a store contains after recording; this one covers the read
     * side, because a named instance's ConnectionProcessor removes requests from whatever store it was
     * handed. Wired to the wrong store it would drain, and delete, the default integration's queue.
     */
    @Test
    public void namedInstanceConnectionProcessor_readsAndRemovesFromItsOwnStore() {
        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));
        def.sessions().beginSession();

        Countly named = Countly.instance("instB");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));
        named.sessions().beginSession();

        ConnectionProcessor namedProcessor = named.connectionQueue_.createConnectionProcessor();
        ConnectionProcessor defaultProcessor = def.connectionQueue_.createConnectionProcessor();

        // each processor must be bound to its own instance's store, and to the server URL it was
        // configured with rather than the other instance's
        Assert.assertSame("a named instance's processor must read its own store",
            named.countlyStore, namedProcessor.getCountlyStore());
        Assert.assertSame(def.countlyStore, defaultProcessor.getCountlyStore());
        Assert.assertNotSame(namedProcessor.getCountlyStore(), defaultProcessor.getCountlyStore());

        // and the request each one would drain belongs to that instance only
        String[] namedRequests = namedProcessor.getCountlyStore().getRequests();
        String[] defaultRequests = defaultProcessor.getCountlyStore().getRequests();
        Assert.assertEquals(1, namedRequests.length);
        Assert.assertEquals(1, defaultRequests.length);
        Assert.assertTrue("the named processor must see only its own app key's request", namedRequests[0].contains("app_key=" + APP_KEY_B));
        Assert.assertTrue(defaultRequests[0].contains("app_key=" + APP_KEY_A));

        // removing through the named processor's store must not touch the default instance's queue
        namedProcessor.getCountlyStore().removeRequest(namedRequests[0]);
        Assert.assertEquals(0, TestUtils.getCurrentRQ(named).length);
        Assert.assertEquals("draining a named instance must never delete the default instance's requests",
            1, TestUtils.getCurrentRQ(def).length);
    }

    /**
     * The namespace ends up in a SharedPreferences file name, and Android silently stops persisting once
     * a file name passes the 255-byte filesystem limit. A long instance name must therefore be capped,
     * while still producing distinct namespaces for distinct names.
     */
    @Test
    public void sanitizeNamespace_capsTheFileNameLength() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longName.append('x');
        }

        String sanitized = CountlyStore.sanitizeNamespace(longName.toString());
        String fileName = CountlyStore.namespacedName("COUNTLY_STORE", sanitized);
        Assert.assertTrue("the derived file name must stay well inside the 255 byte limit, was " + fileName.length(),
            fileName.length() < 200);

        // two long names sharing the truncated prefix must still get their own file
        String other = CountlyStore.sanitizeNamespace(longName + "-other");
        Assert.assertNotEquals(sanitized, other);
    }

    /**
     * Backward compatibility + file-level isolation: the default instance writes to the exact legacy
     * SharedPreferences file, so an app upgrading from a single-instance SDK version keeps its data.
     * A named instance writes only to its suffixed file, invisible to the legacy store.
     */
    @Test
    public void defaultKeepsLegacyStorage_namedIsIsolatedAtFileLevel() {
        // file naming: default -> legacy base name, named -> suffixed
        Assert.assertEquals("COUNTLY_STORE", CountlyStore.namespacedName("COUNTLY_STORE", ""));
        Assert.assertEquals("COUNTLY_STORE", CountlyStore.namespacedName("COUNTLY_STORE", null));
        Assert.assertEquals("COUNTLY_STORE_abc", CountlyStore.namespacedName("COUNTLY_STORE", "abc"));

        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));
        def.sessions().beginSession();

        Countly named = Countly.instance("instFile");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));
        named.sessions().beginSession();

        // a brand-new legacy-scoped store (no namespace) sees the default instance's request, never
        // the named instance's - proving the default still uses the legacy file and the named
        // instance writes elsewhere
        CountlyStore legacyStore = store("");
        Map<String, String>[] rqLegacy = TestUtils.getCurrentRQ("", legacyStore);
        Assert.assertNotNull(firstRequestWithKey(rqLegacy, "begin_session"));
        assertAllRequestsCarryAppKey(rqLegacy, APP_KEY_A);

        // the named instance's namespaced store holds only its own data
        Map<String, String>[] rqNamed = TestUtils.getCurrentRQ(named);
        Assert.assertNotNull(firstRequestWithKey(rqNamed, "begin_session"));
        assertAllRequestsCarryAppKey(rqNamed, APP_KEY_B);
    }

    /**
     * Registry semantics: instances are stable per name, the several ways of naming the default all
     * resolve to the same object, and an instance survives halt() (state resets, identity does not).
     */
    @Test
    public void instanceRegistry_returnsStableObjectsAndDefaultAliases() {
        Countly a = Countly.instance("instB");
        Assert.assertSame("same name must return the same object", a, Countly.instance("instB"));

        Countly def = Countly.sharedInstance();
        Assert.assertSame("null name is the default instance", def, Countly.instance(null));
        Assert.assertSame("empty name is the default instance", def, Countly.instance(""));
        Assert.assertSame("DEFAULT_NAME is the default instance", def, Countly.instance(Countly.DEFAULT_NAME));

        Assert.assertNotSame("a named instance is not the default", def, a);

        // halting resets state but keeps the object registered
        a.init(baseConfig(APP_KEY_B, DEVICE_B));
        a.halt();
        Assert.assertSame("instance identity survives halt()", a, Countly.instance("instB"));
    }

    /**
     * removeInstance halts a named instance AND drops it from the registry (unlike halt(), which keeps
     * it registered), so the object graph it retains becomes GC-eligible - the fix for the registry
     * growing without bound. The default instance can never be removed: it stays a stable object for
     * sharedInstance().
     */
    @Test
    public void removeInstance_deregistersAndHalts_defaultCannotBeRemoved() {
        Countly named = Countly.instance("instRemove");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));
        named.sessions().beginSession();
        Assert.assertTrue(named.isInitialized());
        Assert.assertSame("registered before removal", named, Countly.getInstance("instRemove"));
        Assert.assertTrue("listed before removal", Countly.listInstances().contains("instRemove"));
        Assert.assertEquals(1, TestUtils.getCurrentRQ(named).length);

        Countly.removeInstance("instRemove");

        // deregistered: getInstance no longer sees it and it drops out of the listing
        Assert.assertNull("getInstance must be null after removal", Countly.getInstance("instRemove"));
        Assert.assertFalse("must not be listed after removal", Countly.listInstances().contains("instRemove"));
        // the removed handle was stopped as part of removal
        Assert.assertFalse("removed instance must be stopped", named.isInitialized());

        // ...but its recorded data survives. Deregistering an instance must not throw away requests it
        // has not sent to the server yet.
        CountlyStore removedStore = store(CountlyStore.sanitizeNamespace("instRemove"));
        Map<String, String>[] rqAfterRemoval = TestUtils.getCurrentRQ("", removedStore);
        Assert.assertEquals("removeInstance must not discard unsent requests", 1,
            countRequestsWithKey(rqAfterRemoval, "begin_session"));
        // the session was open when the instance was removed, so it must have been closed on the way out -
        // otherwise the server keeps an open session and a later init of this name opens a second one
        Assert.assertEquals("removeInstance must end the session it leaves behind", 1,
            countRequestsWithKey(rqAfterRemoval, "end_session"));
        Assert.assertEquals("removeInstance must not discard the stored device id",
            DEVICE_B, removedStore.getDeviceID());

        // a later instance(name) creates a fresh, uninitialized object rather than the removed one
        Countly recreated = Countly.instance("instRemove");
        Assert.assertNotSame("instance(name) after removal must create a new object", named, recreated);
        Assert.assertFalse("recreated instance is uninitialized until init()", recreated.isInitialized());

        // and re-initialising that name resumes from the data that was left behind
        recreated.init(baseConfig(APP_KEY_B, DEVICE_B));
        Assert.assertEquals("re-initialising a removed name must resume from its kept queue",
            1, countRequestsWithKey(TestUtils.getCurrentRQ(recreated), "begin_session"));

        // an explicit halt() is still the way to erase an instance's data
        recreated.halt();
        Assert.assertEquals("halt() must still clear the instance's storage",
            0, TestUtils.getCurrentRQ("", store(CountlyStore.sanitizeNamespace("instRemove"))).length);

        // the default (shared) instance can not be removed: it must remain a stable object
        Countly def = Countly.sharedInstance();
        Countly.removeInstance(null);
        Countly.removeInstance(Countly.DEFAULT_NAME);
        Assert.assertSame("default instance survives removeInstance", def, Countly.sharedInstance());
    }

    /**
     * The storage-namespace sanitizer produces file-safe names, is deterministic, and does not let
     * two differently-spelled names collapse onto the same storage file.
     */
    @Test
    public void sanitizeNamespace_isFileSafeAndCollisionResistant() {
        Assert.assertEquals("", CountlyStore.sanitizeNamespace(null));
        Assert.assertEquals("", CountlyStore.sanitizeNamespace(""));

        String sanitized = CountlyStore.sanitizeNamespace("My App/Prod:1");
        // only file-safe characters survive
        Assert.assertTrue("sanitized namespace must be file-safe", sanitized.matches("[A-Za-z0-9_]+"));
        // deterministic
        Assert.assertEquals(sanitized, CountlyStore.sanitizeNamespace("My App/Prod:1"));

        // two names that sanitize to the same prefix must still differ (hash suffix disambiguates)
        Assert.assertNotEquals(CountlyStore.sanitizeNamespace("a.b"), CountlyStore.sanitizeNamespace("a-b"));
    }

    /**
     * Logging is per-instance: enabling logging on one instance does not enable it on another. This
     * exercises the ModuleLog decoupling from the singleton.
     */
    @Test
    public void perInstanceLogging_isIndependent() {
        // two named instances so the check does not depend on the heavily-shared default instance
        Countly loud = Countly.instance("instLoud");
        loud.init(baseConfig(APP_KEY_A, DEVICE_A).setLoggingEnabled(true));

        Countly quiet = Countly.instance("instLog");
        quiet.init(baseConfig(APP_KEY_B, DEVICE_B).setLoggingEnabled(false));

        Assert.assertTrue(loud.isLoggingEnabled());
        Assert.assertTrue(loud.L.loggingEnabled);
        Assert.assertFalse(quiet.isLoggingEnabled());
        Assert.assertFalse(quiet.L.loggingEnabled);

        // toggling one instance's logging leaves the other untouched
        quiet.setLoggingEnabled(true);
        Assert.assertTrue(quiet.L.loggingEnabled);
        Assert.assertTrue(loud.L.loggingEnabled);
    }

    /**
     * Timed events are stored per-instance: a timed event started on one instance is invisible to
     * another. This exercises the ModuleEvents.timedEvents static-to-instance conversion.
     */
    @Test
    public void timedEvents_areIsolatedPerInstance() {
        Countly one = Countly.sharedInstance();
        one.init(baseConfig(APP_KEY_A, DEVICE_A));

        Countly two = Countly.instance("instTimed");
        two.init(baseConfig(APP_KEY_B, DEVICE_B));

        Assert.assertTrue(one.events().startEvent("timer_one"));

        // the timed event lives only on the instance that started it
        Assert.assertEquals(1, one.moduleEvents.timedEvents.size());
        Assert.assertTrue(one.moduleEvents.timedEvents.containsKey("timer_one"));
        Assert.assertEquals(0, two.moduleEvents.timedEvents.size());
        Assert.assertFalse(two.moduleEvents.timedEvents.containsKey("timer_one"));

        // ending it on the other instance is a no-op; it stays owned by the first
        Assert.assertFalse(two.events().endEvent("timer_one"));
        Assert.assertEquals(1, one.moduleEvents.timedEvents.size());
    }

    /**
     * Push is owned by the default ("primary") instance and its preferences live in a single shared
     * file. Halting a named instance must not wipe that shared push state, while the default instance
     * still clears it on halt (legacy behavior).
     */
    @Test
    public void haltingNamedInstance_preservesPrimaryPushPrefs() {
        // primary sets push consent on the shared push preferences file
        store("").setConsentPush(true);
        Assert.assertTrue(store("").getConsentPush());

        // a named instance's lifecycle must not touch the shared push prefs
        Countly named = Countly.instance("instB");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));
        named.halt();
        Assert.assertTrue("named instance halt must not wipe primary push consent", store("").getConsentPush());

        // the default instance still owns and clears the shared push prefs on halt (backward compatible)
        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));
        def.halt();
        Assert.assertFalse("default instance halt clears the shared push prefs", store("").getConsentPush());
    }

    /**
     * The invariant the per-instance limits exist for: one instance's server behaviour settings must not
     * retruncate another instance's data, even when both were built from the SAME CountlyConfig.
     * <p>
     * Precedence is SERVER > STORED > PROVIDED > DEVELOPER, so the instance whose own store carries a limit
     * must honour it, while the instance whose store carries nothing must keep the developer's value. Before
     * the limits moved onto the instance, both read the config's nested limits object, so the first instance
     * to resolve its settings silently changed truncation for the second.
     */
    @Test
    public void internalLimits_serverSettingsOfOneInstanceDoNotRetruncateAnother() throws JSONException {
        //only instB's own namespaced store carries a server behaviour setting, and it lowers the key length
        String storedSbs = new JSONObject()
            .put("t", 1L)
            .put("v", 1)
            .put("c", new JSONObject().put("lkl", 5))
            .toString();
        store(CountlyStore.sanitizeNamespace("instB")).setServerConfig(storedSbs);

        CountlyConfig shared = baseConfig(APP_KEY_A, DEVICE_A);
        shared.sdkInternalLimits.setMaxKeyLength(40);

        Countly withServerLimit = Countly.instance("instB");
        withServerLimit.init(shared);

        Countly withoutServerLimit = Countly.instance("instFresh");
        withoutServerLimit.init(shared);

        Assert.assertEquals("the instance whose stored settings lower the limit must use the server value",
            Integer.valueOf(5), withServerLimit.sdkInternalLimits_.maxKeyLength);
        Assert.assertEquals("the other instance must keep the developer's limit, not inherit the server's",
            Integer.valueOf(40), withoutServerLimit.sdkInternalLimits_.maxKeyLength);
        Assert.assertEquals("and the developer's own config must be left untouched",
            Integer.valueOf(40), shared.sdkInternalLimits.maxKeyLength);
    }

    /**
     * The registry management API. instance(name) creates/accesses a handle but never initializes it
     * (users init themselves); getInstance never creates; listInstances reports every registered instance
     * including the default; haltAllInstances halts every instance while keeping identities registered.
     */
    @Test
    public void registryApi_accessIsLazyAndUninitialized_listAndHaltAll() {
        // getInstance never creates - null until the name is registered
        Assert.assertNull(Countly.getInstance("instCreateA"));

        // instance(name) creates the handle but does NOT auto-initialize it
        Countly handle = Countly.instance("instCreateA");
        Assert.assertNotNull(handle);
        Assert.assertFalse("instance(name) must not auto-initialize", handle.isInitialized());
        Assert.assertSame("instance(name) is just an accessor - same object each call", handle, Countly.getInstance("instCreateA"));

        // the user initializes it explicitly; storage is isolated under the (sanitized) name
        handle.init(baseConfig(APP_KEY_B, DEVICE_B));
        Assert.assertTrue(handle.isInitialized());
        Assert.assertEquals(CountlyStore.sanitizeNamespace("instCreateA"), handle.storageNamespace_);

        // using the app key as the instance name is the natural per-app-key isolation
        Countly byAppKey = Countly.instance("instCreateB");
        byAppKey.init(baseConfig("instCreateB", DEVICE_A));
        Assert.assertEquals(CountlyStore.sanitizeNamespace("instCreateB"), byAppKey.storageNamespace_);

        // listInstances reports every registered instance, the default included under its reserved name
        List<String> names = Countly.listInstances();
        Assert.assertTrue(names.contains("instCreateA"));
        Assert.assertTrue(names.contains("instCreateB"));
        Assert.assertTrue("the default instance is registered and must be listed too", names.contains(Countly.DEFAULT_NAME));

        // haltAllInstances halts every instance while keeping their identities registered
        Countly.haltAllInstances();
        Assert.assertFalse(handle.isInitialized());
        Assert.assertFalse(byAppKey.isInitialized());
        Assert.assertSame(handle, Countly.instance("instCreateA"));
    }

    /**
     * Data preservation on upgrade: an app coming from a previous single-instance SDK version already
     * has data in the legacy (un-namespaced) store. Initializing the default instance must reuse that
     * existing device ID and queued requests, never wipe or re-namespace them.
     */
    @Test
    public void defaultInstance_readsPreExistingLegacyData_noDataLoss() {
        // seed the legacy store the way an older SDK version would have left it
        CountlyStore legacy = store("");
        legacy.setDeviceID("legacy_device");
        legacy.addRequest("app_key=" + APP_KEY_A + "&device_id=legacy_device&legacy_marker=1", false);

        // initialize the default instance with NO device id in config, so the stored one must be kept
        Countly def = Countly.sharedInstance();
        def.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_A, TestUtils.commonURL).setLoggingEnabled(true));

        // the default instance uses the legacy files (empty namespace) ...
        Assert.assertEquals("", def.storageNamespace_);
        // ... so the pre-existing device id and queued request are still there after init
        Assert.assertEquals("legacy_device", store("").getDeviceID());
        Assert.assertNotNull("a pre-existing queued request must survive init", firstRequestWithKey(TestUtils.getCurrentRQ(def), "legacy_marker"));
    }

    /**
     * SSL certificate/public-key pinning material is held per-instance on each instance's own
     * ConnectionQueue, not in a shared static. A pinned named instance must not leak its pins into an
     * unpinned instance (the "last-init-wins" static hazard the refactor removed).
     */
    @Test
    public void sslPinning_isIsolatedPerInstance() {
        String[] pins = { "pin-for-named-instance" };
        // A no-op custom socket factory is supplied only so the placeholder pin is not eagerly parsed
        // into a TrustManager; we are asserting the pinning material is stored per-ConnectionQueue.
        javax.net.ssl.SSLSocketFactory noopFactory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();

        Countly pinned = Countly.instance("instB");
        pinned.init(baseConfig(APP_KEY_B, DEVICE_B)            .enablePublicKeyPinning(pins).setCustomSSLSocketFactory(noopFactory));

        Countly plain = Countly.sharedInstance();
        plain.init(baseConfig(APP_KEY_A, DEVICE_A));

        // each ConnectionQueue holds only its own pinning material - no cross-instance static leakage
        Assert.assertArrayEquals(pins, pinned.connectionQueue_.publicKeyPinCertificates);
        Assert.assertNull("the unpinned instance must not inherit another instance's pins", plain.connectionQueue_.publicKeyPinCertificates);
    }

    /**
     * When no device id is supplied, each instance generates its own OpenUDID in its own namespaced
     * file, so two instances never collapse onto one shared generated device id (which would silently
     * merge two apps' analytics). The default keeps the legacy openudid_prefs file.
     */
    @Test
    public void generatedOpenUdidDeviceId_isIsolatedPerInstance() {
        // start from clean OpenUDID files so both instances must generate fresh, independent ids
        clearOpenUdid("");
        clearOpenUdid(CountlyStore.sanitizeNamespace("instB"));

        // neither config supplies a device id -> the OpenUDID (generated) path is exercised
        Countly def = Countly.sharedInstance();
        def.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_A, TestUtils.commonURL).setLoggingEnabled(true));
        Countly named = Countly.instance("instB");
        named.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_B, TestUtils.commonURL).setLoggingEnabled(true));

        String defId = TestUtils.getCountlyStore(def).getDeviceID();
        String namedId = TestUtils.getCountlyStore(named).getDeviceID();
        Assert.assertNotNull(defId);
        Assert.assertNotNull(namedId);
        Assert.assertNotEquals("each instance must generate its own device id, not share one", defId, namedId);

        // the generated OpenUDID lives in each instance's own file (default -> legacy, named -> suffixed)
        String defOpenUdid = openUdid("");
        String namedOpenUdid = openUdid(CountlyStore.sanitizeNamespace("instB"));
        Assert.assertNotNull(defOpenUdid);
        Assert.assertNotNull(namedOpenUdid);
        Assert.assertNotEquals("named instance's OpenUDID must be isolated from the default's", defOpenUdid, namedOpenUdid);
    }

    private static int countRequestsWithKey(Map<String, String>[] rq, String key) {
        int count = 0;
        for (Map<String, String> request : rq) {
            if (request != null && request.containsKey(key)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether an instance recorded the given event, looking in both places it can be: its event queue,
     * and - once the queue has been drained into a request, which init does immediately - the "events"
     * parameter of one of its queued requests. The request-side match drops the "[CLY]" prefix so it
     * holds whether or not the parameter value is URL-encoded.
     */
    private static boolean recordedEvent(Countly countly, String eventKey) {
        for (Event event : TestUtils.getCountlyStore(countly).getEventList()) {
            if (eventKey.equals(event.key)) {
                return true;
            }
        }

        String unencodedPartOfKey = eventKey.replace("[CLY]", "");
        for (Map<String, String> request : TestUtils.getCurrentRQ(countly)) {
            String events = request == null ? null : request.get("events");
            if (events != null && events.contains(unencodedPartOfKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The same config-caching hazard within one instance: halt() throws away the instance's
     * ConnectionQueue and builds a new one, so a reused config that still cached the old one as
     * requestQueueProvider would leave the re-initialised instance writing through a torn-down queue.
     * init() must rebuild whatever it derived itself, not just when the namespace changes.
     */
    @Test
    public void reusedConfigObject_isRebuiltWhenTheSameInstanceIsReinitialised() {
        CountlyConfig shared = baseConfig(APP_KEY_B, DEVICE_B);

        Countly named = Countly.instance("instB");
        named.init(shared);
        RequestQueueProvider firstQueue = shared.requestQueueProvider;
        Assert.assertNotNull(firstQueue);

        named.halt();
        named.init(shared);

        Assert.assertNotSame("a re-initialised instance must not keep the ConnectionQueue halt() discarded",
            firstQueue, shared.requestQueueProvider);

        // and it still works end to end, writing into its own namespaced store
        named.sessions().beginSession();
        Map<String, String>[] rq = TestUtils.getCurrentRQ(named);
        Assert.assertNotNull("the re-initialised instance must record through its live queue", firstRequestWithKey(rq, "begin_session"));
        assertAllRequestsCarryAppKey(rq, APP_KEY_B);
    }

    /**
     * One CountlyConfig may initialise several instances. What used to make that unsafe was the SDK writing
     * its own resolved state back onto the config object, so this asserts the three things that had to be
     * true before sharing could be allowed: the second instance really initialises, the two instances do not
     * share the internal limits they read on every recorded event, and a value the developer changes between
     * the two inits is honoured rather than reset to what the first init saw.
     */
    @Test
    public void sharedConfigObject_secondInstanceInitialisesWithIsolatedState() {
        CountlyConfig shared = baseConfig(APP_KEY_A, DEVICE_A)
            .setRequiresConsent(true)
            .setConsentEnabled(new String[] { Countly.CountlyFeatureNames.sessions });
        shared.sdkInternalLimits.setMaxKeyLength(40);

        Countly def = Countly.sharedInstance();
        def.init(shared);
        Assert.assertTrue("the first instance initialises normally", def.isInitialized());
        def.sessions().beginSession();

        // the developer changes their mind about the device id before building the second instance
        shared.setDeviceId(DEVICE_B);

        Countly named = Countly.instance("instCfgA");
        named.init(shared);

        Assert.assertTrue("a second instance must initialise from a shared config", named.isInitialized());
        Assert.assertEquals(CountlyStore.sanitizeNamespace("instCfgA"), named.storageNamespace_);

        // the change made between the two inits wins - it must not be reset to what the first init saw
        Assert.assertEquals("the device id the developer set before the second init must be used",
            DEVICE_B, store(CountlyStore.sanitizeNamespace("instCfgA")).getDeviceID());
        Assert.assertEquals("the first instance keeps its own device id", DEVICE_A, store("").getDeviceID());

        // limits are per instance, so one instance's resolved limits can not retruncate the other's data
        Assert.assertNotSame("instances must not share the limits object they read on every event",
            def.sdkInternalLimits_, named.sdkInternalLimits_);
        named.sdkInternalLimits_.setMaxKeyLength(7);
        Assert.assertEquals("changing one instance's limits must not touch the other's",
            Integer.valueOf(40), def.sdkInternalLimits_.maxKeyLength);

        // and the first instance is otherwise untouched by the second init
        Assert.assertTrue(def.isInitialized());
        Assert.assertEquals(APP_KEY_A, def.moduleRequestQueue.baseInfoProvider.getAppKey());
        Map<String, String>[] rqDefault = TestUtils.getCurrentRQ(def);
        Assert.assertEquals(1, countRequestsWithKey(rqDefault, "begin_session"));
        assertAllRequestsCarryAppKey(rqDefault, APP_KEY_A);
        Assert.assertTrue("the first instance keeps the consent requirement it was configured with",
            def.moduleConsent.requiresConsent);
        Assert.assertTrue("the second instance inherits the consent requirement the config carries",
            named.moduleConsent.requiresConsent);
    }

    /**
     * Regression: setConsentPush writes into the process-shared, primary-owned push preferences file.
     * ModuleConsent calls it on every init (doPushConsentSpecialAction(true) when consent is not
     * required), so before the ownership gate merely creating a named instance re-granted the primary
     * instance's persisted push consent - the value getConsentPushNoInit reads before init.
     */
    @Test
    public void namedInstance_cannotOverwritePrimaryPushConsent() {
        // a named-namespace store must not be able to write the shared push consent at all
        store("").setConsentPush(true);
        store(CountlyStore.sanitizeNamespace("instB")).setConsentPush(false);
        Assert.assertTrue("a named instance's store must not overwrite the shared push consent",
            store("").getConsentPush());

        // nor may initialising a named instance flip it - the primary has revoked push consent here
        store("").setConsentPush(false);
        Countly named = Countly.instance("instB");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));
        Assert.assertFalse("initialising a named instance must not re-grant the primary's push consent",
            store("").getConsentPush());

        // the primary instance still owns the flag and its own init stores the default consent
        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));
        Assert.assertTrue("the default instance still owns and writes the shared push consent",
            store("").getConsentPush());
    }

    /**
     * Regression: the cached push click lives in the shared, primary-owned push file, and
     * ModuleEvents.initFinished reads AND clears it on every instance. Whichever instance initialised
     * first therefore recorded another instance's push action under its own app key, then deleted it.
     */
    @Test
    public void namedInstance_doesNotConsumePrimaryCachedPushClick() {
        CountlyStore.cachePushData("msgIdA", "0", TestUtils.getContext());

        // a named instance initialising first must neither record nor drain the primary's push click
        Countly named = Countly.instance("instB");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));

        Assert.assertFalse("a named instance must not record the primary instance's push click",
            recordedEvent(named, ModulePush.PUSH_EVENT_ACTION));

        String[] stillCached = store("").getCachedPushData();
        Assert.assertEquals("the primary's cached push click must survive a named instance's init", "msgIdA", stillCached[0]);
        Assert.assertEquals("0", stillCached[1]);

        // the owning (default) instance still consumes it, and clears it afterwards
        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));

        Assert.assertTrue("the default instance must record its own cached push click",
            recordedEvent(def, ModulePush.PUSH_EVENT_ACTION));
        Assert.assertNull("the owner clears the cached push click once recorded", store("").getCachedPushData()[0]);
    }

    /**
     * Regression: with cached push data present, anythingSetInStorage() makes a brand-new DEFAULT store
     * look like a legacy install, so performMigration0To1 ran against empty storage - and its both-null
     * branch hardcoded OPEN_UDID, generating a UUID that permanently replaced the developer's device ID.
     */
    @Test
    public void defaultInstance_keepsSuppliedDeviceId_whenSharedPushDataMakesStoreLookLegacy() {
        // The realistic trigger: CountlyPush.init() stores the messaging provider with no
        // isInitialized() guard, so an app that inits push before Countly writes this on a fresh
        // install. A push click cached pre-init does the same.
        CountlyStore.storeMessagingProvider(1, TestUtils.getContext());
        CountlyStore.cachePushData("msgIdA", "0", TestUtils.getContext());

        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));

        Assert.assertEquals("a developer-supplied device ID must survive the legacy migration path",
            DEVICE_A, TestUtils.getCountlyStore(def).getDeviceID());
        Assert.assertEquals("DEVELOPER_SUPPLIED", TestUtils.getCountlyStore(def).getDeviceIDType());

        // and it is the ID that actually goes on the wire
        def.sessions().beginSession();
        Map<String, String> begin = firstRequestWithKey(TestUtils.getCurrentRQ(def), "begin_session");
        Assert.assertNotNull(begin);
        Assert.assertEquals(DEVICE_A, begin.get("device_id"));
    }

    /**
     * Deliberate policy: without crash consent the dump is dropped rather than cached for a later run.
     * Retaining it would need its own retention policy, and a minidump is raw process memory we do not
     * want left on the device waiting for a consent that may never come.
     */
    @Test
    public void nativeCrashDump_isDroppedWhenThereIsNoCrashConsent() throws IOException {
        File dump = writeFakeNativeDump("dump_no_consent");

        Countly def = Countly.sharedInstance();
        //consent required but none granted -> the dump can not be reported
        def.init(baseConfig(APP_KEY_A, DEVICE_A).setRequiresConsent(true));

        Assert.assertNull("a dump must not be reported without crash consent",
            firstRequestWithKey(TestUtils.getCurrentRQ(def), "crash"));
        Assert.assertFalse("a dump that can not be reported must be removed, not cached", dump.exists());
    }

    /**
     * Regression: sdk-native writes minidumps into one fixed process-wide directory, and
     * checkForNativeCrashDumps ran on every instance, reading AND deleting them. Whichever instance
     * initialised first uploaded every dump - raw process memory - under its own app key and server URL.
     */
    @Test
    public void namedInstance_doesNotConsumePrimaryNativeCrashDumps() throws IOException {
        File dump = writeFakeNativeDump("dump_a");

        Countly named = Countly.instance("instB");
        named.init(baseConfig(APP_KEY_B, DEVICE_B));

        Assert.assertTrue("a named instance must not delete the process-wide native crash dump", dump.exists());
        Assert.assertNull("a named instance must not report the process-wide native crash dump",
            firstRequestWithKey(TestUtils.getCurrentRQ(named), "crash"));

        // the owning (default) instance still consumes and reports it, under its own app key
        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));

        Map<String, String> crashRequest = firstRequestWithKey(TestUtils.getCurrentRQ(def), "crash");
        Assert.assertNotNull("the default instance must still report the native crash dump", crashRequest);
        Assert.assertEquals(APP_KEY_A, crashRequest.get("app_key"));
        Assert.assertFalse("the owner consumes the dump once reported", dump.exists());
    }

    /**
     * Regression: ModuleCrash.halt() was empty, so the uncaught-exception handler it installed stayed
     * the process default forever, holding the whole instance graph. A halted or removed instance was
     * therefore never collectable (contradicting what removeInstance documents) and kept recording
     * crashes into its own torn-down queues. halt() must unlink it from the global handler chain.
     */
    @Test
    public void haltingInstance_restoresTheUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Countly named = Countly.instance("instB");
            CountlyConfig config = baseConfig(APP_KEY_B, DEVICE_B);
            config.crashes.enableCrashReporting();
            named.init(config);

            Assert.assertNotSame("enabling crash reporting must install a handler",
                original, Thread.getDefaultUncaughtExceptionHandler());

            named.halt();

            Assert.assertSame("halt must restore the handler it wrapped, so the instance stops being reachable from the process-global chain",
                original, Thread.getDefaultUncaughtExceptionHandler());
        } finally {
            //never leak a test handler into the rest of the suite
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }

    /**
     * Regression: feedback widget parsing logged through Countly.sharedInstance().L. Because ModuleLog
     * now carries a per-instance log listener, that delivered one instance's widget metadata (ids,
     * types, tags) to the DEFAULT instance's listener. Diagnostics must follow the instance that owns
     * the data.
     */
    @Test
    public void feedbackWidgetParsing_reportsToTheOwningInstancesLogger() throws JSONException {
        final List<String> namedLog = new CopyOnWriteArrayList<>();
        ModuleLog namedLogger = new ModuleLog();
        namedLogger.SetListener((logMessage, logLevel) -> namedLog.add(logMessage));

        // A listener on the DEFAULT instance's logger. That instance is deliberately left uninitialised:
        // an initialised one logs from background threads, which would make the assertion below racy.
        final List<String> defaultLog = new CopyOnWriteArrayList<>();
        Countly.sharedInstance().L.SetListener((logMessage, logLevel) -> defaultLog.add(logMessage));

        // an entry with an empty widget id makes parseFeedbackList emit a diagnostic about it
        JSONObject response = new JSONObject();
        response.put("result", new JSONArray().put(new JSONObject().put("_id", "").put("type", "nps")));
        ModuleFeedback.parseFeedbackList(response, namedLogger);

        boolean reachedOwnLogger = false;
        for (String message : namedLog) {
            if (message.contains("parseFeedbackList")) {
                reachedOwnLogger = true;
                break;
            }
        }
        Assert.assertTrue("widget parsing diagnostics must reach the logger they were given", reachedOwnLogger);

        for (String message : defaultLog) {
            Assert.assertFalse("another instance's widget parsing must never reach the default instance's log listener",
                message.contains("parseFeedbackList"));
        }
    }

    /**
     * Custom network headers were taken from the config by reference, and the runtime setter mutates
     * that same map in place. Two instances configured from one map therefore shared it, so adding an
     * Authorization header to the instance talking to server A also sent that credential to server B.
     */
    //The HashMaps below are what a developer realistically passes in; making them concurrent would test
    //something no caller does. They are local to one test thread and never shared.
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    @Test
    public void customNetworkRequestHeaders_areNotSharedBetweenInstances() {
        Map<String, String> developerMap = new HashMap<>();
        developerMap.put("X-Env", "prod");

        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A).addCustomNetworkRequestHeaders(developerMap));

        Countly named = Countly.instance("instCfgA");
        named.init(baseConfig(APP_KEY_B, DEVICE_B).addCustomNetworkRequestHeaders(developerMap));

        // both start from the same configured headers
        Assert.assertEquals("prod", def.requestHeaderCustomValues.get("X-Env"));
        Assert.assertEquals("prod", named.requestHeaderCustomValues.get("X-Env"));

        Map<String, String> credential = new HashMap<>();
        credential.put("Authorization", "Bearer tenant-a-token");
        def.requestQueue().addCustomNetworkRequestHeaders(credential);

        Assert.assertEquals("Bearer tenant-a-token", def.requestHeaderCustomValues.get("Authorization"));
        Assert.assertFalse("one instance's credentials must never reach another instance's requests",
            named.requestHeaderCustomValues.containsKey("Authorization"));
        Assert.assertFalse("the SDK must not mutate the map the developer handed to the config",
            developerMap.containsKey("Authorization"));
    }

    /**
     * onRegistrationId is driven by an OS push callback that can arrive before init or after halt. It
     * used to dereference the config unconditionally, so those arrivals crashed the host app inside a
     * push callback. It must log and ignore instead.
     */
    @Test
    public void onRegistrationId_beforeInitAndAfterHalt_isIgnoredInsteadOfCrashing() {
        Countly def = Countly.sharedInstance();
        Assert.assertFalse(def.isInitialized());

        // before any init there is no config and no queue, so this used to throw straight into the push
        // callback that called it
        def.onRegistrationId("token-before-init", Countly.CountlyMessagingProvider.FCM);
        Assert.assertNull("a token arriving before init must be ignored, not accepted", def.lastRegistrationCallID);
        Assert.assertEquals("an ignored token must not queue a request", 0, TestUtils.getCurrentRQ("", store("")).length);

        def.init(baseConfig(APP_KEY_A, DEVICE_A));
        def.halt();
        Assert.assertFalse(def.isInitialized());

        // after halt the config object lingers but the connection queue is gone
        def.onRegistrationId("token-after-halt", Countly.CountlyMessagingProvider.FCM);
        Assert.assertNull("a token arriving after halt must be ignored too", def.lastRegistrationCallID);

        // and an initialised instance still accepts the token. The request itself is queued only after a
        // ten second delay, so assert on the accepted-call state rather than waiting for the queue.
        def.init(baseConfig(APP_KEY_A, DEVICE_A));
        def.onRegistrationId("real-token", Countly.CountlyMessagingProvider.FCM);
        Assert.assertEquals("an initialised instance must accept the push token", "real-token", def.lastRegistrationCallID);
    }

    /**
     * The uncaught-exception chain runs developer code (crash filters) on a thread that is already
     * crashing. A filter that throws must not swallow the delegation: every handler below this
     * instance - other Countly instances and ultimately Android's own KillApplicationHandler - still
     * has to run, otherwise the crash is reported by nobody, no crash dialog shows, and the process
     * is left alive with a dead thread.
     */
    @Test
    public void throwingCrashFilter_doesNotSwallowDownstreamHandlers() {
        Thread.UncaughtExceptionHandler originalDefault = Thread.getDefaultUncaughtExceptionHandler();
        try {
            final boolean[] previousHandlerRan = { false };
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> previousHandlerRan[0] = true);

            Countly named = Countly.instance("instCfgB");
            CountlyConfig config = baseConfig(APP_KEY_B, DEVICE_B);
            config.crashes.enableCrashReporting();
            config.crashes.setGlobalCrashFilterCallback(crash -> {
                throw new IllegalStateException("filter deliberately failing");
            });
            named.init(config);

            Thread.UncaughtExceptionHandler chainHead = Thread.getDefaultUncaughtExceptionHandler();
            Assert.assertTrue("the instance must have installed its crash handler", named.moduleCrash.unhandledCrashHandlerInstalled);

            // simulate the runtime dispatching an uncaught exception into the chain
            chainHead.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

            Assert.assertTrue("a throwing crash filter must not stop delegation to the previous handler", previousHandlerRan[0]);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalDefault);
        }
    }

    /**
     * halt() promises "destroys all stored data ... the next session starts as a new user". The
     * generated device id used to survive the wipe in the separate OpenUDID cache file, so a
     * halted-and-reinitialised instance silently came back as the same user. The wipe must cover the
     * OpenUDID cache too.
     */
    @Test
    public void halt_clearsGeneratedOpenUdid_nextInitIsANewUser() {
        // no device id supplied -> the generated (OpenUDID) path is exercised
        Countly def = Countly.sharedInstance();
        def.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_A, TestUtils.commonURL).setLoggingEnabled(true));
        String firstId = TestUtils.getCountlyStore(def).getDeviceID();
        Assert.assertNotNull(firstId);
        Assert.assertEquals("the generated id and the OpenUDID cache must agree", firstId, openUdid(""));

        def.halt();
        Assert.assertNull("halt must wipe the OpenUDID cache file too", openUdid(""));

        def.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_A, TestUtils.commonURL).setLoggingEnabled(true));
        String secondId = TestUtils.getCountlyStore(def).getDeviceID();
        Assert.assertNotNull(secondId);
        Assert.assertNotEquals("after halt the next init must start as a new user, not resurrect the old id", firstId, secondId);
    }

    /**
     * A halted instance must stop receiving process lifecycle events entirely: teardown removes it
     * from the process-wide CountlyLifecycleDispatcher before nulling anything, and the tearingDown
     * gate drops an event already in flight. Before the dispatcher, this was the CME/NPE window
     * between the main thread's dispatch and a background teardown.
     */
    @Test
    public void haltedInstance_stopsReceivingActivityLifecycle() {
        Application app = (Application) TestUtils.getContext().getApplicationContext();
        Countly instance = Countly.instance("instCfgA");
        // no manual session control: lifecycle events must drive the session automatically
        instance.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_A, TestUtils.commonURL)
            .setDeviceId(DEVICE_A)
            .setLoggingEnabled(true)
            .setApplication(app));

        String ns = CountlyStore.sanitizeNamespace("instCfgA");
        Activity activity = mock(Activity.class);

        // simulate the OS starting an activity: the registered instance auto-begins a session
        CountlyLifecycleDispatcher.getInstance().onActivityStarted(activity);
        Assert.assertNotNull("a registered instance must receive lifecycle events and begin a session",
            firstRequestWithKey(TestUtils.getCurrentRQ("", store(ns)), "begin_session"));

        instance.halt(); // clears storage and deregisters from the dispatcher

        CountlyLifecycleDispatcher.getInstance().onActivityStarted(activity);
        CountlyLifecycleDispatcher.getInstance().onActivityStopped(activity);
        Assert.assertEquals("a halted instance must not receive lifecycle events any more",
            0, store(ns).getRequests().length);
    }

    /**
     * The exact-count contract the foreground seed relies on: registered by CountlyInitProvider before
     * any activity can start, the dispatcher counts starts and stops exactly, never underflows, and
     * reports exactness so a late (provider-stripped) registration falls back to the heuristic.
     */
    @Test
    public void lifecycleDispatcher_exactCountContract() {
        CountlyLifecycleDispatcher dispatcher = CountlyLifecycleDispatcher.getInstance();
        Assert.assertTrue("registered by the provider before any activity, the count must be exact",
            dispatcher.hasExactActivityCount());
        Assert.assertEquals(0, dispatcher.getStartedActivityCount());

        Activity activity = mock(Activity.class);
        dispatcher.onActivityStarted(activity);
        dispatcher.onActivityStarted(activity);
        Assert.assertEquals(2, dispatcher.getStartedActivityCount());
        dispatcher.onActivityStopped(activity);
        Assert.assertEquals(1, dispatcher.getStartedActivityCount());
        dispatcher.onActivityStopped(activity);
        dispatcher.onActivityStopped(activity); // an extra stop must not underflow the count
        Assert.assertEquals(0, dispatcher.getStartedActivityCount());
    }

    /**
     * Foreground-at-init comes from the dispatcher's exact started-activity count (registered by
     * CountlyInitProvider before any activity can start), not from ProcessLifecycleOwner's debounced
     * state: an instance initialised while an activity is started seeds its activity counter exactly
     * and auto-begins its session immediately - deterministically, with no ~700ms debounce window.
     */
    @Test
    public void initWhileActivityStarted_seedsExactForegroundAndBeginsSession() {
        // the test runner pins the foreground override to "background" for isolation; this test is
        // specifically about the exact-count path, which the override outranks - clear it (the runner
        // restores it after the test)
        Countly.lifecycleStateOverrideForTests = null;

        CountlyLifecycleDispatcher.getInstance().onActivityStarted(mock(Activity.class)); // the app is now "in the foreground"

        Application app = (Application) TestUtils.getContext().getApplicationContext();
        Countly instance = Countly.instance("instCfgB");
        instance.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_B, TestUtils.commonURL)
            .setDeviceId(DEVICE_B)
            .setLoggingEnabled(true)
            .setApplication(app));

        String ns = CountlyStore.sanitizeNamespace("instCfgB");
        Assert.assertNotNull("an init while an activity is started must seed foreground from the exact count and begin a session",
            firstRequestWithKey(TestUtils.getCurrentRQ("", store(ns)), "begin_session"));
    }

    /**
     * Two CountlyStore objects can be live over one namespace: removeInstance() keeps the data and
     * documents the name as immediately reusable, while the removed instance's ConnectionProcessor
     * may still be draining the kept queue on its non-awaited executor. The queue mutation is a
     * read-modify-write of one joined string, synchronized per store OBJECT, so without a shared
     * file-level monitor two stores lose each other's updates. Hammer one file from two stores on two
     * threads and require that not a single request is lost or duplicated.
     */
    @Test
    public void concurrentStoresOverOneNamespace_loseNoRequests() throws InterruptedException {
        final String ns = CountlyStore.sanitizeNamespace("instB");
        final CountlyStore first = store(ns);
        final CountlyStore second = store(ns);
        final int perWriter = 40;

        Thread writerA = new Thread(() -> {
            for (int i = 0; i < perWriter; i++) {
                first.addRequest("a_" + i, false);
            }
        });
        Thread writerB = new Thread(() -> {
            for (int i = 0; i < perWriter; i++) {
                second.addRequest("b_" + i, false);
            }
        });
        writerA.start();
        writerB.start();
        writerA.join();
        writerB.join();

        List<String> finalQueue = new ArrayList<>(Arrays.asList(store(ns).getRequests()));
        for (int i = 0; i < perWriter; i++) {
            Assert.assertTrue("request a_" + i + " was lost by a concurrent writer", finalQueue.contains("a_" + i));
            Assert.assertTrue("request b_" + i + " was lost by a concurrent writer", finalQueue.contains("b_" + i));
        }
        Assert.assertEquals("no request may be duplicated either", perWriter * 2, finalQueue.size());
    }
}
