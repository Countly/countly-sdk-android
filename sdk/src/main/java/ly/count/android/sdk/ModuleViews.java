package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

class ModuleViews extends ModuleBase{
    private String lastView = null;
    private int lastViewStart = 0;
    private boolean firstView = true;
    final static String VIEW_EVENT_KEY = "[CLY]_view";

    Class[] autoTrackingActivityExceptions = null;//excluded activities from automatic view tracking

    protected Map<String, Object> automaticViewSegmentation = null;//automatic view segmentation

    //track orientation changes
    boolean trackOrientationChanges = false;
    int currentOrientation = -1;
    final static String ORIENTATION_EVENT_KEY = "[CLY]_orientation";

    //interface for SDK users
    final Views viewsInterface;
    public ModuleViews(Countly cly, CountlyConfig config){
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleEvents] Initialising");
        }

        _cly.setViewTracking(config.enableViewTracking);
        _cly.setAutoTrackingUseShortName(config.autoTrackingUseShortName);

        setAutomaticViewSegmentationInternal(config.automaticViewSegmentation);
        autoTrackingActivityExceptions = config.autoTrackingExceptions;
        trackOrientationChanges = config.trackOrientationChange;

        viewsInterface = new Views();
    }

    void setAutomaticViewSegmentationInternal(Map<String, Object> segmentation){
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling setAutomaticViewSegmentationInternal");
        }

        if(segmentation != null){
            if(!ModuleEvents.checkSegmentationTypes(segmentation)){
                //found a unsupported type, throw exception

                throw new IllegalStateException("Provided a unsupported type for automatic View Segmentation");
            }
        }

        automaticViewSegmentation = segmentation;
    }

    /**
     * Reports duration of last view
     */
    void reportViewDuration() {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "View [" + lastView + "] is getting closed, reporting duration: [" + (UtilsTime.currentTimestampSeconds() - lastViewStart) + "], current timestamp: [" + UtilsTime.currentTimestampSeconds() + "], last views start: [" + lastViewStart + "]");
        }

        if (lastView != null && lastViewStart <= 0) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "Last view start value is not normal: [" + lastViewStart + "]");
            }
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.views)) {
            return;
        }

        //only record view if the view name is not null and if it has a reasonable duration
        //if the lastViewStart is equal to 0, the duration would be set to the current timestamp
        //and therefore will be ignored
        if (lastView != null && lastViewStart > 0) {
            HashMap<String, String> segments = new HashMap<>();
            segments.put("name", lastView);
            segments.put("dur", String.valueOf(UtilsTime.currentTimestampSeconds() - lastViewStart));
            segments.put("segment", "Android");
            _cly.recordEvent(VIEW_EVENT_KEY, segments, 1);
            lastView = null;
            lastViewStart = 0;
        }
    }

    boolean isActivityInExceptionList(Activity act){
        if (autoTrackingActivityExceptions == null){
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
     *  Record a view manually, without automatic tracking
     * or track view that is not automatically tracked
     * like fragment, Message box or transparent Activity
     * @param viewName String - name of the view
     * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
     * @return Returns link to Countly for call chaining
     */
    synchronized Countly recordViewInternal(String viewName, Map<String, Object> viewSegmentation) {
        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
        }

        if (_cly.isLoggingEnabled()) {
            int segmCount = 0;
            if (viewSegmentation != null) {
                segmCount = viewSegmentation.size();
            }
            Log.d(Countly.TAG, "Recording view with name: [" + viewName + "], previous view:[" + lastView + "] view segment count:[" + segmCount + "]");
        }

        reportViewDuration();
        lastView = viewName;
        lastViewStart = UtilsTime.currentTimestampSeconds();
        HashMap<String, String> segmentsString = new HashMap<>();
        segmentsString.put("name", viewName);
        segmentsString.put("visit", "1");
        segmentsString.put("segment", "Android");
        if(firstView) {
            firstView = false;
            segmentsString.put("start", "1");
        }

        Map<String, Integer> segmentsInt = null;
        Map<String, Double> segmentsDouble = null;

        if(viewSegmentation != null){
            segmentsInt = new HashMap<>();
            segmentsDouble = new HashMap<>();

            ModuleEvents.fillInSegmentation(viewSegmentation, segmentsString, segmentsInt, segmentsDouble, null);
        }

        _cly.recordEvent(VIEW_EVENT_KEY, segmentsString, segmentsInt, segmentsDouble, 1, 0, 0);
        return _cly;
    }

    void updateOrientation(int newOrientation){
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [updateOrientation], new orientation:[" + newOrientation + "]");
        }

        if(!_cly.getConsent(Countly.CountlyFeatureNames.events)){
            //we don't have consent, just leave
            return;
        }

        if(currentOrientation != newOrientation){
            currentOrientation = newOrientation;

            Map<String, String> segm = new HashMap<>();

            if(currentOrientation == Configuration.ORIENTATION_PORTRAIT){
                segm.put("mode", "portrait");
            } else {
                segm.put("mode", "landscape");
            }

            _cly.recordEvent(ORIENTATION_EVENT_KEY, segm, 1);
        }
    }

    @Override
    void onConfigurationChanged(Configuration newConfig){
        if(trackOrientationChanges){
            updateOrientation(newConfig.orientation);
        }
    }

    @Override
    void onActivityStopped() {
        //report current view duration
        reportViewDuration();
    }

    void onActivityStarted(Activity activity) {
        //automatic view tracking
        if (_cly.autoViewTracker) {
            if (!isActivityInExceptionList(activity)) {
                String usedActivityName = "NULL ACTIVITY";

                if (activity != null) {
                    if (_cly.automaticTrackingShouldUseShortName) {
                        usedActivityName = activity.getClass().getSimpleName();
                    } else {
                        usedActivityName = activity.getClass().getName();
                    }
                }

                _cly.recordView(usedActivityName, automaticViewSegmentation);
            } else {
                if (_cly.isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[onStart] Ignoring activity because it's in the exception list");
                }
            }
        }

        //orientation tracking
        if (trackOrientationChanges) {
            Resources resources = activity.getResources();
            if (resources != null) {
                updateOrientation(resources.getConfiguration().orientation);
            }
        }
    }

    @Override
    void halt(){
        if(automaticViewSegmentation != null) {
            automaticViewSegmentation.clear();
            automaticViewSegmentation = null;
        }
        autoTrackingActivityExceptions = null;
    }

    public class Views {
        /**
         * Set custom segmentation which will be added to all automatically recorded views
         * @param segmentation
         * @return
         */
        public Countly setAutomaticViewSegmentation(Map<String, Object> segmentation){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Views] Calling setAutomaticViewSegmentation");
            }

            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before setAutomaticViewSegmentation");
            }

            setAutomaticViewSegmentationInternal(segmentation);

            return _cly;
        }

        /**
         * Check state of automatic view tracking
         * @return boolean - true if enabled, false if disabled
         */
        public synchronized boolean isViewTrackingEnabled(){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Views] Calling isViewTrackingEnabled");
            }

            return _cly.autoViewTracker;
        }

        /**
         * Record a view manually, without automatic tracking
         * or track view that is not automatically tracked
         * like fragment, Message box or transparent Activity
         * @param viewName String - name of the view
         * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordView(String viewName, Map<String, Object> viewSegmentation) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
            }

            return recordViewInternal(viewName, viewSegmentation);
        }
    }
}
