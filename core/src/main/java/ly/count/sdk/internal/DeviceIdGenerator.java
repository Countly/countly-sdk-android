package ly.count.sdk.internal;

public interface DeviceIdGenerator {
    boolean isAvailable();
    String generate(Ctx context, int realm);
}
