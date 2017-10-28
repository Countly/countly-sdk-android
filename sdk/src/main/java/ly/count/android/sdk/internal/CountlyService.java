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
    private Tasks tasks, networkTasks;
    private InternalConfig config;
    private Network network;
    private boolean shutdown = false, networking = false;
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
        this.networkTasks = new Tasks("net");
        this.config = Core.initForService(this);
        if (config == null) {
            // TODO: inconsistent state, TBD
            this.core = null;
            stop();
        } else {
            this.core = Core.instance;
            this.core.onLimitedContextAcquired(this);
            if (config.getDeviceId() != null) {
                this.network = new Network();
                this.network.init(config);
            }
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
                    if (config != null && config.getDeviceId() != null && network == null) {
                        L.i("Starting sending requests");
                        this.network = new Network();
                        this.network.init(config);
                    }
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
        tasks.run(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
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
        if (tasks.isRunning()) {
            L.d("Nothing is going on, stopping");
            stopSelf();
        }
    }

    private synchronized void check() {
        if (!shutdown && !tasks.isRunning() && !networkTasks.isRunning() && config.getDeviceId() != null && network != null) {
            tasks.run(submit());
        }
    }

    public Tasks.Task<Boolean> submit() {
        return new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
            @Override
            public Boolean call() throws Exception {
                if (networkTasks.isRunning()) {
                    return false;
                }

                final Request request = Storage.readOne(ctx, new Request(0L), true);
                if (request == null) {
                    return false;
                } else {
                    L.d("Preparing request: " + request);
                    final Boolean check = CountlyService.this.core.isRequestReady(request);
                    if (check == null) {
                        L.d("Request is not ready yet: " + request);
                        return false;
                    } else if (check.equals(Boolean.FALSE)){
                        L.d("Request won't be ready, removing: " + request);
                        Storage.remove(ctx, request);
                        return true;
                    } else {
                        networkTasks.run(network.send(request), new Tasks.Callback<Network.RequestResult>() {
                            @Override
                            public void call(Network.RequestResult requestResult) throws Exception {
                                L.d("Request " + request.storageId() + " sent?: " + requestResult);
                                if (requestResult == null || requestResult == Network.RequestResult.REMOVE || requestResult == Network.RequestResult.OK) {
                                    Storage.remove(ctx, request);
                                }
                                if (shutdown) {
                                    stop();
                                } else {
                                    check();
                                }
                            }
                        });
                        return true;
                    }
                }
            }
        };
    }
}
