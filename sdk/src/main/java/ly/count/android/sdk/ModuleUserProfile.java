package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class ModuleUserProfile extends ModuleBase {
    static final String NAME_KEY = "name";
    static final String USERNAME_KEY = "username";
    static final String EMAIL_KEY = "email";
    static final String ORG_KEY = "organization";
    static final String PHONE_KEY = "phone";
    static final String PICTURE_KEY = "picture";//the one sending the url
    static final String PICTURE_PATH_KEY = "picturePath";//path to a local file
    static final String GENDER_KEY = "gender";
    static final String BYEAR_KEY = "byear";
    static final String CUSTOM_KEY = "custom";

    String[] namedFields = { NAME_KEY, USERNAME_KEY, EMAIL_KEY, ORG_KEY, PHONE_KEY, PICTURE_KEY, PICTURE_PATH_KEY, GENDER_KEY, BYEAR_KEY };

    boolean isSynced = true;

    JSONObject dataStore = new JSONObject();

    UserProfile userProfileInterface;

    //fields from the old object
    String name;
    String username;
    String email;
    String org;
    String phone;
    String picture;
    static String picturePath;//protected only for testing
    String gender;
    Map<String, String> custom;
    Map<String, JSONObject> customMods;
    int byear = 0;

    ModuleUserProfile(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleUserProfile] Initialising");

        userProfileInterface = new UserProfile();
    }

    /**
     * Returns &user_details= prefixed url to add to request data when making request to server
     *
     * @return a String user_details url part with provided user data
     */
    String getDataForRequest() {
        if (!isSynced) {
            isSynced = true;
            final JSONObject json = toJSON();
            if (json != null) {
                String result = json.toString();

                try {
                    result = java.net.URLEncoder.encode(result, "UTF-8");

                    if (result != null && !result.equals("")) {
                        result = "&user_details=" + result;
                        if (picturePath != null) {
                            result += "&" + PICTURE_PATH_KEY + "=" + java.net.URLEncoder.encode(picturePath, "UTF-8");
                        }
                    } else {
                        result = "";
                        if (picturePath != null) {
                            result += "&user_details&" + PICTURE_PATH_KEY + "=" + java.net.URLEncoder.encode(picturePath, "UTF-8");
                        }
                    }
                } catch (UnsupportedEncodingException ignored) {
                    // should never happen because Android guarantees UTF-8 support
                }

                if (result != null) {
                    return result;
                }
            }
        }
        return "";
    }

    /**
     * Creates and returns a JSONObject containing the user data from this object.
     *
     * @return a JSONObject containing the user data from this object
     */
    protected JSONObject toJSON() {
        final JSONObject json = new JSONObject();

        try {
            if (name != null) {
                if (name.equals("")) {
                    json.put(NAME_KEY, JSONObject.NULL);
                } else {
                    json.put(NAME_KEY, name);
                }
            }
            if (username != null) {
                if (username.equals("")) {
                    json.put(USERNAME_KEY, JSONObject.NULL);
                } else {
                    json.put(USERNAME_KEY, username);
                }
            }
            if (email != null) {
                if (email.equals("")) {
                    json.put(EMAIL_KEY, JSONObject.NULL);
                } else {
                    json.put(EMAIL_KEY, email);
                }
            }
            if (org != null) {
                if (org.equals("")) {
                    json.put(ORG_KEY, JSONObject.NULL);
                } else {
                    json.put(ORG_KEY, org);
                }
            }
            if (phone != null) {
                if (phone.equals("")) {
                    json.put(PHONE_KEY, JSONObject.NULL);
                } else {
                    json.put(PHONE_KEY, phone);
                }
            }
            if (picture != null) {
                if (picture.equals("")) {
                    json.put(PICTURE_KEY, JSONObject.NULL);
                } else {
                    json.put(PICTURE_KEY, picture);
                }
            }
            if (gender != null) {
                if (gender.equals("")) {
                    json.put(GENDER_KEY, JSONObject.NULL);
                } else {
                    json.put(GENDER_KEY, gender);
                }
            }
            if (byear != 0) {
                if (byear > 0) {
                    json.put(BYEAR_KEY, byear);
                } else {
                    json.put(BYEAR_KEY, JSONObject.NULL);
                }
            }

            JSONObject ob;
            if (custom != null) {
                ob = new JSONObject(custom);
            } else {
                ob = new JSONObject();
            }
            if (customMods != null) {
                for (Map.Entry<String, JSONObject> entry : customMods.entrySet()) {
                    ob.put(entry.getKey(), entry.getValue());
                }
            }
            json.put(CUSTOM_KEY, ob);
        } catch (JSONException e) {
            Countly.sharedInstance().L.w("[UserData] Got exception converting an UserData to JSON", e);
        }

        return json;
    }

    /**
     * Sets user data fields to values from its JSON representation.
     *
     * @param json JSON object to extract event data from
     */
    void fromJSON(final JSONObject json) {
        if (json != null) {
            name = json.optString(NAME_KEY, null);
            username = json.optString(USERNAME_KEY, null);
            email = json.optString(EMAIL_KEY, null);
            org = json.optString(ORG_KEY, null);
            phone = json.optString(PHONE_KEY, null);
            picture = json.optString(PICTURE_KEY, null);
            gender = json.optString(GENDER_KEY, null);
            byear = json.optInt(BYEAR_KEY, 0);
            if (!json.isNull(CUSTOM_KEY)) {
                JSONObject customJson;
                try {
                    customJson = json.getJSONObject(CUSTOM_KEY);
                    if (customJson.length() == 0) {
                        custom = null;
                    } else {
                        custom = new HashMap<>(customJson.length());
                        Iterator<String> nameItr = customJson.keys();
                        while (nameItr.hasNext()) {
                            final String key = nameItr.next();
                            if (!customJson.isNull(key)) {
                                custom.put(key, customJson.getString(key));
                            }
                        }
                    }
                } catch (JSONException e) {
                    Countly.sharedInstance().L.w("[ModuleUserProfile] Got exception converting an Custom Json to Custom User data", e);
                }
            }
        }
    }

    /**
     * Atomic modifications on custom user property.
     *
     * @param key String with property name to modify
     * @param value String value to use in modification
     * @param mod String with modification command
     */
    void modifyCustomData(String key, Object value, String mod) {
        try {
            if (!(value instanceof Double || value instanceof Integer || value instanceof String)) {
                Countly.sharedInstance().L.w("[ModuleUserProfile] modifyCustomDataCommon, provided an unsupported type for 'value'");
                return;
            }

            if (customMods == null) {
                customMods = new HashMap<>();
            }
            JSONObject ob;
            if (!mod.equals("$pull") && !mod.equals("$push") && !mod.equals("$addToSet")) {
                ob = new JSONObject();
                ob.put(mod, value);
            } else {
                if (customMods.containsKey(key)) {
                    ob = customMods.get(key);
                } else {
                    ob = new JSONObject();
                }
                ob.accumulate(mod, value);
            }
            customMods.put(key, ob);
            isSynced = false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * This mainly performs the filtering of provided values
     * This single call would be used for both predefined properties and custom user properties
     *
     * @param data
     */
    void setPropertiesInternal(@NonNull Map<String, Object> data) {
        if (data.size() == 0) {
            Countly.sharedInstance().L.w("[ModuleUserProfile] setPropertiesInternal, no data was provided");
            return;
        }

        //todo recheck if in the future these can be <String, Object>
        Map<String, String> dataNamedFields = new HashMap<>();
        Map<String, String> dataCustomFields = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean isNamed = false;

            for (String namedField : namedFields) {
                if (namedField.equals(key)) {
                    //if it's a name field
                    isNamed = true;
                    dataNamedFields.put(key, value.toString());
                    break;
                }
            }

            if (!isNamed) {
                dataCustomFields.put(key, value.toString());
            }
        }

        //setting predefined properties
        setData(dataNamedFields);

        //setting custom properties
        if (custom == null) {
            custom = new HashMap<>();
        }
        custom.putAll(dataCustomFields);

        isSynced = false;
    }

    /**
     * Sets user data values.
     *
     * @param data Map with user data
     */
    public void setData(Map<String, String> data) {
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
    }

    void saveInternal() {
        Countly.sharedInstance().L.d("[ModuleUserProfile] saveInternal");
        requestQueueProvider.sendUserData(getDataForRequest());
        clearInternal();
    }

    void clearInternal() {
        Countly.sharedInstance().L.d("[ModuleUserProfile] clearInternal");

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

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        if (config.providedUserProperties != null) {
            L.i("[ModuleUserProfile] Custom user properties were provided during init [" + config.providedUserProperties.size() + "]");
            setPropertiesInternal(config.providedUserProperties);
            saveInternal();
        }
    }

    @Override
    void halt() {
        userProfileInterface = null;
    }

    public class UserProfile {
        /**
         * Increment custom property value by 1.
         *
         * @param key String with property name to increment
         */
        public void increment(String key) {
            synchronized (_cly) {
                modifyCustomData(key, 1, "$inc");
            }
        }

        /**
         * Increment custom property value by provided value.
         *
         * @param key String with property name to increment
         * @param value int value by which to increment
         */
        public void incrementBy(String key, int value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$inc");
            }
        }

        /**
         * Multiply custom property value by provided value.
         *
         * @param key String with property name to multiply
         * @param value int value by which to multiply
         */
        public void multiply(String key, int value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$mul");
            }
        }

        /**
         * Save maximal value between existing and provided.
         *
         * @param key String with property name to check for max
         * @param value int value to check for max
         */
        public void saveMax(String key, int value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$max");
            }
        }

        /**
         * Save minimal value between existing and provided.
         *
         * @param key String with property name to check for min
         * @param value int value to check for min
         */
        public void saveMin(String key, int value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$min");
            }
        }

        /**
         * Set value only if property does not exist yet
         *
         * @param key String with property name to set
         * @param value String value to set
         */
        public void setOnce(String key, String value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$setOnce");
            }
        }

        /* Create array property, if property does not exist and add value to array
         * You can only use it on array properties or properties that do not exist yet
         * @param key String with property name for array property
         * @param value String with value to add to array
         */
        public void push(String key, String value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$push");
            }
        }

        /* Create array property, if property does not exist and add value to array, only if value is not yet in the array
         * You can only use it on array properties or properties that do not exist yet
         * @param key String with property name for array property
         * @param value String with value to add to array
         */
        public void pushUnique(String key, String value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$addToSet");
            }
        }

        /* Create array property, if property does not exist and remove value from array
         * You can only use it on array properties or properties that do not exist yet
         * @param key String with property name for array property
         * @param value String with value to remove from array
         */
        public void pull(String key, String value) {
            synchronized (_cly) {
                modifyCustomData(key, value, "$pull");
            }
        }

        /**
         * Remove custom user property
         */
        //public void unset() {
        //    //todo add in the future
        //}

        /**
         * Set a single user property. It can be either a custom one or one of the predefined ones.
         *
         * @param key the key for the user property
         * @param value the value for the user property to be set. The value should be the allowed data type.
         */
        public void setProperty(String key, Object value) {
            synchronized (_cly) {
                L.i("[UserProfile] Calling 'setProperty'");

                Map<String, Object> data = new HashMap<>();
                data.put(key, value);

                setPropertiesInternal(data);
            }
        }

        /**
         * Provide a map of user properties to set.
         * Those can be either custom user properties or predefined user properties
         *
         * @param data
         */
        public void setProperties(Map<String, Object> data) {
            synchronized (_cly) {
                L.i("[UserProfile] Calling 'setProperties'");

                if (data == null) {
                    L.i("[UserProfile] Provided data can not be 'null'");
                    return;
                }
                setPropertiesInternal(data);
            }
        }

        /*
         * Send provided values to server
         */
        public void save() {
            synchronized (_cly) {
                L.i("[UserProfile] Calling 'save'");
                saveInternal();
            }
        }

        /**
         * Clear queued operations / modifications
         */
        public void clear() {
            synchronized (_cly) {
                L.i("[UserProfile] Calling 'clear'");
                clearInternal();
            }
        }
    }
}
