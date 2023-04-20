package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.List;

class ModuleConfiguration extends ModuleBase implements ConfigurationProvider {
    ModuleConfiguration(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        config.configProvider = this;
        configProvider = this;
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {

    }

    @Override
    void halt() {

    }

    @Override public boolean getConfigBool(ConfigBool key) {
        boolean ret;

        switch (key) {
            case networkingEnabled:
                ret = true;
                break;
            case trackingEnabled:
                ret = true;
                break;
            default:
                L.e("ModuleConfiguration, SDK is requesting an unconfigured key [" + key + "]");
                ret = true;
                break;
        }

        return ret;
    }
}
