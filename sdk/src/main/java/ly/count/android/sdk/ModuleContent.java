package ly.count.android.sdk;

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
import org.json.JSONArray;
import org.json.JSONObject;

public class ModuleContent extends ModuleBase {
    private final ImmediateRequestGenerator iRGenerator;
    Content contentInterface;
    CountlyTimer countlyTimer;
    private boolean shouldFetchContents = false;
    private final int zoneTimerInterval;
    private final ContentCallback globalContentCallback;
    private int waitForDelay = 0;

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

                    shouldFetchContents = false; // disable fetching contents until the next time, this will disable the timer fetching
                } else {
                    L.w("[ModuleContent] fetchContentsInternal, response is not valid, skipping");
                }
            } catch (Exception ex) {
                L.e("[ModuleContent] fetchContentsInternal, Encountered internal issue while trying to fetch contents, [" + ex + "]");
            }
        }, L);
    }

    void registerForContentUpdates(@Nullable String[] categories) {
        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.w("[ModuleContent] registerForContentUpdates, temporary device ID is enabled, skipping");
            return;
        }

        String[] validCategories;

        if (categories == null) {
            L.w("[ModuleContent] registerForContentUpdates, categories is null, providing empty array");
            validCategories = new String[] {};
        } else {
            validCategories = categories;
        }

        countlyTimer.startTimer(zoneTimerInterval, () -> {
            L.d("[ModuleContent] registerForContentUpdates, waitForDelay: [" + waitForDelay + "], shouldFetchContents: [" + shouldFetchContents + "], categories: [" + Arrays.toString(validCategories) + "]");

            if (waitForDelay > 0) {
                waitForDelay--;
                return;
            }

            if (!shouldFetchContents) {
                L.w("[ModuleContent] registerForContentUpdates, shouldFetchContents is false, skipping");
                return;
            }

            fetchContentsInternal(validCategories);
        }, L);
    }

    void notifyAfterContentIsClosed() {
        L.v("[ModuleContent] notifyAfterContentIsClosed, setting waitForDelay to 2 and shouldFetchContents to true");
        waitForDelay = 2; // this is indicating that we will wait 1 min after closing the content and before fetching the next one
        shouldFetchContents = true;
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

        return requestQueueProvider.prepareFetchContents(portraitWidth, portraitHeight, landscapeWidth, landscapeHeight, categories, language);
    }

    boolean validateResponse(@NonNull JSONObject response) {
        return response.has("geo");
        //boolean success = response.optString("result", "error").equals("success");
        //JSONArray content = response.optJSONArray("content");
        //return success && content != null && content.length() > 0;
    }

    @NonNull
    Map<Integer, TransparentActivityConfig> parseContent(@NonNull JSONObject response, @NonNull DisplayMetrics displayMetrics) {
        Map<Integer, TransparentActivityConfig> placementCoordinates = new ConcurrentHashMap<>();
        JSONArray contents = response.optJSONArray("content");
        //assert contents != null; TODO enable later

        JSONObject contentObj = response; //contents.optJSONObject(0); TODO this will be changed
        assert contentObj != null;

        String content = contentObj.optString("html");
        JSONObject coordinates = contentObj.optJSONObject("geo");

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
            // TODO, passing callback with an intent is impossible, need to find a way to pass it
            // Currently, the callback is set as a static variable in TransparentActivity
            TransparentActivity.globalContentCallback = globalContentCallback;
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

    protected void exitContentZoneInternal() {
        shouldFetchContents = false;
        countlyTimer.stopTimer(L);
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
            registerForContentUpdates(categories);
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

            registerForContentUpdates(categories);
        }
    }
}
