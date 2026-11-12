package ly.count.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class ModuleAttribution extends ModuleBase {

    Attribution attributionInterface;

    static final String TYPE_REFERRER = "referrer";
    static final String TYPE_DEEPLINK = "deeplink";
    static final String TYPE_CHANNEL = "channel";

    //keys of the persisted attribution state
    static final String STATE_REFERRER_DONE = "referrer_done";
    static final String STATE_REFERRER_ATTEMPTS = "referrer_attempts";
    static final String STATE_FIRST_LAUNCH_RESOLVED = "first_launch_resolved";
    //the first launch result, kept until the launch activity was looked at
    static final String STATE_FIRST_LAUNCH_PENDING = "first_launch_pending";
    static final String STATE_DEFERRED_PATH = "deferred_path";
    static final String STATE_DEFERRED_URL = "deferred_url";
    static final String STATE_CHANNEL = "channel";

    //a store that keeps saying "try later" (no Play Store, for one) is asked on this many launches only
    static final int MAX_REFERRER_ATTEMPTS = 3;

    final Context context;
    final InstallReferrerReader installReferrerReader;
    final String attributionChannel;
    final DeepLinkCallback deepLinkCallback;
    final String handledIntentExtra;

    boolean referrerReadInProgress = false;
    //a link opened the app in this process; then the first launch reports no DEFERRED or NONE
    boolean linkOpenedThisProcess = false;
    //the intent of the first activity of this process was looked at; until then the first launch is not reported,
    //because that activity may have been opened by a link
    boolean launchIntentInspected = false;
    //a first launch result known only in this process (the referrer library is missing, so every launch finds it again)
    boolean firstLaunchPendingInMemory = false;
    //read and written under the instance lock
    boolean halted = false;

    ModuleAttribution(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleAttribution] Initialising");

        context = config.context;
        installReferrerReader = config.installReferrerReader != null ? config.installReferrerReader : new PlayInstallReferrerReader();
        attributionChannel = config.attributionChannel;
        deepLinkCallback = config.deepLinkCallback;
        handledIntentExtra = "ly.count.android.sdk.deeplink_handled." + cly.instanceName_;

        attributionInterface = new ModuleAttribution.Attribution();
    }

    // ------------------------------------------------------------------ persisted state

    @NonNull JSONObject readState() {
        String raw = storageProvider.getAttributionState();
        if (raw != null && !raw.isEmpty()) {
            try {
                return new JSONObject(raw);
            } catch (JSONException e) {
                L.w("[ModuleAttribution] readState, stored state is not valid, starting over");
            }
        }
        return new JSONObject();
    }

    void writeState(@NonNull JSONObject state) {
        storageProvider.setAttributionState(state.toString());
    }

    void putState(@NonNull String key, @NonNull Object value) {
        JSONObject state = readState();
        try {
            state.put(key, value);
        } catch (JSONException ignored) {
        }
        writeState(state);
    }

    // ------------------------------------------------------------------ signals sent once

    /**
     * Sends what is sent once per install: the build channel and the install referrer. Runs at init and
     * again when the attribution consent is given.
     */
    void processInstallSignals() {
        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
            L.d("[ModuleAttribution] processInstallSignals, no 'attribution' consent, waiting for it");
            return;
        }
        sendChannelIfNeeded();
        readInstallReferrerIfNeeded();
    }

    void sendChannelIfNeeded() {
        if (attributionChannel == null || attributionChannel.isEmpty()) {
            return;
        }
        String sent = readState().optString(STATE_CHANNEL, "");
        if (attributionChannel.equals(sent)) {
            L.v("[ModuleAttribution] sendChannelIfNeeded, channel already sent");
            return;
        }
        L.d("[ModuleAttribution] sendChannelIfNeeded, sending channel [" + attributionChannel + "]");
        requestQueueProvider.sendAttributionSignal(TYPE_CHANNEL, attributionChannel);
        putState(STATE_CHANNEL, attributionChannel);
    }

    void readInstallReferrerIfNeeded() {
        JSONObject state = readState();
        if (state.optBoolean(STATE_REFERRER_DONE, false)) {
            return;
        }
        if (referrerReadInProgress) {
            return;
        }
        if (!installReferrerReader.isAvailable()) {
            //not marked as done: an app update that adds the library still reads it once
            L.i("[ModuleAttribution] readInstallReferrerIfNeeded, the install referrer library ('com.android.installreferrer:installreferrer') is not in the app, the install referrer is not read");
            //not stored: the next launch finds the library missing again, and storing it would write at every init
            if (!readState().optBoolean(STATE_FIRST_LAUNCH_RESOLVED, false)) {
                firstLaunchPendingInMemory = true;
                deliverFirstLaunchIfReady();
            }
            return;
        }
        if (context == null) {
            L.w("[ModuleAttribution] readInstallReferrerIfNeeded, no context, the install referrer is not read");
            return;
        }

        referrerReadInProgress = true;
        L.d("[ModuleAttribution] readInstallReferrerIfNeeded, reading the install referrer");
        try {
            installReferrerReader.read(context, this::onInstallReferrerResult);
        } catch (Exception t) {
            L.w("[ModuleAttribution] readInstallReferrerIfNeeded, reading failed: " + t);
            onInstallReferrerResult(InstallReferrerReader.STATUS_RETRY, null);
        }
    }

    void onInstallReferrerResult(int status, @Nullable String referrer) {
        synchronized (_cly) {
            referrerReadInProgress = false;
            if (halted) {
                return;
            }
            L.d("[ModuleAttribution] onInstallReferrerResult, status:[" + status + "], referrer:[" + referrer + "]");

            JSONObject state = readState();
            if (state.optBoolean(STATE_REFERRER_DONE, false)) {
                return;
            }

            if (status == InstallReferrerReader.STATUS_OK) {
                if (referrer != null && !referrer.isEmpty()) {
                    if (!consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
                        //consent was removed while reading: keep it for the next time consent is given
                        L.d("[ModuleAttribution] onInstallReferrerResult, 'attribution' consent was removed meanwhile, not sending");
                        return;
                    }
                    requestQueueProvider.sendAttributionSignal(TYPE_REFERRER, referrer);
                }
                putState(STATE_REFERRER_DONE, true);
                resolveFirstLaunch(referrer == null ? null : deepLinkFromReferrer(referrer), referrer);
                return;
            }

            if (status == InstallReferrerReader.STATUS_NOT_SUPPORTED) {
                putState(STATE_REFERRER_DONE, true);
                resolveFirstLaunch(null, null);
                return;
            }

            int attempts = state.optInt(STATE_REFERRER_ATTEMPTS, 0) + 1;
            if (attempts >= MAX_REFERRER_ATTEMPTS) {
                L.w("[ModuleAttribution] onInstallReferrerResult, the install referrer could not be read in " + attempts + " launches, giving up");
                putState(STATE_REFERRER_DONE, true);
                resolveFirstLaunch(null, null);
            } else {
                putState(STATE_REFERRER_ATTEMPTS, attempts);
            }
        }
    }

    /**
     * Records the first launch result, DEFERRED or NONE. It is reported once the launch activity was looked at
     * (deliverFirstLaunchIfReady): init usually runs in Application.onCreate, before that activity exists, and a
     * link that opened the app replaces it. Kept in storage, so a process without any activity (a push, a
     * service) leaves it for the next one.
     */
    void resolveFirstLaunch(@Nullable String deferredPath, @Nullable String referrer) {
        JSONObject state = readState();
        if (state.optBoolean(STATE_FIRST_LAUNCH_RESOLVED, false) || state.optBoolean(STATE_FIRST_LAUNCH_PENDING, false)) {
            return;
        }
        try {
            state.put(STATE_FIRST_LAUNCH_PENDING, true);
            if (deferredPath != null && !deferredPath.isEmpty()) {
                state.put(STATE_DEFERRED_PATH, deferredPath);
                state.putOpt(STATE_DEFERRED_URL, referrer);
            }
        } catch (JSONException ignored) {
        }
        writeState(state);
        deliverFirstLaunchIfReady();
    }

    void deliverFirstLaunchIfReady() {
        if (!launchIntentInspected) {
            return;
        }
        JSONObject state = readState();
        boolean pending = firstLaunchPendingInMemory || state.optBoolean(STATE_FIRST_LAUNCH_PENDING, false);
        if (!pending || state.optBoolean(STATE_FIRST_LAUNCH_RESOLVED, false)) {
            return;
        }
        firstLaunchPendingInMemory = false;
        String deferredPath = state.optString(STATE_DEFERRED_PATH, "");
        String referrer = state.optString(STATE_DEFERRED_URL, "");
        state.remove(STATE_FIRST_LAUNCH_PENDING);
        state.remove(STATE_DEFERRED_PATH);
        state.remove(STATE_DEFERRED_URL);
        try {
            state.put(STATE_FIRST_LAUNCH_RESOLVED, true);
        } catch (JSONException ignored) {
        }
        writeState(state);

        if (linkOpenedThisProcess) {
            //the link that opened the app was already reported, it is the more specific destination
            return;
        }
        if (!deferredPath.isEmpty()) {
            notifyDeepLink(DeepLinkCallback.Type.DEFERRED, deferredPath, referrer.isEmpty() ? null : referrer);
        } else {
            notifyDeepLink(DeepLinkCallback.Type.NONE, null, null);
        }
    }

    // ------------------------------------------------------------------ deep links

    void recordDeepLinkInternal(@Nullable Uri uri, boolean automatic) {
        if (uri == null || uri.toString().isEmpty()) {
            L.w("[ModuleAttribution] recordDeepLinkInternal, provided link is not valid. Execution will be aborted.");
            healthTracker.recordLogCode("w700");
            return;
        }
        String url = uri.toString();
        L.d("[ModuleAttribution] recordDeepLinkInternal, link:[" + url + "], automatic:[" + automatic + "]");

        //an automatically captured link is only sent when it is an attribution link: an app's own links can
        //carry one time tokens (password resets, sign in links) that have no business on the server
        if (!automatic || isAttributionLink(uri)) {
            if (consentProvider.getConsent(Countly.CountlyFeatureNames.attribution)) {
                requestQueueProvider.sendAttributionSignal(TYPE_DEEPLINK, url);
            } else {
                L.d("[ModuleAttribution] recordDeepLinkInternal, no 'attribution' consent, the link is not sent");
            }
        }

        linkOpenedThisProcess = true;
        notifyDeepLink(DeepLinkCallback.Type.OPENED, deepLinkPath(uri), url);
    }

    void captureLaunchLink(@Nullable Activity activity) {
        if (activity == null) {
            return;
        }
        recordLaunchLinkIfAny(activity);
        launchIntentInspected = true;
        deliverFirstLaunchIfReady();
    }

    void recordLaunchLinkIfAny(@NonNull Activity activity) {
        Intent intent;
        try {
            intent = activity.getIntent();
        } catch (Exception t) {
            return;
        }
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri data = intent.getData();
        if (data == null || !isLinkScheme(data.getScheme())) {
            return;
        }
        try {
            if (intent.getBooleanExtra(handledIntentExtra, false)) {
                return;
            }
            //marked on the intent itself, so the same link is not recorded again when the activity is
            //recreated (rotation) or restored after process death
            intent.putExtra(handledIntentExtra, true);
        } catch (Exception t) {
            //unparcelable extras from another app; the link is still recorded once for this resume
            L.w("[ModuleAttribution] captureLaunchLink, could not mark the intent: " + t);
        }
        recordDeepLinkInternal(data, true);
    }

    void notifyDeepLink(@NonNull DeepLinkCallback.Type type, @Nullable String path, @Nullable String url) {
        if (deepLinkCallback == null) {
            return;
        }
        try {
            deepLinkCallback.onDeepLink(type, path, url);
        } catch (Exception t) {
            L.e("[ModuleAttribution] notifyDeepLink, the deep link callback threw: " + t);
        }
    }

    static boolean isLinkScheme(@Nullable String scheme) {
        if (scheme == null || scheme.isEmpty()) {
            return false;
        }
        String s = scheme.toLowerCase(Locale.ROOT);
        //opening a document or a resource is not a link
        return !"content".equals(s) && !"file".equals(s) && !"android.resource".equals(s) && !"data".equals(s) && !"javascript".equals(s);
    }

    static boolean isSmartLink(@NonNull Uri uri) {
        List<String> segments = uri.isHierarchical() ? uri.getPathSegments() : null;
        return segments != null && segments.size() >= 2 && "at".equals(segments.get(0));
    }

    static boolean isAttributionLink(@NonNull Uri uri) {
        if (!uri.isHierarchical()) {
            return false;
        }
        if (isSmartLink(uri)) {
            return true;
        }
        for (String name : uri.getQueryParameterNames()) {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.startsWith("utm_") || "cly_clk".equals(n) || "countly_cid".equals(n) || "countly_cuid".equals(n)) {
                return true;
            }
        }
        return false;
    }

    static boolean isObjectId(@NonNull String s) {
        return s.matches("[0-9a-fA-F]{24}");
    }

    /**
     * Where a link wants to take the user: its {@code dl} parameter, otherwise for a smart link
     * {@code /at/[<app id>/]<code>/<path>} the path after the code, otherwise the link's path.
     */
    static @Nullable String deepLinkPath(@NonNull Uri uri) {
        if (!uri.isHierarchical()) {
            return null;
        }
        String dl = uri.getQueryParameter("dl");
        if (dl != null && !dl.isEmpty()) {
            return dl;
        }
        if (isSmartLink(uri)) {
            return smartLinkPath(uri.getPathSegments());
        }
        String path = uri.getPath();
        return path == null || path.isEmpty() ? null : path;
    }

    /**
     * The path after the campaign code of a smart link's segments ("at", [app id], code, path...), null
     * when there is none.
     */
    static @Nullable String smartLinkPath(@NonNull List<String> segments) {
        int codeIndex = segments.size() >= 3 && isObjectId(segments.get(1)) ? 2 : 1;
        if (segments.size() <= codeIndex + 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = codeIndex + 1; i < segments.size(); i++) {
            sb.append('/').append(segments.get(i));
        }
        return sb.toString();
    }

    /**
     * The {@code dl} parameter of a raw install referrer ("utm_source=x&cly_clk=y&dl=%2Fproduct").
     */
    static @Nullable String deepLinkFromReferrer(@NonNull String referrer) {
        try {
            String dl = Uri.parse("https://referrer.invalid/?" + referrer).getQueryParameter("dl");
            return dl == null || dl.isEmpty() ? null : dl;
        } catch (Exception t) {
            return null;
        }
    }

    void recordDirectAttributionInternal(@Nullable String campaignType, @Nullable String campaignData) {
        L.d("[ModuleAttribution] recordDirectAttributionInternal, campaign id:[" + campaignType + "], user id:[" + campaignData + "]");

        if (campaignType == null || campaignType.isEmpty()) {
            L.w("[ModuleAttribution] recordDirectAttributionInternal, provided campaign type value is not valid. Execution will be aborted.");
            healthTracker.recordLogCode("w700");
            return;
        }

        if (campaignData == null || campaignData.isEmpty()) {
            L.w("[ModuleAttribution] recordDirectAttributionInternal, provided campaign data value is not valid. Execution will be aborted.");
            healthTracker.recordLogCode("w700");
            return;
        }

        if (!campaignType.equals("countly") && !campaignType.equals("_special_test")) {
            //stop execution if the type is not "countly"
            //this is a temporary exception
            L.w("[ModuleAttribution] recordDirectAttributionInternal, recording direct attribution with a type other than 'countly' is currently not supported. Execution will be aborted.");
            healthTracker.recordLogCode("w700");
            return;
        }

        if (campaignType.equals("_special_test")) {
            reportSpecialTestAttribution(campaignData);
        }

        if (campaignType.equals("countly")) {
            reportLegacyInstallAttribution(campaignData);
        }
    }

    void reportLegacyInstallAttribution(@NonNull String campaignData) {
        JSONObject jObj;

        try {
            jObj = new JSONObject(campaignData);
        } catch (JSONException e) {
            L.w("[ModuleAttribution] recordDirectAttributionInternal, recording direct attribution data is not in the correct format. Execution will be aborted.");
            healthTracker.recordLogCode("w700");
            return;
        }

        if (!jObj.has("cid")) {
            L.w("[ModuleAttribution] recordDirectAttributionInternal, direct attribution can't be recorded because the data does not contain the 'cid' value. Execution will be aborted.");
            healthTracker.recordLogCode("w700");
            return;
        }

        String campaignId = null;
        try {
            campaignId = jObj.getString("cid");

            if (campaignId.isEmpty()) {
                L.w("[ModuleAttribution] recordDirectAttributionInternal, 'cid' value can't be empty string. Execution will be aborted.");
                healthTracker.recordLogCode("w700");
                return;
            }
        } catch (JSONException e) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, encountered issue while accessing 'cid'. Execution will be aborted.");
            return;
        }

        String campaignUserId = null;

        try {
            if (jObj.has("cuid")) {
                campaignUserId = jObj.getString("cuid");

                if (campaignUserId.isEmpty()) {
                    L.w("[ModuleAttribution] recordDirectAttributionInternal, 'cuid' value can't be empty string. value will be ignored.");
                    healthTracker.recordLogCode("w700");
                    campaignUserId = null;
                }
            }
        } catch (JSONException e) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, encountered issue while accessing 'cuid'. Execution will be aborted.");
            return;
        }

        requestQueueProvider.sendDirectAttributionLegacy(campaignId, campaignUserId);
    }

    void reportSpecialTestAttribution(@NonNull String attributionData) {
        requestQueueProvider.sendDirectAttributionTest(attributionData);
    }

    void recordIndirectAttributionInternal(@Nullable Map<String, String> attributionId) {
        L.d("[ModuleAttribution] recordIndirectAttributionInternal, attribution id:[" + attributionId + "]");

        if (attributionId == null || attributionId.isEmpty()) {
            L.w("[ModuleAttribution] recordIndirectAttributionInternal, provided id values are not valid. Execution will be aborted.");
            healthTracker.recordLogCode("w700");
            return;
        }

        JSONObject jObj = new JSONObject();
        for (Map.Entry<String, String> entry : attributionId.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isEmpty()) {
                L.w("[ModuleAttribution] recordIndirectAttributionInternal, provided key is not valid [" + key + "].");
                healthTracker.recordLogCode("w700");
                continue;
            }

            if (value == null || value.isEmpty()) {
                L.w("[ModuleAttribution] recordIndirectAttributionInternal, for the key[" + key + "] the provided value is not valid [" + value + "].");
                healthTracker.recordLogCode("w700");
                continue;
            }

            try {
                jObj.putOpt(key, value);
            } catch (JSONException e) {
                L.e("[ModuleAttribution] recordIndirectAttributionInternal, an issue happened while trying to add a value: " + e.toString());
            }
        }

        if (jObj.length() == 0) {
            L.w("[ModuleAttribution] recordIndirectAttributionInternal, no valid attribution values were provided");
            healthTracker.recordLogCode("w700");
            return;
        }

        String attributionObj = jObj.toString();

        requestQueueProvider.sendIndirectAttribution(attributionObj);
    }

    @Override
    void onInitialActivitySeeded(@NonNull Activity activity) {
        captureLaunchLink(activity);
    }

    @Override
    void callbackOnActivityResumed(Activity activity) {
        synchronized (_cly) {
            captureLaunchLink(activity);
        }
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        if (newConsent && consentChangeDelta.contains(Countly.CountlyFeatureNames.attribution)) {
            processInstallSignals();
        }
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        //check if any indirect attribution value is set
        if (config.iaAttributionValues != null) {
            if (config.iaAttributionValues.isEmpty()) {
                L.w("[ModuleAttribution] provided attribution ID for indirect attribution is empty string.");
                healthTracker.recordLogCode("w700");
            } else {
                recordIndirectAttributionInternal(config.iaAttributionValues);
            }
        }

        //checking if any direct attribution value is set
        if (config.daCampaignData != null || config.daCampaignType != null) {
            if (config.daCampaignType == null || config.daCampaignType.isEmpty()) {
                L.w("[ModuleAttribution] Can't record direct attribution can't be recorded with an invalid campaign id.");
                healthTracker.recordLogCode("w700");
            } else {
                if (config.daCampaignData != null && config.daCampaignData.isEmpty()) {
                    L.w("[ModuleAttribution] For direct attribution the provided Campaign user ID can't be empty string.");
                    healthTracker.recordLogCode("w700");
                }
                recordDirectAttributionInternal(config.daCampaignType, config.daCampaignData);
            }
        }

        processInstallSignals();
    }

    @Override
    public void halt() {
        synchronized (_cly) {
            halted = true;
        }
        attributionInterface = null;
    }

    public class Attribution {

        /**
         * Report direct user attribution
         */
        public void recordDirectAttribution(String campaignType, String campaignData) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordCampaign'");

                recordDirectAttributionInternal(campaignType, campaignData);
            }
        }

        /**
         * Report indirect user attribution
         *
         * @param attributionValues
         */
        public void recordIndirectAttribution(Map<String, String> attributionValues) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordIndirectAttribution'");

                recordIndirectAttributionInternal(attributionValues);
            }
        }

        /**
         * Report the link that opened the app, for example from {@code onNewIntent} of an activity that
         * is already running. Links in the launch intent of an activity are captured automatically.
         * The link is sent when the "attribution" consent is given; the deep link callback is called either way.
         *
         * @param uri the link that opened the app
         */
        public void recordDeepLink(Uri uri) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordDeepLink'");

                recordDeepLinkInternal(uri, false);
            }
        }
    }
}
