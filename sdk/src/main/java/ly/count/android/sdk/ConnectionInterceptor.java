package ly.count.android.sdk;

import java.net.HttpURLConnection;

/**
 * Interface to intercept Countly requests
 */
public interface ConnectionInterceptor {

    /**
     * This is called for each request which is send by Countly
     *
     * @param connection The connection which is about to be send
     * @param body Body of the connection, null for GET requests
     * @return HttpURLConnection which is used for connection
     */
    HttpURLConnection intercept(HttpURLConnection connection, byte[] body);
}