package ly.count.android.sdk;

/**
 * Enum used throughout Countly which controls what kind of ID Countly should use.
 */
public enum DeviceIdType {
    DEVELOPER_SUPPLIED,//custom value provided by the developer
    OPEN_UDID,//OPEN_UDID generated UDID
    TEMPORARY_ID,//temporary device ID mode
}
