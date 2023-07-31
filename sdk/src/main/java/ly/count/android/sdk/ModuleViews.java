package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
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

    public @NonNull String getCurrentViewId() {
        return currentViewID == null ? "" : currentViewID;
    }

    public @NonNull String getPreviousViewId() {
        return previousViewID == null ? "" : previousViewID;
    }

    static class ViewData {
        String viewID;
        long viewStartTime;
        String viewName;
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

        setAutomaticViewSegmentationInternal(config.globalViewSegmentation);
        autoTrackingActivityExceptions = config.automaticViewTrackingExceptions;
        trackOrientationChanges = config.trackOrientationChange;

        viewsInterface = new Views();
    }

    /**
     * Checks the provided Segmentation by the user. Sanitizes it
     * and transfers the data into an internal Segmentation Object.
     */
    void setAutomaticViewSegmentationInternal(Map<String, Object> segmentation) {
        L.d("[ModuleViews] Calling setAutomaticViewSegmentationInternal");

        automaticViewSegmentation.clear();

        if (segmentation != null) {
            if (Utils.removeUnsupportedDataTypes(segmentation)) {
                //found an unsupported type, print warning
                L.w("[ModuleViews] You have provided an unsupported data type in your View Segmentation. Removing the unsupported values.");
            }

            automaticViewSegmentation.putAll(segmentation);
        }
    }

    /**
     * Records last view with duration (ignores first view)
     */
    void reportViewDuration() {
        if (currentViewID == null || !viewDataMap.containsKey(currentViewID)) {
            L.w("[ModuleViews] reportViewDuration, view id is null or not inside of viewDataMap");
            return;
        }

        ViewData vd = viewDataMap.get(currentViewID);
        if (vd == null) {
            L.w("[ModuleViews] reportViewDuration, view id:[" + currentViewID + "] has a null value");
            return;
        }

        L.d("[ModuleViews] View [" + vd.viewName + "], id:[" + vd.viewID + "] is getting closed, reporting duration: [" + (UtilsTime.currentTimestampSeconds() - vd.viewStartTime) + "] ms, current timestamp: [" + UtilsTime.currentTimestampSeconds() + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }

        //we sanity check the time component and print error in case of problem
        if (vd.viewStartTime <= 0) {
            L.e("[ModuleViews] Last view start value is not normal: [" + vd.viewStartTime + "]");
        }

        //only record view if the view name is not null and if it has a reasonable duration
        //if the PreviousViewStart is equal to 0, the duration would be set to the current timestamp
        //and therefore will be ignored
        if (vd.viewName != null && vd.viewStartTime > 0) {
            long viewDurationSeconds = UtilsTime.currentTimestampSeconds() - vd.viewStartTime;
            Map<String, Object> segments = CreateViewEventSegmentation(vd, false, false, null);
            eventProvider.recordEventInternal(VIEW_EVENT_KEY, segments, 1, 0, viewDurationSeconds, null, vd.viewID);
            viewDataMap.remove(vd.viewID);
        }
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
            viewSegmentation.putAll(customViewSegmentation);
        }

        viewSegmentation.put("name", vd.viewName);
        if (visit) {
            viewSegmentation.put("visit", "1");
        }
        if (firstView) {
            viewSegmentation.put("start", "1");
        }
        viewSegmentation.put("segment", "Android");

        return viewSegmentation;
    }

    public void setGlobalViewSegmentationInternal(@Nullable Map<String, Object> segmentation) {

    }

    public void updateGlobalViewSegmentationInternal(@NonNull Map<String, Object> segmentation) {

    }

    @NonNull Countly recordViewInternalLegacy(String viewName, Map<String, Object> customViewSegmentation) {
        startViewInternal(viewName, customViewSegmentation);
        return _cly;
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
    @Nullable String startViewInternal(String viewName, Map<String, Object> customViewSegmentation) {
        if (!_cly.isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before startViewInternal");
            return null;
        }

        if (viewName == null || viewName.isEmpty()) {
            L.e("[ModuleViews] startViewInternal, Trying to record view with null or empty view name, ignoring request");
            return null;
        }

        // if segmentation is null this just returns so no null check necessary
        Utils.truncateSegmentationValues(customViewSegmentation, _cly.config_.maxSegmentationValues, "[ModuleViews] recordViewInternal", L);

        if (L.logEnabled()) {
            int segmCount = 0;
            if (customViewSegmentation != null) {
                segmCount = customViewSegmentation.size();
            }
            L.d("[ModuleViews] Recording view with name: [" + viewName + "], previous view ID:[" + currentViewID + "] custom view segment count:[" + segmCount + "], first:[" + firstView + "]");
        }

        if (viewDataMap.size() > 0) {
            //there is only a point in calling this if there are any open views
            reportViewDuration();
        } else {
            L.i("[ModuleViews] Skipping to call 'reportViewDuration' due to having no open views");
        }

        ViewData currentViewData = new ViewData();
        currentViewData.viewID = safeViewIDGenerator.GenerateValue();
        currentViewData.viewName = viewName;
        currentViewData.viewStartTime = UtilsTime.currentTimestampSeconds();

        viewDataMap.put(currentViewData.viewID, currentViewData);
        previousViewID = currentViewID;
        currentViewID = currentViewData.viewID;

        Map<String, Object> viewSegmentation = CreateViewEventSegmentation(currentViewData, firstView, true, customViewSegmentation);

        if (firstView) {
            L.d("[ModuleViews] Recording view as the first one in the session. [" + viewName + "]");
            firstView = false;
        }

        eventProvider.recordEventInternal(VIEW_EVENT_KEY, viewSegmentation, 1, 0, 0, null, currentViewData.viewID);

        return currentViewData.viewID;
    }

    void stopViewWithNameInternal(String viewName, Map<String, Object> customViewSegmentation) {

        String viewID = null;

        stopViewWithIDInternal(viewID, customViewSegmentation);
    }

    void stopViewWithIDInternal(String viewID, Map<String, Object> customViewSegmentation) {

    }

    void pauseViewWithIDInternal(String viewID) {

    }

    void resumeViewWithIDInternal(String viewID) {

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
                reportViewDuration();
            }
        }
    }

    @Override
    void onActivityStarted(Activity activity) {
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

                startViewInternal(usedActivityName, automaticViewSegmentation);
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
         * @deprecated Use "Countly.view().startView(...)" in place of this
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
         * @deprecated Use "Countly.view().startView(...)" in place of this
         */
        public Countly recordView(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling recordView [" + viewName + "]");

                if (autoViewTracker) {
                    L.e("[Views] recordView, manual view call will be ignored since automatic tracking is enabled.");
                    return _cly;
                }

                return recordViewInternalLegacy(viewName, viewSegmentation);
            }
        }

        public @Nullable String startView(@Nullable String viewName) {
            synchronized (_cly) {
                L.i("[Views] Calling startView vn[" + viewName + "]");

                if (autoViewTracker) {
                    L.e("[Views] startView, manual view call will be ignored since automatic tracking is enabled.");
                    return null;
                }

                return startViewInternal(viewName, null);
            }
        }

        public @Nullable String startView(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling startView vn[" + viewName + "] sg[" + (viewSegmentation == null ? viewSegmentation : viewSegmentation.size()) + "]");

                if (autoViewTracker) {
                    L.e("[Views] startView, manual view call will be ignored since automatic tracking is enabled.");
                    return null;
                }

                return startViewInternal(viewName, null);
            }
        }

        public void stopViewWithName(@Nullable String viewName) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithName vn[" + viewName + "]");

                stopViewWithIDInternal(viewName, null);
            }
        }

        public void stopViewWithName(@Nullable String viewName, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithName vn[" + viewName + "] sg[" + (viewSegmentation == null ? viewSegmentation : viewSegmentation.size()) + "]");

                stopViewWithIDInternal(viewName, null);
            }
        }

        public void stopViewWithID(@Nullable String viewID) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithID vi[" + viewID + "]");

                stopViewWithIDInternal(viewID, null);
            }
        }

        public void stopViewWithID(@Nullable String viewID, @Nullable Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling stopViewWithName vi[" + viewID + "] sg[" + (viewSegmentation == null ? viewSegmentation : viewSegmentation.size()) + "]");

                stopViewWithIDInternal(viewID, null);
            }
        }

        public void pauseViewWithID(@Nullable String viewID) {
            synchronized (_cly) {
                L.i("[Views] Calling pauseViewWithID vi[" + viewID + "]");

                pauseViewWithIDInternal(viewID);
            }
        }

        public void resumeViewWithID(@Nullable String viewID) {
            synchronized (_cly) {
                L.i("[Views] Calling resumeViewWithID vi[" + viewID + "]");

                pauseViewWithIDInternal(viewID);
            }
        }

        public void setGlobalViewSegmentation(@Nullable Map<String, Object> segmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling setGlobalViewSegmentation sg[" + (segmentation == null ? segmentation : segmentation.size()) + "]");

                setGlobalViewSegmentationInternal(segmentation);
            }
        }

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
    }
}
