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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * This class is the public API for the Countly Android SDK.
 * Get more details <a href="https://github.com/Countly/countly-sdk-android">here</a>.
 */
public class Countly {

    /**
     * Current version of the Count.ly Android SDK as a displayable string.
     */
    protected static final String COUNTLY_SDK_VERSION_STRING = "15.04";
    /**
     * Default string used in the begin session metrics if the
     * app version cannot be found.
     */
    protected static final String DEFAULT_APP_VERSION = "1.0";
    /**
     * Tag used in all logging in the Count.ly SDK.
     */
    protected static final String TAG = "Countly";

    /**
     * Determines how many custom events can be queued locally before
     * an attempt is made to submit them to a Count.ly server.
     */
    protected static final int EVENT_QUEUE_SIZE_THRESHOLD = 10;
    /**
     * How often onTimer() is called.
     */
    protected static final long TIMER_DELAY_IN_SECONDS = 60;

    /**
     * Whether developer is responsible for session handling (starting & stopping) or Countly SDK is.
     */
    protected static boolean programmaticSessionHandling = false;

    /**
     * Enum used in Countly.initMessaging() method which controls what kind of
     * app installation it is. Later (in Countly Dashboard or when calling Countly API method),
     * you'll be able to choose whether you want to send a message to test devices,
     * or to production ones.
     */
    public static enum CountlyMessagingMode {
        TEST,
        PRODUCTION,
    }

    // see http://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
    private static class SingletonHolder {
        static final Countly instance = new Countly();
    }

    @SuppressWarnings("FieldCanBeLocal")
    protected CountlyStore store_;
    private int activityCount_;
    private boolean disableUpdateSessionRequests_;
    protected boolean enableLogging_;
    private Countly.CountlyMessagingMode messagingMode_;
    private Context context_;

    protected String metrics_;
    protected String appKey_;
    private String serverURL_;
    private DeviceId deviceId_;

    /**
     * Returns the Countly singleton.
     */
    public static Countly sharedInstance() {
        return SingletonHolder.instance;
    }

    /**
     * Constructs a Countly object.
     * Creates a new ConnectionQueue and initializes the session timer.
     */
    private Countly() {
    }

    /**
     * Enables manual session handling.
     *
     * By default, Countly SDK is responsible for session duration calculation in case app is:
     * - calling onResume() & onPause() in each Activity for Android API < 14;
     * - calling or not calling onResume() & onPause() in each Activity for Android API > 14.
     *
     * This method disables this functionality to give developer ability to control sessions
     * programmatically, that is calling Countly.startSession() & CountlySession.end() methods
     * whenever needed.
     *
     * !!!! IMPORTANT !!!! This method must be called before Countly.init(). Otherwise,
     * additional session will be recorded.
     *
     * @throws java.lang.IllegalStateException if the Countly SDK has already been initialized
     */
    public static Countly enableProgrammaticSessionHandling() {
        if (sharedInstance().context_ != null) {
            throw new IllegalStateException("enableProgrammaticSessionHandling() must be called prior to Countly.init()");
        }

        programmaticSessionHandling = true;

        return Countly.sharedInstance();
    }

    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * Device ID is supplied by OpenUDID service if available, otherwise Advertising ID is used.
     * BE CAUTIOUS!!!! If neither OpenUDID, nor Advertising ID is available, Countly will ignore this user.
     * @param context application context
     * @param serverURL URL of the Countly server to submit data to; use "https://cloud.count.ly" for Countly Cloud
     * @param appKey app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications
     * @return Countly instance for easy method chaining
     * @throws java.lang.IllegalArgumentException if context, serverURL, appKey, or deviceID are invalid
     * @throws java.lang.IllegalStateException if the Countly SDK has already been initialized
     */
    public Countly init(final Context context, final String serverURL, final String appKey) {
        return init(context, serverURL, appKey, null, OpenUDIDAdapter.isOpenUDIDAvailable() ? DeviceId.Type.OPEN_UDID : DeviceId.Type.ADVERTISING_ID);
    }

    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * @param context application context
     * @param serverURL URL of the Countly server to submit data to; use "https://cloud.count.ly" for Countly Cloud
     * @param appKey app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications
     * @param deviceID unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID
     * @return Countly instance for easy method chaining
     * @throws IllegalArgumentException if context, serverURL, appKey, or deviceID are invalid
     * @throws IllegalStateException if init has previously been called with different values during the same application instance
     */
    public Countly init(final Context context, final String serverURL, final String appKey, final String deviceID) {
        return init(context, serverURL, appKey, deviceID, null);
    }

    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * @param context application context
     * @param serverURL URL of the Countly server to submit data to; use "https://cloud.count.ly" for Countly Cloud
     * @param appKey app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications
     * @param deviceID unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID
     * @param idMode enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID
     * @return Countly instance for easy method chaining
     * @throws IllegalArgumentException if context, serverURL, appKey, or deviceID are invalid
     * @throws IllegalStateException if init has previously been called with different values during the same application instance
     */
    public synchronized Countly init(final Context context, final String serverURL, final String appKey, final String deviceID, DeviceId.Type idMode) {
        if (context == null) {
            throw new IllegalArgumentException("valid context is required");
        }
        if (!isValidURL(serverURL)) {
            throw new IllegalArgumentException("valid serverURL is required");
        }
        if (appKey == null || appKey.length() == 0) {
            throw new IllegalArgumentException("valid appKey is required");
        }
        if (deviceID != null && deviceID.length() == 0) {
            throw new IllegalArgumentException("valid deviceID is required");
        }
        if (deviceID == null && idMode == null) {
            if (OpenUDIDAdapter.isOpenUDIDAvailable()) idMode = DeviceId.Type.OPEN_UDID;
            else if (AdvertisingIdAdapter.isAdvertisingIdAvailable()) idMode = DeviceId.Type.ADVERTISING_ID;
        }
        if (deviceID == null && idMode == DeviceId.Type.OPEN_UDID && !OpenUDIDAdapter.isOpenUDIDAvailable()) {
            throw new IllegalArgumentException("valid deviceID is required because OpenUDID is not available");
        }
        if (deviceID == null && idMode == DeviceId.Type.ADVERTISING_ID && !AdvertisingIdAdapter.isAdvertisingIdAvailable()) {
            throw new IllegalArgumentException("valid deviceID is required because Advertising ID is not available (you need to include Google Play services 4.0+ into your project)");
        }
        if (appKey_ != null && (!appKey_.equals(appKey) ||
                                !serverURL_.equals(serverURL) ||
                                 !DeviceId.deviceIDEqualsNullSafe(deviceID, idMode, deviceId_) )) {
            throw new IllegalStateException("Countly cannot be reinitialized with different values");
        }

        // In some cases CountlyMessaging does some background processing, so it needs a way
        // to start Countly on itself
        if (MessagingAdapter.isMessagingAvailable()) {
            MessagingAdapter.storeConfiguration(context, serverURL, appKey, deviceID, idMode);
        }

        if (appKey_ == null) {
            metrics_ = DeviceInfo.getMetrics(context);
            appKey_ = appKey;
            serverURL_ = serverURL;

            store_ = new CountlyStore(context);

            if (deviceID != null) {
                deviceId_ = new DeviceId(store_, deviceID);
            } else {
                deviceId_ = new DeviceId(store_, idMode);
            }
            deviceId_.init(context, store_, true);

            ConnectionProcessor.start(store_, serverURL_, deviceId_);
        }

        context_ = context;

        if (!programmaticSessionHandling){
            CountlySession.startIfNoneExists();

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                if (context.getApplicationContext() instanceof Application) {
                    // Thanks to this, we don't have to call onStart() / onStop() for Android 4.0+ anymore
                    ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityCreated(Activity activity, Bundle bundle) {}

                        @Override
                        public void onActivityStarted(Activity activity) {
                            incrementActivityCount();
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                        }
                        @Override
                        public void onActivityPaused(Activity activity) {
//                        new Handler().postDelayed(new Runnable() {
//                            @Override
//                            public void run() {
//                                decrementActivityCount();
//                            }
//                        }, 1000);
                        }

                        @Override
                        public void onActivityStopped(Activity activity) {
                            decrementActivityCount();
                        }

                        @Override
                        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}
                        @Override
                        public void onActivityDestroyed(Activity activity) {}
                    });
                } else {
                    Log.e(TAG, "Please update Countly SDK to latest version (Context.getApplicationContext() returns non-instance of Application)");
                }
            }
        }

        return this;
    }

    /**
     * Start new session.
     * @return
     */
    public synchronized CountlySession startSession() {
        CountlySession session = new CountlySession(disableUpdateSessionRequests_ ? 0 : TIMER_DELAY_IN_SECONDS, appKey_, metrics_, store_, deviceId_);

        //check if there is an install referrer data
        String referrer = ReferrerReceiver.getReferrer(context_);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Checking referrer: " + referrer);
        }
        if (referrer != null) {
            session.setReferrer(referrer);
            ReferrerReceiver.deleteReferrer(context_);
        }

        return session;
    }

    /**
     * Checks whether Countly.init has been already called.
     * @return true if Countly is ready to use
     */
    public synchronized boolean isInitialized() {
        return appKey_ != null;
    }

    /**
     * Initializes the Countly MessagingSDK. Call from your main Activity's onCreate() method.
     * @param activity application activity which acts as a final destination for notifications
     * @param activityClass application activity class which acts as a final destination for notifications
     * @param projectID ProjectID for this app from Google API Console
     * @param mode whether this app installation is a test release or production
     * @return Countly instance for easy method chaining
     * @throws IllegalStateException if no CountlyMessaging class is found (you need to use countly-messaging-sdk-android library instead of countly-sdk-android)
     */
    public Countly initMessaging(Activity activity, Class<? extends Activity> activityClass, String projectID, Countly.CountlyMessagingMode mode) {
        return initMessaging(activity, activityClass, projectID, null, mode);
    }

    /**
     * Initializes the Countly MessagingSDK. Call from your main Activity's onCreate() method.
     * @param activity application activity which acts as a final destination for notifications
     * @param activityClass application activity class which acts as a final destination for notifications
     * @param projectID ProjectID for this app from Google API Console
     * @param buttonNames Strings to use when displaying Dialogs (uses new String[]{"Open", "Review"} by default)
     * @param mode whether this app installation is a test release or production
     * @return Countly instance for easy method chaining
     * @throws IllegalStateException if no CountlyMessaging class is found (you need to use countly-messaging-sdk-android library instead of countly-sdk-android)
     */
    public synchronized Countly initMessaging(Activity activity, Class<? extends Activity> activityClass, String projectID, String[] buttonNames, Countly.CountlyMessagingMode mode) {
        if (mode != null && !MessagingAdapter.isMessagingAvailable()) {
            throw new IllegalStateException("you need to include countly-messaging-sdk-android library instead of countly-sdk-android if you want to use Countly Messaging");
        } else {
            if (!MessagingAdapter.init(activity, activityClass, projectID, buttonNames)) {
                throw new IllegalStateException("couldn't initialize Countly Messaging");
            }
        }
        messagingMode_ = mode;

        if (MessagingAdapter.isMessagingAvailable()) {
            MessagingAdapter.storeConfiguration(context_, serverURL_, appKey_, deviceId_.getId(), deviceId_.getType());
        }

        return this;
    }

    /**
     * Immediately disables session &amp; event tracking and clears any stored session &amp; event data.
     * This API is useful if your app has a tracking opt-out switch, and you want to immediately
     * disable tracking when a user opts out. The onStart/onStop/recordEvent methods will throw
     * IllegalStateException after calling this until Countly is reinitialized by calling init
     * again.
     */
    public synchronized void halt() {
        if (store_ != null) {
            store_.clear();
        }
        serverURL_ = null;
        appKey_ = null;
        deviceId_ = null;
        CountlySession.terminateAll(true);
        activityCount_ = 0;
    }

    /**
     * Tells the Countly SDK that an Activity has started. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStart methods for accurate application
     * session tracking.
     * @throws IllegalStateException if Countly SDK has not been initialized
     */
    public synchronized void onStart() {
        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before onStart");
        }

        // onStart() & onStop() are required only for Android versions < 4.0
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH || !(context_.getApplicationContext() instanceof Application)) {
            incrementActivityCount();
        }
    }

    /**
     * Called by Application.ActivityLifecycleCallbacks internally by Countly SDK
     */
    void incrementActivityCount() {
        ++activityCount_;
    }

    /**
     * Tells the Countly SDK that an Activity has stopped. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStop methods for accurate application
     * session tracking.
     * @throws IllegalStateException if Countly SDK has not been initialized, or if
     *                               unbalanced calls to onStart/onStop are detected
     */
    public synchronized void onStop() {
        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before onStop");
        }

        // onStart() & onStop() are required only for Android versions < 4.0
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH || !(context_.getApplicationContext() instanceof Application)) {
            decrementActivityCount();
        }
    }

    /**
     * Called by Application.ActivityLifecycleCallbacks internally by Countly SDK
     */
    void decrementActivityCount() {
        --activityCount_;
        if (activityCount_ <= 0) {
            CountlySession.terminateAll(false);
        }
    }

    /**
     * Called when GCM Registration ID is received. Sends a token session event to the server.
     */
    public void onRegistrationId(final String registrationId) {
        if (CountlySession.leading() == null) {
            CountlySession.whenSessionIsAvailableCalls.add(new Callable() {
                @Override
                public Object call() throws Exception {
                    Countly.sharedInstance().onRegistrationId(registrationId);
                    return null;
                }
            });
        } else {
            CountlySession.leading().setGCMToken(registrationId, messagingMode_);
        }
    }

    /**
     * Records a custom event with no segmentation values, a count of one and a sum of zero.
     * @param key name of the custom event, required, must not be the empty string
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public void recordEvent(final String key) {
        CountlySession.leading().recordEvent(key);
    }

    /**
     * Records a custom event with no segmentation values, the specified count, and a sum of zero.
     * @param key name of the custom event, required, must not be the empty string
     * @param count count to associate with the event, should be more than zero
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public void recordEvent(final String key, final int count) {
        CountlySession.leading().recordEvent(key, count);
    }

    /**
     * Records a custom event with no segmentation values, and the specified count and sum.
     * @param key name of the custom event, required, must not be the empty string
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public void recordEvent(final String key, final int count, final double sum) {
        CountlySession.leading().recordEvent(key, count, sum);
    }

    /**
     * Records a custom event with the specified segmentation values and count, and a sum of zero.
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public void recordEvent(final String key, final Map<String, String> segmentation, final int count) {
        CountlySession.leading().recordEvent(key, segmentation, count);
    }

    /**
     * Records a custom event with the specified values.
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public synchronized void recordEvent(final String key, final Map<String, String> segmentation, final int count, final double sum) {
        CountlySession.leading().recordEvent(key, segmentation, count, sum);
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
     * org - (String) providing user's organization's name where user works
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
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public synchronized void setUserData(Map<String, String> data) {
        CountlySession.leading().setUserData(data);
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
     * org - (String) providing user's organization's name where user works
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
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public synchronized void setUserData(Map<String, String> data, Map<String, String> customdata) {
        CountlySession.leading().setUserData(data, customdata);
    }

    /**
     * Sets custom properties.
     * In custom properties you can provide any string key values to be stored with user
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public synchronized void setCustomUserData(Map<String, String> customdata) {
        CountlySession.leading().setCustomUserData(customdata);
    }

    /**
     * Set user location.
     *
     * Countly detects user location based on IP address. But for geolocation-enabled apps,
     * it's better to supply exact location of user.
     * Allows sending messages to a custom segment of users located in a particular area.
     *
     * @param lat Latitude
     * @param lon Longitude
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     */
    public synchronized Countly setLocation(double lat, double lon) {
        CountlySession.leading().setLocation(lat, lon);
        return this;
    }

    /**
     * Change device ID.
     *
     * Supplied device ID is saved and used instead of strategy defined in Countly.init() parameters
     * for all subsequent API calls.
     * To revert device ID back to Countly.init() parameters, use Countly.revertDeviceId().
     * Typical use case: user login.
     *
     * @param newId New device ID
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     * @return Countly instance for easy method chaining
     */
    public synchronized Countly setDeviceId(String newId) {
        if (newId != null && !"".equals(newId)) {
            String oldId = deviceId_.changeToDeveloperId(store_, newId);
            if (oldId != null) {
                CountlySession.leading().changeDeviceId(oldId, deviceId_.getId());
            }
        }
        return this;
    }

    /**
     * Revert device ID.
     *
     * Countly removes device ID saved when calling Countly.setDeviceId() and uses strategy
     * defined in Countly.init() parameters for all subsequent API calls.
     * Typical use case: user logout.
     *
     * @throws java.lang.NullPointerException if programmatic session management is enabled and no session is started yet
     * @return Countly instance for easy method chaining
     */
    public synchronized Countly revertDeviceId() {
        String oldId = deviceId_.revertFromDeveloperId(store_);
        if (oldId != null) {
            CountlySession.leading().changeDeviceId(oldId, deviceId_.getId());
        }
        return this;
    }

    /**
     * Disable periodic session time updates.
     * By default, Countly will send a request to the server each 30 seconds with a small update
     * containing session duration time. This method allows you to disable such behavior.
     * Note that event updates will still be sent every 10 events or 30 seconds after event recording.
     * @param disable whether or not to disable session time updates
     * @return Countly instance for easy method chaining
     */
    public synchronized Countly setDisableUpdateSessionRequests(final boolean disable) {
        disableUpdateSessionRequests_ = disable;
        CountlySession.setUpdatesEnabled(!disable);
        return this;
    }

    /**
     * Sets whether debug logging is turned on or off. Logging is disabled by default.
     * @param enableLogging true to enable logging, false to disable logging
     * @return Countly instance for easy method chaining
     */
    public synchronized Countly setLoggingEnabled(final boolean enableLogging) {
        enableLogging_ = enableLogging;
        return this;
    }

    public synchronized boolean isLoggingEnabled() {
        return enableLogging_;
    }

    /**
     * Utility method to return a current timestamp that can be used in the Count.ly API.
     */
    static int currentTimestamp() {
        return ((int)(System.currentTimeMillis() / 1000l));
    }

    /**
     * Utility method for testing validity of a URL.
     */
    static boolean isValidURL(final String urlStr) {
        boolean validURL = false;
        if (urlStr != null && urlStr.length() > 0) {
            try {
                new URL(urlStr);
                validURL = true;
            }
            catch (MalformedURLException e) {
                validURL = false;
            }
        }
        return validURL;
    }

    // for unit testing
    int getActivityCount() { return activityCount_; }
    boolean getDisableUpdateSessionRequests() { return disableUpdateSessionRequests_; }
}
