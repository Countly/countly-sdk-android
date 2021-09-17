package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.HashMap;
import java.util.Map;

public class ModuleViews extends ModuleBase {
    private String lastView = null;
    private int lastViewStart = 0;
    private boolean firstView = true;
    final static String VIEW_EVENT_KEY = "[CLY]_view";

    Class[] autoTrackingActivityExceptions = null;//excluded activities from automatic view tracking

    Map<String, Object> automaticViewSegmentation = new HashMap<>();//automatic view segmentation

    boolean autoViewTracker = false;
    boolean automaticTrackingShouldUseShortName = false;//flag for using short names

    //track orientation changes
    boolean trackOrientationChanges = false;
    int currentOrientation = -1;
    final static String ORIENTATION_EVENT_KEY = "[CLY]_orientation";

    //interface for SDK users
    final Views viewsInterface;

    ModuleViews(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleViews] Initialising");

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

    void setAutomaticViewSegmentationInternal(Map<String, Object> segmentation) {
        L.d("[ModuleViews] Calling setAutomaticViewSegmentationInternal");

        automaticViewSegmentation.clear();

        if (segmentation != null) {
            if (Utils.removeUnsupportedDataTypes(segmentation)) {
                //found a unsupported type, print warning

                L.w("[ModuleViews] You have provided a unsupported type for automatic View Segmentation");
            }

            automaticViewSegmentation.putAll(segmentation);
        }
    }

    /**
     * Reports duration of last view
     */
    void reportViewDuration() {
        L.d("[ModuleViews] View [" + lastView + "] is getting closed, reporting duration: [" + (UtilsTime.currentTimestampSeconds() - lastViewStart) + "] ms, current timestamp: [" + UtilsTime.currentTimestampSeconds() + "], last views start: [" + lastViewStart + "]");

        if (lastView != null && lastViewStart <= 0) {
            L.e("[ModuleViews] Last view start value is not normal: [" + lastViewStart + "]");
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }

        //only record view if the view name is not null and if it has a reasonable duration
        //if the lastViewStart is equal to 0, the duration would be set to the current timestamp
        //and therefore will be ignored
        if (lastView != null && lastViewStart > 0) {
            L.d("[ModuleViews] Recording view duration: [" + lastView + "]");
            HashMap<String, Object> segments = new HashMap<>();

            segments.put("name", lastView);
            segments.put("dur", String.valueOf(UtilsTime.currentTimestampSeconds() - lastViewStart));
            segments.put("segment", "Android");
            eventProvider.recordEventInternal(VIEW_EVENT_KEY, segments, 1, 0, 0, null);
            lastView = null;
            lastViewStart = 0;
        }
    }

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
     * Record a view manually, without automatic tracking
     * or track view that is not automatically tracked
     * like fragment, Message box or transparent Activity
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

        if (L.logEnabled()) {
            int segmCount = 0;
            if (customViewSegmentation != null) {
                segmCount = customViewSegmentation.size();
            }
            L.d("[ModuleViews] Recording view with name: [" + viewName + "], previous view:[" + lastView + "] custom view segment count:[" + segmCount + "]");
        }

        reportViewDuration();
        lastView = viewName;
        lastViewStart = UtilsTime.currentTimestampSeconds();

        Map<String, Object> viewSegmentation = new HashMap<>();
        if (customViewSegmentation != null) {
            viewSegmentation.putAll(customViewSegmentation);
        }

        viewSegmentation.put("name", viewName);
        viewSegmentation.put("visit", "1");
        viewSegmentation.put("segment", "Android");
        if (firstView) {
            firstView = false;
            viewSegmentation.put("start", "1");
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
         * or track view that is not automatically tracked
         * like fragment, Message box or transparent Activity
         *
         * @param viewName String - name of the view
         * @return Returns link to Countly for call chaining
         */
        public Countly recordView(String viewName) {
            synchronized (_cly) {
                return recordView(viewName, null);
            }
        }

        /**
         * Record a view manually, without automatic tracking
         * or track view that is not automatically tracked
         * like fragment, Message box or transparent Activity
         *
         * @param viewName String - name of the view
         * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
         * @return Returns link to Countly for call chaining
         */
        public Countly recordView(String viewName, Map<String, Object> viewSegmentation) {
            synchronized (_cly) {
                if (!_cly.isInitialized()) {
                    L.e("Countly.sharedInstance().init must be called before recordView");
                    return _cly;
                }

                L.i("[Views] Calling recordView [" + viewName + "]");

                return recordViewInternal(viewName, viewSegmentation);
            }
        }
    }
}
