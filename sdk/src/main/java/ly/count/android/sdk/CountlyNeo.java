package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import ly.count.android.sdk.internal.Core;

/**
 * Created by artem on 28/12/2016.
 */

public class CountlyNeo {
    private static CountlyNeo instance;
    private final Core core;

    private CountlyNeo(Config config) {
        this.core = new Core(config);
    }

    public static void init (final Application application, final Config config) {
        if (instance != null) {
            // TODO: shutdown if already running
        }

        instance = new CountlyNeo(config);
        instance.core.onApplicationCreated(application);
    }

    // TODO: add all those recordEvent / old init / other deprecated methods
}
