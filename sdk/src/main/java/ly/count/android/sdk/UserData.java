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

import android.util.Log;

public class UserData {
    public static final String NAME_KEY = "name";
    public static final String USERNAME_KEY = "username";
    public static final String EMAIL_KEY = "email";
    public static final String ORG_KEY = "organization";
    public static final String PHONE_KEY = "phone";
    public static final String PICTURE_KEY = "picture";
    public static final String PICTURE_PATH_KEY = "picturePath";
    public static final String GENDER_KEY = "gender";
    public static final String BYEAR_KEY = "byear";
    public static final String CUSTOM_KEY = "custom";

    public static String name;
    public static String username;
    public static String email;
    public static String org;
    public static String phone;
    public static String picture;
    public static String picturePath;
    public static String gender;
    public static Map<String, String> custom;
    public static Map<String, JSONObject> customMods;
    public static int byear = 0;
    public static boolean isSynced = true;

    final ConnectionQueue connectionQueue_;

    //Public methods
    /**
     * Constructs a UserData object.
     * @param connectionQueue to process userdata requests
     */
    UserData(ConnectionQueue connectionQueue) {
        connectionQueue_ = connectionQueue;
    }
    /**
     * Sets information about user. Possible keys are:
     * <ul>
     * <li>
     * name - (String) providing user's full name
     * </li>
     * <li>
     * username - (String) providing user's nickname
     * </li>
     * <li>
     * email - (String) providing user's email address
     * </li>
     * <li>
     * organization - (String) providing user's organization's name where user works
     * </li>
     * <li>
     * phone - (String) providing user's phone number
     * </li>
     * <li>
     * picture - (String) providing WWW URL to user's avatar or profile picture
     * </li>
     * <li>
     * picturePath - (String) providing local path to user's avatar or profile picture
     * </li>
     * <li>
     * gender - (String) providing user's gender as M for male and F for female
     * </li>
     * <li>
     * byear - (int) providing user's year of birth as integer
     * </li>
     * </ul>
     * @param data Map&lt;String, String&gt; with user data
     */
    public void setUserData(Map<String, String> data) {
            setUserData(data, null);
    }

    /**
     * Sets information about user with custom properties.
     * In custom properties you can provide any string key values to be stored with user
     * Possible keys are:
     * <ul>
     * <li>
     * name - (String) providing user's full name
     * </li>
     * <li>
     * username - (String) providing user's nickname
     * </li>
     * <li>
     * email - (String) providing user's email address
     * </li>
     * <li>
     * organization - (String) providing user's organization's name where user works
     * </li>
     * <li>
     * phone - (String) providing user's phone number
     * </li>
     * <li>
     * picture - (String) providing WWW URL to user's avatar or profile picture
     * </li>
     * <li>
     * picturePath - (String) providing local path to user's avatar or profile picture
     * </li>
     * <li>
     * gender - (String) providing user's gender as M for male and F for female
     * </li>
     * <li>
     * byear - (int) providing user's year of birth as integer
     * </li>
     * </ul>
     * @param data Map&lt;String, String&gt; with user data
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     */
    public void setUserData(Map<String, String> data, Map<String, String> customdata) {
        UserData.setData(data);
        if(customdata != null)
            UserData.setCustomData(customdata);
    }

    /**
     * Sets custom properties.
     * In custom properties you can provide any string key values to be stored with user
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     */
    public void setCustomUserData(Map<String, String> customdata) {
        if(customdata != null)
            UserData.setCustomData(customdata);
    }

    /**
     * Sets custom provide key/value as custom property.
     * @param key String with key for the property
     * @param value String with value for the property
     */
    public void setProperty(String key, String value){
        UserData.setCustomProperty(key, value);
    }

    /**
     * Increment custom property value by 1.
     * @param key String with property name to increment
     */
    public void increment(String key){
        UserData.modifyCustomData(key, 1, "$inc");
    }

    /**
     * Increment custom property value by provided value.
     * @param key String with property name to increment
     * @param value int value by which to increment
     */
    public void incrementBy(String key, int value){
        UserData.modifyCustomData(key, value, "$inc");
    }

    /**
     * Multiply custom property value by provided value.
     * @param key String with property name to multiply
     * @param value int value by which to multiply
     */
    public void multiply(String key, int value){
        UserData.modifyCustomData(key, value, "$mul");
    }

    /**
     * Save maximal value between existing and provided.
     * @param key String with property name to check for max
     * @param value int value to check for max
     */
    public void saveMax(String key, int value){
        UserData.modifyCustomData(key, value, "$max");
    }

    /**
     * Save minimal value between existing and provided.
     * @param key String with property name to check for min
     * @param value int value to check for min
     */
    public void saveMin(String key, int value){
        UserData.modifyCustomData(key, value, "$min");
    }

    /**
     * Set value only if property does not exist yet
     * @param key String with property name to set
     * @param value String value to set
     */
    public void setOnce(String key, String value){
        UserData.modifyCustomData(key, value, "$setOnce");
    }

    /* Create array property, if property does not exist and add value to array
     * You can only use it on array properties or properties that do not exist yet
     * @param key String with property name for array property
     * @param value String with value to add to array
     */
    public void pushValue(String key, String value){
        UserData.modifyCustomData(key, value, "$push");
    }

    /* Create array property, if property does not exist and add value to array, only if value is not yet in the array
     * You can only use it on array properties or properties that do not exist yet
     * @param key String with property name for array property
     * @param value String with value to add to array
     */
    public void pushUniqueValue(String key, String value){
        UserData.modifyCustomData(key, value, "$addToSet");
    }

    /* Create array property, if property does not exist and remove value from array
     * You can only use it on array properties or properties that do not exist yet
     * @param key String with property name for array property
     * @param value String with value to remove from array
     */
    public void pullValue(String key, String value){
        UserData.modifyCustomData(key, value, "$pull");
    }

    /*
     * Send provided values to server
     */
    public void save(){
        connectionQueue_.sendUserData();
        UserData.clear();
    }

    /**
     * Clear all submitted information
     */
    static void clear(){
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
     * @param data Map with user data
     */
    static void setData(Map<String, String> data){
        if(data.containsKey(NAME_KEY))
            name = data.get(NAME_KEY);
        if(data.containsKey(USERNAME_KEY))
            username = data.get(USERNAME_KEY);
        if(data.containsKey(EMAIL_KEY))
            email = data.get(EMAIL_KEY);
        if(data.containsKey(ORG_KEY))
            org = data.get(ORG_KEY);
        if(data.containsKey(PHONE_KEY))
            phone = data.get(PHONE_KEY);
        if(data.containsKey(PICTURE_PATH_KEY))
            picturePath = data.get(PICTURE_PATH_KEY);
        if(picturePath != null){
            File sourceFile = new File(picturePath);
            if (!sourceFile.isFile()) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Provided file " + picturePath + " can not be opened");
                }
                picturePath = null;
            }
        }
        if(data.containsKey(PICTURE_KEY))
            picture = data.get(PICTURE_KEY);
        if(data.containsKey(GENDER_KEY))
            gender = data.get(GENDER_KEY);
        if(data.containsKey(BYEAR_KEY)){
            try {
                byear = Integer.parseInt(data.get(BYEAR_KEY));
            }
            catch(NumberFormatException e){
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Incorrect byear number format");
                }
                byear = 0;
            }
        }
        isSynced = false;
    }

    /**
     * Sets user custom properties and values.
     * @param data Map with user custom key/values
     */
    static void setCustomData(Map<String, String> data){
        if(custom == null)
            custom = new HashMap<>();
        custom.putAll(data);
        isSynced = false;
    }

    /**
     * Sets custom provide key/value as custom property.
     * @param key String with key for the property
     * @param value String with value for the property
     */
    static void setCustomProperty(String key, String value){
        if(custom == null)
            custom = new HashMap<>();
        custom.put(key, value);
    }

    /**
     * Atomic modifications on custom user property.
     * @param key String with property name to modify
     * @param value numeric value to use in modification
     * @param mod String with modification command
     */
    static void modifyCustomData(String key, double value, String mod){
        try {
            if(customMods == null)
                customMods = new HashMap<>();
            JSONObject ob;
            if(!mod.equals("$pull") && !mod.equals("$push") && !mod.equals("$addToSet")) {
                ob = new JSONObject();
                ob.put(mod, value);
            }
            else{
                if(customMods.containsKey(key)){
                    ob = customMods.get(key);
                }
                else{
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
     * Atomic modifications on custom user property.
     * @param key String with property name to modify
     * @param value String value to use in modification
     * @param mod String with modification command
     */
    static void modifyCustomData(String key, String value, String mod){
        try {
            if(customMods == null)
                customMods = new HashMap<>();
            JSONObject ob;
            if(!mod.equals("$pull") && !mod.equals("$push") && !mod.equals("$addToSet")) {
                ob = new JSONObject();
                ob.put(mod, value);
            }
            else{
                if(customMods.containsKey(key)){
                    ob = customMods.get(key);
                }
                else{
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
     * Returns &user_details= prefixed url to add to request data when making request to server
     * @return a String user_details url part with provided user data
     */
    static String getDataForRequest(){
        if(!isSynced){
            isSynced = true;
            final JSONObject json = UserData.toJSON();
            if(json != null){
                String result = json.toString();

                try {
                    result = java.net.URLEncoder.encode(result, "UTF-8");

                    if(result != null && !result.equals("")){
                        result = "&user_details="+result;
                        if(picturePath != null)
                            result += "&"+PICTURE_PATH_KEY+"="+java.net.URLEncoder.encode(picturePath, "UTF-8");
                    }
                    else{
                        result = "";
                        if(picturePath != null)
                            result += "&user_details&"+PICTURE_PATH_KEY+"="+java.net.URLEncoder.encode(picturePath, "UTF-8");
                    }
                } catch (UnsupportedEncodingException ignored) {
                    // should never happen because Android guarantees UTF-8 support
                }

                if(result != null)
                    return result;
            }
        }
        return "";
    }

    /**
     * Creates and returns a JSONObject containing the user data from this object.
     * @return a JSONObject containing the user data from this object
     */
    static JSONObject toJSON() {
        final JSONObject json = new JSONObject();

        try {
            if (name != null)
                if(name.equals(""))
                    json.put(NAME_KEY, JSONObject.NULL);
                else
                    json.put(NAME_KEY, name);
            if (username != null)
                if(username.equals(""))
                    json.put(USERNAME_KEY, JSONObject.NULL);
                else
                    json.put(USERNAME_KEY, username);
            if (email != null)
                if(email.equals(""))
                    json.put(EMAIL_KEY, JSONObject.NULL);
                else
                    json.put(EMAIL_KEY, email);
            if (org != null)
                if(org.equals(""))
                    json.put(ORG_KEY, JSONObject.NULL);
                else
                    json.put(ORG_KEY, org);
            if (phone != null)
                if(phone.equals(""))
                    json.put(PHONE_KEY, JSONObject.NULL);
                else
                    json.put(PHONE_KEY, phone);
            if (picture != null)
                if(picture.equals(""))
                    json.put(PICTURE_KEY, JSONObject.NULL);
                else
                    json.put(PICTURE_KEY, picture);
            if (gender != null)
                if(gender.equals(""))
                    json.put(GENDER_KEY, JSONObject.NULL);
                else
                    json.put(GENDER_KEY, gender);
            if (byear != 0)
                if(byear > 0)
                    json.put(BYEAR_KEY, byear);
                else
                    json.put(BYEAR_KEY, JSONObject.NULL);

            JSONObject ob;
            if(custom != null){
                ob = new JSONObject(custom);
            }
            else{
                ob = new JSONObject();
            }
            if(customMods != null) {
                for (Map.Entry<String, JSONObject> entry : customMods.entrySet()) {
                    ob.put(entry.getKey(), entry.getValue());
                }
            }
            json.put(CUSTOM_KEY, ob);
        }
        catch (JSONException e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "Got exception converting an UserData to JSON", e);
            }
        }

        return json;
    }

    /**
     * Sets user data fields to values from its JSON representation.
     * @param json JSON object to extract event data from
     */
    static void fromJSON(final JSONObject json) {
        if(json != null){
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
                    custom = new HashMap<>(customJson.length());
                    Iterator<String> nameItr = customJson.keys();
                    while (nameItr.hasNext()) {
                        final String key = nameItr.next();
                        if (!customJson.isNull(key)) {
                            custom.put(key, customJson.getString(key));
                        }
                    }
                } catch (JSONException e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "Got exception converting an Custom Json to Custom User data", e);
                    }
                }
            }
        }
    }

    //for url query parsing
    public static String getPicturePathFromQuery(URL url){
        String query = url.getQuery();
        String[] pairs = query.split("&");
        String ret = "";
        if(url.getQuery().contains(PICTURE_PATH_KEY)){
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if(pair.substring(0, idx).equals(PICTURE_PATH_KEY)){
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
}