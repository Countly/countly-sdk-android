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
import java.util.HashSet;
import java.util.List;

import ly.count.android.sdk.Config;

import static ly.count.android.sdk.Config.DeviceIdStrategy.INSTANCE_ID;

/**
 * Internal to Countly SDK configuration class. Can and should contain options hidden from outside.
 * Only members of {@link InternalConfig} can be changed, members of {@link Config} are non-modifiable.
 */
final class InternalConfig extends Config implements Storable {
    private static final Log.Module L = Log.module("InternalConfig");

    /**
     * Logger class
     */
    private Class<? extends Log.Logger> loggerClass = Log.AndroidLogger.class;

    /**
     * Running in limited mode, started by itself rather than by developer
     */
    private boolean limited = false;

    /**
     * {@link ly.count.android.sdk.Config.DID} instances generated from Countly SDK (currently maximum 2: Countly device id + FCM).
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
        setFrom(config);
    }

    void setFrom(Config config) {
        List<Field> local = Utils.reflectiveGetDeclaredFields(getClass());
        List<Field> remot = Utils.reflectiveGetDeclaredFields(config.getClass());

        for (Field r : remot) {
            for (Field l : local) {
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
                        L.w("Cannot access field " + r.getName(), iae);
                    }
                }
            }
        }
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

    public boolean isLimited() {
        return limited;
    }

    @Override
    public Long storageId() {
        return 0L;
    }

    @Override
    public String storagePrefix() {
        return getStoragePrefix();
    }

    public static String getStoragePrefix() {
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
            stream.writeObject(salt);
            stream.writeInt(networkConnectionTimeout);
            stream.writeInt(networkReadTimeout);
            stream.writeInt(publicKeyPins == null ? 0 : publicKeyPins.size());
            if (publicKeyPins != null) for (String key: publicKeyPins) { stream.writeUTF(key); }
            stream.writeInt(certificatePins == null ? 0 : certificatePins.size());
            if (certificatePins != null) for (String key: certificatePins) { stream.writeUTF(key); }
            stream.writeInt(sendUpdateEachSeconds);
            stream.writeInt(sendUpdateEachEvents);
            stream.writeInt(sessionCooldownPeriod);
            stream.writeBoolean(programmaticSessionsControl);
            stream.writeBoolean(testMode);
            stream.writeInt(crashReportingANRTimeout);
            stream.writeObject(pushActivityClass);
            stream.writeInt(dids.size());
            for (DID did : dids) {
                byte[] b = did.store();
                if (b != null){
                    stream.writeInt(b.length);
                    stream.write(b);
                }
            }
            stream.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            L.wtf("Cannot serialize config", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
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
                L.wtf("Cannot happen", e);
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
            salt = (String) stream.readObject();
            networkConnectionTimeout = stream.readInt();
            networkReadTimeout = stream.readInt();
            l = stream.readInt();
            publicKeyPins = l == 0 ? null : new HashSet<String>();
            for (int i = 0; i < l; i++) {
                publicKeyPins.add(stream.readUTF());
            }
            l = stream.readInt();
            certificatePins = l == 0 ? null : new HashSet<String>();
            for (int i = 0; i < l; i++) {
                certificatePins.add(stream.readUTF());
            }
            sendUpdateEachSeconds = stream.readInt();
            sendUpdateEachEvents = stream.readInt();
            sessionCooldownPeriod = stream.readInt();
            programmaticSessionsControl = stream.readBoolean();
            testMode = stream.readBoolean();
            crashReportingANRTimeout = stream.readInt();
            pushActivityClass = (String) stream.readObject();

            dids.clear();
            l = stream.readInt();
            while (l-- > 0) {
                DID did = new DID(DeviceIdRealm.DEVICE_ID, INSTANCE_ID, null);
                byte[] b = new byte[stream.readInt()];
                stream.readFully(b);
                did.restore(b);
                dids.add(did);
            }

            return true;
        } catch (IOException | ClassNotFoundException e) {
            L.wtf("Cannot deserialize config", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
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
        DID old = null;
        for (DID did : dids) {
            if (did.realm == id.realm) {
                old = did;
            }
        }
        if (old != null) {
            dids.remove(old);
        }
        dids.add(id);
        return old;
    }

    public boolean removeDeviceId(DID did) {
        return this.dids.remove(did);
    }
}
