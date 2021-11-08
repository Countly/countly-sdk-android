package ly.count.android.sdk;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class UserData {
    static String name;
    static String username;
    static String email;
    static String org;
    static String phone;
    static String picture;
    static String picturePath;//protected only for testing
    static String gender;
    static Map<String, String> custom;
    static Map<String, JSONObject> customMods;
    static int byear = 0;
    static boolean isSynced = true;//protected only for testing

    final RequestQueueProvider requestQueueProvider_;

    /**
     * Constructs a UserData object.
     *
     * @param requestQueueProvider to process userdata requests
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    UserData(RequestQueueProvider requestQueueProvider) {
        requestQueueProvider_ = requestQueueProvider;
    }

    /**
     * Sets information about user. Possible keys are:
     * <ul>
     * <li>name - (String) providing user's full name
     * <li>username - (String) providing user's nickname
     * <li>email - (String) providing user's email address
     * <li>organization - (String) providing user's organization's name where user works
     * <li>phone - (String) providing user's phone number
     * <li>picture - (String) providing WWW URL to user's avatar or profile picture
     * <li>picturePath - (String) providing local path to user's avatar or profile picture
     * <li>gender - (String) providing user's gender as M for male and F for female
     * <li>byear - (int) providing user's year of birth as integer
     * </ul>
     *
     * @param data Map&lt;String, String&gt; with user data
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void setUserData(Map<String, String> data) {
        setUserData(data, null);
    }

    /**
     * Sets information about user with custom properties.
     * In custom properties you can provide any string key values to be stored with user
     * Possible keys are:
     * <ul>
     * <li>name - (String) providing user's full name
     * <li>username - (String) providing user's nickname
     * <li>email - (String) providing user's email address
     * <li>organization - (String) providing user's organization's name where user works
     * <li>phone - (String) providing user's phone number
     * <li>picture - (String) providing WWW URL to user's avatar or profile picture
     * <li>picturePath - (String) providing local path to user's avatar or profile picture
     * <li>gender - (String) providing user's gender as M for male and F for female
     * <li>byear - (int) providing user's year of birth as integer
     * </ul>
     *
     * @param data Map&lt;String, String&gt; with user data
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void setUserData(Map<String, String> data, Map<String, String> customdata) {
        UserData.setData(data);
        if (customdata != null) {
            UserData.setCustomData(customdata);
        }
    }

    /**
     * Sets custom properties.
     * In custom properties you can provide any string key values to be stored with user
     *
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void setCustomUserData(Map<String, String> customdata) {
        if (customdata != null) {
            UserData.setCustomData(customdata);
        }
    }

    /**
     * Sets custom provide key/value as custom property.
     *
     * @param key String with key for the property
     * @param value String with value for the property
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void setProperty(String key, String value) {
        UserData.setCustomProperty(key, value);
    }

    /**
     * Increment custom property value by 1.
     *
     * @param key String with property name to increment
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void increment(String key) {
        ModuleUserProfile.modifyCustomData(key, 1, "$inc");
    }

    /**
     * Increment custom property value by provided value.
     *
     * @param key String with property name to increment
     * @param value int value by which to increment
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void incrementBy(String key, int value) {
        ModuleUserProfile.modifyCustomData(key, value, "$inc");
    }

    /**
     * Multiply custom property value by provided value.
     *
     * @param key String with property name to multiply
     * @param value int value by which to multiply
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void multiply(String key, int value) {
        ModuleUserProfile.modifyCustomData(key, value, "$mul");
    }

    /**
     * Save maximal value between existing and provided.
     *
     * @param key String with property name to check for max
     * @param value int value to check for max
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void saveMax(String key, int value) {
        ModuleUserProfile.modifyCustomData(key, value, "$max");
    }

    /**
     * Save minimal value between existing and provided.
     *
     * @param key String with property name to check for min
     * @param value int value to check for min
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void saveMin(String key, int value) {
        ModuleUserProfile.modifyCustomData(key, value, "$min");
    }

    /**
     * Set value only if property does not exist yet
     *
     * @param key String with property name to set
     * @param value String value to set
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void setOnce(String key, String value) {
        ModuleUserProfile.modifyCustomData(key, value, "$setOnce");
    }

    /** Create array property, if property does not exist and add value to array
     * You can only use it on array properties or properties that do not exist yet
     * @param key String with property name for array property
     * @param value String with value to add to array
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void pushValue(String key, String value) {
        ModuleUserProfile.modifyCustomData(key, value, "$push");
    }

    /** Create array property, if property does not exist and add value to array, only if value is not yet in the array
     * You can only use it on array properties or properties that do not exist yet
     * @param key String with property name for array property
     * @param value String with value to add to array
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void pushUniqueValue(String key, String value) {
        ModuleUserProfile.modifyCustomData(key, value, "$addToSet");
    }

    /** Create array property, if property does not exist and remove value from array
     * You can only use it on array properties or properties that do not exist yet
     * @param key String with property name for array property
     * @param value String with value to remove from array
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void pullValue(String key, String value) {
        ModuleUserProfile.modifyCustomData(key, value, "$pull");
    }

    /**
     * Send provided values to server
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public void save() {
        requestQueueProvider_.sendUserData(ModuleUserProfile.getDataForRequest());
        UserData.clear();
    }

    /**
     * Clear all submitted information
     *
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public static void clear() {
        name = null;
        username = null;
        email = null;
        org = null;
        phone = null;
        picture = null;
        picturePath = null;
        gender = null;
        custom = null;
        customMods = null;
        byear = 0;
        isSynced = true;
    }

    /**
     * Sets user data values.
     *
     * @param data Map with user data
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public static void setData(Map<String, String> data) {
        if (data.containsKey(ModuleUserProfile.NAME_KEY)) {
            name = data.get(ModuleUserProfile.NAME_KEY);
        }
        if (data.containsKey(ModuleUserProfile.USERNAME_KEY)) {
            username = data.get(ModuleUserProfile.USERNAME_KEY);
        }
        if (data.containsKey(ModuleUserProfile.EMAIL_KEY)) {
            email = data.get(ModuleUserProfile.EMAIL_KEY);
        }
        if (data.containsKey(ModuleUserProfile.ORG_KEY)) {
            org = data.get(ModuleUserProfile.ORG_KEY);
        }
        if (data.containsKey(ModuleUserProfile.PHONE_KEY)) {
            phone = data.get(ModuleUserProfile.PHONE_KEY);
        }
        if (data.containsKey(ModuleUserProfile.PICTURE_PATH_KEY)) {
            picturePath = data.get(ModuleUserProfile.PICTURE_PATH_KEY);
        }
        if (picturePath != null) {
            File sourceFile = new File(picturePath);
            if (!sourceFile.isFile()) {
                Countly.sharedInstance().L.w("[UserData] Provided Picture path file [" + picturePath + "] can not be opened");
                picturePath = null;
            }
        }
        if (data.containsKey(ModuleUserProfile.PICTURE_KEY)) {
            picture = data.get(ModuleUserProfile.PICTURE_KEY);
        }
        if (data.containsKey(ModuleUserProfile.GENDER_KEY)) {
            gender = data.get(ModuleUserProfile.GENDER_KEY);
        }
        if (data.containsKey(ModuleUserProfile.BYEAR_KEY)) {
            try {
                byear = Integer.parseInt(data.get(ModuleUserProfile.BYEAR_KEY));
            } catch (NumberFormatException e) {
                Countly.sharedInstance().L.w("[UserData] Incorrect byear number format");
                byear = 0;
            }
        }
        isSynced = false;
    }

    /**
     * Sets user custom properties and values.
     *
     * @param data Map with user custom key/values
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public static void setCustomData(Map<String, String> data) {
        if (custom == null) {
            custom = new HashMap<>();
        }
        custom.putAll(data);
        isSynced = false;
    }

    /**
     * Sets custom provide key/value as custom property.
     *
     * @param key String with key for the property
     * @param value String with value for the property
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public static void setCustomProperty(String key, String value) {
        if (custom == null) {
            custom = new HashMap<>();
        }
        custom.put(key, value);
        isSynced = false;
    }
}