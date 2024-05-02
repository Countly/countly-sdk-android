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

    static final public String key_from_0_to_1_custom_id_set = "0_1_custom_id_set";

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
                Countly.sharedInstance().L.e("[MigrationHelper] performMigration1To2, transforming remote config values, " + e.toString());
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
     * Transitions the SDK to a state where all requests store the device ID with the request
     * This way it does not need to tbe added at request time.
     * This solves some race conditions and simplifies the SDK
     *
     * "&override_id=XXX" - this should be replaced with "&device_id=XXX" as it tries to specify what device ID should be used for the request
     * "&device_id=XXX" - if this is encountered then that indicates a device ID change request. This is the new device ID. In addition to this, "&old_device_id=YYY" should be added which should get the value from the currently available device ID
     *
     * The migration process will start at the beginning
     * And then will try to move into the future and reconstruct the device ID chain
     * If there are merge requests then the device ID needs to be updated in the SDK
     *
     * Change requests without merging would happen instantly. if a session was recorded, it would record an end session request with a device ID override
     * Change requests with merge would happen delayed with the new value being set as "&device_id=XXX"
     *
     *
     * There can be 5 kinds of requests:
     * 1) request with no device ID or override
     * 2) request with device ID that is not temp
     * 3) request with override that is not temp
     * 4) request with device ID that is temp
     * 5) request with override that is temp (this might show up)
     *
     * 12131451
     * 131451
     * 1451
     *
     * @param migrationParams
     */
    void performMigration3To4(@NonNull Map<String, Object> migrationParams) {
        String currentPointDeviceID = storage.getDeviceID();
        if (currentPointDeviceID == null) {
            Countly.sharedInstance().L.e("performMigration3To4, can't perform this migration due to the device ID being 'null'");
            return;
        }

        String[] requests = storage.getRequests();

        for (int a = requests.length - 1; a >= 0; a--) {

            Map<String, String> params = Utils.splitIntoParams(requests[a]);

            boolean containsDeviceID = params.containsKey(param_key_device_id);
            boolean containsOverrideID = params.containsKey(param_key_override_id);

            if (!containsOverrideID && !containsDeviceID) {
                //if there is no device ID or override tag, we just set the current device ID
                params.put(param_key_device_id, currentPointDeviceID);
            } else if (containsOverrideID && !containsDeviceID) {
                //if there is a override tag, then we use it as the device ID and ignore the current point ID
                params.put(param_key_device_id, params.get(param_key_override_id));//set it
                params.remove(param_key_override_id);//use it
            } else if (!containsOverrideID && containsDeviceID) {
                // it contains a device ID value but no override
                // this would be a merge request

            } else if (containsOverrideID && containsDeviceID) {

            } else {
                Countly.sharedInstance().L.e("performMigration3To4, how did you even get here? " + containsDeviceID + " " + containsOverrideID);
            }

            requests[a] = Utils.combineParamsIntoRequest(params);
        }

        storage.replaceRequests(requests);
    }

    public static final String param_key_device_id = "device_id";
    public static final String param_key_override_id = "override_id";
}
