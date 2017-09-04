package ly.count.android.sdk.internal;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import ly.count.android.sdk.Config;

/**
 * {@link Service} which monitors {@link Storage} for new requests and sends them via {@link Network}.
 *
 */
public class CountlyService extends android.app.Service {
    static final String CMD = "ly.count.CMD";
    static final int CMD_START = 1;
    static final int CMD_PING  = 2;
    static final int CMD_STOP  = 3;
    static final int CMD_DEVICE_ID  = 10;
    static final int CMD_CRASH  = 20;

    static final String PARAM_ID = "id";
    static final String PARAM_OLD_ID = "old";

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
        this.tasks = new Tasks();
        this.network = new Network();
        if (Core.initForService(this) == null) {
            // TODO: inconsistent state, TBD
            this.core = null;
            stop();
        } else {
            this.core = Core.instance;
            this.core.onLimitedContextAcquired(this);
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
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_DEVICE_ID) {
                Log.d("[service] Device id");
                // reread config & notify modules
                Config.DID id = null;
                Config.DID old = null;
                boolean success = true;
                if (intent.hasExtra(PARAM_ID)) {
                    id = new Config.DID(Config.DeviceIdRealm.DEVICE_ID, Config.DeviceIdStrategy.OPEN_UDID, null);
                    success = id.restore(intent.getByteArrayExtra(PARAM_ID));
                }
                if (intent.hasExtra(PARAM_OLD_ID)) {
                    old = new Config.DID(Config.DeviceIdRealm.DEVICE_ID, Config.DeviceIdStrategy.OPEN_UDID, null);
                    success = success && !old.restore(intent.getByteArrayExtra(PARAM_OLD_ID));
                }
                if (success) {
                    Core.onDeviceId(id, old);
                    check();
                }
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
                    Log.d("[service] Preparing request: " + request);
                    try {
                        boolean result;
                        Boolean check = CountlyService.this.core.isRequestReady(request);
                        if (check == null) {
                            Log.d("[service] Request is not ready yet: " + request);
                            result = false;
                        } else if (check.equals(Boolean.FALSE)){
                            Log.d("[service] Request won't be ready, removing: " + request);
                            Storage.remove(request);
                            result = true;
                        } else {
                            Log.d("[service] Sending request: " + request.toString());
                            result = network.send(request).get();
                            Log.d("[service] Request " + request.storageId() + " sent?: " + result);
                            if (result) {
                                Storage.remove(request);
                            }
                        }
                        return result;
                    } catch (InterruptedException e) {
                        Log.i("[service] Interrupted while sending request " + request.storageId(), e);
                        return false;
                    } catch (ExecutionException e) {
                        Log.i("[service] Interrupted while sending request " + request.storageId(), e);
                        return false;
                    } catch (CancellationException e) {
                        Log.i("[service] Cancelled while sending request " + request.storageId(), e);
                        return false;
                    } catch (Throwable t) {
                        Log.i("[service] Exception in network task " + request.storageId(), t);
                        shutdown = true;
                        return false;
                    } finally {
                        synchronized (CountlyService.this) {
                            if (shutdown) {
                                future = null;
                                stop();
                            } else {
                                future = tasks.run(submit());
                            }
                        }
                    }
                }
            }
        };
    }
}
