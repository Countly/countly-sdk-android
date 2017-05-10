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
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ly.count.android.sdk.Countly;

/**
* Created by artem on 14/10/14.
*/
public class ProxyActivity extends Activity {

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

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

            if (msg != null) {
                if (extras.containsKey(CountlyMessaging.NOTIFICATION_SHOW_DIALOG)) {
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
                        addButtons(builder, msg);

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
                        if (msg.hasMessage()){
                            builder.setMessage(msg.getNotificationMessage());
                        }
                        if (msg.hasButtons()) {
                            addButtons(builder, msg);
                        } else {
                            builder.setPositiveButton(CountlyMessaging.buttonNames[0], new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                CountlyMessaging.recordMessageAction(msg.getId(), 0);
                                finish();
                                Intent activity = msg.getIntent(ProxyActivity.this, CountlyMessaging.getActivityClass(ProxyActivity.this));
                                if(activity != null)
                                    startActivity(activity);
                                }
                            });
                        }
                    } else if (msg.hasMessage()) {
                        if (msg.hasButtons()) {
                            addButtons(builder, msg);
                        } else {
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
                    Intent activity = msg.getIntent(this, CountlyMessaging.getActivityClass(this));
                    if (activity != null) {
                        startActivity(activity);
                    } else {
                        Log.e(Countly.TAG, "Countly Message with UNKNOWN type in ProxyActivity");
//                        throw new IllegalStateException("Countly Message with UNKNOWN type in ProxyActivity");
                    }
                    if (extras.containsKey(CountlyMessaging.EXTRA_ACTION_INDEX)) {
                        CountlyMessaging.recordMessageAction(msg.getId(), extras.getInt(CountlyMessaging.EXTRA_ACTION_INDEX));
                        for (Message.Button button : msg.getButtons()) {
                            if (button.index == extras.getInt(CountlyMessaging.EXTRA_ACTION_INDEX)) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(button.link)));
                                break;
                            }
                        }
                    } else {
                        CountlyMessaging.recordMessageAction(msg.getId(), 0);
                        if (msg.hasLink()) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getLink())));
                        }
                    }
                    finish();
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(msg.getId().hashCode());
                }
            }
        }
    }

    private void addButtons(final AlertDialog.Builder builder, final Message msg) {
        if (msg.hasButtons()) {
//                            final String[] titles = new String[msg.getButtons().size()];
//                            for (int i = 0; i < msg.getButtons().size(); i++) {
//                                titles[i] = msg.getButtons().get(i).title;
//                            }
//                            builder.setItems(titles, new DialogInterface.OnClickListener() {
//                                @Override
//                                public void onClick(DialogInterface dialog, int which) {
//                                    CountlyMessaging.recordMessageAction(msg.getId(), which + 1);
//                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getButtons().get(which).link)));
//                                    finish();
//                                }
//                            });
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CountlyMessaging.recordMessageAction(msg.getId(), which == DialogInterface.BUTTON_POSITIVE ? 1 : 2);
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getButtons().get(which == DialogInterface.BUTTON_POSITIVE ? 0 : 1).link)));
                    finish();
                }
            };
            builder.setPositiveButton(msg.getButtons().get(0).title, listener);
            if (msg.getButtons().size() > 1) {
                builder.setNeutralButton(msg.getButtons().get(1).title, listener);
            }
        }

    }

    @Override
    protected void onStop () {
        super.onStop();
    }
}
