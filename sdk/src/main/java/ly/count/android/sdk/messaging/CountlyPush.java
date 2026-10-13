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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyStore;
import ly.count.android.sdk.ModuleLog;
import ly.count.android.sdk.Utils;
import ly.count.android.sdk.UtilsNetworking;

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
    public static final String ALLOWED_PACKAGE_NAMES = "allowed_package_names";
    public static final String ALLOWED_CLASS_NAMES = "allowed_class_names";
    static final String ADDITIONAL_INTENT_REDIRECTION_CHECKS = "additional_intent_redirection_checks";
    static final String ALLOWED_INTENT_SCHEMES = "allowed_intent_schemes";

    private static Application.ActivityLifecycleCallbacks callbacks = null;
    private static Activity activity = null;

    protected static CountlyConfigPush countlyConfigPush = null;

    static Integer notificationAccentColor = null;

    /**
     * Read & connection timeout for rich push media download
     */
    static int MEDIA_DOWNLOAD_TIMEOUT = 15_000;

    /**
     * Maximum attempts to download a media for a rich push
     */
    static int MEDIA_DOWNLOAD_ATTEMPTS = 3;

    /**
     * Longest a notification waits for its large icon and media together, in milliseconds. What has not arrived by
     * then is left out, so a slow network delays a notification by this much at most.
     */
    static int MEDIA_TOTAL_BUDGET_MS = 8_000;

    /**
     * Largest image, in bytes, a notification downloads. A larger one is left out rather than decoded.
     */
    static int MEDIA_MAX_BYTES = 5 * 1024 * 1024;

    /**
     * Longest side, in pixels, a notification image is decoded at; a larger image is sampled down while decoding.
     */
    static final int MEDIA_MAX_DIMENSION = 1024;

    //the token the app handed over last, kept so that giving push consent later can send it without asking the provider
    private static volatile String lastToken = null;
    private static volatile Countly.CountlyMessagingProvider lastTokenProvider = null;

    //arrival order, and the newest arrival per collapse key, so a late older message never replaces a newer one
    private static final AtomicLong arrivalSequence = new AtomicLong();
    private static final Map<String, Long> newestArrivalPerCollapseKey = new ConcurrentHashMap<>();

    /**
     * Largest width or height, in pixels, a notification large icon is kept at
     */
    static final int LARGE_ICON_MAX_SIZE = 256;

    /**
     * Prefix of the notification tag used for messages that carry a collapse key
     */
    static final String COLLAPSE_TAG_PREFIX = "ly.count.android.sdk.CountlyPush.collapse.";

    /**
     * Notification ID used together with the collapse tag, the tag alone tells collapsed notifications apart
     */
    static final int COLLAPSE_NOTIFICATION_ID = 0;

    /**
     * Tag the rich push media and large icon download sockets are attributed to in network usage
     * statistics, the same value as the core SDK's UtilsNetworking.SOCKET_TAG, which is not visible
     * from this package.
     */
    private static final int MEDIA_DOWNLOAD_SOCKET_TAG = 0x434C59;

    /**
     * @deprecated No longer used. Setting this field has no effect. Enable the additional intent
     * redirection checks through {@link CountlyConfigPush#enableAdditionalIntentRedirectionChecks()} instead.
     */
    @Deprecated
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
         * Large icon shown next to the notification text. Either an http(s) URL of an image, or the
         * name of a drawable or mipmap resource in the app. A resource that is only ever referenced by
         * this name is invisible to resource shrinking, so keep it with a {@code tools:keep} entry in
         * {@code res/raw/keep.xml}.
         *
         * @return large icon string or {@code null} if no large icon specified
         */
        default String largeIcon() {
            return null;
        }

        /**
         * Collapse key of this message. A notification whose message has the same collapse key as a
         * notification already shown replaces it instead of being shown next to it.
         *
         * @return collapse key string or {@code null} if no collapse key specified
         */
        default String collapseKey() {
            return null;
        }

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
         * @see Button#recordAction(Context)
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
     * Listens for push consent given and sends existing token to the server if any.
     */
    public static class ConsentBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent broadcast) {
            if (countlyConfigPush.provider != null && getPushConsent(context)) {
                String token = lastToken;
                Countly.CountlyMessagingProvider provider = lastTokenProvider;
                if (token == null || provider == null) {
                    provider = countlyConfigPush.provider;
                    token = getToken(context, provider, Countly.sharedInstance().L);
                }
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
    private static String getToken(@Nullable Context context, @Nullable Countly.CountlyMessagingProvider prov, @NonNull ModuleLog L) {
        //todo this seems to be broken
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
    public static Boolean displayMessage(@Nullable Context context, @Nullable final Map<String, String> data, @DrawableRes final int notificationSmallIcon, @Nullable final Intent notificationIntent) {
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
    public static Boolean displayMessage(@Nullable final Context context, @Nullable final Message msg, @DrawableRes final int notificationSmallIcon, @Nullable final Intent notificationIntent) {
        Countly.sharedInstance().L.d("[CountlyPush, displayMessage] Displaying push message");

        if (context == null) {
            Countly.sharedInstance().L.e("[CountlyPush, displayMessage] Provided context was null, execution will stop");
            return false;
        }

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
    public static Boolean displayNotification(@Nullable final Context context, @Nullable final Message msg, @DrawableRes final int notificationSmallIcon, @Nullable final Intent notificationIntent) {
        if (context == null) {
            Countly.sharedInstance().L.e("[CountlyPush, displayNotification] Provided context was null, execution will stop");
            return false;
        }

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

        Set<String> allowedIntentClassNames;
        Set<String> allowedIntentPackageNames;
        Set<String> allowedIntentSchemes;
        boolean useAdditionalIntentRedirectionChecks;

        if (!initFinished) {
            Countly.sharedInstance().L.w("[CountlyPush, displayDialog] Push init has not been completed. Some things might not function.");
            allowedIntentClassNames = new HashSet<>();
            allowedIntentPackageNames = new HashSet<>();
            allowedIntentSchemes = new HashSet<>();
            useAdditionalIntentRedirectionChecks = false;
        } else {
            allowedIntentClassNames = CountlyPush.countlyConfigPush.allowedIntentClassNames;
            allowedIntentPackageNames = CountlyPush.countlyConfigPush.allowedIntentPackageNames;
            allowedIntentSchemes = CountlyPush.countlyConfigPush.allowedIntentSchemes;
            useAdditionalIntentRedirectionChecks = CountlyPush.countlyConfigPush.useAdditionalIntentRedirectionChecks;
        }

        final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            Countly.sharedInstance().L.w("[CountlyPush, displayNotification] Retrieved notification manager is 'null', can't display notification");
            return Boolean.FALSE;
        }

        Intent pushActivityIntent = createPushActivityIntent(context, msg, notificationIntent, 0, allowedIntentClassNames, allowedIntentPackageNames, useAdditionalIntentRedirectionChecks, allowedIntentSchemes);

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
            .setContentIntent(PendingIntent.getActivity(context, msg.hashCode(), pushActivityIntent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        builder.setStyle(new Notification.BigTextStyle().bigText(msg.message()).setBigContentTitle(msg.title()));

        for (int i = 0; i < msg.buttons().size(); i++) {
            Button button = msg.buttons().get(i);

            pushActivityIntent = createPushActivityIntent(context, msg, notificationIntent, i + 1, allowedIntentClassNames, allowedIntentPackageNames, useAdditionalIntentRedirectionChecks, allowedIntentSchemes);
            builder.addAction(button.icon(), button.title(), PendingIntent.getActivity(context, msg.hashCode() + i + 1, pushActivityIntent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        }

        if (msg.sound() != null) {
            if (msg.sound().equals("default")) {
                builder.setDefaults(Notification.DEFAULT_SOUND);
            } else {
                builder.setSound(Uri.parse(msg.sound()));
            }
        }

        final long arrival = recordArrival(msg);
        final long deadline = SystemClock.elapsedRealtime() + MEDIA_TOTAL_BUDGET_MS;
        final AtomicBoolean posted = new AtomicBoolean(false);
        final Handler mainHandler = new Handler(context.getMainLooper());

        //images that have not arrived within the budget are left out, the notification is not held back any longer
        final Runnable postWithWhatArrived = new Runnable() {
            @Override public void run() {
                if (posted.compareAndSet(false, true)) {
                    Countly.sharedInstance().L.w("[CountlyPush, displayNotification] Images did not arrive within [" + MEDIA_TOTAL_BUDGET_MS + "] ms, posting the notification without them");
                    postNotification(manager, msg, arrival, builder.build());
                }
            }
        };
        mainHandler.postDelayed(postWithWhatArrived, MEDIA_TOTAL_BUDGET_MS);

        loadLargeIcon(context, msg.largeIcon(), deadline, new BitmapCallback() {
            @Override
            public void call(final Bitmap largeIcon) {
                if (posted.get()) {
                    return;
                }
                if (largeIcon != null) {
                    builder.setLargeIcon(largeIcon);
                }

                if (msg.media() != null) {
                    loadImage(context, msg, deadline, new BitmapCallback() {
                        @Override
                        public void call(Bitmap bitmap) {
                            if (posted.get()) {
                                return;
                            }
                            if (bitmap != null) {
                                Notification.BigPictureStyle style = new Notification.BigPictureStyle()
                                    .bigPicture(bitmap)
                                    .setBigContentTitle(msg.title())
                                    .setSummaryText(msg.message());
                                if (largeIcon != null) {
                                    style.bigLargeIcon((Bitmap) null);
                                }
                                builder.setStyle(style);
                            }
                            if (posted.compareAndSet(false, true)) {
                                mainHandler.removeCallbacks(postWithWhatArrived);
                                postNotification(manager, msg, arrival, builder.build());
                            }
                        }
                    });
                } else if (posted.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(postWithWhatArrived);
                    postNotification(manager, msg, arrival, builder.build());
                }
            }
        });

        return Boolean.TRUE;
    }

    /**
     * Numbers a message in arrival order and remembers it as the newest message of its collapse key.
     *
     * @param msg the message that arrived
     * @return its place in the arrival order
     */
    private static long recordArrival(@NonNull Message msg) {
        long arrival = arrivalSequence.incrementAndGet();
        String collapseKey = msg.collapseKey();
        if (collapseKey != null) {
            newestArrivalPerCollapseKey.put(collapseKey, arrival);
        }
        return arrival;
    }

    /**
     * Posts the notification of a message. A message with a collapse key is posted under a tag
     * derived from that key, so a later message with the same key replaces it. Any other message
     * keeps a notification of its own, identified by its message ID.
     *
     * @param manager notification manager to post with
     * @param msg message the notification was built from
     * @param arrival place of the message in the arrival order, see {@link #recordArrival(Message)}
     * @param notification notification to post
     */
    private static void postNotification(@NonNull NotificationManager manager, @NonNull Message msg, long arrival, @NonNull Notification notification) {
        String collapseKey = msg.collapseKey();
        if (collapseKey != null) {
            Long newestArrival = newestArrivalPerCollapseKey.get(collapseKey);
            if (newestArrival != null && newestArrival > arrival) {
                Countly.sharedInstance().L.d("[CountlyPush, postNotification] A newer message with collapse key [" + collapseKey + "] arrived meanwhile, not posting this older one");
                return;
            }
            Countly.sharedInstance().L.d("[CountlyPush, postNotification] Posting notification under collapse key [" + collapseKey + "]");
            manager.notify(COLLAPSE_TAG_PREFIX + collapseKey, COLLAPSE_NOTIFICATION_ID, notification);
        } else {
            manager.notify(msg.id().hashCode(), notification);
        }
    }

    /**
     * Removes the notification of a message, addressed the way {@link #postNotification} posted it.
     *
     * @param manager notification manager to cancel with
     * @param msg message whose notification to remove
     */
    static void cancelNotification(@NonNull NotificationManager manager, @NonNull Message msg) {
        String collapseKey = msg.collapseKey();
        if (collapseKey != null) {
            manager.cancel(COLLAPSE_TAG_PREFIX + collapseKey, COLLAPSE_NOTIFICATION_ID);
        } else if (msg.id() != null) {
            manager.cancel(msg.id().hashCode());
        }
    }

    // package-private (not private) so the config -> intent-extra wiring is unit-testable.
    static Intent createPushActivityIntent(@NonNull final Context context, @NonNull final Message msg, @Nullable final Intent notificationIntent, int index, @NonNull Set<String> allowedIntentClassNames, @NonNull Set<String> allowedIntentPackageNames, boolean useAdditionalIntentRedirectionChecks, @NonNull Set<String> allowedIntentSchemes) {
        Intent pushActivityIntent = new Intent(context.getApplicationContext(), CountlyPushActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        pushActivityIntent.setPackage(context.getApplicationContext().getPackageName());
        pushActivityIntent.putExtra(EXTRA_INTENT, actionIntent(context, notificationIntent, msg, index));
        pushActivityIntent.putStringArrayListExtra(ALLOWED_CLASS_NAMES, new ArrayList<>(allowedIntentClassNames));
        pushActivityIntent.putStringArrayListExtra(ALLOWED_PACKAGE_NAMES, new ArrayList<>(allowedIntentPackageNames));
        pushActivityIntent.putStringArrayListExtra(ALLOWED_INTENT_SCHEMES, new ArrayList<>(allowedIntentSchemes));
        // Carried on the SDK-built intent so the activity does not read it from static state. The
        // activity defaults to true (fail-secure) when this extra is absent.
        pushActivityIntent.putExtra(ADDITIONAL_INTENT_REDIRECTION_CHECKS, useAdditionalIntentRedirectionChecks);
        return pushActivityIntent;
    }

    /**
     * Attaches the push payload extras ({@link #EXTRA_MESSAGE} / {@link #EXTRA_ACTION_INDEX}) to an
     * outgoing ACTION_VIEW intent only when the link resolves to our own app, so the message data is
     * not leaked to an external app that happens to handle the link. Shared by the push-activity link
     * paths and the dialog button path so the leak-prevention logic lives in one place.
     */
    static void forwardPayloadIfOwnApp(@NonNull Context context, @NonNull Intent intent, @NonNull Bundle messageBundle, int actionIndex) {
        ComponentName linkTarget = intent.resolveActivity(context.getPackageManager());
        if (linkTarget != null && context.getPackageName().equals(linkTarget.getPackageName())) {
            intent.putExtra(EXTRA_MESSAGE, messageBundle);
            intent.putExtra(EXTRA_ACTION_INDEX, actionIndex);
        }
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
    public static Boolean displayDialog(@NonNull final Activity activity, @NonNull final Message msg) {
        if (activity == null) {
            Countly.sharedInstance().L.e("[CountlyPush, displayDialog] Provided Activity was null, execution will stop");
            return false;
        }

        if (!getPushConsent(activity)) {
            return null;
        }

        Countly.sharedInstance().L.d("[CountlyPush, displayDialog] Displaying push dialog");

        if (!initFinished) {
            Countly.sharedInstance().L.w("[CountlyPush, displayDialog] Push init has not been completed. Some things might not function.");
        }

        loadImage(activity, msg, SystemClock.elapsedRealtime() + MEDIA_TOTAL_BUDGET_MS, new BitmapCallback() {
            @Override
            public void call(Bitmap bitmap) {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    //the user moved on while the image loaded: the message goes to the activity in front now, if any
                    Activity current = CountlyPush.activity;
                    if (current != null && current != activity && !current.isFinishing() && !current.isDestroyed()) {
                        Countly.sharedInstance().L.d("[CountlyPush, displayDialog] The activity is gone by the time the dialog is ready, showing it on the current one");
                        displayDialog(current, msg);
                        return;
                    }
                    Countly.sharedInstance().L.w("[CountlyPush, displayDialog] The activity is gone by the time the dialog is ready, not showing it");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                if (msg != null && msg.media() != null) {
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
                } else if (msg != null && msg.link() != null) {
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

                                Uri link = msg.link();
                                if (link == null) {
                                    Countly.sharedInstance().L.w("[CountlyPush, displayDialog] Dialog link is null, nothing to open");
                                    return;
                                }

                                if (countlyConfigPush != null && countlyConfigPush.notificationButtonURLHandler != null && countlyConfigPush.notificationButtonURLHandler.onClick(link.toString(), activity)) {
                                    Countly.sharedInstance().L.d("[CountlyPush, displayDialog] Link handled by custom URL handler, skipping default link opening.");
                                    return;
                                }

                                Set<String> allowedDialogSchemes = countlyConfigPush != null ? countlyConfigPush.allowedIntentSchemes : null;
                                if (!Utils.isExternalSchemeAllowed(link.getScheme(), allowedDialogSchemes)) {
                                    Countly.sharedInstance().L.w("[CountlyPush, displayDialog] Blocked dialog link with disallowed scheme: [" + link.getScheme() + "]");
                                    return;
                                }

                                try {
                                    Intent i = new Intent(Intent.ACTION_VIEW, link);
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
                    Countly.sharedInstance().L.w("[CountlyPush, displayDialog] The message has neither media, a link nor a body, not showing a dialog");
                    return;
                }

                try {
                    builder.create().show();
                } catch (WindowManager.BadTokenException ex) {
                    Countly.sharedInstance().L.w("[CountlyPush, displayDialog] The activity's window is gone, the dialog is not shown", ex);
                }
            }
        });
        return Boolean.TRUE;
    }

    private static void addButtons(final Context context, final AlertDialog.Builder builder, final Message msg) {
        if (msg.buttons().size() > 0) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    boolean isPositiveButtonPressed = (which == DialogInterface.BUTTON_POSITIVE);
                    Uri buttonLink = msg.buttons().get(isPositiveButtonPressed ? 1 : 0).link();
                    if (countlyConfigPush != null && countlyConfigPush.notificationButtonURLHandler != null && countlyConfigPush.notificationButtonURLHandler.onClick(buttonLink == null ? null : buttonLink.toString(), context)) {
                        Countly.sharedInstance().L.d("[CountlyPush, dialog button onClick] Link handled by custom URL handler, skipping default link opening.");
                        return;
                    }

                    try {
                        msg.recordAction(context, isPositiveButtonPressed ? 2 : 1);

                        Set<String> allowedDialogSchemes = countlyConfigPush != null ? countlyConfigPush.allowedIntentSchemes : null;
                        if (!Utils.isExternalSchemeAllowed(buttonLink == null ? null : buttonLink.getScheme(), allowedDialogSchemes)) {
                            Countly.sharedInstance().L.w("[CountlyPush, dialog button onClick] Blocked dialog button link with disallowed scheme: [" + (buttonLink == null ? null : buttonLink.getScheme()) + "]");
                            return;
                        }

                        Intent intent = new Intent(Intent.ACTION_VIEW, buttonLink);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(EXTRA_MESSAGE, msg);
                        forwardPayloadIfOwnApp(context, intent, bundle, isPositiveButtonPressed ? 2 : 1);
                        context.startActivity(intent);
                    } catch (Exception ex) {
                        Countly.sharedInstance().L.e("[CountlyPush, dialog button onClick] Encountered issue while clicking on button #[" + which + "] [" + ex + "]");
                    }
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
        if (token != null && !token.isEmpty() && provider != null) {
            lastToken = token;
            lastTokenProvider = provider;
        }

        if (!initFinished) {
            Countly.sharedInstance().L.i("[CountlyPush, onTokenRefresh] CountlyPush is not initialized, ignoring call");
            return;
        }

        if (!Countly.sharedInstance().isInitialized()) {
            //is some edge cases this might be called before the SDK is initialized
            Countly.sharedInstance().L.i("[CountlyPush, onTokenRefresh] SDK is not initialized, ignoring call");
            return;
        }

        if (!getPushConsent(null)) {
            Countly.sharedInstance().L.i("[CountlyPush, onTokenRefresh] Consent not given, ignoring call");
            return;
        }

        Countly.sharedInstance().L.i("[CountlyPush, onTokenRefresh] Refreshing FCM push token, with for [" + provider + "]");
        Countly.sharedInstance().onRegistrationId(token, provider);
    }

    static final String FIREBASE_MESSAGING_CLASS = "com.google.firebase.messaging.FirebaseMessaging";
    static final String FIREBASE_INSTANCEID_CLASS = "com.google.firebase.iid.FirebaseInstanceId";

    static final String HUAWEI_MESSAGING_CLASS = "com.huawei.hms.push.HmsMessageService";
    static final String HUAWEI_CONFIG_CLASS = "com.huawei.agconnect.config.AGConnectServicesConfig";
    static final String HUAWEI_INSTANCEID_CLASS = "com.huawei.hms.aaid.HmsInstanceId";

    /**
     * Initialize Countly messaging functionality
     *
     * @param countlyConfigPush push configuration
     * @throws IllegalStateException
     */
    public static void init(@Nullable CountlyConfigPush countlyConfigPush) throws IllegalStateException {
        if (countlyConfigPush == null) {
            Countly.sharedInstance().L.e("[CountlyPush, init] Can't initialize Countly Push, provided 'CountlyConfigPush' was null");
            return;
        }

        Countly.sharedInstance().L.i("[CountlyPush, init] Initializing Countly Push, App:[" + (countlyConfigPush.application != null) + "], provider:[" + countlyConfigPush.provider + "]");

        if (countlyConfigPush.application == null) {
            throw new IllegalStateException("Non 'null' application must be provided!");
        }

        CountlyPush.countlyConfigPush = countlyConfigPush;
        // set preferred push provider
        if (countlyConfigPush.provider == null) {
            if (UtilsMessaging.reflectiveClassExists(FIREBASE_MESSAGING_CLASS, Countly.sharedInstance().L)) {
                countlyConfigPush.setProvider(Countly.CountlyMessagingProvider.FCM);
            } else if (UtilsMessaging.reflectiveClassExists(HUAWEI_MESSAGING_CLASS, Countly.sharedInstance().L)) {
                countlyConfigPush.setProvider(Countly.CountlyMessagingProvider.HMS);
            }
        } else if (countlyConfigPush.provider == Countly.CountlyMessagingProvider.FCM && !UtilsMessaging.reflectiveClassExists(FIREBASE_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            countlyConfigPush.setProvider(Countly.CountlyMessagingProvider.HMS);
        } else if (countlyConfigPush.provider == Countly.CountlyMessagingProvider.HMS && !UtilsMessaging.reflectiveClassExists(HUAWEI_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            countlyConfigPush.setProvider(Countly.CountlyMessagingProvider.FCM);
        }

        // print error in case preferred push provider is not available
        if (countlyConfigPush.provider == Countly.CountlyMessagingProvider.FCM && !UtilsMessaging.reflectiveClassExists(FIREBASE_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            Countly.sharedInstance().L.e("Countly push didn't initialize. No FirebaseMessaging class in the class path. Please either add it to your gradle config or don't use CountlyPush.");
            return;
        } else if (countlyConfigPush.provider == Countly.CountlyMessagingProvider.HMS && !UtilsMessaging.reflectiveClassExists(HUAWEI_MESSAGING_CLASS, Countly.sharedInstance().L)) {
            Countly.sharedInstance().L.e("Countly push didn't initialize. No HmsMessageService class in the class path. Please either add it to your gradle config or don't use CountlyPush.");
            return;
        } else if (countlyConfigPush.provider == null) {
            Countly.sharedInstance().L.e("Countly push didn't initialize. Neither FirebaseMessaging, nor HmsMessageService class in the class path. Please either add Firebase / Huawei dependencies or don't use CountlyPush.");
            return;
        }

        CountlyStore.storeMessagingProvider(countlyConfigPush.provider == Countly.CountlyMessagingProvider.FCM ? 1 : 2, countlyConfigPush.application);

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

            countlyConfigPush.application.registerActivityLifecycleCallbacks(callbacks);

            IntentFilter filter = new IntentFilter();
            filter.addAction(Countly.CONSENT_BROADCAST);
            BroadcastReceiver consentReceiver = new ConsentBroadcastReceiver();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                countlyConfigPush.application.registerReceiver(consentReceiver, filter, countlyConfigPush.application.getPackageName() + COUNTLY_BROADCAST_PERMISSION_POSTFIX, null, Context.RECEIVER_NOT_EXPORTED);
            } else {
                countlyConfigPush.application.registerReceiver(consentReceiver, filter, countlyConfigPush.application.getPackageName() + COUNTLY_BROADCAST_PERMISSION_POSTFIX, null);
            }
        }

        if (countlyConfigPush.provider == Countly.CountlyMessagingProvider.HMS && getPushConsent(countlyConfigPush.application)) {
            String version = getEMUIVersion();
            if (version.startsWith("10")) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String token = getToken(countlyConfigPush.application, Countly.CountlyMessagingProvider.HMS, Countly.sharedInstance().L);
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
            //if SDK is initialized, use the stored value
            return Countly.sharedInstance().consent().getConsent(Countly.CountlyFeatureNames.push);
        } else {
            //if the SDK is not initialized, use the cached value
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

    /**
     * Scales a large icon down so its longer side is at most {@link #LARGE_ICON_MAX_SIZE}, keeping
     * the aspect ratio. The system shows large icons at around 64dp, and Android versions before 9
     * pass the bitmap to the notification service at full size, where an oversized one can exceed
     * the binder transaction limit and fail to be shown.
     *
     * @param bitmap icon to scale
     * @return the scaled bitmap, or the given one if it already fits
     */
    static Bitmap scaleLargeIcon(@NonNull Bitmap bitmap) {
        int longer = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longer <= LARGE_ICON_MAX_SIZE) {
            return bitmap;
        }
        float ratio = (float) LARGE_ICON_MAX_SIZE / longer;
        int width = Math.max(1, Math.round(bitmap.getWidth() * ratio));
        int height = Math.max(1, Math.round(bitmap.getHeight() * ratio));
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    /**
     * Downloads the media of a message in the background and hands the decoded bitmap to the
     * callback on the main thread. The callback receives {@code null} when the message has no media
     * or it could not be downloaded.
     *
     * @param context context whose main thread the callback runs on
     * @param msg message whose media to download
     * @param deadline {@link SystemClock#elapsedRealtime()} after which the download is given up
     * @param callback receives the bitmap
     */
    private static void loadImage(@NonNull final Context context, @NonNull final Message msg, final long deadline, @NonNull final BitmapCallback callback) {
        Utils.runInBackground(new Runnable() {
            @Override public void run() {
                final Bitmap bitmap = msg.media() == null ? null : downloadBitmap(msg.media(), "message media", deadline, MEDIA_MAX_DIMENSION);
                new Handler(context.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.call(bitmap);
                    }
                });
            }
        });
    }

    /**
     * Resolves the large icon of a message and hands it to the callback. An http(s) URL is
     * downloaded in the background and the callback runs on the main thread; any other value is
     * looked up as a drawable or mipmap resource of the app and the callback runs right away, as it
     * does when there is no large icon. The callback receives {@code null} when the icon cannot be
     * resolved, so the notification is still shown without it.
     *
     * @param context context to resolve resources in and whose main thread the callback runs on
     * @param largeIcon large icon value of the message, may be {@code null}
     * @param deadline {@link SystemClock#elapsedRealtime()} after which a download is given up
     * @param callback receives the icon, scaled with {@link #scaleLargeIcon(Bitmap)}
     */
    private static void loadLargeIcon(@NonNull final Context context, @Nullable final String largeIcon, final long deadline, @NonNull final BitmapCallback callback) {
        if (largeIcon == null) {
            callback.call(null);
            return;
        }

        String lowerCase = largeIcon.toLowerCase(Locale.ROOT);
        if (!lowerCase.startsWith("http://") && !lowerCase.startsWith("https://")) {
            Bitmap bitmap = largeIconFromResource(context, largeIcon);
            callback.call(bitmap == null ? null : scaleLargeIcon(bitmap));
            return;
        }

        final URL url;
        try {
            url = new URL(largeIcon);
        } catch (MalformedURLException e) {
            Countly.sharedInstance().L.w("[CountlyPush] loadLargeIcon, Bad large icon URL [" + largeIcon + "], ignoring");
            callback.call(null);
            return;
        }

        Utils.runInBackground(new Runnable() {
            @Override public void run() {
                Bitmap downloaded = downloadBitmap(url, "large icon", deadline, LARGE_ICON_MAX_SIZE);
                final Bitmap bitmap = downloaded == null ? null : scaleLargeIcon(downloaded);
                new Handler(context.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.call(bitmap);
                    }
                });
            }
        });
    }

    /**
     * Looks up a large icon given as the name of a drawable or mipmap resource of the app. Vector
     * and other non-bitmap drawables are drawn into a bitmap at their intrinsic size.
     *
     * @param context context of the app that owns the resource
     * @param name resource name, without the type prefix
     * @return the icon bitmap, or {@code null} if no such resource exists or it cannot be drawn
     */
    private static Bitmap largeIconFromResource(@NonNull Context context, @NonNull String name) {
        try {
            Resources resources = context.getResources();
            int id = resources.getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) {
                id = resources.getIdentifier(name, "mipmap", context.getPackageName());
            }
            if (id == 0) {
                Countly.sharedInstance().L.w("[CountlyPush] largeIconFromResource, No drawable or mipmap resource named [" + name + "], showing the notification without a large icon");
                return null;
            }

            Drawable drawable = resources.getDrawable(id, context.getTheme());
            if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
                return ((BitmapDrawable) drawable).getBitmap();
            }

            int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : LARGE_ICON_MAX_SIZE;
            int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : LARGE_ICON_MAX_SIZE;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            Countly.sharedInstance().L.w("[CountlyPush] largeIconFromResource, Cannot load large icon resource [" + name + "], showing the notification without a large icon", t);
            return null;
        }
    }

    /**
     * Downloads and decodes an image, retrying up to {@link #MEDIA_DOWNLOAD_ATTEMPTS} times while the deadline allows.
     * Must be called off the main thread. An image over {@link #MEDIA_MAX_BYTES} is left out, and a large one is
     * sampled down while decoding, so neither can run the app out of memory.
     *
     * @param url image URL
     * @param what what the image is, for logging
     * @param deadline {@link SystemClock#elapsedRealtime()} after which no further attempt starts
     * @param maxDimension longest side, in pixels, the image is decoded at
     * @return the decoded bitmap, or {@code null} if it could not be downloaded or decoded
     */
    private static Bitmap downloadBitmap(@NonNull URL url, @NonNull String what, long deadline, int maxDimension) {
        for (int attempt = 1; attempt <= MEDIA_DOWNLOAD_ATTEMPTS; attempt++) {
            long remaining = Math.min(MEDIA_DOWNLOAD_TIMEOUT, deadline - SystemClock.elapsedRealtime());
            if (remaining <= 0) {
                Countly.sharedInstance().L.w("[CountlyPush] downloadBitmap, no time left to download " + what + ", leaving it out");
                return null;
            }

            HttpURLConnection connection = null;
            InputStream input = null;
            int previousSocketTag = TrafficStats.getThreadStatsTag();
            TrafficStats.setThreadStatsTag(MEDIA_DOWNLOAD_SOCKET_TAG);
            try {
                connection = (HttpURLConnection) UtilsNetworking.openResourceConnection(url);
                connection.setDoInput(true);
                connection.setConnectTimeout((int) remaining);
                connection.setReadTimeout((int) remaining);
                connection.connect();
                input = connection.getInputStream();

                byte[] data = readAtMost(input, MEDIA_MAX_BYTES);
                if (data == null) {
                    Countly.sharedInstance().L.w("[CountlyPush] downloadBitmap, " + what + " is larger than [" + MEDIA_MAX_BYTES + "] bytes, leaving it out");
                    return null;
                }
                return decodeSampled(data, maxDimension);
            } catch (OutOfMemoryError e) {
                Countly.sharedInstance().L.w("[CountlyPush] downloadBitmap, not enough memory to decode " + what + ", leaving it out", e);
                return null;
            } catch (Exception e) {
                Countly.sharedInstance().L.e("[CountlyPush] downloadBitmap, Cannot download " + what + ", attempt [" + attempt + "]", e);
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
                TrafficStats.setThreadStatsTag(previousSocketTag);
            }
        }
        return null;
    }

    /**
     * Reads a stream to its end.
     *
     * @param input the stream
     * @param maxBytes the most bytes to accept
     * @return the bytes, or {@code null} once the stream holds more than {@code maxBytes}
     * @throws IOException when reading fails
     */
    @Nullable private static byte[] readAtMost(@NonNull InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int read;
        while ((read = input.read(buf, 0, buf.length)) != -1) {
            bytes.write(buf, 0, read);
            if (bytes.size() > maxBytes) {
                return null;
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Decodes an image with its longer side at most {@code maxDimension}. It is sampled down by the largest power of two
     * that keeps the longer side at least {@code maxDimension}, so no detail below the target is lost, and the rest is
     * scaled down exactly.
     *
     * @param data the encoded image
     * @param maxDimension longest side, in pixels, to decode at
     * @return the bitmap, or {@code null} when the data is not a decodable image
     */
    @Nullable static Bitmap decodeSampled(@NonNull byte[] data, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        int longer = Math.max(bounds.outWidth, bounds.outHeight);
        int sampleSize = 1;
        while (longer / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (decoded == null) {
            return null;
        }

        int decodedLonger = Math.max(decoded.getWidth(), decoded.getHeight());
        if (decodedLonger <= maxDimension) {
            return decoded;
        }
        float scale = (float) maxDimension / decodedLonger;
        int width = Math.max(1, Math.round(decoded.getWidth() * scale));
        int height = Math.max(1, Math.round(decoded.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) {
            decoded.recycle();
        }
        return scaled;
    }
}
