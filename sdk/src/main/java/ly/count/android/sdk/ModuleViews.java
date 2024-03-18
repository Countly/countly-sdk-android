package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleViews extends ModuleBase implements ViewIdProvider {
    private String currentViewID = null;
    private String previousViewID = null;

    private boolean firstView = true;

    boolean autoViewTracker = false;
    boolean automaticTrackingShouldUseShortName = false;

    //track orientation changes
    boolean trackOrientationChanges;

    int currentOrientation = -1;
    final static String ORIENTATION_EVENT_KEY = "[CLY]_orientation";

    final static String VIEW_EVENT_KEY = "[CLY]_view";

    Class[] autoTrackingActivityExceptions = null;//excluded activities from automatic view tracking

    Map<String, Object> automaticViewSegmentation = new HashMap<>();//automatic view segmentation

    Map<String, ViewData> viewDataMap = new HashMap<>(); // map viewIDs to its viewData

    SafeIDGenerator safeViewIDGenerator;

    String[] reservedSegmentationKeysViews = { "name", "visit", "start", "segment" };

    public @NonNull String getCurrentViewId() {
        return currentViewID == null ? "" : currentViewID;
    }

    public @NonNull String getPreviousViewId() {
        return previousViewID == null ? "" : previousViewID;
    }

    static class ViewData {
        String viewID;
        long viewStartTimeSeconds; // if this is 0 then the view is not started yet or was paused
        String viewName;
        boolean isAutoStoppedView = false;//views started with "startAutoStoppedView" would have this as "true". If set to "true" views should be automatically closed when another one is started.
        boolean isAutoPaused = false;//this marks that this view automatically paused when going to the background
        Map<String, Object> viewSegmentation = null; // segmentation that can be updated while a view is on
    }

    //interface for SDK users
    final Views viewsInterface;

    /**
     * Checks the Countly config Object. Turns on/off the flags for view tracking accordingly.
     * And initiates the Views interface for the developer to interact with the SDK/ModuleViews.
     */
    ModuleViews(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleViews] Initializing");

        if (config.enableAutomaticViewTracking) {
            L.d("[ModuleViews] Enabling automatic view tracking");
            autoViewTracker = config.enableAutomaticViewTracking;
        }

        if (config.autoTrackingUseShortName) {
            L.d("[ModuleViews] Enabling automatic view tracking short names");
            automaticTrackingShouldUseShortName = config.autoTrackingUseShortName;
        }

        config.viewIdProvider = this;
        safeViewIDGenerator = config.safeViewIDGenerator;

        setGlobalViewSegmentationInternal(config.globalViewSegmentation);
        autoTrackingActivityExceptions = config.automaticViewTrackingExceptions;
        trackOrientationChanges = config.trackOrientationChange;

        viewsInterface = new Views();
    }

    /**
     * Checks the provided Segmentation by the user. Sanitizes it
     * and transfers the data into an internal Segmentation Object.
     */
    void setGlobalViewSegmentationInternal(@Nullable Map<String, Object> segmentation) {
        L.d("[ModuleViews] Calling setGlobalViewSegmentationInternal with[" + (segmentation == null ? "null" : segmentation.size()) + "] entries");

        automaticViewSegmentation.clear();

        if (segmentation != null) {

            Utils.removeReservedKeysFromSegmentation(segmentation, reservedSegmentationKeysViews, "[ModuleViews] setGlobalViewSegmentationInternal, ", L);

            if (Utils.removeUnsupportedDataTypes(segmentation)) {
                //found an unsupported type, print warning
                L.w("[ModuleViews] setGlobalViewSegmentationInternal, You have provided an unsupported data type in your View Segmentation. Removing the unsupported values.");
            }

            automaticViewSegmentation.putAll(segmentation);
        }
    }

    public void updateGlobalViewSegmentationInternal(@NonNull Map<String, Object> segmentation) {
        if (Utils.removeUnsupportedDataTypes(segmentation)) {
            //found an unsupported type, print warning
            L.w("[ModuleViews] updateGlobalViewSegmentationInternal, You have provided an unsupported data type in your View Segmentation. Removing the unsupported values.");
        }

        Utils.removeReservedKeysFromSegmentation(segmentation, reservedSegmentationKeysViews, "[ModuleViews] updateGlobalViewSegmentationInternal, ", L);

        automaticViewSegmentation.putAll(segmentation);
    }

    /**
     * Checks if the current Activity is in the Activity Exception list
     *
     * @return boolean - true if in the list, false else
     */
    boolean isActivityInExceptionList(Activity act) {
        if (autoTrackingActivityExceptions == null) {
            return false;
        }

        for (Class autoTrackingActivityException : autoTrackingActivityExceptions) {
            if (act.getClass().equals(autoTrackingActivityException)) {
                return true;
            }
        }

        return false;
    }

    /**
     * This should be called in case a new session starts so that we could identify the new "first view"
     */
    public void resetFirstView() {
        firstView = true;
    }

    Map<String, Object> CreateViewEventSegmentation(@NonNull ViewData vd, boolean firstView, boolean visit, Map<String, Object> customViewSegmentation) {
        Map<String, Object> viewSegmentation = new HashMap<>();
        if (customViewSegmentation != null) {
            UtilsInternalLimits.truncateSegmentationKeys(customViewSegmentation, _cly.config_.sdkInternalLimits.maxKeyLength, L, "[ModuleViews] CreateViewEventSegmentation");
            viewSegmentation.putAll(customViewSegmentation);
        }

        String truncatedViewName = UtilsInternalLimits.truncateKeyLength(vd.viewName, _cly.config_.sdkInternalLimits.maxKeyLength, L, "[ModuleViews] CreateViewEventSegmentation");
        viewSegmentation.put("name", truncatedViewName);
        if (visit) {
            viewSegmentation.put("visit", "1");
        }
        if (firstView) {
            viewSegmentation.put("start", "1");
        }
        viewSegmentation.put("segment", "Android");

        return viewSegmentation;
    }

    void autoCloseRequiredViews(boolean closeAllViews, Map<String, Object> customViewSegmentation) {
        L.d("[ModuleViews] autoCloseRequiredViews");
        List<String> viewsToRemove = new ArrayList<>(1);

        for (Map.Entry<String, ViewData> entry : viewDataMap.entrySet()) {
            ViewData vd = entry.getValue();
            if (closeAllViews || vd.isAutoStoppedView) {
                viewsToRemove.add(vd.viewID);
            }
        }

        if (viewsToRemove.size() > 0) {
            L.d("[ModuleViews] autoCloseRequiredViews, about to close [" + viewsToRemove.size() + "] views");
        }

        // todo: move to stopViewWithIDInternal?
        Utils.removeReservedKeysFromSegmentation(customViewSegmentation, reservedSegmentationKeysViews, "[ModuleViews] autoCloseRequiredViews, ", L);

        for (int a = 0; a < viewsToRemove.size(); a++) {
            stopViewWithIDInternal(viewsToRemove.get(a), customViewSegmentation);
        }
    }

    /**
     * Record a view manually, without automatic tracking
     * or tracks a view that is not automatically tracked
     * like a fragment, Message box or a transparent Activity
     * with segmentation if provided. (This is the internal function)
     *
     * @param viewName String - name of the view
     * @param customViewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
     * @return Returns link to Countly for call chaining
     */
    @Nullable String startViewInternal(@Nullable String viewName, @Nullable Map<String, Object> customViewSegmentation, boolean viewShouldBeAutomaticallyStopped) {
        if (!_cly.isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before startViewInternal");
            return null;
        }

        if (viewName == null || viewName.isEmpty()) {
            L.e("[ModuleViews] startViewInternal, Trying to record view with null or empty view name, ignoring request");
            return null;
        }

        // if segmentation is null this just returns so no null check necessary
        Utils.truncateSegmentationValues(customViewSegmentation, _cly.config_.sdkInternalLimits.maxSegmentationValues, "[ModuleViews] startViewInternal", L);

        Utils.removeReservedKeysFromSegmentation(customViewSegmentation, reservedSegmentationKeysViews, "[ModuleViews] autoCloseRequiredViews, ", L);

        if (L.logEnabled()) {
            int segmCount = 0;
            if (customViewSegmentation != null) {
                segmCount = customViewSegmentation.size();
            }
            L.d("[ModuleViews] Recording view with name: [" + viewName + "], previous view ID:[" + currentViewID + "] custom view segment count:[" + segmCount + "], first:[" + firstView + "], autoStop:[" + viewShouldBeAutomaticallyStopped + "]");
        }

        //stop views that should be automatically stopped
        //no segmentation should be used in this case
        autoCloseRequiredViews(false, null);

        ViewData currentViewData = new ViewData();
        currentViewData.viewID = safeViewIDGenerator.GenerateValue();
        currentViewData.viewName = viewName;
        currentViewData.viewStartTimeSeconds = UtilsTime.currentTimestampSeconds();
        currentViewData.isAutoStoppedView = viewShouldBeAutomaticallyStopped;

        viewDataMap.put(currentViewData.viewID, currentViewData);
        previousViewID = currentViewID;
        currentViewID = currentViewData.viewID;

        Map<String, Object> accumulatedEventSegm = new HashMap<String, Object>(automaticViewSegmentation);
        if (customViewSegmentation != null) {
            accumulatedEventSegm.putAll(customViewSegmentation);
        }

        Map<String, Object> viewSegmentation = CreateViewEventSegmentation(currentViewData, firstView, true, accumulatedEventSegm);

        if (firstView) {
            L.d("[ModuleViews] Recording view as the first one in the session. [" + viewName + "]");
            firstView = false;
        }

        eventProvider.recordEventInternal(VIEW_EVENT_KEY, viewSegmentation, 1, 0, 0, null, currentViewData.viewID);

        return currentViewData.viewID;
    }

    void stopViewWithNameInternal(@Nullable String viewName, @Nullable Map<String, Object> customViewSegmentation) {
        if (viewName == null || viewName.isEmpty()) {
            L.e("[ModuleViews] stopViewWithNameInternal, Trying to record view with null or empty view name, ignoring request");
            return;
        }

        String viewID = null;

        for (Map.Entry<String, ViewData> entry : viewDataMap.entrySet()) {
            ViewData vd = entry.getValue();
            if (vd != null && viewName.equals(vd.viewName)) {
                viewID = entry.getKey();
            }
        }

        if (viewID == null) {
            L.e("[ModuleViews] stopViewWithNameInternal, No view entry found with the provided name :[" + viewName + "]");
            return;
        }

        stopViewWithIDInternal(viewID, customViewSegmentation);
    }

    void stopViewWithIDInternal(@Nullable String viewID, @Nullable Map<String, Object> customViewSegmentation) {
        if (viewID == null || viewID.isEmpty()) {
            L.e("[ModuleViews] stopViewWithNameInternal, Trying to record view with null or empty view ID, ignoring request");
            return;
        }
        //todo extract common checks
        if (!viewDataMap.containsKey(viewID)) {
            L.w("[ModuleViews] stopViewWithIDInternal, there is no view with the provided view id to close");
            return;
        }

        ViewData vd = viewDataMap.get(viewID);
        if (vd == null) {
            L.e("[ModuleViews] stopViewWithIDInternal, view id:[" + viewID + "] has a 'null' value. This should not be happening");
            return;
        }

        L.d("[ModuleViews] View [" + vd.viewName + "], id:[" + vd.viewID + "] is getting closed, reporting duration: [" + (UtilsTime.currentTimestampSeconds() - vd.viewStartTimeSeconds) + "] s, current timestamp: [" + UtilsTime.currentTimestampSeconds() + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }

        // if segmentation is null this just returns so no null check necessary
        Utils.truncateSegmentationValues(customViewSegmentation, _cly.config_.sdkInternalLimits.maxSegmentationValues, "[ModuleViews] stopViewWithIDInternal", L);

        recordViewEndEvent(vd, customViewSegmentation, "stopViewWithIDInternal");

        viewDataMap.remove(vd.viewID);
    }

    void recordViewEndEvent(ViewData vd, @Nullable Map<String, Object> filteredCustomViewSegmentation, String viewRecordingSource) {
        long lastElapsedDurationSeconds = 0;
        //we sanity check the time component and print error in case of problem
        if (vd.viewStartTimeSeconds < 0) {
            L.e("[ModuleViews] " + viewRecordingSource + ", view start time value is not normal: [" + vd.viewStartTimeSeconds + "], ignoring that duration");
        } else if (vd.viewStartTimeSeconds == 0) {
            L.i("[ModuleViews] " + viewRecordingSource + ", view is either paused or didn't run, ignoring start timestamp");
        } else {
            lastElapsedDurationSeconds = UtilsTime.currentTimestampSeconds() - vd.viewStartTimeSeconds;
        }

        //only record view if the view name is not null
        if (vd.viewName == null) {
            L.e("[ModuleViews] stopViewWithIDInternal, view has no internal name, ignoring it");
            return;
        }

        Map<String, Object> accumulatedEventSegm = new HashMap<String, Object>(automaticViewSegmentation);
        if (filteredCustomViewSegmentation != null) {
            accumulatedEventSegm.putAll(filteredCustomViewSegmentation);
        }
        // add view segmentation too
        if (vd.viewSegmentation != null) {
            accumulatedEventSegm.putAll(vd.viewSegmentation);
        }

        long viewDurationSeconds = lastElapsedDurationSeconds;
        Map<String, Object> segments = CreateViewEventSegmentation(vd, false, false, accumulatedEventSegm);
        eventProvider.recordEventInternal(VIEW_EVENT_KEY, segments, 1, 0, viewDurationSeconds, null, vd.viewID);
    }

    void pauseViewWithIDInternal(String viewID, boolean pausedAutomatically) {
        if (viewID == null || viewID.isEmpty()) {
            L.e("[ModuleViews] pauseViewWithIDInternal, Trying to record view with null or empty view ID, ignoring request");
            return;
        }

        if (!viewDataMap.containsKey(viewID)) {
            L.w("[ModuleViews] pauseViewWithIDInternal, there is no view with the provided view id to close");
            return;
        }

        ViewData vd = viewDataMap.get(viewID);
        if (vd == null) {
            L.e("[ModuleViews] pauseViewWithIDInternal, view id:[" + viewID + "] has a 'null' value. This should not be happening, auto paused:[" + pausedAutomatically + "]");
            return;
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }

        L.d("[ModuleViews] pauseViewWithIDInternal, pausing view for ID:[" + viewID + "], name:[" + vd.viewName + "]");

        if (vd.viewStartTimeSeconds == 0) {
            L.w("[ModuleViews] pauseViewWithIDInternal, pausing a view that is already paused. ID:[" + viewID + "], name:[" + vd.viewName + "]");
            return;
        }

        vd.isAutoPaused = pausedAutomatically;

        recordViewEndEvent(vd, null, "pauseViewWithIDInternal");

        vd.viewStartTimeSeconds = 0;
    }

    void resumeViewWithIDInternal(String viewID) {
        if (viewID == null || viewID.isEmpty()) {
            L.e("[ModuleViews] resumeViewWithIDInternal, Trying to record view with null or empty view ID, ignoring request");
            return;
        }

        if (!viewDataMap.containsKey(viewID)) {
            L.w("[ModuleViews] resumeViewWithIDInternal, there is no view with the provided view id to close");
            return;
        }

        ViewData vd = viewDataMap.get(viewID);
        if (vd == null) {
            L.e("[ModuleViews] resumeViewWithIDInternal, view id:[" + viewID + "] has a 'null' value. This should not be happening");
            return;
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }

        L.d("[ModuleViews] resumeViewWithIDInternal, resuming view for ID:[" + viewID + "], name:[" + vd.viewName + "]");

        if (vd.viewStartTimeSeconds > 0) {
            L.w("[ModuleViews] resumeViewWithIDInternal, resuming a view that is already running. ID:[" + viewID + "], name:[" + vd.viewName + "]");
            return;
        }

        vd.viewStartTimeSeconds = UtilsTime.currentTimestampSeconds();
        vd.isAutoPaused = false;
    }

    public void addSegmentationToViewWithIDInternal(@Nullable String viewID, @Nullable Map<String, Object> viewSegmentation) {
        if (viewID == null || viewSegmentation == null || viewID.isEmpty() || viewSegmentation.isEmpty()) {
            L.e("[Views] addSegmentationToViewWithID, null or empty parameters provided");
            return;
        }

        if (!viewDataMap.containsKey(viewID)) {
            L.w("[ModuleViews] addSegmentationToViewWithID, there is no view with the provided view id");
            return;
        }

        ViewData vd = viewDataMap.get(viewID);
        if (vd == null) {
            L.e("[ModuleViews] addSegmentationToViewWithID, view id:[" + viewID + "] has a 'null' view data. This should not be happening");
            return;
        }

        Utils.truncateSegmentationValues(viewSegmentation, _cly.config_.sdkInternalLimits.maxSegmentationValues, "[ModuleViews] addSegmentationToViewWithID", L);
        Utils.removeReservedKeysFromSegmentation(viewSegmentation, reservedSegmentationKeysViews, "[ModuleViews] addSegmentationToViewWithID, ", L);

        if (vd.viewSegmentation == null) {
            vd.viewSegmentation = new HashMap<>(viewSegmentation);
        } else {
            vd.viewSegmentation.putAll(viewSegmentation);
        }
    }

    public void addSegmentationToViewWithNameInternal(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
        String viewID = null;

        for (Map.Entry<String, ViewData> entry : viewDataMap.entrySet()) {
            ViewData vd = entry.getValue();
            if (vd != null && viewName != null && viewName.equals(vd.viewName)) {
                viewID = entry.getKey();
            }
        }

        if (viewID == null) {
            L.e("[ModuleViews] addSegmentationToViewWithName, No view entry found with the provided name :[" + viewName + "]");
            return;
        }

        L.i("[ModuleViews] Will add segmentation for view: [" + viewName + "] with ID:[" + viewID + "]");

        addSegmentationToViewWithIDInternal(viewID, viewSegmentation);
    }

    void stopAllViewsInternal(Map<String, Object> viewSegmentation) {
        L.d("[ModuleViews] stopAllViewsInternal");

        autoCloseRequiredViews(true, viewSegmentation);
    }

    void updateOrientation(int newOrientation) {
        L.d("[ModuleViews] Calling [updateOrientation], new orientation:[" + newOrientation + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.users)) {
            //we don't have consent, just leave
            return;
        }

        if (currentOrientation != newOrientation) {
            currentOrientation = newOrientation;

            Map<String, Object> segm = new HashMap<>();

            if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                segm.put("mode", "portrait");
            } else {
                segm.put("mode", "landscape");
            }

            eventProvider.recordEventInternal(ORIENTATION_EVENT_KEY, segm, 1, 0, 0, null, null);
        }
    }

    void pauseRunningViewsAndSend() {
        L.d("[ModuleViews] pauseRunningViewsAndSend, going to the background and pausing");
        for (Map.Entry<String, ViewData> entry : viewDataMap.entrySet()) {
            ViewData vd = entry.getValue();

            if (vd.viewStartTimeSeconds > 0) {
                //if the view is running
                pauseViewWithIDInternal(vd.viewID, true);
            }
        }
    }

    void resumeAutoPausedViews() {
        L.d("[ModuleViews] resumeAutoPausedViews, going to the foreground and resuming");
        for (Map.Entry<String, ViewData> entry : viewDataMap.entrySet()) {
            ViewData vd = entry.getValue();

            if (vd.isAutoPaused) {
                //if the view was automatically paused, resume it
                resumeViewWithIDInternal(vd.viewID);
            }
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
    void onActivityStopped(int updatedActivityCount) {
        if (autoViewTracker) {
            //main purpose of this is handling transitions when the app is getting closed/minimised
            //for cases when going from one view to another we would report the duration there
            if (updatedActivityCount <= 0) {
                //try to close the last open view with the current view ID
                stopViewWithIDInternal(currentViewID, null);
            }
        }

        if (updatedActivityCount <= 0) {
            //if we go to the background, pause all running views
            pauseRunningViewsAndSend();
        }
    }

    @Override
    void onActivityStarted(Activity activity, int updatedActivityCount) {
        //automatic view tracking
        if (autoViewTracker) {
            if (!isActivityInExceptionList(activity)) {
                String usedActivityName = "NULL ACTIVITY";

                if (activity != null) {
                    if (automaticTrackingShouldUseShortName) {
                        usedActivityName = activity.getClass().getSimpleName();
                    } else {
                        usedActivityName = activity.getClass().getName();
                    }
                }

                startViewInternal(usedActivityName, automaticViewSegmentation, true);
            } else {
                L.d("[ModuleViews] [onStart] Ignoring activity because it's in the exception list");
            }
        }

        //orientation tracking
        if (trackOrientationChanges) {
            Integer orient = getOrientationFromActivity(activity);
            if (orient != null) {
                updateOrientation(orient);
            }
        }

        if (updatedActivityCount == 1) {
            //if we go to the background, pause all running views
            resumeAutoPausedViews();
        }
    }

    /**
     * Needed for mocking test result
     *
     * @param conf
     * @return
     */
    Integer getOrientationFromConfiguration(@Nullable Configuration conf) {
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

    public class Views {
        /**
         * Check state of automatic view tracking
         *
         * @return boolean - true if enabled, false if disabled
         * @deprecated this call will be removed. There is no replacement
         */
        public boolean isAutomaticViewTrackingEnabled() {
            synchronized (_cly) {
                L.i("[Views] Calling isAutomaticViewTrackingEnabled");

                return autoViewTracker;
            }
        }

        /**
         * Record a view manually, without automatic tracking
         * or tracks a view that is not automatically tracked
         * like a fragment, Message box or a transparent Activity
         *
         * @param viewName String - name of the view
         * @return Returns link to Countly for call chaining
         * @deprecated Use "Countly.sharedInstance().views().startAutoStoppedView(...)" in place of this
         */
        public Countly recordView(@Nullable String viewName) {
            synchronized (_cly) {
                // call the general function that has two parameters
                return recordView(viewName, null);
            }
        }

        /**
         * Record a view manually, without automatic tracking
         * or tracks a view that is not automatically tracked
         * like a fragment, Message box or a transparent Activity
         * with segmentation. (This is the main function that is used)
         *
         * @param viewName String - name of the view
         * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
         * @deprecated Use "Countly.sharedInstance().views().startAutoStoppedView(...)" in place of this
         */
        public Countly recordView(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling recordView [" + viewName + "]");

                if (autoViewTracker) {
                    L.e("[Views] recordView, manual view call will be ignored since automatic tracking is enabled.");
                    return _cly;
                }

                startViewInternal(viewName, viewSegmentation, true);

                return _cly;
            }
        }

        /**
         * Record a view manually, without automatic tracking
         * or tracks a view that is not automatically tracked
         * like a fragment, Message box or a transparent Activity
         *
         * @param viewName String - name of the view
         * @return Returns View ID
         */
        public String startAutoStoppedView(@Nullable String viewName) {
            synchronized (_cly) {
                // call the general function that has two parameters
                return startAutoStoppedView(viewName, null);
            }
        }

        /**
         * Record a view manually, without automatic tracking
         * or tracks a view that is not automatically tracked
         * like a fragment, Message box or a transparent Activity
         * with segmentation. (This is the main function that is used)
         *
         * @param viewName String - name of the view
         * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
         * @return String - view ID
         */
        public String startAutoStoppedView(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling startAutoStoppedView [" + viewName + "]");

                if (autoViewTracker) {
                    L.e("[Views] startAutoStoppedView, manual view call will be ignored since automatic tracking is enabled.");
                    return null;
                }

                return startViewInternal(viewName, viewSegmentation, true);
            }
        }

        /**
         * Updates the segmentation of a view
         *
         * @param viewID String - View ID of the view
         * @param viewSegmentation Map<String, Object> - New segmentation to update the segmentation of a view in memory
         */
        public void addSegmentationToViewWithID(@Nullable String viewID, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling addSegmentationToViewWithID for view ID: [" + viewID + "]");

                if (autoViewTracker) {
                    L.e("[Views] addSegmentationToViewWithID, manual view call will be ignored since automatic tracking is enabled.");
                    return;
                }

                addSegmentationToViewWithIDInternal(viewID, viewSegmentation);
            }
        }

        /**
         * Updates the segmentation of a view
         *
         * @param viewName String - Name of the view
         * @param viewSegmentation Map<String, Object> - New segmentation to update the segmentation of a view in memory
         */
        public void addSegmentationToViewWithName(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling addSegmentationToViewWithName for Name: [" + viewName + "]");

                if (autoViewTracker) {
                    L.e("[Views] addSegmentationToViewWithName, manual view call will be ignored since automatic tracking is enabled.");
                    return;
                }

                addSegmentationToViewWithNameInternal(viewName, viewSegmentation);
            }
        }

        /**
         * Starts a view which would not close automatically (For multi view tracking)
         *
         * @param viewName - String
         * @return String - View ID
         */
        public @Nullable String startView(@Nullable String viewName) {
            synchronized (_cly) {
                L.i("[Views] Calling startView vn[" + viewName + "]");

                if (autoViewTracker) {
                    L.e("[Views] startView, manual view call will be ignored since automatic tracking is enabled.");
                    return null;
                }

                return startViewInternal(viewName, null, false);
            }
        }

        /**
         * Starts a view which would not close automatically (For multi view tracking)
         *
         * @param viewName String - name of the view
         * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
         * @return String - View ID
         */
        public @Nullable String startView(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling startView vn[" + viewName + "] sg[" + (viewSegmentation == null ? viewSegmentation : viewSegmentation.size()) + "]");

                if (autoViewTracker) {
                    L.e("[Views] startView, manual view call will be ignored since automatic tracking is enabled.");
                    return null;
                }

                return startViewInternal(viewName, viewSegmentation, false);
            }
        }

        /**
         * Stops a view with the given name if it was open
         *
         * @param viewName String - view name
         */
        public void stopViewWithName(@Nullable String viewName) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithName vn[" + viewName + "]");

                stopViewWithNameInternal(viewName, null);
            }
        }

        /**
         * Stops a view with the given name if it was open
         *
         * @param viewName String - view name
         * @param viewSegmentation Map<String, Object> - view segmentation
         */
        public void stopViewWithName(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithName vn[" + viewName + "] sg[" + (viewSegmentation == null ? viewSegmentation : viewSegmentation.size()) + "]");

                stopViewWithNameInternal(viewName, viewSegmentation);
            }
        }

        /**
         * Stops a view with the given ID if it was open
         *
         * @param viewID String - view ID
         */
        public void stopViewWithID(@Nullable String viewID) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithID vi[" + viewID + "]");

                stopViewWithIDInternal(viewID, null);
            }
        }

        /**
         * Stops a view with the given ID if it was open
         *
         * @param viewID String - view ID
         * @param viewSegmentation Map<String, Object> - view segmentation
         */
        public void stopViewWithID(@Nullable String viewID, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithName vi[" + viewID + "] sg[" + (viewSegmentation == null ? viewSegmentation : viewSegmentation.size()) + "]");

                stopViewWithIDInternal(viewID, viewSegmentation);
            }
        }

        /**
         * Pauses a view with the given ID
         *
         * @param viewID String - view ID
         */
        public void pauseViewWithID(@Nullable String viewID) {
            synchronized (_cly) {
                L.i("[Views] Calling pauseViewWithID vi[" + viewID + "]");

                pauseViewWithIDInternal(viewID, false);
            }
        }

        /**
         * Resumes a view with the given ID
         *
         * @param viewID String - view ID
         */
        public void resumeViewWithID(@Nullable String viewID) {
            synchronized (_cly) {
                L.i("[Views] Calling resumeViewWithID vi[" + viewID + "]");

                resumeViewWithIDInternal(viewID);
            }
        }

        /**
         * Set a segmentation to be recorded with all views
         *
         * @param segmentation Map<String, Object> - global view segmentation
         */
        public void setGlobalViewSegmentation(@Nullable Map<String, Object> segmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling setGlobalViewSegmentation sg[" + (segmentation == null ? segmentation : segmentation.size()) + "]");

                setGlobalViewSegmentationInternal(segmentation);
            }
        }

        /**
         * Updates the global segmentation for views
         *
         * @param segmentation Map<String, Object> - global view segmentation
         */
        public void updateGlobalViewSegmentation(@Nullable Map<String, Object> segmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling updateGlobalViewSegmentation sg[" + (segmentation == null ? segmentation : segmentation.size()) + "]");

                if (segmentation == null) {
                    L.w("[View] When updating segmentation values, they can't be 'null'.");
                    return;
                }

                updateGlobalViewSegmentationInternal(segmentation);
            }
        }

        /**
         * Stops all views and records a segmentation if set
         *
         * @param viewSegmentation Map<String, Object> - view segmentation
         */
        public void stopAllViews(@Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling stopAllViews sg[" + (viewSegmentation == null ? viewSegmentation : viewSegmentation.size()) + "]");

                stopAllViewsInternal(viewSegmentation);
            }
        }
    }
}
