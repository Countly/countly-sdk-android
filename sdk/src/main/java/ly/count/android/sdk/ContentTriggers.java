package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The content triggers ("jt") the server sends next to the SDK behavior settings ("c"): what the SDK records that makes
 * it send what it holds and ask for content right away, instead of waiting for the content zone timer.
 * <p>
 * "jt": {"e": ["purchase", "[CLY]_session"], "v": ["Checkout"], "p": ["custom.plan"]}
 * <p>
 * "e" holds event keys, and the keys of what the SDK records besides events, "v" holds view names and "p" holds user
 * properties. A list the server leaves out is an empty one.
 */
final class ContentTriggers {
    static final String KEY_SESSION = "[CLY]_session";
    static final String KEY_USER_PROPERTIES = "[CLY]_property_update";
    static final String KEY_CRASH = "[CLY]_crash";
    static final String KEY_CONSENT = "[CLY]_consent";

    /** Least time before the same trigger starts another round, for a listed event key, view name or user property */
    static final long GAP_SPECIFIC_MS = 10_000;
    /** Least time before the same trigger starts another round, for any view, any user property or any error */
    static final long GAP_NON_SPECIFIC_MS = 60_000;

    private static final String LIST_KEYS = "e";
    private static final String LIST_VIEWS = "v";
    private static final String LIST_PROPERTIES = "p";
    private static final String GAP_KEY_VIEW_PREFIX = "v:";
    private static final String GAP_KEY_PROPERTY_PREFIX = "p:";
    private static final String GAP_KEY_VIEW_END_SUFFIX = ":end";

    @NonNull final Set<String> keys;
    @NonNull final Set<String> views;
    @NonNull final Set<String> properties;

    private ContentTriggers(@NonNull Set<String> keys, @NonNull Set<String> views, @NonNull Set<String> properties) {
        this.keys = Collections.unmodifiableSet(keys);
        this.views = Collections.unmodifiableSet(views);
        this.properties = Collections.unmodifiableSet(properties);
    }

    /**
     * Reads the "jt" value of a behavior settings answer
     *
     * @param value the "jt" value, null when the answer has none
     * @param L logger
     * @return the content triggers, or null when the answer has none or has an invalid value, so that the journey
     * trigger events and views apply
     */
    @Nullable
    static ContentTriggers parse(@Nullable Object value, @NonNull ModuleLog L) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof JSONObject)) {
            L.w("[ContentTriggers] parse, content triggers (jt) must be an object, ignoring them: [" + value + "]");
            return null;
        }
        JSONObject object = (JSONObject) value;
        return new ContentTriggers(readList(object, LIST_KEYS, L), readList(object, LIST_VIEWS, L), readList(object, LIST_PROPERTIES, L));
    }

    /**
     * Reads one list of the "jt" object: its non-empty strings
     *
     * @param object the "jt" object
     * @param name list name, "e", "v" or "p"
     * @param L logger
     * @return the values, empty when the list is missing or is not an array
     */
    @NonNull
    private static Set<String> readList(@NonNull JSONObject object, @NonNull String name, @NonNull ModuleLog L) {
        Set<String> values = new HashSet<>();
        if (!object.has(name)) {
            return values;
        }
        JSONArray array = object.optJSONArray(name);
        if (array == null) {
            L.w("[ContentTriggers] readList, content triggers (jt) list [" + name + "] must be an array, ignoring it");
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof String && !((String) item).isEmpty()) {
                values.add((String) item);
            }
        }
        return values;
    }

    /**
     * Something the SDK recorded, as content triggers compare it
     */
    static final class Trigger {
        @NonNull final String key;
        @Nullable final String viewName;
        final boolean viewEnd;
        @Nullable final List<String> properties;
        final boolean customEvent;

        private Trigger(@NonNull String key, @Nullable String viewName, boolean viewEnd, @Nullable List<String> properties, boolean customEvent) {
            this.key = key;
            this.viewName = viewName;
            this.viewEnd = viewEnd;
            this.properties = properties;
            this.customEvent = customEvent;
        }

        /**
         * @param key the event key, as the app passed it
         * @param customEvent true for an event of the app, false for one the SDK records itself
         * @return the trigger of a recorded event
         */
        @NonNull
        static Trigger event(@NonNull String key, boolean customEvent) {
            return new Trigger(key, null, false, null, customEvent);
        }

        /**
         * @param viewName the view name, as the app passed it
         * @param viewEnd false for the event recorded when the view starts, true for the one recorded when it ends
         * @return the trigger of a recorded view event
         */
        @NonNull
        static Trigger view(@NonNull String viewName, boolean viewEnd) {
            return new Trigger(ModuleViews.VIEW_EVENT_KEY, viewName, viewEnd, null, false);
        }

        /**
         * @param properties the user properties the request sends: a user detail ("email"), or "custom." and the key
         * of a custom property
         * @return the trigger of sent user properties
         */
        @NonNull
        static Trigger userProperties(@NonNull List<String> properties) {
            return new Trigger(KEY_USER_PROPERTIES, null, false, properties, false);
        }

        /**
         * @param key one of {@link #KEY_SESSION}, {@link #KEY_CRASH} or {@link #KEY_CONSENT}
         * @return the trigger of what the SDK records besides events
         */
        @NonNull
        static Trigger of(@NonNull String key) {
            return new Trigger(key, null, false, null, false);
        }

        /**
         * @param other another trigger
         * @return true when both stand for the same thing recorded
         */
        boolean sameAs(@NonNull Trigger other) {
            return key.equals(other.key) && viewEnd == other.viewEnd && customEvent == other.customEvent
                && (viewName == null ? other.viewName == null : viewName.equals(other.viewName))
                && (properties == null ? other.properties == null : properties.equals(other.properties));
        }

        @NonNull @Override public String toString() {
            return key + (viewName != null ? " [" + viewName + (viewEnd ? ", end" : ", start") + "]" : "") + (properties != null ? " " + properties : "");
        }
    }

    /**
     * A recorded trigger that starts a round: the triggers its gap is kept for, and the gap
     */
    static final class Match {
        @NonNull final List<String> gapKeys;
        final long gapMs;

        Match(@NonNull List<String> gapKeys, long gapMs) {
            this.gapKeys = gapKeys;
            this.gapMs = gapMs;
        }
    }

    /**
     * Whether what was recorded is a content trigger. When the server sent content triggers, only they decide, and
     * only while the app wants the content zone. Otherwise (an older server, or disableAutoContentTriggers) a custom
     * event key in the journey trigger events and a view name in the journey trigger views decide, also while the app
     * doesn't want the content zone.
     *
     * @param contentTriggers the content triggers in use, null when there are none
     * @param zoneWanted true after the app or the "ecz" setting entered the content zone, until it is exited
     * @param journeyTriggerEvents the journey trigger events ("jte")
     * @param journeyTriggerViews the journey trigger views ("jtv")
     * @param trigger what was recorded
     * @return the match, or null when it is not a content trigger
     */
    @Nullable
    static Match match(@Nullable ContentTriggers contentTriggers, boolean zoneWanted, @NonNull Set<String> journeyTriggerEvents, @NonNull Set<String> journeyTriggerViews,
        @NonNull Trigger trigger) {
        boolean isView = ModuleViews.VIEW_EVENT_KEY.equals(trigger.key);
        String viewSuffix = isView && trigger.viewEnd ? GAP_KEY_VIEW_END_SUFFIX : "";

        if (contentTriggers != null) {
            if (!zoneWanted) {
                return null;
            }
            if (isView && trigger.viewName != null && contentTriggers.views.contains(trigger.viewName)) {
                return new Match(Collections.singletonList(GAP_KEY_VIEW_PREFIX + trigger.viewName + viewSuffix), GAP_SPECIFIC_MS);
            }
            if (KEY_USER_PROPERTIES.equals(trigger.key) && trigger.properties != null) {
                List<String> gapKeys = new ArrayList<>();
                for (String property : trigger.properties) {
                    if (contentTriggers.properties.contains(property)) {
                        gapKeys.add(GAP_KEY_PROPERTY_PREFIX + property);
                    }
                }
                if (!gapKeys.isEmpty()) {
                    return new Match(gapKeys, GAP_SPECIFIC_MS);
                }
            }
            if (!contentTriggers.keys.contains(trigger.key)) {
                return null;
            }
            boolean nonSpecific = isView || KEY_USER_PROPERTIES.equals(trigger.key) || KEY_CRASH.equals(trigger.key);
            return new Match(Collections.singletonList(trigger.key + viewSuffix), nonSpecific ? GAP_NON_SPECIFIC_MS : GAP_SPECIFIC_MS);
        }

        if (isView) {
            if (trigger.viewName != null && journeyTriggerViews.contains(trigger.viewName)) {
                return new Match(Collections.singletonList(GAP_KEY_VIEW_PREFIX + trigger.viewName + viewSuffix), GAP_SPECIFIC_MS);
            }
            return null;
        }
        if (trigger.customEvent && journeyTriggerEvents.contains(trigger.key)) {
            return new Match(Collections.singletonList(trigger.key), GAP_SPECIFIC_MS);
        }
        return null;
    }

    /**
     * Whether a matched trigger may start a round now. When it may, the time of the round is kept in storage, so the
     * gap holds after a restart. Times older than the longest gap are dropped, and a time in the future is ignored.
     *
     * @param match the matched trigger
     * @param storedGaps the stored times, a JSON object of gap key to milliseconds, may be empty
     * @param nowMs the current time in milliseconds
     * @return the times to store when the trigger may start a round, or null when every one of its triggers is inside its gap
     */
    @Nullable
    static String claimGap(@NonNull Match match, @NonNull String storedGaps, long nowMs) {
        JSONObject times;
        try {
            times = storedGaps.isEmpty() ? new JSONObject() : new JSONObject(storedGaps);
        } catch (JSONException e) {
            times = new JSONObject();
        }

        boolean allInGap = true;
        for (String gapKey : match.gapKeys) {
            if (!inGap(times.optLong(gapKey, -1), nowMs, match.gapMs)) {
                allInGap = false;
                break;
            }
        }
        if (allInGap) {
            return null;
        }

        JSONObject kept = new JSONObject();
        Iterator<String> storedKeys = times.keys();
        while (storedKeys.hasNext()) {
            String gapKey = storedKeys.next();
            long time = times.optLong(gapKey, -1);
            if (inGap(time, nowMs, GAP_NON_SPECIFIC_MS)) {
                try {
                    kept.put(gapKey, time);
                } catch (JSONException ignored) {
                }
            }
        }
        for (String gapKey : match.gapKeys) {
            try {
                kept.put(gapKey, nowMs);
            } catch (JSONException ignored) {
            }
        }
        return kept.toString();
    }

    /**
     * @param timeMs time of the trigger's last round, -1 when it has none
     * @param nowMs the current time
     * @param gapMs the gap
     * @return true when the time is inside the gap
     */
    private static boolean inGap(long timeMs, long nowMs, long gapMs) {
        return timeMs >= 0 && nowMs >= timeMs && nowMs - timeMs < gapMs;
    }
}
