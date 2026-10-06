package ly.count.android.sdk.hub;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts requests and responses to and from the bundles that cross the binder, following
 * {@link HubProtocol}.
 */
final class HubBundles {
    private static final int DEFAULT_TIMEOUT_MILLIS = 30_000;

    private HubBundles() {
    }

    /**
     * Puts a request into a bundle.
     *
     * @param request the request
     * @param includeBody whether the body travels inside the bundle; when it does not, the caller adds
     * a file descriptor under {@link HubProtocol#KEY_BODY_FILE}
     * @return the bundle
     */
    static @NonNull Bundle requestToBundle(@NonNull HubRequest request, boolean includeBody) {
        Bundle bundle = new Bundle();
        bundle.putInt(HubProtocol.KEY_VERSION, HubProtocol.VERSION);
        bundle.putInt(HubProtocol.KEY_REPLY_WHEN, HubProtocol.REPLY_WHEN_DELIVERED);
        bundle.putString(HubProtocol.KEY_METHOD, request.getMethod());
        bundle.putString(HubProtocol.KEY_PATH, request.getPath());
        bundle.putString(HubProtocol.KEY_QUERY, request.getQuery());
        putHeaders(bundle, request.getHeaders());
        if (includeBody && request.getBody() != null) {
            bundle.putByteArray(HubProtocol.KEY_BODY, request.getBody());
        }
        bundle.putInt(HubProtocol.KEY_TIMEOUT_MILLIS, request.getTimeoutMillis());
        return bundle;
    }

    /**
     * Reads a request out of a bundle the hub received, including a body sent through a file descriptor.
     *
     * @param bundle the bundle
     * @param maxBodyBytes the largest body the hub accepts
     * @return the request
     * @throws HubRejection if the bundle is not a request the hub understands or the body is too large
     */
    @SuppressWarnings("deprecation")
    static @NonNull HubRequest requestFromBundle(@NonNull Bundle bundle, int maxBodyBytes) throws HubRejection {
        bundle.setClassLoader(HubBundles.class.getClassLoader());
        int version = bundle.getInt(HubProtocol.KEY_VERSION, 0);
        if (version < 1) {
            throw new HubRejection(400, "unsupported protocol version " + version);
        }
        int replyWhen = bundle.getInt(HubProtocol.KEY_REPLY_WHEN, HubProtocol.REPLY_WHEN_DELIVERED);
        if (replyWhen != HubProtocol.REPLY_WHEN_DELIVERED) {
            throw new HubRejection(400, "unsupported reply mode " + replyWhen);
        }
        String method = bundle.getString(HubProtocol.KEY_METHOD);
        String path = bundle.getString(HubProtocol.KEY_PATH);
        if (method == null || path == null) {
            throw new HubRejection(400, "request without method or path");
        }

        byte[] body = bundle.getByteArray(HubProtocol.KEY_BODY);
        Object bodyFile = bundle.getParcelable(HubProtocol.KEY_BODY_FILE);
        if (bodyFile instanceof ParcelFileDescriptor) {
            body = readBody((ParcelFileDescriptor) bodyFile, maxBodyBytes);
        } else if (body != null && body.length > HubProtocol.INLINE_BODY_LIMIT) {
            // A larger body has to come through a file descriptor; inline it would sit in the binder buffer shared by every in-flight call
            throw new HubRejection(413, "inline request body is larger than " + HubProtocol.INLINE_BODY_LIMIT + " bytes");
        }
        if (body != null && body.length > maxBodyBytes) {
            throw new HubRejection(413, "request body is larger than " + maxBodyBytes + " bytes");
        }

        int timeoutMillis = bundle.getInt(HubProtocol.KEY_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS);
        return new HubRequest(method, path, bundle.getString(HubProtocol.KEY_QUERY), readHeaders(bundle), body, timeoutMillis);
    }

    /**
     * Puts a response into the reply bundle.
     *
     * @param response the response
     * @return the bundle
     */
    static @NonNull Bundle responseToBundle(@NonNull HubResponse response) {
        Bundle bundle = new Bundle();
        bundle.putInt(HubProtocol.KEY_STATUS, response.getStatus());
        putHeaders(bundle, response.getHeaders());
        bundle.putByteArray(HubProtocol.KEY_BODY, response.getBody());
        return bundle;
    }

    /**
     * Makes the reply bundle for a request the hub could not get a server response for.
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
     * Reads the response out of a reply bundle.
     *
     * @param bundle the reply, null when the hub sent none
     * @return the response
     * @throws IOException if the reply carries a failure or is not a response
     */
    static @NonNull HubResponse responseFromBundle(@Nullable Bundle bundle) throws IOException {
        if (bundle == null) {
            throw new IOException("The hub sent no reply");
        }
        String failure = bundle.getString(HubProtocol.KEY_FAILURE);
        if (failure != null) {
            throw new IOException("The hub could not deliver the request: " + failure);
        }
        int status = bundle.getInt(HubProtocol.KEY_STATUS, 0);
        if (status <= 0) {
            throw new IOException("The hub sent a reply without a status");
        }
        byte[] body = bundle.getByteArray(HubProtocol.KEY_BODY);
        return new HubResponse(status, readHeaders(bundle), body == null ? new byte[0] : body);
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

    /**
     * Reads a body sent through a file descriptor and closes the descriptor.
     *
     * @param descriptor the descriptor
     * @param maxBodyBytes the largest body the hub accepts
     * @return the body
     * @throws HubRejection if the body is larger than the limit or cannot be read
     */
    private static @NonNull byte[] readBody(@NonNull ParcelFileDescriptor descriptor, int maxBodyBytes) throws HubRejection {
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            // A pipe or socket reports -1 and could block a binder thread forever; only a real file,
            // which is all the client ever sends, has a known size to read up to
            long declaredSize = descriptor.getStatSize();
            if (declaredSize < 0) {
                throw new HubRejection(400, "request body is not a readable file");
            }
            if (declaredSize > maxBodyBytes) {
                throw new HubRejection(413, "request body is larger than " + maxBodyBytes + " bytes");
            }
            // The buffer grows with the bytes actually read, not up to the size the descriptor claims,
            // so a descriptor that lies about its size cannot force a large allocation
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while (body.size() < declaredSize && (read = in.read(buffer)) != -1) {
                body.write(buffer, 0, read);
            }
            return body.toByteArray();
        } catch (IOException e) {
            throw new HubRejection(400, "request body could not be read");
        }
    }
}
