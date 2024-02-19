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

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class MigrationHelperTests {
    ModuleLog mockLog;
    CountlyStore cs;
    StorageProvider sp;
    final int latestSchemaVersion = 3;

    @Before
    public void setUp() {
        mockLog = mock(ModuleLog.class);

        cs = new CountlyStore(getContext(), mockLog);
        sp = cs;

        final CountlyStore countlyStore = new CountlyStore(getContext(), mockLog);
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
        assertTrue(deviceId.length() > 10);
        assertTrue(deviceId.length() < 100);
    }

    /**
     * Verify that the current SDK data schema version is the expected value
     */
    @Test
    public void validateDataSchemaVersion() {
        MigrationHelper mh = new MigrationHelper(sp, mockLog, getApplicationContext());
        assertEquals(latestSchemaVersion, mh.DATA_SCHEMA_VERSIONS);
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
        assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());

        //verify a rerun
        assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());
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
        assertEquals(0, mh.getCurrentSchemaVersion());

        //verify a rerun
        assertEquals(0, mh.getCurrentSchemaVersion());
    }

    /**
     * Validate the result of 'getCurrentSchemaVersion' if there was a schema version set previously
     *
     * It should return the legacy schema version - 0
     */
    @Test
    public void getCurrentSchemaVersionMisc() {
        MigrationHelper mh = new MigrationHelper(sp, mockLog, getApplicationContext());
        assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());

        sp.setDataSchemaVersion(123);
        assertEquals(123, mh.getCurrentSchemaVersion());

        //verify a rerun
        assertEquals(123, mh.getCurrentSchemaVersion());

        sp.setDataSchemaVersion(-333);
        assertEquals(-333, mh.getCurrentSchemaVersion());
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
            assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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
            assertEquals(0, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
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
            assertEquals(0, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
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
            assertEquals(0, mh.getCurrentSchemaVersion());

            if (a == 0) {
                mh.doWork(GetMigrationParams_0_1(false));
            } else {
                mh.doWork(GetMigrationParams_0_1(true));
            }

            assertEquals(latestSchemaVersion, mh.getCurrentSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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

        assertEquals(latestSchemaVersion, cs.getDataSchemaVersion());
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
}
