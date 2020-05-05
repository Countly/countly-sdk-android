package ly.count.android.sdk;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLConnection;
import java.util.Iterator;

public class RemoteConfig {

    public interface RemoteConfigCallback {
        /**
         * Called after receiving remote config update result
         *
         * @param error if is null, it means that no errors were encountered
         */
        void callback(String error);
    }
}
