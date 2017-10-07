package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import ly.count.android.sdk.Config;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(AndroidJUnit4.class)
public class NetworkTests {
    //todo test forced post
    final int paramsAddedByAddCommon = 6;

    //final private String url = "https://www.google.com";
    //final private String apiKey = "1234";

    //final private String url = "http://kadikis.count.ly";
    final private String apiKey = "0e3175bd1db444602076c11000b2f70a415386dc";

    private Config config;
    private InternalConfig internalConfig;

    MockWebServer server;
    String currentURL;

    @Before
    public void setupEveryTest() throws IOException {
        server = new MockWebServer();
        server.start();
        currentURL = server.url("/").toString();

        config = new Config(currentURL, apiKey);
        internalConfig = new InternalConfig(config);
    }

    @After
    public void cleanupEveryTests(){
        currentURL = null;
    }
/*
    @Test
    public void createSimple() throws IOException {
        server.enqueue(new MockResponse().setBody("hello, world!").setResponseCode(431));

        java.net.URL url = new java.net.URL(currentURL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String eventData = "dssd";

        try {
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(eventData);
            writer.flush();
            writer.close();
            os.close();

            conn.connect();
            String resp = conn.getResponseMessage();

            android.util.Log.e(TestingUtilityInternal.LOG_TAG, resp);
            android.util.Log.e(TestingUtilityInternal.LOG_TAG, "" + conn.getResponseCode());
        } finally {
            conn.disconnect();
        }

        Network network = new Network();
        network.init(internalConfig);
    }*/

    //@Test
    public void createSimple2() throws IOException {
        server.enqueue(new MockResponse().setBody("hello, world!"));

        //java.net.URL url = new java.net.URL(currentURL);
        java.net.URL url = new java.net.URL("http://www.google.com");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String eventData = "dssd";

        try {
            conn.setDoOutput(false);
            conn.setRequestMethod("POST");
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(eventData);
            writer.flush();
            writer.close();
            os.close();

            conn.connect();
            String resp = conn.getResponseMessage();

            android.util.Log.e(TestingUtilityInternal.LOG_TAG, resp);
            android.util.Log.e(TestingUtilityInternal.LOG_TAG, "" + conn.getResponseCode());

            BufferedReader br;
            if (200 <= conn.getResponseCode() && conn.getResponseCode() <= 299) {
                br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            } else {
                br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
            }

            StringBuilder total = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                total.append(line).append('\n');
            }

            android.util.Log.e(TestingUtilityInternal.LOG_TAG, "Android http response: " + total.toString());

        } finally {
            conn.disconnect();
        }

        Network network = new Network();
        network.init(internalConfig);
    }


    //@Test
    public void createSimple3() throws IOException, ExecutionException, InterruptedException, Exception {
        server.enqueue(new MockResponse().setBody("hello, world!"));

        Network network = new Network();
        network.init(internalConfig);

        Request rr = new Request(123);

        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "AA");
        Future<Network.NetworkResponse> fut = network.send(rr);
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "BB, " + fut.isDone() + " " + fut.isCancelled());
        //fut.cancel(true);
        //android.util.Log.d("CountlyTests", "BB2 , " + fut.isDone() + " " + fut.isCancelled());
        Network.NetworkResponse nr = fut.get();
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "CC, " + nr.toString());
    }

    @Test
    public void sampleRequestStartSession() throws IOException, ExecutionException, InterruptedException, Exception {
        server.enqueue(new MockResponse().setBody("hello, world!"));

        Network network = new Network();
        network.init(internalConfig);

        String[] params = new String[] {"app_key", "0698b21707df83ee5accd5ff44584e2a35efa861", "timestamp", "1507252971803", "hour", "4", "dow", "5", "tz", "180", "sdk_version", "17.09.1", "sdk_name", "java-native-android", "begin_session", "1", "metrics", "%7B%22_device%22%3A%22Nexus+5X%22%2C%22_os%22%3A%22Android%22%2C%22_os_version%22%3A%227.1.2%22%2C%22_carrier%22%3A%22LMT%22%2C%22_resolution%22%3A%221080x1794%22%2C%22_density%22%3A%22XXHDPI%22%2C%22_locale%22%3A%22en_US%22%2C%22_app_version%22%3A%221.0%22%7D"};
        Request request = new Request(params);

        String targetURL = "http://kadikis.count.ly/i?app_key=0698b21707df83ee5accd5ff44584e2a35efa861&timestamp=1507252971803&hour=4&dow=5&tz=180&sdk_version=17.09.1&sdk_name=java-native-android&begin_session=1&metrics=%7B%22_device%22%3A%22Nexus+5X%22%2C%22_os%22%3A%22Android%22%2C%22_os_version%22%3A%227.1.2%22%2C%22_carrier%22%3A%22LMT%22%2C%22_resolution%22%3A%221080x1794%22%2C%22_density%22%3A%22XXHDPI%22%2C%22_locale%22%3A%22en_US%22%2C%22_app_version%22%3A%221.0%22%7D&device_id=New Device ID&sdk_version=16.12.2&sdk_name=java-native-android&checksum=46f9aeebc12e4dbf9dd49a2c3b30bf8043482ec0";
        String targetRequest = "/i?app_key=0698b21707df83ee5accd5ff44584e2a35efa861&timestamp=1507252971803&hour=4&dow=5&tz=180&sdk_version=17.09.1&sdk_name=java-native-android&begin_session=1&metrics=%7B%22_device%22%3A%22Nexus+5X%22%2C%22_os%22%3A%22Android%22%2C%22_os_version%22%3A%227.1.2%22%2C%22_carrier%22%3A%22LMT%22%2C%22_resolution%22%3A%221080x1794%22%2C%22_density%22%3A%22XXHDPI%22%2C%22_locale%22%3A%22en_US%22%2C%22_app_version%22%3A%221.0%22%7D&device_id=New Device ID&sdk_version=16.12.2&sdk_name=java-native-android&checksum=46f9aeebc12e4dbf9dd49a2c3b30bf8043482ec0";

        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "AA");
        Future<Network.NetworkResponse> fut = network.send(request);
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "BB, " + fut.isDone() + " " + fut.isCancelled());
        //fut.cancel(true);
        //android.util.Log.d("CountlyTests", "BB2 , " + fut.isDone() + " " + fut.isCancelled());
        Network.NetworkResponse nr = fut.get();
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "CC, " + nr.toString());


        RecordedRequest rr = server.takeRequest();

        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "CC, path " + rr.getPath());
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "CC, mehod" + rr.getMethod());
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "CC, req line" + rr.getRequestLine());
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "CC, req url" + rr.getRequestUrl());



        //Assert.assertEquals(targetRequest, rr.getPath());
    }
}
