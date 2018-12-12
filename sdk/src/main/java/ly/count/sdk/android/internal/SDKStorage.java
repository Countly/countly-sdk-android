package ly.count.sdk.android.internal;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ly.count.sdk.Config;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.Storable;
import ly.count.sdk.internal.Storage;

abstract class SDKStorage extends SDKLifecycle {
    private static final Log.Module L = Log.module("SDK");
    private static final String FILE_NAME_PREFIX = "[CLY]";
    private static final String FILE_NAME_SEPARATOR = "_";

    public SDKStorage() {
        super();
    }

    @Override
    public void stop(ly.count.sdk.internal.Ctx ctx, boolean clear) {
        super.stop(ctx, clear);
        if (clear) {
            Storage.await();
            storablePurge(ctx, null);
        }
    }

    private static String getName(Storable storable) {
        return getName(storable.storagePrefix(), storable.storageId().toString());
    }

    private static String getName(String ...names) {
        if (names == null || names.length == 0 || Utils.isEmpty(names[0])) {
            return FILE_NAME_PREFIX;
        } else {
            String prefix = FILE_NAME_PREFIX;
            for (String name : names) {
                if (name == null) {
                    break;
                }
                prefix += FILE_NAME_SEPARATOR + name;
            }
            return prefix;
        }
    }

    private static String extractName(String filename, String prefix) {
        if (filename.indexOf(prefix) == 0) {
            return filename.substring(prefix.length());
        } else {
            return null;
        }
    }

    @Override
    public int storablePurge(ly.count.sdk.internal.Ctx context, String prefix) {
        Ctx ctx = (Ctx) context;
        prefix = getName(prefix) + FILE_NAME_SEPARATOR;

        L.i("Purging storage for prefix " + prefix);

        int deleted = 0;

        String[] files = ctx.getContext().getApplicationContext().fileList();
        for (String file : files) {
            if (file.startsWith(prefix)) {
                if (ctx.getContext().deleteFile(file)) {
                    deleted++;
                }
            }
        }

        return deleted;
    }

    @Override
    public Boolean storableWrite(ly.count.sdk.internal.Ctx context, String prefix, Long id, byte[] data) {
        Ctx ctx = (Ctx) context;
        String filename = getName(prefix, id.toString());

        FileOutputStream stream = null;
        FileLock lock = null;
        try {
            stream = ctx.getContext().getApplicationContext().openFileOutput(filename, android.content.Context.MODE_PRIVATE);
            lock = stream.getChannel().tryLock();
            if (lock == null) {
                return false;
            }
            stream.write(data);
            stream.close();
            return true;
        } catch (IOException e) {
            L.wtf("Cannot write data to " + filename, e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Couldn't close output stream for " + filename, e);
                }
            }
            if (lock != null && lock.isValid()) {
                try {
                    lock.release();
                } catch (IOException e) {
                    L.wtf("Couldn't release lock for " + filename, e);
                }
            }
        }
        return false;
    }

    @Override
    public <T extends Storable> Boolean storableWrite(ly.count.sdk.internal.Ctx context, T storable) {
        return storableWrite(context, storable.storagePrefix(), storable.storageId(), storable.store());
    }

    @Override
    public byte[] storableReadBytes(ly.count.sdk.internal.Ctx context, String filename) {
        Ctx ctx = (Ctx) context;

        ByteArrayOutputStream buffer = null;
        FileInputStream stream = null;

        try {
            buffer = new ByteArrayOutputStream();
            stream = ctx.getContext().getApplicationContext().openFileInput(filename);

            int read;
            byte data[] = new byte[4096];
            while((read = stream.read(data, 0, data.length)) != -1){
                buffer.write(data, 0, read);
            }

            stream.close();

            return buffer.toByteArray();
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            L.wtf("Error while reading file " + filename, e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Couldn't close input stream for " + filename, e);
                }
            }
            if (buffer != null) {
                try {
                    buffer.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
        }
        return null;
    }

    @Override
    public byte[] storableReadBytes(ly.count.sdk.internal.Ctx ctx, String prefix, Long id) {
        return storableReadBytes(ctx, getName(prefix, id.toString()));
    }

    @Override
    public <T extends Storable> Boolean storableRead(ly.count.sdk.internal.Ctx context, T storable) {
        byte[] data = storableReadBytes(context, getName(storable));
        if (data == null) {
            return null;
        } else {
            return storable.restore(data);
        }
    }

    @Override
    public <T extends Storable> Map.Entry<Long, byte[]> storableReadBytesOneOf(ly.count.sdk.internal.Ctx context, T storable, boolean asc) {
        List<Long> list = storableList(context, storable.storagePrefix(), asc ? 1 : -1);
        if (list.size() > 0) {
            return new AbstractMap.SimpleEntry<Long, byte[]>(list.get(0), storableReadBytes(context, getName(storable.storagePrefix(), list.get(0).toString())));
        }
        return null;
    }

    @Override
    public <T extends Storable> Boolean storableRemove(ly.count.sdk.internal.Ctx context, T storable) {
        Ctx ctx = (Ctx) context;
        return ctx.getContext().getApplicationContext().deleteFile(getName(storable.storagePrefix(), storable.storageId().toString()));
    }

    @Override
    public <T extends Storable> Boolean storablePop(ly.count.sdk.internal.Ctx ctx, T storable) {
        Boolean read = storableRead(ctx, storable);
        if (read == null) {
            return null;
        } else if (read) {
            return storableRemove(ctx, storable);
        }
        return read;
    }

    @Override
    public List<Long> storableList(ly.count.sdk.internal.Ctx context, String prefix, int slice) {
        if (Utils.isEmpty(prefix)) {
            Log.wtf("Cannot get list of ids without prefix");
        }
        Ctx ctx = (Ctx) context;
        prefix = prefix + FILE_NAME_SEPARATOR;

        String alternativePrefix = FILE_NAME_PREFIX + FILE_NAME_SEPARATOR + prefix;

        List<Long> list = new ArrayList<>();
        String[] files = ctx.getContext().getApplicationContext().fileList();
        Arrays.sort(files);

        int max = slice == 0 ? Integer.MAX_VALUE : Math.abs(slice);
        for (int i = 0; i < files.length; i++) {
            int idx = slice >= 0 ? i : files.length - 1 - i;
            String file = files[idx];
            if (file.startsWith(prefix) || file.startsWith(alternativePrefix)) {
                try {
                    list.add(Long.parseLong(extractName(file, file.startsWith(prefix) ? prefix : alternativePrefix)));
                } catch (NumberFormatException nfe) {
                    Log.e("Wrong file name: " + file + " / " + prefix);
                }
                if (list.size() >= max) {
                    break;
                }
            }
        }
        return list;
    }
}
