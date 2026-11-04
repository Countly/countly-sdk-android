package ly.count.android.sdk.hub;

import android.os.Bundle;

oneway interface IHubCallback {
    void onResponse(in Bundle response);
}
