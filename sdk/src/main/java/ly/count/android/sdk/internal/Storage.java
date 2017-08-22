package ly.count.android.sdk.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Storing and retrieving data from internal storage of SDK.
 * Thread safety is based on single thread of execution - only one thread works with storage at a time
 * in a single process thanks to {@link Tasks}.
 */

class Storage {
    private static final Tasks tasks = new Tasks();

    static String name(Storable storable) {
        return storable.storagePrefix() + "_" + storable.storageId();
    }

    /**
     * Stores data in device internal memory. When a storable with the same id already exists,
     * replaces it with new data.
     *
     * @param storable Object to store
     * @return true when storing succeeded, false otherwise
     */
    static boolean push(Storable storable) {
        Log.d("Pushing " + name(storable));
        try {
            return pushAsync(storable).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.wtf("Interrupted while pushing " + name(storable), e);
        }
        return false;
    }

    /**
     * Stores data in device internal memory. When a storable with the same id already exists,
     * replaces it with new data. Runs in a storage thread provided by {@link Tasks}
     *
     * @param storable Object to store
     * @param callback nullable callback to call when done
     * @return Future<Boolean> object which resolves as true when storing succeeded, false otherwise
     */
    static Future<Boolean> pushAsync(final Storable storable, Tasks.Callback<Boolean> callback) {
        Log.d("Pushing async " + name(storable));
        return tasks.run(new Tasks.Task<Boolean>(storable.storageId()) {
            @Override
            public Boolean call() throws Exception {
                return Core.instance.pushDataToInternalStorage(storable.storagePrefix(), "" + storable.storageId(), storable.store());
            }
        }, callback);
    }

    /**
     * Shorthand for {@link #pushAsync(Storable, ly.count.android.sdk.internal.Tasks.Callback)}
     *
     * @param storable Object to store
     * @return Future<Boolean> object which resolves as true when storing succeeded, false otherwise
     */
    static Future<Boolean> pushAsync(final Storable storable) {
        return pushAsync(storable, null);
    }
    /**
     * Removes storable from storage.
     *
     * @param storable Object to remove
     * @return true if removed, false otherwise
     */
    static <T extends Storable> Boolean remove(T storable) {
        Log.d("removing " + name(storable));
        try {
            return removeAsync(storable).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.wtf("Interrupted while removing " + name(storable), e);
        }
        return null;
    }

    /**
     * Removes storable from storage.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param storable Object to remove
     * @return Future<Boolean> object which resolves to true if storable is removed, false otherwise
     */
    static <T extends Storable> Future<Boolean> removeAsync(final T storable) {
        return tasks.run(new Tasks.Task<Boolean>(-storable.storageId()) {
            @Override
            public Boolean call() throws Exception {
                return Core.instance.removeDataFromInternalStorage(storable.storagePrefix(), "" + storable.storageId());
            }
        });
    }


    /**
     * Reinitializes storable with data stored previously in device internal memory and deletes corresponding file.
     *
     * @param storable Object to reinitialize
     * @return storable object passed as param when restoring succeeded, null otherwise
     */
    static <T extends Storable> T pop(T storable) {
        Log.d("Popping " + name(storable));
        try {
            return popAsync(storable).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.wtf("Interrupted while popping " + name(storable), e);
        }
        return null;
    }

    /**
     * Reinitializes storable with data stored previously in device internal memory.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param storable Object to reinitialize
     * @return Future<Storable> object which resolves as object passed as param when restoring succeeded, null otherwise
     */
    static <T extends Storable> Future<T> popAsync(final T storable) {
        return tasks.run(new Tasks.Task<T>(-storable.storageId()) {
            @Override
            public T call() throws Exception {
                byte data[] = Core.instance.popDataFromInternalStorage(storable.storagePrefix(), "" + storable.storageId());
                if (data == null) {
                    Log.d("No data for file " + name(storable));
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
     * Reinitializes storable with data stored previously in device internal memory.
     *
     * @param storable Object to reinitialize
     * @return storable object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> T read(T storable) {
        Log.d("read " + name(storable));
        try {
            return readAsync(storable).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.wtf("Interrupted while popping " + name(storable), e);
        }
        return null;
    }

    /**
     * Reinitializes storable with data stored previously in device internal memory.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param storable Object to reinitialize
     * @return Future<Storable> object which resolves as object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> Future<T> readAsync(final T storable) {
        return tasks.run(new Tasks.Task<T>(-storable.storageId()) {
            @Override
            public T call() throws Exception {
                byte data[] = Core.instance.readDataFromInternalStorage(storable.storagePrefix(), "" + storable.storageId());
                if (data == null) {
                    Log.d("No data for file " + name(storable));
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
     * Reinitializes first (or last if asc is false) storable with prefix from storable supplied as parameter.
     *
     * @param storable Object to get prefix from
     * @param asc true if reading first storable, false if reading last one
     * @return storable object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> T readOne(T storable, boolean asc) {
        Log.d("readOne " + storable.storagePrefix());
        try {
            return readOneAsync(storable, asc).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.wtf("Interrupted while popping " + name(storable), e);
        }
        return null;
    }

    /**
     * Reinitializes first (or last if asc is false) storable with prefix from storable supplied as parameter.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param storable Object to get prefix from
     * @param asc true if reading first storable, false if reading last one
     * @return Future<Storable> object which resolves as object passed as param when reading succeeded, null otherwise
     */
    static <T extends Storable> Future<T> readOneAsync(final T storable, final boolean asc) {
        return tasks.run(new Tasks.Task<T>(-storable.storageId()) {
            @Override
            public T call() throws Exception {
                Object data[] = Core.instance.readOneFromInternalStorage(storable.storagePrefix(), asc);
                if (data == null) {
                    Log.d("No data for file " + name(storable));
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
                    Log.wtf("Wrong file name in readOneAsync", e);
                    return null;
                }
            }
        });
    }

    static List<Long> list(String prefix) {
        return list(prefix, 0);
    }

    static List<Long> list(String prefix, int slice) {
        Log.d("Listing " + prefix);
        try {
            return listAsync(prefix, slice).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.wtf("Interrupted while listing " + prefix, e);
        }
        return null;
    }

    /**
     * Retrieves ids of all files stored for a specific prefix ({@link Storable#storagePrefix()}.
     * Runs in a storage thread provided by {@link Tasks}
     *
     * @param prefix String representing type of storable to list (prefix of file names)
     * @param slice integer controlling number and slice direction of results returned:
     *              0 to return all records
     *              1..N to return first N records ordered from first to last
     *              -1..-N to return last N records ordered from last to first
     * @return Future<List<Long>> object which resolves as list of storable ids, not null
     */
    static Future<List<Long>> listAsync(final String prefix, final int slice) {
        return tasks.run(new Tasks.Task<List<Long>>(-1L) {
            @Override
            public List<Long> call() throws Exception {
                List<Long> list = new ArrayList<Long>();
                List<String> files = Core.instance.listDataInInternalStorage(prefix, slice);
                if (files == null) {
                    Log.wtf("Null list while listing storage");
                    return list;
                }
                for (String file : files) {
                    try {
                        list.add(Long.parseLong(file));
                    } catch (Throwable t) {
                        Log.wtf("Exception while parsing storable id", t);
                    }
                }
                return list;
            }
        });
    }

//    static synchronized List<Storable> popAll(String prefix, Class<? extends Storable> claz) {
//        List<String> names = Core.instance.listDataFromInternalStorage(prefix);
//        List<Storable> list = new ArrayList<>();
//        Log.d("Loading " + names.size() + " files prefixed with " + prefix + ": " + names);
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
//            Log.wtf("Storage error - bad file name detected", e);
//        } catch (NoSuchMethodException e) {
//            Log.wtf("Cannot happen", e);
//        } catch (IllegalAccessException e) {
//            Log.wtf("IllegalAccessException which cannot happen", e);
//        } catch (InstantiationException e) {
//            Log.wtf("InstantiationException which cannot happen", e);
//        } catch (InvocationTargetException e) {
//            Log.wtf("InvocationTargetException which cannot happen", e);
//        }
//        Log.d("Loaded " + list.size() + " files prefixed with " + prefix);
//        return list;
//    }
}
