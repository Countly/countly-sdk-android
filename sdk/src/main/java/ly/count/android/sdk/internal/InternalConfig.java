package ly.count.android.sdk.internal;

import java.net.MalformedURLException;
import java.util.ArrayList;

import ly.count.android.sdk.Config;

/**
 * Internal to Countly SDK configuration class. Can and should contain options hidden from outside.
 * Only members of {@link InternalConfig} can be changed, members of {@link Config} are non-modifiable.
 */
final class InternalConfig extends Config {

    /**
     * List of modules built based on Feature set selected.
     */
    private ArrayList<Module> modules;

    /**
     * Shouldn't be used!
     */
    InternalConfig(String url, String appKey) throws MalformedURLException {
        super(url, appKey);
        throw new IllegalStateException("InternalConfig(url, appKey) should not be used");
    }

    InternalConfig(Config config) throws MalformedURLException {
        super(config.getServerURL().toString(), config.getServerAppKey());
        this.features.addAll(config.getFeatures());
        this.usePOST = config.isUsePOST();
        this.loggingTag = config.getLoggingTag();
        this.loggingLevel = config.getLoggingLevel();
        this.programmaticSessionsControl = config.isProgrammaticSessionsControl();
        this.sdkVersion = config.getSdkVersion();
    }
}
