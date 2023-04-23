package ly.count.android.sdk;

interface ImmediateRequestI {
    void doWork(String requestData, String customEndpoint, ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, ImmediateRequestMaker.InternalImmediateRequestCallback callback, ModuleLog log);
}
