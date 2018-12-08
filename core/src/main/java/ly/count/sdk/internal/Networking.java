package ly.count.sdk.internal;

public interface Networking {
    void init(Ctx ctx);
    boolean isSending();
    boolean check(Ctx ctx);
    void stop(Ctx ctx);
}
