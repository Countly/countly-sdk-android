package ly.count.android.sdk.internal;

import java.net.URL;
import java.util.Arrays;
import java.util.Objects;

/**
 * Created by artem on 06/01/2017.
 */

class Request {
    private static final String P_DEVICE_ID = "device_id";

    /**
     * This string is written to request file to ensure it can be fully read from other end of queue.
     */
    private static final String EOR = "\n[CLY][CLY][CLY]";

    /**
     * Params without device_id
     */
    protected Params params;

    /**
     * Response code
     */
    protected int code = -1;

    /**
     * Response string??
     */
    String response;

    private Request(Object... params) {
        if (params.length == 1 && params[0] instanceof Object[]) {
            this.params = new Params(params[0]);
        } else {
            this.params = new Params(params);
        }
    }

    private Request(String params) {
        this.params = new Params(params);
    }

    String serialize() {
        return params + EOR;
    }

    boolean isSent() { return code != -1; }
    boolean isSuccess() { return code >= 200 && code < 300; }
    boolean isError() { return isSent() && !isSuccess(); }

    boolean isGettable(URL serverUrl, String deviceId) {
        return isGettable(serverUrl, deviceId, 0);
    }

    boolean isGettable(URL serverUrl, String deviceId, int addition) {
        return (serverUrl.toString().length() + 1 + params.length() + P_DEVICE_ID.length() + 1 + deviceId.length() + addition) < 1024;
    }

    static Request build(Object... params){ return new Request(params); }

    static Request load(String data){
        if (data.lastIndexOf(EOR) != data.length() - EOR.length()) {
            return null;
        } else {
            return new Request(data.substring(data.length() - EOR.length()));
        }
    }
}
