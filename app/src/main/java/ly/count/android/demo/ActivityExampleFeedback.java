package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.FeedbackRatingCallback;
import ly.count.android.sdk.ModuleFeedback.CountlyFeedbackWidget;
import ly.count.android.sdk.StarRatingCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static ly.count.android.sdk.ModuleFeedback.*;

public class ActivityExampleFeedback extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_feedback);
    }

    @Override
    public void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
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
        Countly.sharedInstance().ratings().presentRatingWidgetWithID(widgetId, "Close", ActivityExampleFeedback.this, new FeedbackRatingCallback() {
            @Override
            public void callback(String error) {
                if (error != null) {
                    Toast.makeText(ActivityExampleFeedback.this, "Encountered error while showing feedback dialog: [" + error + "]", Toast.LENGTH_LONG).show();
                }

                Toast.makeText(ActivityExampleFeedback.this, "presentRatingWidgetWithID callback", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void onClickSendManualRating(View v) {
        //record rating manually without showing any UI
        String widgetId = "5f15c01425f83c169c33cb65";
        Countly.sharedInstance().ratings().recordRatingWidgetWithID(widgetId, 3, "foo@bar.garr", "Ragnaros should watch out", true);
    }

    // Checks if an error was received or the retrieved widget list is null. Would return true if all is kaput.
    boolean didGotAvailableFeedbackWidgetsFaulty(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
        if (error != null) {
            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while getting a list of available feedback widgets: [" + error + "]", Toast.LENGTH_LONG).show();
            return true;
        }

        if (retrievedWidgets == null) {
            Toast.makeText(ActivityExampleFeedback.this, "Got a null widget list", Toast.LENGTH_LONG).show();
            return true;
        }

        return false;
    }

    // returns the first widget with the wanted type from the widget list
    CountlyFeedbackWidget widgetPicker(List<CountlyFeedbackWidget> retrievedWidgets,FeedbackWidgetType type){
        for (CountlyFeedbackWidget widget : retrievedWidgets) {
            if (widget.type == type) {
                return widget;
            }
        }
        return null;
    }

    public void onClickShowSurvey(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, FeedbackWidgetType.survey);

                if (chosenWidget == null) {
                    Toast.makeText(ActivityExampleFeedback.this, "No available Survey widget", Toast.LENGTH_LONG).show();
                    return;
                }

                Countly.sharedInstance().feedback().presentFeedbackWidget(chosenWidget, ActivityExampleFeedback.this, "Close", new FeedbackCallback() {
                    @Override public void onClosed() {
                        Toast.makeText(ActivityExampleFeedback.this, "The feedback widget was closed", Toast.LENGTH_LONG).show();
                    }

                    @Override public void onFinished(String error) {
                        if (error != null) {
                            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while presenting the feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    public void onClickShowNPS(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, FeedbackWidgetType.nps);

                if (chosenWidget == null) {
                    Toast.makeText(ActivityExampleFeedback.this, "No available NPS widget", Toast.LENGTH_LONG).show();
                    return;
                }

                Countly.sharedInstance().feedback().presentFeedbackWidget(chosenWidget, ActivityExampleFeedback.this, "Close", new FeedbackCallback() {
                    @Override public void onClosed() {
                        Toast.makeText(ActivityExampleFeedback.this, "The feedback widget was closed", Toast.LENGTH_LONG).show();
                    }

                    @Override public void onFinished(String error) {
                        if (error != null) {
                            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while presenting the feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    public void onClickShowRating(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, FeedbackWidgetType.rating);

                if (chosenWidget == null) {
                    Toast.makeText(ActivityExampleFeedback.this, "No available Rating widget", Toast.LENGTH_LONG).show();
                    return;
                }

                Countly.sharedInstance().feedback().presentFeedbackWidget(chosenWidget, ActivityExampleFeedback.this, "Close", new FeedbackCallback() {
                    @Override public void onClosed() {
                        Toast.makeText(ActivityExampleFeedback.this, "The feedback widget was closed", Toast.LENGTH_LONG).show();
                    }

                    @Override public void onFinished(String error) {
                        if (error != null) {
                            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while presenting the feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    public void onClickShowAvailableFeedbackWidgets(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                StringBuilder sb = new StringBuilder();

                for (CountlyFeedbackWidget widget : retrievedWidgets) {
                    sb.append("[" + widget.widgetId + " " + widget.name + " " + widget.type + "]\n");
                }

                Toast.makeText(ActivityExampleFeedback.this, sb.toString(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public void onClickReportNPSManually(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, FeedbackWidgetType.nps);

                if (chosenWidget == null) {
                    Toast.makeText(ActivityExampleFeedback.this, "No available NPS widget for manual reporting", Toast.LENGTH_LONG).show();
                    return;
                }

                final CountlyFeedbackWidget widgetToReport = chosenWidget;

                Countly.sharedInstance().feedback().getFeedbackWidgetData(chosenWidget, new RetrieveFeedbackWidgetData() {
                    @Override public void onFinished(JSONObject retrievedWidgetData, String error) {
                        if (error != null) {
                            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while reporting nps feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Log.d(Countly.TAG, "Retrieved nps widget data: " + retrievedWidgetData.toString());

                        Map<String, Object> segm = new HashMap<>();
                        segm.put("rating", 3);//value from 1 to 10
                        segm.put("comment", "Filled out comment");
                        Countly.sharedInstance().feedback().reportFeedbackWidgetManually(widgetToReport, retrievedWidgetData, segm);
                        Toast.makeText(ActivityExampleFeedback.this, "NPS feedback reported manually", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    public void onClickReportRatingManually(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, FeedbackWidgetType.rating);

                if (chosenWidget == null) {
                    Toast.makeText(ActivityExampleFeedback.this, "No available Rating widget for manual reporting", Toast.LENGTH_LONG).show();
                    return;
                }

                final CountlyFeedbackWidget widgetToReport = chosenWidget;

                Countly.sharedInstance().feedback().getFeedbackWidgetData(chosenWidget, new RetrieveFeedbackWidgetData() {
                    @Override public void onFinished(JSONObject retrievedWidgetData, String error) {
                        if (error != null) {
                            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while reporting rating feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Log.d(Countly.TAG, "Retrieved rating widget data: " + retrievedWidgetData.toString());

                        Map<String, Object> segm = new HashMap<>();
                        segm.put("rating", 3);//value from 1 to 5
                        segm.put("comment", "Filled out comment");
                        segm.put("email", "Filled out email");
                        segm.put("contactMe", true);
                        Countly.sharedInstance().feedback().reportFeedbackWidgetManually(widgetToReport, retrievedWidgetData, segm);
                        Toast.makeText(ActivityExampleFeedback.this, "Rating feedback reported manually", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    public void onClickReportSurveyManually(View v) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, FeedbackWidgetType.survey);

                if (chosenWidget == null) {
                    Toast.makeText(ActivityExampleFeedback.this, "No available survey widget for manual reporting", Toast.LENGTH_LONG).show();
                    return;
                }

                final CountlyFeedbackWidget widgetToReport = chosenWidget;

                Countly.sharedInstance().feedback().getFeedbackWidgetData(chosenWidget, new RetrieveFeedbackWidgetData() {
                    @Override public void onFinished(JSONObject retrievedWidgetData, String error) {
                        if (error != null) {
                            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while reporting survey feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Log.d(Countly.TAG, "Retrieved survey widget data: " + retrievedWidgetData.toString());

                        JSONArray questions = retrievedWidgetData.optJSONArray("questions");

                        if (questions == null) {
                            Toast.makeText(ActivityExampleFeedback.this, "No questions found in retrieved survey data", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Map<String, Object> segm = new HashMap<>();
                        Random rnd = new Random();

                        //iterate over all questions and set random answers
                        for (int a = 0; a < questions.length(); a++) {
                            JSONObject question = null;
                            try {
                                question = questions.getJSONObject(a);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            String wType = question.optString("type");
                            String questionId = question.optString("id");
                            String answerKey = "answ-" + questionId;
                            JSONArray choices = question.optJSONArray("choices");

                            switch (wType) {
                                //multiple answer question
                                case "multi":
                                    StringBuilder sb = new StringBuilder();

                                    for (int b = 0; b < choices.length(); b++) {
                                        if (b % 2 == 0) {//pick every other choice
                                            if (b != 0) {
                                                sb.append(",");
                                            }
                                            sb.append(choices.optJSONObject(b).optString("key"));
                                        }
                                    }
                                    segm.put(answerKey, sb.toString());
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
                            }
                        }

                        Countly.sharedInstance().feedback().reportFeedbackWidgetManually(widgetToReport, retrievedWidgetData, segm);

                        Toast.makeText(ActivityExampleFeedback.this, "Survey feedback reported manually", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    public void onClickRetrieveSurveyDataManually(View v) {
        getAndPrintRetrievedFeedbackWidgetData(FeedbackWidgetType.survey);
    }

    public void onClickRetrieveNPSDataManually(View v) {
        getAndPrintRetrievedFeedbackWidgetData(FeedbackWidgetType.nps);
    }

    void getAndPrintRetrievedFeedbackWidgetData(final FeedbackWidgetType widgetType) {
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<CountlyFeedbackWidget> retrievedWidgets, String error) {
                if(didGotAvailableFeedbackWidgetsFaulty(retrievedWidgets, error)){
                    return;
                }

                CountlyFeedbackWidget chosenWidget = widgetPicker(retrievedWidgets, widgetType);

                if (chosenWidget == null) {
                    Toast.makeText(ActivityExampleFeedback.this, "No available survey widget for manual reporting", Toast.LENGTH_LONG).show();
                    return;
                }

                Countly.sharedInstance().feedback().getFeedbackWidgetData(chosenWidget, new RetrieveFeedbackWidgetData() {
                    @Override public void onFinished(JSONObject retrievedWidgetData, String error) {
                        if (error != null) {
                            Toast.makeText(ActivityExampleFeedback.this, "Encountered error while reporting survey feedback widget: [" + error + "]", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Log.d(Countly.TAG, "Retrieved survey widget data: " + retrievedWidgetData.toString());
                        Toast.makeText(ActivityExampleFeedback.this, "Survey data retrieved: " + retrievedWidgetData, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}
