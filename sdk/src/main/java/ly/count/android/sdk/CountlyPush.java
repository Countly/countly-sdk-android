package ly.count.android.sdk;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Parcelable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ly.count.android.sdk.internal.Core;
import ly.count.android.sdk.internal.Tasks;
import ly.count.android.sdk.internal.Utils;
import ly.count.sdk.Session;

/**
 * Just a public holder class for Messaging-related display logic, listeners, managers, etc.
 */

public class CountlyPush {
    public static final String EXTRA_ACTION_INDEX = "ly.count.android.sdk.CountlyPush.Action";
    public static final String EXTRA_MESSAGE = "ly.count.android.sdk.CountlyPush.message";
    public static Class<? extends Activity> pushActivityClass = null;

    /**
     * Message object encapsulating data in {@code RemoteMessage} sent from Countly server.
     */
    public interface Message extends Parcelable {
        /**
         * Title of message
         *
         * @return title string or {@code null} if no title in the message
         */
        String title();

        /**
         * Message text itself
         *
         * @return message string or {@code null} if no message specified
         */
        String message();

        /**
         * Message sound. Default message is sent as "default" string, other sounds are
         * supposed to be sent as URI of sound from app resources.
         *
         * @return sound string or {@code null} if no sound specified
         */
        String sound();

        /**
         * Message badge if any
         *
         * @return message badge number or {@code null} if no badge specified
         */
        Integer badge();

        /**
         * Default message link to open
         *
         * @return message link URL or {@code null} if no link specified
         */
        URL link();

        /**
         * Message media URL to jpeg or png image
         *
         * @return message media URL or {@code null} if no media specified
         */
        URL media();

        /**
         * List of buttons to display along this message if any
         *
         * @return message buttons list or empty list if no buttons specified
         */
        List<Button> buttons();

        /**
         * Set of data keys sent in this message, includes all standard keys like "title" or "message"
         *
         * @return message data keys set
         */
        Set<String> dataKeys();

        /**
         * Check whether data contains the key specified
         *
         * @param key key String to look for
         * @return {@code true} if key exists in the data, {@code false} otherwise
         */
        boolean has(String key);

        /**
         * Get data associated with the key specified
         *
         * @param key key String to look for
         * @return value String for the key or {@code null} if no such key exists in the data
         */
        String data(String key);

        /**
         * Record action event occurrence for this message and put it to current {@link Session}.
         * If no {@link Session} is open at the moment, opens new {@link Session}.
         * Event is recorded for a whole message, not for specific button.
         *
         * @param context Context to record action in
         */
        void recordAction(Context context);

        /**
         * Record action event occurrence for a particular button index and put it to current {@link Session}.
         * If no {@link Session} is open at the moment, opens new {@link Session}.
         * Event is recorded for a particular button, not for a whole message.
         * Behaviour is identical to {@link Button#recordAction(Context)}
         *
         * @param context Context to record action in
         * @param buttonIndex index of button to record Action on
         *
         * @see Button#index()
         * @see Button#recordAction(Context);
         */
        void recordAction(Context context, int buttonIndex);
    }

    /**
     * Button encapsulates information about single button in {@link Message} payload
     */
    public interface Button {
        /**
         * Button index, started from 1
         *
         * @return index of this button
         */
        int index();

        /**
         * Button title
         *
         * @return title of this button
         */
        String title();

        /**
         * Button link
         *
         * @return link of this button
         */
        URL link();

        /**
         * Record action event for this button, usually after a click
         *
         * @param context Context to run in
         * @see Message#recordAction(Context, int)
         */
        void recordAction(Context context);
    }

    /**
     * Standard Countly logic for displaying a {@link Message}
     *
     * @param service context to run in (supposed to be called from {@code FirebaseMessagingService})
     * @param data {@code RemoteMessage#getData()} result
     * @return {@code Boolean.TRUE} if displayed successfully, {@code Boolean.FALSE} if cannot display now, {@code null} if no Countly message is found in {@code data}
     */
    public static Boolean displayMessage(Service service, final Map<String, String> data) {
        final Message msg = decodeMessage(data);
        if (msg == null) {
            return null;
        } else if (isAppRunningInForeground(service)) {
            return displayDialog(service, msg);
        } else {
            return displayNotification(service, msg);
        }
    }

    /**
     * Standard Countly logic for displaying a {@link Notification} based on the {@link Message}
     *
     * @param context context to run in
     * @param msg message to get information from
     * @return {@code Boolean.TRUE} if displayed successfully, {@code Boolean.FALSE} if cannot display now, {@code null} if message is not displayable as {@link Notification}
     */
    public static Boolean displayNotification(final Context context, final Message msg) {
        if (msg.title() == null && msg.message() == null) {
            return null;
        }

        final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent;
        if (pushActivityClass == null) {
            intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        } else {
            intent = new Intent(context, pushActivityClass);
        }
        intent.putExtra(EXTRA_MESSAGE, msg);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Get icon from application or use default one
        int icon;
        try {
            icon = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).icon;
        } catch (PackageManager.NameNotFoundException e) {
            icon = android.R.drawable.ic_dialog_email;
        }

        if (Utils.API(11)) {
            final Notification.Builder builder = new Notification.Builder(context)
                    .setAutoCancel(true)
                    .setSmallIcon(icon)
                    .setTicker(msg.message())
                    .setContentTitle(msg.title())
                    .setContentText(msg.message())
                    .setContentIntent(contentIntent)
                    .setStyle(new Notification.BigTextStyle().bigText(msg.message()).setBigContentTitle(msg.title()));

            for (int i = 0; i < msg.buttons().size(); i++) {
                int index = i + 1;
                Button button = msg.buttons().get(i);
                Intent actionIntent = (Intent) intent.clone();
                actionIntent.putExtra(EXTRA_ACTION_INDEX, index);
                builder.addAction(0, button.title(), PendingIntent.getActivity(context, index, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT));
            }

            if (msg.sound() != null) {
                if (msg.sound().equals("default")) {
                    builder.setDefaults(Notification.DEFAULT_SOUND);
                } else {
                    builder.setSound(Uri.parse(msg.sound()));
                }
            }

            if (Utils.API(16) && msg.media() != null) {
                Core.downloadMedia(msg, new Tasks.Callback<Bitmap>() {
                    @Override
                    public void call(Bitmap bitmap) throws Exception {
                        if (Utils.API(16)) {
                            if (bitmap != null) {
                                builder.setStyle(new Notification.BigPictureStyle()
                                        .bigPicture(bitmap)
                                        .setBigContentTitle(msg.title())
                                        .setSummaryText(msg.message()));
                            }
                            manager.notify(msg.hashCode(), builder.build());
                        }
                    }
                });
            } else {
                manager.notify(msg.hashCode(), builder.build());
            }

            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    /**
     * Standard Countly logic for displaying a {@link AlertDialog} based on the {@link Message}
     *
     * @param context context to run in
     * @param msg message to get information from
     * @return {@code Boolean.TRUE} if displayed successfully, {@code Boolean.FALSE} if cannot display now, {@code null} if message is not displayable as {@link Notification}
     */
    public static Boolean displayDialog(final Context context, final Message msg) {
        Core.downloadMedia(msg, new Tasks.Callback<Bitmap>() {
            @Override
            public void call(Bitmap bitmap) throws Exception {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);

                if (msg.media() != null) {
                    addButtons(context, builder, msg);

                    final LinearLayout layout = new LinearLayout(context);
                    layout.setBackgroundColor(Color.TRANSPARENT);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    if (bitmap != null) {
                        ImageView imageView = new ImageView(context);
                        imageView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));
                        if (msg.media() != null) {
                            imageView.setImageBitmap(bitmap);
                        }
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imageView.setAdjustViewBounds(true);
                        layout.addView(imageView);
                    }

                    if (msg.message() != null) {
                        int padding = (int) (10 * context.getResources().getDisplayMetrics().density + 0.5f);

                        TextView textview = new TextView(context);
                        textview.setText(msg.message());
                        textview.setPadding(padding, padding, padding, padding);
                        layout.addView(textview);
                    }

                    builder.setView(layout);
                } else if (msg.link() != null) {
                    if (msg.title() != null){
                        builder.setTitle(msg.title());
                    }
                    if (msg.message() != null){
                        builder.setMessage(msg.message());
                    }
                    if (msg.buttons().size() > 0) {
                        addButtons(context, builder, msg);
                    } else {
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                msg.recordAction(context, 0);
                                dialog.dismiss();
                            }
                        });
                    }
                } else if (msg.message() != null) {
                    if (msg.buttons().size() > 0) {
                        addButtons(context, builder, msg);
                    } else {
                        msg.recordAction(context);
                    }
                    builder.setTitle(msg.title());
                    builder.setMessage(msg.message());
                    builder.setCancelable(true);
                    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            dialog.dismiss();
                        }
                    });
                } else {
                    throw new IllegalStateException("Countly Message with UNKNOWN type in ProxyActivity");
                }

                builder.create().show();
            }
        });
        return Boolean.TRUE;
    }

    public static void addButtons(final Context context, final AlertDialog.Builder builder, final Message msg) {
        if (msg.buttons().size() > 0) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    msg.recordAction(context, which == DialogInterface.BUTTON_POSITIVE ? 1 : 2);
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(msg.buttons().get(which == DialogInterface.BUTTON_POSITIVE ? 0 : 1).link().toString())));
                    dialog.dismiss();
                }
            };
            builder.setPositiveButton(msg.buttons().get(0).title(), listener);
            if (msg.buttons().size() > 1) {
                builder.setNeutralButton(msg.buttons().get(1).title(), listener);
            }
        }

    }

    /**
     * Decode message from {@code RemoteMessage#getData()} map into {@link Message}.
     *
     * @param data map to decode
     * @return message instance or {@code null} if cannot decode
     */
    public static Message decodeMessage(Map<String, String> data) {
        return Core.decodePushMessage(data);
    }

    /**
     * Token refresh callback to be called from {@code FirebaseInstanceIdService}.
     *
     * @param service context to run in (supposed to be called from {@code FirebaseInstanceIdService})
     * @param token String token to be sent to Countly server
     */
    public static void onTokenRefresh(Service service, String token) {
        Core.onPushTokenRefresh(service, token);
    }


    /**
     * Check whether app is running in foreground.
     * Also available as {@link ly.count.android.sdk.internal.Device#isAppRunningInForeground(Context)}
     *
     * @param context context to check in
     * @return {@code true} if running in foreground, {@code false} otherwise
     */
    public static boolean isAppRunningInForeground (Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
