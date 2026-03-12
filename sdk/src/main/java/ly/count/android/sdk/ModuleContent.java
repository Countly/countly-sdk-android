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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class ModuleContent extends ModuleBase {
    private final ImmediateRequestGenerator iRGenerator;
    Content contentInterface;
    CountlyTimer countlyTimer;
    private boolean shouldFetchContents = false;
    private boolean isCurrentlyInContentZone = false;
    private boolean isCurrentlyRetrying = false;
    private int zoneTimerInterval;
    private final ContentCallback globalContentCallback;
    private int waitForDelay = 0;
    int CONTENT_START_DELAY_MS = 4000; // 4 seconds

    private Activity currentActivity;
    ContentOverlayView contentOverlay;
    // Buffered content when no activity is available
    private Map<Integer, TransparentActivityConfig> pendingContentConfigs;

    ModuleContent(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleContent] Initialising, zoneTimerInterval: [" + config.content.zoneTimerInterval + "], globalContentCallback: [" + config.content.globalContentCallback + "]");
        iRGenerator = config.immediateRequestGenerator;

        contentInterface = new Content();
        countlyTimer = new CountlyTimer();
        zoneTimerInterval = config.content.zoneTimerInterval;
        globalContentCallback = config.content.globalContentCallback;
    }

    @Override
    void onSdkConfigurationChanged(@NonNull CountlyConfig config) {
        zoneTimerInterval = config.content.zoneTimerInterval;
        if (!configProvider.getContentZoneEnabled()) {
            exitContentZoneInternal();
        } else {
            if (!shouldFetchContents) {
                exitContentZoneInternal();
            }
            waitForDelay = 0;
            enterContentZoneInternal(null, 0, null);
        }
    }

    @Override
    void initFinished(@NotNull CountlyConfig config) {
        if (configProvider.getContentZoneEnabled()) {
            enterContentZoneInternal(null, 0, null);
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

        currentActivity = activity;

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

    void fetchContentsInternal(@NonNull String[] categories, @Nullable Runnable callbackOnFailure) {
        L.d("[ModuleContent] fetchContentsInternal, shouldFetchContents: [" + shouldFetchContents + "], categories: [" + Arrays.toString(categories) + "]");

        DisplayMetrics displayMetrics = deviceInfo.mp.getDisplayMetrics(_cly.context_);
        String requestData = prepareContentFetchRequest(displayMetrics, categories);

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

        iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/o/sdk/content", cp, false, networkingIsEnabled, checkResponse -> {
            L.d("[ModuleContent] fetchContentsInternal, processing fetched contents, received response is :[" + checkResponse + "]");
            if (checkResponse == null) {
                return;
            }

            try {
                if (validateResponse(checkResponse)) {
                    L.d("[ModuleContent] fetchContentsInternal, got new content data, showing it");

                    Map<Integer, TransparentActivityConfig> placementCoordinates = parseContent(checkResponse, displayMetrics);
                    if (placementCoordinates.isEmpty()) {
                        L.d("[ModuleContent] fetchContentsInternal, placement coordinates are empty, skipping");
                        return;
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        shouldFetchContents = false; // disable fetching contents until the next time, this will disable the timer fetching
                        isCurrentlyInContentZone = true;
                        isCurrentlyRetrying = false;

                        if (currentActivity != null && !currentActivity.isFinishing()) {
                            showContentOverlay(currentActivity, placementCoordinates);
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

                    fetchContentsInternal(validCategories, callbackOnFailure);
                }
            }, L);
        }
    }

    private void enterContentZoneWithRetriesInternal() {
        if (isCurrentlyRetrying) {
            L.w("[ModuleContent] enterContentZoneWithRetriesInternal, already retrying, skipping");
            return;
        }
        isCurrentlyRetrying = true;
        Handler handler = new Handler(Looper.getMainLooper());
        int maxRetries = 3;
        int delayMillis = 1000;

        Runnable retryRunnable = new Runnable() {
            int attempt = 0;

            @Override
            public void run() {
                if (isCurrentlyInContentZone) {
                    isCurrentlyRetrying = false; // Reset flag on success
                    return;
                }

                if (countlyTimer != null) { // for tests
                    countlyTimer.stopTimer(L);
                }

                final Runnable self = this; // Capture reference to outer Runnable

                enterContentZoneInternal(null, 0, new Runnable() {
                    @Override public void run() {
                        if (isCurrentlyInContentZone) {
                            isCurrentlyRetrying = false; // Reset flag on success
                            return;
                        }
                        attempt++;
                        if (attempt < maxRetries) {
                            handler.postDelayed(self, delayMillis);
                        } else {
                            L.w("[ModuleContent] enterContentZoneWithRetriesInternal, " + maxRetries + " attempted");
                            isCurrentlyRetrying = false;
                        }
                    }
                });
            }
        };

        handler.post(retryRunnable);
    }

    private void showContentOverlay(@NonNull Activity activity, @NonNull Map<Integer, TransparentActivityConfig> placementCoordinates) {
        L.d("[ModuleContent] showContentOverlay, showing content overlay on [" + activity.getClass().getSimpleName() + "]");

        // Do not show content if feedback widget is currently showing
        if (_cly.moduleFeedback != null && _cly.moduleFeedback.feedbackOverlay != null) {
            shouldFetchContents = true;
            isCurrentlyInContentZone = false;
            L.w("[ModuleContent] showContentOverlay, feedback widget is currently showing, skipping content");
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
            activity,
            portrait,
            landscape,
            orientation,
            globalContentCallback,
            this::notifyAfterContentIsClosed
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
    private String prepareContentFetchRequest(@NonNull DisplayMetrics displayMetrics, @NonNull String[] categories) {
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

        return requestQueueProvider.prepareFetchContents(portraitWidth, portraitHeight, landscapeWidth, landscapeHeight, categories, language, deviceType);
    }

    boolean validateResponse(@NonNull JSONObject response) {
        return response.has("geo") && response.has("html");
    }

    @NonNull
    Map<Integer, TransparentActivityConfig> parseContent(@NonNull JSONObject response, @NonNull DisplayMetrics displayMetrics) {
        Map<Integer, TransparentActivityConfig> placementCoordinates = new HashMap<>();

        assert response != null;

        String content = response.optString("html");
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
            exitContentZoneInternal();
        }
    }

    @Override
    void deviceIdChanged(boolean withoutMerge) {
        L.d("[ModuleContent] deviceIdChanged, withoutMerge: [" + withoutMerge + "]");
        if (withoutMerge) {
            exitContentZoneInternal();
        }
    }

    @NonNull
    private Context getSafeAreaContext() {
        return (currentActivity != null && !currentActivity.isFinishing()) ? currentActivity : _cly.context_;
    }

    private void exitContentZoneInternal() {
        shouldFetchContents = false;
        countlyTimer.stopTimer(L);
        waitForDelay = 0;
        if (contentOverlay != null) {
            contentOverlay.destroy();
            contentOverlay = null;
        }
        isCurrentlyInContentZone = false;
        pendingContentConfigs = null;
    }

    void refreshContentZoneInternal(boolean callRQFlush) {
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

        if (callRQFlush) {
            requestQueueProvider.registerInternalGlobalRequestCallbackAction(new Runnable() {
                @Override public void run() {
                    enterContentZoneInternal(null, 0, null);
                }
            });
            _cly.moduleRequestQueue.attemptToSendStoredRequestsInternal();
        } else {
            enterContentZoneWithRetriesInternal();
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

            exitContentZoneInternal();
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

            refreshContentZoneInternal(true);
        }
    }
}