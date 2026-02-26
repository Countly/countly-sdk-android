package ly.count.android.sdk;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        if (UtilsDevice.cutout == null && activity != null) {
            UtilsDevice.getCutout(activity);
        }
        if (isCurrentlyInContentZone
            && activity != null
            && !(activity instanceof TransparentActivity)) {
            try {
                Intent bringToFront = new Intent(activity, TransparentActivity.class);
                bringToFront.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(bringToFront);
            } catch (Exception ex) {
                L.w("[ModuleContent] onActivityStarted, failed to reorder TransparentActivity to front", ex);
            }
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

                    Intent intent = new Intent(_cly.context_, TransparentActivity.class);
                    intent.putExtra(TransparentActivity.CONFIGURATION_LANDSCAPE, placementCoordinates.get(Configuration.ORIENTATION_LANDSCAPE));
                    intent.putExtra(TransparentActivity.CONFIGURATION_PORTRAIT, placementCoordinates.get(Configuration.ORIENTATION_PORTRAIT));
                    intent.putExtra(TransparentActivity.ORIENTATION, _cly.context_.getResources().getConfiguration().orientation);

                    Long id = System.currentTimeMillis();
                    intent.putExtra(TransparentActivity.ID_CALLBACK, id);
                    if (globalContentCallback != null) {
                        TransparentActivity.contentCallbacks.put(id, globalContentCallback);
                    }

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    _cly.context_.startActivity(intent);

                    shouldFetchContents = false; // disable fetching contents until the next time, this will disable the timer fetching
                    isCurrentlyInContentZone = true;
                    isCurrentlyRetrying = false;
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

    void notifyAfterContentIsClosed() {
        L.v("[ModuleContent] notifyAfterContentIsClosed, setting waitForDelay to 2 and shouldFetchContents to true");
        waitForDelay = 2; // this is indicating that we will wait 1 min after closing the content and before fetching the next one
        shouldFetchContents = true;
        isCurrentlyInContentZone = false;
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
            SafeAreaDimensions safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(_cly.context_, L);

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
        Map<Integer, TransparentActivityConfig> placementCoordinates = new ConcurrentHashMap<>();

        assert response != null;

        String content = response.optString("html");
        JSONObject coordinates = response.optJSONObject("geo");

        assert coordinates != null;

        WebViewDisplayOption displayOption = _cly.config_.webViewDisplayOption;
        SafeAreaDimensions safeArea = null;

        if (displayOption == WebViewDisplayOption.SAFE_AREA) {
            L.d("[ModuleContent] parseContent, calculating safe area for coordinate adjustment...");
            safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(_cly.context_, L);
        }

        placementCoordinates.put(Configuration.ORIENTATION_PORTRAIT,
            extractOrientationPlacements(coordinates, displayMetrics.density, "p", content,
                displayOption, safeArea != null ? safeArea.portraitTopOffset : 0, safeArea != null ? safeArea.portraitLeftOffset : 0));
        placementCoordinates.put(Configuration.ORIENTATION_LANDSCAPE,
            extractOrientationPlacements(coordinates, displayMetrics.density, "l", content,
                displayOption, safeArea != null ? safeArea.landscapeTopOffset : 0, safeArea != null ? safeArea.landscapeLeftOffset : 0));

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

    private void exitContentZoneInternal() {
        shouldFetchContents = false;
        countlyTimer.stopTimer(L);
        waitForDelay = 0;
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