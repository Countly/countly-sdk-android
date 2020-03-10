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

    protected Map<String, Object> automaticViewSegmentation = new HashMap<>();//automatic view segmentation

    //track orientation changes
    boolean trackOrientationChanges = false;
    int currentOrientation = -1;
    final static String ORIENTATION_EVENT_KEY = "[CLY]_orientation";

    //interface for SDK users
    final Views viewsInterface;
    public ModuleViews(Countly cly, CountlyConfig config){
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleViews] Initialising");
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

        automaticViewSegmentation.clear();

        if(segmentation != null){
            if(Utils.removeUnsupportedDataTypes(segmentation)){
                //found a unsupported type, print warning

                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "You have provided a unsupported type for automatic View Segmentation");
                }
            }

            Utils.removeKeysFromMap(segmentation, ModuleEvents.reservedSegmentationKeys);

            automaticViewSegmentation.putAll(segmentation);
        }
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
            HashMap<String, Object> segments = new HashMap<>();

            segments.put("name", lastView);
            segments.put("dur", String.valueOf(UtilsTime.currentTimestampSeconds() - lastViewStart));
            segments.put("segment", "Android");
            _cly.moduleEvents.recordEventInternal(VIEW_EVENT_KEY, segments, 1, 0, 0, null, true);
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
     * @param customViewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
     * @return Returns link to Countly for call chaining
     */
    synchronized Countly recordViewInternal(String viewName, Map<String, Object> customViewSegmentation) {
        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
        }

        if (_cly.isLoggingEnabled()) {
            int segmCount = 0;
            if (customViewSegmentation != null) {
                segmCount = customViewSegmentation.size();
            }
            Log.d(Countly.TAG, "Recording view with name: [" + viewName + "], previous view:[" + lastView + "] custom view segment count:[" + segmCount + "]");
        }

        reportViewDuration();
        lastView = viewName;
        lastViewStart = UtilsTime.currentTimestampSeconds();

        Map<String, Object> viewSegmentation = new HashMap<>();
        if(customViewSegmentation != null){
            Utils.removeUnsupportedDataTypes(customViewSegmentation);
            Utils.removeKeysFromMap(customViewSegmentation, ModuleEvents.reservedSegmentationKeys);
            viewSegmentation.putAll(customViewSegmentation);
        }

        viewSegmentation.put("name", viewName);
        viewSegmentation.put("visit", "1");
        viewSegmentation.put("segment", "Android");
        if(firstView) {
            firstView = false;
            viewSegmentation.put("start", "1");
        }

        _cly.moduleEvents.recordEventInternal(VIEW_EVENT_KEY, viewSegmentation, 1, 0, 0, null, true);

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
            Integer orient = getOrientationFromConfiguration(newConfig);
            if(orient != null){
                updateOrientation(orient);
            }
        }
    }

    @Override
    void onActivityStopped() {
        //report current view duration
        reportViewDuration();
    }

    @Override
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
            Integer orient = getOrientationFromActivity(activity);
            if(orient != null) {
                updateOrientation(orient);
            }
        }
    }

    /**
     * Needed for mocking test result
     * @param conf
     * @return
     */
    Integer getOrientationFromConfiguration(Configuration conf){
        if(conf == null){
            return null;
        }

        return conf.orientation;
    }

    /**
     * Needed for mocking test result
     * @param act
     * @return
     */
    Integer getOrientationFromActivity(Activity act){
        if(act == null) {
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
        public synchronized boolean isAutomaticViewTrackingEnabled(){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Views] Calling isAutomaticViewTrackingEnabled");
            }

            return _cly.autoViewTracker;
        }

        /**
         *  Record a view manually, without automatic tracking
         * or track view that is not automatically tracked
         * like fragment, Message box or transparent Activity
         * @param viewName String - name of the view
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordView(String viewName) {
            return recordView(viewName, null);
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
