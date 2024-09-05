package ly.count.android.sdk;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModuleContent extends ModuleBase {
    private final ImmediateRequestGenerator iRGenerator;
    Content contentInterface;
    CountlyTimer countlyTimer;
    private boolean shouldFetchContents = false;
    private final int contentUpdateInterval;
    private final ContentCallback globalContentCallback;

    ModuleContent(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleContent] Initialising");
        iRGenerator = config.immediateRequestGenerator;

        contentInterface = new Content();
        countlyTimer = new CountlyTimer();
        contentUpdateInterval = config.contents.contentUpdateInterval;
        globalContentCallback = config.contents.globalContentCallback;
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
                    Map<Integer, TransparentActivityConfig> placementCoordinates = parseContent(checkResponse, displayMetrics);
                    if (placementCoordinates.isEmpty()) {
                        L.d("[ModuleContent] fetchContentsInternal, placement coordinates are empty, skipping");
                        return;
                    }

                    Intent intent = new Intent(_cly.context_, TransparentActivity.class);
                    intent.putExtra(TransparentActivity.CONFIGURATION_LANDSCAPE, placementCoordinates.get(Configuration.ORIENTATION_LANDSCAPE));
                    intent.putExtra(TransparentActivity.CONFIGURATION_PORTRAIT, placementCoordinates.get(Configuration.ORIENTATION_PORTRAIT));
                    intent.putExtra(TransparentActivity.ORIENTATION, _cly.context_.getResources().getConfiguration().orientation);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    _cly.context_.startActivity(intent);

                    countlyTimer.stopTimer(L); // stop the timer after a new content is fetched
                } else {
                    L.w("[ModuleContent] fetchContentsInternal, response is not valid, skipping");
                }
            } catch (Exception ex) {
                L.e("[ModuleContent] fetchContentsInternal, Encountered internal issue while trying to fetch contents, [" + ex + "]");
            }
        }, L);
    }

    void registerForContentUpdates(@Nullable String[] categories, long initialDelay) {
        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.w("[ModuleContent] registerForContentUpdates, temporary device ID is enabled, skipping");
            return;
        }

        if (!shouldFetchContents) {
            L.w("[ModuleContent] registerForContentUpdates, shouldFetchContents is false, skipping");
            return;
        }

        String[] validCategories;

        if (categories == null) {
            L.w("[ModuleContent] registerForContentUpdates, categories is null, providing empty array");
            validCategories = new String[] {};
        } else {
            validCategories = categories;
        }

        countlyTimer.startTimer(contentUpdateInterval, initialDelay, () -> {
            L.v("[ModuleContent] registerForContentUpdates, experimental mode enabled, directly fetching contents");
            fetchContentsInternal(validCategories);
        }, L);
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

        return requestQueueProvider.prepareFetchContents(portraitWidth, portraitHeight, landscapeWidth, landscapeHeight, categories);
    }

    boolean validateResponse(@NonNull JSONObject response) {
        boolean success = response.optBoolean("result", false);
        JSONArray content = response.optJSONArray("content");
        return success && content != null && content.length() > 0;
    }

    @NonNull
    Map<Integer, TransparentActivityConfig> parseContent(@NonNull JSONObject response, @NonNull DisplayMetrics displayMetrics) {
        Map<Integer, TransparentActivityConfig> placementCoordinates = new ConcurrentHashMap<>();
        JSONArray contents = response.optJSONArray("content");
        assert contents != null;

        JSONObject contentObj = contents.optJSONObject(0);
        assert contentObj != null;

        String content = contentObj.optString("pathToHtml");
        JSONObject coordinates = contentObj.optJSONObject("placementCoordinates");

        assert coordinates != null;
        placementCoordinates.put(Configuration.ORIENTATION_PORTRAIT, extractOrientationPlacements(coordinates, displayMetrics.density, "portrait", content));
        placementCoordinates.put(Configuration.ORIENTATION_LANDSCAPE, extractOrientationPlacements(coordinates, displayMetrics.density, "landscape", content));

        return placementCoordinates;
    }

    private TransparentActivityConfig extractOrientationPlacements(@NonNull JSONObject placements, float density, @NonNull String orientation, @NonNull String content) {
        if (placements.has(orientation)) {
            JSONObject orientationPlacements = placements.optJSONObject(orientation);
            assert orientationPlacements != null;
            int x = orientationPlacements.optInt("x");
            int y = orientationPlacements.optInt("y");
            int w = orientationPlacements.optInt("width");
            int h = orientationPlacements.optInt("height");
            L.d("[ModuleContent] extractOrientationPlacements, orientation: [" + orientation + "], x: [" + x + "], y: [" + y + "], w: [" + w + "], h: [" + h + "]");

            TransparentActivityConfig config = new TransparentActivityConfig((int) Math.ceil(x * density), (int) Math.ceil(y * density), (int) Math.ceil(w * density), (int) Math.ceil(h * density));
            config.url = content;
            config.globalContentCallback = globalContentCallback;
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

    private void optOutFromContent() {
        exitContentZoneInternal();
        shouldFetchContents = false;
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        L.d("[ModuleContent] onConsentChanged, consentChangeDelta: [" + consentChangeDelta + "], newConsent: [" + newConsent + "], changeSource: [" + changeSource + "]");
        if (consentChangeDelta.contains(Countly.CountlyFeatureNames.content) && !newConsent) {
            optOutFromContent();
        }
    }

    @Override
    void deviceIdChanged(boolean withoutMerge) {
        L.d("[ModuleContent] deviceIdChanged, withoutMerge: [" + withoutMerge + "]");
        if (withoutMerge) {
            optOutFromContent();
        }
    }

    @Override
    void initFinished(@NotNull CountlyConfig config) {
        registerForContentUpdates(new String[] {}, 0);
    }

    protected void exitContentZoneInternal() {
        registerForContentUpdates(new String[] {}, contentUpdateInterval * 2L); // 60 seconds initial delay
    }

    public class Content {

        /**
         * Opt in user for the content fetching and updates
         *
         * @param categories categories for the content
         * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
         */
        private void enterContentZone(@Nullable String... categories) {
            L.d("[ModuleContent] openForContent, categories: [" + Arrays.toString(categories) + "]");

            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
                L.w("[ModuleContent] openForContent, Consent is not granted, skipping");
                return;
            }

            shouldFetchContents = true;
            registerForContentUpdates(categories, 0);
        }

        /**
         * Opt in user for the content fetching and updates
         *
         * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
         */
        public void enterContentZone() {
            enterContentZone(new String[] {});
        }

        /**
         * Opt out user from the content fetching and updates
         *
         * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
         */
        public void exitContentZone() {
            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
                L.w("[ModuleContent] exitFromContent, Consent is not granted, skipping");
                return;
            }

            exitContentZoneInternal();
        }

        /**
         * Change the content that is being shown
         *
         * @param categories categories for the content
         * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
         */
        private void changeContent(@Nullable String... categories) {
            L.d("[ModuleContent] changeContent, categories: [" + Arrays.toString(categories) + "]");

            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.content)) {
                L.w("[ModuleContent] changeContent, Consent is not granted, skipping");
                return;
            }

            registerForContentUpdates(categories, 0);
        }
    }
}
