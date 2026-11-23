package ly.count.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

public class ModuleContent extends ModuleBase {
    private final ImmediateRequestGenerator iRGenerator;
    Content contentInterface;
    CountlyTimer countlyTimer;
    private boolean shouldFetchContents = false;
    private boolean isCurrentlyInContentZone = false;
    private int zoneTimerInterval;
    private final boolean webViewEnabled;
    private final ContentCallback globalContentCallback;
    private int waitForDelay = 0;
    int CONTENT_START_DELAY_MS = 4000; // 4 seconds

    static final int TRIGGER_QUEUE_CHECK_MS = 500;
    static final int TRIGGER_QUEUE_CHECKS = 5;
    static final int TRIGGER_CONTENT_RETRY_MS = 1000;
    static final int TRIGGER_CONTENT_REQUESTS = 3;
    static final int TRIGGER_EARLY_LIMIT = 50;
    private final Handler triggerHandler = new Handler(Looper.getMainLooper());
    private volatile boolean contentZoneWanted = false;
    private volatile boolean contentZoneExited = false;
    private volatile boolean triggersHalted = false;
    private boolean triggerRoundRunning = false;
    private boolean triggerRoundPending = false;
    private int contentTriggerChain = 0;
    private volatile boolean behaviorSettingsSettled = false;
    private final List<ContentTriggers.Trigger> earlyTriggers = new ArrayList<>();

    //Weak, like CountlyActivityHolder: the clearing path (onActivityDestroyed) only runs when the
    //instance was initialised with an Application class - an instance without one (seeded through
    //onInitialActivitySeeded) would otherwise pin the seeding Activity for as long as this instance
    //lives, with the process-lifetime instance registry as the GC root.
    private WeakReference<Activity> currentActivity;
    ContentOverlayView contentOverlay;
    // Buffered content when no activity is available
    private Map<Integer, TransparentActivityConfig> pendingContentConfigs;

    //replaced, never refilled in place: the setter can run on any thread while an event reads it
    @NonNull private volatile Map<String, Object> globalContentSegmentation = Collections.emptyMap();

    //the server reads these off feedback widget and content events to count answers and attribute them
    static final String[] reservedGlobalContentSegmentationKeys = {
        "platform", "app_version", "widget_id", "closed", "shown", "started", "answered", "rating", "comment", "email", "contactMe", "campaign_id",
        "content_id", "element_id", "dismissed", "journey_id", "journey_definition_id", "journeyId", "journeyDefinitionId", "journeyInstanceId"
    };
    static final String reservedSurveyAnswerKeyPrefix = "answ-";

    private @Nullable Activity getCurrentActivity() {
        return currentActivity != null ? currentActivity.get() : null;
    }

    ModuleContent(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        //the resolved interval, which is what this module actually uses below - logging the config's value
        //would print a number the SDK is not honouring once the server behaviour settings override it
        L.v("[ModuleContent] Initialising, zoneTimerInterval: [" + cly.moduleConfiguration.currentVZoneTimerInterval + "], globalContentCallback: [" + config.content.globalContentCallback + "]");
        iRGenerator = config.immediateRequestGenerator;

        contentInterface = new Content();
        countlyTimer = new CountlyTimer();
        //resolved for this instance by ModuleConfiguration, not read off the shared config
        zoneTimerInterval = cly.moduleConfiguration.currentVZoneTimerInterval;
        webViewEnabled = config.webViewEnabled;
        globalContentCallback = config.content.globalContentCallback;
        if (!webViewEnabled) {
            L.i("[ModuleContent] WebView is disabled via configuration, content overlay will not be shown");
        }
    }

    /**
     * Stores a sanitized copy of the given segmentation without its reserved keys, or clears it when
     * 'null' or empty.
     */
    void setGlobalContentSegmentationInternal(@Nullable Map<String, Object> segmentation) {
        if (segmentation == null || segmentation.isEmpty()) {
            globalContentSegmentation = Collections.emptyMap();
            return;
        }

        //copied first: the helpers below modify the map in place, and this one is the developer's
        Map<String, Object> sanitized = new LinkedHashMap<>(segmentation);
        String tag = "[ModuleContent] setGlobalContentSegmentationInternal";
        UtilsInternalLimits.removeUnsupportedDataTypes(sanitized, L);
        UtilsInternalLimits.removeReservedKeysFromSegmentation(sanitized, reservedGlobalContentSegmentationKeys, tag + ",", L);
        Iterator<String> keys = sanitized.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(reservedSurveyAnswerKeyPrefix)) {
                L.w(tag + ", provided segmentation contains protected key [" + key + "]");
                healthTracker.recordLogCode("w700");
                keys.remove();
            }
        }
        //after the reserved keys are gone, so they do not use up the segmentation limit
        UtilsInternalLimits.applySdkInternalLimitsToSegmentation(sanitized, _cly.sdkInternalLimits_, L, tag);

        globalContentSegmentation = sanitized;
    }

    /**
     * Merges the instance's global content segmentation under an event's own segmentation. The event's
     * entries win and come last, so a content event trimmed to the segmentation limit loses global
     * entries first.
     *
     * @return a new merged map, or eventSegmentation itself when there is nothing to add
     */
    @NonNull static Map<String, Object> withGlobalContentSegmentation(@NonNull Countly cly, @NonNull Map<String, Object> eventSegmentation) {
        //read once into a local: a teardown on another thread nulls the module fields mid call
        ModuleContent contentModule = cly.moduleContent;
        if (contentModule == null) {
            return eventSegmentation;
        }

        Map<String, Object> globalSegmentation = contentModule.globalContentSegmentation;
        if (globalSegmentation.isEmpty()) {
            return eventSegmentation;
        }

        //putAll keeps a shared key at its global position, and truncateSegmentationValues trims from the front
        Map<String, Object> merged = new LinkedHashMap<>(globalSegmentation);
        merged.keySet().removeAll(eventSegmentation.keySet());
        merged.putAll(eventSegmentation);
        return merged;
    }

    /**
     * Applies a changed zone timer interval to a running content zone. The "ecz" setting decides only the entry at
     * init, so a later change of it neither enters nor exits the content zone.
     */
    @Override
    void onSdkConfigurationChanged(@NonNull CountlyConfig config) {
        //Reached on the main thread from the server-config response, so a teardown can have nulled
        //moduleConfiguration in between; keep the current value rather than crashing on a dying instance.
        ModuleConfiguration configurationModule = _cly.moduleConfiguration;
        if (configurationModule == null) {
            L.w("[ModuleContent] onSdkConfigurationChanged, the configuration module is gone, keeping the current zone timer interval");
            return;
        }
        int previousInterval = zoneTimerInterval;
        zoneTimerInterval = configurationModule.currentVZoneTimerInterval;
        if (zoneTimerInterval != previousInterval && shouldFetchContents && !isCurrentlyInContentZone) {
            L.d("[ModuleContent] onSdkConfigurationChanged, the zone timer interval changed to [" + zoneTimerInterval + "] seconds, restarting the zone timer");
            enterContentZoneInternal(null, 0, null);
        }
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        if (configProvider.getContentZoneEnabled()) {
            enterContentZoneInternal(null, 0, null);
        }
    }

    @Override
    void onInitialActivitySeeded(@NonNull Activity activity) {
        L.d("[ModuleContent] onInitialActivitySeeded, activity: [" + activity.getClass().getSimpleName() + "]");
        currentActivity = new WeakReference<>(activity);
        if (UtilsDevice.cutout == null) {
            UtilsDevice.getCutout(activity);
        }
    }

    @Override
    void onActivityStarted(Activity activity, int updatedActivityCount) {
        if (activity == null) {
            return;
        }

        if (UtilsDevice.cutout == null) {
            UtilsDevice.getCutout(activity);
        }

        currentActivity = new WeakReference<>(activity);

        // Move existing overlay to the new activity
        if (contentOverlay != null && !activity.isFinishing() && !activity.isDestroyed()) {
            try {
                contentOverlay.attachToActivity(activity);
            } catch (Exception ex) {
                L.w("[ModuleContent] onActivityStarted, failed to attach content overlay to activity", ex);
            }
        }

        // Show buffered content if we have a pending overlay
        if (pendingContentConfigs != null && !activity.isFinishing() && !activity.isDestroyed()) {
            shouldFetchContents = false;
            showContentOverlay(activity, pendingContentConfigs);
            pendingContentConfigs = null;
        }
    }

    @Override
    void onActivityStopped(int updatedActivityCount) {
        if (updatedActivityCount == 0 && contentOverlay != null) {
            // No activities visible — remove overlay from WindowManager to prevent WindowLeaked.
            // The overlay is kept alive and will be re-attached in onActivityStarted.
            L.d("[ModuleContent] onActivityStopped, no activities visible, detaching overlay from window");
            contentOverlay.detachFromWindow();
        }
    }

    @Override
    void onActivityDestroyed(@NonNull Activity activity) {
        // Identity check guards against clearing a newer activity when the destroy callback
        // for an older activity arrives after onActivityStarted of the next one (rotation race).
        if (getCurrentActivity() == activity) {
            currentActivity = null;
        }
        // The overlay itself is intentionally kept alive across activity transitions.
        // Its View context is the Application (not the constructing activity), so destroyed
        // activities are not retained through the overlay. See ContentOverlayView constructor.
    }

    void fetchContentsInternal(@NonNull String[] categories, @Nullable Runnable callbackOnFailure, @Nullable String contentId) {
        L.d("[ModuleContent] fetchContentsInternal, shouldFetchContents: [" + shouldFetchContents + "], categories: [" + Arrays.toString(categories) + "], contentId: [" + contentId + "]");

        DisplayMetrics displayMetrics = deviceInfo.mp.getDisplayMetrics(_cly.context_);
        String requestData = prepareContentFetchRequest(displayMetrics, categories, contentId);

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

        iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/o/sdk/content", cp, false, networkingIsEnabled, checkResponse -> {
            L.d("[ModuleContent] fetchContentsInternal, processing fetched contents, received response is :[" + checkResponse + "]");
            if (checkResponse == null) {
                if (callbackOnFailure != null) {
                    callbackOnFailure.run();
                }
                return;
            }

            try {
                if (validateResponse(checkResponse)) {
                    L.d("[ModuleContent] fetchContentsInternal, got new content data, showing it");

                    Map<Integer, TransparentActivityConfig> placementCoordinates = parseContent(checkResponse, displayMetrics);
                    if (placementCoordinates.isEmpty()) {
                        L.d("[ModuleContent] fetchContentsInternal, placement coordinates are empty, skipping");
                        if (callbackOnFailure != null) {
                            callbackOnFailure.run();
                        }
                        return;
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        shouldFetchContents = false; // disable fetching contents until the next time, this will disable the timer fetching
                        isCurrentlyInContentZone = true;

                        Activity heldActivity = getCurrentActivity();
                        if (heldActivity != null && !heldActivity.isFinishing()) {
                            showContentOverlay(heldActivity, placementCoordinates);
                        } else {
                            L.d("[ModuleContent] fetchContentsInternal, no active activity, buffering content");
                            pendingContentConfigs = placementCoordinates;
                        }
                    });
                } else {
                    L.w("[ModuleContent] fetchContentsInternal, response is not valid, skipping");
                    if (callbackOnFailure != null) {
                        callbackOnFailure.run();
                    }
                }
            } catch (Exception ex) {
                L.e("[ModuleContent] fetchContentsInternal, Encountered internal issue while trying to fetch contents, [" + ex + "]");
                if (callbackOnFailure != null) {
                    callbackOnFailure.run();
                }
            }
        }, L);
    }

    private void enterContentZoneInternal(@Nullable String[] categories, final int initialDelayMS, @Nullable Runnable callbackOnFailure) {
        if (!webViewEnabled) {
            L.d("[ModuleContent] enterContentZoneInternal, WebView is disabled via configuration, skipping");
            return;
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
            L.w("[ModuleContent] enterContentZoneInternal, Consent is not granted, skipping");
            return;
        }

        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.w("[ModuleContent] enterContentZoneInternal, temporary device ID is enabled, skipping");
            return;
        }

        if (isCurrentlyInContentZone) {
            L.w("[ModuleContent] enterContentZoneInternal, already in content zone, skipping");
            return;
        }

        shouldFetchContents = true;
        contentZoneWanted = true;
        contentZoneExited = false;

        String[] validCategories;

        if (categories == null) {
            L.w("[ModuleContent] enterContentZoneInternal, categories is null, providing empty array");
            validCategories = new String[] {};
        } else {
            validCategories = categories;
        }

        L.d("[ModuleContent] enterContentZoneInternal, categories: [" + Arrays.toString(validCategories) + "]");

        int contentInitialDelay = initialDelayMS;
        long sdkStartTime = UtilsTime.currentTimestampMs() - Countly.applicationStart;
        if (sdkStartTime < CONTENT_START_DELAY_MS) {
            contentInitialDelay += CONTENT_START_DELAY_MS;
        }

        if (countlyTimer != null) { // for tests, in normal conditions this should never be null here
            countlyTimer.startTimer(zoneTimerInterval, contentInitialDelay, new Runnable() {
                @Override public void run() {
                    L.d("[ModuleContent] enterContentZoneInternal, waitForDelay: [" + waitForDelay + "], shouldFetchContents: [" + shouldFetchContents + "], categories: [" + Arrays.toString(validCategories) + "]");
                    if (waitForDelay > 0) {
                        waitForDelay--;
                        return;
                    }

                    if (!shouldFetchContents) {
                        L.w("[ModuleContent] enterContentZoneInternal, shouldFetchContents is false, skipping");
                        return;
                    }

                    fetchContentsInternal(validCategories, callbackOnFailure, null);
                }
            }, L);
        }
    }

    private void showContentOverlay(@NonNull Activity activity, @NonNull Map<Integer, TransparentActivityConfig> placementCoordinates) {
        L.d("[ModuleContent] showContentOverlay, showing content overlay on [" + activity.getClass().getSimpleName() + "]");

        if (!webViewEnabled) {
            L.w("[ModuleContent] showContentOverlay, WebView is disabled via configuration, skipping");
            return;
        }

        // Do not show content if feedback widget is currently showing
        ModuleFeedback feedbackModule = _cly.moduleFeedback;
        if (feedbackModule != null && feedbackModule.feedbackOverlay != null) {
            shouldFetchContents = true;
            isCurrentlyInContentZone = false;
            L.w("[ModuleContent] showContentOverlay, feedback widget is currently showing, skipping content");
            return;
        }

        // Only one content or feedback overlay may be presented at a time across the whole process,
        // including other SDK instances (the overlay is bound to the single foreground Activity).
        if (ContentOverlayView.isOtherOverlayPresented(contentOverlay)) {
            shouldFetchContents = true;
            isCurrentlyInContentZone = false;
            L.w("[ModuleContent] showContentOverlay, another content or feedback overlay is already being shown (possibly by another instance), skipping content");
            return;
        }

        // Clean up any existing overlay
        if (contentOverlay != null) {
            contentOverlay.destroy();
            contentOverlay = null;
        }

        TransparentActivityConfig portrait = placementCoordinates.get(Configuration.ORIENTATION_PORTRAIT);
        TransparentActivityConfig landscape = placementCoordinates.get(Configuration.ORIENTATION_LANDSCAPE);

        if (portrait == null || landscape == null) {
            L.e("[ModuleContent] showContentOverlay, missing orientation config, portrait: [" + (portrait != null) + "], landscape: [" + (landscape != null) + "], skipping");
            return;
        }

        int orientation = activity.getResources().getConfiguration().orientation;

        contentOverlay = new ContentOverlayView(
            _cly,
            activity,
            portrait,
            landscape,
            orientation,
            globalContentCallback,
            this::notifyAfterContentIsClosed,
            _cly.config_.content.allowedIntentSchemes,
            _cly.config_.content.contentUrlHandler
        );

        contentOverlay.attachToActivity(activity);
        isCurrentlyInContentZone = true;
    }

    void notifyAfterContentIsClosed() {
        L.v("[ModuleContent] notifyAfterContentIsClosed, setting waitForDelay to 2 and shouldFetchContents to true");
        waitForDelay = 2; // this is indicating that we will wait 1 min after closing the content and before fetching the next one
        shouldFetchContents = true;
        isCurrentlyInContentZone = false;
        contentOverlay = null;
    }

    @NonNull
    private String prepareContentFetchRequest(@NonNull DisplayMetrics displayMetrics, @NonNull String[] categories, @Nullable String contentId) {
        Resources resources = _cly.context_.getResources();
        int currentOrientation = resources.getConfiguration().orientation;
        boolean portrait = currentOrientation == Configuration.ORIENTATION_PORTRAIT;

        int portraitWidth, portraitHeight, landscapeWidth, landscapeHeight;

        int totalWidthPx = displayMetrics.widthPixels;
        int totalHeightPx = displayMetrics.heightPixels;
        int totalWidthDp = (int) Math.floor(totalWidthPx / displayMetrics.density);
        int totalHeightDp = (int) Math.floor(totalHeightPx / displayMetrics.density);
        L.d("[ModuleContent] prepareContentFetchRequest, total screen dimensions (px): [" + totalWidthPx + "x" + totalHeightPx + "], (dp): [" + totalWidthDp + "x" + totalHeightDp + "], density: [" + displayMetrics.density + "]");

        WebViewDisplayOption displayOption = _cly.config_.webViewDisplayOption;
        L.d("[ModuleContent] prepareContentFetchRequest, display option: [" + displayOption + "]");

        if (displayOption == WebViewDisplayOption.SAFE_AREA) {
            L.d("[ModuleContent] prepareContentFetchRequest, calculating safe area dimensions...");
            SafeAreaDimensions safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(getSafeAreaContext(), L);

            // px to dp
            portraitWidth = (int) Math.floor(safeArea.portraitWidth / displayMetrics.density);
            portraitHeight = (int) Math.floor(safeArea.portraitHeight / displayMetrics.density);
            landscapeWidth = (int) Math.floor(safeArea.landscapeWidth / displayMetrics.density);
            landscapeHeight = (int) Math.floor(safeArea.landscapeHeight / displayMetrics.density);

            L.d("[ModuleContent] prepareContentFetchRequest, safe area dimensions (px->dp) - Portrait: [" + safeArea.portraitWidth + "x" + safeArea.portraitHeight + " px] -> [" + portraitWidth + "x" + portraitHeight + " dp], topOffset: [" + safeArea.portraitTopOffset + " px]");
            L.d("[ModuleContent] prepareContentFetchRequest, safe area dimensions (px->dp) - Landscape: [" + safeArea.landscapeWidth + "x" + safeArea.landscapeHeight + " px] -> [" + landscapeWidth + "x" + landscapeHeight + " dp], topOffset: [" + safeArea.landscapeTopOffset + " px]");
        } else {
            int scaledWidth = totalWidthDp;
            int scaledHeight = totalHeightDp;

            portraitWidth = portrait ? scaledWidth : scaledHeight;
            portraitHeight = portrait ? scaledHeight : scaledWidth;
            landscapeWidth = portrait ? scaledHeight : scaledWidth;
            landscapeHeight = portrait ? scaledWidth : scaledHeight;

            L.d("[ModuleContent] prepareContentFetchRequest, using immersive mode (full screen) dimensions (dp) - Portrait: [" + portraitWidth + "x" + portraitHeight + "], Landscape: [" + landscapeWidth + "x" + landscapeHeight + "]");
        }

        L.i("[ModuleContent] prepareContentFetchRequest, FINAL dimensions to send to server (dp) - Portrait: [" + portraitWidth + "x" + portraitHeight + "], Landscape: [" + landscapeWidth + "x" + landscapeHeight + "]");

        String language = Locale.getDefault().getLanguage().toLowerCase();
        String deviceType = deviceInfo.mp.getDeviceType(_cly.context_);

        return requestQueueProvider.prepareFetchContents(portraitWidth, portraitHeight, landscapeWidth, landscapeHeight, categories, language, deviceType, contentId);
    }

    boolean validateResponse(@NonNull JSONObject response) {
        return response.has("geo") && response.has("html");
    }

    @NonNull
    Map<Integer, TransparentActivityConfig> parseContent(@NonNull JSONObject response, @NonNull DisplayMetrics displayMetrics) {
        Map<Integer, TransparentActivityConfig> placementCoordinates = new HashMap<>();

        assert response != null;

        String content = response.optString("html");
        if (!content.isEmpty()) {
            content = UtilsDevice.appendThemeParam(content, _cly.context_);
        }
        JSONObject coordinates = response.optJSONObject("geo");

        assert coordinates != null;

        WebViewDisplayOption displayOption = _cly.config_.webViewDisplayOption;
        SafeAreaDimensions safeArea = null;

        if (displayOption == WebViewDisplayOption.SAFE_AREA) {
            L.d("[ModuleContent] parseContent, calculating safe area for coordinate adjustment...");
            safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(getSafeAreaContext(), L);
        }

        TransparentActivityConfig portraitConfig = extractOrientationPlacements(coordinates, displayMetrics.density, "p", content,
            displayOption, safeArea != null ? safeArea.portraitTopOffset : 0, safeArea != null ? safeArea.portraitLeftOffset : 0);
        TransparentActivityConfig landscapeConfig = extractOrientationPlacements(coordinates, displayMetrics.density, "l", content,
            displayOption, safeArea != null ? safeArea.landscapeTopOffset : 0, safeArea != null ? safeArea.landscapeLeftOffset : 0);

        if (portraitConfig != null) {
            placementCoordinates.put(Configuration.ORIENTATION_PORTRAIT, portraitConfig);
        }
        if (landscapeConfig != null) {
            placementCoordinates.put(Configuration.ORIENTATION_LANDSCAPE, landscapeConfig);
        }

        return placementCoordinates;
    }

    private TransparentActivityConfig extractOrientationPlacements(@NonNull JSONObject placements,
        float density, @NonNull String orientation, @NonNull String content,
        WebViewDisplayOption displayOption, int topOffset, int leftOffset) {
        if (placements.has(orientation)) {
            JSONObject orientationPlacements = placements.optJSONObject(orientation);
            assert orientationPlacements != null;
            int x = orientationPlacements.optInt("x");
            int y = orientationPlacements.optInt("y");
            int w = orientationPlacements.optInt("w");
            int h = orientationPlacements.optInt("h");
            L.d("[ModuleContent] extractOrientationPlacements, orientation: [" + orientation + "], x: [" + x + "], y: [" + y + "], w: [" + w + "], h: [" + h + "]");

            int xPx = Math.round(x * density);
            int yPx = Math.round(y * density);
            int wPx = Math.round(w * density);
            int hPx = Math.round(h * density);
            L.d("[ModuleContent] extractOrientationPlacements, orientation: [" + orientation + "], converting dp->px: [" + w + "x" + h + " dp] -> [" + wPx + "x" + hPx + " px], density: [" + density + "]");

            TransparentActivityConfig config = new TransparentActivityConfig(xPx, yPx, wPx, hPx);
            config.url = content;
            config.useSafeArea = (displayOption == WebViewDisplayOption.SAFE_AREA);
            config.topOffset = topOffset;
            config.leftOffset = leftOffset;

            L.d("[ModuleContent] extractOrientationPlacements, orientation: [" + orientation + "], created config - useSafeArea: [" + config.useSafeArea + "], topOffset: [" + config.topOffset + "], leftOffset: [" + config.leftOffset + "]");

            return config;
        }

        return null;
    }

    @Override
    void halt() {
        triggersHalted = true;
        triggerHandler.removeCallbacksAndMessages(null);
        contentInterface = null;
        countlyTimer.stopTimer(L);
        countlyTimer = null;
        if (contentOverlay != null) {
            contentOverlay.destroy();
            contentOverlay = null;
        }
        currentActivity = null;
        pendingContentConfigs = null;
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        L.d("[ModuleContent] onConsentChanged, consentChangeDelta: [" + consentChangeDelta + "], newConsent: [" + newConsent + "], changeSource: [" + changeSource + "]");
        if (consentChangeDelta.contains(Countly.CountlyFeatureNames.content) && !newConsent) {
            if (contentZoneWanted) {
                contentZoneExited = true;
            }
            exitContentZoneInternal();
        }
    }

    @Override
    void deviceIdChanged(boolean withoutMerge) {
        L.d("[ModuleContent] deviceIdChanged, withoutMerge: [" + withoutMerge + "]");
        if (withoutMerge) {
            if (contentZoneWanted) {
                contentZoneExited = true;
            }
            exitContentZoneInternal();
        }
    }

    /**
     * Resumes the content zone after exiting temporary device ID mode. Called only from the
     * temporary-ID-exit path (not from a generic device ID change), so a plain changeWithoutMerge
     * does not silently re-arm a zone the developer turned off. The server config re-fetch on
     * exiting temporary mode only notifies modules when a value changes, so an unchanged (still
     * enabled) content-zone value would otherwise leave the zone torn down here.
     */
    void resumeContentZoneAfterTemporaryIdExit() {
        if (configProvider.getContentZoneEnabled()) {
            L.d("[ModuleContent] resumeContentZoneAfterTemporaryIdExit, content zone is enabled, re-entering");
            enterContentZoneInternal(null, 0, null);
        }
    }

    @NonNull
    private Context getSafeAreaContext() {
        Activity heldActivity = getCurrentActivity();
        return (heldActivity != null && !heldActivity.isFinishing()) ? heldActivity : _cly.context_;
    }

    private void exitContentZoneInternal() {
        shouldFetchContents = false;
        contentZoneWanted = false;
        countlyTimer.stopTimer(L);
        waitForDelay = 0;
        if (contentOverlay != null) {
            contentOverlay.destroy();
            contentOverlay = null;
        }
        isCurrentlyInContentZone = false;
        pendingContentConfigs = null;
    }

    void previewContentInternal(@NonNull String contentId) {
        L.d("[ModuleContent] previewContentInternal, contentId: [" + contentId + "]");

        if (!webViewEnabled) {
            L.d("[ModuleContent] previewContentInternal, WebView is disabled via configuration, skipping");
            return;
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
            L.w("[ModuleContent] previewContentInternal, Consent is not granted, skipping");
            return;
        }

        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.w("[ModuleContent] previewContentInternal, temporary device ID is enabled, skipping");
            return;
        }

        if (isCurrentlyInContentZone) {
            L.w("[ModuleContent] previewContentInternal, content is already being displayed, skipping");
            return;
        }

        fetchContentsInternal(new String[] {}, null, contentId);
    }

    void refreshContentZoneInternal() {
        if (!configProvider.getRefreshContentZoneEnabled()) {
            return;
        }

        if (isCurrentlyInContentZone) {
            L.w("[ModuleContent] refreshContentZone, already in content zone, skipping");
            return;
        }

        if (!shouldFetchContents) {
            exitContentZoneInternal();
        }

        requestQueueProvider.registerInternalGlobalRequestCallbackAction(new Runnable() {
            @Override public void run() {
                enterContentZoneInternal(null, 0, null);
            }
        });
        ModuleRequestQueue requestQueueModule = _cly.moduleRequestQueue;
        if (requestQueueModule != null) {
            requestQueueModule.attemptToSendStoredRequestsInternal();
        }
    }

    /**
     * Called by the module that recorded something that can be a content trigger. Runs the check on the main thread,
     * where all content trigger state is kept.
     *
     * @param trigger what was recorded
     */
    void onContentTrigger(@NonNull final ContentTriggers.Trigger trigger) {
        if (triggersHalted) {
            return;
        }
        if (behaviorSettingsSettled && ContentTriggers.match(configProvider.getContentTriggers(), contentZoneWanted, configProvider.getJourneyTriggerEvents(), configProvider.getJourneyTriggerViews(), trigger) == null) {
            return;
        }
        triggerHandler.post(() -> handleContentTrigger(trigger));
    }

    /**
     * Called when the behavior settings answer of this run arrived, or when none will arrive. What was recorded before
     * it and the settings in use did not list is checked again against the answer, so that content for a first
     * session, which begins before the answer, is still asked for.
     *
     * @param answered true when an answer arrived and was applied
     */
    void onBehaviorSettingsSettled(final boolean answered) {
        if (triggersHalted) {
            return;
        }
        triggerHandler.post(() -> settleEarlyTriggers(answered));
    }

    /**
     * Decides whether what was recorded starts a content trigger round: it must be a content trigger, content must be
     * possible to show, and the same trigger must be outside its gap. A trigger during a running round only makes one
     * more round follow it.
     *
     * @param trigger what was recorded
     */
    private void handleContentTrigger(@NonNull ContentTriggers.Trigger trigger) {
        if (triggersHalted || !webViewEnabled) {
            return;
        }

        ContentTriggers.Match match = ContentTriggers.match(configProvider.getContentTriggers(), contentZoneWanted, configProvider.getJourneyTriggerEvents(), configProvider.getJourneyTriggerViews(), trigger);
        if (match == null) {
            rememberEarlyTrigger(trigger);
            return;
        }

        if (contentZoneExited || !consentProvider.getConsent(Countly.CountlyFeatureNames.content) || deviceIdProvider.isTemporaryIdEnabled()) {
            L.d("[ModuleContent] handleContentTrigger, [" + trigger + "] is a content trigger, but content can not be shown now, so it is not asked for");
            return;
        }

        String gaps = ContentTriggers.claimGap(match, storageProvider.getContentTriggerGaps(), UtilsTime.currentTimestampMs());
        if (gaps == null) {
            L.d("[ModuleContent] handleContentTrigger, [" + trigger + "] started a round less than [" + (match.gapMs / 1000) + "] seconds ago");
            return;
        }
        storageProvider.setContentTriggerGaps(gaps);

        if (triggerRoundRunning) {
            L.d("[ModuleContent] handleContentTrigger, [" + trigger + "] matched while a round is running, one more round follows it");
            triggerRoundPending = true;
            return;
        }

        L.d("[ModuleContent] handleContentTrigger, [" + trigger + "] matched, sending the queued requests and asking for content");
        startTriggerRound();
    }

    /**
     * Keeps what was recorded before the behavior settings answer of this run arrived, when the settings in use did not
     * list it
     *
     * @param trigger what was recorded
     */
    private void rememberEarlyTrigger(@NonNull ContentTriggers.Trigger trigger) {
        if (behaviorSettingsSettled || earlyTriggers.size() >= TRIGGER_EARLY_LIMIT) {
            return;
        }
        for (ContentTriggers.Trigger early : earlyTriggers) {
            if (early.sameAs(trigger)) {
                return;
            }
        }
        earlyTriggers.add(trigger);
    }

    /**
     * Ends the wait for the behavior settings answer of this run. When the answer arrived, on a first start (no settings
     * were stored at init) its "ecz" decides the content zone entry, and the first remembered trigger it lists starts a
     * round.
     *
     * @param answered true when an answer arrived and was applied
     */
    private void settleEarlyTriggers(boolean answered) {
        List<ContentTriggers.Trigger> early = new ArrayList<>(earlyTriggers);
        earlyTriggers.clear();
        if (behaviorSettingsSettled) {
            return;
        }
        behaviorSettingsSettled = true;
        if (!answered) {
            return;
        }

        ModuleConfiguration configurationModule = _cly.moduleConfiguration;
        if (configurationModule != null && !configurationModule.behaviorSettingsStoredAtInit && configProvider.getContentZoneEnabled() && !contentZoneWanted && !contentZoneExited) {
            L.d("[ModuleContent] settleEarlyTriggers, no behavior settings were stored at init, and the first answer has 'ecz' on, entering the content zone");
            enterContentZoneInternal(null, 0, null);
        }

        for (ContentTriggers.Trigger trigger : early) {
            if (ContentTriggers.match(configProvider.getContentTriggers(), contentZoneWanted, configProvider.getJourneyTriggerEvents(), configProvider.getJourneyTriggerViews(), trigger) != null) {
                L.d("[ModuleContent] settleEarlyTriggers, [" + trigger + "] was recorded before the behavior settings arrived");
                handleContentTrigger(trigger);
                return;
            }
        }
    }

    /**
     * Starts a content trigger round: moves the held events into the request queue, sends the queue, and asks for
     * content once the requests queued until now are sent
     */
    private void startTriggerRound() {
        if (triggersHalted) {
            return;
        }
        triggerRoundRunning = true;
        triggerRoundPending = false;

        ModuleRequestQueue requestQueueModule = _cly.moduleRequestQueue;
        if (requestQueueModule != null) {
            requestQueueModule.sendEventsIfNeeded(true);
        }

        String[] queued = storageProvider.getRequests();
        if (queued.length == 0) {
            requestTriggeredContent(++contentTriggerChain, 1);
            endTriggerRound();
            return;
        }

        final String lastQueued = queued[queued.length - 1];
        final int remaining = queued.length;
        if (requestQueueModule != null) {
            requestQueueModule.attemptToSendStoredRequestsInternal();
        }
        triggerHandler.postDelayed(() -> waitForTriggerQueue(lastQueued, remaining, 0), TRIGGER_QUEUE_CHECK_MS);
    }

    /**
     * Checks every TRIGGER_QUEUE_CHECK_MS whether the requests queued when the round started are sent. The round asks
     * for content once they are. When no request of them is sent for TRIGGER_QUEUE_CHECKS checks in a row (a request
     * on its way, the backoff mechanism or the failed request cool-down holds them back), or offline mode starts, the
     * round ends without asking for content, and the content zone timer picks the content up.
     *
     * @param lastQueued the last request queued when the round started
     * @param previousRemaining how many requests up to it were queued at the previous check
     * @param checksWithoutProgress checks in a row at which no request of them was sent
     */
    private void waitForTriggerQueue(@NonNull final String lastQueued, final int previousRemaining, final int checksWithoutProgress) {
        if (triggersHalted) {
            return;
        }
        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.d("[ModuleContent] waitForTriggerQueue, offline mode started, the round ends without a content request");
            endTriggerRound();
            return;
        }

        final int remaining = Arrays.asList(storageProvider.getRequests()).lastIndexOf(lastQueued) + 1;
        if (remaining == 0) {
            requestTriggeredContent(++contentTriggerChain, 1);
            endTriggerRound();
            return;
        }

        final int withoutProgress = remaining < previousRemaining ? 0 : checksWithoutProgress + 1;
        if (withoutProgress >= TRIGGER_QUEUE_CHECKS) {
            L.d("[ModuleContent] waitForTriggerQueue, the queued requests could not be sent in time, the round ends without a content request");
            endTriggerRound();
            return;
        }

        ModuleRequestQueue requestQueueModule = _cly.moduleRequestQueue;
        if (requestQueueModule != null) {
            requestQueueModule.attemptToSendStoredRequestsInternal();
        }
        triggerHandler.postDelayed(() -> waitForTriggerQueue(lastQueued, remaining, withoutProgress), TRIGGER_QUEUE_CHECK_MS);
    }

    /**
     * Sends the content request of a round. When it displays nothing, it is sent again TRIGGER_CONTENT_RETRY_MS later,
     * up to TRIGGER_CONTENT_REQUESTS requests, because the server may still be processing what triggered it. A newer
     * round's content request stops the remaining ones of an older round.
     *
     * @param chain the round's number, compared with the newest one
     * @param attempt the number of this content request in the round, from 1
     */
    private void requestTriggeredContent(final int chain, final int attempt) {
        if (triggersHalted || chain != contentTriggerChain) {
            return;
        }
        if (isCurrentlyInContentZone) {
            L.d("[ModuleContent] requestTriggeredContent, content is already displayed, no content request is sent");
            return;
        }
        boolean zoneNeeded = configProvider.getContentTriggers() != null;
        if (contentZoneExited || (zoneNeeded && !contentZoneWanted) || !webViewEnabled || !consentProvider.getConsent(Countly.CountlyFeatureNames.content) || deviceIdProvider.isTemporaryIdEnabled()) {
            L.d("[ModuleContent] requestTriggeredContent, content can not be shown now, no content request is sent");
            return;
        }

        L.d("[ModuleContent] requestTriggeredContent, sending content request [" + attempt + "] of [" + TRIGGER_CONTENT_REQUESTS + "]");
        fetchContentsInternal(new String[] {}, () -> {
            if (attempt < TRIGGER_CONTENT_REQUESTS && !triggersHalted) {
                triggerHandler.postDelayed(() -> requestTriggeredContent(chain, attempt + 1), TRIGGER_CONTENT_RETRY_MS);
            }
        }, null);
    }

    /**
     * Ends the queue part of a round, and starts the round a trigger during it asked for
     */
    private void endTriggerRound() {
        triggerRoundRunning = false;
        if (triggerRoundPending && !triggersHalted) {
            triggerRoundPending = false;
            triggerHandler.post(this::startTriggerRound);
        }
    }

    public class Content {

        /**
         * Enables content fetching and updates for the user.
         * This method opts the user into receiving content updates
         * and ensures that relevant data is fetched accordingly.
         */
        public void enterContentZone() {
            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
                L.w("[ModuleContent] enterContentZone, Consent is not granted, skipping");
                return;
            }

            enterContentZoneInternal(null, 0, null);
        }

        /**
         * Disables content fetching and updates for the user.
         * This method opts the user out of receiving content updates
         * and stops any ongoing content retrieval processes.
         */
        public void exitContentZone() {
            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
                L.w("[ModuleContent] exitFromContent, Consent is not granted, skipping");
                return;
            }

            contentZoneExited = true;
            exitContentZoneInternal();
        }

        /**
         * Previews a specific content by its ID.
         * This performs a one-time fetch for the given content
         * without starting periodic content updates.
         *
         * @param contentId the ID of the content to preview
         */
        public void previewContent(@Nullable String contentId) {
            if (Utils.isNullOrEmpty(contentId)) {
                L.w("[ModuleContent] previewContent, contentId is null or empty, skipping");
                healthTracker.recordLogCode("w700");
                return;
            }

            previewContentInternal(contentId);
        }

        /**
         * Triggers a manual refresh of the content zone.
         * This method forces an update by fetching the latest content,
         * ensuring the user receives the most up-to-date information.
         */
        public void refreshContentZone() {
            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
                L.w("[ModuleContent] refreshContentZone, Consent is not granted, skipping");
                return;
            }

            refreshContentZoneInternal();
        }

        /**
         * Sets a segmentation recorded with every content and feedback widget event, on top of the
         * keys those events already report. It is kept for as long as the app runs, including when a
         * session ends and a new one starts, and it is not restored after an app restart. Keys those
         * events reserve, like "rating", "closed", "dismissed" or "answ-" answers, are dropped.
         *
         * @param segmentation Map<String, Object> - global content segmentation, 'null' clears it
         * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
         */
        public void setGlobalContentSegmentation(@Nullable Map<String, Object> segmentation) {
            L.i("[Content] Calling setGlobalContentSegmentation sg[" + (segmentation == null ? segmentation : segmentation.size()) + "]");

            setGlobalContentSegmentationInternal(segmentation);
        }
    }
}