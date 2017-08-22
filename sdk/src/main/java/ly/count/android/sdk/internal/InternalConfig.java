package ly.count.android.sdk.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ly.count.android.sdk.Config;

import static ly.count.android.sdk.Config.DeviceIdStrategy.INSTANCE_ID;

/**
 * Internal to Countly SDK configuration class. Can and should contain options hidden from outside.
 * Only members of {@link InternalConfig} can be changed, members of {@link Config} are non-modifiable.
 */
final class InternalConfig extends Config implements Storable {

    /**
     * List of modules built based on Feature set selected.
     */
    private ArrayList<Module> modules;

    /**
     * Logger class
     */
    private Class<? extends Log.Logger> loggerClass = Log.AndroidLogger.class;

    /**
     * Running in limited mode, started by itself rather than by developer
     */
    private boolean limited = false;

    /**
     * InstanceIds generated from Countly SDK (currently maximum 2: Countly device id + FCM).
     * Stored to be able to refresh them.
     */
    private List<DID> dids = new ArrayList<>();

    /**
     * Shouldn't be used!
     */
    InternalConfig(String url, String appKey) throws MalformedURLException {
        super(url, appKey);
        throw new IllegalStateException("InternalConfig(url, appKey) should not be used");
    }

    InternalConfig() throws MalformedURLException {
        super("http://count.ly", "not a key");
    }

    InternalConfig(Config config) throws MalformedURLException {
        //todo double check, should it protect against nulls?
        super(config.getServerURL().toString(), config.getServerAppKey());

        List<Field> local = Utils.reflectiveGetDeclaredFields(getClass());
        List<Field> remot = Utils.reflectiveGetDeclaredFields(config.getClass());

        for (Field r : remot) {
            Log.d("Config " + r.getName());
            for (Field l : local) {
                Log.d("InternalConfig " + l.getName());
                 if (r.getName().equals(l.getName())) {
                    try {
                        r.setAccessible(true);
                        if (l.isAccessible()) {
                            l.set(this, r.get(config));
                        } else {
                            l.setAccessible(true);
                            l.set(this, r.get(config));
                            l.setAccessible(false);
                        }
                    } catch (IllegalAccessException | IllegalArgumentException iae) {
                        Log.w("Cannot access field " + r.getName(), iae);
                    }
                }
            }
        }
//        this.features.addAll(config.getFeatures());
//        this.usePOST = config.isUsePOST();
//        this.loggingTag = config.getLoggingTag();
//        this.loggingLevel = config.getLoggingLevel();
//        this.testMode = config.isTestModeEnabled();
//        this.programmaticSessionsControl = config.isProgrammaticSessionsControl();
//        this.sdkVersion = config.getSdkVersion();
    }

    public Class<? extends Log.Logger> getLoggerClass() {
        return loggerClass;
    }

    public InternalConfig setLoggerClass(Class<? extends Log.Logger> loggerClass) {
        this.loggerClass = loggerClass;
        return this;
    }

    public InternalConfig setLimited(boolean limited) {
        this.limited = limited;
        return this;
    }

    @Override
    public Long storageId() {
        return 0L;
    }

    @Override
    public String storagePrefix() {
        return "config";
    }

    @Override
    public byte[] store() {
        ByteArrayOutputStream bytes = null;
        ObjectOutputStream stream = null;
        try {
            bytes = new ByteArrayOutputStream();
            stream = new ObjectOutputStream(bytes);
            stream.writeUTF(serverURL.toString());
            stream.writeUTF(serverAppKey);
            int ftrs = 0;
            for (Feature feature : features) {
                ftrs = ftrs | feature.getIndex();
            }
            stream.writeInt(ftrs);
            stream.writeUTF(loggingTag);
            stream.writeInt(loggingLevel.getLevel());
            stream.writeUTF(sdkName);
            stream.writeUTF(sdkVersion);
            stream.writeBoolean(usePOST);
            stream.writeInt(sendUpdateEachSeconds);
            stream.writeInt(sendUpdateEachEvents);
            stream.writeBoolean(programmaticSessionsControl);
            stream.writeBoolean(testMode);
            stream.writeInt(dids.size());
            for (DID did : dids) {
                stream.writeInt(did.realm.getIndex());
                stream.writeInt(did.strategy.getIndex());

                if (did.strategy == INSTANCE_ID && did.realm != DeviceIdRealm.DEVICE_ID) {
                    stream.writeUTF(did.id);
                    stream.writeUTF(did.entity);
                    stream.writeUTF(did.scope);
                } else {
                    stream.writeUTF(did.id);
                }
            }
            stream.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            Log.wtf("Cannot serialize config", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
        }
        return null;
    }

    @Override
    public boolean restore(byte[] data) {
        ByteArrayInputStream bytes = null;
        ObjectInputStream stream = null;

        try {
            bytes = new ByteArrayInputStream(data);
            stream = new ObjectInputStream(bytes);

            try {
                Utils.reflectiveSetField(this, "serverURL", new URL(stream.readUTF()));
                Utils.reflectiveSetField(this, "serverAppKey", stream.readUTF());
            } catch (Exception e) {
                Log.wtf("Cannot happen", e);
            }

            int ftrs = stream.readInt();
            for (Feature feature : Feature.values()) {
                if ((feature.getIndex() & ftrs) > 0) {
                    features.add(feature);
                }
            }

            loggingTag = stream.readUTF();
            int l = stream.readInt();
            for (LoggingLevel level : LoggingLevel.values()) {
                if (level.getLevel() == l) {
                    loggingLevel = level;
                    break;
                }
            }

            sdkName = stream.readUTF();
            sdkVersion = stream.readUTF();
            usePOST = stream.readBoolean();
            sendUpdateEachSeconds = stream.readInt();
            sendUpdateEachEvents = stream.readInt();
            programmaticSessionsControl = stream.readBoolean();
            testMode = stream.readBoolean();

            dids.clear();
            l = stream.readInt();
            while (l-- > 0) {
                DeviceIdRealm realm = DeviceIdRealm.fromIndex(stream.readInt());
                DeviceIdStrategy strategy = DeviceIdStrategy.fromIndex(stream.readInt());

                DID did;
                if (strategy == INSTANCE_ID && realm != DeviceIdRealm.DEVICE_ID) {
                    did = new DID(realm, INSTANCE_ID, stream.readUTF(), stream.readUTF(), stream.readUTF());
                } else {
                    did = new DID(realm, strategy, stream.readUTF());
                }

                dids.add(did);
            }

            return true;
        } catch (IOException e) {
            Log.wtf("Cannot deserialize config", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
        }

        return false;
    }

    public DID getDeviceId() {
        return getDeviceId(DeviceIdRealm.DEVICE_ID);
    }

    public DID getDeviceId(DeviceIdRealm realm) {
        for (DID DID : dids) {
            if (DID.realm == realm) {
                return DID;
            }
        }
        return null;
    }

    public DID setDeviceId(DID id) {
        return setDeviceId(id, DeviceIdRealm.DEVICE_ID);
    }

    public DID setDeviceId(DID id, DeviceIdRealm realm) {
        DID old = null;
        for (DID did : dids) {
            if (did.realm == realm) {
                old = did;
            }
        }
        if (old != null) {
            dids.remove(old);
        }
        dids.add(id);
        return old;
    }

    public void addDID(DID did) {
        this.dids.add(did);
    }

    public List<DID> getIIDs() {
        return this.dids;
    }
}
