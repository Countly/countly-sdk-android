package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
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
    static final String PICTURE_KEY = "picture";
    static final String PICTURE_PATH_KEY = "picturePath";
    static final String GENDER_KEY = "gender";
    static final String BYEAR_KEY = "byear";
    static final String CUSTOM_KEY = "custom";

    String[] namedFields = new String[] { NAME_KEY, USERNAME_KEY, EMAIL_KEY, ORG_KEY, PHONE_KEY, PICTURE_KEY, PICTURE_PATH_KEY, GENDER_KEY, BYEAR_KEY };

    boolean isSynced = true;

    JSONObject dataStore = new JSONObject();

    UserProfile userProfileInterface = null;

    ModuleUserProfile(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleUserProfile] Initialising");

        userProfileInterface = new UserProfile();
    }

    //for url query parsing
    //this looks to be for internal use only
    //used when performing requests to get the set picture path
    static String getPicturePathFromQuery(URL url) {
        String query = url.getQuery();

        if (query == null) {
            //assume no query part in url
            return "";
        }

        String[] pairs = query.split("&");
        String ret = "";
        if (url.getQuery().contains(PICTURE_PATH_KEY)) {
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (pair.substring(0, idx).equals(PICTURE_PATH_KEY)) {
                    try {
                        ret = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        ret = "";
                    }
                    break;
                }
            }
        }
        return ret;
    }

    /**
     * Returns &user_details= prefixed url to add to request data when making request to server
     *
     * @return a String user_details url part with provided user data
     */
    static String getDataForRequest() {
        if (!UserData.isSynced) {
            UserData.isSynced = true;
            final JSONObject json = toJSON();
            if (json != null) {
                String result = json.toString();

                try {
                    result = java.net.URLEncoder.encode(result, "UTF-8");

                    if (result != null && !result.equals("")) {
                        result = "&user_details=" + result;
                        if (UserData.picturePath != null) {
                            result += "&" + PICTURE_PATH_KEY + "=" + java.net.URLEncoder.encode(UserData.picturePath, "UTF-8");
                        }
                    } else {
                        result = "";
                        if (UserData.picturePath != null) {
                            result += "&user_details&" + PICTURE_PATH_KEY + "=" + java.net.URLEncoder.encode(UserData.picturePath, "UTF-8");
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
    protected static JSONObject toJSON() {
        final JSONObject json = new JSONObject();

        try {
            if (UserData.name != null) {
                if (UserData.name.equals("")) {
                    json.put(NAME_KEY, JSONObject.NULL);
                } else {
                    json.put(NAME_KEY, UserData.name);
                }
            }
            if (UserData.username != null) {
                if (UserData.username.equals("")) {
                    json.put(USERNAME_KEY, JSONObject.NULL);
                } else {
                    json.put(USERNAME_KEY, UserData.username);
                }
            }
            if (UserData.email != null) {
                if (UserData.email.equals("")) {
                    json.put(EMAIL_KEY, JSONObject.NULL);
                } else {
                    json.put(EMAIL_KEY, UserData.email);
                }
            }
            if (UserData.org != null) {
                if (UserData.org.equals("")) {
                    json.put(ORG_KEY, JSONObject.NULL);
                } else {
                    json.put(ORG_KEY, UserData.org);
                }
            }
            if (UserData.phone != null) {
                if (UserData.phone.equals("")) {
                    json.put(PHONE_KEY, JSONObject.NULL);
                } else {
                    json.put(PHONE_KEY, UserData.phone);
                }
            }
            if (UserData.picture != null) {
                if (UserData.picture.equals("")) {
                    json.put(PICTURE_KEY, JSONObject.NULL);
                } else {
                    json.put(PICTURE_KEY, UserData.picture);
                }
            }
            if (UserData.gender != null) {
                if (UserData.gender.equals("")) {
                    json.put(GENDER_KEY, JSONObject.NULL);
                } else {
                    json.put(GENDER_KEY, UserData.gender);
                }
            }
            if (UserData.byear != 0) {
                if (UserData.byear > 0) {
                    json.put(BYEAR_KEY, UserData.byear);
                } else {
                    json.put(BYEAR_KEY, JSONObject.NULL);
                }
            }

            JSONObject ob;
            if (UserData.custom != null) {
                ob = new JSONObject(UserData.custom);
            } else {
                ob = new JSONObject();
            }
            if (UserData.customMods != null) {
                for (Map.Entry<String, JSONObject> entry : UserData.customMods.entrySet()) {
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
    static void fromJSON(final JSONObject json) {
        if (json != null) {
            UserData.name = json.optString(NAME_KEY, null);
            UserData.username = json.optString(USERNAME_KEY, null);
            UserData.email = json.optString(EMAIL_KEY, null);
            UserData.org = json.optString(ORG_KEY, null);
            UserData.phone = json.optString(PHONE_KEY, null);
            UserData.picture = json.optString(PICTURE_KEY, null);
            UserData.gender = json.optString(GENDER_KEY, null);
            UserData.byear = json.optInt(BYEAR_KEY, 0);
            if (!json.isNull(CUSTOM_KEY)) {
                JSONObject customJson;
                try {
                    customJson = json.getJSONObject(CUSTOM_KEY);
                    if (customJson.length() == 0) {
                        UserData.custom = null;
                    } else {
                        UserData.custom = new HashMap<>(customJson.length());
                        Iterator<String> nameItr = customJson.keys();
                        while (nameItr.hasNext()) {
                            final String key = nameItr.next();
                            if (!customJson.isNull(key)) {
                                UserData.custom.put(key, customJson.getString(key));
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
    static void modifyCustomData(String key, Object value, String mod) {
        try {
            if (!(value instanceof Double || value instanceof Integer || value instanceof String)) {
                Countly.sharedInstance().L.w("[ModuleUserProfile] modifyCustomDataCommon, provided an unsupported type for 'value'");
                return;
            }

            if (UserData.customMods == null) {
                UserData.customMods = new HashMap<>();
            }
            JSONObject ob;
            if (!mod.equals("$pull") && !mod.equals("$push") && !mod.equals("$addToSet")) {
                ob = new JSONObject();
                ob.put(mod, value);
            } else {
                if (UserData.customMods.containsKey(key)) {
                    ob = UserData.customMods.get(key);
                } else {
                    ob = new JSONObject();
                }
                ob.accumulate(mod, value);
            }
            UserData.customMods.put(key, ob);
            UserData.isSynced = false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * This mainly performs the filtering of provided values
     * @param data
     */
    void setPropertiesInternal(@NonNull Map<String, Object> data) {
        if (data.size() == 0) {
            Countly.sharedInstance().L.w("[ModuleUserProfile] setPropertiesInternal, no data was provided");
            return;
        }

        Map<String, Object> dataNamedFields = new HashMap<>();
        Map<String, Object> dataCustomFields = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean isNamed = false;

            for (String namedField : namedFields) {
                if (namedField.equals(key)) {
                    //if it's a name field
                    isNamed = true;
                    dataNamedFields.put(key, value);
                    break;
                }
            }

            if (!isNamed) {
                dataCustomFields.put(key, value);
            }
        }
    }

    void saveInternal() {
        Countly.sharedInstance().L.w("[ModuleUserProfile] saveInternal");
        Countly.userData.save();
    }

    void clearInternal() {
        Countly.sharedInstance().L.w("[ModuleUserProfile] clearInternal");
        Countly.userData.clear();
    }

    @Override
    void halt() {

    }

    public class UserProfile {
        /**
         * Increment custom property value by 1.
         *
         * @param key String with property name to increment
         */
        public void increment(String key) {
            Countly.userData.increment(key);
        }

        /**
         * Increment custom property value by provided value.
         *
         * @param key String with property name to increment
         * @param value int value by which to increment
         */
        public void incrementBy(String key, int value) {
            Countly.userData.incrementBy(key, value);
        }

        /**
         * Multiply custom property value by provided value.
         *
         * @param key String with property name to multiply
         * @param value int value by which to multiply
         */
        public void multiply(String key, int value) {
            Countly.userData.multiply(key, value);
        }

        /**
         * Save maximal value between existing and provided.
         *
         * @param key String with property name to check for max
         * @param value int value to check for max
         */
        public void saveMax(String key, int value) {
            Countly.userData.saveMax(key, value);
        }

        /**
         * Save minimal value between existing and provided.
         *
         * @param key String with property name to check for min
         * @param value int value to check for min
         */
        public void saveMin(String key, int value) {
            Countly.userData.saveMin(key, value);
        }

        /**
         * Set value only if property does not exist yet
         *
         * @param key String with property name to set
         * @param value String value to set
         */
        public void setOnce(String key, String value) {
            Countly.userData.setOnce(key, value);
        }

        /* Create array property, if property does not exist and add value to array
         * You can only use it on array properties or properties that do not exist yet
         * @param key String with property name for array property
         * @param value String with value to add to array
         */
        public void push(String key, String value) {
            Countly.userData.pushValue(key, value);
        }

        /* Create array property, if property does not exist and add value to array, only if value is not yet in the array
         * You can only use it on array properties or properties that do not exist yet
         * @param key String with property name for array property
         * @param value String with value to add to array
         */
        public void pushUnique(String key, String value) {
            Countly.userData.pushUniqueValue(key, value);
        }

        /* Create array property, if property does not exist and remove value from array
         * You can only use it on array properties or properties that do not exist yet
         * @param key String with property name for array property
         * @param value String with value to remove from array
         */
        public void pull(String key, String value) {
            Countly.userData.pullValue(key, value);
        }

        public void unset() {

        }

        /**
         * Set a single user property. It can be either a custom one or one of the predefined ones.
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
         * @param data
         */
        public void setProperties(Map<String, Object> data) {
            synchronized (_cly) {
                L.i("[UserProfile] Calling 'setProperties'");

                if(data == null) {
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
         * Clear all submitted information
         */
        public void clear() {
            synchronized (_cly) {
                L.i("[UserProfile] Calling 'clear'");
                clearInternal();
            }
        }
    }
}
