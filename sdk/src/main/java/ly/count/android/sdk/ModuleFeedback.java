package ly.count.android.sdk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
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

    Feedback feedbackInterface = null;

    ModuleFeedback(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleFeedback] Initialising");
        }

        feedbackInterface = new Feedback();
    }

    public interface RetrieveFeedbackWidgets{
        void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error);
    }

    public interface FeedbackCallback {
        void onFinished(String error);
    }

    void getAvailableFeedbackWidgetsInternal(final RetrieveFeedbackWidgets devCallback) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleFeedback] calling 'getAvailableFeedbackWidgetsInternal', callback set:[" + (devCallback != null) + "]");
        }

        if(devCallback == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleFeedback] available feedback widget list can't be retrieved without a callback");
            }
            return;
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.feedback)) {
            devCallback.onFinished(null, "Consent is not granted");
            return;
        }

        if (_cly.connectionQueue_.getDeviceId().temporaryIdModeEnabled()) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            }
            devCallback.onFinished(null, "[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            return;
        }

        ConnectionProcessor cp = _cly.connectionQueue_.createConnectionProcessor();

        String requestData = _cly.connectionQueue_.prepareFeedbackListRequest();

        (new ImmediateRequestMaker()).execute(requestData, "/o/sdk", cp, false, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
            @Override public void callback(JSONObject checkResponse) {
                if (checkResponse == null) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[ModuleFeedback] Not possible to retrieve widget list. Probably due to lack of connection to the server");
                    }
                    devCallback.onFinished(null, "Not possible to retrieve widget list. Probably due to lack of connection to the server");
                    return;
                }

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[ModuleFeedback] Retrieved request: [" + checkResponse.toString() + "]");
                }

                List<CountlyFeedbackWidget> feedbackEntries = parseFeedbackList(checkResponse);

                devCallback.onFinished(feedbackEntries, null);
            }
        });
    }

    static List<CountlyFeedbackWidget> parseFeedbackList(JSONObject requestResponse) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleFeedback] calling 'parseFeedbackList'");
        }

        List<CountlyFeedbackWidget> parsedRes = new ArrayList<>();
        try {
            if (requestResponse != null) {
                JSONArray jArray = requestResponse.optJSONArray("result");

                if(jArray == null) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "[ModuleFeedback] parseFeedbackList, response does not have a valid 'result' entry. No widgets retrieved.");
                    }
                    return parsedRes;
                }

                for (int a = 0; a < jArray.length(); a++) {
                    try {
                        JSONObject jObj = jArray.getJSONObject(a);

                        String valId = jObj.optString("_id", "");
                        String valType = jObj.optString("type", "");
                        String valName = jObj.optString("name", "");

                        if(valId.isEmpty()) {
                            if (Countly.sharedInstance().isLoggingEnabled()) {
                                Log.e(Countly.TAG, "[ModuleFeedback] parseFeedbackList, retrieved invalid entry with null or empty widget id, dropping");
                            }
                            continue;
                        }

                        if(valType.isEmpty()) {
                            if (Countly.sharedInstance().isLoggingEnabled()) {
                                Log.e(Countly.TAG, "[ModuleFeedback] parseFeedbackList, retrieved invalid entry with null or empty widget type, dropping");
                            }
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
                                if (Countly.sharedInstance().isLoggingEnabled()) {
                                    Log.e(Countly.TAG, "[ModuleFeedback] parseFeedbackList, retrieved unknown widget type, dropping");
                                }
                                continue;
                        }

                        CountlyFeedbackWidget se = new CountlyFeedbackWidget();
                        se.type = plannedType;
                        se.widgetId = valId;
                        se.name = valName;

                        parsedRes.add(se);
                    } catch (Exception ex) {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.e(Countly.TAG, "[ModuleFeedback] parseFeedbackList, failed to parse json, [" + ex.toString() + "]");
                        }
                    }
                }
            }
        } catch (Exception ex) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleFeedback] parseFeedbackList, Encountered exception while parsing feedback list, [" + ex.toString() + "]");
            }
        }

        return parsedRes;
    }

    void presentFeedbackWidgetInternal(final CountlyFeedbackWidget widgetInfo, final Context context, String closeButtonText, FeedbackCallback devCallback) {
        if(widgetInfo == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleFeedback] Can't present widget with null widget info");
            }

            if(devCallback != null) {
                devCallback.onFinished("Can't present widget with null widget info");
            }
            return;
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleFeedback] presentFeedbackWidgetInternal, callback set:[" + (devCallback != null) + ", feedback id:[" + widgetInfo.widgetId + "], feedback type:[" + widgetInfo.type + "]");
        }

        if (context == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleFeedback] Can't show feedback, provided context is null");
            }
            if (devCallback != null) {
                devCallback.onFinished("Can't show feedback, provided context is null");
            }
            return;
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.feedback)) {
            if (devCallback != null) {
                devCallback.onFinished("Consent is not granted");
            }
            return;
        }

        if (_cly.connectionQueue_.getDeviceId().temporaryIdModeEnabled()) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            }
            devCallback.onFinished("[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            return;
        }

        String widgetListUrl = "";

        switch (widgetInfo.type) {
            case survey:
                //'/o/feedback/nps/widget?widget_ids=' + nps[0]._id
                //https://xxxx.count.ly/feedback/nps?widget_id=5f8445c4eecf2a6de4dcb53e
                widgetListUrl = _cly.connectionQueue_.getServerURL() + "/feedback/survey?widget_id=" + UtilsNetworking.urlEncodeString(widgetInfo.widgetId);

                break;
            case nps:
                widgetListUrl = _cly.connectionQueue_.getServerURL() + "/feedback/nps?widget_id=" + UtilsNetworking.urlEncodeString(widgetInfo.widgetId);
                break;
        }

        widgetListUrl += "&device_id=" + UtilsNetworking.urlEncodeString(_cly.connectionQueue_.getDeviceId().getId()) + "&app_key=" + UtilsNetworking.urlEncodeString(_cly.connectionQueue_.getAppKey());
        widgetListUrl += "&sdk_version=" + Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING + "&sdk_name=" + Countly.sharedInstance().COUNTLY_SDK_NAME;
        //device_id, app_key, app_version, sdk_version, sdk_name,

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleFeedback] Using following url for widget:[" + widgetListUrl + "]");
        }

        ModuleRatings.RatingDialogWebView webView = new ModuleRatings.RatingDialogWebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new ModuleRatings.FeedbackDialogWebViewClient());
        webView.loadUrl(widgetListUrl);
        webView.requestFocus();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(webView);
        builder.setCancelable(false);

        if (closeButtonText == null || closeButtonText.isEmpty()) {
            closeButtonText = "Close";
        }

        builder.setNeutralButton(closeButtonText, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialogInterface, int i) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[ModuleFeedback] Cancel button clicked for the feedback widget");
                }

                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.feedback)) {
                    Map<String, Object> segm = new HashMap<>();
                    segm.put("platform", "android");
                    segm.put("app_version", DeviceInfo.getAppVersion(context));
                    segm.put("widget_id", "" + widgetInfo.widgetId);
                    segm.put("closed", "1");

                    final String key;

                    if(widgetInfo.type == FeedbackWidgetType.survey) {
                        key = SURVEY_EVENT_KEY;
                    } else {
                        key = NPS_EVENT_KEY;
                    }

                    _cly.moduleEvents.recordEventInternal(key, segm, 1, 0, 0, null, false);
                }
            }
        });

        builder.show();

        if (devCallback != null) {
            devCallback.onFinished(null);
        }
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
          * @param callback
         */
        public void getAvailableFeedbackWidgets(RetrieveFeedbackWidgets callback) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Feedback] Trying to retrieve feedback widget list");
                }

                getAvailableFeedbackWidgetsInternal(callback);
            }
        }

        /**
         * Present a chosen feedback widget
         * @param widgetInfo
         * @param context
         * @param closeButtonText if this is null, no "close" button will be shown
         * @param devCallback
         */
        public void presentFeedbackWidget(CountlyFeedbackWidget widgetInfo, Context context, String closeButtonText, FeedbackCallback devCallback) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Feedback] Trying to present feedback widget");
                }

                presentFeedbackWidgetInternal(widgetInfo, context, closeButtonText,  devCallback);
            }
        }
    }
}
