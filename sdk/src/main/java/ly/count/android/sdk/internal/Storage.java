package ly.count.android.sdk.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import ly.count.sdk.internal.Storable;
import ly.count.sdk.internal.Transformer;

/**
 * Storing and retrieving data from internal storage of SDK.
 * Thread safety is based on single thread of execution - only one thread works with storage at a time
 * in a single process thanks to {@link Tasks}.
 */

class Storage {
    private static final Log.Module L = Log.module("Storage");

    private static final Tasks tasks = new Tasks("storage");

    static String name(Storable storable) {
        return storable.storagePrefix() + "_" + storable.storageId();
    }

    /**
     * Stores data in device internal memory. When a storable with the same id already exists,
     * replaces it with new data.
     *
     *
     * @param ctx context to run in
     * @param storable Object to store
     * @return true when storing succeeded, false otherwise
     */
    static boolean push(Context ctx, Storable storable) {
        L.d("Pushing " + name(storable));
        try {
            return pushAsync(ctx, storable).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while pushing " + name(storable), e);
        }
        return false;
    }

    /**
     * Stores data in device internal memory. When a storable with the same id already exists,
     * replaces it with new data. Runs in a storage thread provided by {@link Tasks}
     *
     * @param ctx context to run in
     * @param storable Object to store
     * @param callback nullable callback to call when done
     * @return Future<Boolean> object which resolves as true when storing succeeded, false otherwise
     */
    static Future<Boolean> pushAsync(final Context ctx, final Storable storable, Tasks.Callback<Boolean> callback) {
        L.d("Pushing async " + name(storable));
        return tasks.run(new Tasks.Task<Boolean>(storable.storageId()) {
            @Override
            public Boolean call() throws Exception {
                return Core.pushDataToInternalStorage(ctx, storable.storagePrefix(), "" + storable.storageId(), storable.store());
            }
        }, callback);
    }

    /**
     * Shorthand for {@link #pushAsync(Context, Storable, ly.count.android.sdk.internal.Tasks.Callback)}
     *
     * @param ctx context to run in
     * @param storable Object to store
     * @return Future<Boolean> object which resolves as true when storing succeeded, false otherwise
     */
    static Future<Boolean> pushAsync(final Context ctx, final Storable storable) {
        return pushAsync(ctx, storable, null);
    }
    /**
     * Removes storable from storage.
     *
     * @param ctx context to run in
     * @param storable Object to remove
     * @return true if removed, false otherwise
     */
    static <T extends Storable> Boolean remove(final Context ctx, T storable) {
        L.d("removing " + name(storable));
        try {
            return removeAsync(ctx, storable, null).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while removing " + name(storable), e);
        }
        return null;
    }

    /**
     * Removes storable from storage.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param ctx context to run in
     * @param storable Object to remove
     * @return Future<Boolean> object which resolves to true if storable is removed, false otherwise
     */
    static <T extends Storable> Future<Boolean> removeAsync(final Context ctx, final T storable, Tasks.Callback<Boolean> callback) {
        return tasks.run(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
            @Override
            public Boolean call() throws Exception {
                return Core.removeDataFromInternalStorage(ctx, storable.storagePrefix(), "" + storable.storageId());
            }
        }, callback);
    }


    /**
     * Reinitializes storable with data stored previously in device internal memory and deletes corresponding file.
     *
     * @param ctx context to run in
     * @param storable Object to reinitialize
     * @return storable object passed as param when restoring succeeded, null otherwise
     */
    static <T extends Storable> T pop(Context ctx, T storable) {
        L.d("Popping " + name(storable));
        try {
            return popAsync(ctx, storable).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while popping " + name(storable), e);
        }
        return null;
    }

    /**
     * Reinitializes storable with data stored previously in device internal memory.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param ctx context to run in
     * @param storable Object to reinitialize
     * @return Future<Storable> object which resolves as object passed as param when restoring succeeded, null otherwise
     */
    static <T extends Storable> Future<T> popAsync(final Context ctx, final T storable) {
        return tasks.run(new Tasks.Task<T>(-storable.storageId()) {
            @Override
            public T call() throws Exception {
                byte data[] = Core.popDataFromInternalStorage(ctx, storable.storagePrefix(), "" + storable.storageId());
                if (data == null) {
                    L.d("No data for file " + name(storable));
                    return null;
                }
                if (storable.restore(data)) {
                    return storable;
                } else {
                    return null;
                }
            }
        });
    }

    /**
     * Transform existing {@link Storable}s one-by-one replacing data if needed
     *
     * @param ctx context to run in
     * @param prefix Object to reinitialize
     * @return storable object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> boolean transform(final Context ctx, final String prefix, final Transformer transformer) {
        L.d("readAll " + prefix);
        try {
            return tasks.run(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
                @Override
                public Boolean call() throws Exception {
                    boolean success = true;
                    List<String> names = Core.listDataInInternalStorage(ctx, prefix, 0);
                    for (String name : names) {
                        byte data[] = Core.readDataFromInternalStorage(ctx, prefix, name);
                        if (data != null) {
                            byte transformed[] = transformer.doTheJob(Long.parseLong(name), data);
                            if (transformed != null) {
                                if (!Core.pushDataToInternalStorage(ctx, prefix, name, transformed)) {
                                    success = false;
                                    L.e("Couldn't write transformed data for " + name);
                                }
                            }
                        } else {
                            success = false;
                            L.e("Couldn't read data to transform from " + name);
                        }
                    }
                    return success;
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while reading all " + prefix, e);
        }
        return false;
    }

    /**
     * Reinitializes storable with data stored previously in device internal memory.
     *
     * @param ctx context to run in
     * @param storable Object to reinitialize
     * @return storable object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> T read(Context ctx, T storable) {
        L.d("read " + name(storable));
        try {
            return readAsync(ctx, storable).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while popping " + name(storable), e);
        }
        return null;
    }

    /**
     * Reinitializes storable with data stored previously in device internal memory.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param ctx context to run in
     * @param storable Object to reinitialize
     * @return Future<Storable> object which resolves as object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> Future<T> readAsync(final Context ctx, final T storable) {
        return readAsync(ctx, storable, null);
    }

    /**
     * Reinitializes storable with data stored previously in device internal memory.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param ctx context to run in
     * @param storable Object to reinitialize
     * @param callback Callback to call with read result
     * @return Future<Storable> object which resolves as object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> Future<T> readAsync(final Context ctx, final T storable, final Tasks.Callback<T> callback) {
        return tasks.run(new Tasks.Task<T>(-storable.storageId()) {
            @Override
            public T call() throws Exception {
                byte data[] = Core.readDataFromInternalStorage(ctx, storable.storagePrefix(), "" + storable.storageId());
                if (data == null) {
                    L.d("No data for file " + name(storable));
                    return null;
                }
                T ret = null;
                if (storable.restore(data)) {
                    ret = storable;
                }
                if (callback != null) {
                    callback.call(ret);
                }
                return ret;
            }
        });
    }

    /**
     * Reinitializes first (or last if asc is false) storable with prefix from storable supplied as parameter.
     *
     * @param ctx context to run in
     * @param storable Object to get prefix from
     * @param asc true if reading first storable, false if reading last one
     * @return storable object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> T readOne(Context ctx, T storable, boolean asc) {
        L.d("readOne " + storable.storagePrefix());
        try {
            return readOneAsync(ctx, storable, asc).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while popping " + name(storable), e);
        }
        return null;
    }

    /**
     * Reinitializes first (or last if asc is false) storable with prefix from storable supplied as parameter.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param ctx Context to run in
     * @param storable Object to get prefix from
     * @param asc true if reading first storable, false if reading last one
     * @return Future<Storable> object which resolves as object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> Future<T> readOneAsync(final Context ctx, final T storable, final boolean asc) {
        return tasks.run(new Tasks.Task<T>(-storable.storageId()) {
            @Override
            public T call() throws Exception {
                Object data[] = Core.readOneFromInternalStorage(ctx, storable.storagePrefix(), asc);
                if (data == null) {
                    return null;
                }
                try {
                    Long id = Long.parseLong((String)data[0]);
                    Utils.reflectiveSetField(storable, "id", id);

                    if (storable.restore((byte[]) data[1])) {
                        return storable;
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    L.wtf("Wrong file name in readOneAsync", e);
                    return null;
                }
            }
        });
    }

    /**
     * Retrieves ids of all files stored for a specific prefix ({@link Storable#storagePrefix()}.
     * Runs in a storage thread provided by {@link Tasks}, current thread waits for read completion.
     *
     * @param prefix String representing type of storable to list (prefix of file names)
     * @return List<Long> object which resolves as list of storable ids, not null
     */
    static List<Long> list(Context ctx, String prefix) {
        return list(ctx, prefix, 0);
    }

    /**
     * Retrieves ids of files stored for a specific prefix ({@link Storable#storagePrefix()}.
     * Runs in a storage thread provided by {@link Tasks}, current thread waits for read completion.
     *
     * @param prefix String representing type of storable to list (prefix of file names)
     * @param slice integer controlling number and slice direction of results returned:
     *              0 to return all records
     *              1..N to return first N records ordered from first to last
     *              -1..-N to return last N records ordered from last to first
     * @return List<Long> object which resolves as list of storable ids, not null
     */
    static List<Long> list(Context ctx, String prefix, int slice) {
        L.d("Listing " + prefix);
        try {
            return listAsync(ctx, prefix, slice).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while listing " + prefix, e);
        }
        return null;
    }

    /**
     * Retrieves ids of files stored for a specific prefix ({@link Storable#storagePrefix()}.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param prefix String representing type of storable to list (prefix of file names)
     * @param slice integer controlling number and slice direction of results returned:
     *              0 to return all records
     *              1..N to return first N records ordered from first to last
     *              -1..-N to return last N records ordered from last to first
     * @return Future<List<Long>> object which resolves as list of storable ids, not null
     */
    static Future<List<Long>> listAsync(final Context ctx, final String prefix, final int slice) {
        return tasks.run(new Tasks.Task<List<Long>>(Tasks.ID_STRICT) {
            @Override
            public List<Long> call() throws Exception {
                List<Long> list = new ArrayList<Long>();
                List<String> files = Core.listDataInInternalStorage(ctx, prefix, slice);
                if (files == null) {
                    L.wtf("Null list while listing storage");
                    return list;
                }
                for (String file : files) {
                    try {
                        list.add(Long.parseLong(file));
                    } catch (Throwable t) {
                        L.wtf("Exception while parsing storable id", t);
                    }
                }
                Collections.sort(list, new Comparator<Long>() {
                    @Override
                    public int compare(Long o1, Long o2) {
                        return slice >= 0 ? o1.compareTo(o2) : o2.compareTo(o1);
                    }
                });
                return list;
            }
        });
    }

    static void await() {
        L.d("Waiting for storage tasks to complete");
        try {
            tasks.run(new Tasks.Task<Boolean>(Tasks.ID_STRICT) {
                @Override
                public Boolean call() throws Exception {
                    L.d("Waiting for storage tasks to complete DONE");
                    return null;
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            L.wtf("Interrupted while waiting", e);
        }
    }

//    static synchronized List<Storable> popAll(String prefix, Class<? extends Storable> claz) {
//        List<String> names = Core.instance.listDataFromInternalStorage(prefix);
//        List<Storable> list = new ArrayList<>();
//        L.d("Loading " + names.size() + " files prefixed with " + prefix + ": " + names);
//        try {
//            Constructor<? extends Storable> constructor = claz.getConstructor(Long.class);
//
//            for (String name : names) {
//                String idStr = name.substring(name.lastIndexOf('_') + 1);
//                Long id = Long.valueOf(idStr);
//                Storable storable = constructor.newInstance(id);
//                storable = pop(storable);
//                if (storable != null) {
//                    list.add(storable);
//                }
//            }
//        } catch (NumberFormatException e) {
//            L.wtf("Storage error - bad file name detected", e);
//        } catch (NoSuchMethodException e) {
//            L.wtf("Cannot happen", e);
//        } catch (IllegalAccessException e) {
//            L.wtf("IllegalAccessException which cannot happen", e);
//        } catch (InstantiationException e) {
//            L.wtf("InstantiationException which cannot happen", e);
//        } catch (InvocationTargetException e) {
//            L.wtf("InvocationTargetException which cannot happen", e);
//        }
//        L.d("Loaded " + list.size() + " files prefixed with " + prefix);
//        return list;
//    }
}
