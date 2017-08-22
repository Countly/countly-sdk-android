package ly.count.android.demo.messaging;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.messaging.CountlyMessaging;
import ly.count.android.sdk.messaging.Message;

public class MainActivity extends Activity {

    private BroadcastReceiver messageReceiver;

    /** Called when the activity is first created. */
    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
    final String COUNTLY_SERVER_URL = "https://try.count.ly";
    final String COUNTLY_APP_KEY = "acddbc269d62a00ee672a0564da526816dcab43b";
    final String COUNTLY_MESSAGING_PROJECT_ID = "YOUR_PROJECT_ID(NUMBERS ONLY)";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
        Countly.sharedInstance()
                .init(this, "YOUR_SERVER", "YOUR_APP_KEY")
                .initMessaging(this, MainActivity.class, "YOUR_PROJECT_ID(NUMBERS ONLY)", Countly.CountlyMessagingMode.TEST);
//        Countly.enableCertificatePinning(Arrays.asList(
//                "MIIFRjCCBC6gAwIBAgIRAL1eUF/3VJlBKT/L++iaiYgwDQYJKoZIhvcNAQELBQAw\n" +
//                        "gZAxCzAJBgNVBAYTAkdCMRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAO\n" +
//                        "BgNVBAcTB1NhbGZvcmQxGjAYBgNVBAoTEUNPTU9ETyBDQSBMaW1pdGVkMTYwNAYD\n" +
//                        "VQQDEy1DT01PRE8gUlNBIERvbWFpbiBWYWxpZGF0aW9uIFNlY3VyZSBTZXJ2ZXIg\n" +
//                        "Q0EwHhcNMTYwNTA5MDAwMDAwWhcNMTcwNzA2MjM1OTU5WjBXMSEwHwYDVQQLExhE\n" +
//                        "b21haW4gQ29udHJvbCBWYWxpZGF0ZWQxHTAbBgNVBAsTFFBvc2l0aXZlU1NMIFdp\n" +
//                        "bGRjYXJkMRMwEQYDVQQDDAoqLmNvdW50Lmx5MIIBIjANBgkqhkiG9w0BAQEFAAOC\n" +
//                        "AQ8AMIIBCgKCAQEA/fSmao1czr7pr8kCdadfy1wrSs+FL3d5AaS0s/TQShVuYIPf\n" +
//                        "SCJ9zOvbI11Qx0LTMZi24iuUJ2F+iizUKAEsdUU5CPC0EH5++TyOLN31L6fku01C\n" +
//                        "GFc0gb2SJnrpL4SXnZKTDz5XZ5u15FYPxdq+J4X3K+DGndaGlj2cb7ccjae0NYE8\n" +
//                        "Z2xI0IIFLvcOnBZdLwJH2vEj9x3ExBb7ei5bekWT9OfGtRC6Scd0roz4KZbv7lCH\n" +
//                        "+hCGvsfbgmDEc3TdLJR0bHAJCxDuRdTF2TcrlFx61w1XLnAX2vwY8e3TBNmryE5D\n" +
//                        "Ak8NVpwOjuzCHGapr6UrELnUiMkta/fxgZlGMQIDAQABo4IB0TCCAc0wHwYDVR0j\n" +
//                        "BBgwFoAUkK9qOpRaC9iQ6hJWc99DtDoo2ucwHQYDVR0OBBYEFBlrPBAcWuJLFSys\n" +
//                        "lficCZstC0LHMA4GA1UdDwEB/wQEAwIFoDAMBgNVHRMBAf8EAjAAMB0GA1UdJQQW\n" +
//                        "MBQGCCsGAQUFBwMBBggrBgEFBQcDAjBPBgNVHSAESDBGMDoGCysGAQQBsjEBAgIH\n" +
//                        "MCswKQYIKwYBBQUHAgEWHWh0dHBzOi8vc2VjdXJlLmNvbW9kby5jb20vQ1BTMAgG\n" +
//                        "BmeBDAECATBUBgNVHR8ETTBLMEmgR6BFhkNodHRwOi8vY3JsLmNvbW9kb2NhLmNv\n" +
//                        "bS9DT01PRE9SU0FEb21haW5WYWxpZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3JsMIGF\n" +
//                        "BggrBgEFBQcBAQR5MHcwTwYIKwYBBQUHMAKGQ2h0dHA6Ly9jcnQuY29tb2RvY2Eu\n" +
//                        "Y29tL0NPTU9ET1JTQURvbWFpblZhbGlkYXRpb25TZWN1cmVTZXJ2ZXJDQS5jcnQw\n" +
//                        "JAYIKwYBBQUHMAGGGGh0dHA6Ly9vY3NwLmNvbW9kb2NhLmNvbTAfBgNVHREEGDAW\n" +
//                        "ggoqLmNvdW50Lmx5gghjb3VudC5seTANBgkqhkiG9w0BAQsFAAOCAQEALoiydrPN\n" +
//                        "YnVWAwScg1pNXOyWQB+d65Wc9gQJiOQi68yo4QwMVeq/EgvytFc6l2KLJmIdHa9f\n" +
//                        "Q7ijJ4hBrqBXSVmrUFeOrLmH8JVcD/lrgyqQZ7w5lIkpxVz2uINcEtYMrJ7T69rz\n" +
//                        "pfp9p0s8WrMaZrQehDOCMZOf+4wtEqQiAzh6bUkNPWABVHMplaNrlFqel81pZUev\n" +
//                        "TKbvq0VQfYzsqPq9uT7IK3oSPps0kQp5JH1JMiWtMY+OpGMTLIYSc3azAT2ZPWvD\n" +
//                        "8GHd46Ui3glAHnF7i+r6a1g41RzVtloxj1M2IWd/QbAK6RYoz+Ort6T4bRdDxQMA\n" +
//                        "yM8SiWzLjy0pqg=="))
//                .init(this, COUNTLY_SERVER_URL, COUNTLY_APP_KEY)
//                .initMessaging(this, MainActivity.class, COUNTLY_MESSAGING_PROJECT_ID, Countly.CountlyMessagingMode.TEST);
//                .setLocation(LATITUDE, LONGITUDE);
//                .setLoggingEnabled(true);

        Countly.sharedInstance().recordEvent("test", 1);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Countly.sharedInstance().recordEvent("test2", 1, 2);
            }
        }, 5000);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Countly.sharedInstance().recordEvent("test3");
            }
        }, 10000);

    }

    @Override
    public void onStart()
    {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop()
    {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        /** Register for broadcast action if you need to be notified when Countly message received */
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Message message = intent.getParcelableExtra(CountlyMessaging.BROADCAST_RECEIVER_ACTION_MESSAGE);
                Log.i("CountlyActivity", "Got a message with data: " + message.getData());
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(CountlyMessaging.getBroadcastAction(getApplicationContext()));
        registerReceiver(messageReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(messageReceiver);
    }
}
