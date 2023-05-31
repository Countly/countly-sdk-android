package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DeviceId {
    protected final static String temporaryCountlyDeviceId = "CLYTemporaryDeviceID";

    @Nullable
    private String id;

    @Nullable
    private DeviceIdType type;

    ModuleLog L;

    @NonNull
    StorageProvider storageProvider;

    @NonNull
    OpenUDIDProvider openUDIDProvider;

    protected DeviceId(@Nullable String providedId, @NonNull StorageProvider givenStorageProvider, @NonNull ModuleLog moduleLog, @NonNull OpenUDIDProvider openUDIDProvider) {
        if ("".equals(providedId)) {
            throw new IllegalStateException("Empty device ID is not a valid value, [" + providedId + "]");
        }

        storageProvider = givenStorageProvider;
        this.openUDIDProvider = openUDIDProvider;
        L = moduleLog;

        L.d("[DeviceId-int] initialising with values, device ID:[" + providedId + "]");

        //check if there wasn't a value set before. Read if from storage
        String storedId = storageProvider.getDeviceID();
        DeviceIdType storedType = retrieveStoredType();

        L.d("[DeviceId-int] The following values were stored, device ID:[" + storedId + "] type:[" + storedType + "]");

        if (storedId != null && storedType != null) {
            //values are set, just use them and ignore the provided ones
            id = storedId;
            type = storedType;
        } else {
            //if either the type or value are 'null'

            if (storedType == null && storedId != null) {
                // if we know that only the type is 'null'
                // that would mean that either there is no value or a old type is stored
                // In that case fallback to OPEN_UDID
                L.e("[DeviceId-int] init, device id type currently is 'null', falling back to OPEN_UDID");
                setAndStoreId(DeviceIdType.OPEN_UDID, storedId);
            }

            if (storedId == null) {
                // if we reach here then that means that either the value is 'null' or both value and type were 'null'
                // In this case we will regenerate the value and set the type accordingly

                if (providedId == null) {
                    //if the provided ID is 'null' then that means that a new ID must be generated
                    L.i("[DeviceId-int] Using OpenUDID");
                    setAndStoreId(DeviceIdType.OPEN_UDID, openUDIDProvider.getOpenUDID());
                } else if (providedId.equals(temporaryCountlyDeviceId)) {
                    L.i("[DeviceId-int] Entering temp ID mode");

                    setAndStoreId(DeviceIdType.TEMPORARY_ID, providedId);
                } else {
                    //it's a non null value that is not empty string
                    // use it as the developer provided device ID value

                    L.i("[DeviceId-int] Using dev provided ID");
                    setAndStoreId(DeviceIdType.DEVELOPER_SUPPLIED, providedId);
                }
            }
        }
    }

    /**
     * Retrieved stored device ID type
     *
     * @return The currently stored data type
     */
    @Nullable private DeviceIdType retrieveStoredType() {
        // Using strings is safer when it comes to extending Enum values list
        String typeString = storageProvider.getDeviceIDType();
        if (typeString == null) {
            return null;
        } else if (typeString.equals(DeviceIdType.DEVELOPER_SUPPLIED.toString())) {
            return DeviceIdType.DEVELOPER_SUPPLIED;
        } else if (typeString.equals(DeviceIdType.OPEN_UDID.toString())) {
            return DeviceIdType.OPEN_UDID;
        } else if (typeString.equals(DeviceIdType.TEMPORARY_ID.toString())) {
            return DeviceIdType.TEMPORARY_ID;
        } else {
            L.e("[DeviceId-int] device ID type can't be determined, [" + typeString + "]");
            return null;
        }
    }

    protected String getCurrentId() {
        if (id == null && type == DeviceIdType.OPEN_UDID) {
            //using openUDID as a fallback
            id = openUDIDProvider.getOpenUDID();
        }
        return id;
    }

    /**
     * Used only for tests
     *
     * @param type
     * @param id
     */
    @SuppressWarnings("SameParameterValue")
    protected void setId(DeviceIdType type, String id) {
        L.v("[DeviceId-int] setId, Device ID is " + id + " (type " + type + ")");
        this.type = type;
        this.id = id;
    }

    /**
     * If a value is provided, it will take precedence and will not matter what the type is
     *
     * @param deviceId
     */
    protected void changeToCustomId(@NonNull String deviceId) {
        L.v("[DeviceId-int] changeToCustomId, current Device ID is [" + id + "] new ID is[" + deviceId + "]");
        setAndStoreId(DeviceIdType.DEVELOPER_SUPPLIED, deviceId);
    }

    protected void enterTempIDMode() {
        L.v("[DeviceId-int] enterTempIDMode");
        setAndStoreId(DeviceIdType.DEVELOPER_SUPPLIED, ly.count.android.sdk.DeviceId.temporaryCountlyDeviceId);
    }

    void setAndStoreId(DeviceIdType type, String deviceId) {
        this.id = deviceId;
        this.type = type;

        storageProvider.setDeviceID(deviceId);
        storageProvider.setDeviceIDType(type.toString());
    }

    /**
     * Returns the current type which would be returned to the developer
     *
     * @return Currently used device ID type
     */
    protected DeviceIdType getType() {
        if (isTemporaryIdModeEnabled()) {
            return DeviceIdType.TEMPORARY_ID;
        }
        return type;
    }

    /**
     * Checks if temporary device ID mode is enabled by checking the currently set ID
     *
     * @return
     */
    protected boolean isTemporaryIdModeEnabled() {
        String id = getCurrentId();
        if (id == null) {
            return false;
        }

        return id.equals(temporaryCountlyDeviceId);
    }
}
