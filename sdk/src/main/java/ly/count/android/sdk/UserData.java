package ly.count.android.sdk;


import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

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
	
	public static String name;
	public static String username;
	public static String email;
	public static String org;
	public static String phone;
	public static String picture;
	public static String picturePath;
	public static String gender;
	public static int byear = 0;
	public static boolean isSynced = true;
	
	
	/**
     * Sets user data values.
     * @param data Bundle with user data
     */
	static void setData(Bundle data){
		name = data.getString(NAME_KEY, null);
		username = data.getString(USERNAME_KEY, null);
		email = data.getString(EMAIL_KEY, null);
		org = data.getString(ORG_KEY, null);
		phone = data.getString(PHONE_KEY, null);
		picturePath = data.getString(PICTURE_PATH_KEY, null);
		if(picturePath != null){
			File sourceFile = new File(picturePath);
			if (!sourceFile.isFile()) {
				if (Countly.sharedInstance().isLoggingEnabled()) {
					Log.w(Countly.TAG, "Provided file " + picturePath + " can not be opened");
				}
				picturePath = null;
			}
		}
		picture = data.getString(PICTURE_KEY);
		gender = data.getString(GENDER_KEY, null);
		byear = data.getInt(BYEAR_KEY, 0);
		isSynced = false;
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
        		if(name == "")
        			json.put(NAME_KEY, JSONObject.NULL);
        		else
        			json.put(NAME_KEY, name);
        	if (username != null)
        		if(username == "")
        			json.put(USERNAME_KEY, JSONObject.NULL);
        		else
        			json.put(USERNAME_KEY, username);
        	if (email != null)
        		if(email == "")
        			json.put(EMAIL_KEY, JSONObject.NULL);
        		else
        			json.put(EMAIL_KEY, email);
        	if (org != null)
        		if(org == "")
        			json.put(ORG_KEY, JSONObject.NULL);
        		else
        			json.put(ORG_KEY, org);
        	if (phone != null)
        		if(phone == "")
        			json.put(PHONE_KEY, JSONObject.NULL);
        		else
        			json.put(PHONE_KEY, phone);
        	if (picture != null)
        		if(picture == "")
        			json.put(PICTURE_KEY, JSONObject.NULL);
        		else
        			json.put(PICTURE_KEY, picture);
        	if (gender != null)
        		if(gender == "")
        			json.put(GENDER_KEY, JSONObject.NULL);
        		else
        			json.put(GENDER_KEY, gender);
        	if (byear != 0)
        		if(byear > 0)
        			json.put(BYEAR_KEY, byear);
        		else
        			json.put(BYEAR_KEY, JSONObject.NULL);
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
