package ly.count.sdk.android.internal;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

//import ly.count.sdk.internal.Network;
//import ly.count.sdk.internal.SessionImpl;
import ly.count.sdk.internal.Storage;

/**
 * {@link Service} which monitors {@link Storage} for new requests and sends them via {@link Network}.
 *
 */
public class CountlyService extends android.app.Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
//    private static final Log.Module L = Log.module("Service");
//
    static final String CMD = "ly.count.cmd";
    static final String PARAM_1 = "ly.count.param1";
    static final String PARAM_2 = "ly.count.param2";

//    /**
//     * Core instance is being run in {@link InternalConfig#limited} mode.
//     */
//    private Core core;
//
//    /**
//     * Tasks {@link Thread} is used to wait for {}
//     */
//    private Tasks tasks, networkTasks;
//    private InternalConfig config;
//    private Network network;
//    private boolean shutdown = false, networking = false;
//    private Ctx ctx = null;
//    private List<Long> crashes = null;
//    private List<Long> sessions = null;
//    private boolean singleProcess = false;
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    @Override
//    public void onCreate() {
//        L.d("Creating");
//        this.ctx = new CtxImpl(getApplicationContext());
//        this.crashes = new ArrayList<>();
//        this.sessions = new ArrayList<>();
//        this.tasks = new Tasks("service");
//        this.networkTasks = new Tasks("net");
//        this.singleProcess = Device.isSingleProcess(getApplicationContext());
//        this.config = Core.initialized();
//        if (this.config == null) {
//            // TODO: inconsistent state, TBD
//            this.core = null;
//            L.e("CORE NOT INITIALIZED");
//            stop();
//        } else {
//            L.i("Core initialized, user " + (Core.instance.user() == null ? "null" : "not null"));
//            this.core = Core.instance;
//            if (config.getDeviceId() != null) {
//                Ctx ctx = new CtxImpl(CountlyService.this);
//                Core.onDeviceId(ctx, config.getDeviceId(), config.getDeviceId());
//                this.network = new Network();
//                try {
//                    this.network.init(config);
//                } catch (Throwable t) {
//                    Log.e("Error while initializing network", t);
//                    this.core.stop(getApplicationContext(), false);
//                    this.core = null;
//                    this.network = null;
//                }
//            }
//        }
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (this.core == null) {
//            L.d("No core, stopping");
//            stopSelf(startId);
//        } else {
//            if (intent == null) {
//                // service restarted after being killed - restart queue
//                L.d("No intent, restarting");
//                start();
//            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_START) {
//                // service started on app launch - restart queue
//                L.d("Starting");
//                start();
//            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_PING) {
//                // possibly request have been added
//                L.d("Ping");
//                check();
//            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_STOP) {
//                L.d("Stopping");
//                // clean up, prepare for shutdown
//                stop();
//            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_CRASH && intent.hasExtra(PARAM_CRASH_ID)) {
//                Long id = intent.getLongExtra(PARAM_CRASH_ID, -1L);
//                L.d("Got a crash " + id);
//                processCrash(id);
//                check();
//            } else if (intent.hasExtra(CMD) && intent.getIntExtra(CMD, -1) == CMD_DEVICE_ID) {
//                if (singleProcess) {
//                    L.d("Ignoring Device id");
//                    if (config.getDeviceId() != null) {
//                        this.network = new Network();
//                        this.network.init(config);
//                        check();
//                    }
//                    return START_STICKY;
//                } else {
//                    L.d("Device id");
//                }
//                // reread config & notify modules
//                ConfigCore.DID id = null;
//                ConfigCore.DID old = null;
//                boolean success = true;
//                if (intent.hasExtra(PARAM_1)) {
//                    id = new ConfigCore.DID(ConfigCore.DeviceIdRealm.DEVICE_ID, ConfigCore.DeviceIdStrategy.ANDROID_ID, null);
//                    success = id.restore(intent.getByteArrayExtra(PARAM_1));
//                }
//                if (intent.hasExtra(PARAM_2)) {
//                    old = new ConfigCore.DID(ConfigCore.DeviceIdRealm.DEVICE_ID, ConfigCore.DeviceIdStrategy.ANDROID_ID, null);
//                    success = success && !old.restore(intent.getByteArrayExtra(PARAM_2));
//                }
//                if (success) {
//                    Ctx ctx = new CtxImpl(CountlyService.this);
//                    Core.onDeviceId(ctx, id, old);
//                    if (config != null && config.getDeviceId() != null && network == null) {
//                        L.i("Starting sending requests");
//                        this.network = new Network();
//                        try {
//                            this.network.init(config);
//                        } catch (Throwable t) {
//                            Log.e("Error while initializing network", t);
//                            this.core.stop(getApplicationContext(), false);
//                            this.core = null;
//                            this.network = null;
//                            stopSelf();
//                            return START_NOT_STICKY;
//                        }
//                    }
//                    check();
//                }
//            }
//        }
//        return START_STICKY;
//    }
//
//    @Override
//    public void onDestroy() {
//        L.d("Destroying");
//    }
//
//    @Override
//    public void onLowMemory() {
//        L.d("Low Memory");
//    }
//
//    @Override
//    public void onTrimMemory(int level) {
//        L.d("Trim Memory " + level);
//    }
//
//    void start() {
//        tasks.run(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
//            @Override
//            public Boolean call() throws Exception {
//                crashes = new ArrayList<Long>();
//
//                for (Long id : crashes) {
//                    L.i("Found unprocessed crash " + id);
//                    processCrash(id);
//                }
//
//                sessions = Storage.list(ctx, SessionImpl.getStoragePrefix());
//                L.d("recovering " + sessions.size() + " sessions");
//                for (Long id : sessions) {
//                    L.d("recovering session " + id);
//                    SessionImpl session = Storage.read(ctx, new SessionImpl(ctx, id));
//                    if (session == null) {
//                        L.e("no session with id " + id + " found while recovering");
//                    } else {
//                        Boolean success = session.recover(config);
//                        L.d("session " + id + " recovery " + (success == null ? "won't recover" : success ? "success" : "failure"));
//                    }
//                }
//                check();
//                return true;
//            }
//        });
//    }
//
//    private boolean processCrash(Long id) {
//        CrashImpl crash = new CrashImpl(id);
//        crash = Storage.read(ctx, crash);
//        if (crash == null) {
//            L.e("Cannot read crash from storage, skipping");
//            return false;
//        }
//
//        Request request = ModuleRequests.nonSessionRequest(ctx);
//        ModuleCrash.putCrashIntoParams(crash, request.params);
//        if (Storage.push(ctx, request)) {
//            L.i("Added request " + request.storageId() + " instead of crash " + crash.storageId());
//            Boolean success = Storage.remove(ctx, crash);
//            return success == null ? false : success;
//        } else {
//            L.e("Couldn't write request " + request.storageId() + " instead of crash " + crash.storageId());
//            return false;
//        }
//    }
//
//    private synchronized void stop() {
//        shutdown = true;
//        if (tasks.isRunning()) {
//            L.d("Nothing is going on, stopping");
//            stopSelf();
//        }
//    }
//
//    private synchronized void check() {
//        L.d("state: shutdown " + shutdown + " / tasks running " + tasks.isRunning() + " / net running " + networkTasks.isRunning() + " / device id " + config.getDeviceId() + " / network " + (network != null));
//        if (!shutdown && !tasks.isRunning() && !networkTasks.isRunning() && config.getDeviceId() != null && network != null) {
//            tasks.run(submit());
//        }
//    }
//
//    public Tasks.Task<Boolean> submit() {
//        return new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
//            @Override
//            public Boolean call() throws Exception {
//                if (networkTasks.isRunning()) {
//                    return false;
//                }
//
//                final Request request = Storage.readOne(ctx, new Request(0L), true);
//                if (request == null) {
//                    return false;
//                } else {
//                    L.d("Preparing request: " + request);
//                    final Boolean check = CountlyService.this.core.isRequestReady(request);
//                    if (check == null) {
//                        L.d("Request is not ready yet: " + request);
//                        return false;
//                    } else if (check.equals(Boolean.FALSE)){
//                        L.d("Request won't be ready, removing: " + request);
//                        Storage.remove(ctx, request);
//                        return true;
//                    } else {
//                        networkTasks.run(network.send(request), new Tasks.Callback<Network.RequestResult>() {
//                            @Override
//                            public void call(Network.RequestResult requestResult) throws Exception {
//                                L.d("Request " + request.storageId() + " sent?: " + requestResult);
//                                if (requestResult == null || requestResult == Network.RequestResult.REMOVE || requestResult == Network.RequestResult.OK) {
//                                    Storage.remove(ctx, request);
//                                }
//                                if (shutdown) {
//                                    stop();
//                                } else {
//                                    check();
//                                }
//                            }
//                        });
//                        return true;
//                    }
//                }
//            }
//        };
//    }
}
