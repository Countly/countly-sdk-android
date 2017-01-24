package ly.count.android.sdk;

import android.app.Application;

import ly.count.android.sdk.internal.Core;

/**
 * Created by artem on 28/12/2016.
 */

public class CountlyNeo {

    interface CreationOverride {
        Core createCore(Config config);
    }

    private static CountlyNeo instance;
    final Core core;

    CountlyNeo(Config config) {
        this(new Core(config));
    }

    CountlyNeo(Core core) {
        this.core = core;
    }

    public static void init (final Application application, final Config config) {
        init(application, config, null);
    }

    static void init (final Application application, final Config config, final CreationOverride override) {
        if (instance != null) {
            // TODO: shutdown if already running
        }

        if(override != null) {
            instance = new CountlyNeo(override.createCore(config));
        } else {
            instance = new CountlyNeo(config);
        }
        instance.core.onApplicationCreated(application);
    }

    // TODO: add all those recordEvent / old init / other deprecated methods
}
