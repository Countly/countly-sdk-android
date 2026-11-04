package ly.count.android.sdk.hub;

import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Checks how requests and responses are put into bundles and read back, including the shared memory
 * and file descriptors that carry large bodies.
 */
@RunWith(AndroidJUnit4.class)
public class HubBundlesTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MB = 1024 * 1024;

    private final File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();

    private static byte[] bytes(int size) {
        byte[] body = new byte[size];
        for (int i = 0; i < size; i++) {
            body[i] = (byte) (i * 31);
        }
        return body;
    }

    private static HubRequest post(byte[] body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("X-Test", "1");
        return new HubRequest("POST", "/i", "app_key=k", headers, body, 45_000);
    }

    /**
     * Writes a bundle into a parcel and reads it back, as the binder does between processes, so that
     * shared memory and file descriptors are carried the way they are in a real call.
     */
    private static Bundle throughParcel(Bundle bundle) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(bundle);
            parcel.setDataPosition(0);
            Bundle copy = parcel.readBundle(HubBundles.class.getClassLoader());
            Assert.assertNotNull(copy);
            copy.size();
            return copy;
        } finally {
            parcel.recycle();
        }
    }

    @SuppressWarnings("deprecation")
    private static Object carrier(Bundle bundle) {
        Object memory = bundle.getParcelable(HubProtocol.KEY_BODY_MEMORY);
        return memory != null ? memory : bundle.getParcelable(HubProtocol.KEY_BODY_FILE);
    }

    private static void assertClosed(Object carrier) {
        try {
            if (Build.VERSION.SDK_INT >= 27 && carrier instanceof SharedMemory) {
                ((SharedMemory) carrier).getSize();
            } else {
                ((ParcelFileDescriptor) carrier).getFd();
            }
            Assert.fail("the body carrier was left open");
        } catch (IllegalStateException expected) {
            Assert.assertNotNull(expected);
        }
    }

    /**
     * A small request travels inside the bundle and comes out unchanged.
     */
    @Test
    public void request_inlineRoundTrip() throws Exception {
        byte[] body = "app_key=k&events=%5B%5D".getBytes(UTF_8);
        Bundle bundle = HubBundles.requestToBundle(post(body));
        Closeable release = HubBundles.putBody(bundle, body, cacheDir);

        HubRequest read = HubBundles.requestFromBundle(throughParcel(bundle), MB);
        release.close();

        Assert.assertEquals("POST", read.getMethod());
        Assert.assertEquals("/i", read.getPath());
        Assert.assertEquals("app_key=k", read.getQuery());
        Assert.assertEquals("application/x-www-form-urlencoded", read.getHeader("content-type"));
        Assert.assertEquals("1", read.getHeader("X-Test"));
        Assert.assertEquals(45_000, read.getTimeoutMillis());
        Assert.assertArrayEquals(body, read.getBody());
        Assert.assertNull(carrier(bundle));
    }

    /**
     * A body above the inline limit travels in shared memory from Android 8.1, and through the
     * descriptor of an already deleted file before that, and comes out byte for byte.
     */
    @Test
    public void request_largeBodyTravelsOutsideTheBundle() throws Exception {
        byte[] body = bytes(HubProtocol.INLINE_BODY_LIMIT * 3 + 7);
        Bundle bundle = HubBundles.requestToBundle(post(body));
        Closeable release = HubBundles.putBody(bundle, body, cacheDir);

        Assert.assertNull(bundle.getByteArray(HubProtocol.KEY_BODY));
        Object carrier = carrier(bundle);
        if (Build.VERSION.SDK_INT >= 27) {
            Assert.assertTrue(carrier instanceof SharedMemory);
        } else {
            Assert.assertTrue(carrier instanceof ParcelFileDescriptor);
        }
        File[] leftovers = cacheDir.listFiles((dir, name) -> name.startsWith("countly-hub-"));
        Assert.assertTrue(leftovers == null || leftovers.length == 0);

        HubRequest read = HubBundles.requestFromBundle(throughParcel(bundle), MB);
        release.close();

        Assert.assertArrayEquals(body, read.getBody());
    }

    /**
     * A body larger than the hub accepts is refused for good with 413, and whatever carried it is
     * closed even though the request was not read.
     */
    @Test
    public void request_oversizedBody_isRefusedAndItsCarrierClosed() throws Exception {
        byte[] body = bytes(200 * 1024);
        Bundle bundle = HubBundles.requestToBundle(post(body));
        HubBundles.putBody(bundle, body, cacheDir);
        Object carrier = carrier(bundle);

        try {
            HubBundles.requestFromBundle(bundle, 100 * 1024);
            Assert.fail("expected the oversized body to be refused");
        } catch (HubRejection rejection) {
            Assert.assertEquals(413, rejection.status);
            Assert.assertTrue(rejection.finalRefusal);
        }
        assertClosed(carrier);
    }

    /**
     * A body that cannot be read at the moment, here a file opened for writing only, is not refused for
     * good: reading it fails like a busy hub would, so the app's SDK keeps the request. The descriptor is
     * closed all the same.
     */
    @Test
    public void request_bodyThatCannotBeReadNow_isNotRefusedForGood() throws Exception {
        File file = new File(cacheDir, "hub-bundles-write-only.body");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes(100 * 1024));
        }
        ParcelFileDescriptor writeOnly = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            Bundle bundle = HubBundles.requestToBundle(post(null));
            bundle.putParcelable(HubProtocol.KEY_BODY_FILE, writeOnly);

            try {
                HubBundles.requestFromBundle(bundle, MB);
                Assert.fail("expected the body read to fail");
            } catch (HubRejection rejection) {
                Assert.fail("a body that cannot be read now was refused for good: " + rejection.getMessage());
            } catch (IOException expected) {
                Assert.assertNotNull(expected);
            }
            assertClosed(writeOnly);
        } finally {
            writeOnly.close();
            Assert.assertTrue(file.delete());
        }
    }

    /**
     * A request turned away before its body is read has its carrier closed without reading it.
     */
    @Test
    public void closeBody_closesTheCarrierWithoutReading() throws Exception {
        byte[] body = bytes(HubProtocol.INLINE_BODY_LIMIT + 1);
        Bundle bundle = HubBundles.requestToBundle(post(body));
        HubBundles.putBody(bundle, body, cacheDir);
        Object carrier = carrier(bundle);

        Assert.assertTrue(HubBundles.hasOutOfLineBody(bundle));
        HubBundles.closeBody(bundle);

        assertClosed(carrier);
        Bundle inline = HubBundles.requestToBundle(post(new byte[10]));
        HubBundles.putBody(inline, new byte[10], cacheDir);
        Assert.assertFalse(HubBundles.hasOutOfLineBody(inline));
    }

    /**
     * An inline body above what a hub accepts inline is refused, whatever the body limit.
     */
    @Test
    public void request_oversizedInlineBody_isRefused() throws Exception {
        Bundle bundle = HubBundles.requestToBundle(post(null));
        bundle.putByteArray(HubProtocol.KEY_BODY, bytes(HubProtocol.MAX_INLINE_BODY_ACCEPTED + 1));

        try {
            HubBundles.requestFromBundle(bundle, 8 * MB);
            Assert.fail("expected the oversized inline body to be refused");
        } catch (HubRejection rejection) {
            Assert.assertEquals(413, rejection.status);
        }
    }

    /**
     * A body handed over as a pipe is refused right away instead of being read: a pipe has no size,
     * and a sender could keep it open to stall the hub.
     */
    @Test
    public void request_pipeAsBody_isRefusedWithoutBlocking() throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        try {
            Bundle bundle = HubBundles.requestToBundle(post(null));
            bundle.putParcelable(HubProtocol.KEY_BODY_FILE, pipe[0]);
            long before = System.currentTimeMillis();
            try {
                HubBundles.requestFromBundle(bundle, MB);
                Assert.fail("expected a pipe to be refused");
            } catch (HubRejection rejection) {
                Assert.assertEquals(400, rejection.status);
                Assert.assertTrue(rejection.finalRefusal);
                Assert.assertTrue(System.currentTimeMillis() - before < 1_000);
            }
            assertClosed(pipe[0]);
        } finally {
            pipe[1].close();
        }
    }

    /**
     * A bundle that is not a request at all is refused for good. A request in a reply mode only a newer
     * hub knows is refused, but not for good, so the app's SDK keeps it until the host app is updated.
     */
    @Test
    public void request_malformedOrNewerThanTheHub_isRefused() throws Exception {
        Bundle noVersion = HubBundles.requestToBundle(post(null));
        noVersion.putInt(HubProtocol.KEY_VERSION, 0);
        Bundle noMethod = HubBundles.requestToBundle(post(null));
        noMethod.remove(HubProtocol.KEY_METHOD);
        Bundle otherMode = HubBundles.requestToBundle(post(null));
        otherMode.putInt(HubProtocol.KEY_REPLY_WHEN, 2);

        for (Bundle bundle : new Bundle[] { noVersion, noMethod }) {
            try {
                HubBundles.requestFromBundle(bundle, MB);
                Assert.fail("expected the bundle to be refused");
            } catch (HubRejection rejection) {
                Assert.assertEquals(400, rejection.status);
                Assert.assertTrue(rejection.finalRefusal);
            }
        }
        try {
            HubBundles.requestFromBundle(otherMode, MB);
            Assert.fail("expected the unknown reply mode to be refused");
        } catch (HubRejection rejection) {
            Assert.assertEquals(501, rejection.status);
            Assert.assertFalse(rejection.finalRefusal);
        }
    }

    /**
     * Header names that differ only in case are collapsed to the last one, as the server reads them.
     */
    @Test
    public void request_headersDifferingInCase_areCollapsed() throws Exception {
        Bundle bundle = HubBundles.requestToBundle(post(null));
        bundle.putStringArray(HubProtocol.KEY_HEADER_NAMES, new String[] { "Content-Type", "X-A", "content-type", null });
        bundle.putStringArray(HubProtocol.KEY_HEADER_VALUES, new String[] { "a", "1", "b", "ignored" });

        HubRequest read = HubBundles.requestFromBundle(bundle, MB);

        Assert.assertEquals(2, read.getHeaders().size());
        Assert.assertEquals("b", read.getHeader("Content-Type"));
        Assert.assertEquals("1", read.getHeader("X-A"));
    }

    /**
     * A large response travels outside the bundle and comes out byte for byte with its status and headers.
     */
    @Test
    public void response_largeBodyRoundTrip() throws Exception {
        byte[] body = bytes(300 * 1024);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        HubBundles.Outgoing outgoing = HubBundles.responseToBundle(new HubResponse(200, headers, body), cacheDir);

        HubResponse read = HubBundles.responseFromBundle(throughParcel(outgoing.bundle), MB);
        outgoing.release.close();

        Assert.assertEquals(200, read.getStatus());
        Assert.assertEquals("application/json", read.getHeader("content-type"));
        Assert.assertArrayEquals(body, read.getBody());
    }

    /**
     * A response larger than the app accepts is a failure, and its carrier is closed.
     */
    @Test
    public void response_largerThanTheAppAccepts_fails() throws Exception {
        HubBundles.Outgoing outgoing = HubBundles.responseToBundle(new HubResponse(200, new LinkedHashMap<String, String>(), bytes(300 * 1024)), cacheDir);
        Object carrier = carrier(outgoing.bundle);

        try {
            HubBundles.responseFromBundle(outgoing.bundle, 100 * 1024);
            Assert.fail("expected the oversized response to fail");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("larger than"));
        }
        assertClosed(carrier);
    }

    /**
     * A failure, a reply without a status and no reply at all are failures the SDK retries later.
     */
    @Test
    public void response_failuresAreReported() {
        Bundle[] replies = { HubBundles.failureToBundle("the hub is busy"), new Bundle(), null };
        String[] messages = { "the hub is busy", "without a status", "no reply" };
        for (int i = 0; i < replies.length; i++) {
            try {
                HubBundles.responseFromBundle(replies[i], MB);
                Assert.fail("expected a failure for " + messages[i]);
            } catch (IOException expected) {
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(messages[i]));
            }
        }
    }

    /**
     * An admission carries how long the app should wait for the response; nothing else is an admission.
     */
    @Test
    public void admission_carriesTheWaitingTime() {
        Bundle accepted = throughParcel(HubBundles.acceptedToBundle(130_000));

        Assert.assertTrue(HubBundles.isAccepted(accepted));
        Assert.assertEquals(130_000, HubBundles.replyWithinMillis(accepted));
        Assert.assertFalse(HubBundles.isAccepted(HubBundles.failureToBundle("x")));
        Assert.assertFalse(HubBundles.isAccepted(null));
    }
}
