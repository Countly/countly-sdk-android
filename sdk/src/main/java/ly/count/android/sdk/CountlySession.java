package ly.count.android.sdk;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by artem on 24/04/15.
 */


public class CountlySession {
    /**
     * Session begin time
     */
    private long beganOrUpdated = System.nanoTime();

    /**
     * Session has been already finished
     */
    private boolean ended = false;

    /**
     * Session sends update requests to the server
     */
    private boolean updates = true;

    /**
     * Session is leading, that is first one in line.
     * Only leading session can send requests to the server. All other sessions
     * have to wait in line.
     */
    private boolean leading = false;

    /**
     * Internal request storage for non-leading session
     */
    private List<String> queue = new ArrayList<String>();

    /**
     * Device metrics, JSON encoded.
     */
    private final String metrics;

    /**
     * Application key.
     */
    private final String appKey;

    /**
     * Countly store instance.
     */
    private final CountlyStore store;

    /**
     * device ID.
     */
    private String deviceID;
    private DeviceId deviceIDManager;

    /**
     * Timer for update session requests
     */
    private ScheduledExecutorService timer;

    /**
     * List of sessions currently active
     */
    private static final List<CountlySession> sessions = Collections.synchronizedList(new ArrayList<CountlySession>());

    /**
     * Utility method to start new session.
     * @return CountlySession instance.
     */
    public static CountlySession start() {
        return Countly.sharedInstance().startSession();
    }

    protected static CountlySession startIfNoneExists() {
        if (sessions.size() == 0) {
            return Countly.sharedInstance().startSession();
        }
        return null;
    }

    protected static CountlySession leading() {
        if (sessions.size() == 0) {
            return null;
        } else {
            return sessions.get(0);
        }
    }

    protected static CountlySession leadingOrNew() {
        if (leading() == null) {
            return start();
        } else {
            return leading();
        }
    }

    protected static void terminateAll(boolean noRequests) {
        for (CountlySession session : sessions) {
            session.terminate(noRequests);
        }
    }

    protected static void setUpdatesEnabled(boolean updatesEnabled) {
        for (CountlySession session : sessions) {
            session.updates = updatesEnabled;
        }
    }

    protected static final List<Callable> whenSessionIsAvailableCalls = new ArrayList<Callable>();

    /**
     * Start new session
     * @param interval update interval
     */
    protected CountlySession(long interval, String appKey, String metrics, CountlyStore store, DeviceId deviceIdManager) {
        if (interval > 0) {
            timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    update();
                }
            }, interval, interval, TimeUnit.SECONDS);
        }

        this.appKey = appKey;
        this.metrics = metrics;
        this.store = store;
        this.deviceIDManager = deviceIDManager;

        synchronized (sessions) {
            sessions.add(this);

            if (sessions.size() == 1) {
                leading = true;

                if (whenSessionIsAvailableCalls.size() > 0) {
                    for (Callable callable : whenSessionIsAvailableCalls) {
                        try {
                            callable.call();
                        } catch (Throwable e) {
                            Log.e(Countly.TAG, "Exception while processing delayed-until-session-available call", e);
                        }
                    }
                    whenSessionIsAvailableCalls.clear();
                }
            }
        }

        addSessionRequest(genBegin());
    }

    protected void setDeviceID(String deviceID) {
        this.deviceID = deviceID;
    }

    protected String getDeviceID() {
        if (deviceID == null) {
            deviceID = deviceIDManager.getId();
        }
        return deviceID;
    }

    /**
     * Called by timer service, sends update request to the server
     */
    private void update() {
        if (!ended && updates) {
            addSessionRequest(genUpdate());
        }
    }

    /**
     * End this session, send corresponding request to the server
     */
    public void end() {
        end(false);
    }

    private synchronized void end(boolean noRequests) {
        if (!ended) {
            if (timer != null && !timer.isShutdown() && !timer.isTerminated()) {
                timer.shutdown();
            }
            if (!noRequests) {
                addSessionRequest(genEnd());
            }
            ended = true;
            synchronized (sessions) {
                sessions.remove(this);
                if (sessions.size() > 0) {
                    sessions.get(0).promote();
                }
            }
        }
    }

    private synchronized void promote() {
        if (!leading) {
            leading = true;
            for (String request : queue) {
                addCommonRequest(request);
            }
            queue.clear();
        }
    }

    /**
     * App is closing, save all requests in queue
     */
    protected void terminate(boolean noRequests) {
        if (!ended) {
            end();

            if (noRequests) {
                queue.clear();
            } else {
                for (String request : queue) {
                    addCommonRequest(request);
                }
            }
        }
    }

    public void recordEvent(final String key) {
        recordEvent(key, null, 1, 0);
    }

    public void recordEvent(final String key, final int count) {
        recordEvent(key, null, count, 0);
    }

    public void recordEvent(final String key, final int count, final double sum) {
        recordEvent(key, null, count, sum);
    }

    public void recordEvent(final String key, final Map<String, String> segmentation, final int count) {
        recordEvent(key, segmentation, count, 0);
    }

    /**
     * Record event with all possible options
     * @param key key string for this event
     * @param segmentation further details
     * @param count how much times event happened
     * @param sum sum for sum-based events (like purchase: count = 1, sum = $15)
     */
    public synchronized void recordEvent(final String key, final Map<String, String> segmentation, final int count, final double sum) {
        if (!Countly.sharedInstance().isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Valid Countly event key is required");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Countly event count should be greater than zero");
        }
        if (segmentation != null) {
            for (String k : segmentation.keySet()) {
                if (k == null || k.length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                }
                if (segmentation.get(k) == null || segmentation.get(k).length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation value cannot be null or empty");
                }
            }
        }

        store.addEvent(key, segmentation, Countly.currentTimestamp(), count, sum);

        if (store.events().length >= Countly.EVENT_QUEUE_SIZE_THRESHOLD) {
            addCommonRequest(genEvents(store.eventsJSON()));
        }
    }

    /**
     * Set exact location of a user for geolocation-aware push notifications (Enterprise Edition only)
     * @param lat latitude
     * @param lon longitude
     */
    public void setLocation(double lat, double lon) {
        store.setLocation(lat, lon);
        update();
    }


    public void setUserData(final Map<String, String> data) {
        UserData.setData(data);
        addSessionRequest(genUserData());
    }

    public void setUserData(final Map<String, String> data, Map<String, String> customdata) {
        UserData.setData(data);
        UserData.setCustomData(customdata);
        addSessionRequest(genUserData());
    }

    public synchronized void setCustomUserData(Map<String, String> customdata) {
        UserData.setCustomData(customdata);
        addSessionRequest(genUserData());
    }

    public void setReferrer(String referrer) {
        addSessionRequest(genReferrer(referrer));
    }

    public void changeDeviceId(String oldId, String newId) {
        addSessionRequest(genChangeId(oldId, newId));
    }

    public void setGCMToken(String token, Countly.CountlyMessagingMode mode) {
        addSessionRequest(genGCMToken(token, mode));
    }

    public void setUpdates(boolean updates) {
        this.updates = updates;
    }

    private String genBegin() {
        return "begin_session=1"
                + "&timestamp=" + Countly.currentTimestamp()
                + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                + "&app_key=" + appKey
                + "&metrics=" + metrics;
    }

    private String genUpdate() {
        long time = System.nanoTime();
        int duration = (int) Math.round((time - beganOrUpdated) / 1000000000.0d);
        beganOrUpdated = time;

        if (duration > 0 || store.events().length > 0) {
            return "app_key=" + appKey
                    + "&timestamp=" + Countly.currentTimestamp()
                    + (duration > 0 ? "&session_duration=" + duration : "")
                    + (store.events().length > 0 ? "&events=" + store.eventsJSON() : "")
                    + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                    + "&location=" + store.getAndRemoveLocation();
        }

        return null;
    }

    private String genEnd() {
        long time = System.nanoTime();
        int duration = (int) Math.round((time - beganOrUpdated) / 1000000000.0d);
        beganOrUpdated = time;

        String data = "end_session=1"
                + "&timestamp=" + Countly.currentTimestamp()
                + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                + "&app_key=" + appKey;
        if (duration > 0) {
            data += "&session_duration=" + duration;
        }
        if (store.events().length > 0) {
            data += "&events=" + store.eventsJSON();
        }

        return data;
    }

    private String genUserData() {
        String data = UserData.getDataForRequest();
        if ("".equals(data)) {
            return null;
        } else {
            return "app_key=" + appKey
                    + "&timestamp=" + Countly.currentTimestamp()
                    + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                    + UserData.getDataForRequest();
        }
    }

    protected String genEvents(String events) {
        return "events=" + events
                + "&timestamp=" + Countly.currentTimestamp()
                + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                + "&app_key=" + appKey;
    }

    protected String genReferrer(String referrer) {
        return "app_key=" + appKey
                + "&timestamp=" + Countly.currentTimestamp()
                + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                + referrer;
    }

    protected String genChangeId(String oldId, String newId) {
        if (oldId == null || newId == null || "".equals(oldId) || "".equals(newId)) {
            return "old_device_id=" + oldId
                    + "&timestamp=" + Countly.currentTimestamp()
                    + "&app_key=" + appKey
                    + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                    + "&device_id=" + newId;
        }
        return null;
    }

    protected String genGCMToken(String token, Countly.CountlyMessagingMode mode) {
        return "token_session=1"
                + "&" + "timestamp=" + Countly.currentTimestamp()
                + "&" + "app_key=" + appKey
                + "&" + "android_token=" + (token == null ? "" : token)
                + "&" + "test_mode=" + (mode == Countly.CountlyMessagingMode.TEST ? 2 : 0)
                + "&" + "sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
                + "&" + "locale=" + DeviceInfo.getLocale();
    }

    /**
     * Common request is once that can be sent any time, the one which doesn't depend on leading-ness of session.
     * @param request string with API request
     * @return true to have same signature as addSessionRequest() has
     */
    private boolean addCommonRequest(String request) {
        if (request == null || "".equals(request)) {
            return false;
        } else {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Added common request: " + request);
            }
            store.addConnection(request);
            return true;
        }
    }

    /**
     * Session request is one of begin, update, end.
     * Only one session can be leading, that is active at a time. Another session cannot send begin / update / end
     * until leading session sent its end.
     * @param request string with API request
     * @return true if leading, false otherwise
     */
    private boolean addSessionRequest(String request) {
        if (request == null || "".equals(request)) {
            return false;
        } else if (leading) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Added session request for leading session: " + request);
            }
            store.addConnection(request);
            return true;
        } else {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Queued session request for secondary session: " + request);
            }
            queue.add(request);
            return false;
        }
    }
}
