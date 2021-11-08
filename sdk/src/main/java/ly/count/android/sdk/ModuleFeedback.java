package ly.count.android.sdk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModuleFeedback extends ModuleBase {

    public enum FeedbackWidgetType {survey, nps}

    public static class CountlyFeedbackWidget {
        public String widgetId;
        public FeedbackWidgetType type;
        public String name;
    }

    final static String NPS_EVENT_KEY = "[CLY]_nps";
    final static String SURVEY_EVENT_KEY = "[CLY]_survey";

    final String cachedAppVersion;

    Feedback feedbackInterface = null;

    ModuleFeedback(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleFeedback] Initialising");

        cachedAppVersion = DeviceInfo.getAppVersion(config.context);

        feedbackInterface = new Feedback();
    }

    public interface RetrieveFeedbackWidgets {
        void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error);
    }

    public interface RetrieveFeedbackWidgetData {
        void onFinished(JSONObject retrievedWidgetData, String error);
    }

    public interface FeedbackCallback {
        void onClosed();
        void onFinished(String error);
    }

    void getAvailableFeedbackWidgetsInternal(final RetrieveFeedbackWidgets devCallback) {
        L.d("[ModuleFeedback] calling 'getAvailableFeedbackWidgetsInternal', callback set:[" + (devCallback != null) + "]");

        if (devCallback == null) {
            L.e("[ModuleFeedback] available feedback widget list can't be retrieved without a callback");
            return;
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
            devCallback.onFinished(null, "Consent is not granted");
            return;
        }

        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.e("[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            devCallback.onFinished(null, "[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            return;
        }

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();

        String requestData = requestQueueProvider.prepareFeedbackListRequest();

        (new ImmediateRequestMaker()).execute(requestData, "/o/sdk", cp, false, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
            @Override public void callback(JSONObject checkResponse) {
                if (checkResponse == null) {
                    L.d("[ModuleFeedback] Not possible to retrieve widget list. Probably due to lack of connection to the server");
                    devCallback.onFinished(null, "Not possible to retrieve widget list. Probably due to lack of connection to the server");
                    return;
                }

                L.d("[ModuleFeedback] Retrieved request: [" + checkResponse.toString() + "]");

                List<CountlyFeedbackWidget> feedbackEntries = parseFeedbackList(checkResponse);

                devCallback.onFinished(feedbackEntries, null);
            }
        }, L);
    }

    static List<CountlyFeedbackWidget> parseFeedbackList(JSONObject requestResponse) {
        Countly.sharedInstance().L.d("[ModuleFeedback] calling 'parseFeedbackList'");

        List<CountlyFeedbackWidget> parsedRes = new ArrayList<>();
        try {
            if (requestResponse != null) {
                JSONArray jArray = requestResponse.optJSONArray("result");

                if (jArray == null) {
                    Countly.sharedInstance().L.w("[ModuleFeedback] parseFeedbackList, response does not have a valid 'result' entry. No widgets retrieved.");
                    return parsedRes;
                }

                for (int a = 0; a < jArray.length(); a++) {
                    try {
                        JSONObject jObj = jArray.getJSONObject(a);

                        String valId = jObj.optString("_id", "");
                        String valType = jObj.optString("type", "");
                        String valName = jObj.optString("name", "");

                        if (valId.isEmpty()) {
                            Countly.sharedInstance().L.e("[ModuleFeedback] parseFeedbackList, retrieved invalid entry with null or empty widget id, dropping");
                            continue;
                        }

                        if (valType.isEmpty()) {
                            Countly.sharedInstance().L.e("[ModuleFeedback] parseFeedbackList, retrieved invalid entry with null or empty widget type, dropping");
                            continue;
                        }

                        FeedbackWidgetType plannedType;
                        switch (valType) {
                            case "survey":
                                plannedType = FeedbackWidgetType.survey;
                                break;
                            case "nps":
                                plannedType = FeedbackWidgetType.nps;
                                break;
                            default:
                                Countly.sharedInstance().L.e("[ModuleFeedback] parseFeedbackList, retrieved unknown widget type, dropping");
                                continue;
                        }

                        CountlyFeedbackWidget se = new CountlyFeedbackWidget();
                        se.type = plannedType;
                        se.widgetId = valId;
                        se.name = valName;

                        parsedRes.add(se);
                    } catch (Exception ex) {
                        Countly.sharedInstance().L.e("[ModuleFeedback] parseFeedbackList, failed to parse json, [" + ex.toString() + "]");
                    }
                }
            }
        } catch (Exception ex) {
            Countly.sharedInstance().L.e("[ModuleFeedback] parseFeedbackList, Encountered exception while parsing feedback list, [" + ex.toString() + "]");
        }

        return parsedRes;
    }

    void presentFeedbackWidgetInternal(@Nullable final CountlyFeedbackWidget widgetInfo, @Nullable final Context context, @Nullable final String closeButtonText, @Nullable final FeedbackCallback devCallback) {
        if (widgetInfo == null) {
            L.e("[ModuleFeedback] Can't present widget with null widget info");

            if (devCallback != null) {
                devCallback.onFinished("Can't present widget with null widget info");
            }
            return;
        }

        L.d("[ModuleFeedback] presentFeedbackWidgetInternal, callback set:[" + (devCallback != null) + ", widget id:[" + widgetInfo.widgetId + "], widget type:[" + widgetInfo.type + "]");

        if (context == null) {
            L.e("[ModuleFeedback] Can't show feedback, provided context is null");
            if (devCallback != null) {
                devCallback.onFinished("Can't show feedback, provided context is null");
            }
            return;
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
            if (devCallback != null) {
                devCallback.onFinished("Consent is not granted");
            }
            return;
        }

        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.e("[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            if (devCallback != null) {
                devCallback.onFinished("[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            }
            return;
        }

        StringBuilder widgetListUrl = new StringBuilder();

        switch (widgetInfo.type) {
            case survey:
                //'/o/feedback/nps/widget?widget_ids=' + nps[0]._id
                //https://xxxx.count.ly/feedback/nps?widget_id=5f8445c4eecf2a6de4dcb53e
                widgetListUrl.append(baseInfoProvider.getServerURL());
                widgetListUrl.append("/feedback/survey?widget_id=");
                widgetListUrl.append(UtilsNetworking.urlEncodeString(widgetInfo.widgetId));

                break;
            case nps:
                widgetListUrl.append(baseInfoProvider.getServerURL());
                widgetListUrl.append("/feedback/nps?widget_id=");
                widgetListUrl.append(UtilsNetworking.urlEncodeString(widgetInfo.widgetId));
                break;
        }

        widgetListUrl.append("&device_id=");
        widgetListUrl.append(UtilsNetworking.urlEncodeString(deviceIdProvider.getDeviceId()));
        widgetListUrl.append("&app_key=");
        widgetListUrl.append(UtilsNetworking.urlEncodeString(baseInfoProvider.getAppKey()));
        widgetListUrl.append("&sdk_version=");
        widgetListUrl.append(Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING);
        widgetListUrl.append("&sdk_name=");
        widgetListUrl.append(Countly.sharedInstance().COUNTLY_SDK_NAME);
        widgetListUrl.append("&platform=android");

        final String preparedWidgetUrl = widgetListUrl.toString();

        L.d("[ModuleFeedback] Using following url for widget:[" + widgetListUrl + "]");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //enable for chrome debugging
            //WebView.setWebContentsDebuggingEnabled(true);
        }

        final boolean useAlertDialog = true;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                L.d("[ModuleFeedback] Calling on main thread");

                try {

                    ModuleRatings.RatingDialogWebView webView = new ModuleRatings.RatingDialogWebView(context);
                    webView.getSettings().setJavaScriptEnabled(true);
                    webView.setWebViewClient(new ModuleRatings.FeedbackDialogWebViewClient());
                    webView.loadUrl(preparedWidgetUrl);
                    webView.requestFocus();

                    AlertDialog.Builder builder = prepareAlertDialog(context, webView, closeButtonText, widgetInfo, devCallback);

                    if (useAlertDialog) {
                        // use alert dialog to host the webView
                        L.d("[ModuleFeedback] Creating standalone Alert dialog");
                        builder.show();
                    } else {
                        // use dialog fragment to host the webView
                        L.d("[ModuleFeedback] Creating Alert dialog in dialogFragment");

                        //CountlyDialogFragment newFragment = CountlyDialogFragment.newInstance(builder);
                        //newFragment.show(fragmentManager, "CountlyFragmentDialog");
                    }

                    if (devCallback != null) {
                        devCallback.onFinished(null);
                    }
                } catch (Exception ex) {
                    L.e("[ModuleFeedback] Failed at displaying feedback widget dialog, [" + ex.toString() + "]");
                    if (devCallback != null) {
                        devCallback.onFinished("Failed at displaying feedback widget dialog, [" + ex.toString() + "]");
                    }
                }
            }
        });
    }

    AlertDialog.Builder prepareAlertDialog(@NonNull final Context context, @NonNull WebView webView, @Nullable String closeButtonText, @NonNull final CountlyFeedbackWidget widgetInfo, @Nullable final FeedbackCallback devCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(webView);
        builder.setCancelable(false);
        String usedCloseButtonText = closeButtonText;
        if (closeButtonText == null || closeButtonText.isEmpty()) {
            usedCloseButtonText = "Close";
        }
        builder.setNeutralButton(usedCloseButtonText, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialogInterface, int i) {
                L.d("[ModuleFeedback] Cancel button clicked for the feedback widget");
                reportFeedbackWidgetCancelButton(widgetInfo, DeviceInfo.getAppVersion(context));

                if(devCallback != null) {
                    devCallback.onClosed();
                }
            }
        });
        return builder;
    }

    void reportFeedbackWidgetCancelButton(@NonNull CountlyFeedbackWidget widgetInfo, @NonNull String appVersion) {
        L.d("[reportFeedbackWidgetCancelButton] Cancel button event");
        if (consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
            final Map<String, Object> segm = new HashMap<>();
            segm.put("platform", "android");
            segm.put("app_version", appVersion);
            segm.put("widget_id", "" + widgetInfo.widgetId);
            segm.put("closed", "1");
            final String key;

            if (widgetInfo.type == FeedbackWidgetType.survey) {
                key = SURVEY_EVENT_KEY;
            } else {
                key = NPS_EVENT_KEY;
            }

            eventProvider.recordEventInternal(key, segm, 1, 0, 0, null);
        }
    }

    /**
     * Downloads widget info and returns it to the callback
     *
     * @param widgetInfo identifies the specific widget for which you want to download widget data
     * @param devCallback mandatory callback in which the downloaded data will be returned
     */
    void getFeedbackWidgetDataInternal(@Nullable CountlyFeedbackWidget widgetInfo, @Nullable final RetrieveFeedbackWidgetData devCallback) {
        L.d("[ModuleFeedback] calling 'getFeedbackWidgetDataInternal', callback set:[" + (devCallback != null) + "]");

        if (devCallback == null) {
            L.e("[ModuleFeedback] Feedback widget data can't be retrieved without a callback");
            return;
        }

        if(widgetInfo == null) {
            L.e("[ModuleFeedback] Feedback widget data if provided widget is 'null'");
            return;
        }

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
            devCallback.onFinished(null, "Consent is not granted");
            return;
        }

        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.e("[ModuleFeedback] Feedback widget data can't be retrieved when in temporary device ID mode");
            devCallback.onFinished(null, "[ModuleFeedback] Feedback widget data can't be retrieved when in temporary device ID mode");
            return;
        }

        StringBuilder requestData = new StringBuilder();
        String widgetDataEndpoint = "";

        switch (widgetInfo.type) {
            case survey:
                //https://xxxx.count.ly/o/surveys/survey/widget?widget_id=601345cf5e313f74&shown=1platform=Android&app_version=7
                widgetDataEndpoint = "/o/surveys/survey/widget";
                break;
            case nps:
                //https://xxxx.count.ly/o/surveys/nps/widget?widget_id=601345cf5e313f74&shown=1platform=Android&app_version=7
                widgetDataEndpoint = "/o/surveys/nps/widget";
                break;
        }

        requestData.append("widget_id=");
        requestData.append(UtilsNetworking.urlEncodeString(widgetInfo.widgetId));
        requestData.append("&shown=1");
        requestData.append("&sdk_version=");
        requestData.append(Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING);
        requestData.append("&sdk_name=");
        requestData.append(Countly.sharedInstance().COUNTLY_SDK_NAME);
        requestData.append("&platform=android");
        requestData.append("&app_version=");
        requestData.append(cachedAppVersion);

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        String requestDataStr = requestData.toString();

        L.d("[ModuleFeedback] Using following request params for retrieving widget data:[" + requestDataStr + "]");

        (new ImmediateRequestMaker()).execute(requestDataStr, widgetDataEndpoint, cp, false, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
            @Override public void callback(JSONObject checkResponse) {
                if (checkResponse == null) {
                    L.d("[ModuleFeedback] Not possible to retrieve widget data. Probably due to lack of connection to the server");
                    devCallback.onFinished(null, "Not possible to retrieve widget data. Probably due to lack of connection to the server");
                    return;
                }

                L.d("[ModuleFeedback] Retrieved widget data request: [" + checkResponse.toString() + "]");

                devCallback.onFinished(checkResponse, null);
            }
        }, L);
    }

    /**
     * Report widget info and do data validation
     *
     * @param widgetInfo identifies the specific widget for which the feedback is filled out
     * @param widgetData widget data for this specific widget
     * @param widgetResult segmentation of the filled out feedback. If this segmentation is null, it will be assumed that the survey was closed before completion and mark it appropriately
     */
    void reportFeedbackWidgetManuallyInternal(@Nullable CountlyFeedbackWidget widgetInfo, @Nullable JSONObject widgetData, @Nullable Map<String, Object> widgetResult) {
        if (widgetInfo == null) {
            L.e("[ModuleFeedback] Can't report feedback widget data manually with 'null' widget info");
            return;
        }

        L.d("[ModuleFeedback] reportFeedbackWidgetManuallyInternal, widgetData set:[" + (widgetData != null) + ", widget id:[" + widgetInfo.widgetId + "], widget type:[" + widgetInfo.type + "], widget result set:[" + (widgetResult != null) + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
            L.w("[ModuleFeedback] Can't report feedback widget data, consent is not granted");
            return;
        }

        if (deviceIdProvider.isTemporaryIdEnabled()) {
            L.e("[ModuleFeedback] feedback widget result can't be reported when in temporary device ID mode");
            return;
        }

        if (widgetResult != null) {
            //removing broken values first
            Iterator<Map.Entry<String, Object>> iter = widgetResult.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, Object> entry = iter.next();
                if (entry.getKey() == null) {
                    L.w("[ModuleFeedback] provided feedback widget result contains a 'null' key, it will be removed, value[" + entry.getValue() + "]");
                    iter.remove();
                } else if (entry.getKey().isEmpty()) {
                    L.w("[ModuleFeedback] provided feedback widget result contains an empty string key, it will be removed, value[" + entry.getValue() + "]");
                    iter.remove();
                } else if (entry.getValue() == null) {
                    L.w("[ModuleFeedback] provided feedback widget result contains a 'null' value, it will be removed, key[" + entry.getKey() + "]");
                    iter.remove();
                }
            }

            if (widgetInfo.type == FeedbackWidgetType.nps) {
                //in case a nps widget was completed
                if (!widgetResult.containsKey("rating")) {
                    L.e("Provided NPS widget result does not have a 'rating' field, result can't be reported");
                    return;
                }

                //check rating data type
                Object ratingValue = widgetResult.get("rating");
                if (!(ratingValue instanceof Integer)) {
                    L.e("Provided NPS widget 'rating' field is not an integer, result can't be reported");
                    return;
                }

                //check rating value range
                int ratingValI = (int) ratingValue;
                if (ratingValI < 0 || ratingValI > 10) {
                    L.e("Provided NPS widget 'rating' value is out of bounds of the required value '[0;10]', it is probably an error");
                }

                if (!widgetResult.containsKey("comment")) {
                    L.w("Provided NPS widget result does not have a 'comment' field");
                }
            } else if (widgetInfo.type == FeedbackWidgetType.survey) {
                //in case a survey widget was completed
            }
        }

        if (widgetData == null) {
            L.d("[ModuleFeedback] reportFeedbackWidgetManuallyInternal, widgetInfo is 'null', no validation will be done");
        } else {
            //perform data validation

            String idInData = widgetData.optString("_id");

            if (!widgetInfo.widgetId.equals(idInData)) {
                L.w("[ModuleFeedback] id in widget info does not match the id in widget data");
            }

            String typeInData = widgetData.optString("type");

            if (widgetInfo.type == FeedbackWidgetType.nps) {
                if (!"nps".equals(typeInData)) {
                    L.w("[ModuleFeedback] type in widget info does not match the type in widget data");
                }
            } else if (widgetInfo.type == FeedbackWidgetType.survey) {
                if (!"survey".equals(typeInData)) {
                    L.w("[ModuleFeedback] type in widget info does not match the type in widget data");
                }
            }
        }

        final String usedEventKey;

        if (widgetInfo.type == FeedbackWidgetType.nps) {
            usedEventKey = NPS_EVENT_KEY;
            //event when closed
            //{"key":"[CLY]_nps","segmentation":{"widget_id":"600e9d2e563e892016316339","platform":"android","app_version":"0.0","closed":1},"timestamp":1611570486021,"hour":15,"dow":1}

            //event when answered
            //{"key":"[CLY]_nps","segmentation":{"widget_id":"600e9b24563e89201631631f","platform":"android","app_version":"0.0","rating":10,"comment":"Thanks"},"timestamp":1611570182023,"hour":15,"dow":1}
        } else if (widgetInfo.type == FeedbackWidgetType.survey) {
            usedEventKey = SURVEY_EVENT_KEY;

            //event when closed
            //{"key":"[CLY]_survey","segmentation":{"widget_id":"600e9e0b563e89201631633e","platform":"android","app_version":"0.0","closed":1},"timestamp":1611570709449,"hour":16,"dow":1}

            //event when answered
            //{"key":"[CLY]_survey","segmentation":{"widget_id":"600e9e0b563e89201631633e","platform":"android","app_version":"0.0","answ-1611570700-0":"ch1611570700-0"},"timestamp":1611570895465,"hour":16,"dow":1}
        } else {
            usedEventKey = "";
        }

        Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", cachedAppVersion);
        segm.put("widget_id", widgetInfo.widgetId);

        if (widgetResult == null) {
            //mark as closed
            segm.put("closed", "1");
        } else {
            //widget was filled out
            //merge given segmentation
            for (Map.Entry<String, Object> entry : widgetResult.entrySet()) {
                segm.put(entry.getKey(), entry.getValue());
            }
        }

        eventProvider.recordEventInternal(usedEventKey, segm, 1, 0, 0, null);
    }

    @Override
    void initFinished(CountlyConfig config) {

    }

    @Override
    void halt() {
        feedbackInterface = null;
    }

    public class Feedback {
        /**
         * Get a list of available feedback widgets for this device ID
         *
         * @param callback
         */
        public void getAvailableFeedbackWidgets(@Nullable RetrieveFeedbackWidgets callback) {
            synchronized (_cly) {
                L.i("[Feedback] Trying to retrieve feedback widget list");

                getAvailableFeedbackWidgetsInternal(callback);
            }
        }

        /**
         * Present a chosen feedback widget in an alert dialog
         *
         * @param widgetInfo
         * @param context
         * @param closeButtonText if this is null, no "close" button will be shown
         * @param devCallback
         */
        public void presentFeedbackWidget(@Nullable CountlyFeedbackWidget widgetInfo, @Nullable Context context, @Nullable String closeButtonText, @Nullable FeedbackCallback devCallback) {
            synchronized (_cly) {
                L.i("[Feedback] Trying to present feedback widget in an alert dialog");

                presentFeedbackWidgetInternal(widgetInfo, context, closeButtonText, devCallback);
            }
        }

        /**
         * Download data for a specific widget so that it can be displayed with a custom UI
         * When requesting this data, it will count as a shown widget (will increment that "shown" count in the dashboard)
         *
         * @param widgetInfo
         * @param callback
         */
        public void getFeedbackWidgetData(@Nullable CountlyFeedbackWidget widgetInfo, @Nullable RetrieveFeedbackWidgetData callback) {
            synchronized (_cly) {
                L.i("[Feedback] Trying to retrieve feedback widget data");

                getFeedbackWidgetDataInternal(widgetInfo, callback);
            }
        }

        /**
         * Manually report a feedback widget in case a custom interface was used
         * In case widgetResult is passed as "null", it would be assumed that the widget was cancelled
         *
         * @param widgetInfo
         * @param widgetData
         * @param widgetResult
         */
        public void reportFeedbackWidgetManually(@Nullable CountlyFeedbackWidget widgetInfo, @Nullable JSONObject widgetData, @Nullable Map<String, Object> widgetResult) {
            synchronized (_cly) {
                L.i("[Feedback] Trying to report feedback widget manually");

                reportFeedbackWidgetManuallyInternal(widgetInfo, widgetData, widgetResult);
            }
        }
    }
}
