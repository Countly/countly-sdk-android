package ly.count.sdk.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import ly.count.sdk.internal.Log;

/**
 * Encapsulation of {@link ExecutorService} for single thread case which returns the same
 * {@link Future} for {@link Task}s with the same id.
 */

public class Tasks {
    public final Log.Module L;
    public static final Long ID_STRICT = 0L;
    public static final Long ID_LIST = -1L;

    public static abstract class Task<T> implements Callable<T> {
        Long id;

        Task(Long id) {
            this.id = id;
        }
        abstract public T call() throws Exception;
    }

    public interface Callback<T> {
        void call(T param) throws Exception;
    }

    /**
     * Service which runs {@link Callable}s
     */
    private final ExecutorService executor;
    private Long running = null;

    /**
     * Map of {@link Future}s for {@link Callable}s not yet resolved
     */
    private final Map<Long, Future> pending;

    public Tasks(final String name) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                return new Thread(runnable, name);
            }
        });
        pending = new HashMap<>();
        L = Log.module(name + " tasks");
    }

    /**
     * Runs {@link Task} supplied in a single thread executor in a way which omits duplicate tasks
     * which are scheduled to run prior to last one.
     * Example: tasks ABCD, adding task C, queue becomes ABDC.
     *
     * @param task task to run
     * @param <T> Callable result type
     * @return Future of task result
     */
    public <T> Future<T> run(final Task<T> task) {
        return run(task, null);
    }

    /**
     * Runs {@link Task} supplied in a single thread executor in a way which omits duplicate tasks
     * which are scheduled to run prior to last one.
     * Example: tasks ABCD, adding task C, queue becomes ABDC.
     *
     * @param task task to run
     * @param callback callback to call after task completion in executor thread
     * @param <T> Callable result type
     * @return Future of task result
     */
    <T> Future<T> run(final Task<T> task, final Callback<T> callback) {
        synchronized (pending) {
            L.d("pending " + pending.keySet() + ", running " + task.id);
            if (!task.id.equals(0L)) {
                @SuppressWarnings("unchecked")
                Future<T> existing = pending.get(task.id);

                // In case task with same id is already in queue and isn't running yet, return its future instead of adding another task
                if (existing != null) {
                    if (!existing.isDone() && !existing.isCancelled() && (running == null || !running.equals(task.id))) {
                        L.d(task.id + " exists");
                        return existing;
                    }
                }
            }

            Future<T> future = executor.submit(new Task<T>(task.id) {
                @Override
                public T call() throws Exception {
                    running = task.id;
                    T result = task.call();
                    synchronized (pending) {
                        if (!task.id.equals(0L)) {
                            pending.remove(task.id);
                        }
                        running = null;
                        L.d("pending " + pending.keySet() + ", done running " + task.id);
                    }
                    if (callback != null) {
                        callback.call(result);
                    }
                    return result;
                }
            });

            if (!task.id.equals(0L)) {
                pending.put(task.id, future);
            }

            return future;
        }
    }

    boolean isRunning() {
        return running != null;
    }

    void shutdown() {
        if (!executor.isShutdown() && !executor.isTerminated()) {
            L.i("shutting down");
            executor.shutdown();
            try {
                // Wait a while for existing tasks to terminate
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        L.e("didn't shutdown gracefully");
                    }
                }
            } catch (Throwable t) {
                L.e("Error while shutting down tasks", t);
            }
        }
    }

    void await() {
        try {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            L.w("Interrupted while waiting for Tasks to finish running tasks", e);
        }
    }
}
