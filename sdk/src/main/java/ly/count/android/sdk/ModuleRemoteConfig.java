package ly.count.android.sdk;

class ModuleRemoteConfig extends ModuleBase {

    //if set to true, it will automatically download remote configs on module startup
    boolean remoteConfigAutomaticUpdateEnabled = false;
    ly.count.android.sdk.RemoteConfig.RemoteConfigCallback remoteConfigInitCallback = null;

    RemoteConfig remoteConfigInterface = null;

    ModuleRemoteConfig(Countly cly, CountlyConfig config) {
        super(cly);

        remoteConfigInterface = new RemoteConfig();
    }

    @Override
    public void halt() {
        remoteConfigInterface = null;
    }

    public class RemoteConfig {

    }
}
