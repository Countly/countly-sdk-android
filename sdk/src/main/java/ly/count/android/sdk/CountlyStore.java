/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class provides a persistence layer for the local event &amp; connection queues.
 *
 * The "read" methods in this class are not synchronized, because the underlying data store
 * provides thread-safe reads.  The "write" methods in this class are synchronized, because
 * 1) they often read a list of items, modify the list, and then commit it back to the underlying
 * data store, and 2) while the Countly singleton is synchronized to ensure only a single writer
 * at a time from the public API side, the internal implementation has a background thread that
 * submits data to a Countly server, and it writes to this store as well.
 *
 * NOTE: This class is only public to facilitate unit testing, because
 * of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
public class CountlyStore implements StorageProvider, EventQueueProvider {
    private static final String PREFERENCES = "COUNTLY_STORE";
    private static final String PREFERENCES_PUSH = "ly.count.android.api.messaging";
    static final String DELIMITER = ":::";
    private static final String REQUEST_PREFERENCE = "CONNECTIONS";
    private static final String EVENTS_PREFERENCE = "EVENTS";
    private static final String STAR_RATING_PREFERENCE = "STAR_RATING";
    private static final String CACHED_ADVERTISING_ID = "ADVERTISING_ID";
    private static final String REMOTE_CONFIG_VALUES = "REMOTE_CONFIG";
    private static final String STORAGE_SCHEMA_VERSION = "SCHEMA_VERSION";
    private static final String PREFERENCE_KEY_ID_ID = "ly.count.android.api.DeviceId.id";
    private static final String PREFERENCE_KEY_ID_TYPE = "ly.count.android.api.DeviceId.type";

    private static final String CACHED_PUSH_ACTION_ID = "PUSH_ACTION_ID";
    private static final String CACHED_PUSH_ACTION_INDEX = "PUSH_ACTION_INDEX";
    private static final String CACHED_PUSH_MESSAGING_MODE = "PUSH_MESSAGING_MODE";
    private static final String CACHED_PUSH_MESSAGING_PROVIDER = "PUSH_MESSAGING_PROVIDER";
    private static final int MAX_EVENTS = 100;

    private final SharedPreferences preferences_;
    private final SharedPreferences preferencesPush_;

    private static final String CONSENT_GCM_PREFERENCES = "ly.count.android.api.messaging.consent.gcm";

    ModuleLog L;

    int maxRequestQueueSize = 1000;

    /**
     * Constructs a CountlyStore object.
     *
     * @param context used to retrieve storage meta data, must not be null.
     */
    public CountlyStore(final Context context, ModuleLog logModule) {
        if (context == null) {
            throw new IllegalArgumentException("must provide valid context");
        }
        preferences_ = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        preferencesPush_ = createPreferencesPush(context);
        L = logModule;
    }

    public void setLimits(final int maxRequestQueueSize) {
        this.maxRequestQueueSize = maxRequestQueueSize;
    }

    static SharedPreferences createPreferencesPush(Context context) {
        return context.getSharedPreferences(PREFERENCES_PUSH, Context.MODE_PRIVATE);
    }

    /**
     * Returns an unsorted array of the current stored connections.
     */
    public synchronized String[] getRequests() {
        final String joinedConnStr = preferences_.getString(REQUEST_PREFERENCE, "");
        return joinedConnStr.length() == 0 ? new String[0] : joinedConnStr.split(DELIMITER);
    }

    /**
     * Returns an unsorted array of the current stored event JSON strings.
     */
    public synchronized String[] getEvents() {
        final String joinedEventsStr = preferences_.getString(EVENTS_PREFERENCE, "");
        return joinedEventsStr.length() == 0 ? new String[0] : joinedEventsStr.split(DELIMITER);
    }

    /**
     * Returns a list of the current stored events, sorted by timestamp from oldest to newest.
     */
    public synchronized List<Event> getEventList() {
        final String[] array = getEvents();
        final List<Event> events = new ArrayList<>(array.length);
        for (String s : array) {
            try {
                final Event event = Event.fromJSON(new JSONObject(s));
                if (event != null) {
                    events.add(event);
                }
            } catch (JSONException ignored) {
                // should not happen since JSONObject is being constructed from previously stringified JSONObject
                // events -> json objects -> json strings -> storage -> json strings -> here
            }
        }
        // order the events from least to most recent
        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(final Event e1, final Event e2) {
                return (int) (e1.timestamp - e2.timestamp);
            }
        });
        return events;
    }

    /**
     * Returns the number of events in the local event queue.
     *
     * @return the number of events in the local event queue
     */
    public synchronized int getEventQueueSize() {
        return getEvents().length;
    }

    /**
     * Removes all current events from the local queue and returns them as a
     * URL-encoded JSON string that can be submitted to a ConnectionQueue.
     *
     * @return URL-encoded JSON string of event data from the local event queue
     */
    public synchronized String getEventsForRequestAndEmptyEventQueue() {
        String result;

        final List<Event> events = getEventList();

        final JSONArray eventArray = new JSONArray();
        for (Event e : events) {
            eventArray.put(e.toJSON());
        }

        result = eventArray.toString();

        removeEvents(events);

        try {
            result = java.net.URLEncoder.encode(result, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // should never happen because Android guarantees UTF-8 support
        }

        return result;
    }

    public synchronized String getRequestQueueRaw() {
        return preferences_.getString(REQUEST_PREFERENCE, "");
    }

    /**
     * Adds a connection to the local store.
     *
     * @param requestStr the connection to be added, ignored if null or empty
     */
    public synchronized void addRequest(final String requestStr) {
        if (requestStr != null && requestStr.length() > 0) {
            final List<String> connections = new ArrayList<>(Arrays.asList(getRequests()));
            if (connections.size() < MAX_REQUESTS) {
                //request under max requests, add as normal
                connections.add(requestStr);
                preferences_.edit().putString(REQUEST_PREFERENCE, Utils.joinCountlyStore(connections, DELIMITER)).apply();
            } else {
                //reached the limit, start deleting oldest requests
                L.w("[CountlyStore] Store reached it's limit, deleting oldest request");

                deleteOldestRequest();
                addRequest(requestStr);
            }
        }
    }

    synchronized void deleteOldestRequest() {
        final List<String> connections = new ArrayList<>(Arrays.asList(getRequests()));
        connections.remove(0);
        preferences_.edit().putString(REQUEST_PREFERENCE, Utils.joinCountlyStore(connections, DELIMITER)).apply();
    }

    /**
     * Removes a connection from the local store.
     *
     * @param requestStr the connection to be removed, ignored if null or empty,
     * or if a matching connection cannot be found
     */
    public synchronized void removeRequest(final String requestStr) {
        if (requestStr != null && requestStr.length() > 0) {
            final List<String> connections = new ArrayList<>(Arrays.asList(getRequests()));
            if (connections.remove(requestStr)) {
                preferences_.edit().putString(REQUEST_PREFERENCE, Utils.joinCountlyStore(connections, DELIMITER)).apply();
            }
        }
    }

    public synchronized void replaceRequests(final String[] newConns) {
        if (newConns != null) {
            final List<String> connections = new ArrayList<>(Arrays.asList(newConns));
            replaceRequestList(connections);
        }
    }

    public synchronized void replaceRequestList(final List<String> newConns) {
        if (newConns != null) {
            preferences_.edit().putString(REQUEST_PREFERENCE, Utils.joinCountlyStore(newConns, DELIMITER)).apply();
        }
    }

    /**
     * Adds a custom event to the local store.
     *
     * @param event event to be added to the local store, must not be null
     */
    void addEvent(final Event event) {
        final List<Event> events = getEventList();
        if (events.size() < MAX_EVENTS) {
            events.add(event);
            setEventData(joinEvents(events, DELIMITER));
        }
    }

    /**
     * set the new value in event data storage
     *
     * @param eventData
     */
    void setEventData(String eventData) {
        preferences_.edit().putString(EVENTS_PREFERENCE, eventData).apply();
    }

    /**
     * Set the preferences that are used for the star rating
     */
    public synchronized void setStarRatingPreferences(String prefs) {
        preferences_.edit().putString(STAR_RATING_PREFERENCE, prefs).apply();
    }

    /**
     * Get the preferences that are used for the star rating
     */
    public synchronized String getStarRatingPreferences() {
        return preferences_.getString(STAR_RATING_PREFERENCE, "");
    }

    public synchronized void setRemoteConfigValues(String values) {
        preferences_.edit().putString(REMOTE_CONFIG_VALUES, values).apply();
    }

    public synchronized String getRemoteConfigValues() {
        return preferences_.getString(REMOTE_CONFIG_VALUES, "");
    }

    public synchronized void setCachedAdvertisingId(String advertisingId) {
        preferences_.edit().putString(CACHED_ADVERTISING_ID, advertisingId).apply();
    }

    public synchronized String getCachedAdvertisingId() {
        return preferences_.getString(CACHED_ADVERTISING_ID, "");
    }

    void setConsentPush(boolean consentValue) {
        preferencesPush_.edit().putBoolean(CONSENT_GCM_PREFERENCES, consentValue).apply();
    }

    Boolean getConsentPush() {
        return preferencesPush_.getBoolean(CONSENT_GCM_PREFERENCES, false);
    }

    public synchronized static Boolean getConsentPushNoInit(Context context) {
        SharedPreferences sp = createPreferencesPush(context);
        return sp.getBoolean(CONSENT_GCM_PREFERENCES, false);
    }

    /**
     * Adds a custom event to the local store.
     *
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation values for the custom event, may be null
     * @param timestamp timestamp (seconds since 1970) in GMT when the event occurred
     * @param hour current local hour on device
     * @param dow current day of the week on device
     * @param count count associated with the custom event, should be more than zero
     * @param sum sum associated with the custom event, if not used, pass zero.
     * NaN and infinity values will be quietly ignored.
     */
    public void recordEventToEventQueue(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, final long timestamp, final int hour, final int dow) {
        Map<String, String> segmentationString = null;
        Map<String, Integer> segmentationInt = null;
        Map<String, Double> segmentationDouble = null;
        Map<String, Boolean> segmentationBoolean = null;

        if (segmentation != null && segmentation.size() > 0) {
            segmentationString = new HashMap<>();
            segmentationInt = new HashMap<>();
            segmentationDouble = new HashMap<>();
            segmentationBoolean = new HashMap<>();
            Map<String, Object> segmentationReminder = new HashMap<>();

            Utils.fillInSegmentation(segmentation, segmentationString, segmentationInt, segmentationDouble, segmentationBoolean, segmentationReminder);
        }

        final Event event = new Event();
        event.key = key;
        event.segmentation = segmentationString;
        event.segmentationDouble = segmentationDouble;
        event.segmentationInt = segmentationInt;
        event.segmentationBoolean = segmentationBoolean;
        event.timestamp = timestamp;
        event.hour = hour;
        event.dow = dow;
        event.count = count;
        event.sum = sum;
        event.dur = dur;

        addEvent(event);
    }

    /**
     * Removes the specified events from the local store. Does nothing if the event collection
     * is null or empty.
     *
     * @param eventsToRemove collection containing the events to remove from the local store
     */
    public synchronized void removeEvents(final Collection<Event> eventsToRemove) {
        if (eventsToRemove != null && eventsToRemove.size() > 0) {
            final List<Event> events = getEventList();
            if (events.removeAll(eventsToRemove)) {
                preferences_.edit().putString(EVENTS_PREFERENCE, joinEvents(events, DELIMITER)).apply();
            }
        }
    }

    /**
     * Converts a collection of Event objects to URL-encoded JSON to a string, with each
     * event JSON string delimited by the specified delimiter.
     *
     * @param collection events to join into a delimited string
     * @param delimiter delimiter to use, should not be something that can be found in URL-encoded JSON string
     */
    @SuppressWarnings("SameParameterValue")
    static String joinEvents(final Collection<Event> collection, final String delimiter) {
        final List<String> strings = new ArrayList<>();
        for (Event e : collection) {
            strings.add(e.toJSON().toString());
        }
        return Utils.joinCountlyStore(strings, delimiter);
    }

    public static synchronized void cachePushData(String id_key, String index_key, Context context) {
        SharedPreferences sp = createPreferencesPush(context);
        sp.edit().putString(CACHED_PUSH_ACTION_ID, id_key).apply();
        sp.edit().putString(CACHED_PUSH_ACTION_INDEX, index_key).apply();
    }

    String[] getCachedPushData() {
        String[] res = new String[2];
        res[0] = preferencesPush_.getString(CACHED_PUSH_ACTION_ID, null);
        res[1] = preferencesPush_.getString(CACHED_PUSH_ACTION_INDEX, null);
        return res;
    }

    void clearCachedPushData() {
        preferencesPush_.edit().remove(CACHED_PUSH_ACTION_ID).apply();
        preferencesPush_.edit().remove(CACHED_PUSH_ACTION_INDEX).apply();
    }

    public static void cacheLastMessagingMode(int mode, Context context) {
        SharedPreferences sp = createPreferencesPush(context);
        sp.edit().putInt(CACHED_PUSH_MESSAGING_MODE, mode).apply();
    }

    public static int getLastMessagingMode(Context context) {
        SharedPreferences sp = createPreferencesPush(context);
        return sp.getInt(CACHED_PUSH_MESSAGING_MODE, -1);
    }

    public static void storeMessagingProvider(int provider, Context context) {
        SharedPreferences sp = createPreferencesPush(context);
        sp.edit().putInt(CACHED_PUSH_MESSAGING_PROVIDER, provider).apply();
    }

    public static int getMessagingProvider(Context context) {
        SharedPreferences sp = createPreferencesPush(context);
        return sp.getInt(CACHED_PUSH_MESSAGING_PROVIDER, 0);
    }

    // for unit testing
    public synchronized void clear() {
        final SharedPreferences.Editor prefsEditor = preferences_.edit();
        prefsEditor.remove(EVENTS_PREFERENCE);
        prefsEditor.remove(REQUEST_PREFERENCE);
        prefsEditor.clear();
        prefsEditor.apply();

        preferencesPush_.edit().clear().apply();
    }

    public String getDeviceID() {
        return preferences_.getString(PREFERENCE_KEY_ID_ID, null);
    }

    public String getDeviceIDType() {
        return preferences_.getString(PREFERENCE_KEY_ID_TYPE, null);
    }

    public void setDeviceID(String id) {
        if (id == null) {
            preferences_.edit().remove(PREFERENCE_KEY_ID_ID).apply();
        } else {
            preferences_.edit().putString(PREFERENCE_KEY_ID_ID, id).apply();
        }
    }

    public void setDeviceIDType(String type) {
        if (type == null) {
            preferences_.edit().remove(PREFERENCE_KEY_ID_TYPE).apply();
        } else {
            preferences_.edit().putString(PREFERENCE_KEY_ID_TYPE, type).apply();
        }
    }

    public int getDataSchemaVersion() {
        return preferences_.getInt(STORAGE_SCHEMA_VERSION, -1);
    }

    public void setDataSchemaVersion(int version) {
        preferences_.edit().putInt(STORAGE_SCHEMA_VERSION, version).apply();
    }

    /**
     * Check all used preferences to see if any one of the has set some data
     * This would be an indicator that the SDK had been started before
     *
     * @return
     */
    @Override public boolean anythingSetInStorage() {
        if (preferences_.getString(REQUEST_PREFERENCE, null) != null) {
            return true;
        }

        if (preferences_.getString(EVENTS_PREFERENCE, null) != null) {
            return true;
        }

        if (preferences_.getString(STAR_RATING_PREFERENCE, null) != null) {
            return true;
        }

        if (preferences_.getString(CACHED_ADVERTISING_ID, null) != null) {
            return true;
        }

        if (preferences_.getString(REMOTE_CONFIG_VALUES, null) != null) {
            return true;
        }

        if (preferences_.getString(PREFERENCE_KEY_ID_ID, null) != null) {
            return true;
        }

        if (preferences_.getString(PREFERENCE_KEY_ID_TYPE, null) != null) {
            return true;
        }

        if (preferences_.getInt(STORAGE_SCHEMA_VERSION, -100) != -100) {
            return true;
        }

        if (preferencesPush_.getInt(CACHED_PUSH_MESSAGING_MODE, -100) != -100) {
            return true;
        }

        if (preferencesPush_.getInt(CACHED_PUSH_MESSAGING_PROVIDER, -100) != -100) {
            return true;
        }

        if (preferencesPush_.getString(CACHED_PUSH_ACTION_ID, null) != null) {
            return true;
        }

        //noinspection RedundantIfStatement
        if (preferencesPush_.getString(CACHED_PUSH_ACTION_INDEX, null) != null) {
            return true;
        }

        return false;
    }
}
