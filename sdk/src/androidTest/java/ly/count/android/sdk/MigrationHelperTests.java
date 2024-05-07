package ly.count.android.sdk;

import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import ly.count.android.sdk.internal.RemoteConfigValueStore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class MigrationHelperTests {
    ModuleLog mockLog;
    CountlyStore cs;
    StorageProvider sp;
    final int latestSchemaVersion = 4;

    @Before
    public void setUp() {
        mockLog = mock(ModuleLog.class);

        cs = new CountlyStore(TestUtils.getContext(), mockLog);
        sp = cs;

        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mockLog);
        countlyStore.clear();
    }

    @After
    public void tearDown() {
    }

    public Map<String, Object> GetMigrationParams_0_1(boolean customIdProvided) {
        Map<String, Object> migrationParams = new HashMap<>();
        migrationParams.put(MigrationHelper.key_from_0_to_1_custom_id_set, customIdProvided);
        return migrationParams;
    }

    void validateGeneratedUUID(String deviceId) {
        assertNotNull(deviceId);
        Assert.assertTrue(deviceId.length() > 10);
        Assert.assertTrue(deviceId.length() < 100);
    }

    /**
     * Verify that the current SDK data schema version is the expected value
     */
    @Test
    public void validateDataSchemaVersion() {
        MigrationHelper mh = new MigrationHelper(sp, mockLog, getApplicationContext());
        Assert.assertEquals(latestSchemaVersion, mh.DATA_SCHEMA_VERSIONS);
    }

    /**
     * If the SDK has no data, the initial schema version should be set the latest schema version
     */
    @Test
    public void setInitialSchemaVersionEmpty() {
        StorageProvider spMock = mock(StorageProvider.class);
        when(spMock.anythingSetInStorage()).thenReturn(false);

        MigrationHelper mh = new MigrationHelper(spMock, mockLog, getApplicationContext());
        mh.setInitialSchemaVersion();

        verify(spMock).anythingSetInStorage();
        verify(spMock).setDataSchemaVersion(mh.DATA_SCHEMA_VERSIONS);
    }

    /**
     * If the SDK has data in storage, the initial schema version should be set to 0
     */
    @Test
    public void setInitialSchemaVersionLegacy() {
        StorageProvider spMock = mock(StorageProvider.class);
        when(spMock.anythingSetInStorage()).thenReturn(true);

        MigrationHelper mh = new MigrationHelper(spMock, mockLog, getApplicationContext());
        mh.setInitialSchemaVersion();

        verify(spMock).anythingSetInStorage();
        verify(spMock).setDataSchemaVersion(0);
    }

    /**
     * Validate the result of 'getCurrentSchemaVersion' if it's run the first time and there is no data.
     *
     * It should return the latest schema version
     */
    @Test
    public void getCurrentSchemaVersionEmpty() {
        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        Assert.assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());

        //verify a rerun
        Assert.assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());
    }

    /**
     * Validate the result of 'getCurrentSchemaVersion' if it's run the first time and there is some data.
     *
     * It should return the legacy schema version - 0
     */
    @Test
    public void getCurrentSchemaVersionLegacy() {
        cs.addRequest("fff", false);
        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        Assert.assertEquals(0, mh.getCurrentSchemaVersion());

        //verify a rerun
        Assert.assertEquals(0, mh.getCurrentSchemaVersion());
    }

    /**
     * Validate the result of 'getCurrentSchemaVersion' if there was a schema version set previously
     *
     * It should return the legacy schema version - 0
     */
    @Test
    public void getCurrentSchemaVersionMisc() {
        MigrationHelper mh = new MigrationHelper(sp, mockLog, getApplicationContext());
        Assert.assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());

        sp.setDataSchemaVersion(123);
        Assert.assertEquals(123, mh.getCurrentSchemaVersion());

        //verify a rerun
        Assert.assertEquals(123, mh.getCurrentSchemaVersion());

        sp.setDataSchemaVersion(-333);
        Assert.assertEquals(-333, mh.getCurrentSchemaVersion());
    }

    /**
     * We start on the latest version and nothing should change
     * Calling "doWork"
     * This is 'true' when Custom ID not provided or it is provided
     */
    @Test
    public void performMigration0to1_0_doWork_id_not_provided() {
        for (int a = 0; a <= 1; a++) {
            cs.clear();
            MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
            Assert.assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            Assert.assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
            //we started at the latest version and the device ID type and value should be null
            Assert.assertNull(cs.getDeviceID());
            Assert.assertNull(cs.getDeviceIDType());
        }
    }

    /**
     * We start on the latest version and nothing should change
     * Calling "init"
     */
    @Test
    public void performMigration0to1_0_init() {
        Assert.assertFalse(cs.anythingSetInStorage());
        Assert.assertNull(cs.getDeviceID());
        Assert.assertNull(cs.getDeviceIDType());

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        validateGeneratedUUID(cs.getDeviceID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * We are on the legacy version and we should get to the latest schema version.
     * In case of developer supplied ID, nothing should be changed
     * Calling "doWork"
     */
    @Test
    public void performMigration0to1_1() {
        for (int a = 0; a <= 1; a++) {
            cs.clear();
            cs.addRequest("fff", false);
            cs.setDeviceIDType(DeviceIdType.DEVELOPER_SUPPLIED.toString());
            MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
            Assert.assertEquals(0, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            Assert.assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
            Assert.assertNull(cs.getDeviceID());
            Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED.toString(), cs.getDeviceIDType());
        }
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * In case of ADVERTISING_ID it should be changed to OPEN_UDID,
     * and a new ID should be generated because it currently is 'null'
     *
     * Calling "doWork"
     */
    @Test
    public void performMigration0to1_2() {
        for (int a = 0; a <= 1; a++) {
            cs.clear();
            cs.addRequest("fff", false);//request added to indicate that this is not the first launch but a legacy version
            cs.setDeviceIDType(MigrationHelper.legacyDeviceIDTypeValue_AdvertisingID);
            MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
            Assert.assertEquals(0, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            Assert.assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
            validateGeneratedUUID(cs.getDeviceID());
            Assert.assertEquals(DeviceIdType.OPEN_UDID.toString(), cs.getDeviceIDType());
        }
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * In case of OPEN_UDID it should not be changed,
     * A new ID should be generated because it currently is 'null'
     *
     * Calling "doWork"
     */
    @Test
    public void performMigration0to1_3() {
        for (int a = 0; a <= 1; a++) {
            cs.clear();
            cs.addRequest("fff", false);//request added to indicate that this is not the first launch but a legacy version
            cs.setDeviceIDType(DeviceIdType.OPEN_UDID.toString());
            MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
            Assert.assertEquals(0, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            Assert.assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
            validateGeneratedUUID(cs.getDeviceID());
            Assert.assertEquals(DeviceIdType.OPEN_UDID.toString(), cs.getDeviceIDType());
        }
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * In case of ADVERTISING_ID it should be changed to OPEN_UDID,
     * and a new ID should be generated because it currently is 'null'
     *
     * Calling "init"
     */
    @Test
    public void performMigration0to1_4() {
        cs.setDeviceIDType(MigrationHelper.legacyDeviceIDTypeValue_AdvertisingID);

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        String initialID = countly.deviceId().getID();
        validateGeneratedUUID(initialID);
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());

        //perform a second init and confirm that everything is still stable
        Countly countly2 = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly2.deviceId().getType());
        Assert.assertEquals(initialID, countly2.deviceId().getID());
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * In case of OPEN_UDID it should not be changed,
     * and a new ID should be generated because it currently is 'null'
     */
    @Test
    public void performMigration0to1_5() {
        cs.setDeviceIDType(DeviceIdType.OPEN_UDID.toString());

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        String initialID = countly.deviceId().getID();
        validateGeneratedUUID(initialID);
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());

        Countly countly2 = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly2.deviceId().getType());
        Assert.assertEquals(initialID, countly2.deviceId().getID());
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * Since the initial type is ADVERTISING_ID, it should be changed to OPEN_UDID
     * a new ID should not be generated because it already is a valid value
     */
    @Test
    public void performMigration0to1_6() {
        cs.setDeviceIDType(MigrationHelper.legacyDeviceIDTypeValue_AdvertisingID);
        cs.setDeviceID("ab");

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        Assert.assertEquals("ab", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * Since the initial type is OPEN_UDID, it should not change
     * a new ID should not be generated because it already is a valid value
     */
    @Test
    public void performMigration0to1_7() {
        cs.setDeviceIDType(DeviceIdType.OPEN_UDID.toString());
        cs.setDeviceID("cd");

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        Assert.assertEquals("cd", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * We are on schema version 1
     * Type and device ID should remain the same
     */
    @Test
    public void performMigration0to1_8() {
        cs.setDataSchemaVersion(1);
        cs.setDeviceIDType(DeviceIdType.OPEN_UDID.toString());
        cs.setDeviceID("cd");

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        Assert.assertEquals("cd", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * Device ID is provided, but no device ID has been set. This is a transition from a older version
     * a new ID should not be generated because it already is a valid value
     * The type should be set as OPEN_UDID since we are not providing a device ID during init
     */
    @Test
    public void performMigration0to1_9() {
        cs.setDeviceID("cd");

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        Assert.assertEquals("cd", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * Device ID and device ID type are not set, but there is something in the queue. This is a transition from a older version
     * a new ID should not be generated because it already is a valid value
     */
    @Test
    public void performMigration0to1_10() {
        cs.addRequest("qqq", false);

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        validateGeneratedUUID(countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * We are on the legacy version and we should get to the latest schema version
     * Device ID is provided, but no device ID has been set. This is a transition from a older version
     * a new ID should not be generated because it already is a valid value
     * The type should be set as developer supplied since we are trying to provide a device ID during init
     */
    @Test
    public void performMigration0to1_11() {
        cs.setDeviceID("cd");

        Countly countly = new Countly().init(new CountlyConfig(getApplicationContext(), TestUtils.commonAppKey, TestUtils.commonURL).setDeviceId("asd"));

        Assert.assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
        Assert.assertEquals("cd", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Transform the old structure to the new one
     * A mixed object should still not throw it off
     * All values should be accepted
     */
    @Test
    public void performMigration1To2_1() throws JSONException {
        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());

        JSONArray jsonArray = new JSONArray();
        jsonArray.put("11");
        jsonArray.put(44);

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("s", 3);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        cs.setRemoteConfigValues("{" + rcEntryLegacy("a", 123) + "," + rcEntryLegacy("b", "fg") + "," + rcEntryLegacy("c", jsonArray) + "," + rcEntryLegacy("d", jsonObject) + "}");
        mh.performMigration1To2(new HashMap<>());
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(cs.getRemoteConfigValues(), false);

        Assert.assertEquals(4, rcvs.values.length());

        Assert.assertEquals(123, rcvs.getValue("a").value);
        Assert.assertTrue(rcvs.getValue("a").isCurrentUsersData);

        Assert.assertEquals("fg", rcvs.getValue("b").value);
        Assert.assertTrue(rcvs.getValue("b").isCurrentUsersData);

        Assert.assertEquals(jsonArray, rcvs.getValue("c").value);
        Assert.assertTrue(rcvs.getValue("c").isCurrentUsersData);

        JSONObject retVal = (JSONObject) rcvs.getValue("d").value;
        Assert.assertEquals(jsonObject.get("s"), retVal.get("s"));
        Assert.assertTrue(rcvs.getValue("d").isCurrentUsersData);
    }

    /**
     * Make sure that an empty object works for migration
     */
    @Test
    public void performMigration1To2_2() {
        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());

        cs.setRemoteConfigValues("");
        mh.performMigration1To2(new HashMap<>());
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(cs.getRemoteConfigValues(), false);

        Assert.assertEquals(0, rcvs.values.length());
    }

    /**
     * Make sure that a null object works for migration
     */
    @Test
    public void performMigration1To2_3() {
        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());

        cs.setRemoteConfigValues(null);
        mh.performMigration1To2(new HashMap<>());
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(cs.getRemoteConfigValues(), false);

        Assert.assertEquals(0, rcvs.values.length());
    }

    /**
     * Make sure that garbage doesn't break it
     */
    @Test
    public void performMigration1To2_4() {
        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());

        cs.setRemoteConfigValues("dsfsdf");
        mh.performMigration1To2(new HashMap<>());
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(cs.getRemoteConfigValues(), false);

        Assert.assertEquals(0, rcvs.values.length());
    }

    @Test
    public void performMigration2To3_1() {
        SharedPreferences sp = CountlyStore.createPreferencesPush(getApplicationContext());
        sp.edit().putString(MigrationHelper.legacyCACHED_PUSH_MESSAGING_MODE, "abc").apply();

        Assert.assertEquals("abc", sp.getString(MigrationHelper.legacyCACHED_PUSH_MESSAGING_MODE, null));

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration2To3(new HashMap<>());
        Assert.assertNull(sp.getString(MigrationHelper.legacyCACHED_PUSH_MESSAGING_MODE, null));
    }

    /**
     * Create a legacy entry
     *
     * @param key
     * @param value
     * @return
     */
    public static String rcEntryLegacy(String key, Object value) {
        StringBuilder ret = new StringBuilder();
        ret.append("\"" + key + "\":");

        if (value instanceof String) {
            ret.append("\"");
            ret.append(value);
            ret.append("\"");
        } else if (value instanceof JSONArray) {
            ret.append(value);
        } else if (value instanceof JSONObject) {
            ret.append(value);
        } else {
            ret.append(value);
        }

        return ret.toString();
    }

    /**
     * Empty queue, device ID not acquired
     */
    @Test
    public void performMigration3To4_1() {
        TestUtils.assertQueueSizes(0, 0, cs);
        Assert.assertNull(cs.getDeviceID());

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());
    }

    /**
     * Empty queue, device ID acquired
     */
    @Test
    public void performMigration3To4_2() {
        TestUtils.assertQueueSizes(0, 0, cs);
        cs.setDeviceID("123");

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());
    }

    /**
     * single requests in queue, no device ID
     * nothing should change
     */
    @Test
    public void performMigration3To4_3() {
        TestUtils.assertQueueSizes(0, 0, cs);
        cs.addRequest(generateMockRequest(null, -1), true);
        TestUtils.assertQueueSizes(1, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(1, 0, cs);
        String[] reqs = cs.getRequests();
        Assert.assertEquals(generateMockRequest(null, -1), reqs[0]);
    }

    /**
     * 3 requests in queue, device ID acquired, not temp ID
     * All should get that ID
     */
    @Test
    public void performMigration3To4_4() {
        TestUtils.assertQueueSizes(0, 0, cs);
        cs.setDeviceID("123");

        cs.addRequest(generateMockRequest(null, -1), true);
        cs.addRequest(generateMockRequest(null, -1), true);
        cs.addRequest(generateMockRequest(null, -1), true);

        TestUtils.assertQueueSizes(3, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(3, 0, cs);
        String[] reqs = cs.getRequests();
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[2]);
    }

    /**
     * 3 requests in queue, temp ID
     * probably all requests should get temp ID device ID
     */
    @Test
    public void performMigration3To4_5() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID(DeviceId.temporaryCountlyDeviceId);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        TestUtils.assertQueueSizes(3, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(3, 0, cs);
        String[] reqs = cs.getRequests();
        validateRequestsAreEqual(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 2), reqs[2]);
    }

    /**
     * 3 requests in queue, device ID  merge request, 3 requests, another merge request, 3 requests
     */
    @Test
    public void performMigration3To4_6() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("789");

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest("123", 1), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest("456", 1), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        TestUtils.assertQueueSizes(11, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(11, 0, cs);

        String[] reqs = cs.getRequests();

        validateRequestsAreEqual(generateMockRequest("789", 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("789", 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("789", 2), reqs[2]);

        validateRequestsAreEqual(generateMockRequest("123", 1, "789"), reqs[3]);

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[4]);
        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[5]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[6]);

        validateRequestsAreEqual(generateMockRequest("456", 1, "123"), reqs[7]);

        validateRequestsAreEqual(generateMockRequest("456", 0), reqs[8]);
        validateRequestsAreEqual(generateMockRequest("456", 1), reqs[9]);
        validateRequestsAreEqual(generateMockRequest("456", 2), reqs[10]);
    }

    /**
     * 3 requests in queue, device ID  merge request, 2 requests with no ID 2 requests with temp ID
     */
    @Test
    public void performMigration3To4_7() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("456");

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest("123", 1), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 2), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 2), true);

        TestUtils.assertQueueSizes(8, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(8, 0, cs);

        String[] reqs = cs.getRequests();

        validateRequestsAreEqual(generateMockRequest("456", 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("456", 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("456", 2), reqs[2]);

        validateRequestsAreEqual(generateMockRequest("123", 1, "456"), reqs[3]);

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[4]);
        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[5]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[6]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[7]);
    }

    /**
     * 1 requests in queue, without device ID, 1 requests with temp id, 1 requests with no ID
     * Device ID acquired
     * At the end, all should get the current device ID
     */
    @Test
    public void performMigration3To4_8() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("123");

        cs.addRequest(generateMockRequest(null, -1), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 1), true);
        cs.addRequest(generateMockRequest(null, -1), true);

        TestUtils.assertQueueSizes(3, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(3, 0, cs);
        String[] reqs = cs.getRequests();
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[2]);
    }

    /**
     * 3 requests, 1 without merge, 3 requests, 1 with merge, 3 requests, 1 without merge, 3 requests, 1 temp id
     * Device ID acquired
     */
    @Test
    public void performMigration3To4_9() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("NEW_ID");

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest(1, "123"), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest("456", 1), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest(1, "789"), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 1), true);

        TestUtils.assertQueueSizes(16, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(16, 0, cs);
        String[] reqs = cs.getRequests();

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[2]);

        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[3]);

        validateRequestsAreEqual(generateMockRequest("789", 0), reqs[4]);
        validateRequestsAreEqual(generateMockRequest("789", 1), reqs[5]);
        validateRequestsAreEqual(generateMockRequest("789", 2), reqs[6]);

        validateRequestsAreEqual(generateMockRequest("456", 1, "789"), reqs[7]);

        validateRequestsAreEqual(generateMockRequest("456", 0), reqs[8]);
        validateRequestsAreEqual(generateMockRequest("456", 1), reqs[9]);
        validateRequestsAreEqual(generateMockRequest("456", 2), reqs[10]);

        validateRequestsAreEqual(generateMockRequest("456", 1), reqs[11]);

        validateRequestsAreEqual(generateMockRequest("NEW_ID", 0), reqs[12]);
        validateRequestsAreEqual(generateMockRequest("NEW_ID", 1), reqs[13]);
        validateRequestsAreEqual(generateMockRequest("NEW_ID", 2), reqs[14]);
        validateRequestsAreEqual(generateMockRequest("NEW_ID", 1), reqs[15]);
    }

    /**
     * 3 requests, 1 without merge, 3 requests, 1 with merge, 3 requests
     */
    @Test
    public void performMigration3To4_10() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("NEW_ID");

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest(1, "123"), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest("456", 1), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        TestUtils.assertQueueSizes(11, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(11, 0, cs);
        String[] reqs = cs.getRequests();

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[2]);

        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[3]);

        validateRequestsAreEqual(generateMockRequest("NEW_ID", 0), reqs[4]);
        validateRequestsAreEqual(generateMockRequest("NEW_ID", 1), reqs[5]);
        validateRequestsAreEqual(generateMockRequest("NEW_ID", 2), reqs[6]);

        validateRequestsAreEqual(generateMockRequest("456", 1, "NEW_ID"), reqs[7]);

        validateRequestsAreEqual(generateMockRequest("456", 0), reqs[8]);
        validateRequestsAreEqual(generateMockRequest("456", 1), reqs[9]);
        validateRequestsAreEqual(generateMockRequest("456", 2), reqs[10]);
    }

    /**
     * 3 requests and device id given, at the end all request should contain device id
     */
    @Test
    public void performMigration3To4_11() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("NEW_ID");

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(null, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        TestUtils.assertQueueSizes(3, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(3, 0, cs);
        String[] reqs = cs.getRequests();

        validateRequestsAreEqual(generateMockRequest("NEW_ID", 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("NEW_ID", 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("NEW_ID", 2), reqs[2]);
    }

    /**
     * 1 request, 1 temp, 1 request, 1 without merge, 1 request, 1 temp, 1 request, 1 without merge, 1 request, 1 temp, 1 request
     */
    @Test
    public void performMigration3To4_12() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("789");

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest(0, "123"), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 0), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest(0, "456"), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 2), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        TestUtils.assertQueueSizes(11, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(11, 0, cs);
        String[] reqs = cs.getRequests();

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[2]);

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[3]);

        validateRequestsAreEqual(generateMockRequest("456", 0), reqs[4]);
        validateRequestsAreEqual(generateMockRequest("456", 1), reqs[5]);
        validateRequestsAreEqual(generateMockRequest("456", 2), reqs[6]);

        validateRequestsAreEqual(generateMockRequest("456", 0), reqs[7]);

        validateRequestsAreEqual(generateMockRequest("789", 0), reqs[8]);
        validateRequestsAreEqual(generateMockRequest("789", 1), reqs[9]);
        validateRequestsAreEqual(generateMockRequest("789", 2), reqs[10]);
    }

    /**
     * 1 request, 1 temp, 1 request, 1 merge, 1 request, 1 temp, 1 request, 1 without merge, 1 request, 1 temp, 1 request
     */
    @Test
    public void performMigration3To4_13() {
        TestUtils.assertQueueSizes(0, 0, cs);

        cs.setDeviceID("789");

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 1), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest("123", 0), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 0), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        cs.addRequest(generateMockRequest(2, "456"), true);

        cs.addRequest(generateMockRequest(null, 0), true);
        cs.addRequest(generateMockRequest(DeviceId.temporaryCountlyDeviceId, 2), true);
        cs.addRequest(generateMockRequest(null, 2), true);

        TestUtils.assertQueueSizes(11, 0, cs);

        MigrationHelper mh = new MigrationHelper(cs, mockLog, getApplicationContext());
        mh.performMigration3To4(new HashMap<>());

        TestUtils.assertQueueSizes(11, 0, cs);
        String[] reqs = cs.getRequests();

        validateRequestsAreEqual(generateMockRequest("456", 0), reqs[0]);
        validateRequestsAreEqual(generateMockRequest("456", 1), reqs[1]);
        validateRequestsAreEqual(generateMockRequest("456", 2), reqs[2]);

        validateRequestsAreEqual(generateMockRequest("123", 0, "456"), reqs[3]);

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[4]);
        validateRequestsAreEqual(generateMockRequest("123", 1), reqs[5]);
        validateRequestsAreEqual(generateMockRequest("123", 2), reqs[6]);

        validateRequestsAreEqual(generateMockRequest("123", 0), reqs[7]);

        validateRequestsAreEqual(generateMockRequest("789", 0), reqs[8]);
        validateRequestsAreEqual(generateMockRequest("789", 1), reqs[9]);
        validateRequestsAreEqual(generateMockRequest("789", 2), reqs[10]);
    }

    String[][] mockParams = { { "app_key", "asdsad" }, { "sdk_version", "445" }, { "sdk_name", "dfdfs" }, { "timestamp", "24234" } };

    String generateMockRequest(String deviceIDV, int deviceIDPos) {
        return generateMockRequest(deviceIDV, deviceIDPos, null, null);
    }

    String generateMockRequest(int deviceIDPos, String overrideID) {
        return generateMockRequest(null, deviceIDPos, null, overrideID);
    }

    String generateMockRequest(String deviceIDV, int deviceIDPos, String oldId) {
        return generateMockRequest(deviceIDV, deviceIDPos, oldId, null);
    }

    /**
     * @param deviceIDV
     * @param deviceIDPos 0 - start, 1 - mid, 2 - end
     * @return
     */
    String generateMockRequest(String deviceIDV, int deviceIDPos, String oldId, String overrideId) {
        StringBuilder sb = new StringBuilder(100);

        if (deviceIDPos == 0) {
            //put it at the start
            appendIfNotNull(sb, "device_id", deviceIDV);
            appendIfNotNull(sb, "&old_device_id", oldId);
            appendIfNotNull(sb, "override_id", overrideId);
        }

        for (int a = 0; a < mockParams.length; a++) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(mockParams[a][0]);
            sb.append('=');
            sb.append(mockParams[a][1]);

            if (deviceIDPos == 1 && a == 1) {
                //put it in the middle
                appendIfNotNull(sb, "&device_id", deviceIDV);
                appendIfNotNull(sb, "&old_device_id", oldId);
                appendIfNotNull(sb, "&override_id", overrideId);
            }
        }

        if (deviceIDPos == 2) {
            //put it at the end
            appendIfNotNull(sb, "&device_id", deviceIDV);
            appendIfNotNull(sb, "&old_device_id", oldId);
            appendIfNotNull(sb, "&override_id", overrideId);
        }

        return sb.toString();
    }

    private void appendIfNotNull(StringBuilder sb, String key, String value) {
        if (value != null) {
            sb.append(key);
            sb.append('=');
            sb.append(value);
        }
    }

    void validateRequestsAreEqual(String required, String gotten) {
        Assert.assertEquals(Utils.splitIntoParams(required), Utils.splitIntoParams(gotten));
    }
}
