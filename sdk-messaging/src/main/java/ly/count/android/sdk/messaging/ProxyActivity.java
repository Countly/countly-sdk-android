package ly.count.android.sdk.messaging;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ly.count.android.sdk.Countly;

import static ly.count.android.sdk.messaging.CountlyMessaging.PROPERTY_ADD_METADATA_TO_PUSH_INTENTS;
import static ly.count.android.sdk.messaging.CountlyMessaging.getGCMPreferences;

/**
* Created by artem on 14/10/14.
*/
public class ProxyActivity extends Activity {

    @SuppressWarnings("EmptyMethod")
    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    protected void onNewIntent (Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onStart () {
        super.onStart();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            final Message msg = extras.getParcelable(CountlyMessaging.EXTRA_MESSAGE);
            final boolean addMetadata = getGCMPreferences(this).getBoolean(PROPERTY_ADD_METADATA_TO_PUSH_INTENTS, false);

            if (msg != null) {
                if (extras.containsKey(CountlyMessaging.NOTIFICATION_SHOW_DIALOG)) {
                    //if the message should show a notification dialog

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(msg.getNotificationTitle(this));
                    builder.setCancelable(true)
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    finish();
                                }
                            });
                    if (msg.hasMedia()) {
                        //if rich media has to be shown

                        addButtons(builder, msg, addMetadata);

                        LinearLayout layout = new LinearLayout(this);
                        layout.setBackgroundColor(Color.TRANSPARENT);
                        layout.setOrientation(LinearLayout.VERTICAL);

                        ImageView imageView = new ImageView(this);
                        imageView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));
                        if (msg.hasMedia() && Message.getFromStore(msg.getMedia()) != null) {
                            imageView.setImageBitmap((Bitmap)Message.getFromStore(msg.getMedia()));
                        }
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imageView.setAdjustViewBounds(true);
                        layout.addView(imageView);

                        if (msg.hasMessage()) {
                            int padding = (int) (10 * getResources().getDisplayMetrics().density + 0.5f);

                            TextView textview = new TextView(this);
                            textview.setText(msg.getMessage());
                            textview.setPadding(padding, padding, padding, padding);
                            layout.addView(textview);
                        }

                        builder.setView(layout);
                    } else if (msg.hasLink()) {
                        //if a link has to be shown

                        if (msg.hasMessage()){
                            //if the notification, has a message, add it
                            builder.setMessage(msg.getNotificationMessage());
                        }
                        if (msg.hasButtons()) {
                            //if buttons are provided
                            addButtons(builder, msg, addMetadata);
                        } else {
                            builder.setPositiveButton(CountlyMessaging.buttonNames[0], new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //record server side that the message was received
                                    CountlyMessaging.recordMessageAction(msg.getId(), 0);
                                    finish();
                                    Intent activityIntent = msg.getIntent(ProxyActivity.this, CountlyMessaging.getActivityClass(ProxyActivity.this));
                                    addExtrasToIntent(activityIntent, addMetadata, 1, msg.getLink(), msg.getTitle(), msg.getMessage());
                                    //todo add extra's here
                                    //Countly._internalNotifyAboutPushClickEvent(1, msg.getLink(), msg.getTitle(), msg.getMessage());
                                    if (activityIntent != null) {
                                        startActivity(activityIntent);
                                    }
                                }
                            });
                        }
                    } else if (msg.hasMessage()) {
                        //if only a message has to be shown

                        if (msg.hasButtons()) {
                            //if buttons are provided
                            addButtons(builder, msg, addMetadata);
                        } else {
                            //record server side that the message was received
                            CountlyMessaging.recordMessageAction(msg.getId());
                        }
                        builder.setMessage(msg.getNotificationMessage());
                        builder.setCancelable(true);
                        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                finish();
                            }
                        });
                    } else {
                        throw new IllegalStateException("Countly Message with UNKNOWN type in ProxyActivity");
                    }

                    builder.create().show();
                } else {
                    //show message in the notification bar

                    //try one way
                    Intent activityIntent = msg.getIntent(this, CountlyMessaging.getActivityClass(this));
                    if (activityIntent != null) {
                        addExtrasToIntent(activityIntent, addMetadata, -1, msg.getLink(), msg.getTitle(), msg.getMessage());
                        startActivity(activityIntent);
                    } else {
                        Log.e(Countly.TAG, "Countly Message with UNKNOWN type in ProxyActivity");
//                        throw new IllegalStateException("Countly Message with UNKNOWN type in ProxyActivity");
                    }

                    //first one did not work, try another
                    if (extras.containsKey(CountlyMessaging.EXTRA_ACTION_INDEX)) {
                        CountlyMessaging.recordMessageAction(msg.getId(), extras.getInt(CountlyMessaging.EXTRA_ACTION_INDEX));
                        for (Message.Button button : msg.getButtons()) {
                            if (button.index == extras.getInt(CountlyMessaging.EXTRA_ACTION_INDEX)) {
                                Intent targetIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(button.link));
                                int buttonIndex = msg.getButtons().indexOf(button);
                                addExtrasToIntent(targetIntent, addMetadata, buttonIndex, button.link, msg.getTitle(), msg.getMessage());

                                startActivity(targetIntent);
                                break;
                            }
                        }
                    } else {
                        CountlyMessaging.recordMessageAction(msg.getId(), 0);
                        if (msg.hasLink()) {
                            Intent targetIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getLink()));
                            addExtrasToIntent(targetIntent, addMetadata, 0, msg.getLink(), msg.getTitle(), msg.getMessage());
                            startActivity(targetIntent);
                        }
                    }
                    finish();
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(msg.getId().hashCode());
                }
            }
        }
    }

    private void addButtons(final AlertDialog.Builder builder, final Message msg, final boolean addMetadata) {
        //add buttons to the dialog

        if (msg.hasButtons()) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CountlyMessaging.recordMessageAction(msg.getId(), which == DialogInterface.BUTTON_POSITIVE ? 1 : 2);
                    //todo add extra's here
                    //
                    String buttonLink = msg.getButtons().get(which == DialogInterface.BUTTON_POSITIVE ? 0 : 1).link;

                    Intent targetIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(buttonLink));
                    addExtrasToIntent(targetIntent, addMetadata, which == DialogInterface.BUTTON_POSITIVE ? 1 : 2, buttonLink, msg.getTitle(), msg.getMessage());
                    startActivity(targetIntent);
                    finish();
                }
            };
            builder.setPositiveButton(msg.getButtons().get(0).title, listener);
            if (msg.getButtons().size() > 1) {
                builder.setNeutralButton(msg.getButtons().get(1).title, listener);
            }
        }
    }

    public final static String intentExtraWhichButton = "ly.count.android.api.messaging.intent.extra.which.button";
    public final static String intentExtraButtonLink = "ly.count.android.api.messaging.intent.extra.button.link";
    public final static String intentExtraMessageTitle = "ly.count.android.api.messaging.intent.extra.message.title";
    public final static String intentExtraMessageText = "ly.count.android.api.messaging.intent.extra.message.text";

    private void addExtrasToIntent(final Intent intent, final boolean addMetadata, final int whichButton, final String buttonLink, final String messageTitle, final String messageText){
        if(addMetadata) {
            if (intent != null) {
                intent.putExtra(intentExtraWhichButton, whichButton);

                if (buttonLink != null) {
                    intent.putExtra(intentExtraButtonLink, buttonLink);
                }

                if (messageTitle != null) {
                    intent.putExtra(intentExtraMessageTitle, messageTitle);
                }

                if (messageText != null) {
                    intent.putExtra(intentExtraMessageText, messageText);
                }
            } else {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Provided intent to 'addExtrasToIntent' is null");
                }
            }
        }
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    protected void onStop () {
        super.onStop();
    }
}