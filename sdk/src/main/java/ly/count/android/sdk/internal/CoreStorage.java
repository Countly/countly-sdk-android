package ly.count.android.sdk.internal;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;

/**
 * Storage-related methods of {@link Core}
 */

public class CoreStorage extends CoreLifecycle {

    private static final String FILE_NAME_PREFIX = "[CLY]";
    private static final String FILE_NAME_SEPARATOR = "_";

    private static String getName(String ...names) {
        if (names == null || names.length == 0 || Utils.isEmpty(names[0])) {
            return FILE_NAME_PREFIX;
        } else {
            String prefix = FILE_NAME_PREFIX;
            for (String name : names) {
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

    static int purgeInternalStorage(Context ctx, String prefix) {
        prefix = getName(prefix) + FILE_NAME_SEPARATOR;

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

    static List<String> listDataInInternalStorage(Context ctx, String prefix, int slice) {
        prefix = getName(prefix) + FILE_NAME_SEPARATOR;

        List<String> list = new ArrayList<>();
        String[] files = ctx.getContext().getApplicationContext().fileList();

        int max = slice == 0 ? Integer.MAX_VALUE : Math.abs(slice);
        for (int i = 0; i < files.length; i++) {
            int idx = slice >= 0 ? i : files.length - 1 - i;
            String file = files[idx];
            if (file.startsWith(prefix)) {
                list.add(file.substring(prefix.length()));
                if (list.size() >= max) {
                    break;
                }
            }
        }
        return list;
    }

    static boolean pushDataToInternalStorage(Context ctx, String prefix, String name, byte[] data) {
        String filename = getName(prefix, name);

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
            System.out.println(e);
            Log.wtf("Cannot write data to " + name, e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Couldn't close output stream for " + name, e);
                }
            }
            if (lock != null && lock.isValid()) {
                try {
                    lock.release();
                } catch (IOException e) {
                    Log.wtf("Couldn't release lock for " + name, e);
                }
            }
        }
        return false;
    }

    static boolean removeDataFromInternalStorage(Context ctx, String prefix, String name) {
        return ctx.getContext().getApplicationContext().deleteFile(getName(prefix, name));
    }

    static byte[] popDataFromInternalStorage(Context ctx, String prefix, String name) {
        byte[] data = readDataFromInternalStorage(ctx, prefix, name);
        if (data != null) {
            ctx.getContext().getApplicationContext().deleteFile(getName(prefix, name));
        }
        return data;
    }

    static byte[] readDataFromInternalStorage(Context ctx, String prefix, String name) {
        String filename = getName(prefix, name);

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

            data = buffer.toByteArray();

            return data;

        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            Log.wtf("Error while reading file " + name, e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Couldn't close input stream for " + name, e);
                }
            }
            if (buffer != null) {
                try {
                    buffer.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
        }

        return null;
    }

    static Object[] readOneFromInternalStorage(Context ctx, String prefix, boolean asc) {
        String start = getName(prefix);
        String fileStart = start + FILE_NAME_SEPARATOR;

        String[] files = ctx.getContext().getApplicationContext().fileList();

        for (int i = 0; i < files.length; i++) {
            int idx = asc ? i : files.length - 1 - i;
            String file = files[idx];
            if (file.startsWith(fileStart)) {
                Object[] arr = new Object[2];
                arr[0] = extractName(file, fileStart);
                arr[1] = readDataFromInternalStorage(ctx, prefix, extractName(file, fileStart));
                return arr;
            }
        }

        return null;
    }
//
//    int purgeInternalStorage(String prefix) {
//        return purgeInternalStorage(Core.instance.longLivingContext, prefix);
//    }
//
//    List<String> listDataInInternalStorage(String prefix, int slice) {
//        return listDataInInternalStorage(Core.instance.longLivingContext, prefix, slice);
//    }
//
//    public boolean pushDataToInternalStorage(String prefix, String name, byte[] data) {
//        return pushDataToInternalStorage(Core.instance.longLivingContext, prefix, name, data);
//    }
//
//    boolean removeDataFromInternalStorage(String prefix, String name) {
//        return removeDataFromInternalStorage(Core.instance.longLivingContext, prefix, name);
//    }
//
//    byte[] popDataFromInternalStorage(String prefix, String name) {
//        return popDataFromInternalStorage(Core.instance.longLivingContext, prefix, name);
//    }
//
//    byte[] readDataFromInternalStorage(String prefix, String name) {
//        return readDataFromInternalStorage(Core.instance.longLivingContext, prefix, name);
//    }
//
//    Object[] readOneFromInternalStorage(String prefix, boolean asc) {
//        return readOneFromInternalStorage(Core.instance.longLivingContext, prefix, asc);
//    }
//
}
