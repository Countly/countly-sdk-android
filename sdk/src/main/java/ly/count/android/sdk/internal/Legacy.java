package ly.count.android.sdk.internal;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Legacy SDK transition code: preference names, data handling.
 */

public class Legacy {
    static final String PREFERENCES = "COUNTLY_STORE";

    static final String KEY_CONNECTIONS = "CONNECTIONS";
    static final String KEY_EVENTS = "EVENTS";
    static final String KEY_LOCATION = "LOCATION_PREFERENCE";
    static final String KEY_STAR = "STAR_RATING";

    static final String KEY_ID_ID = "ly.count.android.api.DeviceId.id";
    static final String KEY_ID_TYPE = "ly.count.android.api.DeviceId.type";

    static final String DELIMITER = ":::";

    private static SharedPreferences preferences(Context context) {
        return context.getContext().getSharedPreferences(PREFERENCES, android.content.Context.MODE_PRIVATE);
    }

    static String get(Context context, String key) {
        return preferences(context).getString(key, null);
    }

    @SuppressLint("ApplySharedPref")
    static String getOnce(Context context, String key) {
        SharedPreferences prefs = preferences(context);
        String value = prefs.getString(key, null);
        if (value != null) {
            prefs.edit().remove(key).commit();
        }
        return value;
    }

    static boolean isMigrationNeeded(Context context) {
        return preferences(context).contains(KEY_CONNECTIONS);
    }

    /**
     * Main migration method which transfers and transcodes data from legacy {@link SharedPreferences}
     * storage to current files-based {@link Storage}.
     *
     * @param ctx Context in which to do migration
     */
    @SuppressLint("ApplySharedPref")
    static void migrate(final Context ctx) {
        String requestsStr = preferences(ctx).getString(KEY_CONNECTIONS, null);
        String eventsStr = preferences(ctx).getString(KEY_EVENTS, null);
        String locationStr = preferences(ctx).getString(KEY_LOCATION, null);
        String starStr = preferences(ctx).getString(KEY_STAR, null);

        if (Utils.isNotEmpty(requestsStr)) {
            String[] requests = requestsStr.split(DELIMITER);
            Log.d("Migrating " + requests.length + " requests");
            for (String str : requests) {
                Params params = new Params(str);
                String timestamp = params.get("timestamp");
                if (Utils.isNotEmpty(timestamp)) {
                    try {
                        Request request = new Request(Long.parseLong(timestamp));
                        request.params.add(params);
                        Log.d("Migrating request: " + str + ", time " + request.storageId());
                        Storage.pushAsync(ctx, request, removeClb(ctx, KEY_CONNECTIONS));
                    } catch (NumberFormatException e) {
                        Log.wtf("Couldn't import request " + str, e);
                    }
                } else {
                    Request request = new Request(Device.uniqueTimestamp());
                    Log.d("Migrating request: " + str + ", current time " + request.storageId());
                    request.params.add(params);
                    Storage.pushAsync(ctx, request, removeClb(ctx, KEY_CONNECTIONS));
                }
            }
        }

        if (Utils.isNotEmpty(eventsStr)) {
            String[] events = eventsStr.split(DELIMITER);
            Log.d("Migrating " + events.length + " events");
            JSONArray array = new JSONArray();
            for (String str : events) {
                try {
                    array.put(new JSONObject(str));
                } catch (JSONException e) {
                    Log.w("Couldn't parse event json: " + str);
                }
            }
            if (array.length() > 0) {
                Request request = new Request(Device.uniqueTimestamp());
                request.params.add("events", array.toString());
                Storage.pushAsync(ctx, request, removeClb(ctx, KEY_EVENTS));
            } else {
                preferences(ctx).edit().remove(KEY_EVENTS).commit();
            }
        }

        if (Utils.isNotEmpty(locationStr)) {
            String[] comps = locationStr.split(",");
            if (comps.length == 2) {
                try {
                    double lat = Double.parseDouble(comps[0]),
                            lon = Double.parseDouble(comps[1]);
                    ModuleRequests.location(ctx, lat, lon);
                } catch (NumberFormatException e) {
                    preferences(ctx).edit().remove(KEY_LOCATION).commit();
                }
            }
        } else {
            preferences(ctx).edit().remove(KEY_LOCATION).commit();
        }

        // TODO: city/country, star rating
    }

    private static Tasks.Callback<Boolean> removeClb(final Context context, final String key) {
        return new Tasks.Callback<Boolean>() {
            @SuppressLint("ApplySharedPref")
            @Override
            public void call(Boolean done) throws Exception {
                if (done) {
                    preferences(context).edit().remove(key).commit();
                }
            }
        };
    }
}
