package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.bouncycastle.ocsp.Req;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.User;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class NetworkTests extends BaseTests {
    private static final int PORT = 30301;

    private Network network;

    public MockWebServer server;

    @Override
    protected Config defaultConfig() throws MalformedURLException {
        InternalConfig config = new InternalConfig(new Config("http://localhost:" + PORT, APP_KEY).enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG));
        config.setDeviceId(new Config.DID(Config.DeviceIdRealm.DEVICE_ID, Config.DeviceIdStrategy.CUSTOM_ID, "devid"));
        return config;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        server = new MockWebServer();
        server.start(PORT);

        setUpService(defaultConfig());
        service.onCreate();
        network = Whitebox.getInternalState(service, "network");
        Utils.reflectiveSetField(network, "sleeps", false);
    }

    @After
    public void cleanupEveryTests() throws IOException {
        server.shutdown();
    }

    @Test
    public void testOpenConnectionGET() throws Exception {
        String url = "http://try.count.ly/i?",
                params = "a=1&b=2";
        HttpURLConnection connection = new Network().openConnection(url, params, true);
        Assert.assertEquals("GET", connection.getRequestMethod());
        Assert.assertEquals(new URL(url + params), connection.getURL());
        Assert.assertEquals(false, connection.getDoOutput());
    }

    @Test
    public void testOpenConnectionPOST() throws Exception {
        String url = "http://try.count.ly/i?",
                params = "a=1&b=2";
        HttpURLConnection connection = new Network().openConnection(url, params, false);
        Assert.assertEquals("POST", connection.getRequestMethod());
        Assert.assertEquals(new URL(url), connection.getURL());
        Assert.assertEquals(true, connection.getDoOutput());
    }

    @Test
    public void test4XX() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(404));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.RETRY, result);
    }

    @Test
    public void test400() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(400));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.REMOVE, result);
    }

    @Test
    public void test3XX() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(301));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.RETRY, result);
    }

    @Test
    public void test5XX() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(500));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.RETRY, result);
    }

    @Test
    public void test200Success() throws Exception {
        server.enqueue(new MockResponse().setBody("Success").setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.OK, result);
    }

    @Test
    public void test200NullResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.OK, result);
    }

//    @Test
//    public void testChecksum() throws Exception {
//        server.enqueue(new MockResponse().setBody("Success").setResponseCode(200));
//
//        Request request = new Request("a", 1, "b", "something", "c", false);
//
//        Network.RequestResult result = network.send(request).call();
//        Assert.assertEquals(Network.RequestResult.OK, result);
//
//        RecordedRequest rr = server.takeRequest();
//        String query = rr.getRequestUrl().encodedQuery();
//        String[] parts = query.split("&");
//
//        Assert.assertTrue(parts[parts.length - 1].startsWith("checksum256"));
//    }

    @Test
    public void testCheckGetHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.OK, result);

        RecordedRequest rr = server.takeRequest();
        Assert.assertEquals("GET", rr.getMethod());
        Assert.assertEquals(null, rr.getHeader("content-type"));
        Assert.assertEquals(0, rr.getBody().size());
    }

    @Test
    public void testUsePost() throws Exception {
        config.setUsePOST(true);
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.OK, result);

        RecordedRequest rr = server.takeRequest();
        Assert.assertEquals("POST", rr.getMethod());
        Assert.assertEquals("application/x-www-form-urlencoded", rr.getHeader("content-type"));
        Assert.assertEquals(request.params.toString(), rr.getBody().readString(Charset.forName(Utils.UTF8)));
    }

    @Test
    public void testAutoPost() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("a", String.format("%1000.1000s", "x"));

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.OK, result);

        RecordedRequest rr = server.takeRequest();
        Assert.assertEquals("POST", rr.getMethod());
        Assert.assertEquals("application/x-www-form-urlencoded", rr.getHeader("content-type"));
        Assert.assertEquals(request.params.toString(), rr.getBody().readString(Charset.forName(Utils.UTF8)));
    }

    @Test
    public void testMultiPart() throws Exception {
        Core.instance.user().edit().setPicture("picturebytes".getBytes()).commit();
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("k1", "value 1", "k2", 2, "k3", false, UserEditorImpl.PICTURE_PATH, UserEditorImpl.PICTURE_IN_USER_PROFILE);

        Network.RequestResult result = network.send(request).call();
        Assert.assertEquals(Network.RequestResult.OK, result);

        RecordedRequest rr = server.takeRequest();
        Assert.assertEquals("POST", rr.getMethod());
        Assert.assertTrue(rr.getHeader("content-type").startsWith("multipart/form-data"));

        String body = rr.getBody().readString(Charset.forName(Utils.UTF8));
        Assert.assertTrue(body.contains("Content-Disposition: form-data; name=\"profilePicture\"; filename=\"image\""));
        Assert.assertTrue(body.contains("Content-Disposition: form-data; name=\"k1\""));
        Assert.assertTrue(body.contains("Content-Disposition: form-data; name=\"k2\""));
        Assert.assertTrue(body.contains("Content-Disposition: form-data; name=\"k3\""));
        Assert.assertTrue(body.contains("Content-Disposition: form-data; name=\"app_key\""));
        Assert.assertTrue(body.contains("Content-Disposition: form-data; name=\"sdk_name\""));
        Assert.assertTrue(body.contains("Content-Disposition: form-data; name=\"sdk_version\""));
    }

    @Test
    public void testBackoff() throws Exception {
        Utils.reflectiveSetField(network, "sleeps", true);

        server.enqueue(new MockResponse().setBody("error 1").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("error 2").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("error 3").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("error 4").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("Success 1").setResponseCode(200));
        server.enqueue(new MockResponse().setBody("error 5").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("Success 2").setResponseCode(200));

        Request request1 = new Request("a", 1, "b", "something", "c", false);
        Request request2 = new Request("a", 2, "b", "something2", "c", true);

        Tasks tasks = new Tasks("tmp");
        Future<Network.RequestResult> future1 = tasks.run(network.send(request1));
        Future<Network.RequestResult> future2 = tasks.run(network.send(request2));

        Thread.sleep(1200);
        Assert.assertEquals(1, Utils.reflectiveGetField(network, "slept"));
        Assert.assertFalse(future1.isDone());
        Assert.assertFalse(future2.isDone());

        Thread.sleep(1000);
        Assert.assertEquals(2, Utils.reflectiveGetField(network, "slept"));
        Assert.assertFalse(future1.isDone());
        Assert.assertFalse(future2.isDone());

        Thread.sleep(2000);
        Assert.assertEquals(3, Utils.reflectiveGetField(network, "slept"));
        Assert.assertFalse(future1.isDone());
        Assert.assertFalse(future2.isDone());
        Thread.sleep(1000);
        Assert.assertEquals(3, Utils.reflectiveGetField(network, "slept"));
        Assert.assertFalse(future1.isDone());
        Assert.assertFalse(future2.isDone());
        Thread.sleep(1000);
        Assert.assertEquals(3, Utils.reflectiveGetField(network, "slept"));
        Assert.assertFalse(future1.isDone());
        Assert.assertFalse(future2.isDone());
        Thread.sleep(1000);
        Assert.assertTrue(future1.isDone());
        Assert.assertFalse(future2.isDone());
        Assert.assertEquals(0, Utils.reflectiveGetField(network, "slept"));
        Assert.assertEquals(Network.RequestResult.OK, future1.get());
        Thread.sleep(1000);
        Assert.assertEquals(0, Utils.reflectiveGetField(network, "slept"));
        Assert.assertTrue(future1.isDone());
        Assert.assertTrue(future2.isDone());
        Assert.assertEquals(Network.RequestResult.OK, future1.get());
        Assert.assertEquals(Network.RequestResult.OK, future2.get());
    }
}