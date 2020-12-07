package ly.count.android.sdk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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

    ModuleLog L;

    ModuleFeedback(Countly cly, CountlyConfig config) {
        super(cly);

        L = cly.L;

        L.v("[ModuleFeedback] Initialising");

        feedbackInterface = new Feedback();
    }

    public interface RetrieveFeedbackWidgets {
        void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error);
    }

    public interface FeedbackCallback {
        void onFinished(String error);
    }

    void getAvailableFeedbackWidgetsInternal(final RetrieveFeedbackWidgets devCallback) {
        L.d("[ModuleFeedback] calling 'getAvailableFeedbackWidgetsInternal', callback set:[" + (devCallback != null) + "]");

        if (devCallback == null) {
            L.e("[ModuleFeedback] available feedback widget list can't be retrieved without a callback");
            return;
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.feedback)) {
            devCallback.onFinished(null, "Consent is not granted");
            return;
        }

        if (_cly.connectionQueue_.getDeviceId().temporaryIdModeEnabled()) {
            L.e("[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            devCallback.onFinished(null, "[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            return;
        }

        ConnectionProcessor cp = _cly.connectionQueue_.createConnectionProcessor();

        String requestData = _cly.connectionQueue_.prepareFeedbackListRequest();

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
        }, _cly.L);
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

    void presentFeedbackWidgetInternal(final CountlyFeedbackWidget widgetInfo, final Context context, final String closeButtonText, final FeedbackCallback devCallback) {
        if (widgetInfo == null) {
            L.e("[ModuleFeedback] Can't present widget with null widget info");

            if (devCallback != null) {
                devCallback.onFinished("Can't present widget with null widget info");
            }
            return;
        }

        L.d("[ModuleFeedback] presentFeedbackWidgetInternal, callback set:[" + (devCallback != null) + ", feedback id:[" + widgetInfo.widgetId + "], feedback type:[" + widgetInfo.type + "]");

        if (context == null) {
            L.e("[ModuleFeedback] Can't show feedback, provided context is null");
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
            L.e("[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            devCallback.onFinished("[ModuleFeedback] available feedback widget list can't be retrieved when in temporary device ID mode");
            return;
        }

        StringBuilder widgetListUrl = new StringBuilder();

        switch (widgetInfo.type) {
            case survey:
                //'/o/feedback/nps/widget?widget_ids=' + nps[0]._id
                //https://xxxx.count.ly/feedback/nps?widget_id=5f8445c4eecf2a6de4dcb53e
                widgetListUrl.append(_cly.connectionQueue_.getServerURL());
                widgetListUrl.append("/feedback/survey?widget_id=");
                widgetListUrl.append(UtilsNetworking.urlEncodeString(widgetInfo.widgetId));

                break;
            case nps:
                widgetListUrl.append(_cly.connectionQueue_.getServerURL());
                widgetListUrl.append("/feedback/nps?widget_id=");
                widgetListUrl.append(UtilsNetworking.urlEncodeString(widgetInfo.widgetId));
                break;
        }

        widgetListUrl.append("&device_id=");
        widgetListUrl.append(UtilsNetworking.urlEncodeString(_cly.connectionQueue_.getDeviceId().getId()));
        widgetListUrl.append("&app_key=");
        widgetListUrl.append(UtilsNetworking.urlEncodeString(_cly.connectionQueue_.getAppKey()));
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

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                L.d("[ModuleFeedback] Calling on main thread");

                ModuleRatings.RatingDialogWebView webView = new ModuleRatings.RatingDialogWebView(context);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.setWebViewClient(new ModuleRatings.FeedbackDialogWebViewClient());
                webView.loadUrl(preparedWidgetUrl);
                webView.requestFocus();

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

                        if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.feedback)) {
                            Map<String, Object> segm = new HashMap<>();
                            segm.put("platform", "android");
                            segm.put("app_version", DeviceInfo.getAppVersion(context));
                            segm.put("widget_id", "" + widgetInfo.widgetId);
                            segm.put("closed", "1");

                            final String key;

                            if (widgetInfo.type == FeedbackWidgetType.survey) {
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
        });
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
        public void getAvailableFeedbackWidgets(RetrieveFeedbackWidgets callback) {
            synchronized (_cly) {
                L.i("[Feedback] Trying to retrieve feedback widget list");

                getAvailableFeedbackWidgetsInternal(callback);
            }
        }

        /**
         * Present a chosen feedback widget
         *
         * @param widgetInfo
         * @param context
         * @param closeButtonText if this is null, no "close" button will be shown
         * @param devCallback
         */
        public void presentFeedbackWidget(CountlyFeedbackWidget widgetInfo, Context context, String closeButtonText, FeedbackCallback devCallback) {
            synchronized (_cly) {
                L.i("[Feedback] Trying to present feedback widget");

                presentFeedbackWidgetInternal(widgetInfo, context, closeButtonText, devCallback);
            }
        }
    }
}
