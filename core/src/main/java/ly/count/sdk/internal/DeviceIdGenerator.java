package ly.count.sdk.internal;

public interface DeviceIdGenerator {
    boolean isAvailable();
    String generate(CtxCore context, int realm);
}
