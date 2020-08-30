package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;

public class ModuleViews extends ModuleBase {
    // viewName to startTime
    @VisibleForTesting Map<String, Integer> namedViewStartTimes = new HashMap<>();
    // identity of object to start time
    @VisibleForTesting Map<Integer, Integer> identityStartTimes = new HashMap<>();
    // identity of object to custom segmentation
    private Map<Integer, Map<String, Object>> identitySegmentation = new HashMap<>();
    // manually added persistent names
    private Map<Integer, String> persistentNames = new HashMap<>();

    private boolean firstView = true;
    final static String VIEW_EVENT_KEY = "[CLY]_view";

    Class[] autoTrackingActivityExceptions = null;//excluded activities from automatic view tracking

    Map<String, Object> automaticViewSegmentation = new HashMap<>();//automatic view segmentation

    //track orientation changes
    boolean trackOrientationChanges = false;
    int currentOrientation = -1;
    final static String ORIENTATION_EVENT_KEY = "[CLY]_orientation";

    //interface for SDK users
    final Views viewsInterface;

    ModuleViews(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleViews] Initialising");
        }

        _cly.setViewTracking(config.enableViewTracking);
        _cly.setAutoTrackingUseShortName(config.autoTrackingUseShortName);

        setAutomaticViewSegmentationInternal(config.automaticViewSegmentation);
        autoTrackingActivityExceptions = config.autoTrackingExceptions;
        trackOrientationChanges = config.trackOrientationChange;

        viewsInterface = new Views();
    }

    void setAutomaticViewSegmentationInternal(Map<String, Object> segmentation) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleViews] Calling setAutomaticViewSegmentationInternal");
        }

        automaticViewSegmentation.clear();

        if (segmentation != null) {
            if (Utils.removeUnsupportedDataTypes(segmentation)) {
                //found a unsupported type, print warning

                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[ModuleViews] You have provided a unsupported type for automatic View Segmentation");
                }
            }

            Utils.removeKeysFromMap(segmentation, ModuleEvents.reservedSegmentationKeys);

            automaticViewSegmentation.putAll(segmentation);
        }
    }

    /**
     * Reports duration of a named view
     */
    @VisibleForTesting
    void reportNamedViewDuration(String viewName) {
        if (!_cly.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }
        if (namedViewStartTimes.containsKey(viewName)) {
            int curr = UtilsTime.currentTimestampSeconds();
            //noinspection ConstantConditions
            int start = namedViewStartTimes.get(viewName);
            int dur = curr - start;
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleViews] View [" + viewName + "] is getting closed, reporting duration: [" + dur + "], current timestamp: [" + curr + "], view start: [" + start + "]");
            }
            recordViewEventEnd(viewName, dur);
            namedViewStartTimes.remove(viewName);
        }
    }

    @VisibleForTesting
    <V> void reportIdentityViewDuration(@NonNull V obj) {
        String viewName = _cly.getViewName(obj);
        int identity = System.identityHashCode(obj);
        if (!_cly.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }
        if (identityStartTimes.containsKey(identity)) {
            int curr = UtilsTime.currentTimestampSeconds();
            //noinspection ConstantConditions
            int start = identityStartTimes.get(identity);
            int dur = curr - start;
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleViews] View [" + viewName + "] is getting closed, reporting duration: [" + dur + "], current timestamp: [" + curr + "], view start: [" + start + "]");
            }
            recordViewEventEnd(viewName, dur);
            identityStartTimes.remove(identity);
        }
    }

    private <V> boolean shouldTrack(V view) {
        if (autoTrackingActivityExceptions != null) {
            //noinspection rawtypes
            for (Class autoTrackingActivityException : autoTrackingActivityExceptions) {
                if (view.getClass().equals(autoTrackingActivityException)) {
                    return false;
                }
            }
        }
        return view.getClass().getAnnotation(DoNotTrack.class) == null;
    }

    /**
     * Record a view manually, without automatic tracking
     * or track view that is not automatically tracked
     * like fragment, Message box or transparent Activity
     *
     * @param viewName String - name of the view
     * @param customViewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
     * @return Returns link to Countly for call chaining
     */
    @VisibleForTesting
    synchronized Countly recordNamedView(String viewName, Map<String, Object> customViewSegmentation) {
        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
        }

        if(viewName == null || viewName.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleViews] Trying to record view with null or empty view name, ignoring request");
            }
            return _cly;
        }

        Map<String, Object> viewSegmentation = new HashMap<>();
        if (customViewSegmentation != null) {
            viewSegmentation.putAll(customViewSegmentation);
        }
        if (automaticViewSegmentation != null) {
            viewSegmentation.putAll(automaticViewSegmentation);
        }
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleViews] Recording view with name: [" + viewName + "], custom view segment count:[" + viewSegmentation.size() + "]");
        }
        namedViewStartTimes.put(viewName, UtilsTime.currentTimestampSeconds());
        recordViewEventStart(viewName, customViewSegmentation);
        return _cly;
    }

    @VisibleForTesting
    synchronized <V> Countly recordIdentityView(@NonNull V view) {
        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before trackLifecycle");
        }
        String viewName = _cly.getViewName(view);
        int identity = System.identityHashCode(view);
        Map<String, Object> viewSegmentation = new HashMap<>();
        if (identitySegmentation.containsKey(identity)) {
            //noinspection ConstantConditions
            viewSegmentation.putAll(identitySegmentation.get(identity));
        }
        if (automaticViewSegmentation != null) {
            viewSegmentation.putAll(automaticViewSegmentation);
        }
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleViews] Recording view with name: [" + viewName + "], custom view segment count:[" + viewSegmentation.size() + "]");
        }
        identityStartTimes.put(identity, UtilsTime.currentTimestampSeconds());
        recordViewEventStart(viewName, viewSegmentation);
        return _cly;
    }

    @VisibleForTesting
    void recordViewEventStart(@NonNull String viewName, @Nullable Map<String, Object> customViewSegmentation) {

        Map<String, Object> viewSegmentation = new HashMap<>();
        if (customViewSegmentation != null) {
            Utils.removeUnsupportedDataTypes(customViewSegmentation);
            Utils.removeKeysFromMap(customViewSegmentation, ModuleEvents.reservedSegmentationKeys);
            viewSegmentation.putAll(customViewSegmentation);
        }

        viewSegmentation.put("name", viewName);
        viewSegmentation.put("visit", "1");
        viewSegmentation.put("segment", "Android");
        if (firstView) {
            firstView = false;
            viewSegmentation.put("start", "1");
        }

        _cly.moduleEvents.recordEventInternal(VIEW_EVENT_KEY, viewSegmentation, 1, 0, 0, null, true);
    }

    @VisibleForTesting
    void recordViewEventEnd(@NonNull String viewName, int duration) {
        HashMap<String, Object> segments = new HashMap<>();
        segments.put("name", viewName);
        segments.put("dur", String.valueOf(duration));
        segments.put("segment", "Android");
        _cly.moduleEvents.recordEventInternal(VIEW_EVENT_KEY, segments, 1, 0, 0, null, true);
    }

    void updateOrientation(int newOrientation) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleViews] Calling [updateOrientation], new orientation:[" + newOrientation + "]");
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.events)) {
            //we don't have consent, just leave
            return;
        }

        if (currentOrientation != newOrientation) {
            currentOrientation = newOrientation;

            Map<String, String> segm = new HashMap<>();

            if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                segm.put("mode", "portrait");
            } else {
                segm.put("mode", "landscape");
            }

            _cly.recordEvent(ORIENTATION_EVENT_KEY, segm, 1);
        }
    }

    @Override
    void onConfigurationChanged(Configuration newConfig) {
        if (trackOrientationChanges) {
            Integer orient = getOrientationFromConfiguration(newConfig);
            if (orient != null) {
                updateOrientation(orient);
            }
        }
    }

    @Override
    <F> void onFragmentStarted(F fragment) {
        super.onFragmentStarted(fragment);
        onViewAppeared(fragment);
    }

    @Override
    <F> void onFragmentStopped(F fragment) {
        super.onFragmentStopped(fragment);
        onViewDisappeared(fragment);
    }

    @Override
    void onActivityStarted(Activity activity) {
        onViewAppeared(activity);
    }

    @Override
    void onActivityStopped(Activity activity) {
        onViewDisappeared(activity);
    }

    @VisibleForTesting
    <V> void onViewAppeared(@NonNull V view) {
        //automatic view tracking
        if (_cly.autoViewTracker) {
            if (shouldTrack(view)) {
                recordIdentityView(view);
            } else {
                if (_cly.isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[ModuleViews] [onViewAppeared] Ignoring activity because it's in the exception list");
                }
            }
        }
        if (view instanceof Activity) {
            trackOrientation((Activity) view);
        }
    }

    private void trackOrientation(@NonNull Activity activity) {
        //orientation tracking
        if (trackOrientationChanges) {
            Integer orient = getOrientationFromActivity(activity);
            if (orient != null) {
                updateOrientation(orient);
            }
        }
    }

    @VisibleForTesting
    <V> void onViewDisappeared(@NonNull V obj) {
        reportIdentityViewDuration(obj);
    }

    /**
     * Needed for mocking test result
     *
     * @param conf
     * @return
     */
    Integer getOrientationFromConfiguration(Configuration conf) {
        if (conf == null) {
            return null;
        }

        return conf.orientation;
    }

    /**
     * Needed for mocking test result
     *
     * @param act
     * @return
     */
    Integer getOrientationFromActivity(Activity act) {
        if (act == null) {
            return null;
        }
        Resources resources = act.getResources();
        if (resources != null) {
            return resources.getConfiguration().orientation;
        } else {
            return null;
        }
    }

    @Override
    void halt() {
        if (automaticViewSegmentation != null) {
            automaticViewSegmentation.clear();
            automaticViewSegmentation = null;
        }
        autoTrackingActivityExceptions = null;
    }

    public Map<Integer, String> getPersistentNames() {
        return this.persistentNames;
    }

    public class Views {
        /**
         * Check state of automatic view tracking
         *
         * @return boolean - true if enabled, false if disabled
         */
        public synchronized boolean isAutomaticViewTrackingEnabled() {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Views] Calling isAutomaticViewTrackingEnabled");
            }
            return _cly.autoViewTracker;
        }

        /**
         * Record a view manually, without automatic tracking
         * or track view that is not automatically tracked
         * like fragment, Message box or transparent Activity
         *
         * @param viewName String - name of the view
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized Countly recordView(String viewName) {
            return recordView(viewName, null);
        }

        /**
         * Record a view manually by name. Only 1 view of each unique name can be recorded at a time.
         *
         * @param viewName String - name of the view
         * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized Countly recordView(String viewName, Map<String, Object> viewSegmentation) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
            }

            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Views] Calling recordView [" + viewName + "]");
            }
            return recordNamedView(viewName, viewSegmentation);
        }

        /**
         * Record a view by identity hashcode.
         *
         * @param view The view
         * @param <V> an arbitrary type. Activity, Fragment, View, etc.
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized <V> Countly recordView(@NonNull V view) {
            return recordView(view, null, null);
        }

        /**
         * Record a view by identity hashcode.
         * @param view The view
         * @param viewSegmentation segmentation to be associated with this view
         * @param <V> an arbitrary type. Activity, Fragment, View, etc.
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized <V> Countly recordView(@NonNull V view, @Nullable Map<String, Object> viewSegmentation) {
            return recordView(view, viewSegmentation, null);
        }

        /**
         * Record a view by identity hashcode.
         * @param view The view
         * @param viewSegmentation segmentation to be associated with this view
         * @param persistentName a persistent name to be associated wit this view
         * @param <V> an arbitrary type. Activity, Fragment, View, etc.
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized <V> Countly recordView(@NonNull V view, @Nullable Map<String, Object> viewSegmentation, @Nullable String persistentName) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
            }

            if (!shouldTrack(view)) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Views] View is in exception list or is annotated with"
                        + " @DoNotTrack. Ignoring...");
                }
                return _cly;
            }

            if (viewSegmentation != null) {
                addViewSegmentation(view, viewSegmentation);
            }

            if (persistentName != null) {
                addViewPersistentName(view, persistentName);
            }

            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Views] Calling recordView [" + _cly.getViewName(view) + "]");
            }
            return recordIdentityView(view);
        }

        /**
         * Ends the view recording for the provided view name
         * @param viewName The view's name
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized Countly endViewRecording(@NonNull String viewName) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before endViewRecording");
            }
            reportNamedViewDuration(viewName);
            return _cly;
        }

        /**
         * Ends the view recording for the provided view
         * @param view The view
         * @param <V> an arbitrary type. Activity, Fragment, View, etc.
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized <V> Countly endViewRecording(@NonNull V view) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before endViewRecording");
            }
            reportIdentityViewDuration(view);
            return _cly;
        }

        /**
         * Adds view segmentation for an arbitrary view
         * @param view The view
         * @param viewSegmentation View segmentation to use for view tracking
         * @param <V> an arbitrary type. Activity, Fragment, View, etc.
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized <V> Countly addViewSegmentation(@NonNull V view, @NonNull Map<String, Object> viewSegmentation) {
            if(!_cly.isInitialized()) {
                throw new IllegalStateException("Countly is not initialized");
            }
            identitySegmentation.put(System.identityHashCode(view), viewSegmentation);
            return _cly;
        }

        /**
         * Adds a persistent name for an arbitrary view
         * @param view The view
         * @param persistentName A {@link PersistentName} for the view
         * @param <V> an arbitrary type. Activity, Fragment, View, etc.
         * @return Returns link to Countly for call chaining
         * @throws IllegalStateException If Countly is not initialized
         */
        public synchronized <V> Countly addViewPersistentName(@NonNull V view, @NonNull String persistentName) {
            if(!_cly.isInitialized()) {
                throw new IllegalStateException("Countly is not initialized");
            }
            persistentNames.put(System.identityHashCode(view), persistentName);
            return _cly;
        }
    }
}
