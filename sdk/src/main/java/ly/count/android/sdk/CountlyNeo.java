package ly.count.android.sdk;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.os.Bundle;

import ly.count.android.sdk.internal.Core;
import ly.count.android.sdk.internal.Utils;

/**
 * Created by artem on 28/12/2016.
 */

public class CountlyNeo extends CountlyNeoLifecycle {

    protected CountlyNeo(Core core) {
        super(core);
    }

    /**
     * Returns current {@link Session} if any.
     *
     * NOTE: {@link Session} instances can expire, for example when {@link ly.count.android.sdk.Config.DID} changes.
     * It also holds application context.
     * So either do not store {@link Session} instances in any static variables and use {@code #currentSession()} every time you need it,
     * or check {@link Session#isActive()} / {@link Session#isLeading()} before use it.
     *
     * @return session instance if there is one, {@code null} if there is no current session or if Countly is not initialized yet
     */
    public static Session currentSession(){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
            return null;
        }
        return instance.core.sessionLeading();
    }

    /**
     * Returns current {@link Session} if there is one already started or new {@link Session} object
     * if no active session is out there.
     *
     * NOTE: {@link Session} instances can expire, for example when {@link ly.count.android.sdk.Config.DID} changes.
     * It also holds application context.
     * So either do not store {@link Session} instances in any static variables and use {@code #currentSession()} every time you need it,
     * or check {@link Session#isActive()} / {@link Session#isLeading()} before use it.
     *
     * @return current session instance if there is one, new session instance if there is no current session or {@code null} if Countly is not initialized yet
     */
    public static Session currentOrNewSession(Context context) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
            return null;
        }
        return instance.core.sessionLeadingOrNew(context);
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
     * In case {@link Config#programmaticSessionsControl} is on it only closes current session, so {@link #currentSession()} will return {@code null} soon.
     * In case {@link Config#programmaticSessionsControl} is off it closes current session, then starts new one.
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
