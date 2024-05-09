package ly.count.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import ly.count.android.sdk.internal.RemoteConfigValueStore;
import org.json.JSONException;
import org.json.JSONObject;

class MigrationHelper {
    /**
     * 0 - legacy version. State of the SDK before the first migration was introduced
     * 1 - version where the device ID is guaranteed and advertising ID is deprecated/removed as a type
     * 2 - transitioning old RC store to one that supports metadata
     * 3 - removing messaging mode info
     * x - adding device ID to all requests
     */
    static final int DATA_SCHEMA_VERSIONS = 4;
    static final String key_from_0_to_1_custom_id_set = "0_1_custom_id_set";
    static final String param_key_device_id = "device_id";
    static final String param_key_override_id = "override_id";
    static final String param_key_old_device_id = "old_device_id";
    StorageProvider storage;
    ModuleLog L;
    Context cachedContext;

    static final public String legacyDeviceIDTypeValue_AdvertisingID = "ADVERTISING_ID";

    public static final String legacyCACHED_PUSH_MESSAGING_MODE = "PUSH_MESSAGING_MODE";

    public MigrationHelper(@NonNull StorageProvider storage, @NonNull ModuleLog moduleLog, @NonNull Context context) {
        assert storage != null;
        assert moduleLog != null;
        assert context != null;

        this.storage = storage;
        L = moduleLog;
        cachedContext = context;
        L.v("[MigrationHelper] Initialising");
    }

    /**
     * Called from SDK side to perform the required steps to check if the migration is required and then execute it if it is.
     */
    public void doWork(@NonNull Map<String, Object> migrationParams) {
        assert migrationParams != null;
        assert !migrationParams.isEmpty();

        int currentVersion = getCurrentSchemaVersion();
        L.v("[MigrationHelper] doWork, current version:[" + currentVersion + "]");

        assert currentVersion >= 0;

        if (currentVersion < 0) {
            L.e("[MigrationHelper] doWork, returned schema version is negative, encountered serious issue");
            return;
        }

        while (currentVersion < DATA_SCHEMA_VERSIONS) {
            performMigrationStep(currentVersion, migrationParams);

            currentVersion = getCurrentSchemaVersion();
        }
    }

    /**
     * Return the current schema version.
     * If no schema version is stored, the initial version will be acquired
     *
     * @return
     */
    int getCurrentSchemaVersion() {
        int currentVersion = storage.getDataSchemaVersion();

        if (currentVersion == -1) {
            //no schema version set
            setInitialSchemaVersion();
            currentVersion = storage.getDataSchemaVersion();
        }

        return currentVersion;
    }

    /**
     * Perform migration from the provided version to the next one
     *
     * @param currentVersion
     */
    void performMigrationStep(int currentVersion, @NonNull Map<String, Object> migrationParams) {
        assert currentVersion >= 0;
        assert currentVersion <= DATA_SCHEMA_VERSIONS;
        assert migrationParams != null;

        int newVersion = currentVersion;

        switch (currentVersion) {
            case 0:
                L.w("[MigrationHelper] performMigrationStep, performing migration from version [0] -> [1]");
                performMigration0To1(migrationParams);
                newVersion = newVersion + 1;
                break;
            case 1:
                L.w("[MigrationHelper] performMigrationStep, performing migration from version [1] -> [2]");
                performMigration1To2(migrationParams);
                newVersion = newVersion + 1;
                break;
            case 2:
                L.w("[MigrationHelper] performMigrationStep, performing migration from version [2] -> [3]");
                performMigration2To3(migrationParams);
                newVersion = newVersion + 1;
                break;
            case 3:
                L.w("[MigrationHelper] performMigrationStep, performing migration from version [3] -> [4]");
                performMigration3To4(migrationParams);
                newVersion = newVersion + 1;
                break;
            case DATA_SCHEMA_VERSIONS:
                L.w("[MigrationHelper] performMigrationStep, attempting to perform migration while already having the latest schema version, skipping [" + currentVersion + "]");
                break;
            default:
                L.w("[MigrationHelper] performMigrationStep, migration is performed out of the currently expected bounds, skipping [" + currentVersion + "]");
                break;
        }

        //assuming that the required migration steps are performed, increasing current schema version
        if (newVersion != currentVersion) {
            storage.setDataSchemaVersion(newVersion);
        }
    }

    /**
     * Set the current schema version the first time this code is executed
     *
     * If nothing is in storage then we can assume that this is the first run and no migration required.
     * In that case set the current version to the latest available one
     *
     * If something is in storage, assume that the SDK had been run before and migration is required.
     */
    void setInitialSchemaVersion() {
        if (storage.anythingSetInStorage()) {
            //we are on a legacy version
            storage.setDataSchemaVersion(0);
            return;
        }

        //no data means new install, apply the latest schema version
        storage.setDataSchemaVersion(DATA_SCHEMA_VERSIONS);
    }

    /**
     * Specific migration from schema version 0 to 1
     * This should make sure that a device ID exists and
     * that the advertising_ID type is transformed to open_udid
     */
    void performMigration0To1(@NonNull Map<String, Object> migrationParams) {
        String deviceIDType = storage.getDeviceIDType();
        String deviceID = storage.getDeviceID();

        if (deviceIDType == null && deviceID == null) {
            //if both the ID and type are null we are in big trouble
            //set type to OPEN_UDID and generate the ID afterwards
            storage.setDeviceIDType(DeviceIdType.OPEN_UDID.toString());
            deviceIDType = DeviceIdType.OPEN_UDID.toString();
        } else if (deviceIDType == null) {
            //if the type is null, but the ID value is not null, we have to guess the type
            Boolean customIdProvided = (Boolean) migrationParams.get(key_from_0_to_1_custom_id_set);
            if (customIdProvided == null) {
                customIdProvided = false;
            }

            if (customIdProvided) {
                //if a custom device ID is provided during init, assume that the previous type was dev supplied
                storage.setDeviceIDType(DeviceIdType.DEVELOPER_SUPPLIED.toString());
                deviceIDType = DeviceIdType.DEVELOPER_SUPPLIED.toString();
            } else {
                //if a custom device ID was not provided during init, assume that the previous type was SDK generated
                storage.setDeviceIDType(DeviceIdType.OPEN_UDID.toString());
                deviceIDType = DeviceIdType.OPEN_UDID.toString();
            }
        }

        //update the device ID type
        //noinspection StatementWithEmptyBody
        if (deviceIDType.equals(DeviceIdType.OPEN_UDID.toString())) {
            //current device ID is OPEN_UDID
            //nothing should change
        } else if (deviceIDType.equals(legacyDeviceIDTypeValue_AdvertisingID)) {
            //current device ID is ADVERTISING_ID
            //it's type should be changed to OPEN_UDID.
            storage.setDeviceIDType(DeviceIdType.OPEN_UDID.toString());
            deviceIDType = DeviceIdType.OPEN_UDID.toString();
        }

        //generate a deviceID in case the current type is OPEN_UDID (either migrated or originally as such) and there is no ID
        if (deviceIDType.equals(DeviceIdType.OPEN_UDID.toString())) {
            if (deviceID == null || deviceID.isEmpty()) {
                //in case there is no valid ID, generate it
                storage.setDeviceID(UUID.randomUUID().toString());
            }
        }
    }

    /**
     * Tranforming the old RC structure date store to one that supports metadata
     *
     * @param migrationParams
     */
    void performMigration1To2(@NonNull Map<String, Object> migrationParams) {
        String currentRC = storage.getRemoteConfigValues();

        JSONObject initialStructure;

        try {
            initialStructure = new JSONObject(currentRC);
        } catch (JSONException e) {
            L.w("[MigrationHelper] performMigration1To2, failed at parsing old RC data. Clearing data structure and continuing. " + e);
            storage.setRemoteConfigValues("");
            return;
        }

        JSONObject newStructure = new JSONObject();
        Iterator<String> iter = initialStructure.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                Object value = initialStructure.opt(key);
                if (value == null) {
                    continue;
                }
                JSONObject migratedValue = new JSONObject();
                migratedValue.put(RemoteConfigValueStore.keyValue, initialStructure.get(key));
                migratedValue.put(RemoteConfigValueStore.keyCacheFlag, RemoteConfigValueStore.cacheValFresh);
                newStructure.put(key, migratedValue);
            } catch (Exception e) {
                L.e("[MigrationHelper] performMigration1To2, transforming remote config values, " + e.toString());
            }
        }

        storage.setRemoteConfigValues(newStructure.toString());
    }

    /**
     * Removing the messaging mode info from storage
     *
     * @param migrationParams
     */
    void performMigration2To3(@NonNull Map<String, Object> migrationParams) {
        SharedPreferences sp = CountlyStore.createPreferencesPush(cachedContext);
        sp.edit().remove(legacyCACHED_PUSH_MESSAGING_MODE).apply();
    }

    /**
     * <pre>
     * Transitions the SDK to a state where all requests store the device ID with the request
     * This way it does not need to be added at request time.
     * This solves some race conditions and simplifies the SDK
     * -
     * "&override_id=XXX" - this should be replaced with "&device_id=XXX" as it tries to specify what device ID should be used for the request
     * "&device_id=XXX" - if this is encountered then that indicates a device ID change request. This is the new device ID. In addition to this, "&old_device_id=YYY" should be added which should get the value from the currently available device ID
     * -
     * The migration process will start at the beginning
     * And then will try to move into the future and reconstruct the device ID chain
     * If there are merge requests then the device ID needs to be updated in the SDK
     * -
     * Change requests without merging would happen instantly. if a session was recorded, it would record an end session request with a device ID override
     * Change requests with merge would happen delayed with the new value being set as "&device_id=XXX"
     * -
     * There can be 5 kinds of requests:
     * 1) request with no device ID or override
     * 2) request with device ID that is not temp
     * 3) request with override that is not temp
     * 4) request with device ID that is temp
     * 5) request with override that is temp (this might show up)
     * -
     * 12131451
     * 131451
     * 1451
     * </pre>
     *
     * @param migrationParams
     */
    void performMigration3To4(@NonNull Map<String, Object> migrationParams) {
        String currentPointDeviceID = storage.getDeviceID();

        if (currentPointDeviceID == null) {
            L.e("performMigration3To4, can't perform this migration due to the device ID being 'null'");
            return;
        }

        String oldDeviceId = "";
        String overrideCache = currentPointDeviceID;

        String[] requests = storage.getRequests();
        // this is for the looking for last merge request id, because last merge request id is not saved yet so latest device id should be it
        DeviceIdWithMerge deviceId = searchForDeviceIdInRequests(requests, requests.length - 1);

        if (deviceId.deviceId != null && deviceId.withMerge) {
            // if we have a merge request, then the last merge request is the latest device id and save it also
            // because merge is not saved yet, also current one is the old device id
            oldDeviceId = currentPointDeviceID;
            currentPointDeviceID = deviceId.deviceId;

            // this is saved because it was not saved before
            storage.setDeviceID(currentPointDeviceID);
            storage.setDeviceIDType(DeviceIdType.DEVELOPER_SUPPLIED.toString());
        }

        for (int a = requests.length - 1; a >= 0; a--) {

            Map<String, String> params = Utils.splitIntoParams(requests[a]);

            boolean containsDeviceID = params.containsKey(param_key_device_id);
            boolean containsOverrideID = params.containsKey(param_key_override_id);

            if (containsOverrideID) {
                // if we have a without merge request
                String paramDeviceId = params.remove(param_key_override_id);
                assert paramDeviceId != null;

                // if it is not a temporary device id
                if (!paramDeviceId.equals(DeviceId.temporaryCountlyDeviceId)) {
                    // update current device id to the oldest id
                    currentPointDeviceID = paramDeviceId;
                    // and cache it for possible old merge requests to use it as old device id
                    overrideCache = currentPointDeviceID;

                    // if we have without merge request, we should search for the last merge request if exists
                    // because latest merge request id is not saved to the storage yet, so we should use the latest device id as the old device id,
                    // and also we should update the current device id to the merge request id
                    deviceId = searchForDeviceIdInRequests(requests, a - 1);
                    if (deviceId.deviceId != null) {
                        if (deviceId.withMerge) {
                            oldDeviceId = overrideCache; // make current device id the old device id
                            currentPointDeviceID = deviceId.deviceId;
                        }
                    }
                }
                params.put(param_key_device_id, currentPointDeviceID);
            } else if (containsDeviceID) { // if we have a with merge request
                String paramDeviceId = params.get(param_key_device_id);
                assert paramDeviceId != null;

                if (paramDeviceId.equals(DeviceId.temporaryCountlyDeviceId)) {
                    params.put(param_key_device_id, currentPointDeviceID);
                } else {
                    // if it is not a temporary device id
                    // search for the older merge request, if exists old device id will be the older merge request's device id
                    // if not, old device id will be cached override id
                    deviceId = searchForDeviceIdInRequests(requests, a - 1);
                    if (deviceId.deviceId != null) {
                        if (deviceId.withMerge) {
                            oldDeviceId = deviceId.deviceId;
                        }
                    } else {
                        oldDeviceId = overrideCache;
                    }

                    currentPointDeviceID = oldDeviceId; // update current as old
                    // we did not add device_id param because it already exists
                    params.put(param_key_old_device_id, oldDeviceId);
                }
            } else {
                params.put(param_key_device_id, currentPointDeviceID);
            }

            requests[a] = Utils.combineParamsIntoRequest(params);
        }

        storage.replaceRequests(requests);
    }

    private static class DeviceIdWithMerge {
        String deviceId;
        boolean withMerge;

        public DeviceIdWithMerge(String deviceId, boolean withMerge) {
            this.deviceId = deviceId;
            this.withMerge = withMerge;
        }
    }

    private final DeviceIdWithMerge reusableDeviceIdWithMerge = new DeviceIdWithMerge(null, false); // to reduce object creation

    /**
     * Search for any device id request that is not a temporary device id
     * looks for "device_id" or "override_id" in the requests
     * return null if not found
     *
     * @param requests array of requests
     * @param index to start searching from
     * @return device id and if it is a merge request
     */
    private DeviceIdWithMerge searchForDeviceIdInRequests(String[] requests, int index) {
        assert requests != null;
        if (index < 0) {
            reusableDeviceIdWithMerge.deviceId = null;
            reusableDeviceIdWithMerge.withMerge = false;
            return reusableDeviceIdWithMerge;
        }

        String deviceID = null;
        boolean withMerge = true;
        for (int a = index; a >= 0; a--) {
            Map<String, String> params = Utils.splitIntoParams(requests[a]);
            if (params.containsKey(param_key_device_id)) {
                deviceID = params.get(param_key_device_id);
                assert deviceID != null;

                if (deviceID.equals(DeviceId.temporaryCountlyDeviceId)) {
                    deviceID = null;
                    continue;
                }
                deviceID = params.get(param_key_device_id);
                break;
            } else if (params.containsKey(param_key_override_id)) {
                deviceID = params.get(param_key_override_id);
                assert deviceID != null;

                if (deviceID.equals(DeviceId.temporaryCountlyDeviceId)) {
                    deviceID = null;
                    continue;
                }
                deviceID = params.get(param_key_override_id);
                withMerge = false;
                break;
            }
        }

        reusableDeviceIdWithMerge.deviceId = deviceID;
        reusableDeviceIdWithMerge.withMerge = withMerge;
        return reusableDeviceIdWithMerge;
    }
}
