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

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
    private static final String[] NAMES = { "instB", "instLog", "instLoud", "instTimed", "instFile", "instCreateA", "instCreateB", "ignoredName" };

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
        named.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instB"));

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
        named.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instFile"));
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
        a.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instB"));
        a.halt();
        Assert.assertSame("instance identity survives halt()", a, Countly.instance("instB"));
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
        loud.init(baseConfig(APP_KEY_A, DEVICE_A).setInstanceName("instLoud").setLoggingEnabled(true));

        Countly quiet = Countly.instance("instLog");
        quiet.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instLog").setLoggingEnabled(false));

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
        two.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instTimed"));

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
        named.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instB"));
        named.halt();
        Assert.assertTrue("named instance halt must not wipe primary push consent", store("").getConsentPush());

        // the default instance still owns and clears the shared push prefs on halt (backward compatible)
        Countly def = Countly.sharedInstance();
        def.init(baseConfig(APP_KEY_A, DEVICE_A));
        def.halt();
        Assert.assertFalse("default instance halt clears the shared push prefs", store("").getConsentPush());
    }

    /**
     * The registry management API. instance(name) creates/accesses a handle but never initializes it
     * (users init themselves); getInstance never creates; listInstances reports named instances only;
     * haltAllInstances halts every instance while keeping identities registered.
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
        handle.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instCreateA"));
        Assert.assertTrue(handle.isInitialized());
        Assert.assertEquals(CountlyStore.sanitizeNamespace("instCreateA"), handle.storageNamespace_);

        // using the app key as the instance name is the natural per-app-key isolation
        Countly byAppKey = Countly.instance("instCreateB");
        byAppKey.init(baseConfig("instCreateB", DEVICE_A));
        Assert.assertEquals(CountlyStore.sanitizeNamespace("instCreateB"), byAppKey.storageNamespace_);

        // listInstances reports the named instances but never the default
        java.util.List<String> names = Countly.listInstances();
        Assert.assertTrue(names.contains("instCreateA"));
        Assert.assertTrue(names.contains("instCreateB"));
        Assert.assertFalse(names.contains(Countly.DEFAULT_NAME));

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
        pinned.init(baseConfig(APP_KEY_B, DEVICE_B).setInstanceName("instB")
            .enablePublicKeyPinning(pins).setCustomSSLSocketFactory(noopFactory));

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
        named.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_B, TestUtils.commonURL).setInstanceName("instB").setLoggingEnabled(true));

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

    /**
     * setInstanceName is advisory: setting it on the default instance (obtained via sharedInstance())
     * does NOT create a named instance or namespace storage, and the SDK warns loudly rather than
     * silently landing data in the wrong place.
     */
    @Test
    public void setInstanceNameOnDefaultInstance_isIgnoredButWarned() {
        final List<String> warnings = new ArrayList<>();
        // A standalone default-named instance (instanceName_ == DEFAULT_NAME, exactly as
        // sharedInstance() is) exercises the same "config names the default instance" path, while
        // keeping the log-listener assertion independent of other tests mutating the shared default.
        Countly def = new Countly();
        def.init(new CountlyConfig(TestUtils.getContext(), APP_KEY_A, TestUtils.commonURL)
            .setDeviceId(DEVICE_A)
            .setInstanceName("ignoredName")
            .setLoggingEnabled(true)
            .setLogListener((logMessage, logLevel) -> {
                if (logLevel == ModuleLog.LogLevel.Warning) {
                    warnings.add(logMessage);
                }
            }));

        // the default instance ignores the config name: legacy storage, not registered under the name
        Assert.assertEquals("", def.storageNamespace_);
        Assert.assertNull("setInstanceName on the default instance must not register a named instance", Countly.getInstance("ignoredName"));

        // and it warns loudly instead of failing silently
        boolean warned = false;
        for (String w : warnings) {
            if (w.contains("ignoredName")) {
                warned = true;
                break;
            }
        }
        Assert.assertTrue("a warning must be logged when setInstanceName is set on the default instance", warned);
    }
}
