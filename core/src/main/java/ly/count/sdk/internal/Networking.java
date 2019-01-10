package ly.count.sdk.internal;

public interface Networking {
    void init(CtxCore ctx);
    boolean isSending();
    boolean check(CtxCore ctx);
    void stop(CtxCore ctx);
}
