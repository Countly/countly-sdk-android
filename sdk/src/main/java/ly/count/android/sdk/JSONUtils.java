package ly.count.android.sdk;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

class JSONUtils {

    /**
     * Serialize any container, which implements Map interface
     * @param map Map, which should be serialized
     * @return JSONObject for passed map
     * @throws JSONException if unable to serialized passed map
     */
    static JSONObject serializeMap(Map map) throws JSONException {
        JSONObject json = new JSONObject();
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new JSONException("Map key must be String, but found " + key.getClass().getName());
            }
            Object value = map.get(key);
            if (value instanceof List) {
                json.put((String)key, serializeList((List)value));
            } else if (value.getClass().isArray()) {
                json.put((String)key, serializeArray((Object[])value));
            } else {
                json.put((String)key, value);
            }
        }

        return json;
    }

    /**
     * Serialize any container, which implements List interface
     * @param list List, which should be serialized
     * @return JSONOArray for passed list
     * @throws JSONException if unable to serialized passed list
     */
    static JSONArray serializeList(List list) throws JSONException {
        JSONArray json = new JSONArray();
        for (Object value : list) {
            if (value instanceof Map) {
                json.put(serializeMap((Map)value));
            } else if (value instanceof List) {
                json.put(serializeList((List)value));
            } else if (value.getClass().isArray()) {
                json.put(serializeArray((Object[])value));
            } else {
                json.put(value);
            }
        }

        return json;
    }

    /**
     * Serialize array
     * @param array Array, which should be serialized
     * @return JSONOArray for passed array
     * @throws JSONException if unable to serialized passed array
     */
    static JSONArray serializeArray(Object[] array) throws JSONException {
        JSONArray json = new JSONArray();
        for (Object value : array) {
            if (value instanceof Map) {
                json.put(serializeMap((Map)value));
            } else if (value instanceof List) {
                json.put(serializeList((List)value));
            } else if (value.getClass().isArray()) {
                json.put(serializeArray((Object[])value));
            } else if (value.getClass().isPrimitive()) {
                switch (value.getClass().getName()) {
                    case "int":
                        json.put((int)value);
                        break;
                    case "long":
                        json.put((long)value);
                    case "double":
                        json.put((double)value);
                        break;
                    case "boolean":
                        json.put((boolean)value);
                        break;
                    default:
                        throw new JSONException("Unable to handle provided value of primitive type " + value.getClass().getName());
                }
            } else {
                json.put(value);
            }
        }

        return json;
    }
}
