package ly.count.android.sdk.hub;

import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.OsConstants;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts requests and responses to and from the bundles that cross the binder, following
 * {@link HubProtocol}. Bodies up to {@link HubProtocol#INLINE_BODY_LIMIT} travel inside the bundle,
 * larger ones in read-only shared memory from Android 8.1, and before that through the descriptor of a
 * temporary file that is deleted as soon as it is opened, so nothing is left behind if a process dies.
 */
final class HubBundles {
    private static final int DEFAULT_TIMEOUT_MILLIS = 30_000;

    private HubBundles() {
    }

    /**
     * Puts the method, path, query, headers and timeout of a request into a bundle. The body is added
     * separately with {@link #putBody}.
     *
     * @param request the request
     * @return the bundle
     */
    static @NonNull Bundle requestToBundle(@NonNull HubRequest request) {
        Bundle bundle = new Bundle();
        bundle.putInt(HubProtocol.KEY_VERSION, HubProtocol.VERSION);
        bundle.putInt(HubProtocol.KEY_REPLY_WHEN, HubProtocol.REPLY_WHEN_DELIVERED);
        bundle.putString(HubProtocol.KEY_METHOD, request.getMethod());
        bundle.putString(HubProtocol.KEY_PATH, request.getPath());
        bundle.putString(HubProtocol.KEY_QUERY, request.getQuery());
        putHeaders(bundle, request.getHeaders());
        bundle.putInt(HubProtocol.KEY_TIMEOUT_MILLIS, request.getTimeoutMillis());
        return bundle;
    }

    /**
     * Adds a body to a bundle, inline when it is small and otherwise in shared memory or a file
     * descriptor. The returned handle must be closed once the bundle has been sent.
     *
     * @param bundle the bundle to add to
     * @param body the body, or null when there is none
     * @param cacheDir where a temporary file can be written before Android 8.1
     * @return a handle that releases what carries the body, never null
     * @throws IOException if the body cannot be placed in shared memory or a file
     */
    static @NonNull Closeable putBody(@NonNull Bundle bundle, @Nullable byte[] body, @NonNull File cacheDir) throws IOException {
        if (body == null) {
            return () -> {
            };
        }
        if (body.length <= HubProtocol.INLINE_BODY_LIMIT) {
            bundle.putByteArray(HubProtocol.KEY_BODY, body);
            return () -> {
            };
        }
        if (Build.VERSION.SDK_INT >= 27) {
            final SharedMemory memory = toSharedMemory(body);
            bundle.putParcelable(HubProtocol.KEY_BODY_MEMORY, memory);
            return memory::close;
        }
        final ParcelFileDescriptor descriptor = toUnlinkedFile(body, cacheDir);
        bundle.putParcelable(HubProtocol.KEY_BODY_FILE, descriptor);
        return descriptor;
    }

    /**
     * Reads a request out of a bundle the hub received, including a body carried in shared memory or a
     * file descriptor. Whatever carries the body is closed before returning, whether or not the request
     * is accepted.
     *
     * @param bundle the bundle
     * @param maxBodyBytes the largest body the hub accepts
     * @return the request
     * @throws HubRejection if the bundle is not a request this hub accepts; the rejection tells whether
     * the request can never be accepted, or only not by this version of the hub
     * @throws IOException if the body could not be read this time, for example because its shared memory
     * could not be mapped; the request can be handed over again later
     */
    static @NonNull HubRequest requestFromBundle(@NonNull Bundle bundle, int maxBodyBytes) throws HubRejection, IOException {
        bundle.setClassLoader(HubBundles.class.getClassLoader());
        Object memory = parcelable(bundle, HubProtocol.KEY_BODY_MEMORY);
        Object file = parcelable(bundle, HubProtocol.KEY_BODY_FILE);
        try {
            int version = bundle.getInt(HubProtocol.KEY_VERSION, 0);
            if (version < 1) {
                throw new HubRejection(400, "unsupported protocol version " + version, true);
            }
            int replyWhen = bundle.getInt(HubProtocol.KEY_REPLY_WHEN, HubProtocol.REPLY_WHEN_DELIVERED);
            if (replyWhen != HubProtocol.REPLY_WHEN_DELIVERED) {
                throw new HubRejection(501, "reply mode " + replyWhen + " is not supported by this hub", false);
            }
            String method = bundle.getString(HubProtocol.KEY_METHOD);
            String path = bundle.getString(HubProtocol.KEY_PATH);
            if (method == null || path == null) {
                throw new HubRejection(400, "request without method or path", true);
            }

            byte[] body;
            try {
                body = readBody(bundle.getByteArray(HubProtocol.KEY_BODY), memory, file, HubProtocol.MAX_INLINE_BODY_ACCEPTED, maxBodyBytes);
            } catch (BodyTooLargeException e) {
                throw new HubRejection(413, e.getMessage(), true);
            } catch (UnreadableBodyException e) {
                throw new HubRejection(400, e.getMessage(), true);
            }

            int timeoutMillis = bundle.getInt(HubProtocol.KEY_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS);
            return new HubRequest(method, path, bundle.getString(HubProtocol.KEY_QUERY), readHeaders(bundle), body, timeoutMillis);
        } finally {
            closeQuietly(memory);
            closeQuietly(file);
        }
    }

    /**
     * Makes the reply that tells the client its request was admitted and that the response will come
     * through the callback.
     *
     * @param replyWithinMillis how long the client should wait for the callback
     * @return the bundle
     */
    static @NonNull Bundle acceptedToBundle(long replyWithinMillis) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(HubProtocol.KEY_ACCEPTED, true);
        bundle.putLong(HubProtocol.KEY_REPLY_WITHIN_MILLIS, replyWithinMillis);
        return bundle;
    }

    /**
     * Makes the reply for a request the hub could not get a server response for.
     *
     * @param message why, without any request data
     * @return the bundle
     */
    static @NonNull Bundle failureToBundle(@NonNull String message) {
        Bundle bundle = new Bundle();
        bundle.putString(HubProtocol.KEY_FAILURE, message);
        return bundle;
    }

    /**
     * Puts a response into a reply bundle, with a large body in shared memory or a file descriptor.
     *
     * @param response the response
     * @param cacheDir where a temporary file can be written before Android 8.1
     * @return the bundle and the handle that releases its body once the bundle has been sent
     * @throws IOException if the body cannot be placed in shared memory or a file
     */
    static @NonNull Outgoing responseToBundle(@NonNull HubResponse response, @NonNull File cacheDir) throws IOException {
        Bundle bundle = new Bundle();
        bundle.putInt(HubProtocol.KEY_STATUS, response.getStatus());
        putHeaders(bundle, response.getHeaders());
        Closeable release = putBody(bundle, response.getBody(), cacheDir);
        return new Outgoing(bundle, release);
    }

    /**
     * Tells whether a reply admitted the request, so that its response comes through the callback.
     *
     * @param bundle the reply
     * @return true when the request was admitted
     */
    static boolean isAccepted(@Nullable Bundle bundle) {
        return bundle != null && bundle.getBoolean(HubProtocol.KEY_ACCEPTED, false);
    }

    /**
     * @param bundle an admitting reply
     * @return how long the client should wait for the callback, in milliseconds
     */
    static long replyWithinMillis(@NonNull Bundle bundle) {
        return bundle.getLong(HubProtocol.KEY_REPLY_WITHIN_MILLIS, 0);
    }

    /**
     * Reads the response out of a reply or callback bundle and closes whatever carried its body.
     *
     * @param bundle the reply, null when the hub sent none
     * @param maxBodyBytes the largest response body accepted
     * @return the response
     * @throws IOException if the reply carries a failure, is not a response, or its body cannot be read
     */
    static @NonNull HubResponse responseFromBundle(@Nullable Bundle bundle, int maxBodyBytes) throws IOException {
        if (bundle == null) {
            throw new IOException("The hub sent no reply");
        }
        bundle.setClassLoader(HubBundles.class.getClassLoader());
        Object memory = parcelable(bundle, HubProtocol.KEY_BODY_MEMORY);
        Object file = parcelable(bundle, HubProtocol.KEY_BODY_FILE);
        try {
            String failure = bundle.getString(HubProtocol.KEY_FAILURE);
            if (failure != null) {
                throw new IOException("The hub could not deliver the request: " + failure);
            }
            int status = bundle.getInt(HubProtocol.KEY_STATUS, 0);
            if (status <= 0) {
                throw new IOException("The hub sent a reply without a status");
            }
            byte[] body = readBody(bundle.getByteArray(HubProtocol.KEY_BODY), memory, file, maxBodyBytes, maxBodyBytes);
            return new HubResponse(status, readHeaders(bundle), body == null ? new byte[0] : body);
        } finally {
            closeQuietly(memory);
            closeQuietly(file);
        }
    }

    /**
     * A bundle ready to be sent and the handle that releases its body afterwards.
     */
    static final class Outgoing {
        final Bundle bundle;
        final Closeable release;

        /**
         * @param bundle the bundle
         * @param release releases what carries the body
         */
        Outgoing(@NonNull Bundle bundle, @NonNull Closeable release) {
            this.bundle = bundle;
            this.release = release;
        }
    }

    /**
     * Tells whether a request's body travels in shared memory or a file descriptor rather than inline.
     *
     * @param bundle the request bundle
     * @return true when the body is carried outside the bundle
     */
    static boolean hasOutOfLineBody(@NonNull Bundle bundle) {
        bundle.setClassLoader(HubBundles.class.getClassLoader());
        return bundle.containsKey(HubProtocol.KEY_BODY_MEMORY) || bundle.containsKey(HubProtocol.KEY_BODY_FILE);
    }

    /**
     * Closes the shared memory or file descriptor that carries a request's body, for a request that is
     * turned away before its body is read.
     *
     * @param bundle the request bundle
     */
    static void closeBody(@NonNull Bundle bundle) {
        bundle.setClassLoader(HubBundles.class.getClassLoader());
        closeQuietly(parcelable(bundle, HubProtocol.KEY_BODY_MEMORY));
        closeQuietly(parcelable(bundle, HubProtocol.KEY_BODY_FILE));
    }

    /**
     * A body is larger than the receiving side accepts.
     */
    private static final class BodyTooLargeException extends IOException {
        /**
         * @param message what was too large
         */
        BodyTooLargeException(@NonNull String message) {
            super(message);
        }
    }

    /**
     * A body is carried by something that is not a readable regular file, which can never succeed.
     */
    private static final class UnreadableBodyException extends IOException {
        /**
         * @param message what was wrong with the carrier
         */
        UnreadableBodyException(@NonNull String message) {
            super(message);
        }
    }

    /**
     * Reads a body from whichever carrier is present, checking it against the limits.
     *
     * @param inline the inline body, or null
     * @param memory a {@link SharedMemory} carrying the body, or null
     * @param file a {@link ParcelFileDescriptor} carrying the body, or null
     * @param maxInlineBytes the largest inline body accepted
     * @param maxBodyBytes the largest body accepted
     * @return the body, or null when there is none
     * @throws IOException if the body cannot be read this time, {@link UnreadableBodyException} if its
     * carrier can never be read, or {@link BodyTooLargeException} if it is too large
     */
    private static @Nullable byte[] readBody(@Nullable byte[] inline, @Nullable Object memory, @Nullable Object file, int maxInlineBytes, int maxBodyBytes) throws IOException {
        if (Build.VERSION.SDK_INT >= 27 && memory instanceof SharedMemory) {
            return fromSharedMemory((SharedMemory) memory, maxBodyBytes);
        }
        if (file instanceof ParcelFileDescriptor) {
            return fromFile((ParcelFileDescriptor) file, maxBodyBytes);
        }
        if (inline != null) {
            if (inline.length > maxInlineBytes) {
                throw new BodyTooLargeException("inline body is larger than " + maxInlineBytes + " bytes");
            }
            if (inline.length > maxBodyBytes) {
                throw new BodyTooLargeException("body is larger than " + maxBodyBytes + " bytes");
            }
        }
        return inline;
    }

    /**
     * Copies a body into read-only shared memory.
     *
     * @param body the body
     * @return the shared memory, protected against writes
     * @throws IOException if the shared memory cannot be created
     */
    @RequiresApi(27)
    private static @NonNull SharedMemory toSharedMemory(@NonNull byte[] body) throws IOException {
        try {
            SharedMemory memory = SharedMemory.create("countly-hub-body", body.length);
            ByteBuffer buffer = memory.mapReadWrite();
            buffer.put(body);
            SharedMemory.unmap(buffer);
            memory.setProtect(OsConstants.PROT_READ);
            return memory;
        } catch (ErrnoException | RuntimeException e) {
            throw new IOException("The body could not be placed in shared memory", e);
        }
    }

    /**
     * Copies a body out of shared memory. Reading mapped memory cannot block, so a sender cannot stall
     * the reading thread.
     *
     * @param memory the shared memory
     * @param maxBodyBytes the largest body accepted
     * @return the body
     * @throws IOException if the memory cannot be mapped, or {@link BodyTooLargeException} if it is too large
     */
    @RequiresApi(27)
    private static @NonNull byte[] fromSharedMemory(@NonNull SharedMemory memory, int maxBodyBytes) throws IOException {
        int size = memory.getSize();
        if (size > maxBodyBytes) {
            throw new BodyTooLargeException("body is larger than " + maxBodyBytes + " bytes");
        }
        try {
            ByteBuffer buffer = memory.mapReadOnly();
            try {
                byte[] body = new byte[size];
                buffer.get(body);
                return body;
            } finally {
                SharedMemory.unmap(buffer);
            }
        } catch (ErrnoException | RuntimeException e) {
            throw new IOException("The body could not be read from shared memory", e);
        }
    }

    /**
     * Writes a body to a temporary file, opens it and deletes the file right away. The open descriptor
     * keeps the data readable until it is closed, so no file is left behind if a process dies.
     *
     * @param body the body
     * @param cacheDir where to write the file
     * @return a read-only descriptor of the deleted file
     * @throws IOException if the file cannot be written
     */
    private static @NonNull ParcelFileDescriptor toUnlinkedFile(@NonNull byte[] body, @NonNull File cacheDir) throws IOException {
        File file = File.createTempFile("countly-hub-", ".body", cacheDir);
        try {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(body);
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } finally {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    /**
     * Reads a body sent through a file descriptor. Only a regular file is read: a pipe or socket reports
     * no size and could block the reading thread.
     *
     * @param descriptor the descriptor
     * @param maxBodyBytes the largest body accepted
     * @return the body
     * @throws IOException if the body cannot be read, {@link UnreadableBodyException} if the descriptor is
     * not a regular file, or {@link BodyTooLargeException} if the body is too large
     */
    private static @NonNull byte[] fromFile(@NonNull ParcelFileDescriptor descriptor, int maxBodyBytes) throws IOException {
        long declaredSize = descriptor.getStatSize();
        if (declaredSize < 0) {
            throw new UnreadableBodyException("request body is not a regular file");
        }
        if (declaredSize > maxBodyBytes) {
            throw new BodyTooLargeException("body is larger than " + maxBodyBytes + " bytes");
        }
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            ByteArrayOutputStream body = new ByteArrayOutputStream((int) declaredSize);
            byte[] buffer = new byte[16 * 1024];
            int read;
            while (body.size() < declaredSize && (read = in.read(buffer)) != -1) {
                body.write(buffer, 0, read);
            }
            return body.toByteArray();
        }
    }

    /**
     * @param bundle the bundle to read from
     * @param key the key
     * @return the parcelable stored under the key, or null
     */
    @SuppressWarnings("deprecation")
    private static @Nullable Object parcelable(@NonNull Bundle bundle, @NonNull String key) {
        return bundle.getParcelable(key);
    }

    /**
     * Closes a body carrier read from a bundle, ignoring failures.
     *
     * @param carrier a {@link SharedMemory}, a {@link ParcelFileDescriptor} or null
     */
    private static void closeQuietly(@Nullable Object carrier) {
        try {
            if (carrier instanceof Closeable) {
                ((Closeable) carrier).close();
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /**
     * @param bundle the bundle to write into
     * @param headers the headers to write as two parallel arrays
     */
    private static void putHeaders(@NonNull Bundle bundle, @NonNull Map<String, String> headers) {
        String[] names = new String[headers.size()];
        String[] values = new String[headers.size()];
        int i = 0;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            names[i] = header.getKey();
            values[i] = header.getValue();
            i++;
        }
        bundle.putStringArray(HubProtocol.KEY_HEADER_NAMES, names);
        bundle.putStringArray(HubProtocol.KEY_HEADER_VALUES, values);
    }

    /**
     * Reads the headers, keeping one entry per header name. Names that differ only in case are collapsed
     * to a single entry whose value is the last one, so the gate and the uplink read the same header the
     * server would, and a crafted duplicate cannot hide a second value from either.
     *
     * @param bundle the bundle to read from
     * @return the headers, skipping entries without a name or value
     */
    private static @NonNull Map<String, String> readHeaders(@NonNull Bundle bundle) {
        Map<String, String> headers = new LinkedHashMap<>();
        String[] names = bundle.getStringArray(HubProtocol.KEY_HEADER_NAMES);
        String[] values = bundle.getStringArray(HubProtocol.KEY_HEADER_VALUES);
        if (names == null || values == null) {
            return headers;
        }
        Map<String, String> canonicalByLowerName = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(names.length, values.length); i++) {
            if (names[i] == null || values[i] == null) {
                continue;
            }
            String previousName = canonicalByLowerName.put(names[i].toLowerCase(Locale.ROOT), names[i]);
            if (previousName != null) {
                headers.remove(previousName);
            }
            headers.put(names[i], values[i]);
        }
        return headers;
    }
}
