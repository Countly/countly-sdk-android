package ly.count.android.sdk.internal;

import android.os.Debug;
import android.support.test.runner.AndroidJUnit4;
import android.util.*;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.HttpsURLConnection;

import ly.count.android.sdk.Config;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

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
        android.util.Log.d(TestingUtilityInternal.LOG_TAG, "CC, " + nr.GetInternalState());
    }
}
