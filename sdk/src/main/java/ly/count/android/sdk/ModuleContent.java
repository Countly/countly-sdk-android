package ly.count.android.sdk;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
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
    private int zoneTimerInterval;
    private final ContentCallback globalContentCallback;
    private int waitForDelay = 0;
    int CONTENT_START_DELAY_MS = 4000; // 4 seconds
    int REFRESH_CONTENT_ZONE_DELAY_MS = 2500; // 2.5 seconds

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
            enterContentZoneInternal(null, 0);
        }
    }

    @Override
    void initFinished(@NotNull CountlyConfig config) {
        if (configProvider.getContentZoneEnabled()) {
            enterContentZoneInternal(null, 0);
        }
    }

    @Override
    void onActivityStarted(Activity activity, int updatedActivityCount) {
        if (UtilsDevice.cutout == null && activity != null) {
            UtilsDevice.getCutout(activity);
        }
    }

    void fetchContentsInternal(@NonNull String[] categories) {
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

                    iRGenerator.CreatePreflightRequestMaker().doWork(checkResponse.optString("html"), null, cp, false, true, preflightResponse -> {
                        if (preflightResponse == null) {
                            L.d("[ModuleContent] fetchContentsInternal, preflight check failed, skipping showing content");
                            return;
                        }

                        Map<Integer, TransparentActivityConfig> placementCoordinates = parseContent(checkResponse, displayMetrics);
                        if (placementCoordinates.isEmpty()) {
                            L.d("[ModuleContent] fetchContentsInternal, placement coordinates are empty, skipping");
                            return;
                        }

                        L.d("[ModuleContent] fetchContentsInternal, preflight check succeeded");
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
                    }, L);
                } else {
                    L.w("[ModuleContent] fetchContentsInternal, response is not valid, skipping");
                }
            } catch (Exception ex) {
                L.e("[ModuleContent] fetchContentsInternal, Encountered internal issue while trying to fetch contents, [" + ex + "]");
            }
        }, L);
    }

    private void enterContentZoneInternal(@Nullable String[] categories, final int initialDelayMS) {
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

                fetchContentsInternal(validCategories);
            }
        }, L);
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

        int scaledWidth = (int) Math.ceil(displayMetrics.widthPixels / displayMetrics.density);
        int scaledHeight = (int) Math.ceil(displayMetrics.heightPixels / displayMetrics.density);

        // this calculation needs improvement for status bar and navigation bar
        int portraitWidth = portrait ? scaledWidth : scaledHeight;
        int portraitHeight = portrait ? scaledHeight : scaledWidth;
        int landscapeWidth = portrait ? scaledHeight : scaledWidth;
        int landscapeHeight = portrait ? scaledWidth : scaledHeight;

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
        placementCoordinates.put(Configuration.ORIENTATION_PORTRAIT, extractOrientationPlacements(coordinates, displayMetrics.density, "p", content));
        placementCoordinates.put(Configuration.ORIENTATION_LANDSCAPE, extractOrientationPlacements(coordinates, displayMetrics.density, "l", content));

        return placementCoordinates;
    }

    private TransparentActivityConfig extractOrientationPlacements(@NonNull JSONObject placements, float density, @NonNull String orientation, @NonNull String content) {
        if (placements.has(orientation)) {
            JSONObject orientationPlacements = placements.optJSONObject(orientation);
            assert orientationPlacements != null;
            int x = orientationPlacements.optInt("x");
            int y = orientationPlacements.optInt("y");
            int w = orientationPlacements.optInt("w");
            int h = orientationPlacements.optInt("h");
            L.d("[ModuleContent] extractOrientationPlacements, orientation: [" + orientation + "], x: [" + x + "], y: [" + y + "], w: [" + w + "], h: [" + h + "]");
            TransparentActivityConfig config = new TransparentActivityConfig((int) Math.ceil(x * density), (int) Math.ceil(y * density), (int) Math.ceil(w * density), (int) Math.ceil(h * density));
            config.url = content;
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

    private void refreshContentZoneInternal() {
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

        _cly.moduleRequestQueue.attemptToSendStoredRequestsInternal();

        enterContentZoneInternal(null, REFRESH_CONTENT_ZONE_DELAY_MS);
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

            enterContentZoneInternal(null, 0);
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

            refreshContentZoneInternal();
        }
    }
}
