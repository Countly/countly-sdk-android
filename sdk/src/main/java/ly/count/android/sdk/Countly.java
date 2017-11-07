package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.os.Bundle;

import ly.count.android.sdk.internal.ContextImpl;
import ly.count.android.sdk.internal.Core;
import ly.count.android.sdk.internal.Utils;

/**
 * Main Countly SDK API class.
 * <ul>
 *     <li>Initialize Countly SDK using {@link #init(Application, Config)}.</li>
 *     <li>Stop Countly SDK with {@link #stop(Context, boolean)} if needed.</li>
 *     <li>Call {@link #onActivityCreated(Activity, Bundle)}, {@link #onActivityStarted(Activity)} {@link #onActivityStopped(Activity)} if targeting API levels < 14.</li>
 *     <li>Use {@link #session(Context)} to get a {@link Session} instance.</li>
 *     <li>Use {@link #login(Context, String)} & {@link #logout(Context)} when user logs in & logs out.</li>
 * </ul>
 */

public class Countly extends CountlyLifecycle {

    protected Countly(Core core) {
        super(core);
    }

    /**
     * Returns current {@link Session} if any or creates new {@link Session} instance.
     *
     * NOTE: {@link Session} instances can expire, for example when {@link ly.count.android.sdk.Config.DID} changes.
     * It also holds application context.
     * So either do not store {@link Session} instances in any static variables and use {@code #session()} every time you need it,
     * or check {@link Session#isActive()} before using it.
     *
     * @param context current Android Context
     * @return session current {@link Session} instance if there is one, creates new one if current is not set
     */
    public static Session session(Context context){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        }
        return instance.core.session(new ContextImpl(context), null);
    }

    /**
     * Get current User Profile object.
     *
     * @see User#edit() to get {@link UserEditor} object
     * @see UserEditor#commit() to submit changes to the server
     * @return current User Profile instance
     */
    public static User user() {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        }
        return instance.core.user();
    }

    /**
     * Token refresh callback to be called from {@code FirebaseInstanceIdService} whenever new token is acquired.
     *
     * @param service context to run in (supposed to be called from {@code FirebaseInstanceIdService})
     * @param token String token to be sent to Countly server
     */
    public static void onFirebaseToken(Service service, String token) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            Core.onPushTokenRefresh(service, token);
        }
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     *
     * @deprecated since 18.X, use {@link #login(Context, String)}} instead
     * @param context Context to run in
     * @param id new user / device id string
     */
    public static void changeDeviceId(Context context, String id) {
        if (Utils.isEmpty(id)) {
            logout(context);
        } else {
            login(context, id);
        }
    }

    /**
     * Login function to set device (user) id on Countly server to the string specified here.
     * Closes current session, then starts new one automatically.
     *
     * @param context Context to run in
     * @param id new user / device id string, cannot be empty
     */
    public static void login(Context context, String id) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            instance.core.login(context, id);
        }
    }

    /**
     * Logout function to make current user anonymous (that is with random id according to
     * {@link Config#deviceIdStrategy} and such). Obviously makes sense only after a call to {@link #login(Context, String)}.
     *
     * Closes current session, opens new one if {@link Config#programmaticSessionsControl} is off, acquires device id.
     *
     * @param context Context to run in
     */
    public static void logout(Context context) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            instance.core.logout(context);
        }
    }


    // TODO: add all those recordEvent / old init / other deprecated methods with check on instance not null (return doing nothing when it's null)
}
