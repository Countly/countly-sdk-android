package ly.count.android.sdk;

public class ModuleAttribution extends ModuleBase {

    Attribution attributionInterface;

    ModuleAttribution(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleAttribution] Initialising");

        attributionInterface = new ModuleAttribution.Attribution();
    }

    @Override
    public void halt() {
        attributionInterface = null;
    }

    public class Attribution {

    }
}
