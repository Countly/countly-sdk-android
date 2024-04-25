package ly.count.android.demo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.ModuleFeedback;
import ly.count.android.sdk.ModuleFeedback.CountlyFeedbackWidget;
import ly.count.android.sdk.ModuleFeedback.FeedbackWidgetType;
import ly.count.android.sdk.StarRatingCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ActivityExampleFeedback extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_feedback);
    }

    public void onClickViewOther02(View v) {
        //show star rating
        Countly.sharedInstance().ratings().showStarRating(this, new StarRatingCallback() {
            @Override
            public void onRate(int rating) {
                Toast.makeText(ActivityExampleFeedback.this, "onRate called with rating: " + rating, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDismiss() {
                Toast.makeText(ActivityExampleFeedback.this, "onDismiss called", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onClickViewOther07(View v) {
        //show rating widget
        String widgetId = "614871419f030e44be07d82f";
        Countly.sharedInstance().ratings().presentRatingWidgetWithID(widgetId, "Close", this, error -> {
            if (error != null) {
                Toast.makeText(this, "Encountered error while showing feedback dialog: [" + error + "]", Toast.LENGTH_LONG).show();
            }

            Toast.makeText(this, "presentRatingWidgetWithID callback", Toast.LENGTH_LONG).show();
        });
    }

    public void onClickSendManualRating(View v) {
        //record rating manually without showing any UI
        String widgetId = "5f15c01425f83c169c33cb65";
        Countly.sharedInstance().ratings().recordRatingWidgetWithID(widgetId, 3, "foo@bar.garr", "Ragnaros should watch out", true);
    }

    // Checks if an error was received or the retrieved widget list is null. Would return true if all is kaput.
    boolean validateRetrievedFeedbackWidgetList(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
        if (error != null) {
            Toast.makeText(this, "Encountered error while getting a list of available feedback widgets: [" + error + "]", Toast.LENGTH_LONG).show();
            return true;
        }

        if (retrievedWidgets == null) {
            Toast.makeText(this, "Got a null widget list", Toast.LENGTH_LONG).show();
            return true;
        }

        return false;
    }

    // returns the first widget with the wanted type from the widget list
    CountlyFeedbackWidget widgetPicker(List<CountlyFeedbackWidget> retrievedWidgets, FeedbackWidgetType type) {
        for (CountlyFeedbackWidget widget : retrievedWidgets) {
            if (widget.type == type) {
                return widget;
            }
        }
        return null;
    }

    void GetAndShowFeedbackWidget(FeedbackWidgetType type) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets((retrievedWidgets, error) -> {
            if (validateRetrievedFeedbackWidgetList(retrievedWidgets, error)) {
                return;
            }

            CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, type);

            if (chosenWidget == null) {
                Toast.makeText(this, "No available Survey widget", Toast.LENGTH_LONG).show();
                return;
            }

            Countly.sharedInstance().feedback().presentFeedbackWidget(chosenWidget, this, "Close", new ModuleFeedback.FeedbackCallback() {
                @Override public void onClosed() {
                    Toast.makeText(ActivityExampleFeedback.this, "The feedback widget was closed", Toast.LENGTH_LONG).show();
                }

                @Override public void onFinished(String error) {
                    if (error != null) {
                        Toast.makeText(ActivityExampleFeedback.this, "Encountered error while presenting the feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
    }

    public void onClickShowSurvey(View v) {
        GetAndShowFeedbackWidget(FeedbackWidgetType.survey);
    }

    public void onClickShowNPS(View v) {
        GetAndShowFeedbackWidget(FeedbackWidgetType.nps);
    }

    public void onClickShowRating(View v) {
        GetAndShowFeedbackWidget(FeedbackWidgetType.rating);
    }

    public void onClickShowAvailableFeedbackWidgets(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets((retrievedWidgets, error) -> {
            if (validateRetrievedFeedbackWidgetList(retrievedWidgets, error)) {
                return;
            }

            StringBuilder sb = new StringBuilder();

            for (CountlyFeedbackWidget widget : retrievedWidgets) {
                sb.append("[").append(widget.widgetId).append(" ").append(widget.name).append(" ").append(widget.type).append("]\n");
            }

            Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
        });
    }

    public void GetDataForFirstWidgetOfType(FeedbackWidgetType type, FeedbackCallbacks callback) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets((retrievedWidgets, error) -> {
            if (validateRetrievedFeedbackWidgetList(retrievedWidgets, error)) {
                return;
            }

            CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, type);

            if (chosenWidget == null) {
                Toast.makeText(this, "No available NPS widget for manual reporting", Toast.LENGTH_LONG).show();
                return;
            }

            final CountlyFeedbackWidget widgetToReport = chosenWidget;

            Countly.sharedInstance().feedback().getFeedbackWidgetData(chosenWidget, (retrievedWidgetData, error1) -> {
                String val;
                if (type == FeedbackWidgetType.nps) {
                    val = "nps";
                } else if (type == FeedbackWidgetType.survey) {
                    val = "survey";
                } else {
                    val = "rating";
                }

                if (error1 != null) {
                    Toast.makeText(this, "Encountered error while reporting " + val + " feedback widget: [" + error1 + "]", Toast.LENGTH_LONG).show();
                    return;
                }
                Log.d(Countly.TAG, "Retrieved " + val + " widget data: " + retrievedWidgetData.toString());

                callback.onFinished(widgetToReport, retrievedWidgetData);
            });
        });
    }

    public interface FeedbackCallbacks {
        void onFinished(CountlyFeedbackWidget widgetToReport, JSONObject retrievedWidgetData);
    }

    public void onClickReportNPSManually(View v) {
        GetDataForFirstWidgetOfType(FeedbackWidgetType.nps, (widgetToReport, retrievedWidgetData) -> {
            //do note that error handling has already been taken care of at this point
            Map<String, Object> segm = new ConcurrentHashMap<>();
            segm.put("rating", 3);//value from 1 to 10
            segm.put("comment", "Filled out comment");
            Countly.sharedInstance().feedback().reportFeedbackWidgetManually(widgetToReport, retrievedWidgetData, segm);
            Toast.makeText(this, "NPS feedback reported manually", Toast.LENGTH_LONG).show();
        });
    }

    public void onClickReportRatingManually(View v) {
        GetDataForFirstWidgetOfType(FeedbackWidgetType.rating, (widgetToReport, retrievedWidgetData) -> {
            //do note that error handling has already been taken care of at this point
            Map<String, Object> segm = new ConcurrentHashMap<>();
            segm.put("rating", 3);//value from 1 to 5
            segm.put("comment", "Filled out comment");
            segm.put("email", "Filled out email");
            segm.put("contactMe", true);
            Countly.sharedInstance().feedback().reportFeedbackWidgetManually(widgetToReport, retrievedWidgetData, segm);
            Toast.makeText(this, "Rating feedback reported manually", Toast.LENGTH_LONG).show();
        });
    }

    public void onClickReportSurveyManually(View v) {
        GetDataForFirstWidgetOfType(FeedbackWidgetType.rating, (widgetToReport, retrievedWidgetData) -> {
            //do note that error handling has already been taken care of at this point
            JSONArray questions = retrievedWidgetData.optJSONArray("questions");

            if (questions == null) {
                Toast.makeText(this, "No questions found in retrieved survey data", Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> segm = new ConcurrentHashMap<>();
            Random rnd = new Random();

            //iterate over all questions and set random answers
            StringBuilder sb = new StringBuilder();
            for (int a = 0; a < questions.length(); a++) {
                JSONObject question;
                try {
                    question = questions.getJSONObject(a);
                } catch (JSONException e) {
                    Log.e(Countly.TAG, "Failed to get question from survey data" + e);
                    continue;
                }
                String wType = question.optString("type");
                String questionId = question.optString("id");
                String answerKey = "answ-" + questionId;
                JSONArray choices = question.optJSONArray("choices");
                if (choices == null) {
                    continue;
                }

                switch (wType) {
                    //multiple answer question
                    case "multi":
                        for (int b = 0; b < choices.length(); b++) {
                            if (b % 2 == 0) {//pick every other choice
                                String gonnaPick = choices.optJSONObject(b).optString("key");
                                if (b != 0) {
                                    gonnaPick = ", " + gonnaPick;
                                }
                                sb.append(gonnaPick);
                            }
                        }
                        segm.put(answerKey, sb.toString());
                        sb.setLength(0);
                        break;
                    //radio buttons
                    case "radio":
                        //dropdown value selector
                    case "dropdown":
                        int pick = rnd.nextInt(choices.length());
                        segm.put(answerKey, choices.optJSONObject(pick).optString("key"));//pick the key of random choice
                        break;
                    //text input field
                    case "text":
                        segm.put(answerKey, "Some random text");
                        break;
                    //rating picker
                    case "rating":
                        segm.put(answerKey, rnd.nextInt(11));//put a random rating
                        break;
                    default:
                        break;
                }
            }

            Countly.sharedInstance().feedback().reportFeedbackWidgetManually(widgetToReport, retrievedWidgetData, segm);

            Toast.makeText(this, "Survey feedback reported manually", Toast.LENGTH_LONG).show();
        });
    }

    public void onClickRetrieveSurveyDataManually(View v) {
        getAndPrintRetrievedFeedbackWidgetData(FeedbackWidgetType.survey);
    }

    public void onClickRetrieveNPSDataManually(View v) {
        getAndPrintRetrievedFeedbackWidgetData(FeedbackWidgetType.nps);
    }

    void getAndPrintRetrievedFeedbackWidgetData(final FeedbackWidgetType widgetType) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets((retrievedWidgets, error) -> {
            if (validateRetrievedFeedbackWidgetList(retrievedWidgets, error)) {
                return;
            }

            CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, widgetType);

            if (chosenWidget == null) {
                Toast.makeText(this, "No available survey widget for manual reporting", Toast.LENGTH_LONG).show();
                return;
            }

            Countly.sharedInstance().feedback().getFeedbackWidgetData(chosenWidget, (retrievedWidgetData, error1) -> {
                if (error1 != null) {
                    Toast.makeText(this, "Encountered error while reporting survey feedback widget: [" + error1 + "]", Toast.LENGTH_LONG).show();
                    return;
                }
                Log.d(Countly.TAG, "Retrieved survey widget data: " + retrievedWidgetData.toString());
                Toast.makeText(this, "Survey data retrieved: " + retrievedWidgetData, Toast.LENGTH_LONG).show();
            });
        });
    }
}
