package ly.count.android.sdk.messaging;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyStore;
import ly.count.android.sdk.ModuleLog;
import ly.count.android.sdk.Utils;

/**
 * Just a public holder class for Messaging-related display logic, listeners, managers, etc.
 */

public class CountlyPush {
    public static final String EXTRA_ACTION_INDEX = "ly.count.android.sdk.CountlyPush.Action";
    public static final String EXTRA_MESSAGE = "ly.count.android.sdk.CountlyPush.message";
    public static final String EXTRA_INTENT = "ly.count.android.sdk.CountlyPush.intent";
    public static final String CHANNEL_ID = "ly.count.android.sdk.CountlyPush.CHANNEL_ID";
    public static final String SECURE_NOTIFICATION_BROADCAST = "ly.count.android.sdk.CountlyPush.SECURE_NOTIFICATION_BROADCAST";
    public static final String COUNTLY_BROADCAST_PERMISSION_POSTFIX = ".CountlyPush.BROADCAST_PERMISSION";

    private static Application.ActivityLifecycleCallbacks callbacks = null;
    private static Activity activity = null;

    private static Countly.CountlyMessagingMode mode = null;
    private static Countly.CountlyMessagingProvider provider = null;

    static Integer notificationAccentColor = null;

    /**
     * Read & connection timeout for rich push media download
     */
    static int MEDIA_DOWNLOAD_TIMEOUT = 15000;

    /**
     * Maximum attempts to download a media for a rich push
     */
    static int MEDIA_DOWNLOAD_ATTEMPTS = 3;

    public static boolean useAdditionalIntentRedirectionChecks = false;

    static boolean initFinished = false;

    /**
     * Message object encapsulating data in {@code RemoteMessage} sent from Countly server.
     */
    public interface Message extends Parcelable {
        /**
         * Countly internal message ID
         *
         * @return id string or {@code null} if no id in the message
         */
        String id();

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
         * @return message link Uri or {@code null} if no link specified
         */
        Uri link();

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
         * Record action event occurrence for this message and put it to current session.
         * If no session is open at the moment, opens new session.
         * Event is recorded for a whole message, not for specific button.
         *
         * @param context Context to record action in
         */
        void recordAction(Context context);

        /**
         * Record action event occurrence for a particular button index and put it to current session.
         * If no session is open at the moment, opens new session.
         * Event is recorded for a particular button, not for a whole message.
         * Behaviour is identical to {@link Button#recordAction(Context)}
         *
         * @param context Context to record action in
         * @param buttonIndex index of button to record Action on: first button has index 1, second one is 2 (0 is reserved for notification-wide action)
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
         * Button index, starts from 1
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
        Uri link();

        /**
         * Record action event for this button, usually after a click
         *
         * @param context Context to run in
         * @see Message#recordAction(Context, int)
         */
        void recordAction(Context context);

        /**
         * Optional method to return icon code
         *
         * @return int resource code for {@link Notification.Action#getSmallIcon()}
         */
        int icon();
    }

    /**
     * Notification action (or contentIntent) receiver. Responsible for recording action event.
     * Starts:
     * - activity specified in last parameter of {@link #displayNotification(Context, Message, int, Intent)} if it's not {@code null};
     * - currently active activity if there is one, see {@link #callbacks};
     * - main activity otherwise.
     */
    public static class NotificationBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent broadcast) {
            Countly.sharedInstance().L.d("[CountlyPush, NotificationBroadcastReceiver] Push broadcast receiver receiving message");

            broadcast.setExtrasClassLoader(CountlyPush.class.getClassLoader());

            Intent intent = broadcast.getParcelableExtra(EXTRA_INTENT);

            if (intent == null) {
                Countly.sharedInstance().L.e("[CountlyPush, NotificationBroadcastReceiver] Received a null Intent, stopping execution");
                return;
            }

            int flags = intent.getFlags();
            if (((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) || ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)) {
                Countly.sharedInstance().L.w("[CountlyPush, NotificationBroadcastReceiver] Attempt to get URI permissions");
                return;
            }

            if (useAdditionalIntentRedirectionChecks) {
                ComponentName componentName = intent.getComponent();
                String intentPackageName = componentName.getPackageName();
                String intentClassName = componentName.getClassName();
                String contextPackageName = context.getPackageName();

                if (intentPackageName != null && !intentPackageName.equals(contextPackageName)) {
                    Countly.sharedInstance().L.w("[CountlyPush, NotificationBroadcastReceiver] Untrusted intent package");
                    return;
                }

                if (intentPackageName == null || !intentClassName.startsWith(intentPackageName)) {
                    Countly.sharedInstance().L.w("[CountlyPush, NotificationBroadcastReceiver] intent class name and intent package names do not match");
                    return;
                }
            }

            Countly.sharedInstance().L.d("[CountlyPush, NotificationBroadcastReceiver] Push broadcast, after filtering");

            intent.setExtrasClassLoader(CountlyPush.class.getClassLoader());

            int index = intent.getIntExtra(EXTRA_ACTION_INDEX, 0);
            Bundle bundle = intent.getParcelableExtra(EXTRA_MESSAGE);
            if (bundle == null) {
                Countly.sharedInstance().L.e("[CountlyPush, NotificationBroadcastReceiver] Received a null Intent bundle, stopping execution");
                return;
            }

            Message message = bundle.getParcelable(EXTRA_MESSAGE);
            if (message == null) {
                Countly.sharedInstance().L.e("[CountlyPush, NotificationBroadcastReceiver] Received a null Intent bundle message, stopping execution");
                return;
            }

            message.recordAction(context, index);

            final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(message.hashCode());
            }

            Intent closeNotificationsPanel = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(closeNotificationsPanel);

            if (index == 0) {
                try {
                    if (message.link() != null) {
                        Countly.sharedInstance().L.d("[CountlyPush, NotificationBroadcastReceiver] Starting activity with given link. Push body. [" + message.link() + "]");
                        Intent i = new Intent(Intent.ACTION_VIEW, message.link());
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        i.putExtra(EXTRA_MESSAGE, bundle);
                        i.putExtra(EXTRA_ACTION_INDEX, index);
                        context.startActivity(i);
                    } else {
                        Countly.sharedInstance().L.d("[CountlyPush, NotificationBroadcastReceiver] Starting activity without a link. Push body");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                } catch (Exception ex) {
                    Countly.sharedInstance().L.e("[CountlyPush, displayDialog] Encountered issue while clicking on notification body [" + ex.toString() + "]");
                }
            } else {
                try {
                    Countly.sharedInstance().L.d("[CountlyPush, NotificationBroadcastReceiver] Starting activity with given button link. [" + (index - 1) + "] [" + message.buttons().get(index - 1).link() + "]");
                    Intent i = new Intent(Intent.ACTION_VIEW, message.buttons().get(index - 1).link());
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.putExtra(EXTRA_MESSAGE, bundle);
                    i.putExtra(EXTRA_ACTION_INDEX, index);
                    context.startActivity(i);
                } catch (Exception ex) {
                    Countly.sharedInstance().L.e("[CountlyPush, displayDialog] Encountered issue while clicking on notification button [" + ex.toString() + "]");
                }
            }
        }
    }

    /**
     * Listens for push consent given and sends existing token to the server if any.
     */
    public static class ConsentBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent broadcast) {
            if (mode != null && provider != null && getPushConsent(context)) {
                String token = getToken(context, provider, Countly.sharedInstance().L);
                if (token != null && !"".equals(token)) {
                    onTokenRefresh(token, provider);
                }
            }
        }
    }

    /**
     * Retrieves current FCM token from FIREBASE_INSTANCEID_CLASS.
     *
     * @return token string or null if no token is currently available.
     */
    private static String getToken(Context context, Countly.CountlyMessagingProvider prov, ModuleLog L) {
        if (prov == Countly.CountlyMessagingProvider.FCM) {
            try {
                Object instance = UtilsMessaging.reflectiveCall(FIREBASE_INSTANCEID_CLASS, null, "getInstance", L);
                return (String) UtilsMessaging.reflectiveCall(FIREBASE_INSTANCEID_CLASS, instance, "getToken", L);
            } catch (Throwable logged) {
                Countly.sharedInstance().L.e("[CountlyPush, getToken] Couldn't get token for Countly FCM", logged);
                return null;
            }
        } else if (prov == Countly.CountlyMessagingProvider.HMS) {
            try {
                Object config = UtilsMessaging.reflectiveCallStrict(HUAWEI_CONFIG_CLASS, null, "fromContext", L, context, Context.class);
                if (config == null) {
                    Countly.sharedInstance().L.e("No Huawei Config");
                    return null;
                }

                Object appId = UtilsMessaging.reflectiveCall(HUAWEI_CONFIG_CLASS, config, "getString", L, "client/app_id");
                if (appId == null || "".equals(appId)) {
                    Countly.sharedInstance().L.e("No Huawei app id in config");
                    return null;
                }

                Object instanceId = UtilsMessaging.reflectiveCallStrict(HUAWEI_INSTANCEID_CLASS, null, "getInstance", L, context, Context.class);
                if (instanceId == null) {
                    Countly.sharedInstance().L.e("No Huawei instance id class");
                    return null;
                }

                Object token = UtilsMessaging.reflectiveCall(HUAWEI_INSTANCEID_CLASS, instanceId, "getToken", L, appId, "HCM");
                return (String) token;
            } catch (Throwable logged) {
                Countly.sharedInstance().L.e("[CountlyPush, getToken] Couldn't get token for Countly huawei push kit", logged);
                return null;
            }
        } else {
            Countly.sharedInstance().L.e("[CountlyPush, getToken] Message provider is neither FCM or HMS, aborting");
            return null;
        }
    }

    /**
     * Standard Countly logic for displaying a {@link Message}.
     *
     * This would display the push message in a dialog if the app was in foreground.
     * If the app was in background, it would display the push message as a notification.
     *
     * @param context context to run in (supposed to be called from {@code FirebaseMessagingService})
     * @param data {@code RemoteMessage#getData()} result
     * @return {@code Boolean.TRUE} if displayed successfully, {@code Boolean.FALSE} if cannot display now, {@code null} if no Countly message is found in {@code data}
     */
    public static Boolean displayMessage(Context context, final Map<String, String> data, @DrawableRes final int notificationSmallIcon, final Intent notificationIntent) {
        return displayMessage(context, decodeMessage(data), notificationSmallIcon, notificationIntent);
    }

    /**
     * Standard Countly logic for displaying a {@link Message}
     *
     * This would display the push message in a dialog if the app was in foreground.
     * If the app was in background, it would display the push message as a notification.
     *
     * @param context context to run in (supposed to be called from {@code FirebaseMessagingService})
     * @param msg {@link Message} instance
     * @return {@code Boolean.TRUE} if displayed successfully, {@code Boolean.FALSE} if cannot display now, {@code null} if no Countly message is found in {@code data}
     */
    public static Boolean displayMessage(final Context context, final Message msg, @DrawableRes final int notificationSmallIcon, final Intent notificationIntent) {
        Countly.sharedInstance().L.d("[CountlyPush, displayMessage] Displaying push message");

        if (!initFinished) {
            Countly.sharedInstance().L.w("[CountlyPush, displayDialog] Push init has not been completed. Some things might not function.");
        }

        if (msg == null) {
            return null;
        } else if (msg.message() == null) {
            // nothing to display
            return null;
        } else if (isAppRunningInForeground(context)) {
            if (activity != null) {
                return displayDialog(activity, msg);
            } else {
                return displayNotification(context, msg, notificationSmallIcon, notificationIntent);
            }
        } else {
            return displayNotification(context, msg, notificationSmallIcon, notificationIntent);
        }
    }

    /**
     * Standard Countly logic for displaying a {@link Notification} based on the {@link Message}
     *
     * @param context context to run in
     * @param msg message to get information from
     * @param notificationSmallIcon smallIcon for notification {@link Notification#getSmallIcon()}
     * @param notificationIntent activity-starting intent to send when user taps on {@link Notification} or one of its {@link android.app.Notification.Action}s. Pass {@code null} to go with main activity.
     * @return {@code Boolean.TRUE} if displayed successfully, {@code Boolean.FALSE} if cannot display now, {@code null} if message is not displayable as {@link Notification}
     */
    public static Boolean displayNotification(final Context context, final Message msg, @DrawableRes final int notificationSmallIcon, final Intent notificationIntent) {
        if (!getPushConsent(context)) {
            return null;
        }

        if (msg == null) {
            Countly.sharedInstance().L.w("[CountlyPush, displayNotification] Message is 'null', can't display a notification");
            return null;
        } else if (msg.title() == null && msg.message() == null) {
            Countly.sharedInstance().L.w("[CountlyPush, displayNotification] Message title and message body is 'null', can't display a notification");
            return null;
        }

        Countly.sharedInstance().L.d("[CountlyPush, displayNotification] Displaying push notification, additional intent provided:[" + (notificationIntent != null) + "]");

        if (!initFinished) {
            Countly.sharedInstance().L.w("[CountlyPush, displayDialog] Push init has not been completed. Some things might not function.");
        }

        final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            Countly.sharedInstance().L.w("[CountlyPush, displayNotification] Retrieved notification manager is 'null', can't display notification");
            return Boolean.FALSE;
        }

        Intent broadcast = new Intent(SECURE_NOTIFICATION_BROADCAST, null, context.getApplicationContext(), NotificationBroadcastReceiver.class);
        broadcast.setPackage(context.getApplicationContext().getPackageName());
        broadcast.putExtra(EXTRA_INTENT, actionIntent(context, notificationIntent, msg, 0));

        final Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(context.getApplicationContext(), CHANNEL_ID) : new Notification.Builder(context.getApplicationContext()))
            .setAutoCancel(true)
            .setSmallIcon(notificationSmallIcon)
            .setTicker(msg.message())
            .setContentTitle(msg.title())
            .setContentText(msg.message());

        if (msg.badge() != null) {
            builder.setNumber(msg.badge());
        }

        if (android.os.Build.VERSION.SDK_INT > 21) {
            if (notificationAccentColor != null) {
                builder.setColor(notificationAccentColor);
            }
        }

        builder.setAutoCancel(true)
            .setContentIntent(PendingIntent.getBroadcast(context, msg.hashCode(), broadcast, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        builder.setStyle(new Notification.BigTextStyle().bigText(msg.message()).setBigContentTitle(msg.title()));

        for (int i = 0; i < msg.buttons().size(); i++) {
            Button button = msg.buttons().get(i);

            broadcast = new Intent(SECURE_NOTIFICATION_BROADCAST, null, context.getApplicationContext(), NotificationBroadcastReceiver.class);
            broadcast.setPackage(context.getApplicationContext().getPackageName());
            broadcast.putExtra(EXTRA_INTENT, actionIntent(context, notificationIntent, msg, i + 1));

            builder.addAction(button.icon(), button.title(), PendingIntent.getBroadcast(context, msg.hashCode() + i + 1, broadcast, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        }

        if (msg.sound() != null) {
            if (msg.sound().equals("default")) {
                builder.setDefaults(Notification.DEFAULT_SOUND);
            } else {
                builder.setSound(Uri.parse(msg.sound()));
            }
        }

        if (msg.media() != null) {
            loadImage(context, msg, new BitmapCallback() {
                @Override
                public void call(Bitmap bitmap) {
                    if (bitmap != null) {
                        builder.setStyle(new Notification.BigPictureStyle()
                            .bigPicture(bitmap)
                            .setBigContentTitle(msg.title())
                            .setSummaryText(msg.message()));
                    }
                    manager.notify(msg.hashCode(), builder.build());
                }
            }, 1);
        } else {
            manager.notify(msg.hashCode(), builder.build());
        }

        return Boolean.TRUE;
    }

    private static Intent actionIntent(Context context, Intent notificationIntent, Message message, int index) {
        Intent intent;
        if (notificationIntent == null) {
            intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        } else {
            intent = (Intent) notificationIntent.clone();
        }
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_MESSAGE, bundle);
        intent.putExtra(EXTRA_ACTION_INDEX, index);
        return intent;
    }

    /**
     * Standard Countly logic for displaying a {@link AlertDialog} based on the {@link Message}
     *
     * @param activity context to run in
     * @param msg message to get information from
     * @return {@code Boolean.TRUE} if displayed successfully, {@code Boolean.FALSE} if cannot display now, {@code null} if message is not displayable as {@link Notification}
     */
    public static Boolean displayDialog(final Activity activity, final Message msg) {
        if (!getPushConsent(activity)) {
            return null;
        }

        Countly.sharedInstance().L.d("[CountlyPush, displayDialog] Displaying push dialog");

        if (!initFinished) {
            Countly.sharedInstance().L.w("[CountlyPush, displayDialog] Push init has not been completed. Some things might not function.");
        }

        loadImage(activity, msg, new BitmapCallback() {
            @Override
            public void call(Bitmap bitmap) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                if (msg.media() != null) {
                    addButtons(activity, builder, msg);

                    final LinearLayout layout = new LinearLayout(activity);
                    layout.setBackgroundColor(Color.TRANSPARENT);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    int padding = (int) (10 * activity.getResources().getDisplayMetrics().density + 0.5f);

                    if (msg.title() != null) {
                        TextView textview = new TextView(activity);
                        textview.setText(msg.title());
                        textview.setPadding(padding, padding, padding, padding);
                        textview.setTypeface(null, Typeface.BOLD);
                        textview.setGravity(Gravity.CENTER);
                        layout.addView(textview);
                    }

                    if (bitmap != null) {
                        ImageView imageView = new ImageView(activity);
                        imageView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));
                        if (msg.media() != null) {
                            imageView.setImageBitmap(bitmap);
                        }
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imageView.setAdjustViewBounds(true);
                        imageView.setPadding(padding, padding, padding, padding);
                        layout.addView(imageView);
                    }

                    if (msg.message() != null) {
                        TextView textview = new TextView(activity);
                        textview.setText(msg.message());
                        textview.setPadding(padding, padding, padding, padding);
                        layout.addView(textview);
                    }

                    builder.setView(layout);
                } else if (msg.link() != null) {
                    if (msg.title() != null) {
                        builder.setTitle(msg.title());
                    }
                    if (msg.message() != null) {
                        builder.setMessage(msg.message());
                    }
                    if (msg.buttons().size() > 0) {
                        addButtons(activity, builder, msg);
                    } else {
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                msg.recordAction(activity, 0);
                                dialog.dismiss();

                                try {
                                    Intent i = new Intent(Intent.ACTION_VIEW, msg.link());
                                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    i.putExtra(EXTRA_ACTION_INDEX, 0);// put zero because non 'button' action
                                    activity.startActivity(i);
                                } catch (Exception ex) {
                                    Countly.sharedInstance().L.e("[CountlyPush, displayDialog] Encountered issue while clicking 'ok' button in dialog [" + ex.toString() + "]");
                                }
                            }
                        });
                    }
                } else if (msg.message() != null) {
                    if (msg.buttons().size() > 0) {
                        addButtons(activity, builder, msg);
                    } else {
                        msg.recordAction(activity);
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
        }, 1);
        return Boolean.TRUE;
    }

    private static void addButtons(final Context context, final AlertDialog.Builder builder, final Message msg) {
        if (msg.buttons().size() > 0) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        msg.recordAction(context, which == DialogInterface.BUTTON_POSITIVE ? 2 : 1);
                        Intent intent = new Intent(Intent.ACTION_VIEW, msg.buttons().get(which == DialogInterface.BUTTON_POSITIVE ? 1 : 0).link());
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(EXTRA_MESSAGE, msg);
                        intent.putExtra(EXTRA_MESSAGE, bundle);
                        intent.putExtra(EXTRA_ACTION_INDEX, which == DialogInterface.BUTTON_POSITIVE ? 2 : 1);
                        context.startActivity(intent);
                    } catch (Exception ex) {
                        Countly.sharedInstance().L.e("[CountlyPush, dialog button onClick] Encountered issue while clicking on button #[" + which + "] [" + ex.toString() + "]");
                    }
                    dialog.dismiss();
                }
            };
            builder.setNeutralButton(msg.buttons().get(0).title(), listener);
            if (msg.buttons().size() > 1) {
                builder.setPositiveButton(msg.buttons().get(1).title(), listener);
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
        ModulePush.MessageImpl message = new ModulePush.MessageImpl(data);
        return message.id == null ? null : message;
    }

    /**
     * Token refresh callback to be called from {@code FirebaseInstanceIdService}.
     *
     * @param token String token to be sent to Countly server
     */
    public static void onTokenRefresh(String token) {
        onTokenRefresh(token, Countly.CountlyMessagingProvider.FCM);
    }

    /**
     * Token refresh callback to be called from {@code FirebaseInstanceIdService}.
     *
     * @param token String token to be sent to Countly server
     * @param provider which provider the token belongs to
     */
    public static void onTokenRefresh(String token, Countly.CountlyMessagingProvider provider) {
        if (!Countly.sharedInstance().isInitialized()) {
            //is some edge cases this might be called before the SDK is initialized
            Countly.sharedInstance().L.i("[CountlyPush, onTokenRefresh] SDK is not initialized, ignoring call");
            return;
        }

        if (!getPushConsent(null)) {
            Countly.sharedInstance().L.i("[CountlyPush, onTokenRefresh] Consent not given, ignoring call");
            return;
        }
        Countly.sharedInstance().L.i("[CountlyPush, onTokenRefresh] Refreshing FCM push token, with mode: [" + mode + "] for [" + provider + "]");
        Countly.sharedInstance().onRegistrationId(token, mode, provider);
    }

    static final String FIREBASE_MESSAGING_CLASS = "com.google.firebase.messaging.FirebaseMessaging";
    static final String FIREBASE_INSTANCEID_CLASS = "com.google.firebase.iid.FirebaseInstanceId";

    static final String HUAWEI_MESSAGING_CLASS = "com.huawei.hms.push.HmsMessageService";
    static final String HUAWEI_CONFIG_CLASS = "com.huawei.agconnect.config.AGConnectServicesConfig";
    static final String HUAWEI_INSTANCEID_CLASS = "com.huawei.hms.aaid.HmsInstanceId";

    /**
     * Initialize Countly messaging functionality
     *
     * @param application application instance
     * @param mode whether to mark push token as test or as production one
     * @throws IllegalStateException
     */
    public static void init(final Application application, Countly.CountlyMessagingMode mode) throws IllegalStateException {
        init(application, mode, null);
    }

    /**
     * Initialize Countly messaging functionality
     *
     * @param application application instance
     * @param mode whether to mark push token as test or as production one
     * @param preferredProvider prefer specified push provider, {@code null} means use FCM first, then fallback to Huawei
     * @throws IllegalStateException
     */
    public static void init(final Application application, Countly.CountlyMessagingMode mode, Countly.CountlyMessagingProvider preferredProvider) throws IllegalStateException {
        Countly.sharedInstance().L.i("[CountlyPush, init] Initialising Countly Push, App:[" + (application != null) + "], mode:[" + mode + "] provider:[" + preferredProvider + "]");

        if (application == null) {
            throw new IllegalStateException("Non 'null' application must be provided!");
        }

        // set preferred push provider
        CountlyPush.provider = preferredProvider;
        if (provider == null) {
            if (UtilsMessaging.reflectiveClassExists(FIREBASE_MESSAGING_CLASS, Countly.sharedInstance().L)) {
                provider = Countly.CountlyMessagingProvider.FCM;
            } else if (UtilsMessaging.reflectiveClassExists(HUAWEI_MESSAGING_CLASS, Countly.sharedInstance().L)) {
                provider = Countly.CountlyMessagingProvider.HMS;
            }
        } else if (provider == Countly.CountlyMessagingProvider.FCM && !UtilsMessaging.reflectiveClassExists(FIREBASE_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            provider = Countly.CountlyMessagingProvider.HMS;
        } else if (provider == Countly.CountlyMessagingProvider.HMS && !UtilsMessaging.reflectiveClassExists(HUAWEI_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            provider = Countly.CountlyMessagingProvider.FCM;
        }

        // print error in case preferred push provider is not available
        if (provider == Countly.CountlyMessagingProvider.FCM && !UtilsMessaging.reflectiveClassExists(FIREBASE_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            Countly.sharedInstance().L.e("Countly push didn't initialise. No FirebaseMessaging class in the class path. Please either add it to your gradle config or don't use CountlyPush.");
            return;
        } else if (provider == Countly.CountlyMessagingProvider.HMS && !UtilsMessaging.reflectiveClassExists(HUAWEI_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            Countly.sharedInstance().L.e("Countly push didn't initialise. No HmsMessageService class in the class path. Please either add it to your gradle config or don't use CountlyPush.");
            return;
        } else if (provider == null) {
            Countly.sharedInstance().L.e("Countly push didn't initialise. Neither FirebaseMessaging, nor HmsMessageService class in the class path. Please either add Firebase / Huawei dependencies or don't use CountlyPush.");
            return;
        }

        CountlyPush.mode = mode;
        CountlyStore.cacheLastMessagingMode(mode == Countly.CountlyMessagingMode.TEST ? 0 : 1, application);
        CountlyStore.storeMessagingProvider(provider == Countly.CountlyMessagingProvider.FCM ? 1 : 2, application);

        if (callbacks == null) {
            callbacks = new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    CountlyPush.activity = activity;
                }

                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    if (activity.equals(CountlyPush.activity)) {
                        CountlyPush.activity = null;
                    }
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            };

            application.registerActivityLifecycleCallbacks(callbacks);

            IntentFilter filter = new IntentFilter();
            filter.addAction(Countly.CONSENT_BROADCAST);
            BroadcastReceiver consentReceiver = new ConsentBroadcastReceiver();
            application.registerReceiver(consentReceiver, filter, application.getPackageName() + COUNTLY_BROADCAST_PERMISSION_POSTFIX, null);
        }

        if (provider == Countly.CountlyMessagingProvider.HMS && getPushConsent(application)) {
            String version = getEMUIVersion();
            if (version.startsWith("10")) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String token = getToken(application, Countly.CountlyMessagingProvider.HMS, Countly.sharedInstance().L);
                        if (token != null && !"".equals(token)) {
                            onTokenRefresh(token, Countly.CountlyMessagingProvider.HMS);
                        }
                    }
                }).start();
            }
        }

        //mark this so that sanity checks can be performed in the future
        initFinished = true;
    }

    static boolean getPushConsent(Context context) {
        if (Countly.sharedInstance().isInitialized() || context == null) {
            //todo currently this is also used when context is null and might result in unintended consequences
            //if SDK is initialised, use the stored value
            return Countly.sharedInstance().consent().getConsent(Countly.CountlyFeatureNames.push);
        } else {
            //if the SDK is not initialised, use the cached value
            return CountlyStore.getConsentPushNoInit(context);
        }
    }

    private static String getEMUIVersion() {
        try {
            String line = Build.DISPLAY;
            int spaceIndex = line.indexOf(" ");
            int lastIndex = line.indexOf("(");
            if (lastIndex != -1) {
                return line.substring(spaceIndex, lastIndex).trim();
            } else {
                return line.substring(spaceIndex).trim();
            }
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Returns which messaging mode was used in the previous init
     * -1 - no data / no init has happened
     * 0 - test mode
     * 1 - production mode
     */
    public static int getLastMessagingMethod(Context context) {
        return CountlyStore.getLastMessagingMode(context);
    }

    public static void setNotificationAccentColor(int alpha, int red, int green, int blue) {
        alpha = Math.min(255, Math.max(0, alpha));
        red = Math.min(255, Math.max(0, red));
        green = Math.min(255, Math.max(0, green));
        blue = Math.min(255, Math.max(0, blue));

        Countly.sharedInstance().L.d("[CountlyPush] Calling [setNotificationAccentColor], [" + alpha + "][" + red + "][" + green + "][" + blue + "]");

        notificationAccentColor = Color.argb(alpha, red, green, blue);
    }

    /**
     * Check whether app is running in foreground.
     *
     * @param context context to check in
     * @return {@code true} if running in foreground, {@code false} otherwise
     */
    private static boolean isAppRunningInForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            Countly.sharedInstance().L.d("[CountlyPush] Checking if app in foreground, NO");
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                Countly.sharedInstance().L.d("[CountlyPush] Checking if app in foreground, YES");
                return true;
            }
        }
        Countly.sharedInstance().L.d("[CountlyPush] Checking if app in foreground, NO");
        return false;
    }

    private interface BitmapCallback {
        void call(Bitmap bitmap);
    }

    private static void loadImage(final Context context, final Message msg, final BitmapCallback callback, final int attempt) {
        Utils.runInBackground(new Runnable() {
            @Override public void run() {
                final Bitmap[] bitmap = new Bitmap[] { null };

                if (msg.media() != null) {
                    HttpURLConnection connection = null;
                    InputStream input = null;
                    try {
                        connection = (HttpURLConnection) msg.media().openConnection();
                        connection.setDoInput(true);
                        connection.setConnectTimeout(MEDIA_DOWNLOAD_TIMEOUT);
                        connection.setReadTimeout(MEDIA_DOWNLOAD_TIMEOUT);
                        connection.connect();
                        input = connection.getInputStream();
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int read;
                        while ((read = input.read(buf, 0, buf.length)) != -1) {
                            bytes.write(buf, 0, read);
                        }
                        bytes.flush();

                        byte[] data = bytes.toByteArray();
                        bitmap[0] = BitmapFactory.decodeByteArray(data, 0, data.length);
                    } catch (Exception e) {
                        Countly.sharedInstance().L.e("[CountlyPush] loadImage, Cannot download message media ", e);
                        if (attempt < MEDIA_DOWNLOAD_ATTEMPTS) {
                            loadImage(context, msg, callback, attempt + 1);
                            return;
                        }
                    } finally {
                        if (input != null) {
                            try {
                                input.close();
                            } catch (IOException ignored) {
                            }
                        }
                        if (connection != null) {
                            try {
                                connection.disconnect();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }

                new Handler(context.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.call(bitmap[0]);
                    }
                });
            }
        });
    }
}
