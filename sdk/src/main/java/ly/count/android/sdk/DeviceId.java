package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DeviceId {
    /**
     * Enum used throughout Countly which controls what kind of ID Countly should use.
     *
     * @deprecated Replace this type with "DeviceIdType"
     */
    public enum Type {
        DEVELOPER_SUPPLIED,//custom value provided by the developer
        OPEN_UDID,//OPEN_UDID generated UDID
        /**
         * @deprecated The usage of this device_ID type is deprecated. It will be removed in the future
         */
        ADVERTISING_ID,//id provided by the android OS
        TEMPORARY_ID,//temporary device ID mode
    }

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

    protected DeviceId(@NonNull DeviceIdType providedType, @Nullable String providedId, @NonNull StorageProvider givenStorageProvider, @NonNull ModuleLog moduleLog, @NonNull OpenUDIDProvider openUDIDProvider) {
        if (providedType == DeviceIdType.DEVELOPER_SUPPLIED) {
            if (providedId == null || "".equals(providedId)) {
                throw new IllegalStateException("If using developer supplied type, a valid device ID should be provided, [" + providedId + "]");
            }
        } else if (providedType == DeviceIdType.TEMPORARY_ID) {
            if (providedId == null || "".equals(providedId)) {
                throw new IllegalStateException("If using temporary ID type, a valid device ID should be provided, [" + providedId + "]");
            }

            if (!providedId.equals(temporaryCountlyDeviceId)) {
                throw new IllegalStateException("If using temporary ID type, the device ID value should be the required one, [" + providedId + "]");
            }
        } else if (providedType == DeviceIdType.OPEN_UDID || providedType == DeviceIdType.ADVERTISING_ID) {
            //just adding this check for completeness
        } else {
            throw new IllegalStateException("Null device ID type is not allowed");
        }

        storageProvider = givenStorageProvider;
        this.openUDIDProvider = openUDIDProvider;
        L = moduleLog;

        L.d("[DeviceId-int] initialising with values, device ID:[" + providedId + "] type:[" + providedType + "]");

        //check if there wasn't a value set before. Read if from storage
        String storedId = storageProvider.getDeviceID();
        DeviceIdType storedType = retrieveStoredType();

        L.d("[DeviceId-int] The following values were stored, device ID:[" + storedId + "] type:[" + storedType + "]");

        if (storedId != null && storedType != null) {
            //values are set, just use them and ignore the provided ones
            id = storedId;
            type = storedType;
        } else {
            if (storedType == null && storedId != null) {
                L.e("[DeviceId-int] init, device id type currently is null, falling back to OPEN_UDID");
                setAndStoreId(DeviceIdType.OPEN_UDID, storedId);
            }

            if (storedId == null) {
                //id value will be regenerated only if the values isn't already set
                //this is to prevent the device id to change in case the underlying mechanism for openUDID or advertising ID changes
                switch (providedType) {
                    case TEMPORARY_ID:
                        setAndStoreId(DeviceIdType.TEMPORARY_ID, providedId);
                        break;
                    case DEVELOPER_SUPPLIED:
                        // no initialization for developer id
                        // we just store the provided value so that it's
                        setAndStoreId(DeviceIdType.DEVELOPER_SUPPLIED, providedId);
                        break;
                    case OPEN_UDID:
                        L.i("[DeviceId-int] Using OpenUDID");
                        setAndStoreId(DeviceIdType.OPEN_UDID, openUDIDProvider.getOpenUDID());
                        break;
                    case ADVERTISING_ID:
                        // Fall back to OpenUDID on devices without google play services set up
                        L.i("[DeviceId-int] Use of Advertising ID is deprecated, falling back to OpenUDID");
                        setAndStoreId(DeviceIdType.OPEN_UDID, openUDIDProvider.getOpenUDID());
                        break;
                }
            }
        }
    }

    /**
     * Retrieved stored device ID type
     *
     * @return The currently stored data type
     */
    private DeviceIdType retrieveStoredType() {
        // Using strings is safer when it comes to extending Enum values list
        String typeString = storageProvider.getDeviceIDType();
        if (typeString == null) {
            return null;
        } else if (typeString.equals(DeviceIdType.DEVELOPER_SUPPLIED.toString())) {
            return DeviceIdType.DEVELOPER_SUPPLIED;
        } else if (typeString.equals(DeviceIdType.OPEN_UDID.toString())) {
            return DeviceIdType.OPEN_UDID;
        } else if (typeString.equals(DeviceIdType.ADVERTISING_ID.toString())) {
            return DeviceIdType.ADVERTISING_ID;
        } else if (typeString.equals(DeviceIdType.TEMPORARY_ID.toString())) {
            return DeviceIdType.TEMPORARY_ID;
        } else {
            L.e("[DeviceId-int] device ID type can't be determined");
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
        L.v("[DeviceId-int] changeToCustomId, Device ID is " + id);
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
