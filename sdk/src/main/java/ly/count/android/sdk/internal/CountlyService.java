package ly.count.android.sdk.internal;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import ly.count.android.sdk.Config;

/**
 * {@link Service} which monitors {@link Storage} for new requests and sends them via {@link Network}.
 *
 */
public class CountlyService extends android.app.Service {
    private static final Log.Module L = Log.module("Service");

    static final String CMD = "ly.count.CMD";
    static final int CMD_START = 1;
    static final int CMD_PING  = 2;
    static final int CMD_STOP  = 3;
    static final int CMD_DEVICE_ID  = 10;
    static final int CMD_CRASH  = 20;

    static final String PARAM_ID = "id";
    static final String PARAM_OLD_ID = "old";
    static final String PARAM_CRASH_ID = "crash_id";

    /**
     * Core instance is being run in {@link InternalConfig#limited} mode.
     */
    private Core core;

    /**
     * Tasks {@link Thread} is used to wait for {}
     */
    private Tasks tasks;
    private InternalConfig config;
    private Network network;
    private Future<Boolean> future = null;
    private boolean shutdown = false;
    private Context ctx = null;
    private List<Long> crashes = null;
    private List<Long> sessions = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        L.d("Creating");
        this.ctx = new ContextImpl(getApplicationContext());
        this.crashes = new ArrayList<>();
        this.sessions = new ArrayList<>();
        this.tasks = new Tasks("service");
        this.network = new Network();
        this.config = Core.initForService(this);
        if (config == null) {
            // TODO: inconsistent state, TBD
            this.core = null;
            stop();
        } else {
            this.core = Core.instance;
            this.core.onLimitedContextAcquired(this);
            this.network.init(config);
            this.future = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.core == null) {
            L.d("No core, stopping");
            stopSelf(startId);
        } else {
            if (intent == null) {
                // service restarted after being killed - restart queue
                L.d("No intent, restarting");
                start();
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_START) {
                // service started on app launch - restart queue
                L.d("Starting");
                start();
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_PING) {
                // possibly request have been added
                L.d("Ping");
                check();
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_STOP) {
                L.d("Stopping");
                // clean up, prepare for shutdown
                stop();
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_CRASH && intent.hasExtra(PARAM_CRASH_ID)) {
                Long id = intent.getLongExtra(PARAM_CRASH_ID, -1L);
                L.d("Got a crash " + id);
                processCrash(id);
            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_DEVICE_ID) {
                L.d("Device id");
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
                    Context ctx = new ContextImpl(CountlyService.this);
                    Core.onDeviceId(ctx, id, old);
                    check();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        L.d("Destroying");
    }

    @Override
    public void onLowMemory() {
        L.d("Low Memory");
    }

    @Override
    public void onTrimMemory(int level) {
        L.d("Trim Memory " + level);
    }

    void start() {
        future = tasks.run(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
            @Override
            public Boolean call() throws Exception {
                crashes = new ArrayList<Long>();

                for (Long id : crashes) {
                    L.i("Found unprocessed crash " + id);
                    processCrash(id);
                }

                sessions = Storage.list(ctx, SessionImpl.getStoragePrefix());
                L.d("recovering " + sessions.size() + " sessions");
                for (Long id : sessions) {
                    L.d("recovering session " + id);
                    SessionImpl session = Storage.read(ctx, new SessionImpl(ctx, id));
                    if (session == null) {
                        L.e("no session with id " + id + " found while recovering");
                    } else {
                        Boolean success = session.recover(config);
                        L.d("session " + id + " recovery " + (success == null ? "won't recover" : success ? "success" : "failure"));
                    }
                }
                check();
                return true;
            }
        });
    }

    private boolean processCrash(Long id) {
        CrashImpl crash = new CrashImpl(id);
        crash = Storage.read(ctx, crash);
        if (crash == null) {
            L.e("Cannot read crash from storage, skipping");
            return false;
        }

        Request request = ModuleRequests.nonSessionRequest(config);
        ModuleCrash.putCrashIntoParams(crash, request.params);
        if (Storage.push(ctx, request)) {
            L.i("Added request " + request.storageId() + " instead of crash " + crash.storageId());
            Boolean success = Storage.remove(ctx, crash);
            return success == null ? false : success;
        } else {
            L.e("Couldn't write request " + request.storageId() + " instead of crash " + crash.storageId());
            return false;
        }
    }

    private synchronized void stop() {
        shutdown = true;
        if (future == null || future.isDone()) {
            L.d("Nothing is going on, stopping");
            stopSelf();
        }
    }

    private synchronized void check() {
        if (!shutdown && (future == null || future.isDone())) {
            future = tasks.run(submit());
        }
    }

    public Tasks.Task<Boolean> submit() {
        return new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
            @Override
            public Boolean call() throws Exception {
                Request request = Storage.readOne(ctx, new Request(0L), true);
                if (request == null) {
                    return false;
                } else {
                    L.d("Preparing request: " + request);
                    try {
                        boolean result;
                        Boolean check = CountlyService.this.core.isRequestReady(request);
                        if (check == null) {
                            L.d("Request is not ready yet: " + request);
                            result = false;
                        } else if (check.equals(Boolean.FALSE)){
                            L.d("Request won't be ready, removing: " + request);
                            Storage.remove(ctx, request);
                            result = true;
                        } else {
                            L.d("Sending request: " + request.toString());
                            Network.NetworkResponse nr = network.send(request).get();
                            result = nr.requestSucceeded;
                            L.d("Request " + request.storageId() + " sent?: " + result);
                            if (result) {
                                Storage.remove(ctx, request);
                            }
                        }
                        return result;
                    } catch (InterruptedException e) {
                        L.i("Interrupted while sending request " + request.storageId(), e);
                        return false;
                    } catch (ExecutionException e) {
                        L.i("Interrupted while sending request " + request.storageId(), e);
                        return false;
                    } catch (CancellationException e) {
                        L.i("Cancelled while sending request " + request.storageId(), e);
                        return false;
                    } catch (Throwable t) {
                        L.i("Exception in network task " + request.storageId(), t);
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
