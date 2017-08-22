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
     * @param context either Application instance in case of normal startup, or context in case of implicit start
     * @param config config instance in case of normal startup, null otherwise
     */
    private static void initInternal (Context context, Config config) {
        if (instance != null) {
            // TODO: shutdown if already running
            instance = null;
        }

        instance = new CountlyNeo(new Core());
        if (!instance.core.init(config, context)) {
            // TODO: inconsistent state, couldn't init, TBD
        }

        if (context == null) {
            Log.wtf("Context cannot be null");
        } else if (context instanceof Application) {
            instance.core.onApplicationCreated((Application)context);
        } else {
            instance.core.onContextCreated(context);
        }
    }

    /**
     * Returns whether Countly SDK has been already initialized or not.
     *
     * @return true if already initialized
     */
    public static boolean isInitialized() { return instance != null; }

    // TODO: add all those recordEvent / old init / other deprecated methods with check on instance not null (return doing nothing when it's null)
}
