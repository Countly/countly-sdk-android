package ly.count.android.sdk.internal;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Class managing all networking operations.
 * Contract:
 * <ul>
 *     <li>Instantiated in a {@link android.app.Service} once.</li>
 *     <li>Doesn't have any queues, sends one request at a time (guaranteed to have maximum one {@link #send(Request)} unresolved call at a time).</li>
 *     <li>Returns a {@link Future} which resolves to either success or a failure.</li>
 *     <li>Doesn't do any storage or configuration-related operations, doesn't call modules, etc.</li>
 * </ul>
 */

//class Network extends ModuleBase { - may be

class Network {
    Network() {
    }

    /**
     * @see Module#init(InternalConfig)
     *
     * @param config
     * @throws IllegalArgumentException
     */
    void init (InternalConfig config) throws IllegalArgumentException {
        // ssl config (cert & public key pinning)
        // GET/POST handling
        // sha1 signing
        // 301/302 handling, probably configurable (like allowFollowingRedirects) and with response
        //      validation because public WiFi APs return 30X to login page which returns 200
        // exponential back off - not sure where to have it: either some
        //      sleeping delay in Future thread or in Service thread, probably better having it here
        // network status callbacks - may be
        // APM stuff - later
    }

        Future<Boolean> send(Request request) { // - may be, null = error
//    Future<Response> send(Request request) { - may be
//    Future<Request> send(Request request) {
            return new Future<Boolean>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return false;
                }

                @Override
                public Boolean get() throws InterruptedException, ExecutionException {
                    return Boolean.TRUE;
                }

                @Override
                public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    return Boolean.TRUE;
                }
            };
//        return null;
    }
}
