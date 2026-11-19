package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * In each request scenario the SDK queues the requests sdk/api/requests/[scenario].json records, with the values that
 * change from run to run masked. The scenarios make the calls the other Countly SDKs make in their own request tests.
 * Queued requests go to /i, which is the path each record carries. A deliberate change: run the connected tests, then
 * ./gradlew :sdk:requestsUpdate, which writes the records the failing tests printed, check the difference and add a line
 * to CHANGELOG.md.
 */
@RunWith(AndroidJUnit4.class)
public class scRQ_RequestScenarioTests {

    private static final String MASK = "<masked>";
    private static final List<String> MASKED_PARAMS = Arrays.asList("timestamp", "hour", "dow", "tz", "sdk_version", "av", "rr", "checksum", "checksum256", "session_duration");
    private static final List<String> JSON_PARAMS = Arrays.asList("events", "metrics", "user_details", "crash", "consent", "hc");
    private static final List<String> MASKED_EVENT_FIELDS = Arrays.asList("timestamp", "hour", "dow", "id", "pvid", "cvid", "peid");
    private static final List<String> MASKED_CRASH_FIELDS = Arrays.asList("_error", "_run", "_os", "_os_version", "_resolution", "_density", "_locale", "_device", "_device_type", "_manufacturer", "_cpu", "_opengl", "_ram_current", "_ram_total", "_disk_current", "_disk_total", "_bat", "_orientation", "_root", "_online", "_muted", "_background");
    private static final int MAX_INT = 2147483647;

    /**
     * The calls of a scenario.
     */
    private interface Scenario {
        /**
         * Makes the calls of the scenario.
         *
         * @param countly the initialized SDK
         * @throws InterruptedException when waiting for the SDK is interrupted
         */
        void run(Countly countly) throws InterruptedException;
    }

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

    @Test
    public void parity() throws Exception {
        assertRequests("parity", config(), countly -> {
            countly.sessions().beginSession();
            countly.events().recordEvent("purchase", paritySegmentation(), 2, 9.99, 5);
            Map<String, Object> properties = new HashMap<>();
            properties.put("name", "John Doe");
            properties.put("username", "john_d");
            properties.put("email", "john@example.com");
            properties.put("organization", "Countly");
            properties.put("phone", "1234567890");
            properties.put("picture", "https://example.com/picture.jpg");
            properties.put("gender", "M");
            properties.put("byear", 1980);
            properties.put("tier", "gold");
            properties.put("age", 30);
            countly.userProfile().setProperties(properties);
            countly.userProfile().save();
            // a session update reports elapsed seconds, so at least one has to pass for it to be sent
            Thread.sleep(1100);
            countly.sessions().updateSession();
            countly.sessions().endSession();
        });
    }

    @Test
    public void views() throws Exception {
        assertRequests("views", config(), countly -> {
            countly.sessions().beginSession();
            Map<String, Object> segmentation = new HashMap<>();
            segmentation.put("source", "scenario");
            countly.views().startView("Home", segmentation);
            countly.views().stopViewWithName("Home");
            countly.views().startView("Settings");
            countly.sessions().endSession();
        });
    }

    @Test
    public void crash() throws Exception {
        assertRequests("crash", config(), countly -> {
            countly.crashes().addCrashBreadcrumb("breadcrumb one");
            Map<String, Object> segmentation = new HashMap<>();
            segmentation.put("tier", "gold");
            countly.crashes().recordHandledException(new Exception("parity crash"), segmentation);
        });
    }

    @Test
    public void consent() throws Exception {
        assertRequests("consent", config().setRequiresConsent(true), countly -> {
            countly.consent().giveConsent(new String[] { "sessions", "events" });
            countly.sessions().beginSession();
            countly.events().recordEvent("consented");
            settle(countly);
            countly.consent().removeConsent(new String[] { "events" });
            countly.events().recordEvent("not_consented");
            countly.sessions().endSession();
        });
    }

    @Test
    public void deviceId() throws Exception {
        assertRequests("device-id", config(), countly -> {
            countly.sessions().beginSession();
            countly.deviceId().changeWithMerge("parity-device-002");
            countly.events().recordEvent("after_merge");
            settle(countly);
            countly.deviceId().changeWithoutMerge("parity-device-003");
            countly.events().recordEvent("after_new_id");
            countly.sessions().endSession();
        });
    }

    /**
     * The init config every scenario starts from: manual sessions, the parity device ID and fixed device metrics.
     *
     * @return config
     */
    private CountlyConfig config() {
        CountlyConfig config = new CountlyConfig(TestUtils.getApplication(), "parity-app-key", "https://parity.example")
            .setDeviceId("parity-device-001")
            .enableManualSessionControl()
            .setTrackOrientationChanges(false)
            .setLoggingEnabled(true);
        config.metricProviderOverride = new MockedMetricProvider();
        return config;
    }

    /**
     * The event segmentation of the parity scenario, with one value of every type.
     *
     * @return segmentation
     */
    private Map<String, Object> paritySegmentation() {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("nar", "uto");
        segmentation.put("sasuke", MAX_INT);
        segmentation.put("sakura", false);
        segmentation.put("itachi", Double.MAX_VALUE);
        segmentation.put("kakaski", Double.MIN_VALUE);
        segmentation.put("hashirama", -MAX_INT);
        Map<String, Object> tobi = new HashMap<>();
        tobi.put("obito", "uchiha");
        tobi.put("zetsu", "white");
        segmentation.put("tobi", tobi);
        segmentation.put("stringList", Arrays.asList("value1", "value2", "value3"));
        segmentation.put("intList", Arrays.asList(1, 2, 3));
        segmentation.put("doubleList", Arrays.asList(1.1, 2.2, 3.3));
        segmentation.put("boolList", Arrays.asList(true, false, true));
        segmentation.put("mixedList", Arrays.asList("value1", 2, 3.3, true));
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("key1", "value" + i);
            entry.put("key2", i + 1);
            mapList.add(entry);
        }
        segmentation.put("mapList", mapList);
        return segmentation;
    }

    /**
     * Moves the recorded events into the request queue and waits until the queue stops growing, so that what the calls
     * before queued stays apart from what follows.
     *
     * @param countly the SDK
     * @throws InterruptedException when waiting is interrupted
     */
    private void settle(Countly countly) throws InterruptedException {
        countly.moduleRequestQueue.sendEventsIfNeeded(true);
        int count = -1;
        long quietSince = System.currentTimeMillis();
        long started = quietSince;
        while (System.currentTimeMillis() - started < 10_000) {
            Thread.sleep(50);
            int queued = TestUtils.getCountlyStore().getRequests().length;
            if (queued != count) {
                count = queued;
                quietSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - quietSince > 500) {
                return;
            }
        }
        Assert.fail("the request queue kept changing for 10 seconds");
    }

    /**
     * Runs a scenario on a new SDK instance and compares the requests it queued with the record of the scenario.
     *
     * @param name scenario name, the record file name without .json
     * @param config init config
     * @param scenario the calls
     * @throws Exception when the record cannot be read or the requests cannot be parsed
     */
    private void assertRequests(String name, CountlyConfig config, Scenario scenario) throws Exception {
        Countly countly = new Countly().init(config);
        scenario.run(countly);
        settle(countly);
        String current = report(TestUtils.getCurrentRQ());
        String committed = committed(name);
        if (!committed.equals(current)) {
            // test reports drop the leading spaces of each line, so the JSON is printed behind a prefix requestsUpdate removes
            StringBuilder printed = new StringBuilder();
            for (String line : current.split("\n")) {
                printed.append("| ").append(line).append("\n");
            }
            Assert.fail("the requests of the " + name + " scenario differ from sdk/api/requests/" + name + ".json. If that is deliberate, run ./gradlew :sdk:requestsUpdate, check the difference and add a line to CHANGELOG.md.\n"
                + "----- " + name + ".json -----\n" + printed + "----- end -----");
        }
    }

    /**
     * The committed record of a scenario, from the androidTest assets that sdk/api/ provides.
     *
     * @param name scenario name
     * @return file content, or a note that it is missing
     * @throws IOException when the asset cannot be read
     */
    private String committed(String name) throws IOException {
        String path = "requests/" + name + ".json";
        if (!Arrays.asList(InstrumentationRegistry.getInstrumentation().getContext().getAssets().list("requests")).contains(name + ".json")) {
            return "(no sdk/api/" + path + ")\n";
        }
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    /**
     * The record of queued requests: each with its path and its parameters, the values that change from run to run masked,
     * the JSON parameters parsed and the keys of every object sorted.
     *
     * @param requests the request queue, as TestUtils.getCurrentRQ gives it
     * @return file content
     * @throws JSONException when a JSON parameter cannot be parsed
     */
    private String report(Map<String, String>[] requests) throws JSONException {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < requests.length; i++) {
            JSONObject params = new JSONObject();
            for (Map.Entry<String, String> param : requests[i].entrySet()) {
                String name = param.getKey();
                Object value = param.getValue();
                if (MASKED_PARAMS.contains(name)) {
                    value = MASK;
                } else if (JSON_PARAMS.contains(name) && !param.getValue().isEmpty()) {
                    value = maskedJson(name, new JSONTokener(param.getValue()).nextValue());
                }
                params.put(name, value);
            }
            out.append(i == 0 ? "\n" : ",\n").append("  {\n    \"path\": \"/i\",\n    \"params\": ");
            write(out, params, "    ");
            out.append("\n  }");
        }
        return out.append(requests.length == 0 ? "]\n" : "\n]\n").toString();
    }

    /**
     * The value of a JSON parameter with the values that change from run to run masked.
     *
     * @param name parameter name
     * @param value parsed parameter value
     * @return the masked value
     * @throws JSONException when the value cannot be read
     */
    private Object maskedJson(String name, Object value) throws JSONException {
        if ((name.equals("metrics") || name.equals("hc")) && value instanceof JSONObject) {
            return masked((JSONObject) value, keys((JSONObject) value));
        }
        if (name.equals("events") && value instanceof JSONArray) {
            JSONArray events = (JSONArray) value;
            JSONArray result = new JSONArray();
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                List<String> fields = new ArrayList<>(MASKED_EVENT_FIELDS);
                if ("[CLY]_view".equals(event.optString("key"))) {
                    fields.add("dur");
                }
                result.put(masked(event, fields));
            }
            return result;
        }
        if (name.equals("crash") && value instanceof JSONObject) {
            return masked((JSONObject) value, MASKED_CRASH_FIELDS);
        }
        return value;
    }

    /**
     * A copy of an object with the values of the given keys masked.
     *
     * @param value object
     * @param fields keys to mask
     * @return the masked copy
     * @throws JSONException when the object cannot be read
     */
    private JSONObject masked(JSONObject value, List<String> fields) throws JSONException {
        JSONObject copy = new JSONObject();
        for (String key : keys(value)) {
            copy.put(key, fields.contains(key) ? MASK : value.get(key));
        }
        return copy;
    }

    /**
     * The keys of an object, sorted.
     *
     * @param value object
     * @return keys
     */
    private List<String> keys(JSONObject value) {
        TreeSet<String> keys = new TreeSet<>();
        java.util.Iterator<String> it = value.keys();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        return new ArrayList<>(keys);
    }

    /**
     * Writes a JSON value with two-space indentation and sorted object keys, escaping only what JSON requires.
     *
     * @param out where to write
     * @param value JSON value
     * @param indent indentation of the line the value starts on
     * @throws JSONException when the value cannot be read
     */
    private void write(StringBuilder out, Object value, String indent) throws JSONException {
        if (value instanceof JSONObject) {
            List<String> keys = keys((JSONObject) value);
            if (keys.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{");
            for (int i = 0; i < keys.size(); i++) {
                out.append(i == 0 ? "\n" : ",\n").append(indent).append("  ");
                string(out, keys.get(i));
                out.append(": ");
                write(out, ((JSONObject) value).get(keys.get(i)), indent + "  ");
            }
            out.append("\n").append(indent).append("}");
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() == 0) {
                out.append("[]");
                return;
            }
            out.append("[");
            for (int i = 0; i < array.length(); i++) {
                out.append(i == 0 ? "\n" : ",\n").append(indent).append("  ");
                write(out, array.get(i), indent + "  ");
            }
            out.append("\n").append(indent).append("]");
        } else if (value instanceof String) {
            string(out, (String) value);
        } else if (value == null || value == JSONObject.NULL) {
            out.append("null");
        } else {
            out.append(value);
        }
    }

    /**
     * Writes a JSON string, escaping quotes, backslashes and control characters.
     *
     * @param out where to write
     * @param value text
     */
    private void string(StringBuilder out, String value) {
        out.append('"');
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }
}
