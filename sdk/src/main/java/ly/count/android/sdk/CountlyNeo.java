package ly.count.android.sdk;

import android.app.Application;
import android.content.Context;

import ly.count.android.sdk.internal.Core;
import ly.count.android.sdk.internal.Log;

/**
 * Created by artem on 28/12/2016.
 */

public class CountlyNeo {
    private static CountlyNeo instance;
    private final Core core;

    private CountlyNeo(Core core) {
        this.core = core;
    }

    /**
     * Initialize Countly.
     * To be called from {@link Application#onCreate()}.
     *
     * @param application Application instance
     * @param config configuration object
     */
    public static void init (final Application application, final Config config) {
        if (config == null) {
            Log.wtf("Config cannot be null");
        } else {
            initInternal(application, config);
        }
    }

    /**
     * Init Countly
     *
     * @param application current Application instance
     * @param config config instance in case of normal startup, null otherwise
     */
    private static void initInternal (Application application, Config config) {
        if (instance != null) {
            // TODO: shutdown if already running
            instance = null;
        }

        Core core = Core.initForApplication(config, application);
        if (core == null) {
            // TODO: inconsistent state, couldn't init, TBD
            return;
        }
        instance = new CountlyNeo(core);
        instance.core.onContextAcquired(application);
    }

    /**
     * Returns whether Countly SDK has been already initialized or not.
     *
     * @return true if already initialized
     */
    public static boolean isInitialized() { return instance != null; }

    /**
     * Returns current {@link Session} if any.
     *
     * @return session instance if there is one, {@code null} if there is no current session or if Countly is not initialized yet
     */
    public static Session currentSession(){
        return isInitialized() ? instance.core.sessionLeading() : null;
    }

    /**
     * Returns current {@link Session} if there is one already started or new {@link Session} object
     * if no active session is out there.
     *
     * @return current session instance if there is one, new session instance if there is no current session or {@code null} if Countly is not initialized yet
     */
    public static Session currentOrNewSession(Context context) {
        return isInitialized() ? instance.core.sessionLeadingOrNew(context) : null;
    }

    // TODO: add all those recordEvent / old init / other deprecated methods with check on instance not null (return doing nothing when it's null)
}
