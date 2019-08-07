package ly.count.sdk.java.internal;

import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.SDKCore;

public class SDK extends SDKStorage {
    @Override
    public void onRequest(ly.count.sdk.internal.CtxCore ctx, Request request) {
        onSignal(ctx, SDKCore.Signal.Ping.getIndex(), null);
    }
}
