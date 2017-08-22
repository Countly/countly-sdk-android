package ly.count.android.sdk.internal;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * {@link Service} which monitors {@link Storage} for new requests and sends them via {@link Network}.
 *
 */
public class CountlyService extends android.app.Service {
    static final String CMD = "ly.count.CMD";
    static final int CMD_START = 1;
    static final int CMD_PING  = 2;
    static final int CMD_STOP  = 3;

    /**
     * Core instance is being run in {@link InternalConfig#limited} mode.
     */
    private Core core;

    /**
     * Tasks {@link Thread} is used to wait for {}
     */
    private Tasks tasks;
    private Network network;
    private Future<Boolean> future = null;
    private boolean shutdown = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d("[service] Creating");
        this.core = new Core();
        this.tasks = new Tasks();
        this.network = new Network();
        if (!this.core.init(null, getApplication())) {
            // TODO: inconsistent state, TBD
            this.core = null;
            stop();
        } else {
            this.future = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.core == null) {
            Log.d("[service] No core, stopping");
            stopSelf(startId);
        } else {
            if (intent == null) {
                // service restarted after being killed - restart queue
                Log.d("[service] No intent, restarting");
                start();
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_START) {
                // service started on app launch - restart queue
                Log.d("[service] Starting");
                start();
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_PING) {
                // possibly request have been added
                Log.d("[service] Ping");
                check();
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_STOP) {
                Log.d("[service] Stopping");
                // clean up, prepare for shutdown
                stop();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d("[service] Destroying");
    }

    @Override
    public void onLowMemory() {
        Log.d("[service] Low Memory");
    }

    @Override
    public void onTrimMemory(int level) {
        Log.d("[service] Trim Memory " + level);
    }

    private void start() {
        check();
    }

    private synchronized void stop() {
        shutdown = true;
        if (future == null || future.isDone()) {
            Log.d("[service] Nothing is going on, stopping");
            stopSelf();
        }
    }

    private synchronized void check() {
        if (!shutdown && (future == null || future.isDone())) {
            future = tasks.run(submit());
        }
    }

    private Tasks.Task<Boolean> submit() {
        return new Tasks.Task<Boolean>(0L) {
            @Override
            public Boolean call() throws Exception {
                Request request = Storage.readOne(new Request(0L), true);
                if (request == null) {
                    return false;
                } else {
                    Log.d("[service] Sending request " + request.storageId() + ": " + request.toString());
                    try {
                        boolean result = network.send(request).get();
                        Log.d("[service] Request " + request.storageId() + " sent?: " + result);
                        if (result) {
                            Storage.remove(request);
                        }
                        synchronized (CountlyService.this) {
                            if (shutdown) {
                                future = null;
                                stop();
                            } else {
                                future = tasks.run(submit());
                            }
                        }
                        return result;
                    } catch (InterruptedException e) {
                        Log.i("Interrupted while sending request " + request.storageId(), e);
                        return false;
                    } catch (ExecutionException e) {
                        Log.i("Interrupted while sending request " + request.storageId(), e);
                        return false;
                    } catch (CancellationException e) {
                        Log.i("Cancelled while sending request " + request.storageId(), e);
                        return false;
                    } catch (Throwable t) {
                        Log.i("Exception in network task " + request.storageId(), t);
                        synchronized (CountlyService.this) {
                            future = null;
                            shutdown = true;
                        }
                        stopSelf();
                        return false;
                    }
                }
            }
        };
    }
}
