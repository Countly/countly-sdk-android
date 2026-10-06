package ly.count.android.sdk.hub;

import android.os.Bundle;

interface ICountlyHub {
    int getProtocolVersion();

    Bundle exchange(in Bundle request);
}
