package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ModuleViews extends ModuleBase {
    private String lastViewID = null;
    private boolean firstView = true;

    boolean autoViewTracker = false;
    boolean automaticTrackingShouldUseShortName = false;

    //track orientation changes
    boolean trackOrientationChanges = false;
    int currentOrientation = -1;
    final static String ORIENTATION_EVENT_KEY = "[CLY]_orientation";

    final static String VIEW_EVENT_KEY = "[CLY]_view";

    Class[] autoTrackingActivityExceptions = null;//excluded activities from automatic view tracking

    Map<String, Object> automaticViewSegmentation = new HashMap<>();//automatic view segmentation

    Map<String, ViewData> viewDataMap = new HashMap<>(); // map viewIDs to its viewData
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

        if (config.enableViewTracking) {
            L.d("[ModuleViews] Enabling automatic view tracking");
            autoViewTracker = config.enableViewTracking;
        }

        if (config.autoTrackingUseShortName) {
            L.d("[ModuleViews] Enabling automatic view tracking short names");
            automaticTrackingShouldUseShortName = config.autoTrackingUseShortName;
        }

        setAutomaticViewSegmentationInternal(config.automaticViewSegmentation);
        autoTrackingActivityExceptions = config.autoTrackingExceptions;
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
        if (lastViewID == null || !viewDataMap.containsKey(lastViewID)) {
            L.w("[ModuleViews] reportViewDuration, view id is null or not inside of viewDataMap");
            return;
        }

        ViewData vd = viewDataMap.get(lastViewID);
        if (vd == null) {
            L.w("[ModuleViews] reportViewDuration, view id:[" + lastViewID + "] has a null value");
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
        //if the lastViewStart is equal to 0, the duration would be set to the current timestamp
        //and therefore will be ignored
        if (vd.viewName != null && vd.viewStartTime > 0) {
            Map<String, Object> segments = CreateViewEventSegmentation(vd, false, false, true, null);
            eventProvider.recordEventInternal(VIEW_EVENT_KEY, segments, 1, 0, 0, null);
            lastViewID = null;
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

    Map<String, Object> CreateViewEventSegmentation(@NonNull ViewData vd, boolean firstView, boolean visit, boolean duration, Map<String, Object> customViewSegmentation) {
        Map<String, Object> viewSegmentation = new HashMap<>();
        if (customViewSegmentation != null) {
            viewSegmentation.putAll(customViewSegmentation);
        }

        viewSegmentation.put("name", vd.viewName);
        if (visit) {
            viewSegmentation.put("visit", "1");
        }
        if (duration) {
            viewSegmentation.put("dur", String.valueOf(UtilsTime.currentTimestampSeconds() - vd.viewStartTime));
        }
        if (firstView) {
            viewSegmentation.put("start", "1");
        }
        viewSegmentation.put("segment", "Android");
        viewSegmentation.put("_idv", lastViewID);

        return viewSegmentation;
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
    synchronized Countly recordViewInternal(String viewName, Map<String, Object> customViewSegmentation) {
        if (!_cly.isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before recordView");
            return _cly;
        }

        if (viewName == null || viewName.isEmpty()) {
            L.e("[ModuleViews] Trying to record view with null or empty view name, ignoring request");
            return _cly;
        }

        // if segmentation is null this just returns so no null check necessary
        Utils.truncateSegmentationValues(customViewSegmentation, _cly.config_.maxSegmentationValues, "[ModuleViews] recordViewInternal", L);

        if (L.logEnabled()) {
            int segmCount = 0;
            if (customViewSegmentation != null) {
                segmCount = customViewSegmentation.size();
            }
            L.d("[ModuleViews] Recording view with name: [" + viewName + "], previous view ID:[" + lastViewID + "] custom view segment count:[" + segmCount + "], first:[" + firstView + "]");
        }

        reportViewDuration();

        ViewData currentViewData = new ViewData();
        currentViewData.viewID = safeIDGenerator.GenerateValue();
        currentViewData.viewName = viewName;
        currentViewData.viewStartTime = UtilsTime.currentTimestampSeconds();

        viewDataMap.put(currentViewData.viewID, currentViewData);
        lastViewID = currentViewData.viewID;

        Map<String, Object> viewSegmentation = CreateViewEventSegmentation(currentViewData, firstView, true, false, customViewSegmentation);

        if (firstView) {
            L.d("[ModuleViews] Recording view as the first one in the session. [" + viewName + "]");
            firstView = false;
        }

        eventProvider.recordEventInternal(VIEW_EVENT_KEY, viewSegmentation, 1, 0, 0, null);

        return _cly;
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

            eventProvider.recordEventInternal(ORIENTATION_EVENT_KEY, segm, 1, 0, 0, null);
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
    void onActivityStopped() {
        if (autoViewTracker) {
            //report current view duration
            reportViewDuration();
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

                recordViewInternal(usedActivityName, automaticViewSegmentation);
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
         */
        public Countly recordView(String viewName) {
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
         */
        public Countly recordView(String viewName, Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                L.i("[Views] Calling recordView [" + viewName + "]");

                return recordViewInternal(viewName, viewSegmentation);
            }
        }
    }
}
