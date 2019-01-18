package ly.count.sdk.android.internal;

import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;
import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.net.HttpURLConnection;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Future;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.android.Config;
import ly.count.sdk.android.Countly;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.Storage;
import ly.count.sdk.internal.Tasks;

import ly.count.sdk.internal.Transport;
import ly.count.sdk.internal.UserEditorImpl;
import ly.count.sdk.internal.UserImpl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;


@RunWith(AndroidJUnit4.class)
public class TransportTests extends BaseTests {
    @Rule
    public TestName testName = new TestName();

    private static final int PORT = 30301;
    @Test
    public void filler(){

    }

    private Transport network;

    public MockWebServer server;

    @Override
    protected Config defaultConfig() throws Exception {
        Config configC = new Config("http://localhost:" + PORT, APP_KEY).enableTestMode().setLoggingLevel(ConfigCore.LoggingLevel.DEBUG);
        configC.setCustomDeviceId("devid");
        return  configC;
/*
        InternalConfig config = new InternalConfig(new ConfigCore("http://localhost:" + PORT, APP_KEY).enableTestMode().setLoggingLevel(ConfigCore.LoggingLevel.DEBUG));
        config.setDeviceId(new ConfigCore.DID(Config.DeviceIdRealm.DEVICE_ID.getIndex(), Config.DeviceIdStrategy.CUSTOM_ID.getIndex(), "devid"));
        return (Config)((ConfigCore)config);//todo, does this work or will it break things? (AK, 16.01.2019)
        */
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        server = new MockWebServer();
        server.start(PORT);

        setUpService(defaultConfig());

        /*
        //todo, this should be uncommented when CountlyService is fixed. (AK, 16.01.2019)
        service.onCreate();

        Utils.reflectiveSetField(service, "singleProcess", false);
        */
//            Intent intent = new Intent();
//            intent.putExtra(CountlyService.CMD, CountlyService.CMD_DEVICE_ID);
//            service.onStartCommand(intent, 0, 0);

        /*
        //todo, this should be uncommented when CountlyService is fixed. (AK, 16.01.2019)
        network = Whitebox.getInternalState(service, "network");
        Utils.reflectiveSetField(network, "sleeps", false);
        */
    }

    @After
    public void cleanupEveryTests() throws Exception {
        server.shutdown();

        Tasks storageTasks = Utils.reflectiveGetField(Storage.class, "tasks");
        Whitebox.invokeMethod(storageTasks, "await");
    }

    @Test
    public void testOpenConnectionGET() throws Exception {
        String url = "http://try.count.ly/i?",
                params = "a=1&b=2";
        HttpURLConnection connection = new Transport().openConnection(url, params, true);
        Assert.assertEquals("GET", connection.getRequestMethod());
        Assert.assertEquals(new URL(url + params), connection.getURL());
        Assert.assertEquals(false, connection.getDoOutput());
    }

    @Test
    public void testOpenConnectionPOST() throws Exception {
        String url = "http://try.count.ly/i?",
                params = "a=1&b=2";
        HttpURLConnection connection = new Transport().openConnection(url, params, false);
        Assert.assertEquals("POST", connection.getRequestMethod());
        Assert.assertEquals(new URL(url), connection.getURL());
        Assert.assertEquals(true, connection.getDoOutput());
    }

    /*
    //todo, this should be uncommented when CountlyService is fixed. (AK, 16.01.2019)
    @Test
    public void test4XX() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(404));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.REMOVE, result);
    }

    @Test
    public void test400() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(400));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.REMOVE, result);
    }

    @Test
    public void test3XX() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(301));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.RETRY, result);
    }

    @Test
    public void test5XX() throws Exception {
        server.enqueue(new MockResponse().setBody("Baaad").setResponseCode(500));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.RETRY, result);
    }

    @Test
    public void test200Success() throws Exception {
        server.enqueue(new MockResponse().setBody("Success").setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.OK, result);
    }

    @Test
    public void test200NullResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.OK, result);
    }

    @Test
    public void testChecksum() throws Exception {
        server.enqueue(new MockResponse().setBody("Success").setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.OK, result);

        RecordedRequest rr = server.takeRequest();
        String query = rr.getRequestUrl().encodedQuery();
        String[] parts = query.split("&");

        Assert.assertTrue(parts[parts.length - 1].startsWith("checksum256"));
    }

    @Test
    public void testCheckGetHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("a", 1, "b", "something", "c", false);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.OK, result);

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

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.OK, result);

        RecordedRequest rr = server.takeRequest();
        Assert.assertEquals("POST", rr.getMethod());
        Assert.assertEquals("application/x-www-form-urlencoded", rr.getHeader("content-type"));
        Assert.assertEquals(request.params.toString(), rr.getBody().readString(Charset.forName(Utils.UTF8)));
    }

    @Test
    public void testAutoPost() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("a", String.format("%1000.1000s", "x"));

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.OK, result);

        RecordedRequest rr = server.takeRequest();
        Assert.assertEquals("POST", rr.getMethod());
        Assert.assertEquals("application/x-www-form-urlencoded", rr.getHeader("content-type"));
        Assert.assertEquals(request.params.toString(), rr.getBody().readString(Charset.forName(Utils.UTF8)));
    }

    @Test
    public void testMultiPart() throws Exception {
        Utils.reflectiveSetField(Countly.sharedInstance(), "user", new UserImpl(ctx));
        Countly.sharedInstance().user().edit().setPicture("picturebytes".getBytes()).commit();
        server.enqueue(new MockResponse().setResponseCode(200));

        Request request = new Request("k1", "value 1", "k2", 2, "k3", false, UserEditorImpl.PICTURE_PATH, UserEditorImpl.PICTURE_IN_USER_PROFILE);

        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.OK, result);

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
        Future<Transport.RequestResult> future1 = tasks.run(network.send(request1));
        Future<Transport.RequestResult> future2 = tasks.run(network.send(request2));

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
        Assert.assertEquals(Transport.RequestResult.OK, future1.get());
        Thread.sleep(1000);
        Assert.assertEquals(0, Utils.reflectiveGetField(network, "slept"));
        Assert.assertTrue(future1.isDone());
        Assert.assertTrue(future2.isDone());
        Assert.assertEquals(Transport.RequestResult.OK, future1.get());
        Assert.assertEquals(Transport.RequestResult.OK, future2.get());
    }
    */

    @Test(expected = IllegalArgumentException.class)
    public void testPinBadKeyFormat() throws Exception {
        ConfigCore config = defaultConfigWithLogsForConfigTests().addPublicKeyPin("aaa");
        new Transport().init(new InternalConfig(config));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPinBadCertFormat() throws Exception {
        ConfigCore config = defaultConfigWithLogsForConfigTests().addCertificatePin("aaa");
        new Transport().init(new InternalConfig(config));
    }

    private String validPubKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtqD2fFglxAbZsU6EUN5M\n" +
            "9NEXFm+d4AQU+a0k7lem700l+kvtmzes+bAWhN2C54uJ7BBgIb3j6imtJd0jYIJa\n" +
            "cNLAJWPmKkJ2rVr9VbeXVXV2/5o3NwQ4Kny70mlU2JhRwIRFD6BYuFjS1o/K7I6d\n" +
            "qEWvMd9Enm57SfsKVi54bNZ1gAMXwT3Ecy0aN4JPnrQ6C5PZTkxDxVRziEsbe9Ob\n" +
            "91Kv1MPY3YhNzlNfFHlR8f8qoMNIlbOww2YRfa/Vb3V5rtkAT/TrLHh4ZvUHmzwM\n" +
            "kKAQLvLWCAh6IbYBXNWZnRPdmv77z5TPS8fG4SnwoBDyvH3kRos7woIpKMewH3bx\n" +
            "jQIDAQAB\n" +
            "-----END PUBLIC KEY-----\n";

    private String validCertPem = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFRTCCBC2gAwIBAgIQMsrEzoWq+R8Ml4pXRnu/xjANBgkqhkiG9w0BAQsFADCB\n" +
            "kDELMAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4G\n" +
            "A1UEBxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxNjA0BgNV\n" +
            "BAMTLUNPTU9ETyBSU0EgRG9tYWluIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZlciBD\n" +
            "QTAeFw0xNzA2MjgwMDAwMDBaFw0yMDA3MDUyMzU5NTlaMFcxITAfBgNVBAsTGERv\n" +
            "bWFpbiBDb250cm9sIFZhbGlkYXRlZDEdMBsGA1UECxMUUG9zaXRpdmVTU0wgV2ls\n" +
            "ZGNhcmQxEzARBgNVBAMMCiouY291bnQubHkwggEiMA0GCSqGSIb3DQEBAQUAA4IB\n" +
            "DwAwggEKAoIBAQC2oPZ8WCXEBtmxToRQ3kz00RcWb53gBBT5rSTuV6bvTSX6S+2b\n" +
            "N6z5sBaE3YLni4nsEGAhvePqKa0l3SNgglpw0sAlY+YqQnatWv1Vt5dVdXb/mjc3\n" +
            "BDgqfLvSaVTYmFHAhEUPoFi4WNLWj8rsjp2oRa8x30SebntJ+wpWLnhs1nWAAxfB\n" +
            "PcRzLRo3gk+etDoLk9lOTEPFVHOISxt705v3Uq/Uw9jdiE3OU18UeVHx/yqgw0iV\n" +
            "s7DDZhF9r9VvdXmu2QBP9OsseHhm9QebPAyQoBAu8tYICHohtgFc1ZmdE92a/vvP\n" +
            "lM9Lx8bhKfCgEPK8feRGizvCgikox7AfdvGNAgMBAAGjggHRMIIBzTAfBgNVHSME\n" +
            "GDAWgBSQr2o6lFoL2JDqElZz30O0Oija5zAdBgNVHQ4EFgQUq1hZiEczrfVhD7H6\n" +
            "Z08oLnfI6fowDgYDVR0PAQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYw\n" +
            "FAYIKwYBBQUHAwEGCCsGAQUFBwMCME8GA1UdIARIMEYwOgYLKwYBBAGyMQECAgcw\n" +
            "KzApBggrBgEFBQcCARYdaHR0cHM6Ly9zZWN1cmUuY29tb2RvLmNvbS9DUFMwCAYG\n" +
            "Z4EMAQIBMFQGA1UdHwRNMEswSaBHoEWGQ2h0dHA6Ly9jcmwuY29tb2RvY2EuY29t\n" +
            "L0NPTU9ET1JTQURvbWFpblZhbGlkYXRpb25TZWN1cmVTZXJ2ZXJDQS5jcmwwgYUG\n" +
            "CCsGAQUFBwEBBHkwdzBPBggrBgEFBQcwAoZDaHR0cDovL2NydC5jb21vZG9jYS5j\n" +
            "b20vQ09NT0RPUlNBRG9tYWluVmFsaWRhdGlvblNlY3VyZVNlcnZlckNBLmNydDAk\n" +
            "BggrBgEFBQcwAYYYaHR0cDovL29jc3AuY29tb2RvY2EuY29tMB8GA1UdEQQYMBaC\n" +
            "CiouY291bnQubHmCCGNvdW50Lmx5MA0GCSqGSIb3DQEBCwUAA4IBAQAVQSO9igSK\n" +
            "xEwLOnha6TfcFiByVldNnMNs6nLiXs01hb6dvOZ+0VR1oO/RfVEoso/yqBgOV/yO\n" +
            "D5bJmHc49RiKRPTFxJuCxRoffbNMyUDBXCXdmnSSocmBNhS9FDBAEVMfLljanoyp\n" +
            "KKRNPboEplYVIzA4LCjzL10ZK4DNPBbXRq+hS27RIZTnK8KyQAm0d9mB7YWKgVqM\n" +
            "164zCcAQ5h0ALD4oYr+b4h9Jx3XWVd76cYRK2n/1je3M0UD+d+omqKMwtijuEqRv\n" +
            "vs8EkylLcGMtqjr9es0JQjrDqz0g64XE/ntdiDdVVr5wAuoTvotbfHlvN2UnVUAB\n" +
            "mVibCgJcyzGs\n" +
            "-----END CERTIFICATE-----\n";

    private String selfSignedCert = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFXTCCA0WgAwIBAgIJAPWIE8uVwgBOMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQwHhcNMTcxMTIyMDgwOTA5WhcNMTgxMTIyMDgwOTA5WjBF\n" +
            "MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n" +
            "ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIIC\n" +
            "CgKCAgEA20erkJT4kwFy9gHJ4j08jIMjxQjAzStpdKQfM9lagJwzaL3AOkrGiiG8\n" +
            "Xt5QXvvmAbA43b5sAGWGk3YCKVgaD5PS5ylboyNy7bnjWBcLwcBQxFD7y8Jobm+f\n" +
            "DxdbAgYhVfTgl1QQmLaGCNlDrcjjRxFX1li/UM7uAJvcU9HPDKZ4ZAuQ0z/IIE6t\n" +
            "Nsg+41zQnaEHLyDDF4HaiOuuN+K1jKJdBQfFgc7oW2OUPLMbc8O/e/DyBLnBSBbq\n" +
            "vOqr8nLV++hX1UFrmp1ee63CCt55xjiYEoGN6+Hgd6tmWhEwR0PJw2ND4ZZUGpjY\n" +
            "WqPOST2UphadRIc7NRYHsYmWbOGN80TPqvuCTIHkoEun78DHw4JrPTeYa7FS0/1/\n" +
            "RZ+yoa0MnNr5H74UW2cWZn88Af/lJQ4rjCdjK7CAapO3LieV4Hu+cGxMCtc7T/98\n" +
            "ZCML5758JGHadxl3llVVHtxq8ZBponYsr2bycOu9C2ksC/G69jCRzwvD4IIFc1aT\n" +
            "h4TyprnDD4xKoN3QFCsmYWR8soA07ewazFmn+ORX2BV3WsrmOTjHDki02iinceP/\n" +
            "BXB3sQgKnvoFYMoHzDN0r0QsEy+QJDS7XVOA4P9oVxU5LrgDYuGPXlS1nFfOr8V+\n" +
            "msarqzspcWcH0pVomru3h8AhDCTj1xkMoWH77te6J9NmabyEQv8CAwEAAaNQME4w\n" +
            "HQYDVR0OBBYEFOm/v5nMdI1kgwSDvifMu7jtGHnQMB8GA1UdIwQYMBaAFOm/v5nM\n" +
            "dI1kgwSDvifMu7jtGHnQMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIB\n" +
            "AAhDGyc28JYLDDJ6ERaTWk9mdl700OdYOllTRZS51qKe9nZOrtFKKMY5GT4BodER\n" +
            "h/oPrxVg9VQoO1DegLv9fJ5T5fFaVd34XFugCQaHRxdjB23AAPqTxa8cUgvq8b5s\n" +
            "JM7g5dzR5tTVzOI3qFY1sz9jLXeL39EJeLMO3yDQY7sJfot8vwPFW7caL9U2mJ3z\n" +
            "jHQlmD6bGUjM1ePY4itmHo2dn8mpOuNWS57D+Lgu70wHhMVDY1zAsxE/QgEZVBx0\n" +
            "scYHbxXOZTv6qU1DnyW6Sa29XM/UgBBSQJy7ByW/84TbK2IHDHfwToHM3/vWgswg\n" +
            "c0C5rZRycbiI7HZNO3lKPe4w9DfJ+5YO6nMQPIMSoK9dJtUYmmRdLXkYVDxazYxl\n" +
            "L6qjjvqhdVHclSq4qC9c1iRzFCLLnihyWX02YijC71SMdAj7JP2wIBut4mHFd21A\n" +
            "DBSLKyFcwNtaorzbngXwBsORB8dJkI/DeOBhIO1S4NU6Q2g3QP98C4tVlSbQrxx7\n" +
            "nONxgTEZ/NMGawQO/NKsrfIRUJQFhBV/q43JNi0vCaFGs4Hae6d3S4tF56wQwb/s\n" +
            "ayaHG6B3w43H2OWRZrCld2W4QyP9cA1Tcu+Vubt2mkNAYxy0Ctfmkg+D5Vjr1rEX\n" +
            "AIOamakpmH1mPSKexvXt+v65nqSLgntkMs5UXOLpGhX4\n" +
            "-----END CERTIFICATE-----\n";

    private String selfSignedKey = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8SekjOfo8JRBh/qEiCwN\n" +
            "D5nkMi+/nHBRMS5ZDgHWDh9CN7YitO4qeqewOjZv1jcNE1YQa8hm4tAxbZAGYsnX\n" +
            "PMJEuSdMOVMqrml4gpnDYoRqZpbb5h9ovveJfR/zwnN/aKP1tvAWshMAhJDVT8mq\n" +
            "8kyMkwkM+yjYsAboGTsn7BTKBw601vtVu1zQg2t/ajAPpb2DPZG/JlScgSvua6jY\n" +
            "SKJ/wbRVuV0pFJTNg8pepqxtbgR3Yw6+BlZat9OBJYXmGQW1sIhDi0Ifzis4lnRZ\n" +
            "qzKbpkNwtlRWz3nHsWOvbQBJoVo9M1Fs3o4SRLCosZ7DGrPvMiZ27l0pTlrXhBj6\n" +
            "XwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    private void testParsing(boolean isKey, String value) throws Exception {
        ConfigCore config = defaultConfigWithLogsForConfigTests();
        if (isKey) config.addPublicKeyPin(value);
        else config.addCertificatePin(value);
        Transport network = new Transport();
        network.init(new InternalConfig(config));
        Assert.assertEquals(1, ((List)Utils.reflectiveGetField(network, isKey ? "keyPins" : "certPins")).size());

        config = defaultConfigWithLogsForConfigTests();
        if (isKey) config.addPublicKeyPin(value.replaceAll("\n", ""));
        else config.addCertificatePin(value.replaceAll("\n", ""));

        network = new Transport();
        network.init(new InternalConfig(config));
        Assert.assertEquals(1, ((List)Utils.reflectiveGetField(network, isKey ? "keyPins" : "certPins")).size());
    }

    @Test
    public void testPinKeyPem() throws Exception {
        testParsing(true, validPubKeyPem);
    }

    @Test
    public void testPinKeyFromCertPem() throws Exception {
        testParsing(true, validCertPem);
    }

    @Test
    public void testPinCertPem() throws Exception {
        testParsing(false, validCertPem);
    }

    @Test
    public void testPinSSKeyPem() throws Exception {
        testParsing(true, selfSignedKey);
    }

    @Test
    public void testPinSSKeyFromCertPem() throws Exception {
        testParsing(true, selfSignedCert);
    }

    @Test
    public void testPinSSCertPem() throws Exception {
        testParsing(false, selfSignedCert);
    }

    @Test
    public void testPinSSKeyFilePem() throws Exception {
        testParsing(true, "public_key.pem");
    }

    @Test
    public void testPinSSKeyFromCertFilePem() throws Exception {
        testParsing(true, "cert.pem");
    }

    @Test
    public void testPinSSCertFilePem() throws Exception {
        testParsing(false, "cert.pem");
    }

    @Test
    public void testPinSSKeyFileDer() throws Exception {
        testParsing(true, "public_key.der");
    }

    @Test
    public void testPinSSKeyFromCertFileDer() throws Exception {
        testParsing(true, "cert.der");
    }

    @Test
    public void testPinSSCertFileDer() throws Exception {
        testParsing(false, "cert.der");
    }

    /*
    //todo to fix these tests, CountlyService needs to be fixed/finished. (AK, 16.01.2019)
    private void setUpPinning(String[] keys, String[] certs) throws Exception {
        Config cfg = new Config("https://count.ly", "111")
                .setLoggingLevel(ConfigCore.LoggingLevel.DEBUG)
                .enableTestMode()
                .setCustomDeviceId("did");

        if (keys != null) {
            for (String key : keys) {
                cfg.addPublicKeyPin(key);
            }
        }

        if (certs != null) {
            for (String cert : certs) {
                cfg.addCertificatePin(cert);
            }
        }

        config = new InternalConfig(cfg);
        config.setDeviceId(new ConfigCore.DID(Config.DeviceIdRealm.DEVICE_ID.getIndex(), Config.DeviceIdStrategy.CUSTOM_ID.getIndex(), "did"));
        Storage.push(ctx, config);

        setUpService(config);

        service.onCreate();

        Utils.reflectiveSetField(service, "singleProcess", false);
        Intent intent = new Intent();
        intent.putExtra(CountlyService.CMD, CountlyService.CMD_DEVICE_ID);
        service.onStartCommand(intent, 0, 0);

        network = Whitebox.getInternalState(service, "network");
        Utils.reflectiveSetField(network, "sleeps", false);
    }

    @Test
    public void testPinConnectKeyPem() throws Exception {
        setUpPinning(new String[]{validPubKeyPem}, null);

        Request request = new Request("a", 1, "b", "something", "c", false);
        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.REMOVE, result);
    }

    @Test
    public void testPinConnectKeyInvalidPem() throws Exception {
        setUpPinning(new String[]{selfSignedKey}, null);

        Request request = new Request("a", 1, "b", "something", "c", false);
        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.RETRY, result);
    }

    @Test
    public void testPinConnectCertPem() throws Exception {
        setUpPinning(null, new String[]{validCertPem});

        Request request = new Request("a", 1, "b", "something", "c", false);
        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.REMOVE, result);
    }

    @Test
    public void testPinConnectCertInvalidPem() throws Exception {
        setUpPinning(null, new String[]{selfSignedCert});

        Request request = new Request("a", 1, "b", "something", "c", false);
        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.RETRY, result);
    }

    @Test
    public void testPinConnectKeyValidInvalidPem() throws Exception {
        setUpPinning(new String[]{selfSignedKey, validPubKeyPem}, null);

        Request request = new Request("a", 1, "b", "something", "c", false);
        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.REMOVE, result);
    }

    @Test
    public void testPinConnectCertValidInvalidPem() throws Exception {
        setUpPinning(null, new String[]{selfSignedCert, validCertPem});

        Request request = new Request("a", 1, "b", "something", "c", false);
        Transport.RequestResult result = network.send(request).call();
        Assert.assertEquals(Transport.RequestResult.REMOVE, result);
    }
    */
}