package ly.count.android.sdk.hub;

import android.os.Bundle;
import ly.count.android.sdk.hub.IHubCallback;

interface ICountlyHub {
    int getProtocolVersion();

    Bundle submit(in Bundle request, IHubCallback callback);
}
