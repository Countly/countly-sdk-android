package ly.count.android.sdk.internal;

import java.net.URL;
import java.nio.charset.Charset;

/**
 * Class which encapsulates request logic and manipulation: building, status of sending, etc.
 */

class Request implements Storable {
    private final Long id;

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
     * Create request from params with current time as id.
     */
    protected Request(Object... params) {
        this.id = Utils.uniqueTimestamp();
        this.params = new Params(params);
    }

    /**
     * Deserialization constructor (use existing id).
     */
    protected Request(Long id) {
        this.id = id;
    }

    boolean isGettable(URL serverUrl, String deviceId) {
        return isGettable(serverUrl, deviceId, 0);
    }

    boolean isGettable(URL serverUrl, String deviceId, int addition) {
        return (serverUrl.toString().length() + 1 + params.length() + P_DEVICE_ID.length() + 1 + deviceId.length() + addition) < 1024;
    }

    static Request build(Object... params){ return new Request(params); }

    @Override
    public Long storageId() {
        return id;
    }

    @Override
    public String storagePrefix() {
        return "request";
    }

    @Override
    public byte[] store() {
        return (params.toString() + EOR).getBytes();
    }

    @Override
    public boolean restore(byte[] data) {
        String str = new String(data, Charset.forName(Utils.UTF8));
        if (str.endsWith(EOR)) {
            params = new Params(0, str.length() - EOR.length());
            return true;
        } else {
            return false;
        }
    }
}
