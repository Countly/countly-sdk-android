package ly.count.sdk.android.internal;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.ModuleRatingCore;
import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Params;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.Storage;
import ly.count.sdk.internal.Tasks;

/**
 * Legacy SDK transition code: preference names, data handling.
 */

public class Legacy {
    private static final Log.Module L = Log.module("Legacy");

    static final String PREFERENCES = "COUNTLY_STORE";

    static final String KEY_CONNECTIONS = "CONNECTIONS";
    static final String KEY_EVENTS = "EVENTS";
    static final String KEY_LOCATION = "LOCATION_PREFERENCE";
    static final String KEY_STAR = "STAR_RATING";

    static final String KEY_ID_ID = "ly.count.android.api.DeviceId.id";
    static final String KEY_ID_TYPE = "ly.count.android.api.DeviceId.type";

    static final String DELIMITER = ":::";

    private static SharedPreferences preferences(Ctx ctx) {
        return ctx.getContext().getSharedPreferences(PREFERENCES, android.content.Context.MODE_PRIVATE);
    }

    static String get(Ctx ctx, String key) {
        return preferences(ctx).getString(key, null);
    }

    @SuppressLint("ApplySharedPref")
    static String getOnce(Ctx ctx, String key) {
        SharedPreferences prefs = preferences(ctx);
        String value = prefs.getString(key, null);
        if (value != null) {
            prefs.edit().remove(key).commit();
        }
        return value;
    }

    static boolean isMigrationNeeded(Ctx ctx) {
        return preferences(ctx).contains(KEY_CONNECTIONS);
    }

    /**
     * Main migration method which transfers and transcodes data from legacy {@link SharedPreferences}
     * storage to current files-based {@link Storage}.
     *
     * @param ctx Ctx in which to do migration
     */
    @SuppressLint("ApplySharedPref")
    static void migrate(final Ctx ctx) {
        String requestsStr = preferences(ctx).getString(KEY_CONNECTIONS, null);
        String eventsStr = preferences(ctx).getString(KEY_EVENTS, null);
        String locationStr = preferences(ctx).getString(KEY_LOCATION, null);
        String starStr = preferences(ctx).getString(KEY_STAR, null);

        //migrate stored requests
        if (Utils.isNotEmpty(requestsStr)) {
            String[] requests = requestsStr.split(DELIMITER);
            L.d("Migrating " + requests.length + " requests");
            for (String str : requests) {
                Params params = new Params(str);
                String timestamp = params.get("timestamp");
                if (Utils.isNotEmpty(timestamp)) {
                    try {
                        Request request = ModuleRequests.nonSessionRequest(ctx, Long.parseLong(timestamp));
                        request.params.add(params);
                        L.d("Migrating request: " + str + ", time " + request.storageId());
                        Storage.pushAsync(ctx, request, removeClb(ctx, KEY_CONNECTIONS));
                    } catch (NumberFormatException e) {
                        L.wtf("Couldn't import request " + str, e);
                    }
                } else {
                    Request request = ModuleRequests.nonSessionRequest(ctx, Device.dev.uniqueTimestamp());
                    L.d("Migrating request: " + str + ", current time " + request.storageId());
                    request.params.add(params);
                    Storage.pushAsync(ctx, request, removeClb(ctx, KEY_CONNECTIONS));
                }
            }
        }

        //migrate events
        if (Utils.isNotEmpty(eventsStr)) {
            String[] events = eventsStr.split(DELIMITER);
            L.d("Migrating " + events.length + " events");
            JSONArray array = new JSONArray();
            for (String str : events) {
                try {
                    array.put(new JSONObject(str));
                } catch (JSONException e) {
                    L.w("Couldn't parse event json: " + str);
                }
            }
            if (array.length() > 0) {
                Request request = ModuleRequests.nonSessionRequest(ctx, Device.dev.uniqueTimestamp());
                request.params.add("events", array.toString());
                Storage.pushAsync(ctx, request, removeClb(ctx, KEY_EVENTS));
            } else {
                preferences(ctx).edit().remove(KEY_EVENTS).commit();
            }
        }

        //migrate location
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

        //migrate star rating
        if (Utils.isNotEmpty(starStr)) {
            L.d("Migrating Star Rating preferences");

            final String KEY_APP_VERSION = "sr_app_version";
            final String KEY_SESSION_LIMIT = "sr_session_limit";
            final String KEY_SESSION_AMOUNT = "sr_session_amount";
            final String KEY_IS_SHOWN_FOR_CURRENT = "sr_is_shown";
            final String KEY_AUTOMATIC_RATING_IS_SHOWN = "sr_is_automatic_shown";
            final String KEY_DISABLE_AUTOMATIC_NEW_VERSIONS = "sr_is_disable_automatic_new";
            final String KEY_AUTOMATIC_HAS_BEEN_SHOWN = "sr_automatic_has_been_shown";
            final String KEY_DIALOG_IS_CANCELLABLE = "sr_automatic_dialog_is_cancellable";
            final String KEY_DIALOG_TEXT_TITLE = "sr_text_title";
            final String KEY_DIALOG_TEXT_MESSAGE = "sr_text_message";
            final String KEY_DIALOG_TEXT_DISMISS = "sr_text_dismiss";

            ModuleRatingCore.StarRatingPreferences srp = new ModuleRatingCore.StarRatingPreferences();
            JSONObject srJSON;
            try {
                srJSON = new JSONObject(starStr);

                if(srJSON.length() > 0)
                {
                    //if there are any fields set
                    srp.appVersion = srJSON.getString(KEY_APP_VERSION);
                    srp.sessionLimit = srJSON.getInt(KEY_SESSION_LIMIT);
                    srp.sessionAmount = srJSON.getInt(KEY_SESSION_AMOUNT);
                    srp.isShownForCurrentVersion = srJSON.getBoolean(KEY_IS_SHOWN_FOR_CURRENT);
                    srp.automaticRatingShouldBeShown = srJSON.getBoolean(KEY_AUTOMATIC_RATING_IS_SHOWN);
                    srp.disabledAutomaticForNewVersions = srJSON.getBoolean(KEY_DISABLE_AUTOMATIC_NEW_VERSIONS);
                    srp.automaticHasBeenShown = srJSON.getBoolean(KEY_AUTOMATIC_HAS_BEEN_SHOWN);
                    srp.isDialogCancellable = srJSON.getBoolean(KEY_DIALOG_IS_CANCELLABLE);
                    srp.dialogTextTitle = srJSON.getString(KEY_DIALOG_TEXT_TITLE);
                    srp.dialogTextMessage = srJSON.getString(KEY_DIALOG_TEXT_MESSAGE);
                    srp.dialogTextDismiss = srJSON.getString(KEY_DIALOG_TEXT_DISMISS);
                }
            } catch (JSONException e) {
                L.w("Got exception converting JSON to a StarRatingPreferences", e);
                //failed to read preferences, resort to defaults
            }

            //save srp to disk
            Storage.push(ctx, srp);
            Storage.await();
        }

        // TODO: city/country
    }

    private static Tasks.Callback<Boolean> removeClb(final Ctx ctx, final String key) {
        return new Tasks.Callback<Boolean>() {
            @SuppressLint("ApplySharedPref")
            @Override
            public void call(Boolean done) throws Exception {
                if (done) {
                    preferences(ctx).edit().remove(key).commit();
                }
            }
        };
    }
}
