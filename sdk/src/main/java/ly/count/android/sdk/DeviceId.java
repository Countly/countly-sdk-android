package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DeviceId {
    /**
     * Enum used throughout Countly which controls what kind of ID Countly should use.
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

    protected DeviceId(@NonNull DeviceIdType type, @Nullable String developerSuppliedId, @NonNull StorageProvider givenStorageProvider, @NonNull ModuleLog moduleLog, @NonNull OpenUDIDProvider openUDIDProvider) {
        if(type == DeviceIdType.DEVELOPER_SUPPLIED) {
            if (developerSuppliedId == null || "".equals(developerSuppliedId)) {
                throw new IllegalStateException("If using developer supplied type, a valid device ID should be provided, [" + developerSuppliedId + "]");
            }
        } else if(type == DeviceIdType.TEMPORARY_ID) {
            if (developerSuppliedId == null || "".equals(developerSuppliedId)) {
                throw new IllegalStateException("If using temporary ID type, a valid device ID should be provided, [" + developerSuppliedId + "]");
            }

            if(!developerSuppliedId.equals(temporaryCountlyDeviceId)) {
                throw new IllegalStateException("If using temporary ID type, the device ID value should be the required one, [" + developerSuppliedId + "]");
            }

        } else if(type == DeviceIdType.OPEN_UDID || type == DeviceIdType.ADVERTISING_ID){

        } else {
            throw new IllegalStateException("Null device ID type is not allowed");
        }

        storageProvider = givenStorageProvider;
        this.openUDIDProvider = openUDIDProvider;

        //setup the preferred device ID type
        this.type = type;
        this.id = developerSuppliedId;
        L = moduleLog;


        L.d("[DeviceId-int] initialising with values, device ID:[" + this.id + "] type:[" + this.type + "]");

        //check if there wasn't a value set before
        String storedId = storageProvider.getDeviceID();
        if (storedId != null) {
            //if there was a value saved previously, set it and it's type. Overwrite the in constructor set ones
            this.id = storedId;
            this.type = retrieveStoredType();

            L.d("[DeviceId-int] retrieveId, Retrieving a previously set device ID:[" + this.id + "] type:[" + this.type + "]");
        } else {
            L.d("[DeviceId-int] retrieveId, no previous ID stored");
        }
    }

    /**
     * Initialize device ID generation, that is start up required services and send requests.
     * Device ID is expected to be available after some time.
     * In some cases, Countly can override ID generation strategy to other one, for example when
     * Google Play Services are not available and user chose Advertising ID strategy, it will fall
     * back to OpenUDID
     */
    protected void init() {
        DeviceIdType storedType = retrieveStoredType();
        L.d("[DeviceId-int] init, current type:[" + type + "] overriddenType:[" + storedType + "]");

        // Some time ago some ID generation strategy was not available and SDK fell back to
        // some other strategy. We still have to use that strategy.
        if (storedType != null && storedType != type) {
            L.i("[DeviceId-int] init, Overridden device ID generation strategy detected: " + storedType + ", using it instead of " + this.type);
            type = storedType;
        }

        if (type == null) {
            L.e("[DeviceId-int] init, device id type currently is null, falling back to OPEN_UDID");
            type = DeviceIdType.OPEN_UDID;
        }

        String storedID = storageProvider.getDeviceID();

        if (storedID == null) {
            //id value will be regenerated only if the values isn't already set
            //this is to prevent the device id to change in case the underlying mechanism for openUDID or advertising ID changes
            switch (type) {
                case DEVELOPER_SUPPLIED:
                    // no initialization for developer id
                    // we just store the provided value so that it's
                    setAndStoreId(DeviceIdType.DEVELOPER_SUPPLIED, id);
                    break;
                case OPEN_UDID:
                    L.i("[DeviceId-int] Using OpenUDID");
                    fallbackToOpenUDID();
                    break;
                case ADVERTISING_ID:
                    // Fall back to OpenUDID on devices without google play services set up
                    L.i("[DeviceId-int] Use of Advertising ID is deprecated, falling back to OpenUDID");
                    fallbackToOpenUDID();
                    break;
            }
        }
    }

    /**
     * Retrieved stored device ID type
     *
     * @return
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
     * @param type
     * @param id
     */
    @SuppressWarnings("SameParameterValue")
    protected void setId(DeviceIdType type, String id) {
        L.v("[DeviceId-int] setId, Device ID is " + id + " (type " + type + ")");
        this.type = type;
        this.id = id;
    }

    protected void fallbackToOpenUDID() {
        setAndStoreId(DeviceIdType.OPEN_UDID, openUDIDProvider.getOpenUDID());
    }

    /**
     * If a value is provided, it will take precedence and will not used no matter what the type is
     *
     * @param type
     * @param deviceId
     * @param runInit
     */
    protected void changeToId(@NonNull DeviceIdType type, @Nullable String deviceId, boolean runInit) {
        L.v("[DeviceId-int] changeToId, Device ID is " + id + " (type " + type + "), init:" + runInit);
        setAndStoreId(type, deviceId);
        if (runInit) {
            init();
        }
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
     * @return
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

    /**
     * Helper method for null safe comparison of current device ID and the one supplied to Countly.init
     *
     * @return true if supplied device ID equal to the one registered before
     */
    static boolean deviceIDEqualsNullSafe(final String id, DeviceIdType type, final DeviceId deviceId) {
        if (type == null || type == DeviceIdType.DEVELOPER_SUPPLIED) {
            //going here if no type is provided or type is developer supplied
            final String deviceIdId = deviceId == null ? null : deviceId.getCurrentId();
            return (deviceIdId == null && id == null) || (deviceIdId != null && deviceIdId.equals(id));
        } else {
            //if type is provided, but it is not developer supplied
            return true;
        }
    }
}
