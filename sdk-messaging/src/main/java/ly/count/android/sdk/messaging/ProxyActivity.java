package ly.count.android.sdk.messaging;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

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
        if(extras != null) {
            final Message msg = extras.getParcelable(CountlyMessaging.EXTRA_MESSAGE);

            if (msg != null) {
                if (extras.containsKey(CountlyMessaging.NOTIFICATION_SHOW_DIALOG)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(msg.getNotificationTitle(this))
                            .setMessage(msg.getNotificationMessage());

                    if (msg.hasLink()) {
                        builder.setCancelable(true)
                                .setPositiveButton(CountlyMessaging.buttonNames[0], new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        CountlyMessaging.recordMessageAction(msg.getId());
                                        finish();
                                        Intent activity = msg.getIntent(ProxyActivity.this, CountlyMessaging.getActivityClass(ProxyActivity.this));
                                        if(activity != null)
                                            startActivity(activity);
                                    }
                                })
                                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        finish();
                                    }
                                });
                    } else if (msg.hasMessage()) {
                        CountlyMessaging.recordMessageAction(msg.getId());
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
                    CountlyMessaging.recordMessageAction(msg.getId());
                    Intent activity = msg.getIntent(this, CountlyMessaging.getActivityClass(this));
                    if (activity != null)
                        startActivity(activity);
                    else
                        Log.e(Countly.TAG, "Countly Message with UNKNOWN type in ProxyActivity");
//                        throw new IllegalStateException("Countly Message with UNKNOWN type in ProxyActivity");
                }
            }
        }
    }

    @Override
    protected void onStop () {
        super.onStop();
    }
}
