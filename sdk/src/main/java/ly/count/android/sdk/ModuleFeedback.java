package ly.count.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModuleFeedback extends ModuleBase {

    public enum SurveyType {survey, nps}

    public static class CountlyPresentableFeedback {
        public String widgetId;
        public SurveyType type;
    }

    Feedback feedbackInterface = null;

    ModuleFeedback(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleFeedback] Initialising");
        }

        feedbackInterface = new Feedback();
    }

    public interface RetrieveFeedbackWidgets{
        void onFinished(List<CountlyPresentableFeedback> retrievedWidgets, String error);
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

        if (!_cly.getConsent(Countly.CountlyFeatureNames.surveys)) {
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

        String requestData = _cly.connectionQueue_.prepareSurveyListRequest();

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

                List<CountlyPresentableFeedback> feedbackEntries = parseFeedbackList(checkResponse);

                devCallback.onFinished(feedbackEntries, null);
            }
        });
    }

    List<CountlyPresentableFeedback> parseFeedbackList(JSONObject requestResponse) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleFeedback] calling 'parseSurveyList'");
        }

        List<CountlyPresentableFeedback> parsedRes = new ArrayList<>();
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

                        SurveyType plannedType;
                        switch (valType) {
                            case "survey":
                                plannedType = SurveyType.survey;
                                break;
                            case "nps":
                                plannedType = SurveyType.nps;
                                break;
                            default:
                                if (Countly.sharedInstance().isLoggingEnabled()) {
                                    Log.e(Countly.TAG, "[ModuleFeedback] parseFeedbackList, retrieved unknown widget type, dropping");
                                }
                                continue;
                        }

                        CountlyPresentableFeedback se = new CountlyPresentableFeedback();
                        se.type = plannedType;
                        se.widgetId = valId;

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

    void presentFeedbackWidgetInternal(CountlyPresentableFeedback widgetInfo, Activity activity, String closeButtonText, FeedbackCallback devCallback) {
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
            Log.d(Countly.TAG, "[ModuleFeedback] presentFeedbackWidgetInternal, callback set:[" + (devCallback != null) + ", survey id:[" + widgetInfo.widgetId + "], survey type:[" + widgetInfo.type + "]");
        }

        if (activity == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleFeedback] Can't show survey, provided activity is null");
            }
            if (devCallback != null) {
                devCallback.onFinished("Can't show survey, provided activity is null");
            }
            return;
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.surveys)) {
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
                //'/o/surveys/nps/widget?widget_ids=' + nps[0]._id
                //https://xxxx.count.ly/surveys/widget/nps?widget_id=5f8445c4eecf2a6de4dcb53e
                widgetListUrl = _cly.connectionQueue_.getServerURL() + "/feedback/survey?widget_id=" + widgetInfo.widgetId;

                break;
            case nps:
                widgetListUrl = _cly.connectionQueue_.getServerURL() + "/feedback/nps?widget_id=" + widgetInfo.widgetId;
                break;
        }

        widgetListUrl += "&device_id=" + _cly.connectionQueue_.getDeviceId().getId() + "&app_key=" + _cly.connectionQueue_.getAppKey();
        widgetListUrl += "&sdk_version=" + Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING + "&sdk_name=" + Countly.sharedInstance().COUNTLY_SDK_NAME;
        //device_id, app_key, app_version, sdk_version, sdk_name,

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleFeedback] Using following url for widget:[" + widgetListUrl + "]");
        }

        ModuleRatings.RatingDialogWebView webView = new ModuleRatings.RatingDialogWebView(activity);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new ModuleRatings.FeedbackDialogWebViewClient());
        webView.loadUrl(widgetListUrl);
        webView.requestFocus();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(webView);

        if (closeButtonText != null && !closeButtonText.isEmpty()) {
            builder.setNeutralButton(closeButtonText, null);
        }

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
         * @param activity
         * @param closeButtonText if this is null, no "close" button will be shown
         * @param devCallback
         */
        public void presentFeedbackWidget(CountlyPresentableFeedback widgetInfo, Activity activity, String closeButtonText, FeedbackCallback devCallback) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Feedback] Trying to present feedback widget");
                }

                presentFeedbackWidgetInternal(widgetInfo, activity, closeButtonText,  devCallback);
            }
        }
    }
}
