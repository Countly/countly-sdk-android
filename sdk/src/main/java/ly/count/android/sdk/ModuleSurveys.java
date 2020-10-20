package ly.count.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModuleSurveys extends ModuleBase {

    enum SurveyType {basicSurvey, NPS, unknown}

    //data class
    static class SurveyEntry {
        String id;
        String surveyTypeRaw;
        SurveyType type;
    }

    ModuleSurveys.Surveys surveysInterface = null;

    ModuleSurveys(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleSurveys] Initialising");
        }

        surveysInterface = new ModuleSurveys.Surveys();
    }

    void showSurveyInternal(Activity activity, SurveyCallback callback, String closeButtonText) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSurveys] Calling 'showSurveyInternal'");
        }

        loadVisualizeSurveyList(activity, SurveyType.basicSurvey, closeButtonText, callback, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
            @Override public void callback(JSONObject checkResponse) {

            }
        });
    }

    void showNpsInternal(Activity activity, SurveyCallback devCallback, String closeButtonText) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSurveys] Calling 'showNpsInternal'");
        }

        loadVisualizeSurveyList(activity, SurveyType.NPS, closeButtonText, devCallback, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
            @Override public void callback(JSONObject checkResponse) {

            }
        });
    }

    void loadVisualizeSurveyList(final Activity activity, final SurveyType requiredSurveyType, final String closeButtonText, final SurveyCallback devCallback, final ImmediateRequestMaker.InternalFeedbackRatingCallback finishCallback) {
        if (activity == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleSurveys] Can't show survey, provided activity is null");
            }
            if (devCallback != null) {
                devCallback.onCompleted(false, "Can't show survey, provided activity is null");
            }
            return;
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.surveys)) {
            if (devCallback != null) {
                devCallback.onCompleted(false, "Consent is not granted");
            }
            return;
        }

        if (requiredSurveyType == SurveyType.unknown) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleSurveys] Can't show survey for a unknown type");
            }
            if (devCallback != null) {
                devCallback.onCompleted(false, "Can't show survey for a unknown type");
            }
            return;
        }

        ConnectionProcessor cp = _cly.connectionQueue_.createConnectionProcessor();

        String requestData = _cly.connectionQueue_.prepareSurveyListRequest(requiredSurveyType);

        (new ImmediateRequestMaker()).execute(requestData, "/o/sdk", cp, false, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
            @Override public void callback(JSONObject checkResponse) {
                if (checkResponse == null) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[ModuleSurveys] Not possible to show Survey/NPS, probably a lack of connection to the server");
                    }
                    if (devCallback != null) {
                        devCallback.onCompleted(false, "Not possible to show Survey/NPS popup, probably no internet connection");
                    }
                    return;
                }

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[ModuleSurveys] Retrieved request: [" + checkResponse.toString() + "]");
                }

                List<SurveyEntry> surveyEntries = parseSurveyList(checkResponse);
                SurveyEntry filteredEntry = null;

                //find first valid entry
                if (surveyEntries != null) {
                    for (SurveyEntry entry : surveyEntries) {
                        if (entry.type == requiredSurveyType) {
                            filteredEntry = entry;
                            break;
                        }
                    }
                }

                if (filteredEntry == null) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[ModuleSurveys] After filtering, no valid entry found");
                    }

                    if (devCallback != null) {
                        devCallback.onCompleted(false, null);
                    }

                    return;
                }

                visualizeSurvey(activity, filteredEntry, closeButtonText, requiredSurveyType, devCallback);
            }
        });
    }

    void visualizeSurvey(Activity activity, SurveyEntry survey, String closeButtonText, SurveyType surveyType, SurveyCallback devCallback) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSurveys] visualizeSurvey, type: [" + surveyType + "], callback set:[" + (devCallback != null) + ", survey id:[" + survey.id + "], survey type:[" + survey.type + "]");
        }

        String widgetListUrl = "";

        switch (survey.type) {
            case basicSurvey:
                //'/o/surveys/nps/widget?widget_ids=' + nps[0]._id
                //https://xxxx.count.ly/surveys/widget/nps?widget_id=5f8445c4eecf2a6de4dcb53e
                widgetListUrl = _cly.connectionQueue_.getServerURL() + "/survey?widget_id=" + survey.id;

                break;
            case NPS:
                widgetListUrl = _cly.connectionQueue_.getServerURL() + "/nps?widget_id=" + survey.id;
                break;
            case unknown:
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ModuleSurveys] visualizeSurvey, unknown survey type, cancelling");
                }
                if (devCallback != null) {
                    devCallback.onCompleted(false, "Unknown survey type encountered");
                }
                return;
        }

        

        widgetListUrl += "&device_id=" + _cly.connectionQueue_.getDeviceId().getId() + "&app_key=" + _cly.connectionQueue_.getAppKey();
        widgetListUrl += "&sdk_version=" + Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING + "&sdk_name=" + Countly.sharedInstance().COUNTLY_SDK_NAME;
        //device_id, app_key, app_version, sdk_version, sdk_name,

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSurveys] Using following url for widget:[" + widgetListUrl + "]");
        }

        if (activity == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleSurveys] visualizeSurvey, activity is null");
            }
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
            devCallback.onCompleted(true, null);
        }
    }

    List<SurveyEntry> parseSurveyList(JSONObject requestResponse) {
        List<SurveyEntry> parsedRes = null;
        try {
            if (requestResponse != null) {
                JSONArray jArray = requestResponse.optJSONArray("result");

                if (jArray != null) {
                    int len = jArray.length();

                    for (int a = 0; a < len; a++) {
                        try {
                            JSONObject jObj = jArray.getJSONObject(a);

                            String valId = jObj.optString("_id", "");
                            String valType = jObj.optString("type", "");

                            if (!valId.isEmpty() && !valType.isEmpty()) {
                                SurveyEntry se = new SurveyEntry();
                                se.surveyTypeRaw = valType;
                                se.id = valId;

                                switch (valType) {
                                    case "survey":
                                        se.type = SurveyType.basicSurvey;
                                        break;
                                    case "nps":
                                        se.type = SurveyType.NPS;
                                        break;
                                    default:
                                        se.type = SurveyType.unknown;
                                        break;
                                }

                                if (parsedRes == null) {
                                    parsedRes = new ArrayList<>();
                                }

                                parsedRes.add(se);
                            }
                        } catch (Exception ex) {
                            if (Countly.sharedInstance().isLoggingEnabled()) {
                                Log.e(Countly.TAG, "[ModuleSurveys] parseSurveyList, failed to parse json, [" + ex.toString() + "]");
                            }
                        }
                    }
                } else {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.v(Countly.TAG, "[ModuleSurveys] parseSurveyList, response does not have a valid 'result' entry");
                    }
                }
            }
        } catch (Exception ex) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleSurveys] parseSurveyList, Encountered exception while parsing survey list, [" + ex.toString() + "]");
            }
        }

        return parsedRes;
    }

    @Override
    void initFinished(CountlyConfig config) {

    }

    @Override
    void halt() {
        surveysInterface = null;
    }

    public class Surveys {
        public void showSurvey(Activity activity, SurveyCallback callback) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Surveys] Trying to show survey");
                }

                showSurveyInternal(activity, callback, "close");
            }
        }

        public void showNps(Activity activity, SurveyCallback callback) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Surveys] Trying to show NPS");
                }

                showNpsInternal(activity, callback, "close");
            }
        }
    }
}
