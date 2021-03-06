package ly.count.android.sdk;

import android.content.Context;

public class DeviceId {
    /**
     * Enum used throughout Countly which controls what kind of ID Countly should use.
     */
    public enum Type {
        DEVELOPER_SUPPLIED,//custom value provided by the developer
        OPEN_UDID,//OPEN_UDID generated UDID
        ADVERTISING_ID,//id provided by the android OS
        TEMPORARY_ID,//temporary device ID mode
    }

    protected final static String temporaryCountlyDeviceId = "CLYTemporaryDeviceID";

    private String id = null;
    private Type type;

    ModuleLog L;

    StorageProvider storageProvider;
    OpenUDIDProvider openUDIDProvider;

    /**
     * Initialize DeviceId with Type of OPEN_UDID or ADVERTISING_ID
     *
     * @param type type of ID generation strategy
     */
    protected DeviceId(StorageProvider givenStorageProvider, Type type, ModuleLog moduleLog, OpenUDIDProvider openUDIDProvider) {
        if (type == null) {
            throw new IllegalStateException("Please specify DeviceId.Type, that is which type of device ID generation you want to use");
        } else if (type == Type.DEVELOPER_SUPPLIED) {
            throw new IllegalStateException("Please use another DeviceId constructor for device IDs supplied by developer");
        }
        storageProvider = givenStorageProvider;
        this.openUDIDProvider = openUDIDProvider;

        //setup the preferred device ID type
        this.type = type;
        L = moduleLog;

        L.d("[DeviceId] initialising with no values, provided type:[" + this.type + "]");

        //check if there wasn't a value set before
        retrieveId();
    }

    /**
     * Initialize DeviceId with Developer-supplied id string
     *
     * @param developerSuppliedId Device ID string supplied by developer
     */
    protected DeviceId(StorageProvider givenStorageProvider, String developerSuppliedId, ModuleLog moduleLog, OpenUDIDProvider openUDIDProvider) {
        if (developerSuppliedId == null || "".equals(developerSuppliedId)) {
            throw new IllegalStateException("Please make sure that device ID is not null or empty");
        }
        storageProvider = givenStorageProvider;
        this.openUDIDProvider = openUDIDProvider;

        //setup the preferred device ID type
        this.type = Type.DEVELOPER_SUPPLIED;
        this.id = developerSuppliedId;
        L = moduleLog;

        L.d("[DeviceId] initialising with values, device ID:[" + this.id + "] type:[" + this.type + "]");

        //check if there wasn't a value set before
        retrieveId();
    }

    /**
     * Used during setup to retrieve the previously saved value
     */
    private void retrieveId() {
        //check if there is some stored value
        String storedId = storageProvider.getDeviceID();
        if (storedId != null) {
            //if there was a value saved previously, set it and it's type. Overwrite the in constructor set ones
            this.id = storedId;
            this.type = retrieveType();

            L.d("[DeviceId] retrieveId, Retrieving a previously set device ID:[" + this.id + "] type:[" + this.type + "]");
        } else {
            L.d("[DeviceId] retrieveId, no previous ID stored");
        }
    }

    /**
     * Initialize device ID generation, that is start up required services and send requests.
     * Device ID is expected to be available after some time.
     * In some cases, Countly can override ID generation strategy to other one, for example when
     * Google Play Services are not available and user chose Advertising ID strategy, it will fall
     * back to OpenUDID
     *
     * @param context Context to use
     */
    protected void init(Context context) {
        Type storedType = retrieveType();
        L.d("[DeviceId] init, current type:[" + type + "] overriddenType:[" + storedType + "]");

        // Some time ago some ID generation strategy was not available and SDK fell back to
        // some other strategy. We still have to use that strategy.
        if (storedType != null && storedType != type) {
            L.i("[DeviceId] init, Overridden device ID generation strategy detected: " + storedType + ", using it instead of " + this.type);
            type = storedType;
        }

        if(type == null){
            L.e("[DeviceId] init, device id type currently is null, falling back to OPEN_UDID");
            type = Type.OPEN_UDID;
        }

        String storedID = storageProvider.getDeviceID();


        if(storedID == null) {
            //id value will be regenerated only if the values isn't already set
            //this is to prevent the device id to change in case the underlying mechanism for openUDID or advertising ID changes
            switch (type) {
                case DEVELOPER_SUPPLIED:
                    // no initialization for developer id
                    // we just store the provided value so that it's
                    setAndStoreId(Type.DEVELOPER_SUPPLIED, id);
                    break;
                case OPEN_UDID:
                    L.i("[DeviceId] Using OpenUDID");
                    fallbackToOpenUDID();
                    break;
                case ADVERTISING_ID:
                    if (AdvertisingIdAdapter.isAdvertisingIdAvailable()) {
                        L.i("[DeviceId] Using Advertising ID");
                        AdvertisingIdAdapter.setAdvertisingId(context, this);
                    } else {
                        // Fall back to OpenUDID on devices without google play services set up
                        L.i("[DeviceId] Advertising ID is not available, falling back to OpenUDID");
                        fallbackToOpenUDID();
                    }
                    break;
            }
        }
    }

    /**
     * Retrieved stored device ID type
     * @return
     */
    private Type retrieveType() {
        // Using strings is safer when it comes to extending Enum values list
        String typeString = storageProvider.getDeviceIDType();
        if (typeString == null) {
            return null;
        } else if (typeString.equals(Type.DEVELOPER_SUPPLIED.toString())) {
            return Type.DEVELOPER_SUPPLIED;
        } else if (typeString.equals(Type.OPEN_UDID.toString())) {
            return Type.OPEN_UDID;
        } else if (typeString.equals(Type.ADVERTISING_ID.toString())) {
            return Type.ADVERTISING_ID;
        } else if (typeString.equals(Type.TEMPORARY_ID.toString())) {
            return Type.TEMPORARY_ID;
        } else {
            L.e("[DeviceId] device ID type can't be determined");
            return null;
        }
    }

    protected String getId() {
        if (id == null && type == Type.OPEN_UDID) {
            //using openUDID as a fallback
            id = OpenUDIDAdapter.OpenUDID;
        }
        return id;
    }

    @SuppressWarnings("SameParameterValue")
    protected void setId(Type type, String id) {
        L.w("[DeviceId] Device ID is " + id + " (type " + type + ")");
        this.type = type;
        this.id = id;
    }

    protected void fallbackToOpenUDID() {
        setAndStoreId(Type.OPEN_UDID, openUDIDProvider.getOpenUDID());
    }

    /**
     * If a value is provided, it will take precedence and will not used no matter what the type is
     * @param context
     * @param type
     * @param deviceId
     * @param runInit
     */
    protected void changeToId(Context context, Type type, String deviceId, boolean runInit) {
        setAndStoreId(type, deviceId);
        if(runInit) {
            init(context);
        }
    }

    void setAndStoreId(Type type, String deviceId) {
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
    protected Type getType() {
        if (temporaryIdModeEnabled()) {
            return Type.TEMPORARY_ID;
        }
        return type;
    }

    /**
     * Checks if temporary device ID mode is enabled by checking the currently set ID
     *
     * @return
     */
    protected boolean temporaryIdModeEnabled() {
        String id = getId();
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
    static boolean deviceIDEqualsNullSafe(final String id, Type type, final DeviceId deviceId) {
        if (type == null || type == Type.DEVELOPER_SUPPLIED) {
            //going here if no type is provided or type is developer supplied
            final String deviceIdId = deviceId == null ? null : deviceId.getId();
            return (deviceIdId == null && id == null) || (deviceIdId != null && deviceIdId.equals(id));
        } else {
            //if type is provided, but it is not developer supplied
            return true;
        }
    }
}
